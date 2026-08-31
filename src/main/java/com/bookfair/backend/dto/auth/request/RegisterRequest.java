package com.bookfair.backend.dto.auth.request;


import com.bookfair.backend.dto.organization.request.CreateOrganizationRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record RegisterRequest(
    @NotBlank(message = "Username is required")
    String username,

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @Pattern(regexp = "^[+0-9 ()-]{7,25}$", message = "Invalid contact number format")
    String contactNumber,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    String password,

    @Valid
    CreateOrganizationRequest organizationDetails
) {}
