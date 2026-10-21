package com.example.urlshortener.controller;

import jakarta.validation.Valid;
import com.example.urlshortener.dto.UrlCreateRequest;
import com.example.urlshortener.dto.UrlCreateResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.dto.ClickEventDto;
import com.example.urlshortener.entity.ApiKey;
import com.example.urlshortener.repository.ApiKeyRepository;
import com.example.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/urls")
public class UrlController {

    private final UrlService urlService;
    private final ApiKeyRepository apiKeyRepository;

    public UrlController(UrlService urlService, ApiKeyRepository apiKeyRepository) {
        this.urlService = urlService;
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Gets the authenticated user from the security context and returns their owner.
     *
     * @return the owner of the authenticated user, or "anonymous" if not authenticated
     */
    private String getAuthenticatedOwner() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String owner = null; // default: unauthenticated
        if (auth != null && auth.isAuthenticated()) {
            // The principal is the access key (from our authentication filter)
            String accessKey = auth.getName();
            ApiKey apiKey = apiKeyRepository.findByAccessKey(accessKey);
            if (apiKey != null) {
                owner = apiKey.getOwner();
            }
        }
        return owner;
    }

    @PostMapping
    public ResponseEntity<UrlCreateResponse> createUrl(
            @Valid @RequestBody UrlCreateRequest request) {
        String owner = getAuthenticatedOwner();
        UrlCreateResponse response = urlService.createUrlMapping(request, owner);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UrlDetailsResponse> getUrlDetails(@PathVariable Long id) {
        String owner = getAuthenticatedOwner();
        UrlDetailsResponse response = urlService.getUrlDetails(id, owner);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UrlDetailsResponse> updateUrl(
            @PathVariable Long id,
            @Valid @RequestBody UrlCreateRequest request) {
        String owner = getAuthenticatedOwner();
        UrlDetailsResponse response = urlService.updateUrlMapping(id, request, owner);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUrl(@PathVariable Long id) {
        String owner = getAuthenticatedOwner();
        boolean deleted = urlService.deleteUrlMapping(id, owner);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<UrlDetailsResponse>> listUrls(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String createdBy) {

        String owner = getAuthenticatedOwner();
        // For simplicity, we're only allowing users to see their own URLs
        // In a more advanced system, we might allow admin users to see all URLs
        // or apply different filtering based on roles

        // For now, we'll just get all URLs for the current user and manually paginate
        // In a production system, we would add pagination support to the service layer
        List<UrlDetailsResponse> allUrls = urlService.getUrlDetailsByOwner(owner);

        // Apply createdBy filter if provided (for admin-like functionality)
        if (createdBy != null && !createdBy.isEmpty()) {
            allUrls = allUrls.stream()
                    .filter(url -> StringUtils.equals(url.getCreatedBy(), createdBy))
                    .collect(Collectors.toList());
        }

        int start = Math.min(page * size, allUrls.size());
        int end = Math.min(start + size, allUrls.size());
        List<UrlDetailsResponse> pageContent = allUrls.subList(start, end);

        return ResponseEntity.ok(pageContent);
    }

    @GetMapping("/{id}/clicks")
    public ResponseEntity<List<ClickEventDto>> getUrlClicks(@PathVariable Long id) {
        String owner = getAuthenticatedOwner();
        List<ClickEventDto> clicks = urlService.getClickEventsForUrl(id, owner);
        return ResponseEntity.ok(clicks);
    }

    @GetMapping("/clicks")
    public ResponseEntity<List<ClickEventDto>> getGlobalClicks(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(defaultValue = "100") int limit) {

        String owner = getAuthenticatedOwner();
        List<ClickEventDto> clicks = urlService.getGlobalClickEvents(owner, startTime, endTime, ipAddress, limit);
        return ResponseEntity.ok(clicks);
    }
}