package com.bookfair.backend.dto.venue.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UpdateVenueRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Description is required")
    String description,

    @NotBlank(message = "Address is required")
    String address,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "Country is required")
    String country,

    @NotBlank(message = "Postal code is required")
    String postalCode,

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Contact number must be a valid phone number")
    String contactNumber,

    @Email(message = "Email must be valid")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Website is required")
    @Pattern(regexp = "^(https?://)?(www\\.)?([a-zA-Z0-9]+\\.)?[a-zA-Z0-9]+\\.[a-zA-Z]{2,}(/\\S*)?$", message = "Website must be a valid URL")
    String website,

    @NotBlank(message = "Map image url is required")
    String mapImageUrl,

    @NotBlank(message = "Blueprint image url is required")
    String blueprintImageUrl,

    @NotBlank(message = "Premise ID is required")
    String premiseId,

    @NotNull(message = "Total square footage is required")
    Double totalSquareFootage,

    @NotNull(message = "Parking available is required")
    Boolean parkingAvailable,

    @NotNull(message = "Food court available is required")
    Boolean foodCourtAvailable,

    @NotNull(message = "Owner organization ID is required")
    UUID ownerOrganizationId,

    List<UUID> partnerOrganizationIds,

    @NotNull(message = "Active is required")
    Boolean active
) {}
