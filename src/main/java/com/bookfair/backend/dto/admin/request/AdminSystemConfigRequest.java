package com.bookfair.backend.dto.admin.request;

import jakarta.validation.constraints.NotBlank;
public record AdminSystemConfigRequest(
    @NotBlank(message = "Config key cannot be blank")
    String configKey,

    @NotBlank(message = "Config value cannot be blank")
    String configValue
) {}
