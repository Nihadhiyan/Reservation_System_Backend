package com.bookfair.backend.dto.venue.response;

import java.util.List;
import java.util.UUID;

import com.bookfair.backend.dto.common.SimpleOrganizationDto;

public record VenueResponse(
    UUID id,
    String name,
    String description,
    String address,
    String city,
    String country,
    String postalCode,
    String contactNumber,
    String email,
    String website,
    Double latitude,
    Double longitude,
    String googlePlaceId,
    String mapImageUrl,
    String blueprintImageUrl,
    String premiseId,
    Double totalSquareFootage,
    Boolean parkingAvailable,
    Boolean foodCourtAvailable,
    SimpleOrganizationDto owner,
    List<SimpleOrganizationDto> partners,
    Boolean verified,
    Boolean active
) {}
