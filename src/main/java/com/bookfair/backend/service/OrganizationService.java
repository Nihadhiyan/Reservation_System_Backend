package com.bookfair.backend.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookfair.backend.dto.organization.mapper.OrganizationMapper;
import com.bookfair.backend.dto.organization.request.CreateOrganizationRequest;
import com.bookfair.backend.dto.organization.request.UpdateOrganizationRequest;
import com.bookfair.backend.dto.organization.response.OrganizationMemberResponse;
import com.bookfair.backend.dto.organization.response.OrganizationResponse;
import com.bookfair.backend.dto.organization.response.PublicOrganizationResponse;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.DuplicateResourceException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.ResourceNotFoundException;
import com.bookfair.backend.model.DeletionAudit;
import com.bookfair.backend.model.Organization;
import com.bookfair.backend.model.enums.OrganizationCapability;
import com.bookfair.backend.model.User;
import com.bookfair.backend.repository.OrganizationRepository;
import com.bookfair.backend.repository.UserRepository;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.event.organization.OrganizationCreatedEvent;
import com.bookfair.backend.event.organization.OrganizationDeactivatedEvent;
import com.bookfair.backend.event.organization.OrganizationCapabilityChangedEvent;
import com.bookfair.backend.event.cache.OrganizationUpdatedEvent;
import com.bookfair.backend.event.audit.SecurityAuditEvent;
import com.bookfair.backend.util.SecurityUtils;
import static java.util.Objects.requireNonNull;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationMapper organizationMapper;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SecurityUtils securityUtils;

    // Cache organization lists by page parameters to optimize read-heavy directory
    // queries
    @Cacheable(value = "organizationList", key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort")
    @Transactional(readOnly = true)
    public Page<PublicOrganizationResponse> getAllOrganizations(Pageable pageable) {
        requireNonNull(pageable, "pageable cannot be null");
        return organizationRepository.findAllByActiveTrue(pageable)
                .map(organizationMapper::toPublicOrganizationResponse);
    }

    // Cache organization profile lookups by ID
    @Cacheable(value = "organization", key = "#id")
    @Transactional(readOnly = true)
    public PublicOrganizationResponse getOrganizationById(UUID id) {
        Organization organization = organizationRepository.findByIdAndActiveTrue(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                        ErrorCode.ORGANIZATION_NOT_FOUND));

        return organizationMapper.toPublicOrganizationResponse(organization);
    }

    @Cacheable(value = "userOrganizations", key = "#userId")
    @Transactional(readOnly = true)
    public List<OrganizationResponse> getMyOrganizations(UUID userId) {
        return memberRepository.findByUserIdWithOrganizations(userId).stream()
                .map(om -> om.getOrganization())
                .map(organizationMapper::toOrganizationResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationMemberResponse> getMembers(UUID orgId) {
        requireNonNull(orgId, "orgId cannot be null");
        organizationRepository.findByIdAndActiveTrue(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                        ErrorCode.ORGANIZATION_NOT_FOUND));
        return memberRepository.findByOrganizationIdAndActiveTrue(orgId).stream()
                .map(organizationMapper::toOrganizationMemberResponse)
                .toList();
    }

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        requireNonNull(request, "request cannot be null");
        if (organizationRepository.existsByNameAndActiveTrue(requireNonNull(request.name()))) {
            throw new DuplicateResourceException("An organization with this name already exists.",
                    ErrorCode.DUPLICATE_ORGANIZATION_NAME);
        }

        if (organizationRepository.existsByRegistrationNumberAndActiveTrue(request.registrationNumber())) {
            throw new DuplicateResourceException(
                    "An organization with this registration number already exists.",
                    ErrorCode.DUPLICATE_REGISTRATION_NUMBER);
        }

        Organization organization = organizationMapper.toOrganizationFromCreateOrganizationRequest(request);
        Organization savedOrganization = organizationRepository.save(requireNonNull(organization));

        // Publish event to trigger AFTER_COMMIT cache eviction
        applicationEventPublisher.publishEvent(new OrganizationCreatedEvent(savedOrganization.getId()));

        return organizationMapper.toOrganizationResponse(savedOrganization);
    }

    @Transactional
    public OrganizationResponse updateOrganization(UUID id, UpdateOrganizationRequest request) {
        requireNonNull(request, "request cannot be null");
        Organization organization = organizationRepository.findByIdAndActiveTrue(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                        ErrorCode.ORGANIZATION_NOT_FOUND));

        if (!organization.getName().equalsIgnoreCase(request.name()) &&
                organizationRepository.existsByNameAndActiveTrue(requireNonNull(request.name()))) {
            throw new DuplicateResourceException("An organization with this name already exists.",
                    ErrorCode.DUPLICATE_ORGANIZATION_NAME);
        }

        Set<OrganizationCapability> oldCapabilities = new HashSet<>(organization.getCapabilities());

        organizationMapper.updateOrganizationFromOrganizationRequest(request, organization);
        Organization updatedOrganization = organizationRepository.save(organization);

        if (!oldCapabilities.equals(organization.getCapabilities())) {
            applicationEventPublisher.publishEvent(
                    new OrganizationCapabilityChangedEvent(
                            requireNonNull(updatedOrganization.getId()),
                            requireNonNull(updatedOrganization.getCapabilities())));
        }

        // Publish event to trigger AFTER_COMMIT cache eviction
        applicationEventPublisher.publishEvent(new OrganizationUpdatedEvent(updatedOrganization.getId()));

        return organizationMapper.toOrganizationResponse(updatedOrganization);
    }

    @Transactional
    public void deactivateOrganization(UUID id) {
        Organization organization = organizationRepository.findByIdAndActiveTrue(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                        ErrorCode.ORGANIZATION_NOT_FOUND));

        // Authorization is enforced by @orgAuth.isOrgAdmin at the controller — not re-checked
        // here to avoid two independently-maintained implementations of the same rule.
        User requestingUser = getCurrentUser();

        softDelete(organization);

        publishOrganizationDeactivatedEvent(organization.getId());
        applicationEventPublisher.publishEvent(new SecurityAuditEvent("DEACTIVATE_ORGANIZATION",
                requestingUser.getUsername(),
                "Deactivated organization: " + organization.getName(), Instant.now()));
    }

    @Transactional
    public void verifyOrganization(UUID id) {
        Organization organization = organizationRepository.findByIdAndActiveTrue(requireNonNull(id))
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found",
                        ErrorCode.ORGANIZATION_NOT_FOUND));

        if (Boolean.TRUE.equals(organization.getVerified())) {
            throw new BusinessException("Organization is already verified.", ErrorCode.BUSINESS_RULE_VIOLATION);
        }

        organization.setVerified(true);
        organizationRepository.save(organization);

        // Publish event to trigger AFTER_COMMIT cache eviction
        applicationEventPublisher.publishEvent(new OrganizationUpdatedEvent(organization.getId()));
    }

    private User getCurrentUser() {
        return userRepository.findById(requireNonNull(securityUtils.getCurrentUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found", ErrorCode.USER_NOT_FOUND));
    }

    private void softDelete(Organization organization) {
        organization.setActive(false);
        organization.setDeletionAudit(new DeletionAudit(Instant.now(), requireNonNull(securityUtils.getCurrentUserId())));
        organizationRepository.save(organization);
    }

    private void publishOrganizationDeactivatedEvent(UUID organizationId) {
        applicationEventPublisher.publishEvent(
                new OrganizationDeactivatedEvent(requireNonNull(organizationId), requireNonNull(securityUtils.getCurrentUserId())));
    }
}
