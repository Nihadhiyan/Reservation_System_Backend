package com.bookfair.backend.security.keycloak;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.bookfair.backend.config.KeycloakProperties;
import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.UnauthorizedException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Thin client for the parts of Keycloak our backend talks to directly:
 * exchanging credentials/refresh tokens for a session (Resource Owner
 * Password Credentials grant — deliberately kept server-side so the
 * frontend's existing login form never changes), ending a session, and
 * provisioning/updating user credentials via the Admin REST API.
 *
 * Inbound request authentication (validating a Bearer token already issued
 * by Keycloak) is handled separately by Spring's OAuth2 resource server
 * support — this class is only for the outbound calls our own backend makes
 * to Keycloak's token and admin endpoints.
 */
@org.springframework.stereotype.Service
@Slf4j
public class KeycloakIdentityService {

    private final KeycloakProperties keycloakProperties;
    private final RestClient restClient = RestClient.create();

    public KeycloakIdentityService(KeycloakProperties keycloakProperties) {
        this.keycloakProperties = keycloakProperties;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn) {
    }

    public TokenResponse passwordGrant(String username, String password) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "password");
        form.add("scope", "openid");
        form.add("username", username);
        form.add("password", password);
        return requestToken(form, "Invalid username or password");
    }

    public TokenResponse refreshGrant(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return requestToken(form, "Refresh token is expired, invalid, or has been revoked");
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        MultiValueMap<String, String> form = baseForm();
        form.add("refresh_token", refreshToken);
        try {
            restClient.post()
                    .uri(keycloakProperties.logoutEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            // Logout is best-effort from the caller's perspective — the frontend
            // discards its tokens either way, and our own jti blacklist (see
            // AuthService.logout) already revokes the access token immediately.
            // A failed Keycloak-side session teardown just means that refresh
            // token lingers until its natural expiry instead of being revoked early.
            log.warn("Keycloak logout call failed (non-fatal): {}", e.getMessage());
        }
    }

    /** Creates the Keycloak-side credential store for a newly registered local user. */
    public void createUser(String username, String email, String password) {
        Map<String, Object> body = Map.of(
                "username", username,
                "email", email,
                "enabled", true,
                "emailVerified", false,
                // Our own User entity has no first/last name fields — this app only
                // collects a username — but Keycloak's default realm User Profile
                // schema marks firstName/lastName as required. Leaving them unset
                // makes Keycloak dynamically attach a VERIFY_PROFILE requirement
                // that silently rejects every password-grant login with
                // "Account is not fully set up" (confirmed against a real Keycloak
                // instance), since that required action can only be resolved through
                // Keycloak's own hosted UI, which this app's login flow never visits.
                "firstName", username,
                "lastName", "User",
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false)));

        try {
            restClient.post()
                    .uri(keycloakProperties.adminUsersEndpoint())
                    .header("Authorization", "Bearer " + adminAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            log.error("Keycloak user provisioning failed for [{}]: {} {}", username, e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new BusinessException("Unable to provision identity account. Please try again later.",
                    ErrorCode.SERVICE_UNAVAILABLE);
        } catch (RestClientException e) {
            log.error("Keycloak unreachable while provisioning user [{}]: {}", username, e.getMessage());
            throw new BusinessException("Identity service is temporarily unavailable. Please try again later.",
                    ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** Overwrites a user's Keycloak-side password to keep it in sync with our local hash. */
    public void updateUserPassword(String username, String newPassword) {
        String userId = findUserIdByUsername(username);
        Map<String, Object> body = Map.of(
                "type", "password",
                "value", newPassword,
                "temporary", false);

        try {
            restClient.put()
                    .uri(keycloakProperties.adminUsersEndpoint() + "/" + userId + "/reset-password")
                    .header("Authorization", "Bearer " + adminAccessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Keycloak password sync failed for [{}]: {}", username, e.getMessage());
            throw new BusinessException("Unable to update identity credentials. Please try again later.",
                    ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private String findUserIdByUsername(String username) {
        try {
            List<Map<String, Object>> results = restClient.get()
                    .uri(URI.create(keycloakProperties.adminUsersEndpoint()
                            + "?username=" + username + "&exact=true"))
                    .header("Authorization", "Bearer " + adminAccessToken())
                    .retrieve()
                    .body(List.class);
            if (results == null || results.isEmpty()) {
                throw new BusinessException("Identity account not found for user", ErrorCode.SERVICE_UNAVAILABLE);
            }
            return (String) results.get(0).get("id");
        } catch (RestClientException e) {
            log.error("Keycloak user lookup failed for [{}]: {}", username, e.getMessage());
            throw new BusinessException("Identity service is temporarily unavailable. Please try again later.",
                    ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** Client-credentials grant: authenticates as the backend's own service account for Admin API calls. */
    private String adminAccessToken() {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "client_credentials");
        TokenResponse response = requestToken(form, "Backend service account rejected by Keycloak");
        return response.accessToken();
    }

    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", keycloakProperties.getClientId());
        form.add("client_secret", keycloakProperties.getClientSecret());
        return form;
    }

    private TokenResponse requestToken(MultiValueMap<String, String> form, String unauthorizedMessage) {
        try {
            return restClient.post()
                    .uri(keycloakProperties.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new UnauthorizedException(unauthorizedMessage, ErrorCode.UNAUTHORIZED);
                    })
                    .body(TokenResponse.class);
        } catch (UnauthorizedException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("Keycloak token endpoint unreachable: {}", e.getMessage());
            throw new BusinessException("Identity service is temporarily unavailable. Please try again later.",
                    ErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
