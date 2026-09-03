package com.bookfair.backend.security.keycloak;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import com.bookfair.backend.model.OrganizationMember;
import com.bookfair.backend.model.User;
import com.bookfair.backend.repository.OrganizationMemberRepository;
import com.bookfair.backend.repository.UserRepository;
import com.bookfair.backend.service.TokenBlacklistService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final OrganizationMemberRepository memberRepository;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidBearerTokenException("Token is missing the required email claim");
        }

        User user = userRepository.findByEmailAndActiveTrue(email)
                .orElseThrow(() -> new InvalidBearerTokenException(
                        "No active local account is associated with this identity"));

        String jti = jwt.getId();
        if (jti != null && tokenBlacklistService.isAccessTokenBlacklisted(jti)) {
            throw new InvalidBearerTokenException("Token has been revoked");
        }

        Long checkpointEpochSeconds = tokenBlacklistService.getSecurityCheckpoint(user.getId());
        if (checkpointEpochSeconds != null && jwt.getIssuedAt() != null
                && jwt.getIssuedAt().getEpochSecond() < checkpointEpochSeconds) {
            throw new InvalidBearerTokenException("Session invalidated by a recent security event");
        }

        return new UsernamePasswordAuthenticationToken(user.getId(), jwt, buildAuthorities(user));
    }

    private List<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getSystemRole().name()));

        List<OrganizationMember> members = memberRepository.findByUserIdWithOrganizations(user.getId());

        // de-duplication rationale as the code this replaces: a user with the
        // same org role (e.g. ORG_ADMIN) across multiple organizations should only
        // get one blanket ROLE_ authority, not one per organization.
        Set<String> grantedRoleAuthorities = new HashSet<>();
        for (OrganizationMember member : members) {
            String orgId = member.getOrganization().getId().toString();
            String role = member.getRole().name();

            authorities.add(new SimpleGrantedAuthority("ORG_" + orgId + "_" + role));

            String roleAuthority = "ROLE_" + role;
            if (grantedRoleAuthorities.add(roleAuthority)) {
                authorities.add(new SimpleGrantedAuthority(roleAuthority));
            }
        }

        return authorities;
    }
}
