-- Expand first: the following migration removes only the verified redundant tables.
SELECT pg_advisory_xact_lock(734920261104);
SELECT id FROM risk_ingest_state WHERE id = 1 FOR UPDATE;

ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS customer_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS reference_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS posting_datetime TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS sender_bank VARCHAR(255),
    ADD COLUMN IF NOT EXISTS receiver_bank VARCHAR(255),
    ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(50),
    ADD COLUMN IF NOT EXISTS transfer_method VARCHAR(50),
    ADD COLUMN IF NOT EXISTS fee_vnd NUMERIC,
    ADD COLUMN IF NOT EXISTS channel VARCHAR(50),
    ADD COLUMN IF NOT EXISTS status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS ip_address INET,
    ADD COLUMN IF NOT EXISTS device_hash VARCHAR(128),
    ADD COLUMN IF NOT EXISTS device_used VARCHAR(50),
    ADD COLUMN IF NOT EXISTS device_os VARCHAR(100),
    ADD COLUMN IF NOT EXISTS location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS ip_geo_region VARCHAR(100),
    ADD COLUMN IF NOT EXISTS new_device_transaction BOOLEAN,
    ADD COLUMN IF NOT EXISTS device_seen_count INTEGER,
    ADD COLUMN IF NOT EXISTS ip_seen_count INTEGER,
    ADD COLUMN IF NOT EXISTS is_ip_shared BOOLEAN,
    ADD COLUMN IF NOT EXISTS txn_hour SMALLINT,
    ADD COLUMN IF NOT EXISTS is_weekend BOOLEAN,
    ADD COLUMN IF NOT EXISTS is_night_txn BOOLEAN,
    ADD COLUMN IF NOT EXISTS txn_count_last_1h INTEGER,
    ADD COLUMN IF NOT EXISTS txn_count_last_24h INTEGER,
    ADD COLUMN IF NOT EXISTS total_amount_last_1h_vnd NUMERIC,
    ADD COLUMN IF NOT EXISTS time_since_last_transaction_min NUMERIC,
    ADD COLUMN IF NOT EXISTS user_avg_txn_amt_vnd NUMERIC,
    ADD COLUMN source_data JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(source_data) = 'object'),
    ADD COLUMN source_label JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(source_label) = 'object'),
    ADD COLUMN source_imported_at TIMESTAMPTZ,
    ADD COLUMN source_label_imported_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM transactions t JOIN transaction_source_data s USING (transaction_ref)
        WHERE t.customer_id IS NOT NULL AND t.customer_id <> s.customer_id
    ) THEN
        RAISE EXCEPTION 'Existing transaction customer conflicts with imported customer';
    END IF;
    IF EXISTS (SELECT 1 FROM transactions
        WHERE posting_datetime IS NOT NULL AND posting_datetime IS DISTINCT FROM posted_at)
    THEN
        RAISE EXCEPTION 'posting_datetime and posted_at differ; reconcile before merging';
    END IF;
    IF EXISTS (
        SELECT 1 FROM transactions t JOIN transaction_source_data s USING (transaction_ref)
        CROSS JOIN LATERAL (
            SELECT jsonb_object_agg(key, CASE WHEN value = '""'::jsonb THEN 'null'::jsonb ELSE value END) AS values_json
            FROM jsonb_each(s.raw_data)
            WHERE key = ANY(ARRAY['reference_no','sender_bank','receiver_bank','transaction_type',
                'transfer_method','fee_vnd','channel','status','description','ip_address','device_hash',
                'device_used','device_os','location','ip_geo_region','new_device_transaction',
                'device_seen_count','ip_seen_count','is_ip_shared','txn_hour','is_weekend','is_night_txn',
                'txn_count_last_1h','txn_count_last_24h','total_amount_last_1h_vnd',
                'time_since_last_transaction_min','user_avg_txn_amt_vnd'])
        ) metadata
        CROSS JOIN LATERAL jsonb_populate_record(NULL::transactions, metadata.values_json) parsed
        WHERE EXISTS (
            SELECT 1 FROM jsonb_each(to_jsonb(parsed)) f
            WHERE metadata.values_json ? f.key AND to_jsonb(t)->f.key <> 'null'::jsonb
              AND to_jsonb(t)->f.key IS DISTINCT FROM f.value
        )
    ) THEN
        RAISE EXCEPTION 'Existing CSV columns differ from imported data; refusing to overwrite';
    END IF;
    IF EXISTS (
        SELECT 1 FROM transaction_source_data
        WHERE raw_data->>'customer_id' IS DISTINCT FROM customer_id
           OR raw_data->>'reference_no' IS DISTINCT FROM reference_no
    ) OR EXISTS (
        SELECT 1 FROM transaction_source_labels
        WHERE raw_data->>'is_fraud' IS DISTINCT FROM CASE WHEN is_fraud THEN '1' ELSE '0' END
           OR raw_data->>'fraud_type' IS DISTINCT FROM fraud_type
           OR NULLIF(raw_data->>'fraud_event_id', '') IS DISTINCT FROM fraud_event_id
           OR NULLIF(raw_data->>'suspicious_account_id', '') IS DISTINCT FROM suspicious_account_id
           OR raw_data->>'data_split' IS DISTINCT FROM data_split
    ) THEN
        RAISE EXCEPTION 'Imported JSON and normalized metadata differ; reconcile before merging';
    END IF;
