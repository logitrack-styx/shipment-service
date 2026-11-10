ALTER TABLE outbox_events ADD COLUMN attempts INT NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN available_at TIMESTAMP(6) NULL;
ALTER TABLE outbox_events ADD COLUMN lease_token VARCHAR(36) NULL;
CREATE INDEX idx_outbox_available ON outbox_events(status, available_at, occurred_at);
