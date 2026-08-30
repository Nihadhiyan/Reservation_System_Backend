package com.bookfair.backend.service;

import com.bookfair.backend.dto.eventstall.mapper.EventStallMapper;
import com.bookfair.backend.dto.eventstall.request.CreateEventStallRequest;
import com.bookfair.backend.dto.eventstall.request.UpdateEventStallRequest;
import com.bookfair.backend.dto.eventstall.response.EventStallResponse;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.*;
import com.bookfair.backend.model.enums.AvailabilityStatus;
import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventStallService {

    private final EventStallRepository eventStallRepository;
    private final EventRepository eventRepository;
    private final StallRepository stallRepository;
    private final EventStallMapper eventStallMapper;
    private final ApplicationEventPublisher eventPublisher;

    // Called automatically when an organizer's EventSpaceBooking is confirmed
    // Generates EventStall records for all stalls in the booked spaces
    // All start as active and available — organizer customizes after
    @Transactional
    public void generateEventStallsForEvent(UUID eventId, List<Stall> stalls) {
        // Filter out stalls that already have EventStall records
        // (prevents duplicates if called multiple times)
        List<Stall> newStalls = stalls.stream()
            .filter(s -> !eventStallRepository
                .existsByEventIdAndStallId(eventId, s.getId()))
            .toList();

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Event not found", ErrorCode.EVENT_NOT_FOUND));

        List<EventStall> eventStalls = newStalls.stream()
            .map(stall -> EventStall.builder()
                .event(event)
                .stall(stall)
                .activeForEvent(true)
                .availabilityStatus(AvailabilityStatus.AVAILABLE)
                .customLayout(null)
                .customName(null)
                .eventPrice(null)
                .build())
            .toList();

        eventStallRepository.saveAll(eventStalls);

        log.info("Generated {} EventStall records for event [{}]",
            eventStalls.size(), eventId);
    }

    // Organizer adds a specific stall to their event with optional customization
    @Transactional
    @CacheEvict(value = "eventLayout", key = "#eventId + '*'")
    public EventStallResponse addStallToEvent(
            UUID eventId, CreateEventStallRequest request) {

        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Event not found", ErrorCode.EVENT_NOT_FOUND));

        Stall stall = stallRepository.findById(request.stallId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Stall not found", ErrorCode.STALL_NOT_FOUND));

        // Check stall not already in this event
        if (eventStallRepository.existsByEventIdAndStallId(eventId, stall.getId())) {
            throw new BusinessException(
                "Stall '" + stall.getName() + "' is already added to this event.",
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        EventStall eventStall = EventStall.builder()
            .event(event)
            .stall(stall)
            .activeForEvent(
                request.activeForEvent() != null ? request.activeForEvent() : true)
            .availabilityStatus(AvailabilityStatus.AVAILABLE)
            .customLayout(request.customLayout() != null
                ? new com.bookfair.backend.model.embedded.LayoutPosition(request.customLayout().x(), request.customLayout().y(), request.customLayout().width(), request.customLayout().length(), request.customLayout().rotation()) : null)
            .customName(request.customName())
            .eventPrice(request.eventPrice())
            .build();

        EventStall saved = eventStallRepository.save(eventStall);

        log.info("Organizer added stall [{}] to event [{}]",
            stall.getId(), eventId);

        return eventStallMapper.toEventStallResponse(saved);
    }

    // Organizer updates stall configuration for their event
    @Transactional
    @CacheEvict(value = "eventLayout", key = "#eventId + '*'")
    public EventStallResponse updateEventStall(
            UUID eventId, UUID stallId, UpdateEventStallRequest request) {

        EventStall eventStall = eventStallRepository
            .findByEventIdAndStallId(eventId, stallId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "EventStall not found for this event and stall",
                ErrorCode.STALL_NOT_FOUND));

        // Cannot deactivate a stall that is already booked by a vendor
        if (request.activeForEvent() != null
                && !request.activeForEvent()
                && eventStall.getAvailabilityStatus() == AvailabilityStatus.BOOKED) {
            throw new BusinessException(
                "Cannot disable stall '"
                + eventStall.getEffectiveName()
                + "' — it is already booked by a vendor.",
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        // Apply updates — only non-null values from request
        if (request.activeForEvent() != null) {
            eventStall.setActiveForEvent(request.activeForEvent());
            // If disabling, block it so vendors can't book
            if (!request.activeForEvent()) {
                eventStall.setAvailabilityStatus(AvailabilityStatus.BLOCKED);
            }
            // If re-enabling, make it available again
            if (request.activeForEvent()
                    && eventStall.getAvailabilityStatus() == AvailabilityStatus.BLOCKED) {
                eventStall.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
            }
        }

        if (request.customLayout() != null) {
            // Validate new position doesn't overlap other stalls in this event
            validateNoOverlapWithOtherEventStalls(eventId, stallId, request.customLayout());
            eventStall.setCustomLayout(
                new com.bookfair.backend.model.embedded.LayoutPosition(request.customLayout().x(), request.customLayout().y(), request.customLayout().width(), request.customLayout().length(), request.customLayout().rotation()));
        }

        if (request.customName() != null) {
            eventStall.setCustomName(
                request.customName().isBlank() ? null : request.customName());
        }

        if (request.eventPrice() != null) {
            eventStall.setEventPrice(request.eventPrice());
        }

        EventStall saved = eventStallRepository.save(eventStall);

        log.info("Organizer updated EventStall [{}] in event [{}]",
            stallId, eventId);

        return eventStallMapper.toEventStallResponse(saved);
    }

    // Vendor-facing — available stalls for an event
    // This is what vendors see when browsing to book
    @Transactional(readOnly = true)
    @Cacheable(value = "eventLayout", key = "#eventId + '-all'")
    public List<EventStallResponse> getAvailableStallsForEvent(UUID eventId) {
        return eventStallRepository
            .findByEventIdAndAvailabilityStatus(eventId, AvailabilityStatus.AVAILABLE)
            .stream()
            .map(eventStallMapper::toEventStallResponse)
            .toList();
    }

    // Vendor-facing — available stalls in a specific hall within an event
    // Used for the hall layout map view
    @Transactional(readOnly = true)
    @Cacheable(value = "eventLayout", key = "#eventId + '-' + #hallId")
    public List<EventStallResponse> getEventLayoutForHall(UUID eventId, UUID hallId) {
        return eventStallRepository
            .findByEventIdAndHallIdAndActiveForEventTrue(eventId, hallId)
            .stream()
            .map(eventStallMapper::toEventStallResponse)
            .toList();
    }

    // Organizer-facing — all stalls including disabled ones
    // For the organizer's management view of their event layout
    @Transactional(readOnly = true)
    public List<EventStallResponse> getFullEventLayoutForHall(UUID eventId, UUID hallId) {
        return eventStallRepository
            .findAllByEventIdAndHallId(eventId, hallId)
            .stream()
            .map(eventStallMapper::toEventStallResponse)
            .toList();
    }

    // Validate no spatial overlap with other active event stalls in the same hall
    private void validateNoOverlapWithOtherEventStalls(
            UUID eventId, UUID currentStallId, LayoutPositionDto newLayout) {

        // Get stall's hall
        EventStall current = eventStallRepository
            .findByEventIdAndStallId(eventId, currentStallId)
            .orElseThrow();

        UUID hallId = current.getStall().getHall().getId();

        List<EventStall> others = eventStallRepository
            .findByEventIdAndHallIdAndActiveForEventTrue(eventId, hallId)
            .stream()
            .filter(es -> !es.getStall().getId().equals(currentStallId))
            .toList();

        for (EventStall other : others) {
            com.bookfair.backend.model.embedded.LayoutPosition otherLayout = other.getEffectiveLayout();
            if (otherLayout == null) continue;

            if (rectanglesOverlap(
                    newLayout.x(), newLayout.y(),
                    newLayout.width(), newLayout.length(),
                    otherLayout.getX(), otherLayout.getY(),
                    otherLayout.getWidth(), otherLayout.getLength())) {
                throw new BusinessException(
                    "New position overlaps with stall '"
                    + other.getEffectiveName() + "'.",
                    ErrorCode.BUSINESS_RULE_VIOLATION);
            }
        }
    }

    private boolean rectanglesOverlap(
            int x1, int y1, int w1, int h1,
            int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2 && x1 + w1 > x2
            && y1 < y2 + h2 && y1 + h1 > y2;
    }
}
