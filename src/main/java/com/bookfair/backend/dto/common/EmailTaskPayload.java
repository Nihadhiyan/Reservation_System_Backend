package com.bookfair.backend.dto.common;

import java.util.Map;

public record EmailTaskPayload(
    String to,
    String subject,
    String templateName,
    Map<String, Object> variables,
    String qrBase64
) {}