END;
$$;

UPDATE transactions t
SET customer_id = s.customer_id,
    reference_no = s.reference_no,
    source_data = s.raw_data,
    source_imported_at = s.imported_at,
    sender_bank = NULLIF(s.raw_data->>'sender_bank', ''),
    receiver_bank = NULLIF(s.raw_data->>'receiver_bank', ''),
    transaction_type = NULLIF(s.raw_data->>'transaction_type', ''),
    transfer_method = NULLIF(s.raw_data->>'transfer_method', ''),
    fee_vnd = NULLIF(s.raw_data->>'fee_vnd', '')::numeric,
    channel = NULLIF(s.raw_data->>'channel', ''),
    status = NULLIF(s.raw_data->>'status', ''),
    description = NULLIF(s.raw_data->>'description', ''),
    ip_address = NULLIF(s.raw_data->>'ip_address', '')::inet,
    device_hash = NULLIF(s.raw_data->>'device_hash', ''),
    device_used = NULLIF(s.raw_data->>'device_used', ''),
    device_os = NULLIF(s.raw_data->>'device_os', ''),
    location = NULLIF(s.raw_data->>'location', ''),
    ip_geo_region = NULLIF(s.raw_data->>'ip_geo_region', ''),
    new_device_transaction = NULLIF(s.raw_data->>'new_device_transaction', '')::boolean,
    device_seen_count = NULLIF(s.raw_data->>'device_seen_count', '')::integer,
    ip_seen_count = NULLIF(s.raw_data->>'ip_seen_count', '')::integer,
    is_ip_shared = NULLIF(s.raw_data->>'is_ip_shared', '')::boolean,
    txn_hour = NULLIF(s.raw_data->>'txn_hour', '')::smallint,
    is_weekend = NULLIF(s.raw_data->>'is_weekend', '')::boolean,
    is_night_txn = NULLIF(s.raw_data->>'is_night_txn', '')::boolean,
    txn_count_last_1h = NULLIF(s.raw_data->>'txn_count_last_1h', '')::integer,
    txn_count_last_24h = NULLIF(s.raw_data->>'txn_count_last_24h', '')::integer,
    total_amount_last_1h_vnd = NULLIF(s.raw_data->>'total_amount_last_1h_vnd', '')::numeric,
    time_since_last_transaction_min = NULLIF(s.raw_data->>'time_since_last_transaction_min', '')::numeric,
    user_avg_txn_amt_vnd = NULLIF(s.raw_data->>'user_avg_txn_amt_vnd', '')::numeric
FROM transaction_source_data s
WHERE s.transaction_ref = t.transaction_ref;

UPDATE transactions t
SET source_label = l.raw_data, source_label_imported_at = l.imported_at
FROM transaction_source_labels l
WHERE l.transaction_ref = t.transaction_ref;

UPDATE transactions t SET customer_id = a.customer_id
FROM account_customer_mapping a
WHERE t.customer_id IS NULL AND a.account_id = t.from_account_ref;

ALTER TABLE transactions ADD CONSTRAINT transactions_customer_fkey
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id);
CREATE INDEX idx_transactions_customer ON transactions (customer_id);

-- One existing evaluation ledger for both the HTTP evaluator and the scanner.
-- Each reevaluation appends an entry. Canonical transfer identity stays in transactions.
ALTER TABLE risk_transfer_history
    ADD COLUMN evaluation_id UUID NOT NULL DEFAULT gen_random_uuid(),
    ADD CONSTRAINT risk_transfer_history_evaluation_id_key UNIQUE (evaluation_id),
    DROP CONSTRAINT risk_transfer_history_transaction_ref_key;
CREATE INDEX idx_risk_history_latest
    ON risk_transfer_history (transaction_ref, ingest_order DESC);

INSERT INTO risk_transfer_history
    (evaluation_id, ingest_order, transaction_ref, sender_account_id, receiver_account_id,
     amount_vnd, currency, transaction_time, posted_at, evaluated_at,
     request_json, result_json, audit_json)
