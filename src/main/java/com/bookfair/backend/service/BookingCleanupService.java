package com.bookfair.backend.service;

import com.bookfair.backend.repository.EventSpaceBookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingCleanupService {

    private final EventSpaceBookingRepository bookingRepository;

    @Scheduled(cron = "0 */15 * * * ?", zone = "UTC") // Every 15 minutes
    @Transactional
    public void expirePendingBookings() {
        // Expire pending bookings older than 30 minutes
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        int expired = bookingRepository.expirePendingBookingsOlderThan(cutoff);
        if (expired > 0) {
            log.info("Expired {} pending space bookings", expired);
        }
    }
}
