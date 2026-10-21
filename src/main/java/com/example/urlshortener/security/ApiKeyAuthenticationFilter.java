package com.example.urlshortener.security;

import com.example.urlshortener.entity.ApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.util.Collections;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final ApiKeyAuthenticationService apiKeyAuthenticationService;

    private final String accessKeyHeader;
    private final String secretKeyHeader;

    public ApiKeyAuthenticationFilter(ApiKeyAuthenticationService apiKeyAuthenticationService,
                                      @Value("${app.api-key.access-header:X-Access-Key}") String accessKeyHeader,
                                      @Value("${app.api-key.secret-header:X-Secret-Key}") String secretKeyHeader) {
        this.apiKeyAuthenticationService = apiKeyAuthenticationService;
        this.accessKeyHeader = accessKeyHeader;
        this.secretKeyHeader = secretKeyHeader;
        log.debug("ApiKeyAuthenticationFilter initialized. accessKeyHeader={}, secretKeyHeader={}",
                accessKeyHeader, secretKeyHeader);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        log.debug("ApiKeyAuthenticationFilter executing for path: {}", request.getRequestURI());

        String accessKey = request.getHeader(accessKeyHeader);
        String secretKey = request.getHeader(secretKeyHeader);

        log.debug("Access key header {} present: {}", accessKeyHeader, accessKey != null);
        log.debug("Secret key header {} present: {}", secretKeyHeader, secretKey != null);

        if (accessKey != null && secretKey != null) {
            log.debug("Attempting authentication for access key: {}", accessKey);
            ApiKey apiKey = apiKeyAuthenticationService.authenticate(accessKey, secretKey);
            if (apiKey != null) {
                log.debug("Authentication successful for access key: {}", accessKey);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        apiKey.getAccessKey(),
                        null,
                        Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authentication set in SecurityContext");
            } else {
                log.debug("Authentication failed for access key: {}", accessKey);
                response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=\"URL Shortener\"");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API Key");
                return;
            }
        } else {
            log.debug("No API key headers provided");
        }
        filterChain.doFilter(request, response);
    }
}
