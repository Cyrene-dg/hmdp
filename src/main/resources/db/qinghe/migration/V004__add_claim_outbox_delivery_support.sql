-- WP-04 adds observable, multi-instance outbox publication state.
-- It remains additive and does not alter legacy hmdp tables.

ALTER TABLE qh_outbox_event
    ADD COLUMN lease_owner VARCHAR(64) NULL AFTER lease_until,
    ADD COLUMN last_error VARCHAR(512) NULL AFTER lease_owner,
    ADD COLUMN published_at DATETIME(3) NULL AFTER last_error;

CREATE TABLE qh_outbox_delivery_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(40) NOT NULL,
    attempt_no INT NOT NULL,
    outcome VARCHAR(24) NOT NULL,
    error_message VARCHAR(512) NULL,
    attempted_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_outbox_attempt_event_no (event_id, attempt_no),
    KEY idx_qh_outbox_attempt_time (attempted_at),
    CONSTRAINT fk_qh_outbox_attempt_event FOREIGN KEY (event_id)
        REFERENCES qh_outbox_event (event_id),
    CONSTRAINT ck_qh_outbox_attempt_positive CHECK (attempt_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
