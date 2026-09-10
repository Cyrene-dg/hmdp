ALTER TABLE qh_recon_batch
    MODIFY COLUMN provider VARCHAR(64) NOT NULL,
    ADD COLUMN recon_batch_no VARCHAR(40) NULL AFTER id,
    ADD COLUMN file_name VARCHAR(160) NULL AFTER checksum,
    ADD COLUMN schema_version VARCHAR(16) NULL AFTER file_name,
    ADD COLUMN checksum_algorithm VARCHAR(16) NULL AFTER schema_version,
    ADD COLUMN correction_of_batch_no VARCHAR(64) NULL AFTER checksum_algorithm,
    ADD COLUMN generated_at DATETIME(3) NULL AFTER correction_of_batch_no,
    ADD COLUMN imported_rows INT NOT NULL DEFAULT 0 AFTER total_rows,
    ADD COLUMN success_rows INT NOT NULL DEFAULT 0 AFTER imported_rows,
    ADD COLUMN matched_rows INT NOT NULL DEFAULT 0 AFTER error_rows,
    ADD COLUMN difference_rows INT NOT NULL DEFAULT 0 AFTER matched_rows,
    ADD COLUMN direct_matched_rows INT NOT NULL DEFAULT 0 AFTER difference_rows,
    ADD COLUMN franchise_eligible_rows INT NOT NULL DEFAULT 0 AFTER direct_matched_rows,
    ADD COLUMN next_line_no INT NOT NULL DEFAULT 2 AFTER franchise_eligible_rows,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER next_line_no,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER last_error_code,
    ADD COLUMN completed_at DATETIME(3) NULL AFTER version;

UPDATE qh_recon_batch
SET recon_batch_no = CONCAT('RCB-LEGACY-', LPAD(id, 12, '0')),
    file_name = CONCAT('LEGACY-', batch_no, '.csv'),
    schema_version = 'LEGACY',
    checksum_algorithm = 'SHA-256'
WHERE recon_batch_no IS NULL;

ALTER TABLE qh_recon_batch
    MODIFY COLUMN recon_batch_no VARCHAR(40) NOT NULL,
    MODIFY COLUMN file_name VARCHAR(160) NOT NULL,
    MODIFY COLUMN schema_version VARCHAR(16) NOT NULL,
    MODIFY COLUMN checksum_algorithm VARCHAR(16) NOT NULL,
    ADD UNIQUE KEY uq_qh_recon_batch_no (recon_batch_no),
    ADD KEY idx_qh_recon_correction (provider, correction_of_batch_no);

ALTER TABLE qh_recon_record
    ADD COLUMN store_code VARCHAR(32) NULL AFTER line_no,
    ADD COLUMN terminal_no VARCHAR(32) NULL AFTER store_code,
    ADD COLUMN pos_order_no VARCHAR(64) NULL AFTER terminal_no,
    ADD COLUMN right_code_hash CHAR(64) NULL AFTER redemption_no,
    ADD COLUMN match_reason VARCHAR(128) NULL AFTER match_status,
    ADD COLUMN matched_redemption_id BIGINT NULL AFTER match_reason,
    ADD COLUMN matched_reversal_id BIGINT NULL AFTER matched_redemption_id,
    ADD COLUMN store_ownership VARCHAR(24) NULL AFTER matched_reversal_id,
    ADD COLUMN settlement_eligible TINYINT(1) NOT NULL DEFAULT 0 AFTER store_ownership,
    ADD COLUMN matched_at DATETIME(3) NULL AFTER settlement_eligible,
    ADD KEY idx_qh_recon_record_match_status (batch_id, match_status),
    ADD KEY idx_qh_recon_record_matched_redemption (matched_redemption_id),
    ADD KEY idx_qh_recon_record_matched_reversal (matched_reversal_id),
    ADD CONSTRAINT fk_qh_recon_record_redemption FOREIGN KEY (matched_redemption_id)
        REFERENCES qh_redemption (id),
    ADD CONSTRAINT fk_qh_recon_record_reversal FOREIGN KEY (matched_reversal_id)
        REFERENCES qh_redemption_reversal (id);

CREATE TABLE qh_recon_import_chunk (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    chunk_no INT NOT NULL,
    first_line_no INT NOT NULL,
    last_line_no INT NOT NULL,
    row_count INT NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(64) NULL,
    completed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_recon_chunk_batch_no (batch_id, chunk_no),
    KEY idx_qh_recon_chunk_status (batch_id, status),
    CONSTRAINT fk_qh_recon_chunk_batch FOREIGN KEY (batch_id) REFERENCES qh_recon_batch (id),
    CONSTRAINT ck_qh_recon_chunk_range CHECK (
        chunk_no >= 0 AND first_line_no >= 2 AND last_line_no >= first_line_no
        AND row_count = last_line_no - first_line_no + 1
        AND success_rows >= 0 AND error_rows >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_recon_import_issue (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    chunk_no INT NOT NULL,
    line_no INT NOT NULL,
    error_code VARCHAR(64) NOT NULL,
    raw_digest CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_recon_issue_batch_line (batch_id, line_no),
    KEY idx_qh_recon_issue_batch_code (batch_id, error_code),
    CONSTRAINT fk_qh_recon_issue_batch FOREIGN KEY (batch_id) REFERENCES qh_recon_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_recon_difference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    record_id BIGINT NULL,
    redemption_id BIGINT NULL,
    reversal_id BIGINT NULL,
    difference_type VARCHAR(32) NOT NULL,
    business_key VARCHAR(160) NOT NULL,
    detail VARCHAR(512) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_recon_difference_key (batch_id, difference_type, business_key),
    KEY idx_qh_recon_difference_batch_type (batch_id, difference_type),
    CONSTRAINT fk_qh_recon_difference_batch FOREIGN KEY (batch_id) REFERENCES qh_recon_batch (id),
    CONSTRAINT fk_qh_recon_difference_record FOREIGN KEY (record_id) REFERENCES qh_recon_record (id),
    CONSTRAINT fk_qh_recon_difference_redemption FOREIGN KEY (redemption_id) REFERENCES qh_redemption (id),
    CONSTRAINT fk_qh_recon_difference_reversal FOREIGN KEY (reversal_id) REFERENCES qh_redemption_reversal (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_recon_file_attempt (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(64) NOT NULL,
    provider_batch_no VARCHAR(64) NOT NULL,
    checksum CHAR(64) NOT NULL,
    result VARCHAR(24) NOT NULL,
    recon_batch_id BIGINT NULL,
    received_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_qh_recon_attempt_provider_batch (provider, provider_batch_no, received_at),
    KEY idx_qh_recon_attempt_batch (recon_batch_id),
    CONSTRAINT fk_qh_recon_attempt_batch FOREIGN KEY (recon_batch_id) REFERENCES qh_recon_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