SELECT e.id,
       GREATEST((SELECT last_ingest_order FROM risk_ingest_state WHERE id = 1),
                COALESCE((SELECT max(ingest_order) FROM risk_transfer_history), 0))
           + row_number() OVER (ORDER BY e.evaluated_at, e.id),
       e.transaction_ref,
       e.request_snapshot->>'fromAccountRef',
       e.request_snapshot->>'toAccountRef',
       (e.request_snapshot->>'amount')::numeric(19, 0),
       e.request_snapshot->>'currency',
       (e.request_snapshot->>'transactionTime')::timestamptz,
       (e.request_snapshot->>'postedAt')::timestamptz,
       e.evaluated_at,
       jsonb_build_object(
           'transactionRef', e.transaction_ref,
           'senderAccountId', e.request_snapshot->>'fromAccountRef',
           'receiverAccountId', e.request_snapshot->>'toAccountRef',
           'amountVnd', (e.request_snapshot->>'amount')::numeric(19, 0),
           'currency', e.request_snapshot->>'currency',
           'transactionTime', e.request_snapshot->>'transactionTime',
           'postedAt', e.request_snapshot->'postedAt')::text,
       e.result::text,
       (e.audit || jsonb_build_object('sourceSnapshot', e.request_snapshot))::text
FROM transaction_evaluations e;

UPDATE risk_ingest_state s
SET last_ingest_order = GREATEST(s.last_ingest_order,
        COALESCE((SELECT max(ingest_order) FROM risk_transfer_history), 0));

UPDATE risk_ingest_state s
SET last_transaction_time = latest.transaction_time,
    last_transaction_time_exact = latest.request_json::jsonb->>'transactionTime'
FROM (
    SELECT transaction_time, request_json FROM risk_transfer_history
    ORDER BY transaction_time DESC, ingest_order DESC LIMIT 1
) latest
WHERE s.last_transaction_time IS NULL OR latest.transaction_time > s.last_transaction_time;

-- Restore already-computed scores instead of queuing the same dataset for a second run.
-- Only compatible source snapshots are reused; all historical results remain untouched.
WITH latest AS (
    SELECT DISTINCT ON (transaction_ref) * FROM risk_transfer_history
    ORDER BY transaction_ref, ingest_order DESC
)
UPDATE transactions t
SET risk_score = (h.result_json::jsonb->>'riskScore')::numeric,
    risk_level = h.result_json::jsonb->>'riskLevel',
    evaluation_status = h.result_json::jsonb->>'evaluationStatus',
    evaluation_decision = h.result_json::jsonb->>'decision',
    rule_version = h.result_json::jsonb->>'ruleVersion',
    evaluated_at = h.evaluated_at,
    processing_status = 'COMPLETED',
    next_attempt_at = NULL,
    last_error = NULL,
    fraud_reason = COALESCE(
        (SELECT string_agg(r->>'ruleCode', ', ' ORDER BY ord)
         FROM jsonb_array_elements(h.result_json::jsonb->'triggeredRules') WITH ORDINALITY AS rules(r, ord)),
        h.result_json::jsonb->>'decision'),
    rule_hits = COALESCE(
        (SELECT jsonb_agg(jsonb_build_object(
            'ruleCode', r->>'ruleCode', 'ruleName', r->>'ruleCode',
            'scoreContribution', r->'scoreContribution',
            'explanation', 'Preserved from evaluation history') ORDER BY ord)
         FROM jsonb_array_elements(h.result_json::jsonb->'triggeredRules') WITH ORDINALITY AS rules(r, ord)),
        '[]'::jsonb)
FROM latest h
WHERE h.transaction_ref = t.transaction_ref
  AND h.sender_account_id = t.from_account_ref AND h.receiver_account_id = t.to_account_ref
  AND h.amount_vnd = t.amount AND h.currency = t.currency
  AND h.transaction_time = t.transaction_time
  AND h.posted_at IS NOT DISTINCT FROM t.posted_at
  AND t.evaluated_at IS NULL;

CREATE OR REPLACE FUNCTION protect_transaction_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.transaction_ref, NEW.from_account_ref, NEW.from_account_masked,
           NEW.from_customer_name, NEW.to_account_ref, NEW.to_account_masked,
           NEW.to_customer_name, NEW.amount, NEW.currency, NEW.transaction_time, NEW.posted_at)
       IS DISTINCT FROM
       ROW(OLD.transaction_ref, OLD.from_account_ref, OLD.from_account_masked,
           OLD.from_customer_name, OLD.to_account_ref, OLD.to_account_masked,
           OLD.to_customer_name, OLD.amount, OLD.currency, OLD.transaction_time, OLD.posted_at)
       AND (EXISTS (SELECT 1 FROM risk_transfer_history WHERE transaction_ref = OLD.transaction_ref)
         OR EXISTS (SELECT 1 FROM transaction_evaluations WHERE transaction_ref = OLD.transaction_ref))
    THEN
        RAISE EXCEPTION 'Evaluated transaction source is immutable' USING ERRCODE = '23514';
    END IF;
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;
