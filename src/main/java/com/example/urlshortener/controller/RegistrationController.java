package com.example.urlshortener.controller;

import com.example.urlshortener.dto.RegistrationRequest;
import com.example.urlshortener.dto.RegistrationResponse;
import com.example.urlshortener.service.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public registration endpoint — does not require authentication.
 * POST /api/auth/register
 *
 * Returns:
 *   201 — registration successful (body contains the access key, shown once)
 *   400 — validation failure (handled by GlobalExceptionHandler)
 *   409 — username already taken (handled by GlobalExceptionHandler)
 *   429 — registration rate limit exceeded
 *   503 — registration temporarily unavailable (Redis down)
 */
@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody RegistrationRequest request,
            HttpServletRequest httpRequest) {
        try {
            RegistrationResponse response = registrationService.register(request, httpRequest);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            // Rate limit exceeded (429) or Redis unavailable (503)
            String msg = e.getMessage();
            boolean rateLimited = msg != null && msg.contains("Too many");
            HttpStatus status = rateLimited ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.SERVICE_UNAVAILABLE;
            return ResponseEntity.status(status).body(Map.of(
                    "error", rateLimited ? "Too Many Requests" : "Service Unavailable",
                    "message", msg
            ));
        }
        // IllegalArgumentException (duplicate username) propagates to GlobalExceptionHandler → 409
        // MethodArgumentNotValidException (validation) propagates to GlobalExceptionHandler → 400
    }
}
