-- WP-05 records message consumption and terminal failure handling without altering legacy tables.

CREATE TABLE qh_claim_issue_delivery (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(40) NOT NULL,
    claim_id BIGINT NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    processed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_claim_issue_event (event_id),
    KEY idx_qh_claim_issue_claim_time (claim_id, processed_at),
    CONSTRAINT fk_qh_claim_issue_claim FOREIGN KEY (claim_id) REFERENCES qh_claim_request (id),
    CONSTRAINT fk_qh_claim_issue_event FOREIGN KEY (event_id) REFERENCES qh_outbox_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

