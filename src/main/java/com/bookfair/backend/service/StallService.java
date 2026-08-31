package com.bookfair.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.stall.mapper.StallMapper;
import com.bookfair.backend.dto.stall.request.CreateStallRequest;
import com.bookfair.backend.dto.stall.request.UpdateStallRequest;
import com.bookfair.backend.dto.stall.response.StallResponse;
import com.bookfair.backend.event.cache.LayoutUpdatedEvent;
import com.bookfair.backend.event.stall.StallCreatedEvent;
import com.bookfair.backend.event.stall.StallDeactivatedEvent;
import com.bookfair.backend.event.stall.StallStatusChangedEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.DuplicateResourceException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.LayoutMarker;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.HallRepository;
import com.bookfair.backend.repository.LayoutMarkerRepository;
import com.bookfair.backend.repository.StallRepository;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StallService {

    private final StallRepository stallRepository;
    private final HallRepository hallRepository;
    private final EventRepository eventRepository;
    private final LayoutMarkerRepository layoutMarkerRepository;
    private final LayoutGenerationService layoutGenerationService;
    private final StallMapper stallMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<StallResponse> getAllStallsForHall(UUID hallId) {
        requireNonNull(hallId, "hallId cannot be null");
        return stallRepository.findByHallIdAndActiveTrue(hallId).stream()
                .map(stallMapper::toStallResponse).toList();
    }

    @Transactional(readOnly = true)
    public StallResponse getStallById(UUID id) {
        Stall stall = stallRepository.findById(requireNonNull(id))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Physical Stall not found", ErrorCode.STALL_NOT_FOUND));

        return stallMapper.toStallResponse(stall);
    }

    @Transactional
    public List<StallResponse> createStalls(List<CreateStallRequest> stallRequests) {
        if (stallRequests == null || stallRequests.isEmpty()) {
            throw new BusinessException("Stall requests list must not be empty", ErrorCode.VALIDATION_ERROR);
        }

        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        String currentUser = (authentication != null) ? authentication.getName() : "system";

        Set<String> seenNames = new HashSet<>();
        Map<UUID, Long> hallCounts = stallRequests.stream()
                .collect(Collectors.groupingBy(sr -> requireNonNull(sr.hallId()), Collectors.counting()));

        // Prefetched once per distinct Hall in the batch (not per stall) — also fixes
        // toStallFromCreateStallRequest never populating Stall.hall (a required FK),
        // which would otherwise NPE in the overlap check below or fail on save.
        Map<UUID, Hall> hallsById = new java.util.HashMap<>();
        Map<UUID, List<Stall>> existingStallsByHall = new java.util.HashMap<>();
        Map<UUID, List<LayoutMarker>> existingMarkersByHall = new java.util.HashMap<>();

        for (Map.Entry<UUID, Long> entry : hallCounts.entrySet()) {
            UUID hallId = entry.getKey();
            long newCount = entry.getValue();
            Hall hall = hallRepository.findById(requireNonNull(hallId))
                    .orElseThrow(
                            () -> new ResourceNotFoundException("Hall not found: " + hallId, ErrorCode.HALL_NOT_FOUND));
            if (!Boolean.TRUE.equals(hall.getActive())) {
                throw new BusinessException("Cannot create stalls under an inactive Hall: " + hall.getName(),
                        ErrorCode.BUSINESS_RULE_VIOLATION);
            }
            if (hall.getMaxStalls() != null) {
                long currentCount = stallRepository.countByHallIdAndActiveTrue(hallId);
                if ((currentCount + newCount) > hall.getMaxStalls()) {
                    throw new BusinessException("Creating stalls exceeds Hall capacity limit of "
                            + hall.getMaxStalls() + " for hall " + hall.getName(), ErrorCode.BUSINESS_RULE_VIOLATION);
                }
            }
            hallsById.put(hallId, hall);
            existingStallsByHall.put(hallId, new ArrayList<>(stallRepository.findByHallIdAndActiveTrue(hallId)));
            existingMarkersByHall.put(hallId, layoutMarkerRepository.findByHallIdAndActiveTrue(hallId));
        }

        List<Stall> stalls = new ArrayList<>();
        for (CreateStallRequest req : stallRequests) {
            String nameKey = req.hallId() + "-" + req.name().toLowerCase();
            if (!seenNames.add(nameKey)) {
                throw new BusinessException("Duplicate stall name in batch request: " + req.name(),
                        ErrorCode.BUSINESS_RULE_VIOLATION);
            }
            if (stallRepository.existsByHallIdAndName(req.hallId(), req.name())) {
                throw new DuplicateResourceException("Stall already exists with name: " + req.name(),
                        ErrorCode.BUSINESS_RULE_VIOLATION);
            }
            Stall stall = stallMapper.toStallFromCreateStallRequest(req);
            Hall hall = hallsById.get(req.hallId());
            stall.setHall(hall);

            layoutGenerationService.validateSpatialConstraints(hall, stall.getLayout(), null,
                    existingStallsByHall.get(req.hallId()), existingMarkersByHall.get(req.hallId()));
            // Note: O(n^2) overlap check, batch sizes should be kept small.
            for (Stall alreadyAdded : stalls) {
                if (alreadyAdded.getHall().getId().equals(stall.getHall().getId()) && alreadyAdded.getLayout() != null
                        && stall.getLayout() != null) {
                    if (rectanglesOverlap(stall.getLayout().getXCoord(), stall.getLayout().getYCoord(),
                            stall.getLayout().getWidth(), stall.getLayout().getHeight(),
                            alreadyAdded.getLayout().getXCoord(), alreadyAdded.getLayout().getYCoord(),
                            alreadyAdded.getLayout().getWidth(), alreadyAdded.getLayout().getHeight())) {
                        throw new BusinessException("Requested stalls overlap with each other: " + stall.getName()
                                + " and " + alreadyAdded.getName(), ErrorCode.BUSINESS_RULE_VIOLATION);
                    }
                }
            }
            // Keep this hall's prefetched list current so later stalls in the same batch
            // are checked against stalls already accepted earlier in this same request.
            existingStallsByHall.get(req.hallId()).add(stall);
            stalls.add(stall);
        }

        List<Stall> savedStalls = stallRepository.saveAll(stalls);

        savedStalls.forEach(savedStall -> {
            eventPublisher.publishEvent(new StallCreatedEvent(
                    requireNonNull(savedStall.getId()),
                    requireNonNull(savedStall.getName()),
                    requireNonNull(savedStall.getHall().getId()),
                    requireNonNull(currentUser)));
            log.info("Stall {} created successfully", savedStall.getName());
        });

        return savedStalls.stream().map(stallMapper::toStallResponse).toList();
    }

    @Transactional
    public StallResponse updateStall(UUID id, UpdateStallRequest stallRequest) {
        Stall stall = stallRepository.findById(requireNonNull(id))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Physical Stall not found", ErrorCode.STALL_NOT_FOUND));

        String oldStatus = stall.getActive() ? "ACTIVE" : "INACTIVE";

        if (stallRequest.active() != null && !stallRequest.active() && "ACTIVE".equals(oldStatus)) {
            validateNoActiveBookingsForStall(id, stall.getName());
        }

        stallMapper.updateStallFromStallRequest(stallRequest, stall);

        if (stall.getLayout() != null) {
            layoutGenerationService.validateSpatialConstraints(stall.getHall(), stall.getLayout(), stall.getId());
        }

        Stall updatedStall = stallRepository.save(stall);

        if (stallRequest.active() != null && !(stallRequest.active() ? "ACTIVE" : "INACTIVE").equals(oldStatus)) {
            eventPublisher.publishEvent(new StallStatusChangedEvent(
                    requireNonNull(updatedStall.getId()),
                    requireNonNull(updatedStall.getName()),
                    oldStatus,
                    updatedStall.getActive() ? "ACTIVE" : "INACTIVE"));
            if (!updatedStall.getActive()) {
                eventPublisher.publishEvent(new StallDeactivatedEvent(updatedStall.getId()));
            }
        } else {
            // Name/layout changes don't flip active status but still affect what the hall
            // layout view shows, so the cache must be invalidated regardless.
            eventPublisher.publishEvent(new LayoutUpdatedEvent(updatedStall.getHall().getId()));
        }

        return stallMapper.toStallResponse(updatedStall);
    }

    @Transactional
    public StallResponse updateStallStatus(UUID stallId, com.bookfair.backend.model.enums.StallActiveStatus newStatus) {
        Stall stall = stallRepository.findById(requireNonNull(stallId))
                .orElseThrow(
                        () -> new ResourceNotFoundException("Physical Stall not found", ErrorCode.STALL_NOT_FOUND));

        String oldStatus = stall.getActive() ? "ACTIVE" : "INACTIVE";
        boolean newActive = newStatus == com.bookfair.backend.model.enums.StallActiveStatus.ACTIVE;

        if (!newActive && "ACTIVE".equals(oldStatus)) {
            validateNoActiveBookingsForStall(stallId, stall.getName());
        }

        stall.setActive(newActive);

        Stall updatedStall = stallRepository.save(stall);

        eventPublisher.publishEvent(new StallStatusChangedEvent(
                requireNonNull(updatedStall.getId()),
                requireNonNull(updatedStall.getName()),
                oldStatus,
                requireNonNull(newStatus.name())));

        if (!newActive && "ACTIVE".equals(oldStatus)) {
            eventPublisher.publishEvent(new StallDeactivatedEvent(updatedStall.getId()));
        }

        return stallMapper.toStallResponse(updatedStall);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<StallResponse> getAvailableStalls(UUID eventId, org.springframework.data.domain.Pageable pageable) {
        return stallRepository.findAvailableStallsByEventId(requireNonNull(eventId), pageable)
                .map(stallMapper::toStallResponse);
    }

    @Transactional
    public void deactivateStall(List<UUID> ids) {
        List<Stall> stalls = stallRepository.findAllByIdInAndActiveTrue(requireNonNull(ids));
        for (Stall stall : stalls) {
            validateNoActiveBookingsForStall(stall.getId(), stall.getName());
            stall.setActive(false);
            eventPublisher.publishEvent(new StallDeactivatedEvent(stall.getId()));
        }
        stallRepository.saveAll(stalls);
    }

    private void validateNoActiveBookingsForStall(UUID stallId, String stallName) {
        UUID venueId = stallRepository.findVenueIdByStallId(stallId)
            .orElseThrow(() -> new ResourceNotFoundException("Stall's venue not found", ErrorCode.VENUE_NOT_FOUND));

        List<Event> upcoming = eventRepository
            .findUpcomingOrOngoingEventsForVenue(venueId, Instant.now());

        if (!upcoming.isEmpty()) {
            Event next = upcoming.get(0);
            throw new BusinessException(
                "Cannot deactivate stall '" + stallName 
                + "' — event '" + next.getName() 
                + "' is scheduled at this venue until " 
                + next.getEndDateTime() + ".", 
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

    private boolean rectanglesOverlap(int x1, int y1, int w1, int h1, int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }
}
