DROP TABLE IF EXISTS qh_campaign_publication_snapshot;
DROP TABLE IF EXISTS qh_campaign_review;

ALTER TABLE qh_inventory_adjustment
    DROP COLUMN review_comment,
    DROP COLUMN reviewed_at,
    DROP COLUMN submitted_at;

ALTER TABLE qh_campaign
    DROP CHECK ck_qh_campaign_subsidy_non_negative,
    DROP CHECK ck_qh_campaign_claim_limit,
    DROP COLUMN terminated_at,
    DROP COLUMN terminated_reason,
    DROP COLUMN terminated_by,
    DROP COLUMN scheduled_at,
    DROP COLUMN submitted_at,
    DROP COLUMN franchise_subsidy_fen,
    DROP COLUMN member_claim_limit;
