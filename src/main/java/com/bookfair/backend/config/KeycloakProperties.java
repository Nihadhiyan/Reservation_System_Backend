package com.bookfair.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

    // Container-internal URL (compose service name) — used for server-to-server
    // calls (token exchange, admin API). Never sent to the browser.
    @NotBlank(message = "Keycloak server URL is missing!")
    private String serverUrl;

    @NotBlank(message = "Keycloak realm is missing!")
    private String realm;

    @NotBlank(message = "Keycloak client id is missing!")
    private String clientId;

    @NotBlank(message = "Keycloak client secret is missing!")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private String clientSecret;

    public String tokenEndpoint() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
    }

    public String logoutEndpoint() {
        return serverUrl + "/realms/" + realm + "/protocol/openid-connect/logout";
    }

    public String adminUsersEndpoint() {
        return serverUrl + "/admin/realms/" + realm + "/users";
    }
}
