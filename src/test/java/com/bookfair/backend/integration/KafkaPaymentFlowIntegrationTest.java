package com.bookfair.backend.integration;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.model.*;
import com.bookfair.backend.model.enums.*;
import com.bookfair.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the payment-completion Kafka fan-out actually works against a real
 * broker and a real Postgres database — not mocks. This is the only test in
 * the suite that would have caught the application.yml misconfiguration found
 * during the DevOps review (spring.kafka.* nested under management.* instead
 * of spring.*, which silently broke the producer/consumer JSON (de)serializer
 * config): a unit test mocking KafkaTemplate can't detect a broken Spring
 * Kafka autoconfiguration binding, since the mock never touches real config.
 *
 * It also proves the TicketingConsumer race/expiry fix (see TicketingConsumerTest
 * for the isolated-logic version) end-to-end: publish a real PaymentCompletedEvent,
 * let the real @KafkaListener consume it, and assert the database actually changed.
 */
class KafkaPaymentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private VenueRepository venueRepository;
    @Autowired private BuildingRepository buildingRepository;
    @Autowired private FloorRepository floorRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private StallRepository stallRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private EventSpaceBookingRepository bookingRepository;

    private Reservation seedPendingReservationWithOneStallBooking(Instant expiresAt) {
        Organization org = new Organization();
        org.setName("Test Vendor Org " + UUID.randomUUID());
        org.setRegistrationNumber("REG-" + UUID.randomUUID());
        org.setCapabilities(Set.of(OrganizationCapability.OPERATES_STALLS));
        org = organizationRepository.save(org);

        User user = new User();
        user.setUsername("vendor_" + UUID.randomUUID());
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("irrelevant-hash");
        user.setSystemRole(SystemRole.CUSTOMER);
        user = userRepository.save(user);

        Venue venue = new Venue();
        venue.setName("Test Venue " + UUID.randomUUID());
        venue.setAddress("1 Test St");
        venue.setCity("Testville");
        venue.setCountry("Testland");
        venue.setEmail("venue" + UUID.randomUUID() + "@example.com");
        venue.setTotalSquareFootage(10000.0);
        venue.setOwner(org);
        venue.setPremiseId("TEST-PREMISE-" + UUID.randomUUID());
        venue = venueRepository.save(venue);

        Building building = new Building();
        building.setName("Main Building");
        building.setSquareFootage(5000.0);
        building.setType(BuildingType.INDOOR);
        building.setLayoutPosition(new LayoutPosition(0, 0, 1000, 1000));
        building.setVenue(venue);
        building = buildingRepository.save(building);

        Floor floor = new Floor();
        floor.setLevelName("Ground");
        floor.setLevelNumber(0);
        floor.setBuilding(building);
        floor = floorRepository.save(floor);

        Hall hall = new Hall();
        hall.setName("Hall A");
        hall.setSpaceCategory(SpaceCategory.INDOOR);
        hall.setHallType(HallType.STANDARD);
        hall.setLayout(new LayoutPosition(0, 0, 500, 500));
        hall.setFloor(floor);
        hall = hallRepository.save(hall);

        Stall stall = new Stall();
        stall.setName("S-1");
        stall.setHall(hall);
        stall.setLayout(new LayoutPosition(0, 0, 10, 10));
        stall.setSquareFootage(50.0);
        stall = stallRepository.save(stall);

        Genre genre = new Genre();
        genre.setName("Fiction " + UUID.randomUUID());
        genre = genreRepository.save(genre);

        Event event = new Event();
        event.setName("Test Book Fair " + UUID.randomUUID());
        event.setStartDateTime(Instant.now().plus(1, ChronoUnit.DAYS));
        event.setEndDateTime(Instant.now().plus(3, ChronoUnit.DAYS));
        event.setEventType(EventType.BOOK_FAIR);
        event.setStatus(EventStatus.UPCOMING);
        event.setVenue(venue);
        event.setOrganizer(org);
        event = eventRepository.save(event);

        Reservation reservation = new Reservation();
        reservation.setUser(user);
        reservation.setOrganization(org);
        reservation.setEvent(event);
        reservation.setGenre(genre);
        // reservationStartDateTime means "when this hold begins", not "when the event
        // itself starts" — expiresAt (the short hold window) must be after it, so derive
        // it relative to expiresAt (mirrors the real 15-minute hold window) rather than
        // relative to "now", which would violate that ordering in the expired-hold case.
        reservation.setReservationStartDateTime(expiresAt.minus(15, ChronoUnit.MINUTES));
        reservation.setExpiresAt(expiresAt);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setTotalPrice(BigDecimal.valueOf(150));
        reservation = reservationRepository.save(reservation);

        EventSpaceBooking booking = new EventSpaceBooking();
        booking.setEvent(event);
        booking.setBookingLevel(BookingLevel.STALL);
        booking.setStall(stall);
        booking.setStatus(BookingStatus.PENDING);
        booking.setPrice(BigDecimal.valueOf(150));
        booking.setStartsAt(event.getStartDateTime());
        booking.setEndsAt(event.getEndDateTime());
        booking.setReservation(reservation);
        bookingRepository.save(booking);

        return reservation;
    }

    @Test
    void paymentCompletedEvent_confirmsReservationThroughRealKafkaBroker() {
        Reservation reservation = seedPendingReservationWithOneStallBooking(Instant.now().plus(15, ChronoUnit.MINUTES));

        kafkaTemplate.send("payment-completed-topic", reservation.getId().toString(),
                new PaymentCompletedEvent(reservation.getId(), "pi_integration_test", BigDecimal.valueOf(150)));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            Reservation reloaded = reservationRepository.findById(reservation.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
            assertThat(reloaded.getQrCodePayload()).isEqualTo("RES-" + reservation.getId());
        });
    }

    @Test
    void paymentCompletedEvent_doesNotConfirm_whenReservationHoldAlreadyExpired() {
        // End-to-end proof of the TicketingConsumer fix: even through a real broker
        // and real DB, a payment completing after the hold window must not resurrect it.
        Reservation reservation = seedPendingReservationWithOneStallBooking(Instant.now().minus(1, ChronoUnit.MINUTES));

        kafkaTemplate.send("payment-completed-topic", reservation.getId().toString(),
                new PaymentCompletedEvent(reservation.getId(), "pi_integration_test_2", BigDecimal.valueOf(150)));

        // Give the consumer time to process, then assert it did NOT confirm.
        await().pollDelay(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Reservation reloaded = reservationRepository.findById(reservation.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(ReservationStatus.PENDING);
            assertThat(reloaded.getQrCodePayload()).isNull();
        });
    }
}
