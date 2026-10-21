package com.example.urlshortener.service;

import com.example.urlshortener.dto.UrlCreateRequest;
import com.example.urlshortener.dto.UrlCreateResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.dto.ClickEventDto;
import com.example.urlshortener.entity.ApiKey;
import com.example.urlshortener.entity.ClickEvent;
import com.example.urlshortener.entity.UrlMapping;
import com.example.urlshortener.repository.ApiKeyRepository;
import com.example.urlshortener.repository.ClickEventRepository;
import com.example.urlshortener.repository.UrlMappingRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UrlService {

    private final UrlMappingRepository urlMappingRepository;
    private final CounterService counterService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ClickEventPublisher clickEventPublisher;
    private final ClickEventRepository clickEventRepository;

    @Value("${url.cache.prefix:url:}")
    private String cachePrefix;

    @Value("${url.cache.ttl:3600}")
    private long cacheTtlSeconds;

    public UrlService(UrlMappingRepository urlMappingRepository, CounterService counterService, RedisTemplate<String, String> redisTemplate, ClickEventPublisher clickEventPublisher, ClickEventRepository clickEventRepository) {
        this.urlMappingRepository = urlMappingRepository;
        this.counterService = counterService;
        this.redisTemplate = redisTemplate;
        this.clickEventPublisher = clickEventPublisher;
        this.clickEventRepository = clickEventRepository;
    }

    /**
     * Creates a new URL mapping.
     *
     * This method demonstrates several concepts:
     *
     * 1. **Transaction Management**:
     *    - The method is annotated with {@code @Transactional} to ensure that the
     *      creation of the URL mapping is atomic. If any error occurs after the
     *      database write, the transaction will be rolled back.
     *    - This is crucial for maintaining data consistency, especially when
     *      we later add related operations (e.g., creating an initial click event).
     *
     * 2. **Uniqueness Validation**:
     *    - We check for the existence of a custom alias (if provided) or the
     *      generated short code to prevent duplicate short codes.
     *    - This check is performed within the transaction to avoid race conditions
     *      where two transactions might simultaneously check for non-existence
     *      and then both try to insert the same short code.
     *    - Although the probability of conflict is low for randomly generated
     *      codes, it is a good practice to validate uniqueness at the service
     *      layer before attempting to insert.
     *
     * 3. **Separation of Concerns**:
     *    - The service layer handles business logic (validation, short code
     *      generation, transaction boundaries) while the repository handles
     *      data access.
     *    - This separation makes the code easier to test and maintain.
     *
     * 4. **Defensive Programming**:
     *    - We validate input parameters (via Bean Validation on the DTO) and
     *      handle edge cases like null or empty values.
     *    - This prevents invalid data from entering our system and makes
     *      error messages clear to the client.
     *
     * 5. **Audit Trail**:
     *    - We record the creator of the URL mapping (if provided) and the
     *      creation timestamp. This allows for auditing and ownership tracking.
     *    - In a production system, this information could be used for
     *      rate limiting, analytics, or compliance purposes.
     *
     * 6. **Cache-Aside Pattern (Lazy Loading)**:
     *    - Although not directly applied in this method (as we are writing),
     *      we note that when a URL is created, we could pre-populate the cache
     *      to avoid a cache miss on the first read. However, we choose to
     *      populate the cache lazily on read to keep the write path simple.
     *    - This demonstrates the trade-off between write complexity and read
     *      performance.
     */
    @Transactional
    public UrlCreateResponse createUrlMapping(UrlCreateRequest request, String createdBy) {
        String originalUrl = request.getOriginalUrl();
        String customAlias = request.getCustomAlias();

        String shortCode;
        if (customAlias != null && !customAlias.isBlank()) {
            // Use the custom alias provided by the user
            shortCode = customAlias.trim();
            // Check if the custom alias is already taken
            if (urlMappingRepository.existsByShortCode(shortCode)) {
                throw new IllegalArgumentException("Custom alias '" + shortCode + "' is already in use");
            }
        } else {
            // Generate a short code using our counter-based service
            shortCode = counterService.generateShortCode();
        }

        // Create and save the URL mapping entity
        UrlMapping urlMapping = new UrlMapping();
        urlMapping.setOriginalUrl(originalUrl);
        urlMapping.setShortCode(shortCode);
        urlMapping.setCreatedAt(LocalDateTime.now());
        urlMapping.setCreatedBy(createdBy); // May be null for anonymous URLs

        UrlMapping saved = urlMappingRepository.save(urlMapping);

        // Prepare the response DTO
        UrlCreateResponse response = new UrlCreateResponse();
        response.setShortCode(saved.getShortCode());
        response.setOriginalUrl(saved.getOriginalUrl());
        response.setCreatedBy(saved.getCreatedBy());

        return response;
    }

    /**
     * Retrieves the details of a URL mapping by its ID, ensuring that it belongs to the specified owner.
     *
     * @param id   the ID of the URL mapping
     * @param owner the owner of the URL mapping (for authorization)
     * @return the URL details if found and owned by the specified owner, null otherwise
     */
    public UrlDetailsResponse getUrlDetails(Long id, String owner) {
        UrlMapping urlMapping = urlMappingRepository.findById(id).orElse(null);
        if (urlMapping == null) {
            return null;
        }
        // Check ownership
        if (!StringUtils.equals(urlMapping.getCreatedBy(), owner)) {
            return null;
        }
        // Map to DTO
        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setId(urlMapping.getId());
        response.setOriginalUrl(urlMapping.getOriginalUrl());
        response.setShortCode(urlMapping.getShortCode());
        response.setCreatedAt(urlMapping.getCreatedAt());
        response.setUpdatedAt(urlMapping.getUpdatedAt());
        response.setCreatedBy(urlMapping.getCreatedBy());
        response.setClickCount(clickEventRepository.countByUrlMapping(urlMapping));
        return response;
    }

    /**
     * Retrieves the original URL for a given short code, using a cache-aside pattern.
     *
     * This method demonstrates the **Cache-Aside (Look-Aside) Pattern**:
     *
     * 1. **Check Cache**: First, we look for the short code in Redis (O(1) time complexity).
     * 2. **Cache Miss**: If not found in Redis, we query the database.
     * 3. **Populate Cache**: If found in the database, we store the result in Redis with a TTL.
     * 4. **Return Result**: We return the original URL to the caller.
     *
     * Why Cache-Aside?
     *    - It is simple to implement and decouples the caching logic from the data access layer.
     *    - The application is responsible for populating the cache, giving us full control
     *      over what gets cached and when.
     *    - It handles cache misses gracefully by falling back to the database.
     *
     * Cache TTL (Time-To-Live):
     *    - We set a TTL of 1 hour (configurable) for cached URL mappings.
     *    - This ensures that stale data is eventually removed from the cache.
     *    - The choice of TTL depends on the expected access patterns (hot data).
     *    - For a URL shortener, we expect a few URLs to be heavily accessed (following the
     *      Pareto principle), so a longer TTL is acceptable for those hot URLs.
     *    - We do not cache negative results (i.e., when a short code is not found) to
     *      avoid caching errors. Instead, we let each miss result in a database query.
     *    - This approach assumes that the database is fast enough to handle occasional
     *      cache misses, which is reasonable given our indexing strategy.
     *
     * Redis Data Types:
     *    - We use the Redis String data type to store the mapping from short code to original URL.
     *    - The key is constructed as `{prefix}{shortCode}` (e.g., "url:abc123").
     *    - This design allows us to easily scan or delete keys by prefix if needed.
     *
     * Cache Invalidation:
     *    - When a URL mapping is deleted (via the delete operation), we must
     *      remove the corresponding key from Redis to prevent serving stale data.
     *    - This is an example of explicit cache invalidation to maintain cache
     *      coherency.
     *    - We do not rely on the TTL alone for invalidation because we want
     *      to immediately remove the mapping upon deletion.
     */
    public String getOriginalUrl(String shortCode) {
        // Construct the Redis key
        String key = cachePrefix + shortCode;

        // 1. Check Redis (cache)
        String cachedUrl = redisTemplate.opsForValue().get(key);
        if (StringUtils.isNotEmpty(cachedUrl)) {
            // Cache hit: return the URL from Redis
            return cachedUrl;
        }

        // 2. Cache miss: query the database
        UrlMapping urlMapping = urlMappingRepository.findByShortCode(shortCode);
        if (urlMapping == null) {
            // Not found in database either; return null
            return null;
        }

        // 3. Populate cache (asynchronous would be better for performance, but we do it synchronously for simplicity)
        String originalUrl = urlMapping.getOriginalUrl();
        redisTemplate.opsForValue().set(key, originalUrl, cacheTtlSeconds, TimeUnit.SECONDS);

        // 4. Return the URL
        return originalUrl;
    }

    /**
     * Records a click event for the given short code with the provided request details.
     *
     * This method is intended to be called by the redirect controller after
     * retrieving the original URL. It publishes a click event to the message
     * queue for asynchronous processing.
     *
     * @param shortCode the short code that was accessed
     * @param ipAddress the IP address of the client
     * @param userAgent the user agent string of the client
     * @param referrer the referrer URL (if any)
     */
    public void recordClick(String shortCode, String ipAddress, String userAgent, String referrer) {
        // First, verify that the URL exists (optional)
        String originalUrl = getOriginalUrl(shortCode);
        if (originalUrl == null) {
            throw new IllegalArgumentException("URL not found for short code: " + shortCode);
        }
        // Publish the click event
        ClickEventDto clickEvent = new ClickEventDto();
        clickEvent.setShortCode(shortCode);
        clickEvent.setClickedAt(LocalDateTime.now());
        clickEvent.setIpAddress(ipAddress);
        clickEvent.setUserAgent(userAgent);
        clickEvent.setReferrer(referrer);
        clickEvent.setEventId(UUID.randomUUID());
        clickEventPublisher.publishClickEvent(clickEvent);
    }

    /**
     * Checks if a short code exists in the database (bypassing the cache).
     *
     * This method is used when we need to verify existence without considering
     * the cache (e.g., during creation to avoid duplicates).
     *
     * @param shortCode the short code to check
     * @return true if the short code exists, false otherwise
     */
    public boolean exists(String shortCode) {
        return urlMappingRepository.existsByShortCode(shortCode);
    }

    /**
     * Invalidates the cache for a given short code.
     *
     * This method is called when a URL mapping is deleted to ensure that
     * subsequent requests do not serve a stale cached value.
     *
     * @param shortCode the short code to invalidate
     */
    public void invalidateCache(String shortCode) {
        String key = cachePrefix + shortCode;
        redisTemplate.delete(key);
    }

    /**
     * Retrieves all URL mappings for a given owner.
     *
     * This method is used for listing URLs owned by a specific user.
     *
     * @param owner the owner of the URL mappings
     * @return list of URL details for the owner
     */
    @Transactional(readOnly = true)
    public List<UrlDetailsResponse> getUrlDetailsByOwner(String owner) {
        List<UrlMapping> urlMappings = urlMappingRepository.findByCreatedBy(owner);
        return urlMappings.stream()
                .map(this::mapToUrlDetailsResponse)
                .collect(Collectors.toList());
    }

    /**
     * Maps a UrlMapping entity to a UrlDetailsResponse DTO.
     *
     * @param urlMapping the entity to map
     * @return the DTO representation
     */
    private UrlDetailsResponse mapToUrlDetailsResponse(UrlMapping urlMapping) {
        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setId(urlMapping.getId());
        response.setOriginalUrl(urlMapping.getOriginalUrl());
        response.setShortCode(urlMapping.getShortCode());
        response.setCreatedAt(urlMapping.getCreatedAt());
        response.setUpdatedAt(urlMapping.getUpdatedAt());
        response.setCreatedBy(urlMapping.getCreatedBy());
        response.setClickCount(clickEventRepository.countByUrlMapping(urlMapping));
        return response;
    }

    /**
     * Updates an existing URL mapping.
     *
     * This method demonstrates:
     *
     * 1. **Transaction Management**: Ensures atomicity of the update operation.
     * 2. **Ownership Validation**: Verifies that the user owns the URL before allowing updates.
     * 3. **Cache Invalidation**: Removes the old mapping from Redis to prevent serving stale data.
     * 4. **Timestamp Management**: Updates the updatedAt field to track when the URL was last modified.
     *
     * @param id           the ID of the URL mapping to update
     * @param request      the updated URL information
     * @param owner        the owner of the URL mapping (for authorization)
     * @return             the updated URL mapping information, or null if not found or not owned
     */
    @Transactional
    public UrlDetailsResponse updateUrlMapping(Long id, UrlCreateRequest request, String owner) {
        // Find the existing URL mapping
        UrlMapping urlMapping = urlMappingRepository.findById(id).orElse(null);
        if (urlMapping == null) {
            return null;
        }

        // Check ownership
        if (!StringUtils.equals(urlMapping.getCreatedBy(), owner)) {
            return null;
        }

        String originalUrl = request.getOriginalUrl();
        String customAlias = request.getCustomAlias();

        // If a custom alias is provided and it's different from the current one, check if it's available
        if (customAlias != null && !customAlias.isBlank() && !StringUtils.equals(customAlias.trim(), urlMapping.getShortCode())) {
            String shortCode = customAlias.trim();
            if (urlMappingRepository.existsByShortCode(shortCode)) {
                throw new IllegalArgumentException("Custom alias '" + shortCode + "' is already in use");
            }
            // Update the short code and invalidate the old cache entry
            invalidateCache(urlMapping.getShortCode());
            urlMapping.setShortCode(shortCode);
        }

        // Update the original URL if it has changed
        if (!StringUtils.equals(originalUrl, urlMapping.getOriginalUrl())) {
            urlMapping.setOriginalUrl(originalUrl);
            // Invalidate the cache for the short code since the target URL has changed
            invalidateCache(urlMapping.getShortCode());
        }

        // Update the updatedAt timestamp
        urlMapping.setUpdatedAt(LocalDateTime.now());

        // Save the updated entity
        UrlMapping saved = urlMappingRepository.save(urlMapping);

        // Return the details
        UrlDetailsResponse response = new UrlDetailsResponse();
        response.setId(saved.getId());
        response.setOriginalUrl(saved.getOriginalUrl());
        response.setShortCode(saved.getShortCode());
        response.setCreatedAt(saved.getCreatedAt());
        response.setUpdatedAt(saved.getUpdatedAt());
        response.setCreatedBy(saved.getCreatedBy());
        response.setClickCount(clickEventRepository.countByUrlMapping(saved));

        return response;
    }

    /**
     * Deletes a URL mapping.
     *
     * This method demonstrates:
     *
     * 1. **Transaction Management**: Ensures atomicity of the delete operation.
     * 2. **Ownership Validation**: Verifies that the user owns the URL before allowing deletion.
     * 3. **Cache Invalidation**: Removes the mapping from Redis to prevent serving stale data.
     * 4. **Cascading Delete Considerations**: In a production system, we might want to
     *    preserve click events for analytics even after deleting the URL mapping.
     *
     * @param id   the ID of the URL mapping to delete
     * @param owner the owner of the URL mapping (for authorization)
     * @return     true if the URL was deleted, false if not found or not owned
     */
    @Transactional
    public boolean deleteUrlMapping(Long id, String owner) {
        // Find the existing URL mapping
        UrlMapping urlMapping = urlMappingRepository.findById(id).orElse(null);
        if (urlMapping == null) {
            return false;
        }

        // Check ownership
        if (!StringUtils.equals(urlMapping.getCreatedBy(), owner)) {
            return false;
        }

        // Invalidate the cache entry
        invalidateCache(urlMapping.getShortCode());

        // Delete the URL mapping
        // Note: We're not deleting associated click events to preserve analytics data
        // In a real system, you might want to archive or delete them based on data retention policies
        urlMappingRepository.delete(urlMapping);

        return true;
    }

    /**
     * Retrieves click events for a specific URL mapping.
     *
     * This method demonstrates:
     *
     * 1. **Read Operations**: Efficiently retrieving related entities using JPA.
     * 2. **Pagination Concepts**: In a real implementation, we would add pagination
     *    parameters to handle large numbers of click events.
     * 3. **Analytics Data**: Click events provide valuable analytics data for
     *    understanding URL usage patterns.
     *
     * @param id   the ID of the URL mapping
     * @param owner the owner of the URL mapping (for authorization)
     * @return     list of click events for the URL, or empty list if not found or not owned
     */
    @Transactional(readOnly = true)
    public List<ClickEventDto> getClickEventsForUrl(Long id, String owner) {
        // First check if the URL exists and the user owns it
        UrlMapping urlMapping = urlMappingRepository.findById(id).orElse(null);
        if (urlMapping == null || !StringUtils.equals(urlMapping.getCreatedBy(), owner)) {
            return Collections.emptyList();
        }

        // Retrieve click events for this URL mapping
        List<ClickEvent> clickEvents = clickEventRepository.findByUrlMappingOrderByClickedAtDesc(urlMapping);

        // Convert to DTOs
        return clickEvents.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves click events globally with optional filtering.
     *
     * This method demonstrates:
     *
     * 1. **Global Analytics**: Providing insights across all URLs in the system.
     * 2. **Filtering Capabilities**: Allowing filtering by time range, IP address, etc.
     * 3. **Performance Considerations**: In a production system, we would need
     *    proper indexing and potentially data warehousing for large-scale analytics.
     *
     * @param startTime   optional start time for filtering (inclusive)
     * @param endTime     optional end time for filtering (inclusive)
     * @param ipAddress   optional IP address to filter by
     * @param limit       maximum number of results to return
     * @return            list of click events matching the filters
     */
    @Transactional(readOnly = true)
    public List<ClickEventDto> getGlobalClickEvents(
            String owner,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String ipAddress,
            int limit) {

        // Scope all queries to the owner's URL mappings
        List<UrlMapping> ownerUrls = urlMappingRepository.findByCreatedBy(owner);
        if (ownerUrls.isEmpty()) {
            return Collections.emptyList();
        }

        List<ClickEvent> clickEvents;

        // Build query based on provided filters, scoped to owner's URLs
        if (startTime != null && endTime != null && ipAddress != null && !ipAddress.isEmpty()) {
            clickEvents = clickEventRepository.findByUrlMappingInAndClickedAtBetweenAndIpAddressOrderByClickedAtDesc(
                    ownerUrls, startTime, endTime, ipAddress);
        } else if (startTime != null && endTime != null) {
            clickEvents = clickEventRepository.findByUrlMappingInAndClickedAtBetweenOrderByClickedAtDesc(
                    ownerUrls, startTime, endTime);
        } else if (ipAddress != null && !ipAddress.isEmpty()) {
            clickEvents = clickEventRepository.findByUrlMappingInAndIpAddressOrderByClickedAtDesc(
                    ownerUrls, ipAddress);
        } else {
            clickEvents = clickEventRepository.findByUrlMappingInOrderByClickedAtDesc(ownerUrls);
        }

        // Apply limit if specified
        if (limit > 0 && clickEvents.size() > limit) {
            clickEvents = clickEvents.subList(0, limit);
        }

        // Convert to DTOs
        return clickEvents.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Converts a ClickEvent entity to a ClickEventDto.
     *
     * @param clickEvent the entity to convert
     * @return the DTO representation
     */
    private ClickEventDto convertToDto(ClickEvent clickEvent) {
        ClickEventDto dto = new ClickEventDto();
        dto.setShortCode(clickEvent.getUrlMapping().getShortCode());
        dto.setClickedAt(clickEvent.getClickedAt());
        dto.setIpAddress(clickEvent.getIpAddress());
        dto.setUserAgent(clickEvent.getUserAgent());
        dto.setReferrer(clickEvent.getReferrer());
        dto.setEventId(clickEvent.getEventId());
        return dto;
    }

}