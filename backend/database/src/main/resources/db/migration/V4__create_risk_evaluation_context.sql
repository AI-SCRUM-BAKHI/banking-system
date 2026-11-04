-- Independent of fraud_transactions: this ledger includes ALL successful transfers.
CREATE TABLE risk_ingest_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    last_transaction_time TIMESTAMPTZ,
    last_transaction_time_exact VARCHAR(40),
    last_ingest_order BIGINT NOT NULL DEFAULT 0
);
INSERT INTO risk_ingest_state (id) VALUES (1);

CREATE TABLE risk_transfer_history (
    ingest_order BIGINT PRIMARY KEY,
    transaction_ref VARCHAR(80) NOT NULL UNIQUE,
    sender_account_id VARCHAR(80) NOT NULL,
    receiver_account_id VARCHAR(80) NOT NULL,
    amount_vnd NUMERIC(19, 0) NOT NULL CHECK (amount_vnd > 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'VND'),
    transaction_time TIMESTAMPTZ NOT NULL,
    posted_at TIMESTAMPTZ,
    evaluated_at TIMESTAMPTZ NOT NULL,
    request_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    audit_json TEXT NOT NULL,
    CHECK (sender_account_id <> receiver_account_id),
    CHECK (posted_at IS NULL OR (posted_at >= transaction_time AND posted_at <= evaluated_at))
);
CREATE INDEX idx_risk_history_outbound
    ON risk_transfer_history (sender_account_id, transaction_time, ingest_order);
CREATE INDEX idx_risk_history_inbound_posting
    ON risk_transfer_history (receiver_account_id, posted_at);

-- Only the trusted importer/operator may certify these intervals after verifying
-- ledger completeness. Evaluation requests cannot write coverage or account age.
-- Intervals are contiguous, inclusive, and may not bridge missing source data.
CREATE TABLE risk_account_coverage (
    account_id VARCHAR(80) PRIMARY KEY,
    opened_at TIMESTAMPTZ,
    outbound_from TIMESTAMPTZ,
    outbound_through TIMESTAMPTZ,
    inbound_posted_from TIMESTAMPTZ,
    inbound_posted_through TIMESTAMPTZ,
    posting_complete BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK ((outbound_from IS NULL AND outbound_through IS NULL)
        OR (outbound_from IS NOT NULL AND outbound_through IS NOT NULL
            AND outbound_from <= outbound_through)),
    CHECK ((inbound_posted_from IS NULL AND inbound_posted_through IS NULL)
        OR (inbound_posted_from IS NOT NULL AND inbound_posted_through IS NOT NULL
            AND inbound_posted_from <= inbound_posted_through)),
    CHECK (opened_at IS NULL OR outbound_from IS NULL OR outbound_from >= opened_at),
    CHECK (opened_at IS NULL OR inbound_posted_from IS NULL OR inbound_posted_from >= opened_at)
);
