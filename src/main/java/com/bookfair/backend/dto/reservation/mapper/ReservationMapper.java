package com.bookfair.backend.dto.reservation.mapper;

import java.math.BigDecimal;
import java.time.Instant;

import org.mapstruct.Mapper;

import com.bookfair.backend.dto.common.SimpleEventDto;
import com.bookfair.backend.dto.common.SimpleStallDto;
import com.bookfair.backend.dto.common.SimpleUserDto;
import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.reservation.response.ReservationDetailResponse;
import com.bookfair.backend.dto.reservation.response.ReservationResponse;
import com.bookfair.backend.dto.reservation.response.ReservationSummaryResponse;
import com.bookfair.backend.model.Event;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.Reservation;
import com.bookfair.backend.model.EventSpaceBooking;
import com.bookfair.backend.model.Stall;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.Genre;

import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface ReservationMapper {

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    @Mapping(target = "totalAmount", source = "totalPrice")
    ReservationResponse toReservationResponse(Reservation reservation);

    @Mapping(target = "bookFair", source = "event")
    @Mapping(target = "totalAmount", source = "totalPrice")
    ReservationSummaryResponse toReservationSummaryResponse(Reservation reservation);

    @Mapping(target = "organizationId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    @Mapping(target = "totalAmount", source = "totalPrice")
    ReservationDetailResponse toReservationDetailResponse(Reservation reservation);

    SimpleUserDto toSimpleUserDto(User user);

    SimpleEventDto toSimpleEventDto(Event event);

    SimpleStallDto toSimpleStallDto(Stall stall);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "spaceBookings", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "organization", source = "organization")
    @Mapping(target = "event", source = "event")
    @Mapping(target = "genre", source = "genre")
    @Mapping(target = "reservationStartDateTime", source = "reservationStartDateTime")
    @Mapping(target = "expiresAt", source = "expiresAt")
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "qrCodePayload", ignore = true)
    Reservation toReservation(User user, Organization organization, Event event, Genre genre, Instant reservationStartDateTime, Instant expiresAt);
}
