package com.bookfair.backend.dto.settlement.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.EventSettlement;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.TransactionHistory;
import com.bookfair.backend.model.enums.RentType;
import com.bookfair.backend.model.enums.SettlementStatus;
import com.bookfair.backend.model.enums.TransactionRole;

import java.math.BigDecimal;

@Mapper(config = GlobalMapperConfig.class)
public interface SettlementMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", source = "event")
    @Mapping(target = "organizer", source = "event.organizer")
    @Mapping(target = "venueOwner", source = "event.venue.owner")
    @Mapping(target = "snapshottedDailyRentRate", source = "snapshottedDailyRentRate")
    @Mapping(target = "snapshottedRentType", source = "snapshottedRentType")
    @Mapping(target = "totalRentOwed", source = "totalRent")
    @Mapping(target = "remainingBalance", source = "totalRent")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "amountPaidToOwner", ignore = true)
    @Mapping(target = "organizerProfit", ignore = true)
    EventSettlement toEventSettlement(Event event, BigDecimal snapshottedDailyRentRate, RentType snapshottedRentType, BigDecimal totalRent, SettlementStatus status);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "event", source = "event")
    @Mapping(target = "amount", source = "amount")
    @Mapping(target = "sourceRole", source = "sourceRole")
    @Mapping(target = "destinationRole", source = "destinationRole")
    @Mapping(target = "reservation", source = "reservation")
    @Mapping(target = "description", source = "description")
    TransactionHistory toTransactionHistory(Event event, BigDecimal amount, TransactionRole sourceRole, TransactionRole destinationRole, Reservation reservation, String description);
}
