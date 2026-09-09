-- Qinghe marketing schema v1. All new tables use the qh_ namespace.
-- This additive migration does not alter legacy hmdp tables.

CREATE TABLE qh_store (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_store_code VARCHAR(32) NOT NULL,
    name VARCHAR(128) NOT NULL,
    ownership_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    pos_version VARCHAR(32) NULL,
    source_version VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_store_external_code (external_store_code),
    KEY idx_qh_store_status_type (status, ownership_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_member_mapping (
    id BIGINT NOT NULL AUTO_INCREMENT,
    external_member_no VARCHAR(64) NOT NULL,
    platform_user_id BIGINT NOT NULL,
    status_snapshot VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_member_external_no (external_member_no),
    UNIQUE KEY uq_qh_member_platform_user (platform_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_benefit_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    template_no VARCHAR(40) NOT NULL,
    type VARCHAR(24) NOT NULL,
    title VARCHAR(128) NOT NULL,
    rules_snapshot JSON NOT NULL,
    validity_type VARCHAR(24) NOT NULL,
    validity_value INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_template_no (template_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_campaign (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campaign_no VARCHAR(40) NOT NULL,
    template_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    status VARCHAR(24) NOT NULL,
    begin_at DATETIME(3) NOT NULL,
    end_at DATETIME(3) NOT NULL,
    rule_version BIGINT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    approved_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_campaign_no (campaign_no),
    KEY idx_qh_campaign_status_time (status, begin_at, end_at),
    CONSTRAINT fk_qh_campaign_template FOREIGN KEY (template_id) REFERENCES qh_benefit_template (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_campaign_inventory (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    total_stock BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_campaign_inventory_campaign (campaign_id),
    CONSTRAINT fk_qh_inventory_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id),
    CONSTRAINT ck_qh_inventory_non_negative CHECK (total_stock >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_inventory_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    adjustment_no VARCHAR(40) NOT NULL,
    campaign_id BIGINT NOT NULL,
    delta_stock BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    applicant_id VARCHAR(64) NOT NULL,
    approver_id VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    applied_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_inventory_adjustment_no (adjustment_no),
    KEY idx_qh_inventory_adjustment_campaign_status (campaign_id, status),
    CONSTRAINT fk_qh_adjustment_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id),
    CONSTRAINT ck_qh_adjustment_positive CHECK (delta_stock > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_campaign_store (
    campaign_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    store_type VARCHAR(16) NOT NULL,
    subsidy_fen BIGINT NOT NULL,
    participation_status VARCHAR(24) NOT NULL,
    rule_version BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (campaign_id, store_id),
    KEY idx_qh_campaign_store_store (store_id, participation_status),
    CONSTRAINT fk_qh_campaign_store_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id),
    CONSTRAINT fk_qh_campaign_store_store FOREIGN KEY (store_id) REFERENCES qh_store (id),
    CONSTRAINT ck_qh_campaign_store_subsidy CHECK (subsidy_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_claim_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    claim_no VARCHAR(40) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    campaign_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    claim_cycle VARCHAR(32) NOT NULL,
    reservation_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_claim_no (claim_no),
    UNIQUE KEY uq_qh_claim_member_campaign_cycle (member_id, campaign_id, claim_cycle),
    UNIQUE KEY uq_qh_claim_member_request (member_id, request_id),
    UNIQUE KEY uq_qh_claim_reservation (reservation_id),
    KEY idx_qh_claim_status_updated (status, updated_at),
    CONSTRAINT fk_qh_claim_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id),
    CONSTRAINT fk_qh_claim_member FOREIGN KEY (member_id) REFERENCES qh_member_mapping (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_member_entitlement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    entitlement_no VARCHAR(40) NOT NULL,
    right_code_hash CHAR(64) NOT NULL,
    encrypted_right_code VARBINARY(512) NOT NULL,
    source_claim_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_entitlement_no (entitlement_no),
    UNIQUE KEY uq_qh_entitlement_source_claim (source_claim_id),
    UNIQUE KEY uq_qh_entitlement_right_hash (right_code_hash),
    KEY idx_qh_entitlement_member_status (member_id, status),
    CONSTRAINT fk_qh_entitlement_claim FOREIGN KEY (source_claim_id) REFERENCES qh_claim_request (id),
    CONSTRAINT fk_qh_entitlement_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id),
    CONSTRAINT fk_qh_entitlement_member FOREIGN KEY (member_id) REFERENCES qh_member_mapping (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_redemption (
    id BIGINT NOT NULL AUTO_INCREMENT,
    redemption_no VARCHAR(40) NOT NULL,
    pos_client_id VARCHAR(64) NOT NULL,
    pos_request_no VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    pos_order_no VARCHAR(64) NOT NULL,
    entitlement_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_redemption_no (redemption_no),
    UNIQUE KEY uq_qh_redemption_client_request (pos_client_id, pos_request_no),
    KEY idx_qh_redemption_entitlement_status (entitlement_id, status),
    KEY idx_qh_redemption_pos_order (pos_order_no),
    CONSTRAINT fk_qh_redemption_entitlement FOREIGN KEY (entitlement_id) REFERENCES qh_member_entitlement (id),
    CONSTRAINT fk_qh_redemption_store FOREIGN KEY (store_id) REFERENCES qh_store (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_redemption_reversal (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reversal_no VARCHAR(40) NOT NULL,
    pos_client_id VARCHAR(64) NOT NULL,
    pos_request_no VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    redemption_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_reversal_no (reversal_no),
    UNIQUE KEY uq_qh_reversal_client_request (pos_client_id, pos_request_no),
    UNIQUE KEY uq_qh_reversal_redemption (redemption_id),
    CONSTRAINT fk_qh_reversal_redemption FOREIGN KEY (redemption_id) REFERENCES qh_redemption (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_subsidy_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT,
    redemption_id BIGINT NOT NULL,
    campaign_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    subsidy_fen BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    snapshot_version BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_subsidy_redemption (redemption_id),
    KEY idx_qh_subsidy_store_status (store_id, status),
    KEY idx_qh_subsidy_campaign_status (campaign_id, status),
    CONSTRAINT fk_qh_subsidy_redemption FOREIGN KEY (redemption_id) REFERENCES qh_redemption (id),
    CONSTRAINT fk_qh_subsidy_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id),
    CONSTRAINT fk_qh_subsidy_store FOREIGN KEY (store_id) REFERENCES qh_store (id),
    CONSTRAINT ck_qh_subsidy_non_negative CHECK (subsidy_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_recon_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    provider VARCHAR(32) NOT NULL,
    batch_no VARCHAR(64) NOT NULL,
    business_date DATE NOT NULL,
    checksum CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_recon_provider_batch (provider, batch_no),
    KEY idx_qh_recon_business_date_status (business_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_recon_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    pos_request_no VARCHAR(64) NOT NULL,
    redemption_no VARCHAR(40) NULL,
    operation_type VARCHAR(24) NOT NULL,
    operation_status VARCHAR(24) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    match_status VARCHAR(32) NOT NULL,
    raw_digest CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_recon_record_batch_line (batch_id, line_no),
    KEY idx_qh_recon_record_redemption (redemption_no),
    KEY idx_qh_recon_record_pos_request (pos_request_no),
    CONSTRAINT fk_qh_recon_record_batch FOREIGN KEY (batch_id) REFERENCES qh_recon_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_settlement_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_no VARCHAR(40) NOT NULL,
    recon_batch_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    status VARCHAR(24) NOT NULL,
    detail_count INT NOT NULL,
    total_fen BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    confirmed_by VARCHAR(64) NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_settlement_batch_no (batch_no),
    UNIQUE KEY uq_qh_settlement_recon_batch (recon_batch_id),
    KEY idx_qh_settlement_date_status (business_date, status),
    CONSTRAINT fk_qh_settlement_recon_batch FOREIGN KEY (recon_batch_id) REFERENCES qh_recon_batch (id),
    CONSTRAINT ck_qh_settlement_total CHECK (detail_count >= 0 AND total_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_settlement_detail (
    id BIGINT NOT NULL AUTO_INCREMENT,
    settlement_batch_id BIGINT NULL,
    candidate_id BIGINT NOT NULL,
    redemption_id BIGINT NOT NULL,
    recon_batch_id BIGINT NOT NULL,
    subsidy_fen BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_settlement_detail_candidate (candidate_id),
    UNIQUE KEY uq_qh_settlement_detail_redemption (redemption_id),
    KEY idx_qh_settlement_detail_batch_status (settlement_batch_id, status),
    CONSTRAINT fk_qh_settlement_detail_batch FOREIGN KEY (settlement_batch_id) REFERENCES qh_settlement_batch (id),
    CONSTRAINT fk_qh_settlement_detail_candidate FOREIGN KEY (candidate_id) REFERENCES qh_subsidy_candidate (id),
    CONSTRAINT fk_qh_settlement_detail_redemption FOREIGN KEY (redemption_id) REFERENCES qh_redemption (id),
    CONSTRAINT fk_qh_settlement_detail_recon FOREIGN KEY (recon_batch_id) REFERENCES qh_recon_batch (id),
    CONSTRAINT ck_qh_settlement_detail_subsidy CHECK (subsidy_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(40) NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_version BIGINT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(3) NULL,
    lease_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_outbox_event_id (event_id),
    KEY idx_qh_outbox_status_retry (status, next_retry_at, lease_until),
    KEY idx_qh_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_operation_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_type VARCHAR(24) NOT NULL,
    operator_id VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id VARCHAR(64) NOT NULL,
    before_state VARCHAR(32) NULL,
    after_state VARCHAR(32) NULL,
    reason VARCHAR(512) NULL,
    result VARCHAR(24) NOT NULL,
    request_id VARCHAR(64) NULL,
    trace_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_qh_operation_business (business_type, business_id),
    KEY idx_qh_operation_operator_time (operator_id, created_at),
    KEY idx_qh_operation_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
