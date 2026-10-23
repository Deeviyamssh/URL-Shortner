package com.example.urlshortener.controller;

import com.example.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class RedirectController {

    private final UrlService urlService;

    public RedirectController(UrlService urlService) {
        this.urlService = urlService;
    }

    // Only match single-segment paths without dots (excludes static files like index.html, favicon.ico)
    @GetMapping("/{shortCode:[^.]+}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        String originalUrl = urlService.getOriginalUrl(shortCode);
        if (originalUrl == null) {
            return ResponseEntity.notFound().build();
        }

        // Extract request details for click recording
        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String referrer = request.getHeader("Referer");

        // Record the click (asynchronous via message queue)
        urlService.recordClick(shortCode, ipAddress, userAgent, referrer);

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(java.net.URI.create(originalUrl));
        return ResponseEntity.status(302).headers(headers).build();
    }

}