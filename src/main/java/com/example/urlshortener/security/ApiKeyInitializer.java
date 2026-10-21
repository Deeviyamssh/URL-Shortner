package com.example.urlshortener.security;

import com.example.urlshortener.entity.ApiKey;
import com.example.urlshortener.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ApiKeyInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyInitializer.class);
    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyInitializer(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        // Check if any API key exists
        if (apiKeyRepository.count() == 0) {
            log.info("No API keys found. Creating a default API key for development.");

            // In a real application, you would generate these securely and store them safely.
            // For this educational project, we provide a default key.
            String owner = "dev-user";
            String accessKey = "dev-access-key";
            String secretKey = "dev-secret-key";

            // Hash the secret key using BCrypt (same as in ApiKeyAuthenticationService)
            org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                    new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
            String hashedKey = encoder.encode(secretKey);

            ApiKey apiKey = new ApiKey();
            apiKey.setOwner(owner);
            apiKey.setAccessKey(accessKey);
            apiKey.setHashedKey(hashedKey);
            apiKey.setCreatedAt(LocalDateTime.now());
            // No expiration for dev key

            apiKeyRepository.save(apiKey);

            log.info("Default API key created for development:");
            log.info("  Owner: {}", owner);
            log.info("  Access Key (header X-Access-Key): {}", accessKey);
            log.info("  Secret Key: set for development (see ApiKeyInitializer source). Not logged for security.");
            log.info("WARNING: This is for development only. Do not use in production.");
        } else {
            log.info("API keys already exist in the database. Skipping default key creation.");
        }
    }
}