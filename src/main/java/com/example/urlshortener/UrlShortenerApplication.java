package com.example.urlshortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the URL shortener application.
 *
 * This class is annotated with {@code @SpringBootApplication} which is a convenience annotation that adds:
 * - {@code @Configuration}: Tags the class as a source of bean definitions for the application context.
 * - {@code @EnableAutoConfiguration}: Tells Spring Boot to start adding beans based on classpath settings, other beans, and various property settings.
 * - {@code @ComponentScan}: Tells Spring to look for other components, configurations, and services in the {@code com/example/urlshortener} package, letting it find the controllers, services, and repositories.
 *
 * The {@code main} method uses Spring Boot's {@code SpringApplication.run()} method to launch the application.
 *
 * Did you know? This approach eliminates the need for a traditional web.xml file. Spring Boot uses an embedded servlet container ( Tomcat, Jetty, or Undertow) and configures it automatically based on the dependencies.
 *
 * This demonstrates the principle of "convention over configuration" and the Spring Boot philosophy of making it easy to create stand-alone, production-grade Spring based applications.
 */
@SpringBootApplication
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
    }

}