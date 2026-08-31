package com.bookfair.backend.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.dto.hall.mapper.HallMapper;
import com.bookfair.backend.dto.hall.request.CreateHallRequest;
import com.bookfair.backend.dto.hall.request.UpdateHallRequest;
import com.bookfair.backend.dto.hall.response.HallLayoutResponse;
import com.bookfair.backend.dto.hall.response.HallResponse;
import com.bookfair.backend.dto.stall.mapper.StallMapper;
import com.bookfair.backend.dto.stall.response.StallResponse;
import com.bookfair.backend.event.cache.HallUpdatedEvent;
import com.bookfair.backend.event.hierarchy.HallDeactivatedEvent;
import com.bookfair.backend.event.layout.HallDimensionsChangedEvent;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.Floor;
import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.LayoutMarker;
import com.bookfair.backend.model.LayoutPosition;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.enums.AvailabilityStatus;
import com.bookfair.backend.repository.EventRepository;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.EventStallRepository;
import com.bookfair.backend.repository.FloorRepository;
import com.bookfair.backend.repository.HallRepository;
import com.bookfair.backend.repository.LayoutMarkerRepository;
import com.bookfair.backend.repository.StallRepository;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HallService {

        private final HallRepository hallRepository;
        private final FloorRepository floorRepository;
        private final StallRepository stallRepository;
        private final EventRepository eventRepository;
        private final EventSpaceBookingRepository bookingRepository;
        private final EventStallRepository eventStallRepository;
        private final LayoutMarkerRepository layoutMarkerRepository;
        private final HallMapper hallMapper;
        private final StallMapper stallMapper;
        private final CommonMapper commonMapper;
        private final ApplicationEventPublisher eventPublisher;

        @Transactional
        public HallResponse createHall(CreateHallRequest request) {
                requireNonNull(request, "request cannot be null");
                Floor floor = floorRepository.findById(requireNonNull(request.floorId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Floor not found",
                                                ErrorCode.FLOOR_NOT_FOUND));

                if (!Boolean.TRUE.equals(floor.getActive())) {
                        throw new BusinessException("Cannot create a Hall under an inactive Floor.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                Hall hall = hallMapper.toHall(request, floor);

                Hall saved = hallRepository.save(hall);
                return hallMapper.toHallResponse(saved);
        }

        @Transactional(readOnly = true)
        public HallResponse getHallById(UUID id) {
                Hall hall = hallRepository.findById(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException("Hall not found",
                                                ErrorCode.HALL_NOT_FOUND));

                return hallMapper.toHallResponse(hall);
        }

        @Transactional(readOnly = true)
        public HallLayoutResponse getHallLayout(UUID id) {
                Hall hall = hallRepository.findById(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException("Hall not found",
                                                ErrorCode.HALL_NOT_FOUND));

                return hallMapper.toHallLayoutResponse(hall);
        }

        @Transactional
        public HallResponse updateHall(UUID id, UpdateHallRequest request) {
                requireNonNull(request, "request cannot be null");
                // Lock the Hall row for the duration of this transaction so that a concurrent
                // stall placement/grid generation on this Hall cannot interleave with a resize
                // and produce stalls that end up outside the (possibly shrinking) bounds.
                Hall hall = hallRepository.findByIdForUpdate(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException("Hall not found",
                                                ErrorCode.HALL_NOT_FOUND));

                boolean wasInactive = !Boolean.TRUE.equals(hall.getActive());

                Integer oldWidth = (hall.getLayout() != null) ? hall.getLayout().getWidth() : null;
                Integer oldHeight = (hall.getLayout() != null) ? hall.getLayout().getHeight() : null;

                Floor floor = floorRepository.findById(requireNonNull(request.floorId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Floor not found",
                                                ErrorCode.FLOOR_NOT_FOUND));

                if (!hall.getFloor().getId().equals(floor.getId()) && !Boolean.TRUE.equals(floor.getActive())) {
                        throw new BusinessException("Cannot move Hall to an inactive Floor.",
                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                }

                if (!hall.getFloor().getId().equals(floor.getId())) {
                        UUID oldVenueId = hall.getFloor().getBuilding().getVenue().getId();
                        UUID newVenueId = floor.getBuilding().getVenue().getId();
                        if (!oldVenueId.equals(newVenueId)) {
                                if (bookingRepository.countActiveByHallId(hall.getId(), Instant.now()) > 0) {
                                        throw new BusinessException("Cannot move Hall across Venues because stalls are assigned to Events in the original Venue.",
                                                        ErrorCode.BUSINESS_RULE_VIOLATION);
                                }
                        }
                }

                if (request.active() != null && !request.active() && Boolean.TRUE.equals(hall.getActive())) {
                        validateNoActiveBookingsForHall(hall.getId(), hall.getName());
                }

                LayoutPosition layout = commonMapper.toLayoutPosition(request.layout());
                Integer newWidth = (layout != null) ? layout.getWidth() : null;
                Integer newHeight = (layout != null) ? layout.getHeight() : null;

                if (newWidth != null && newHeight != null && (!Objects.equals(oldWidth, newWidth) || !Objects.equals(oldHeight, newHeight))) {
                        List<Stall> stalls = stallRepository.findByHallIdAndActiveTrue(hall.getId());
                        for (Stall stall : stalls) {
                                if (stall.getLayout() != null && stall.getLayout().getXCoord() != null && stall.getLayout().getYCoord() != null
                                                && stall.getLayout().getWidth() != null && stall.getLayout().getHeight() != null) {
                                        if (stall.getLayout().getXCoord() + stall.getLayout().getWidth() > newWidth
                                                        || stall.getLayout().getYCoord() + stall.getLayout().getHeight() > newHeight) {
                                                throw new BusinessException("Cannot resize Hall: Stall " + stall.getName() + " would exceed new dimensions.", ErrorCode.BUSINESS_RULE_VIOLATION);
                                        }
                                }
                        }
                        List<LayoutMarker> markers = layoutMarkerRepository.findByHallIdAndActiveTrue(hall.getId());
                        for (LayoutMarker marker : markers) {
                                if (marker.getLayout() != null && marker.getLayout().getXCoord() != null && marker.getLayout().getYCoord() != null
                                                && marker.getLayout().getWidth() != null && marker.getLayout().getHeight() != null) {
                                        if (marker.getLayout().getXCoord() + marker.getLayout().getWidth() > newWidth
                                                        || marker.getLayout().getYCoord() + marker.getLayout().getHeight() > newHeight) {
                                                throw new BusinessException("Cannot resize Hall: LayoutMarker " + marker.getLabel() + " would exceed new dimensions.", ErrorCode.BUSINESS_RULE_VIOLATION);
                                        }
                                }
                        }
                }

                hall.setName(request.name());
                hall.setSpaceCategory(request.spaceCategory());
                hall.setHallType(request.hallType());
                hall.setBlueprintImageUrl(request.blueprintImageUrl());
                hall.setSquareFootage(request.squareFootage());
                hall.setMaxStalls(request.maxStalls());
                hall.setWifiAvailable(request.wifiAvailable());
                hall.setAirConditioned(request.airConditioned());
                hall.setActive(request.active());
                hall.setLayout(layout);
                hall.setFloor(floor);

                Hall saved = hallRepository.save(hall);
                eventPublisher.publishEvent(new HallUpdatedEvent(saved.getId()));

                if (newWidth != null && newHeight != null && (!Objects.equals(oldWidth, newWidth) || !Objects.equals(oldHeight, newHeight))) {
                        eventPublisher.publishEvent(new HallDimensionsChangedEvent(saved.getId(), newWidth, newHeight));
                }

                // Deactivating a Hall cascades to deactivate its Stalls/EventStalls (HierarchyDeactivationListener).
                // Without a symmetric reactivation path, a Hall flipped back to active would otherwise be left
                // with zero usable stalls forever. Reactivate stalls that were never actually booked/blocked by
                // a real reservation; leave anything with a genuine BOOKED/BLOCKED EventStall for manual review.
                if (wasInactive && Boolean.TRUE.equals(saved.getActive())) {
                        reactivateHallChildren(saved);
                }

                return hallMapper.toHallResponse(saved);
        }

        private void reactivateHallChildren(Hall hall) {
                List<Stall> toReactivate = stallRepository.findByHallIdAndActiveFalse(hall.getId())
                        .stream()
                        .filter(s -> !eventStallRepository.existsByStallIdAndAvailabilityStatusIn(
                                s.getId(), List.of(AvailabilityStatus.BOOKED, AvailabilityStatus.BLOCKED)))
                        .peek(s -> s.setActive(true))
                        .toList();

                stallRepository.saveAll(toReactivate);
        }

        @Transactional
        public void deleteHall(UUID id) {
                Hall hall = hallRepository.findById(requireNonNull(id))
                                .orElseThrow(() -> new ResourceNotFoundException("Hall not found",
                                                ErrorCode.HALL_NOT_FOUND));

                validateNoActiveBookingsForHall(hall.getId(), hall.getName());

                hall.setActive(false);
                hallRepository.save(hall);
                eventPublisher.publishEvent(new HallDeactivatedEvent(hall.getId()));
        }

        private void validateNoActiveBookingsForHall(UUID hallId, String hallName) {
        UUID venueId = hallRepository.findVenueIdByHallId(hallId)
            .orElseThrow(() -> new ResourceNotFoundException("Hall's venue not found", ErrorCode.VENUE_NOT_FOUND));

        List<Event> upcoming = eventRepository
            .findUpcomingOrOngoingEventsForVenue(venueId, Instant.now());

        if (!upcoming.isEmpty()) {
            Event next = upcoming.get(0);
            throw new BusinessException(
                "Cannot deactivate hall '" + hallName 
                + "' — event '" + next.getName() 
                + "' is scheduled at this venue until " 
                + next.getEndDateTime() + ".", 
                ErrorCode.BUSINESS_RULE_VIOLATION);
        }
    }

        @Transactional(readOnly = true)
        public List<StallResponse> getStallsByHall(UUID hallId) {
                if (!hallRepository.existsById(requireNonNull(hallId))) {
                        throw new ResourceNotFoundException("Hall not found", ErrorCode.HALL_NOT_FOUND);
                }

                return stallRepository.findByHallIdAndActiveTrue(hallId).stream()
                                .map(stallMapper::toStallResponse)
                                .toList();
        }


}
