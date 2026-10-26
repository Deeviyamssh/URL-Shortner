package com.example.urlshortener.config;

import com.example.urlshortener.ratelimit.RateLimiterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiterInterceptor rateLimiterInterceptor;

    public WebConfig(RateLimiterInterceptor rateLimiterInterceptor) {
        this.rateLimiterInterceptor = rateLimiterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimiterInterceptor)
                .addPathPatterns("/api/**", "/{shortCode}")
                .excludePathPatterns("/actuator/**", "/api/auth/register", "/", "/index.html", "/favicon.ico",
                        "/favicon.svg", "/vite.svg", "/assets/**", "/*.js", "/*.css", "/*.map");
    }
}