ALTER TABLE qh_redemption_reversal
    ADD COLUMN operator_no VARCHAR(32) NULL AFTER reason_code,
    ADD COLUMN reason_remark VARCHAR(256) NULL AFTER operator_no,
    ADD COLUMN first_processed_at DATETIME(3) NULL AFTER occurred_at,
    ADD COLUMN updated_at DATETIME(3) NULL AFTER created_at;

CREATE TABLE qh_pos_reversal_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pos_client_id VARCHAR(64) NOT NULL,
    pos_request_no VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    target_redemption_no VARCHAR(40) NOT NULL,
    store_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    redemption_id BIGINT NULL,
    reversal_id BIGINT NULL,
    failure_code VARCHAR(64) NULL,
    right_status VARCHAR(24) NULL,
    first_processed_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_reversal_request_client_no (pos_client_id, pos_request_no),
    KEY idx_qh_reversal_request_status_updated (status, updated_at),
    CONSTRAINT fk_qh_reversal_request_store FOREIGN KEY (store_id) REFERENCES qh_store (id),
    CONSTRAINT fk_qh_reversal_request_redemption FOREIGN KEY (redemption_id) REFERENCES qh_redemption (id),
    CONSTRAINT fk_qh_reversal_request_reversal FOREIGN KEY (reversal_id) REFERENCES qh_redemption_reversal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
