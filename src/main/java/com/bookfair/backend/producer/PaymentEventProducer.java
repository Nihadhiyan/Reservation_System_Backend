package com.bookfair.backend.producer;

import com.bookfair.backend.event.payment.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${clausis.kafka.topics.payment-completed:payment-completed-topic}")
    private String paymentCompletedTopic;

    public void publishPaymentCompletedEvent(PaymentCompletedEvent event) {
        log.info("Publishing PaymentCompletedEvent to Kafka topic {}: {}", paymentCompletedTopic, event);
        kafkaTemplate.send(paymentCompletedTopic, event.reservationId().toString(), event);
    }
}
