-- V3: Relax NOT NULL constraints on optional click_event columns and add event_id for idempotency.
-- V1 created click_events with ip_address, user_agent, referrer as NOT NULL.
-- These may be missing from browser requests; relax them.
-- event_id is added as optional UUID for idempotent retry deduplication.

ALTER TABLE click_events ALTER COLUMN ip_address DROP NOT NULL;
ALTER TABLE click_events ALTER COLUMN user_agent DROP NOT NULL;
ALTER TABLE click_events ALTER COLUMN referrer DROP NOT NULL;

-- event_id was NOT NULL UNIQUE in V1; make it optional for rows inserted before this feature.
ALTER TABLE click_events ALTER COLUMN event_id DROP NOT NULL;

-- Ensure a partial unique index exists (handles rows where event_id IS NOT NULL).
-- Use IF NOT EXISTS to be safe if V1 already created a unique constraint.
DROP INDEX IF EXISTS idx_click_events_event_id;
CREATE UNIQUE INDEX IF NOT EXISTS idx_click_events_event_id ON click_events(event_id) WHERE event_id IS NOT NULL;
