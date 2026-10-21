package com.example.urlshortener;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class GenerateApiKey {
    public static void main(String[] args) {
        String accessKey = "test-access-key";
        String secretKey = "test-secret-key";
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hashedKey = encoder.encode(secretKey);
        System.out.println("Access Key: " + accessKey);
        System.out.println("Secret Key: " + secretKey);
        System.out.println("Hashed Key: " + hashedKey);
        System.out.println();
        System.out.println("SQL INSERT statement:");
        System.out.printf("INSERT INTO api_keys (owner, access_key, hashed_key, created_at) VALUES ('%s', '%s', '%s', NOW());%n",
                "test-owner", accessKey, hashedKey);
    }
}