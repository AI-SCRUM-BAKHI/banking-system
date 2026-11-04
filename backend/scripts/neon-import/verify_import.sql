-- Read-only verification of the consolidated schema.
SELECT count(*) AS customers FROM public.customers;
SELECT count(*) AS accounts, count(DISTINCT customer_id) AS customers
FROM public.account_customer_mapping;

SELECT count(*) AS all_transactions,
       count(*) FILTER (WHERE source_data <> '{}'::jsonb) AS imported_csv_rows,
       count(*) FILTER (WHERE source_label <> '{}'::jsonb) AS labels,
       count(*) FILTER (WHERE source_label->>'is_fraud' = '1') AS synthetic_positives,
       count(*) FILTER (WHERE customer_id IS NULL) AS missing_customer_ids
FROM public.transactions;

SELECT processing_status, evaluation_decision, count(*)
FROM public.transactions GROUP BY processing_status, evaluation_decision
ORDER BY processing_status, evaluation_decision;

SELECT count(*) AS source_rows_missing_labels FROM public.transactions
WHERE source_data <> '{}'::jsonb AND source_label = '{}'::jsonb;

SELECT count(*) AS sender_customer_mismatches
FROM public.transactions t
LEFT JOIN public.account_customer_mapping a ON a.account_id = t.from_account_ref
WHERE t.source_data <> '{}'::jsonb
  AND (a.account_id IS NULL OR a.customer_id IS DISTINCT FROM t.customer_id);

SELECT customer_id, customer_name FROM public.customers ORDER BY customer_id;
SELECT count(*) AS evaluation_entries, count(DISTINCT transaction_ref) AS evaluated_transactions
FROM public.risk_transfer_history;

SELECT id, imported_at, row_counts, inserted_counts, file_sha256
FROM public.dataset_import_runs ORDER BY imported_at DESC LIMIT 5;

SELECT account_id, outbound_from, outbound_through,
       inbound_posted_from, inbound_posted_through, posting_complete
FROM public.risk_account_coverage ORDER BY account_id;
