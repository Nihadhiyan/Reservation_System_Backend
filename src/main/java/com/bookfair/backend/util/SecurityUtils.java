package com.bookfair.backend.util;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.bookfair.backend.exception.BusinessException;
import com.bookfair.backend.exception.ErrorCode;

@Component
public class SecurityUtils {
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID id) return id;
        if (auth != null && auth.getPrincipal() instanceof String s) return UUID.fromString(s);
        throw new BusinessException("Unable to resolve current user", ErrorCode.UNAUTHORIZED);
    }
}
