-- Development/test rollback for V002. Do not run after dependent business data exists.
DROP TABLE IF EXISTS qh_pos_nonce;
DROP TABLE IF EXISTS qh_pos_credential;
DROP TABLE IF EXISTS qh_store_import_row;
DROP TABLE IF EXISTS qh_store_import_batch;
DROP TABLE IF EXISTS qh_platform_session;
