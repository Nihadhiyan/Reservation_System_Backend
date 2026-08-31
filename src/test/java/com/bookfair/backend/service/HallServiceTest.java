package com.bookfair.backend.service;

import com.bookfair.backend.dto.common.LayoutPositionDto;
import com.bookfair.backend.dto.common.Mapper.CommonMapper;
import com.bookfair.backend.dto.hall.mapper.HallMapper;
import com.bookfair.backend.dto.hall.request.UpdateHallRequest;
import com.bookfair.backend.dto.hall.response.HallResponse;
import com.bookfair.backend.dto.stall.mapper.StallMapper;
import com.bookfair.backend.model.*;
import com.bookfair.backend.model.enums.AvailabilityStatus;
import com.bookfair.backend.model.enums.HallType;
import com.bookfair.backend.model.enums.SpaceCategory;
import com.bookfair.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers the reactivateHallChildren fix: reactivating a Hall must not silently
 * resurrect stalls that have a genuine BOOKED/BLOCKED EventStall — those need
 * manual review, not automatic reactivation.
 */
@ExtendWith(MockitoExtension.class)
class HallServiceTest {

    @Mock private HallRepository hallRepository;
    @Mock private FloorRepository floorRepository;
    @Mock private StallRepository stallRepository;
    @Mock private EventRepository eventRepository;
    @Mock private EventSpaceBookingRepository bookingRepository;
    @Mock private EventStallRepository eventStallRepository;
    @Mock private LayoutMarkerRepository layoutMarkerRepository;
    @Mock private HallMapper hallMapper;
    @Mock private StallMapper stallMapper;
    @Mock private CommonMapper commonMapper;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private HallService hallService;

    private UUID hallId;
    private Hall hall;
    private Floor floor;
    private UpdateHallRequest request;

    @BeforeEach
    void setUp() {
        hallId = UUID.randomUUID();
        UUID floorId = UUID.randomUUID();

        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        Building building = new Building();
        building.setVenue(venue);
        floor = new Floor();
        floor.setId(floorId);
        floor.setActive(true);
        floor.setBuilding(building);

        LayoutPosition layout = new LayoutPosition();
        layout.setWidth(1000);
        layout.setHeight(1000);

        hall = new Hall();
        hall.setId(hallId);
        hall.setActive(false); // wasInactive = true
        hall.setFloor(floor);
        hall.setLayout(layout);

        request = new UpdateHallRequest(floorId, "Hall A", SpaceCategory.INDOOR, HallType.STANDARD,
                new LayoutPositionDto(0, 0, 1000, 1000), "http://img", 500.0, 10, true, true, true);

        when(hallRepository.findByIdForUpdate(hallId)).thenReturn(Optional.of(hall));
        when(floorRepository.findById(floorId)).thenReturn(Optional.of(floor));
        // Same dimensions as the hall's current layout — keeps the resize-validation
        // branch (a different concern) out of these reactivation-focused tests.
        LayoutPosition sameLayout = new LayoutPosition();
        sameLayout.setWidth(1000);
        sameLayout.setHeight(1000);
        when(commonMapper.toLayoutPosition(request.layout())).thenReturn(sameLayout);
        when(hallRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hallMapper.toHallResponse(any())).thenReturn(mock(HallResponse.class));
    }

    private Stall inactiveStall() {
        Stall stall = new Stall();
        stall.setId(UUID.randomUUID());
        stall.setActive(false);
        stall.setHall(hall);
        return stall;
    }

    @Test
    void reactivatesStalls_withNoGenuineEventStallBookings() {
        Stall freeStall = inactiveStall();
        when(stallRepository.findByHallIdAndActiveFalse(hallId)).thenReturn(List.of(freeStall));
        when(eventStallRepository.existsByStallIdAndAvailabilityStatusIn(
                eq(freeStall.getId()), eq(List.of(AvailabilityStatus.BOOKED, AvailabilityStatus.BLOCKED))))
                .thenReturn(false);

        hallService.updateHall(hallId, request);

        assertThat(freeStall.getActive()).isTrue();
        verify(stallRepository).saveAll(List.of(freeStall));
    }

    @Test
    void doesNotReactivate_stallWithGenuineBookedEventStall() {
        Stall bookedStall = inactiveStall();
        when(stallRepository.findByHallIdAndActiveFalse(hallId)).thenReturn(List.of(bookedStall));
        when(eventStallRepository.existsByStallIdAndAvailabilityStatusIn(
                eq(bookedStall.getId()), eq(List.of(AvailabilityStatus.BOOKED, AvailabilityStatus.BLOCKED))))
                .thenReturn(true);

        hallService.updateHall(hallId, request);

        assertThat(bookedStall.getActive()).isFalse();
        verify(stallRepository).saveAll(List.of());
    }

    @Test
    void doesNotReactivateAnything_whenHallWasAlreadyActive() {
        hall.setActive(true); // wasInactive = false

        hallService.updateHall(hallId, request);

        verify(stallRepository, never()).findByHallIdAndActiveFalse(any());
        verify(eventStallRepository, never()).existsByStallIdAndAvailabilityStatusIn(any(), any());
    }
}
