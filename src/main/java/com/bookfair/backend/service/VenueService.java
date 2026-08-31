package com.bookfair.backend.service;

import java.util.List;
import java.util.UUID;
import java.time.Instant;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.building.mapper.BuildingMapper;
import com.bookfair.backend.dto.building.response.BuildingResponse;
import com.bookfair.backend.dto.venue.mapper.VenueMapper;
import com.bookfair.backend.dto.venue.request.CreateVenueRequest;
import com.bookfair.backend.dto.venue.request.UpdateVenueRequest;
import com.bookfair.backend.dto.venue.response.VenueMapResponse;
import com.bookfair.backend.dto.venue.response.VenueResponse;
import com.bookfair.backend.event.cache.VenueCreatedEvent;
import com.bookfair.backend.event.cache.VenueUpdatedEvent;
import com.bookfair.backend.event.hierarchy.VenueDeactivatedEvent;
import com.bookfair.backend.dto.common.LayoutMarkerDto;
import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.DuplicateResourceException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.Venue;
import com.bookfair.backend.repository.BuildingRepository;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.OrganizationRepository;
import com.bookfair.backend.repository.VenueRepository;
import com.bookfair.backend.repository.LayoutMarkerRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VenueService {
        private final VenueRepository venueRepository;
        private final OrganizationRepository organizationRepository;
        private final BuildingRepository buildingRepository;
        private final EventRepository eventRepository;
        private final LayoutMarkerRepository layoutMarkerRepository;
        private final VenueMapper venueMapper;
        private final BuildingMapper buildingMapper;
        private final CommonMapper commonMapper;
        private final ApplicationEventPublisher eventPublisher;

        @PreAuthorize("hasRole('SUPER_ADMIN') or @orgAuth.isVenueOwnerAdmin(authentication, #request.ownerOrganizationId())")
        @Transactional
        public VenueResponse createVenue(CreateVenueRequest request) {
                requireNonNull(request, "request cannot be null");
                if (venueRepository.existsByNameAndActiveTrue(requireNonNull(request.name()))) {
                        throw new DuplicateResourceException(
                                        "A venue with this name already exists.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                Organization owner = organizationRepository.findById(requireNonNull(request.ownerOrganizationId()))
                                .orElseThrow(
                                                () -> new ResourceNotFoundException("Owner org not found",
                                                                ErrorCode.ORGANIZATION_NOT_FOUND));

                List<Organization> partners = (request.partnerOrganizationIds() != null
                                && !request.partnerOrganizationIds().isEmpty())
                                                ? organizationRepository
                                                                .findAllById(requireNonNull(request.partnerOrganizationIds()))
                                                : List.of();

                Venue venue = venueMapper.toVenueFromCreateVenueRequest(request);
                venue.setActive(true);
                venue.setOwner(owner);
                venue.setPartners(partners);

                Venue saved = venueRepository.save(venue);
                eventPublisher.publishEvent(new VenueCreatedEvent(saved.getId()));
                return venueMapper.toVenueResponse(saved);
        }

        @Transactional(readOnly = true)
        public Page<VenueResponse> getAllVenues(Pageable pageable) {
                return venueRepository.findAllByActiveTrue(requireNonNull(pageable))
                                .map(venueMapper::toVenueResponse);
        }

        @Cacheable(value = "venues")
        @Transactional(readOnly = true)
        public List<VenueResponse> getAllVenues() {
                return venueRepository.findAllByActiveTrue().stream()
                                .map(venueMapper::toVenueResponse)
                                .toList();
        }

        @Cacheable(value = "venue", key = "#id")
        @Transactional(readOnly = true)
        public VenueResponse getVenue(UUID id) {
                Venue venue = venueRepository.findByIdAndActiveTrue(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Venue not found with ID: " + id,
                                                ErrorCode.VENUE_NOT_FOUND));
                return venueMapper.toVenueResponse(venue);
        }

        @Cacheable(value = "venueMap", key = "#id")
        @Transactional(readOnly = true)
        public VenueMapResponse getVenueMap(UUID id) {
                Venue venue = venueRepository.findDetailedById(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Venue not found with ID: " + id,
                                                ErrorCode.VENUE_NOT_FOUND));
                return venueMapper.toVenueMapResponse(venue);
        }

        @Transactional
        public VenueResponse updateVenue(UUID id, UpdateVenueRequest request) {
                Venue venue = venueRepository.findById(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Venue not found with ID: " + id,
                                                ErrorCode.VENUE_NOT_FOUND));

                if (!venue.getName().equals(request.name()) &&
                                venueRepository.existsByNameAndActiveTrue(requireNonNull(request.name()))) {
                        throw new DuplicateResourceException(
                                        "A venue with this name already exists.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                boolean oldActive = Boolean.TRUE.equals(venue.getActive());
                if (request.active() != null && !request.active() && oldActive) {
                        validateNoActiveBookingsForVenue(venue.getId(), venue.getName());
                }

                Organization owner = organizationRepository.findById(requireNonNull(request.ownerOrganizationId()))
                                .orElseThrow(
                                                () -> new ResourceNotFoundException("Owner org not found",
                                                                ErrorCode.ORGANIZATION_NOT_FOUND));

                List<Organization> partners = (request.partnerOrganizationIds() != null
                                && !request.partnerOrganizationIds().isEmpty())
                                                ? organizationRepository
                                                                .findAllById(requireNonNull(request.partnerOrganizationIds()))
                                                : List.of();

                venue = venueMapper.updateVenueFromUpdateVenueRequest(request, venue);
                venue.setOwner(owner);
                venue.setPartners(partners);

                Venue saved = venueRepository.save(venue);
                if (oldActive && !Boolean.TRUE.equals(saved.getActive())) {
                        eventPublisher.publishEvent(new VenueDeactivatedEvent(saved.getId()));
                } else {
                        eventPublisher.publishEvent(new VenueUpdatedEvent(saved.getId()));
                }
                return venueMapper.toVenueResponse(saved);
        }

        @Transactional
        public void deleteVenue(UUID id) {
                Venue venue = venueRepository.findById(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException(
                                                "Venue not found with ID: " + id,
                                                ErrorCode.VENUE_NOT_FOUND));
                validateNoActiveBookingsForVenue(venue.getId(), venue.getName());
                venue.setActive(false);
                venueRepository.save(venue);
                eventPublisher.publishEvent(new VenueDeactivatedEvent(venue.getId()));
        }

        private void validateNoActiveBookingsForVenue(UUID venueId, String venueName) {
                List<Event> upcomingEvents = eventRepository
                        .findUpcomingOrOngoingEventsForVenue(venueId, Instant.now());

                if (!upcomingEvents.isEmpty()) {
                        Event next = upcomingEvents.get(0);
                        throw new BusinessException(
                                "Cannot deactivate venue '" + venueName
                                + "' — event '" + next.getName()
                                + "' is scheduled at this venue until "
                                + next.getEndDateTime() + ".",
                                ErrorCode.BUSINESS_RULE_VIOLATION);
                }
        }

        @Transactional(readOnly = true)
        public List<BuildingResponse> getBuildingsByVenue(UUID venueId) {
                if (!venueRepository.existsByIdAndActiveTrue(requireNonNull(venueId))) {
                        throw new ResourceNotFoundException(
                                        "Venue not found with ID: " + venueId,
                                        ErrorCode.VENUE_NOT_FOUND);
                }
                return buildingRepository.findByVenueIdAndActiveTrue(venueId).stream()
                                .map(buildingMapper::toBuildingResponse)
                                .toList();
        }

        @Transactional(readOnly = true)
        public List<LayoutMarkerDto> getMarkersByVenue(UUID venueId) {
                if (!venueRepository.existsByIdAndActiveTrue(requireNonNull(venueId))) {
                        throw new ResourceNotFoundException(
                                        "Venue not found with ID: " + venueId,
                                        ErrorCode.VENUE_NOT_FOUND);
                }
                return commonMapper.toLayoutMarkerDtos(layoutMarkerRepository.findByVenueIdAndActiveTrue(venueId));
        }
}
