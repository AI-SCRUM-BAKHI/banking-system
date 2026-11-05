CREATE TABLE fraud_ai_analysis (
    transaction_ref VARCHAR(80) PRIMARY KEY,
    analysis_json TEXT NOT NULL,
    snapshot_hash VARCHAR(64) NOT NULL,
    model VARCHAR(200) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
