-- Flyway Migration V1: Initial Schema
-- This migration creates the initial schema for the URL Shortener application

-- Create sequence for generating unique numbers for short codes
CREATE SEQUENCE IF NOT EXISTS url_short_code_seq START WITH 1 INCREMENT BY 1;

-- Create table for API keys
CREATE TABLE api_keys (
    id BIGSERIAL PRIMARY KEY,
    owner VARCHAR(255) NOT NULL UNIQUE,
    access_key VARCHAR(255) NOT NULL UNIQUE,
    hashed_secret VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NULL
);

-- Create table for URL mappings
CREATE TABLE url_mappings (
    id BIGSERIAL PRIMARY KEY,
    original_url VARCHAR(2048) NOT NULL,
    short_code VARCHAR(255) NOT NULL UNIQUE,
    api_key_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    CONSTRAINT fk_url_mappings_api_key FOREIGN KEY (api_key_id) REFERENCES api_keys(id)
);

-- Create table for click events
CREATE TABLE click_events (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    url_id BIGINT NOT NULL,
    clicked_at TIMESTAMP NOT NULL,
    ip_address VARCHAR(45) NOT NULL, -- IPv6 address length
    user_agent VARCHAR(500) NOT NULL,
    referrer VARCHAR(500) NOT NULL,
    CONSTRAINT fk_click_events_url_mapping FOREIGN KEY (url_id) REFERENCES url_mappings(id)
);

-- Create indexes for performance
CREATE INDEX idx_url_mappings_api_key_created_at ON url_mappings(api_key_id, created_at DESC);
CREATE INDEX idx_click_events_url_id_clicked_at ON click_events(url_id, clicked_at DESC);