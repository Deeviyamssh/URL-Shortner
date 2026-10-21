-- Align the initial schema with the current JPA model without rewriting V1 history.
ALTER TABLE api_keys RENAME COLUMN hashed_secret TO hashed_key;

-- Replace the numeric API-key FK with the owner key used by the existing entity/service model.
ALTER TABLE url_mappings ADD COLUMN created_by VARCHAR(255);
UPDATE url_mappings um
SET created_by = ak.owner
FROM api_keys ak
WHERE um.api_key_id = ak.id;
ALTER TABLE url_mappings ALTER COLUMN created_by SET NOT NULL;
ALTER TABLE url_mappings
    ADD CONSTRAINT fk_url_mappings_owner FOREIGN KEY (created_by) REFERENCES api_keys(owner);
DROP INDEX IF EXISTS idx_url_mappings_api_key_created_at;
ALTER TABLE url_mappings DROP CONSTRAINT IF EXISTS fk_url_mappings_api_key;
ALTER TABLE url_mappings DROP COLUMN api_key_id;
CREATE INDEX idx_url_mappings_created_by_created_at ON url_mappings(created_by, created_at DESC);

-- Preserve analytics consistency when a URL mapping is deleted.
ALTER TABLE click_events DROP CONSTRAINT IF EXISTS fk_click_events_url_mapping;
ALTER TABLE click_events
    ADD CONSTRAINT fk_click_events_url_mapping FOREIGN KEY (url_id) REFERENCES url_mappings(id) ON DELETE CASCADE;
