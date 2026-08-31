package com.bookfair.backend.service;

import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.model.Building;
import com.bookfair.backend.model.Floor;
import com.bookfair.backend.model.Hall;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.model.Venue;
import com.bookfair.backend.model.enums.BookingLevel;
import com.bookfair.backend.repository.BuildingRepository;
import com.bookfair.backend.repository.FloorRepository;
import com.bookfair.backend.repository.HallRepository;
import com.bookfair.backend.repository.StallRepository;
import com.bookfair.backend.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final VenueRepository venueRepository;
    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final HallRepository hallRepository;
    private final StallRepository stallRepository;

    public BigDecimal calculatePrice(BookingLevel level, UUID spaceId, Instant start, Instant end) {
        long days = Duration.between(start, end).toDays();
        if (days == 0) days = 1; // Minimum 1 day

        BigDecimal dailyRate = resolveRate(level, spaceId);
        
        if (dailyRate == null) {
            throw new BusinessException(
                "No daily rate set for this " + level.name().toLowerCase() + " or its parents. Please configure pricing before accepting bookings.",
                ErrorCode.PRICING_NOT_CONFIGURED);
        }

        return dailyRate.multiply(BigDecimal.valueOf(days));
    }

    private BigDecimal resolveRate(BookingLevel level, UUID spaceId) {
        return switch (level) {
            case STALL -> {
                Stall s = stallRepository.findById(spaceId).orElseThrow();
                if (s.getDailyRate() != null) yield s.getDailyRate();
                if (s.getHall().getDailyRate() != null) yield s.getHall().getDailyRate();
                if (s.getHall().getFloor().getDailyRate() != null) yield s.getHall().getFloor().getDailyRate();
                if (s.getHall().getFloor().getBuilding().getDailyRate() != null) yield s.getHall().getFloor().getBuilding().getDailyRate();
                yield s.getHall().getFloor().getBuilding().getVenue().getDailyRentRate();
            }
            case HALL -> {
                Hall h = hallRepository.findById(spaceId).orElseThrow();
                if (h.getDailyRate() != null) yield h.getDailyRate();
                if (h.getFloor().getDailyRate() != null) yield h.getFloor().getDailyRate();
                if (h.getFloor().getBuilding().getDailyRate() != null) yield h.getFloor().getBuilding().getDailyRate();
                yield h.getFloor().getBuilding().getVenue().getDailyRentRate();
            }
            case FLOOR -> {
                Floor f = floorRepository.findById(spaceId).orElseThrow();
                if (f.getDailyRate() != null) yield f.getDailyRate();
                if (f.getBuilding().getDailyRate() != null) yield f.getBuilding().getDailyRate();
                yield f.getBuilding().getVenue().getDailyRentRate();
            }
            case BUILDING -> {
                Building b = buildingRepository.findById(spaceId).orElseThrow();
                if (b.getDailyRate() != null) yield b.getDailyRate();
                yield b.getVenue().getDailyRentRate();
            }
            case VENUE -> {
                Venue v = venueRepository.findById(spaceId).orElseThrow();
                yield v.getDailyRentRate();
            }
        };
    }
}
