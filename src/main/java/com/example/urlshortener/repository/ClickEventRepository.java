package com.example.urlshortener.repository;

import com.example.urlshortener.entity.ClickEvent;
import com.example.urlshortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByUrlMappingOrderByClickedAtDesc(UrlMapping urlMapping);

    long countByUrlMapping(UrlMapping urlMapping);

    // Scoped to a list of URL mappings (owner-filtered global analytics)
    List<ClickEvent> findByUrlMappingInOrderByClickedAtDesc(List<UrlMapping> urlMappings);

    List<ClickEvent> findByUrlMappingInAndClickedAtBetweenOrderByClickedAtDesc(
            List<UrlMapping> urlMappings, LocalDateTime startTime, LocalDateTime endTime);

    List<ClickEvent> findByUrlMappingInAndIpAddressOrderByClickedAtDesc(
            List<UrlMapping> urlMappings, String ipAddress);

    List<ClickEvent> findByUrlMappingInAndClickedAtBetweenAndIpAddressOrderByClickedAtDesc(
            List<UrlMapping> urlMappings, LocalDateTime startTime, LocalDateTime endTime, String ipAddress);

    // Legacy unscoped queries (kept for backward compatibility)
    List<ClickEvent> findByClickedAtBetweenAndIpAddressOrderByClickedAtDesc(
            LocalDateTime startTime, LocalDateTime endTime, String ipAddress);

    List<ClickEvent> findByClickedAtBetweenOrderByClickedAtDesc(
            LocalDateTime startTime, LocalDateTime endTime);

    List<ClickEvent> findByIpAddressOrderByClickedAtDesc(String ipAddress);

    List<ClickEvent> findAllByOrderByClickedAtDesc();
}
