-- Development-only rollback for V004 before claim/outbox facts exist.

DROP TABLE IF EXISTS qh_outbox_delivery_attempt;

ALTER TABLE qh_outbox_event
    DROP COLUMN published_at,
    DROP COLUMN last_error,
    DROP COLUMN lease_owner;
