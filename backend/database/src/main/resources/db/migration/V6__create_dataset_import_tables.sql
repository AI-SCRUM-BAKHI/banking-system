-- Preserve dataset identity and original CSV values separately from engine outputs.
CREATE TABLE account_customer_mapping (
    account_id VARCHAR(80) PRIMARY KEY,
    customer_id VARCHAR(100) NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    bank VARCHAR(120) NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_account_customer_mapping_customer
    ON account_customer_mapping (customer_id);

CREATE TABLE transaction_source_data (
    transaction_ref VARCHAR(100) PRIMARY KEY REFERENCES transactions(transaction_ref),
    customer_id VARCHAR(100) NOT NULL,
    reference_no VARCHAR(100) NOT NULL,
    raw_data JSONB NOT NULL CHECK (jsonb_typeof(raw_data) = 'object'),
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transaction_source_data_customer ON transaction_source_data (customer_id);

CREATE TABLE transaction_source_labels (
    transaction_ref VARCHAR(100) PRIMARY KEY REFERENCES transactions(transaction_ref),
    is_fraud BOOLEAN NOT NULL,
    fraud_type VARCHAR(80) NOT NULL,
    fraud_event_id VARCHAR(100),
    suspicious_account_id VARCHAR(80) REFERENCES account_customer_mapping(account_id),
    data_split VARCHAR(30) NOT NULL,
    raw_data JSONB NOT NULL CHECK (jsonb_typeof(raw_data) = 'object'),
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
COMMENT ON TABLE transaction_source_labels IS
    'Synthetic dataset labels for comparison only; never engine predictions or review decisions.';

CREATE TABLE dataset_import_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    imported_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    file_sha256 JSONB NOT NULL CHECK (jsonb_typeof(file_sha256) = 'object'),
    row_counts JSONB NOT NULL CHECK (jsonb_typeof(row_counts) = 'object'),
    inserted_counts JSONB NOT NULL CHECK (jsonb_typeof(inserted_counts) = 'object')
);
