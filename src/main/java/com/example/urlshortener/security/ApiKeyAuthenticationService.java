package com.example.urlshortener.security;

import com.example.urlshortener.entity.ApiKey;
import com.example.urlshortener.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ApiKeyAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationService.class);

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthenticationService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    public ApiKey authenticate(String accessKey, String secretKey) {
        log.debug("Authenticating access key: [{}]", accessKey);

        if (accessKey == null || secretKey == null) {
            log.debug("accessKey or secretKey is null");
            return null;
        }

        ApiKey apiKey = apiKeyRepository.findByAccessKey(accessKey);

        if (apiKey == null) {
            log.debug("API key not found for access key: [{}]", accessKey);
            return null;
        }

        log.debug("API key found for access key: [{}]", accessKey);
        log.debug("owner={}, expired={}", apiKey.getOwner(), apiKey.isExpired());

        if (apiKey.isExpired()) {
            log.debug("API key expired for access key: [{}]", accessKey);
            return null;
        }

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        boolean matches = passwordEncoder.matches(secretKey, apiKey.getHashedKey());

        log.debug("BCrypt matches={} for access key: [{}]", matches, accessKey);

        if (matches) {
            log.debug("Authentication SUCCESS for access key: [{}]", accessKey);
            return apiKey;
        }

        log.debug("Authentication FAILED - BCrypt mismatch for access key: [{}]", accessKey);
        return null;
    }
}
