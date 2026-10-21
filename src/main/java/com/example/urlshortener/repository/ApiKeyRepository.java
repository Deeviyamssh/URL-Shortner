package com.example.urlshortener.repository;

import com.example.urlshortener.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    ApiKey findByOwner(String owner);

    ApiKey findByAccessKey(String accessKey);
}