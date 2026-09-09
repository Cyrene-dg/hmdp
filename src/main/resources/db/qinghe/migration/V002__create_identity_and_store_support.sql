-- WP-02 identity, platform-session, store-import and POS credential support.
-- V001 remains immutable and this migration only adds new tables.

CREATE TABLE qh_platform_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_no VARCHAR(48) NOT NULL,
    access_token_hash CHAR(64) NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    last_access_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_session_no (session_no),
    UNIQUE KEY uq_qh_session_token_hash (access_token_hash),
    KEY idx_qh_session_member_status (member_id, status, expires_at),
    CONSTRAINT fk_qh_session_member FOREIGN KEY (member_id) REFERENCES qh_member_mapping (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_store_import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    import_no VARCHAR(48) NOT NULL,
    source_version VARCHAR(64) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_rows INT NOT NULL,
    valid_rows INT NOT NULL,
    error_rows INT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    committed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_store_import_no (import_no),
    UNIQUE KEY uq_qh_store_import_source_version (source_version),
    CONSTRAINT ck_qh_store_import_counts CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND error_rows >= 0
        AND total_rows = valid_rows + error_rows
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_store_import_row (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    source_row_number INT NOT NULL,
    external_store_code VARCHAR(32) NULL,
    store_name VARCHAR(128) NULL,
    ownership_type VARCHAR(16) NULL,
    store_status VARCHAR(16) NULL,
    pos_version VARCHAR(32) NULL,
    validation_error VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_store_import_row (batch_id, source_row_number),
    KEY idx_qh_store_import_row_batch (batch_id),
    CONSTRAINT fk_qh_store_import_row_batch FOREIGN KEY (batch_id)
        REFERENCES qh_store_import_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_pos_credential (
    id BIGINT NOT NULL AUTO_INCREMENT,
    store_id BIGINT NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    secret_reference VARCHAR(255) NOT NULL,
    secret_version INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    activated_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_pos_credential_client (client_id),
    KEY idx_qh_pos_credential_store_status (store_id, status),
    CONSTRAINT fk_qh_pos_credential_store FOREIGN KEY (store_id) REFERENCES qh_store (id),
    CONSTRAINT ck_qh_pos_secret_version CHECK (secret_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_pos_nonce (
    id BIGINT NOT NULL AUTO_INCREMENT,
    client_id VARCHAR(64) NOT NULL,
    nonce_value VARCHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_pos_nonce_client_value (client_id, nonce_value),
    KEY idx_qh_pos_nonce_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
