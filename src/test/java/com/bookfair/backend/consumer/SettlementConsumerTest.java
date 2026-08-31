package com.bookfair.backend.consumer;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.repository.ReservationRepository;
import com.bookfair.backend.service.SettlementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementConsumerTest {

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private SettlementService settlementService;

    @InjectMocks
    private SettlementConsumer settlementConsumer;

    @Test
    void processesSettlement_whenReservationExists() {
        UUID reservationId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(reservationId, "pi_test", BigDecimal.valueOf(200));
        Reservation reservation = new Reservation();
        reservation.setId(reservationId);
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));

        settlementConsumer.processSettlement(event);

        verify(settlementService).processVendorPayment(reservation, BigDecimal.valueOf(200));
    }

    @Test
    void doesNothing_whenReservationNotFound() {
        UUID reservationId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(reservationId, "pi_test", BigDecimal.valueOf(200));
        when(reservationRepository.findById(reservationId)).thenReturn(Optional.empty());

        settlementConsumer.processSettlement(event);

        verifyNoInteractions(settlementService);
    }
}
