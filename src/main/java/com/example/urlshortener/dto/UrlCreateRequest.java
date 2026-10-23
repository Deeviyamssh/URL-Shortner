package com.example.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

public class UrlCreateRequest {

    @NotBlank(message = "URL is required")
    @Size(max = 2048, message = "URL must not exceed 2048 characters")
    @Pattern(
        regexp = "^(https?://).+",
        message = "URL must start with http:// or https://"
    )
    private String originalUrl;


    @Nullable
    @Pattern(
        regexp = "^[a-zA-Z0-9_-]+$",
        message = "Custom alias may only contain letters, numbers, hyphens, and underscores"
    )
    private String customAlias;

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getCustomAlias() {
        return customAlias;
    }

    public void setCustomAlias(String customAlias) {
        this.customAlias = customAlias;
    }
}
