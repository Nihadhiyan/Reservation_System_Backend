package com.bookfair.backend.config;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Data
@Component
@Validated
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    @Valid
    private final Api api = new Api();
    @Valid
    private final Webhook webhook = new Webhook();
    @Valid
    private final Checkout checkout = new Checkout();

    @Data
    public static class Api {

        @NotBlank(message = "Stripe API Key is missing!")
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private String key;
    }

    @Data
    public static class Webhook {

        @NotBlank(message = "Stripe Webhook Secret is missing!")
        @ToString.Exclude
        @EqualsAndHashCode.Exclude
        private String secret;
    }

    @Data
    public static class Checkout {

        @NotBlank(message = "Stripe checkout success URL is missing!")
        private String successUrl;

        @NotBlank(message = "Stripe checkout cancel URL is missing!")
        private String cancelUrl;
    }
}
