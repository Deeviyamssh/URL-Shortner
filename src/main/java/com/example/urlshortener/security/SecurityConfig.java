package com.example.urlshortener.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    public SecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (for simplicity, in production you would enable it and handle it properly)
            .csrf(csrf -> csrf.disable())
            // Disable form login
            .formLogin(form -> form.disable())
            // Disable HTTP Basic
            .httpBasic(httpBasic -> httpBasic.disable())
            // Add our API key authentication filter before BasicAuthenticationFilter (or at the start)
            .addFilterBefore(apiKeyAuthenticationFilter, BasicAuthenticationFilter.class)
            // Authorize requests
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/actuator/**").permitAll()

                        .requestMatchers("/api/**").access((authenticationSupplier, context) -> {
                            Authentication authentication = authenticationSupplier.get();

                            if (authentication == null) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }

                            if (!authentication.isAuthenticated()) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }

                            Object principal = authentication.getPrincipal();

                            if (principal instanceof String && "anonymousUser".equals(principal)) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }

                            return new org.springframework.security.authorization.AuthorizationDecision(true);
                        })

                        // Short URLs must be publicly accessible
                        .requestMatchers("/{shortCode}").permitAll()

                        // Frontend static assets must be publicly accessible
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/favicon.svg",
                                "/vite.svg", "/assets/**", "/*.js", "/*.css", "/*.map").permitAll()

                        .anyRequest().denyAll()
                )
            // Session management: we don't use sessions for API key authentication
            .sessionManagement(session -> session.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS));

        return http.build();
    }
}