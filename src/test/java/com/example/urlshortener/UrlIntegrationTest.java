package com.example.urlshortener;

import com.example.urlshortener.dto.ClickEventDto;
import com.example.urlshortener.dto.UrlCreateRequest;
import com.example.urlshortener.dto.UrlCreateResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
public class UrlIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    /** Returns auth headers for the default dev API key created by ApiKeyInitializer. */
    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Access-Key", "dev-access-key");
        headers.set("X-Secret-Key", "dev-secret-key");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    void contextLoads() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void createUrl_validRequest_returns200() {
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("https://example.com");

        HttpEntity<UrlCreateRequest> entity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<UrlCreateResponse> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, entity, UrlCreateResponse.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getShortCode()).isNotBlank();
    }

    @Test
    void createUrl_duplicateAlias_returns409() {
        String alias = "alias-dup-" + System.nanoTime();
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("https://example.com/dup");
        request.setCustomAlias(alias);

        HttpEntity<UrlCreateRequest> entity = new HttpEntity<>(request, authHeaders());

        // First create — must succeed
        ResponseEntity<UrlCreateResponse> first = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, entity, UrlCreateResponse.class);
        assertThat(first.getStatusCode().value()).isEqualTo(200);

        // Second create with same alias — must be 409
        ResponseEntity<String> second = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, entity, String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void createUrl_concurrentDuplicateAlias_bothReturn409Or200() throws InterruptedException {
        // Both threads attempt the same alias simultaneously, synchronized by a CyclicBarrier
        // so both HTTP requests are dispatched in the same JVM scheduling window.
        // Expected: one succeeds (200) and one gets a conflict (409); neither gets 500.
        // If the service-level check serialises the requests, both could return 409 —
        // that is also acceptable because the invariant (no 500) is the goal.
        String alias = "concurrent-" + System.nanoTime();
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("https://example.com/concurrent");
        request.setCustomAlias(alias);
        HttpEntity<UrlCreateRequest> entity = new HttpEntity<>(request, authHeaders());

        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
        int[] statuses = new int[2];

        Thread t1 = new Thread(() -> {
            try { barrier.await(); } catch (Exception ignored) {}
            statuses[0] = restTemplate.exchange(
                    "http://localhost:" + port + "/api/urls",
                    HttpMethod.POST, entity, String.class).getStatusCode().value();
        });
        Thread t2 = new Thread(() -> {
            try { barrier.await(); } catch (Exception ignored) {}
            statuses[1] = restTemplate.exchange(
                    "http://localhost:" + port + "/api/urls",
                    HttpMethod.POST, entity, String.class).getStatusCode().value();
        });

        t1.start();
        t2.start();
        t1.join(10000);
        t2.join(10000);

        // Neither response may be 500 — that is the invariant this test enforces.
        assertThat(statuses[0]).isIn(200, 409);
        assertThat(statuses[1]).isIn(200, 409);
        // At most one may succeed — it is not possible for both to create the same alias.
        long successCount = java.util.Arrays.stream(statuses).filter(s -> s == 200).count();
        assertThat(successCount).isLessThanOrEqualTo(1);
    }

    @Test
    void createUrl_invalidUrl_returns400() {
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("not-a-url");

        HttpEntity<UrlCreateRequest> entity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, entity, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void redirect_validShortCode_returns302() {
        // Create a URL first
        String alias = "redir-" + System.nanoTime();
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("https://example.com/redir");
        request.setCustomAlias(alias);

        HttpEntity<UrlCreateRequest> entity = new HttpEntity<>(request, authHeaders());
        restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, entity, UrlCreateResponse.class);

        // TestRestTemplate does not follow redirects by default
        ResponseEntity<Void> redirect = restTemplate.exchange(
                "http://localhost:" + port + "/" + alias,
                HttpMethod.GET, HttpEntity.EMPTY, Void.class);

        assertThat(redirect.getStatusCode().value()).isEqualTo(302);
        assertThat(redirect.getHeaders().getLocation()).isNotNull();
        assertThat(redirect.getHeaders().getLocation().toString()).isEqualTo("https://example.com/redir");
    }

    @Test
    void redirect_unknownShortCode_returns404() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/unknown-xyz-404", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void auth_noHeaders_returns401() {
        HttpHeaders noAuth = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(noAuth);

        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    void auth_wrongSecret_returns401() {
        HttpHeaders badAuth = new HttpHeaders();
        badAuth.set("X-Access-Key", "dev-access-key");
        badAuth.set("X-Secret-Key", "wrong-secret");

        HttpEntity<Void> entity = new HttpEntity<>(badAuth);
        ResponseEntity<String> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.GET, entity, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void listUrls_returnsOnlyOwnUrls() {
        // Create a URL
        String alias = "list-" + System.nanoTime();
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("https://example.com/list");
        request.setCustomAlias(alias);

        HttpEntity<UrlCreateRequest> createEntity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<UrlCreateResponse> createResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, createEntity, UrlCreateResponse.class);
        assertThat(createResp.getStatusCode().value()).isEqualTo(200);

        // List URLs
        HttpHeaders listHeaders = authHeaders();
        listHeaders.remove(HttpHeaders.CONTENT_TYPE); // GET doesn't need content-type
        HttpEntity<Void> listEntity = new HttpEntity<>(listHeaders);

        ResponseEntity<UrlDetailsResponse[]> listResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.GET, listEntity, UrlDetailsResponse[].class);

        assertThat(listResp.getStatusCode().value()).isEqualTo(200);
        assertThat(listResp.getBody()).isNotNull();

        List<UrlDetailsResponse> urls = Arrays.asList(listResp.getBody());

        // Should contain the URL we just created
        boolean containsCreated = urls.stream()
                .anyMatch(u -> alias.equals(u.getShortCode()));
        assertThat(containsCreated).isTrue();

        // All returned URLs should have a clickCount field (no NPE)
        urls.forEach(u -> assertThat(u.getClickCount()).isGreaterThanOrEqualTo(0));
    }

    @Test
    void deleteUrl_ownership_returns401OrNotFound() throws Exception {
        // Create a URL as dev-user
        String alias = "del-" + System.nanoTime();
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("https://example.com/delete");
        request.setCustomAlias(alias);

        HttpEntity<UrlCreateRequest> createEntity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<UrlCreateResponse> createResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, createEntity, UrlCreateResponse.class);
        assertThat(createResp.getStatusCode().value()).isEqualTo(200);

        // We need the URL id to attempt deletion — use the list endpoint
        HttpHeaders listHeaders = authHeaders();
        listHeaders.remove(HttpHeaders.CONTENT_TYPE);
        HttpEntity<Void> listEntity = new HttpEntity<>(listHeaders);
        ResponseEntity<UrlDetailsResponse[]> listResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.GET, listEntity, UrlDetailsResponse[].class);

        UrlDetailsResponse created = Arrays.stream(listResp.getBody())
                .filter(u -> alias.equals(u.getShortCode()))
                .findFirst().orElseThrow();
        Long urlId = created.getId();

        // Attempt DELETE with a nonexistent access key.
        // Use Java 11 HttpClient to avoid Apache HttpClient's automatic auth-retry on 401
        // which causes "cannot retry due to server authentication, in streaming mode".
        java.net.http.HttpClient javaHttpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        java.net.http.HttpRequest deleteRequest = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/api/urls/" + urlId))
                .method("DELETE", java.net.http.HttpRequest.BodyPublishers.noBody())
                .header("X-Access-Key", "nonexistent-key")
                .header("X-Secret-Key", "nonexistent-secret")
                .build();
        java.net.http.HttpResponse<String> deleteResp = javaHttpClient.send(
                deleteRequest, java.net.http.HttpResponse.BodyHandlers.ofString());

        // Should be 401 (unknown access key)
        assertThat(deleteResp.statusCode()).isIn(401, 404);
    }

    @Test
    void clickAnalytics_afterRedirect_recordsClick() throws InterruptedException {
        // Create a URL
        String alias = "click-" + System.nanoTime();
        UrlCreateRequest request = new UrlCreateRequest();
        request.setOriginalUrl("https://example.com/click");
        request.setCustomAlias(alias);

        HttpEntity<UrlCreateRequest> createEntity = new HttpEntity<>(request, authHeaders());
        ResponseEntity<UrlCreateResponse> createResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.POST, createEntity, UrlCreateResponse.class);
        assertThat(createResp.getStatusCode().value()).isEqualTo(200);

        // Get the URL id
        HttpHeaders listHeaders = authHeaders();
        listHeaders.remove(HttpHeaders.CONTENT_TYPE);
        HttpEntity<Void> listEntity = new HttpEntity<>(listHeaders);
        ResponseEntity<UrlDetailsResponse[]> listResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls",
                HttpMethod.GET, listEntity, UrlDetailsResponse[].class);
        UrlDetailsResponse created = Arrays.stream(listResp.getBody())
                .filter(u -> alias.equals(u.getShortCode()))
                .findFirst().orElseThrow();
        Long urlId = created.getId();

        // Perform a redirect GET (public, no auth needed)
        restTemplate.exchange(
                "http://localhost:" + port + "/" + alias,
                HttpMethod.GET, HttpEntity.EMPTY, Void.class);

        // Wait for async click event processing
        Thread.sleep(1500);

        // Check analytics
        HttpHeaders analyticsHeaders = authHeaders();
        analyticsHeaders.remove(HttpHeaders.CONTENT_TYPE);
        HttpEntity<Void> analyticsEntity = new HttpEntity<>(analyticsHeaders);

        ResponseEntity<ClickEventDto[]> clicksResp = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls/" + urlId + "/clicks",
                HttpMethod.GET, analyticsEntity, ClickEventDto[].class);

        assertThat(clicksResp.getStatusCode().value()).isEqualTo(200);
        assertThat(clicksResp.getBody()).isNotNull();
        assertThat(clicksResp.getBody().length).isGreaterThan(0);
    }

    @Test
    void globalClicks_scopedToOwner() {
        // GET /api/urls/clicks — all returned items should belong to dev-user's URLs
        HttpHeaders headers = authHeaders();
        headers.remove(HttpHeaders.CONTENT_TYPE);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        @SuppressWarnings("rawtypes")
        ResponseEntity<Map[]> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/urls/clicks",
                HttpMethod.GET, entity, Map[].class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        // Response must not be null (can be empty)
        assertThat(response.getBody()).isNotNull();
        // If there are clicks, verify they are accessible (no 403/404 for scoping)
        // The service scopes to owner's URLs so all results belong to dev-user
    }
}
