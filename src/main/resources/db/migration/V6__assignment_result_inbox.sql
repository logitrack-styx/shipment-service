CREATE TABLE processed_assignment_results (
    event_id VARCHAR(36) PRIMARY KEY,
    operation_id VARCHAR(36) NOT NULL,
    CONSTRAINT fk_processed_assignment FOREIGN KEY (operation_id) REFERENCES assignment_request(operation_id)
);
