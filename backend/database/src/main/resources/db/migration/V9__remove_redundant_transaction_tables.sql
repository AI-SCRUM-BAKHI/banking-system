SELECT pg_advisory_xact_lock(734920261104);
SELECT id FROM risk_ingest_state WHERE id = 1 FOR UPDATE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM transaction_source_data s LEFT JOIN transactions t USING (transaction_ref)
        WHERE t.transaction_ref IS NULL OR t.source_data IS DISTINCT FROM s.raw_data
           OR t.customer_id IS DISTINCT FROM s.customer_id
           OR t.reference_no IS DISTINCT FROM s.reference_no
           OR t.source_imported_at IS DISTINCT FROM s.imported_at
    ) OR EXISTS (
        SELECT 1 FROM transaction_source_labels l LEFT JOIN transactions t USING (transaction_ref)
        WHERE t.transaction_ref IS NULL OR t.source_label IS DISTINCT FROM l.raw_data
           OR t.source_label_imported_at IS DISTINCT FROM l.imported_at
    ) OR EXISTS (
        SELECT 1 FROM transaction_evaluations e
        LEFT JOIN risk_transfer_history h ON h.evaluation_id = e.id
        WHERE h.evaluation_id IS NULL OR h.result_json::jsonb IS DISTINCT FROM e.result
           OR h.audit_json::jsonb->'sourceSnapshot' IS DISTINCT FROM e.request_snapshot
           OR h.evaluated_at IS DISTINCT FROM e.evaluated_at
    ) THEN
        RAISE EXCEPTION 'Consolidation is incomplete; refusing to remove source tables';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION protect_transaction_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.transaction_ref, NEW.from_account_ref, NEW.from_account_masked,
           NEW.from_customer_name, NEW.to_account_ref, NEW.to_account_masked,
           NEW.to_customer_name, NEW.amount, NEW.currency, NEW.transaction_time, NEW.posted_at)
       IS DISTINCT FROM
       ROW(OLD.transaction_ref, OLD.from_account_ref, OLD.from_account_masked,
           OLD.from_customer_name, OLD.to_account_ref, OLD.to_account_masked,
           OLD.to_customer_name, OLD.amount, OLD.currency, OLD.transaction_time, OLD.posted_at)
       AND EXISTS (SELECT 1 FROM risk_transfer_history WHERE transaction_ref = OLD.transaction_ref)
    THEN
        RAISE EXCEPTION 'Evaluated transaction source is immutable' USING ERRCODE = '23514';
    END IF;
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- No CASCADE: unexpected external dependencies must stop the migration.
DROP TABLE transaction_source_labels;
DROP TABLE transaction_source_data;
DROP TABLE transaction_evaluations;

-- The legacy alias was checked against the canonical posting timestamp in V8.
ALTER TABLE transactions DROP COLUMN posting_datetime;
