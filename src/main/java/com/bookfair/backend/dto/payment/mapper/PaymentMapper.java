package com.bookfair.backend.dto.payment.mapper;

import java.math.BigDecimal;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.payment.response.PaymentResponse;
import com.bookfair.backend.dto.payment.response.PaymentSummaryResponse;
import com.bookfair.backend.model.Payment;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.enums.CurrencyCode;

@Mapper(config = GlobalMapperConfig.class)
public interface PaymentMapper {
    PaymentResponse toPaymentResponse(Payment payment);

    PaymentSummaryResponse toPaymentSummaryResponse(Payment payment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "reservation", source = "reservation")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "transactionId", source = "transactionId")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "paymentGateway", source = "gatewayType", qualifiedByName = "toPaymentGateway")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toPayment(Reservation reservation, BigDecimal amount, String transactionId, CurrencyCode currency, String gatewayType);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reservation", source = "reservation")
    @Mapping(target = "transactionId", source = "transactionId")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "paymentGateway", source = "gatewayType", qualifiedByName = "toPaymentGateway")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Payment toWebhookPayment(Reservation reservation, String transactionId, BigDecimal amount, CurrencyCode currency, String gatewayType);

    @org.mapstruct.Named("toPaymentGateway")
    default com.bookfair.backend.model.enums.PaymentGateway toPaymentGateway(String gatewayType) {
        return com.bookfair.backend.model.enums.PaymentGateway.valueOf(gatewayType.toUpperCase());
    }
}

