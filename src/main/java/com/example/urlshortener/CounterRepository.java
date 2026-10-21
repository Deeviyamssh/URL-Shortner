package com.example.urlshortener;

import com.example.urlshortener.entity.UrlMapping;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CounterRepository extends JpaRepository<UrlMapping, Long> {

    @Query(value = "SELECT nextval('url_short_code_seq')", nativeQuery = true)
    Long getNextShortCodeSequenceValue();
}