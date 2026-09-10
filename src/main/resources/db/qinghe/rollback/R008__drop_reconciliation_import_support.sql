DROP TABLE IF EXISTS qh_recon_file_attempt;
DROP TABLE IF EXISTS qh_recon_difference;
DROP TABLE IF EXISTS qh_recon_import_issue;
DROP TABLE IF EXISTS qh_recon_import_chunk;

ALTER TABLE qh_recon_record
    DROP FOREIGN KEY fk_qh_recon_record_reversal,
    DROP FOREIGN KEY fk_qh_recon_record_redemption,
    DROP INDEX idx_qh_recon_record_matched_reversal,
    DROP INDEX idx_qh_recon_record_matched_redemption,
    DROP INDEX idx_qh_recon_record_match_status,
    DROP COLUMN matched_at,
    DROP COLUMN settlement_eligible,
    DROP COLUMN store_ownership,
    DROP COLUMN matched_reversal_id,
    DROP COLUMN matched_redemption_id,
    DROP COLUMN match_reason,
    DROP COLUMN right_code_hash,
    DROP COLUMN pos_order_no,
    DROP COLUMN terminal_no,
    DROP COLUMN store_code;

ALTER TABLE qh_recon_batch
    DROP INDEX idx_qh_recon_correction,
    DROP INDEX uq_qh_recon_batch_no,
    DROP COLUMN completed_at,
    DROP COLUMN version,
    DROP COLUMN last_error_code,
    DROP COLUMN next_line_no,
    DROP COLUMN franchise_eligible_rows,
    DROP COLUMN direct_matched_rows,
    DROP COLUMN difference_rows,
    DROP COLUMN matched_rows,
    DROP COLUMN success_rows,
    DROP COLUMN imported_rows,
    DROP COLUMN generated_at,
    DROP COLUMN correction_of_batch_no,
    DROP COLUMN checksum_algorithm,
    DROP COLUMN schema_version,
    DROP COLUMN file_name,
    DROP COLUMN recon_batch_no,
    MODIFY COLUMN provider VARCHAR(32) NOT NULL;
