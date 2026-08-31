package com.bookfair.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import com.bookfair.backend.event.cache.EventUpdatedEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.event.mapper.EventMapper;
import com.bookfair.backend.dto.event.request.CreateEventRequest;
import com.bookfair.backend.dto.event.request.UpdateEventRequest;
import com.bookfair.backend.dto.event.response.EventResponse;

import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.DuplicateResourceException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ForbiddenException;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.enums.EventStatus;
import com.bookfair.backend.model.enums.AvailabilityStatus;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.enums.SystemRole;
import com.bookfair.backend.model.enums.OrganizationRole;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.Venue;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.OrganizationRepository;
import com.bookfair.backend.repository.UserRepository;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.repository.VenueRepository;
import com.bookfair.backend.util.SecurityUtils;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventService {
        private final EventRepository eventRepository;
        private final EventSpaceBookingRepository bookingRepository;
        private final OrganizationRepository organizationRepository;
        private final VenueRepository venueRepository;
        private final UserRepository userRepository;
        private final OrganizationMemberRepository memberRepository;
        private final EventMapper eventMapper;
        private final org.springframework.context.ApplicationEventPublisher eventPublisher;
        private final SecurityUtils securityUtils;

        // Cache upcoming events list to optimize high-traffic landing page requests
        @Cacheable(value = "events", key = "'upcoming'")
        @Transactional(readOnly = true)
        public List<EventResponse> getUpcomingEvents() {
                return eventRepository.findByStatusAndActiveTrue(EventStatus.UPCOMING).stream()
                                .map(eventMapper::toEventResponse)
                                .toList();
        }

        // Cache paginated event catalog queries
        @Cacheable(value = "events", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
        @Transactional(readOnly = true)
        public Page<EventResponse> getAllEvents(Pageable pageable) {
                requireNonNull(pageable, "pageable cannot be null");
                return eventRepository.findAll(pageable)
                                .map(eventMapper::toEventResponse);
        }

        // Cache individual event details by ID
        @Cacheable(value = "events", key = "#id")
        @Transactional(readOnly = true)
        public EventResponse getEventById(UUID id) {
                Event event = eventRepository.findByIdAndActiveTrue(id)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found",
                                                ErrorCode.EVENT_NOT_FOUND));
                return eventMapper.toEventResponse(event);
        }



        @Transactional
        public EventResponse createEvent(CreateEventRequest request) {
                requireNonNull(request, "request cannot be null");

                if (eventRepository.existsByNameAndActiveTrue(requireNonNull(request.name()))) {
                        throw new DuplicateResourceException(
                                        "An event with this name already exists.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                Organization organizer = organizationRepository.findById(requireNonNull(request.organizerId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                                                ErrorCode.ORGANIZATION_NOT_FOUND));

                Venue venue = venueRepository.findById(requireNonNull(request.venueId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Venue not found",
                                                ErrorCode.VENUE_NOT_FOUND));

                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                requireOrgAdmin(requestingUser, organizer.getId(),
                                "You cannot create an event for another organization.");

                List<Organization> partners = (request.partnerIds() != null && !request.partnerIds().isEmpty())
                                ? organizationRepository.findAllById(requireNonNull(request.partnerIds()))
                                : List.of();

                Event event = eventMapper.toEvent(request, organizer, venue, partners);
                Event savedEvent = eventRepository.save(requireNonNull(event));

                // Publish event to trigger AFTER_COMMIT cache eviction
                eventPublisher.publishEvent(new EventUpdatedEvent(savedEvent.getId()));

                return eventMapper.toEventResponse(savedEvent);
        }

        @Transactional
        public EventResponse updateEvent(UUID id, UpdateEventRequest request) {
                requireNonNull(request, "request cannot be null");
                Event event = eventRepository.findByIdAndActiveTrue(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found",
                                                ErrorCode.EVENT_NOT_FOUND));

                Organization organizer = organizationRepository.findById(requireNonNull(request.organizerId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                                                ErrorCode.ORGANIZATION_NOT_FOUND));

                Venue venue = venueRepository.findById(requireNonNull(request.venueId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Venue not found",
                                                ErrorCode.VENUE_NOT_FOUND));

                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                requireOrgAdmin(requestingUser, event.getOrganizer().getId(),
                                "You cannot modify an event outside your organization.");
                if (!event.getOrganizer().getId().equals(organizer.getId())) {
                        requireOrgAdmin(requestingUser, organizer.getId(),
                                        "You cannot transfer an event to another organization.");
                }

                if (!event.getVenue().getId().equals(venue.getId())) {
                        long count = bookingRepository.countByEventId(event.getId());
                        if (count > 0) {
                                throw new BusinessException("Cannot change Event Venue because stalls from the original Venue are currently assigned to this Event.",
                                                ErrorCode.BUSINESS_RULE_VIOLATION);
                        }
                }

                boolean oldActive = Boolean.TRUE.equals(event.getActive());
                if (request.active() != null && !request.active() && oldActive) {
                        validateNoActiveBookingsForEvent(event.getId(), event.getName());
                }

                List<Organization> partners = (request.partnerIds() != null && !request.partnerIds().isEmpty())
                                ? organizationRepository.findAllById(requireNonNull(request.partnerIds()))
                                : List.of();

                event.setName(request.name());
                event.setEventType(request.eventType());
                event.setStartDateTime(request.startDateTime());
                event.setEndDateTime(request.endDateTime());
                event.setStatus(request.status());
                event.setActive(request.active() != null ? request.active() : event.getActive());
                event.setOrganizer(organizer);
                event.setVenue(venue);
                event.setPartners(partners);

                Event updatedEvent = eventRepository.save(event);

                // Publish event to trigger AFTER_COMMIT cache eviction
                eventPublisher.publishEvent(new EventUpdatedEvent(updatedEvent.getId()));
                if (oldActive && !Boolean.TRUE.equals(updatedEvent.getActive())) {
                        eventPublisher.publishEvent(new com.bookfair.backend.event.hierarchy.EventDeactivatedEvent(updatedEvent.getId()));
                }

                return eventMapper.toEventResponse(updatedEvent);
        }

        @Transactional
        public void deleteEvent(UUID id) {
                Event event = eventRepository.findByIdAndActiveTrue(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found",
                                                ErrorCode.EVENT_NOT_FOUND));

                User requestingUser = userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                                .orElseThrow(() -> new ResourceNotFoundException("User not found",
                                                ErrorCode.USER_NOT_FOUND));

                requireOrgAdmin(requestingUser, event.getOrganizer().getId(),
                                "You cannot delete an event outside your organization.");

                validateNoActiveBookingsForEvent(event.getId(), event.getName());

                event.setActive(false);
                eventRepository.save(event);

                // Publish event to trigger AFTER_COMMIT cache eviction
                eventPublisher.publishEvent(new EventUpdatedEvent(event.getId()));
                eventPublisher.publishEvent(new com.bookfair.backend.event.hierarchy.EventDeactivatedEvent(event.getId()));
        }

        private void validateNoActiveBookingsForEvent(UUID eventId, String eventName) {
                // with EventSpaceBooking, if there's any active booking, we shouldn't deactivate
                long count = bookingRepository.countByEventIdAndStatusIn(eventId,
                                List.of(com.bookfair.backend.model.enums.BookingStatus.PENDING,
                                                com.bookfair.backend.model.enums.BookingStatus.CONFIRMED));
                if (count > 0) {
                        throw new BusinessException("Cannot deactivate Event " + eventName + " because it is currently booked or blocked.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }
        }

        // SUPER_ADMIN bypasses; otherwise the requesting user must be an ORG_ADMIN of organizationId.
        private void requireOrgAdmin(User user, UUID organizationId, String forbiddenMessage) {
                if (user.getSystemRole() == SystemRole.SUPER_ADMIN) {
                        return;
                }
                OrganizationMember member = memberRepository.findByUserIdAndOrganizationId(user.getId(), organizationId)
                                .orElse(null);
                if (member == null || member.getRole() != OrganizationRole.ORG_ADMIN) {
                        throw new ForbiddenException(forbiddenMessage, ErrorCode.FORBIDDEN);
                }
        }

        @Transactional
        public void changeStatus(UUID eventId, String newStatusString) {
                Event eventInstance = eventRepository.findByIdAndActiveTrue(eventId)
                                .orElseThrow(() -> new ResourceNotFoundException("Event not found",
                                                ErrorCode.EVENT_NOT_FOUND));

                String oldStatus = eventInstance.getStatus().name();
                EventStatus newStatus = EventStatus.valueOf(newStatusString.toUpperCase());

                if (!oldStatus.equals(newStatus.name())) {
                        eventInstance.setStatus(newStatus);
                        eventRepository.save(eventInstance);

                        eventPublisher.publishEvent(new com.bookfair.backend.event.event.EventStatusChangedEvent(
                                        eventInstance.getId(),
                                        oldStatus,
                                        newStatus.name()));
                        // Publish event to trigger AFTER_COMMIT cache eviction
                        eventPublisher.publishEvent(new EventUpdatedEvent(eventInstance.getId()));
                }
        }
}
