CREATE TABLE IF NOT EXISTS transactions (
    transaction_ref VARCHAR(100) PRIMARY KEY,
    from_account_ref VARCHAR(100) NOT NULL,
    from_account_masked VARCHAR(100) NOT NULL,
    from_customer_name VARCHAR(255),
    to_account_ref VARCHAR(100) NOT NULL,
    to_account_masked VARCHAR(100) NOT NULL,
    to_customer_name VARCHAR(255),
    amount NUMERIC(20, 2) NOT NULL CHECK (amount >= 0),
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    transaction_time TIMESTAMPTZ NOT NULL,
    risk_score NUMERIC(5, 2) NOT NULL DEFAULT 0 CHECK (risk_score BETWEEN 0 AND 100),
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW'
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    fraud_reason TEXT,
    rule_hits JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(rule_hits) = 'array'),
    transaction_chain JSONB NOT NULL DEFAULT '[]'::jsonb
        CHECK (jsonb_typeof(transaction_chain) = 'array'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Also adopt a source table previously created using the documented source schema.
ALTER TABLE transactions
    ADD COLUMN posted_at TIMESTAMPTZ,
    ADD COLUMN processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (processing_status IN ('PENDING', 'COMPLETED', 'RETRY', 'FAILED')),
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN evaluated_at TIMESTAMPTZ,
    ADD COLUMN evaluation_status VARCHAR(20) CHECK (evaluation_status IN ('COMPLETE', 'PARTIAL')),
    ADD COLUMN evaluation_decision VARCHAR(30)
        CHECK (evaluation_decision IN ('FRAUD', 'NOT_FRAUD', 'INSUFFICIENT_DATA')),
    ADD COLUMN rule_version VARCHAR(100),
    ADD COLUMN last_error TEXT;

CREATE INDEX IF NOT EXISTS idx_transactions_from_account ON transactions (from_account_ref);
CREATE INDEX IF NOT EXISTS idx_transactions_to_account ON transactions (to_account_ref);
CREATE INDEX IF NOT EXISTS idx_transactions_time ON transactions (transaction_time DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_risk ON transactions (risk_level, risk_score DESC);
CREATE INDEX idx_transactions_scan
    ON transactions (processing_status, next_attempt_at, transaction_time, transaction_ref)
    WHERE processing_status IN ('PENDING', 'RETRY');
CREATE INDEX idx_transactions_outbound
    ON transactions (from_account_ref, transaction_time, transaction_ref);
CREATE INDEX idx_transactions_inbound ON transactions (to_account_ref, posted_at);

CREATE TABLE transaction_evaluations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_ref VARCHAR(100) NOT NULL REFERENCES transactions(transaction_ref),
    evaluated_at TIMESTAMPTZ NOT NULL,
    rule_version VARCHAR(100) NOT NULL,
    request_snapshot JSONB NOT NULL,
    result JSONB NOT NULL,
    audit JSONB NOT NULL
);
CREATE INDEX idx_transaction_evaluations_ref
    ON transaction_evaluations (transaction_ref, evaluated_at DESC);

CREATE FUNCTION protect_transaction_source() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.transaction_ref, NEW.from_account_ref, NEW.from_account_masked,
           NEW.from_customer_name, NEW.to_account_ref, NEW.to_account_masked,
           NEW.to_customer_name, NEW.amount, NEW.currency, NEW.transaction_time, NEW.posted_at)
       IS DISTINCT FROM
       ROW(OLD.transaction_ref, OLD.from_account_ref, OLD.from_account_masked,
           OLD.from_customer_name, OLD.to_account_ref, OLD.to_account_masked,
           OLD.to_customer_name, OLD.amount, OLD.currency, OLD.transaction_time, OLD.posted_at)
       AND EXISTS (SELECT 1 FROM transaction_evaluations WHERE transaction_ref = OLD.transaction_ref)
    THEN
        RAISE EXCEPTION 'Evaluated transaction source is immutable' USING ERRCODE = '23514';
    END IF;
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;
CREATE TRIGGER transactions_before_update BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION protect_transaction_source();
