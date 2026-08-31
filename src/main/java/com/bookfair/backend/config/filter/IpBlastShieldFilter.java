package com.bookfair.backend.config.filter;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.bookfair.backend.config.AppProperties;
import com.bookfair.backend.exception.ErrorCode;
import com.bookfair.backend.exception.TooManyRequestsException;
import com.bookfair.backend.service.RateLimitingService;
import com.bookfair.backend.util.RequestUtils;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class IpBlastShieldFilter extends OncePerRequestFilter{

    private final RateLimitingService rateLimitingService;
    private final AppProperties appProperties;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public IpBlastShieldFilter(
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
        
        String ipAddress = RequestUtils.getClientIpAddress(request);

        boolean isAllowed = rateLimitingService.isAllowed("ip:" + ipAddress, appProperties.getSecurity().getIpRateLimitRequestsPerMinute(), appProperties.getSecurity().getIpRateLimitTimeWindowSeconds());

        if (!isAllowed) {
            handlerExceptionResolver.resolveException(request, response, null, new TooManyRequestsException("Too many requests. Please try again later.", ErrorCode.TOO_MANY_REQUESTS));

            log.warn("IP Address {} is rate limited", ipAddress);

            return;
        }

        filterChain.doFilter(request, response);
    }
    
}
