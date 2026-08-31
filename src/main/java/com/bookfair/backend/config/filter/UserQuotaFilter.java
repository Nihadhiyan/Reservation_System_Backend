package com.bookfair.backend.config.filter;

import org.springframework.security.core.Authentication;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.bookfair.backend.config.AppProperties;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.TooManyRequestsException;
import com.bookfair.backend.service.RateLimitingService;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UserQuotaFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final AppProperties appProperties;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public UserQuotaFilter(
        RateLimitingService rateLimitingService,
        AppProperties appProperties,
        @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.rateLimitingService = rateLimitingService;
        this.appProperties = appProperties;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if(authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
            boolean isAllowed = rateLimitingService.isAllowed("user:" + authentication.getName(), appProperties.getSecurity().getUserQuotaLimitRequestsPerMinute(), appProperties.getSecurity().getUserQuotaLimitTimeWindowSeconds());
            
            if(!isAllowed) {
                handlerExceptionResolver.resolveException(request, response, null, new TooManyRequestsException("Too many requests. Please try again later.", ErrorCode.TOO_MANY_REQUESTS));
                log.warn("User {} is rate limited", authentication.getName());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

}
