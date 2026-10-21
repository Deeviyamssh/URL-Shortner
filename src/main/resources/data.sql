-- This SQL file is executed on startup if the platform supports it.
-- We rely on Hibernate's schema generation (spring.jpa.hibernate.ddl-auto=update) for creating tables.
-- You can add additional SQL statements here for constraints, indexes, or initial data.

-- Create sequence for generating unique numbers for short codes
CREATE SEQUENCE IF NOT EXISTS url_short_code_seq START WITH 1 INCREMENT BY 1;

-- Create table for URL mappings (if not already created by Hibernate)
-- We rely on Hibernate's DDL generation, but we can add constraints here.
-- Note: If Hibernate creates the table first, this statement may fail if the table already exists.
-- In practice, we let Hibernate handle the schema and use this for additional constraints.

-- Add a unique constraint on (url_id, clickedAt, ipAddress) in the click_events table
-- to prevent duplicate click events from the same IP at the same timestamp.
-- We do this via ALTER TABLE if the table exists.
DO $$
BEGIN
   IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                  WHERE constraint_name = 'uk_click_events_url_id_clicked_at_ipaddress'
                    AND table_name = 'click_events') THEN
      ALTER TABLE click_events
      ADD CONSTRAINT uk_click_events_url_id_clicked_at_ipaddress
      UNIQUE (url_id, clicked_at, ip_address);
   END IF;
END $$;

-- Example: Add a comment on tables
-- COMMENT ON TABLE url_mappings IS 'Stores the mapping between short codes and original URLs';
-- COMMENT ON TABLE click_events IS 'Stores individual click events for analytics';
-- COMMENT ON TABLE api_keys IS 'Stores API key information for authentication';

-- Example: Create an index for faster lookup of click events by url_id and clicked_at
-- CREATE INDEX idx_click_events_url_id_clicked_at ON click_events (url_id, clicked_at);