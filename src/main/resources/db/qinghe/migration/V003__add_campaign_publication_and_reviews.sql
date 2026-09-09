ALTER TABLE qh_campaign
    ADD COLUMN member_claim_limit INT NOT NULL DEFAULT 1 AFTER end_at,
    ADD COLUMN franchise_subsidy_fen BIGINT NULL AFTER member_claim_limit,
    ADD COLUMN submitted_at DATETIME(3) NULL AFTER approved_by,
    ADD COLUMN scheduled_at DATETIME(3) NULL AFTER submitted_at,
    ADD COLUMN terminated_by VARCHAR(64) NULL AFTER scheduled_at,
    ADD COLUMN terminated_reason VARCHAR(512) NULL AFTER terminated_by,
    ADD COLUMN terminated_at DATETIME(3) NULL AFTER terminated_reason,
    ADD CONSTRAINT ck_qh_campaign_claim_limit CHECK (member_claim_limit = 1),
    ADD CONSTRAINT ck_qh_campaign_subsidy_non_negative
        CHECK (franchise_subsidy_fen IS NULL OR franchise_subsidy_fen >= 0);

ALTER TABLE qh_inventory_adjustment
    ADD COLUMN submitted_at DATETIME(3) NULL AFTER approver_id,
    ADD COLUMN reviewed_at DATETIME(3) NULL AFTER submitted_at,
    ADD COLUMN review_comment VARCHAR(512) NULL AFTER reviewed_at;

CREATE TABLE qh_campaign_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_no VARCHAR(40) NOT NULL,
    campaign_id BIGINT NOT NULL,
    decision VARCHAR(16) NOT NULL,
    applicant_id VARCHAR(64) NOT NULL,
    reviewer_id VARCHAR(64) NOT NULL,
    before_status VARCHAR(24) NOT NULL,
    after_status VARCHAR(24) NOT NULL,
    comment VARCHAR(512) NULL,
    reviewed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_campaign_review_no (review_no),
    KEY idx_qh_campaign_review_campaign_time (campaign_id, reviewed_at),
    CONSTRAINT fk_qh_campaign_review_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE qh_campaign_publication_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    campaign_id BIGINT NOT NULL,
    rule_version BIGINT NOT NULL,
    template_snapshot JSON NOT NULL,
    claim_begin_at DATETIME(3) NOT NULL,
    claim_end_at DATETIME(3) NOT NULL,
    member_claim_limit INT NOT NULL,
    initial_stock BIGINT NOT NULL,
    franchise_subsidy_fen BIGINT NULL,
    published_by VARCHAR(64) NOT NULL,
    published_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_qh_campaign_publication_campaign (campaign_id),
    CONSTRAINT fk_qh_campaign_publication_campaign FOREIGN KEY (campaign_id) REFERENCES qh_campaign (id),
    CONSTRAINT ck_qh_publication_claim_limit CHECK (member_claim_limit = 1),
    CONSTRAINT ck_qh_publication_stock_positive CHECK (initial_stock > 0),
    CONSTRAINT ck_qh_publication_subsidy_non_negative
        CHECK (franchise_subsidy_fen IS NULL OR franchise_subsidy_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
