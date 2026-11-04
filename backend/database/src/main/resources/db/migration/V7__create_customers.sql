-- A customer can own multiple accounts, potentially at different banks.
-- Customer identity lives here; account/bank details remain in the mapping table.
DO $$
BEGIN
    IF EXISTS (
        SELECT customer_id FROM account_customer_mapping
        GROUP BY customer_id HAVING count(DISTINCT customer_name) > 1
    ) THEN
        RAISE EXCEPTION 'Conflicting customer names in account_customer_mapping';
    END IF;
END;
$$;

CREATE TABLE customers (
    customer_id VARCHAR(100) PRIMARY KEY,
    customer_name VARCHAR(120) NOT NULL CHECK (btrim(customer_name) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO customers (customer_id, customer_name)
SELECT DISTINCT customer_id, customer_name
FROM account_customer_mapping;

ALTER TABLE account_customer_mapping
    ADD CONSTRAINT account_customer_mapping_customer_fkey
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id);

ALTER TABLE transaction_source_data
    ADD CONSTRAINT transaction_source_data_customer_fkey
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id);
