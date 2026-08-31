package com.bookfair.backend.dto.organization.request;

import java.util.Set;
import com.bookfair.backend.model.enums.OrganizationCapability;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record UpdateOrganizationRequest(
    @NotBlank(message = "Organization name is required")
    String name,

    @NotBlank(message = "Contact number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$", message = "Invalid contact number format")
    String contactNumber,

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String contactEmail,

    @NotBlank(message = "Address is required")
    String billingAddress,

    @NotEmpty(message = "At least one capability is required")
    Set<OrganizationCapability> capabilities
) {}
