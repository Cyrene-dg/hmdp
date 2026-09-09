DROP TABLE IF EXISTS qh_pos_redemption_request;

ALTER TABLE qh_redemption
    DROP COLUMN first_processed_at,
    DROP COLUMN operator_no,
    DROP COLUMN terminal_no;
