package com.bookfair.backend.dto.organization.mapper;

import java.time.Instant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.bookfair.backend.dto.common.SimpleOrganizationDto;
import com.bookfair.backend.dto.config.GlobalMapperConfig;
import com.bookfair.backend.dto.organization.request.CreateOrganizationRequest;
import com.bookfair.backend.dto.organization.request.InviteRequest;
import com.bookfair.backend.dto.organization.request.UpdateOrganizationRequest;
import com.bookfair.backend.dto.organization.response.OrganizationResponse;
import com.bookfair.backend.dto.organization.response.PublicOrganizationResponse;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.OrganizationInvite;
import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.User;
import com.bookfair.backend.model.enums.OrganizationRole;

@Mapper(config = GlobalMapperConfig.class)
public interface OrganizationMapper {
    OrganizationResponse toOrganizationResponse(Organization organization);

    PublicOrganizationResponse toPublicOrganizationResponse(Organization organization);

    SimpleOrganizationDto toSimpleOrganizationDto(Organization organization);

    Organization toOrganizationFromCreateOrganizationRequest(CreateOrganizationRequest request);

    Organization updateOrganizationFromOrganizationRequest(UpdateOrganizationRequest request, @MappingTarget Organization organization);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "verified", constant = "false")
    @Mapping(target = "name", source = "dto.name")
    @Mapping(target = "capabilities", source = "dto.capabilities")
    @Mapping(target = "contactNumber", source = "dto.contactNumber")
    @Mapping(target = "billingAddress", source = "dto.billingAddress")
    @Mapping(target = "contactEmail", source = "dto.contactEmail")
    @Mapping(target = "registrationNumber", source = "dto.registrationNumber")
    @Mapping(target = "employees", ignore = true)
    @Mapping(target = "ownedVenues", ignore = true)
    @Mapping(target = "partnerVenues", ignore = true)
    @Mapping(target = "deletionAudit", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Organization toOrganizationFromRegisterRequest(CreateOrganizationRequest dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "organization", source = "organization")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "active", constant = "true")
    OrganizationMember toOrganizationMember(User user, Organization organization, OrganizationRole role);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "used", constant = "false")
    @Mapping(target = "organization", source = "organization")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "assignedRole", source = "request.role")
    @Mapping(target = "token", source = "token")
    @Mapping(target = "expiresAt", source = "expiresAt")
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    OrganizationInvite toOrganizationInvite(Organization organization, InviteRequest request, String token, Instant expiresAt);
}

