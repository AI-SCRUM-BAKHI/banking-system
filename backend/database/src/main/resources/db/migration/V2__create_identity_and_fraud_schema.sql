CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'BANK_USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_username_key UNIQUE (username),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'BANK_USER')),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE IF NOT EXISTS fraud_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_ref VARCHAR(80) NOT NULL,
    from_account_ref VARCHAR(80) NOT NULL,
    from_account_masked VARCHAR(40) NOT NULL,
    from_customer_name VARCHAR(120),
    to_account_ref VARCHAR(80) NOT NULL,
    to_account_masked VARCHAR(40) NOT NULL,
    to_customer_name VARCHAR(120),
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    transaction_time TIMESTAMPTZ NOT NULL,
    risk_score NUMERIC(5, 2) NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    fraud_reason VARCHAR(500) NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fraud_transactions_transaction_ref_key UNIQUE (transaction_ref),
    CONSTRAINT fraud_transactions_amount_check CHECK (amount > 0),
    CONSTRAINT fraud_transactions_risk_score_check CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT fraud_transactions_risk_level_check
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT fraud_transactions_review_status_check
        CHECK (review_status IN ('NEW', 'REVIEWING', 'CONFIRMED_FRAUD', 'FALSE_POSITIVE'))
);

CREATE TABLE IF NOT EXISTS fraud_rule_hits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    fraud_transaction_id UUID NOT NULL,
    rule_code VARCHAR(50) NOT NULL,
    rule_name VARCHAR(160) NOT NULL,
    score_contribution NUMERIC(5, 2) NOT NULL,
    explanation VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fraud_rule_hits_fraud_transaction_id_fkey
        FOREIGN KEY (fraud_transaction_id) REFERENCES fraud_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fraud_rule_hits_fraud_transaction_id_rule_code_key
        UNIQUE (fraud_transaction_id, rule_code),
    CONSTRAINT fraud_rule_hits_score_contribution_check
        CHECK (score_contribution BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS transaction_chain (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    root_fraud_transaction_id UUID NOT NULL,
    transaction_ref VARCHAR(80) NOT NULL,
    from_account_ref VARCHAR(80) NOT NULL,
    from_account_masked VARCHAR(40) NOT NULL,
    from_customer_name VARCHAR(120),
    to_account_ref VARCHAR(80) NOT NULL,
    to_account_masked VARCHAR(40) NOT NULL,
    to_customer_name VARCHAR(120),
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'VND',
    transaction_time TIMESTAMPTZ NOT NULL,
    hop_level INTEGER NOT NULL,
    is_root BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT transaction_chain_root_fraud_transaction_id_fkey
        FOREIGN KEY (root_fraud_transaction_id) REFERENCES fraud_transactions (id) ON DELETE CASCADE,
    CONSTRAINT transaction_chain_root_fraud_transaction_id_transaction_ref_key
        UNIQUE (root_fraud_transaction_id, transaction_ref),
    CONSTRAINT transaction_chain_amount_check CHECK (amount > 0),
    CONSTRAINT transaction_chain_hop_level_check CHECK (hop_level >= 0),
    CONSTRAINT transaction_chain_check
        CHECK ((is_root AND hop_level = 0) OR (NOT is_root AND hop_level > 0))
);

CREATE INDEX IF NOT EXISTS idx_fraud_transactions_created
    ON fraud_transactions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_fraud_transactions_risk
    ON fraud_transactions (risk_score DESC, transaction_time DESC);
CREATE INDEX IF NOT EXISTS idx_rule_hits_transaction
    ON fraud_rule_hits (fraud_transaction_id);
CREATE INDEX IF NOT EXISTS idx_transaction_chain_root_hop
    ON transaction_chain (root_fraud_transaction_id, hop_level, transaction_time);

CREATE OR REPLACE VIEW v_fraud_details AS
SELECT
    fraud_transaction.*,
    COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'id', rule_hit.id,
                'ruleCode', rule_hit.rule_code,
                'ruleName', rule_hit.rule_name,
                'scoreContribution', rule_hit.score_contribution,
                'explanation', rule_hit.explanation,
                'createdAt', rule_hit.created_at
            ) ORDER BY rule_hit.created_at
        ) FILTER (WHERE rule_hit.id IS NOT NULL),
        '[]'::jsonb
    ) AS rule_hits
FROM fraud_transactions AS fraud_transaction
LEFT JOIN fraud_rule_hits AS rule_hit
    ON rule_hit.fraud_transaction_id = fraud_transaction.id
GROUP BY fraud_transaction.id;

CREATE OR REPLACE VIEW v_fraud_graph_nodes AS
SELECT
    root_fraud_transaction_id,
    from_account_ref AS node_id,
    from_account_masked AS account_label,
    from_customer_name AS customer_name
FROM transaction_chain
UNION
SELECT
    root_fraud_transaction_id,
    to_account_ref AS node_id,
    to_account_masked AS account_label,
    to_customer_name AS customer_name
FROM transaction_chain;

CREATE OR REPLACE VIEW v_fraud_graph_edges AS
SELECT
    id AS edge_id,
    root_fraud_transaction_id,
    transaction_ref,
    from_account_ref AS source_node_id,
    to_account_ref AS target_node_id,
    amount,
    currency,
    transaction_time,
    hop_level,
    is_root
FROM transaction_chain;
