package com.bookfair.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.model.EventSpaceBooking;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.BookingStatus;
import com.bookfair.backend.model.enums.ReservationStatus;
import com.bookfair.backend.event.reservation.ReservationExpiredEvent;
import com.bookfair.backend.repository.EventSpaceBookingRepository;
import com.bookfair.backend.repository.ReservationRepository;
import org.springframework.context.ApplicationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationCleanupService {
    private final ReservationRepository reservationRepository;
    private final EventSpaceBookingRepository bookingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseExpiredReservations() {
        List<Reservation> expiredReservations = reservationRepository
                .findByExpiresAtBeforeAndStatus(Instant.now(), ReservationStatus.PENDING);

        if (expiredReservations.isEmpty()) {
            return;
        }

        log.info("Found {} expired reservations. Releasing stalls back to the public...", expiredReservations.size());

        List<EventSpaceBooking> bookingsToRelease = new ArrayList<>();
        List<Reservation> confirmedExpired = new ArrayList<>();

        for (Reservation res : expiredReservations) {
            java.util.Optional<Reservation> lockedOpt = reservationRepository.findByIdAndStatusForUpdate(res.getId(), ReservationStatus.PENDING);
            if (lockedOpt.isEmpty()) {
                continue;
            }
            Reservation reservation = lockedOpt.get();
            reservation.setStatus(ReservationStatus.EXPIRED);
            confirmedExpired.add(reservation);

            for (EventSpaceBooking b : reservation.getSpaceBookings()) {
                b.setStatus(BookingStatus.CANCELLED); // Or EXPIRED depending on exact enum value used for this
                bookingsToRelease.add(b);
            }
        }

        if (confirmedExpired.isEmpty()) {
            return;
        }

        bookingRepository.saveAll(bookingsToRelease);
        reservationRepository.saveAll(confirmedExpired);

        log.info("Successfully released {} bookings from {} expired reservations.", bookingsToRelease.size(),
                confirmedExpired.size());

        for (Reservation reservation : confirmedExpired) {
            eventPublisher.publishEvent(new ReservationExpiredEvent(reservation.getUser().getId(), reservation.getUser().getUsername(), reservation.getUser().getEmail(), reservation.getId(), reservation.getEvent().getName()));
        }

    }
}
