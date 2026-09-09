DROP TABLE IF EXISTS qh_pos_reversal_request;

ALTER TABLE qh_redemption_reversal
    DROP COLUMN updated_at,
    DROP COLUMN first_processed_at,
    DROP COLUMN reason_remark,
    DROP COLUMN operator_no;
