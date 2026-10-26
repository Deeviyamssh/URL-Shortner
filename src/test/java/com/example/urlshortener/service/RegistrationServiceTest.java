package com.example.urlshortener.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the IP‑resolver logic in RegistrationService.
 * Does not start Spring Boot; uses mocks to verify the trusted‑proxy logic.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private HttpServletRequest request;

    @Test
    void resolveClientIp_untrustedRemote_ignoresXForwardedFor() {
        RegistrationService service = createServiceWithTrustedProxies("192.168.1.100", "10.0.0.1");
        when(request.getRemoteAddr()).thenReturn("203.0.113.5"); // untrusted

        String ip = service.resolveClientIp(request);
        assertThat(ip).isEqualTo("203.0.113.5"); // remote addr, not the spoofed header
        // X‑Forwarded‑For header must NOT be read
        verify(request, never()).getHeader("X-Forwarded-For");
    }

    @Test
    void resolveClientIp_trustedRemote_emptyHeader_returnsRemote() {
        RegistrationService service = createServiceWithTrustedProxies("192.168.1.100");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        String ip = service.resolveClientIp(request);
        assertThat(ip).isEqualTo("192.168.1.100");
        verify(request).getHeader("X-Forwarded-For");
    }

    @Test
    void resolveClientIp_trustedRemote_validXForwardedFor_returnsFirstClientIp() {
        RegistrationService service = createServiceWithTrustedProxies("192.168.1.100");
        when(request.getRemoteAddr()).thenReturn("192.168.1.100");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 192.168.1.100, 10.0.0.1");

        String ip = service.resolveClientIp(request);
        assertThat(ip).isEqualTo("203.0.113.10"); // first valid entry
        verify(request).getHeader("X-Forwarded-For");
    }

    @Test
    void resolveClientIp_trustedRemote_headerWithUnknownAndEmpty_skipsThem() {
        RegistrationService service = createServiceWithTrustedProxies("10.0.0.5");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown, , 203.0.113.20, 10.0.0.5");

        String ip = service.resolveClientIp(request);
        assertThat(ip).isEqualTo("203.0.113.20");
        verify(request).getHeader("X-Forwarded-For");
    }

    @Test
    void resolveClientIp_trustedRemote_allInvalid_returnsRemote() {
        RegistrationService service = createServiceWithTrustedProxies("10.0.0.5");
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");
        when(request.getHeader("X-Forwarded-For")).thenReturn("unknown, ,");

        String ip = service.resolveClientIp(request);
        assertThat(ip).isEqualTo("10.0.0.5");
        verify(request).getHeader("X-Forwarded-For");
    }

    @Test
    void resolveClientIp_noTrustedProxiesConfigured_alwaysIgnoresHeader() {
        RegistrationService service = createServiceWithTrustedProxies(); // empty list
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");

        String ip = service.resolveClientIp(request);
        assertThat(ip).isEqualTo("192.168.1.1");
        // Header must NOT be read because trustedProxyIps is empty
        verify(request, never()).getHeader("X-Forwarded-For");
    }

    private RegistrationService createServiceWithTrustedProxies(String... trustedIps) {
        RegistrationService service = new RegistrationService(null, null);
        // Use reflection to set the trustedProxyIps field
        try {
            var field = RegistrationService.class.getDeclaredField("trustedProxyIps");
            field.setAccessible(true);
            field.set(service, trustedIps.length == 0 ? List.of() : Arrays.asList(trustedIps));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return service;
    }
}