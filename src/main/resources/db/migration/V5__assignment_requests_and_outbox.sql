CREATE TABLE assignment_request (
    operation_id VARCHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(36) NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    envio_id VARCHAR(36) NOT NULL,
    repartidor_id VARCHAR(36) NOT NULL,
    status VARCHAR(12) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    reason VARCHAR(255),
    INDEX idx_assignment_request_envio_status (envio_id, status)
);

CREATE TABLE outbox_events (
    event_id VARCHAR(36) PRIMARY KEY,
    operation_id VARCHAR(36) NOT NULL UNIQUE,
    routing_key VARCHAR(100) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_outbox_assignment FOREIGN KEY (operation_id) REFERENCES assignment_request(operation_id),
    INDEX idx_outbox_events_pending (status, occurred_at, event_id)
);
