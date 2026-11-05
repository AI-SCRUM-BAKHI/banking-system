import contextlib
import csv
import io
import json
import os
import tempfile
import unittest
import uuid
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

import import_dataset as importer


class CsvFixture(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.directory = Path(self.folder.name)
        self.accounts = [
            dict(zip(importer.ACCOUNT_FIELDS, ["A", "CUS-A", "Nguyễn An", "Bank A"])),
            dict(zip(importer.ACCOUNT_FIELDS, ["B", "CUS-B", "Trần Bình", "Bank B"])),
        ]
        self.transactions = []
        for number in range(2):
            row = dict.fromkeys(importer.TRANSACTION_FIELDS, "")
            row.update(transaction_id=f"TX{number}", customer_id="CUS-A",
                       reference_no=f"REF{number}", sender_account_id="A", sender_name="Nguyễn An",
                       sender_bank="Bank A", receiver_account_id="B", receiver_name="Trần Bình",
                       receiver_bank="Bank B", amount_vnd="100000", currency="VND", status="SUCCESS",
                       transaction_datetime="2020-01-01T08:00:00+07:00",
                       posting_datetime="2020-01-01T08:01:00+07:00", ip_address="203.0.113.1")
            self.transactions.append(row)
        self.labels = [
            dict(zip(importer.LABEL_FIELDS, ["TX0", "0", "NORMAL", "", "", "history"])),
            dict(zip(importer.LABEL_FIELDS, ["TX1", "1", "AMOUNT_SPIKE", "EVENT1", "A", "test"])),
        ]
        self.write_files()

    def tearDown(self):
        self.folder.cleanup()

    def write_files(self):
        for name, fields, rows in [
            ("account_customer_mapping.csv", importer.ACCOUNT_FIELDS, self.accounts),
            ("transactions_completed.csv", importer.TRANSACTION_FIELDS, self.transactions),
            ("transaction_labels.csv", importer.LABEL_FIELDS, self.labels),
        ]:
            with (self.directory / name).open("w", encoding="utf-8-sig", newline="") as stream:
                writer = csv.DictWriter(stream, fieldnames=fields)
                writer.writeheader()
                writer.writerows(rows)


class ValidationTests(CsvFixture):
    def test_accepts_bom_offsets_unicode_and_preserves_original_fields(self):
        dataset = importer.validate_dataset(self.directory)
        self.assertEqual(dataset.transactions[0]["ip_address"], "203.0.113.1")
        self.assertEqual(dataset.accounts[0]["customer_name"], "Nguyễn An")
        self.assertEqual(dataset.summary()["synthetic_positive_labels"], 1)
        self.assertEqual(importer.timestamp(self.transactions[0]["transaction_datetime"]),
                         datetime(2020, 1, 1, 1, tzinfo=timezone.utc))

    def test_rejects_duplicate_keys_in_every_file(self):
        for rows in [self.accounts, self.transactions, self.labels]:
            with self.subTest(rows=rows[0]):
                rows.append(rows[0].copy())
                self.write_files()
                with self.assertRaises(importer.ImportProblem):
                    importer.validate_dataset(self.directory)
                rows.pop()

    def test_rejects_transaction_file_mistaken_for_labels(self):
        (self.directory / "transaction_labels.csv").write_bytes(
            (self.directory / "transactions_completed.csv").read_bytes())
        with self.assertRaisesRegex(importer.ImportProblem, "header"):
            importer.validate_dataset(self.directory)

    def test_rejects_conflicting_names_for_one_customer(self):
        self.accounts[1]["customer_id"] = self.accounts[0]["customer_id"]
        self.write_files()
        with self.assertRaisesRegex(importer.ImportProblem, "Conflicting names"):
            importer.validate_dataset(self.directory)

    def test_allows_multiple_accounts_for_one_customer(self):
        self.accounts[1]["customer_id"] = "CUS-A"
        self.accounts[1]["customer_name"] = "Nguyễn An"
        for row in self.transactions:
            row["receiver_name"] = "Nguyễn An"
        self.write_files()
        dataset = importer.validate_dataset(self.directory)
        self.assertEqual(dataset.summary()["customers"], 1)

    def test_rejects_missing_and_foreign_labels(self):
        self.labels.pop()
        self.write_files()
        with self.assertRaisesRegex(importer.ImportProblem, "exactly"):
            importer.validate_dataset(self.directory)
        self.labels[0]["transaction_id"] = "foreign"
        self.write_files()
        with self.assertRaises(importer.ImportProblem):
            importer.validate_dataset(self.directory)

    def test_rejects_bad_amounts_before_database_access(self):
        for amount in ["0", "-1", "0.01", "1000000000000000", "NaN", "Infinity", ""]:
            with self.subTest(amount=amount):
                self.transactions[0]["amount_vnd"] = amount
                self.write_files()
                with self.assertRaises(importer.ImportProblem):
                    importer.validate_dataset(self.directory)

    def test_rejects_bad_mapping_time_and_currency(self):
        changes = [
            ("sender_account_id", "UNKNOWN"), ("sender_name", "different"),
            ("customer_id", "CUS-B"), ("currency", "USD"), ("status", "FAILED"),
            ("receiver_account_id", "A"), ("transaction_datetime", "2020-01-01T08:00:00"),
            ("transaction_datetime", "2999-01-01T08:00:00Z"),
            ("posting_datetime", "2019-01-01T08:00:00Z"),
            ("transaction_datetime", "2020-01-01T08:00:00.1234567Z"),
        ]
        for field, value in changes:
            original = self.transactions[0][field]
            with self.subTest(field=field, value=value):
                self.transactions[0][field] = value
                self.write_files()
                with self.assertRaises(importer.ImportProblem):
                    importer.validate_dataset(self.directory)
                self.transactions[0][field] = original

    def test_default_command_never_opens_database(self):
        with patch.object(importer, "database_url") as url, contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(importer.main(["--data-dir", str(self.directory)]), 0)
            url.assert_not_called()

    def test_rejects_pooled_uri(self):
        with patch.dict(os.environ, {"NEON_IMPORT_DATABASE_URL":
                                   "postgresql://user:password@ep-example-pooler.neon.tech/db"}):
            with self.assertRaisesRegex(importer.ImportProblem, "direct"):
                importer.database_url()


@unittest.skipUnless(os.environ.get("NEON_IMPORT_TEST_DATABASE_URL"),
                     "set NEON_IMPORT_TEST_DATABASE_URL to a disposable local PostgreSQL admin URL")
class PostgresDatabase(CsvFixture):
    schema_version = 9
    def setUp(self):
        super().setUp()
        import psycopg
        from psycopg import sql
        from psycopg.conninfo import make_conninfo
        self.admin = psycopg.connect(os.environ["NEON_IMPORT_TEST_DATABASE_URL"], autocommit=True)
        self.dbname = "neon_import_test_" + uuid.uuid4().hex
        self.admin.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(self.dbname)))
        self.url = make_conninfo(os.environ["NEON_IMPORT_TEST_DATABASE_URL"], dbname=self.dbname)
        self.connection = psycopg.connect(self.url)
        directory = Path(__file__).parent / "migrations"
        if not directory.exists():
            directory = Path(__file__).resolve().parents[2] / "database/src/main/resources/db/migration"
        with self.connection.transaction():
            self.migration_directory = directory
            for migration in sorted(directory.glob("V[1-9]__*.sql")):
                if int(migration.name.split('__')[0][1:]) <= self.schema_version:
                    self.connection.execute(migration.read_text(encoding="utf-8"))
        self.dataset = importer.validate_dataset(self.directory)

    def tearDown(self):
        from psycopg import sql
        self.connection.close()
        self.admin.execute(sql.SQL("DROP DATABASE {} WITH (FORCE)").format(sql.Identifier(self.dbname)))
        self.admin.close()
        super().tearDown()

    def count(self, table):
        from psycopg import sql
        return self.connection.execute(sql.SQL("SELECT count(*) FROM public.{}").format(
            sql.Identifier(table))).fetchone()[0]


class PostgresTests(PostgresDatabase):
    def test_full_import_and_reimport_preserve_engine_results(self):
        with self.connection.transaction():
            result = importer.upload(self.connection, self.dataset)
        self.assertEqual(result["inserted"]["transactions"], 2)
        self.assertEqual(result["inserted"]["customers"], 2)
        self.assertEqual(self.count("account_customer_mapping"), 2)
        self.assertEqual(self.connection.execute("SELECT count(*) FROM transactions WHERE source_label <> '{}'::jsonb").fetchone()[0], 2)
        self.assertEqual(self.count("fraud_transactions"), 0)
        row = self.connection.execute("SELECT processing_status, risk_score FROM transactions LIMIT 1").fetchone()
        self.assertEqual(row[0], "PENDING")
        self.assertEqual(row[1], 0)
        raw = self.connection.execute("SELECT source_data FROM transactions WHERE transaction_ref='TX0'").fetchone()[0]
        self.assertEqual(raw, self.transactions[0])
        self.assertEqual(self.connection.execute("SELECT host(ip_address),sender_bank FROM transactions WHERE transaction_ref='TX0'").fetchone(),
                         ('203.0.113.1','Bank A'))
        self.connection.execute("UPDATE transactions SET processing_status='COMPLETED', risk_score=75")
        self.connection.commit()
        with self.connection.transaction():
            repeat = importer.upload(self.connection, self.dataset)
        self.assertTrue(all(count == 0 for count in repeat["inserted"].values()))
        self.assertEqual(self.connection.execute("SELECT risk_score FROM transactions LIMIT 1").fetchone()[0], 75)
        self.assertEqual(self.count("dataset_import_runs"), 2)

    def test_conflicting_transaction_rolls_back_all_new_tables(self):
        self.connection.execute("""INSERT INTO transactions(transaction_ref, from_account_ref,
            from_account_masked, to_account_ref, to_account_masked, amount, transaction_time)
            VALUES ('TX0','A','****A','B','****B',1,'2020-01-01T01:00:00Z')""")
        self.connection.commit()
        with self.assertRaisesRegex(importer.ImportProblem, "differs"):
            with self.connection.transaction():
                importer.upload(self.connection, self.dataset)
        self.assertEqual(self.count("account_customer_mapping"), 0)
        self.assertEqual(self.count("customers"), 0)
        self.assertEqual(self.count("dataset_import_runs"), 0)
        self.assertEqual(self.count("transactions"), 1)

    def test_conflicting_raw_data_or_labels_are_not_overwritten(self):
        with self.connection.transaction():
            importer.upload(self.connection, self.dataset)
        self.connection.execute("UPDATE transactions SET source_label=jsonb_set(source_label,'{fraud_type}','\"DIFFERENT\"') WHERE transaction_ref='TX1'")
        self.connection.commit()
        with self.assertRaisesRegex(importer.ImportProblem, "transactions"):
            with self.connection.transaction():
                importer.upload(self.connection, self.dataset)
        self.assertEqual(self.count("dataset_import_runs"), 1)
        self.connection.commit()
        self.connection.execute("UPDATE transactions SET source_data='{}' WHERE transaction_ref='TX0'")
        self.connection.commit()
        with self.assertRaisesRegex(importer.ImportProblem, "transactions"):
            with self.connection.transaction():
                importer.upload(self.connection, self.dataset)

    def test_scanner_lock_blocks_import_without_changes(self):
        import psycopg
        with psycopg.connect(self.url, autocommit=True) as other:
            other.execute("SELECT pg_advisory_lock(%s)", (importer.SCAN_LOCK_KEY,))
            try:
                with self.assertRaisesRegex(importer.ImportProblem, "Scanner/importer"):
                    with self.connection.transaction():
                        importer.upload(self.connection, self.dataset)
                self.assertEqual(self.count("transactions"), 0)
            finally:
                other.execute("SELECT pg_advisory_unlock(%s)", (importer.SCAN_LOCK_KEY,))

    def test_schema_preflight_is_read_only(self):
        with self.connection.transaction():
            self.connection.execute("SET TRANSACTION READ ONLY")
            target = importer.check_schema(self.connection)
            self.assertEqual(target["database"], self.dbname)
        self.assertEqual(self.count("dataset_import_runs"), 0)

    def test_cli_never_prints_driver_credentials(self):
        import psycopg
        error_output = io.StringIO()
        with patch.dict(os.environ, {"NEON_IMPORT_DATABASE_URL": "postgresql://u:SECRET@localhost/db"}), \
                patch.object(psycopg, "connect", side_effect=psycopg.OperationalError("SECRET in URI")), \
                contextlib.redirect_stderr(error_output), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(importer.main(["--data-dir", str(self.directory), "--apply"]), 1)
        self.assertNotIn("SECRET", error_output.getvalue())


class ConsolidationTests(PostgresDatabase):
    schema_version = 7

    def seed_legacy(self):
        from psycopg.types.json import Jsonb
        c = self.connection
        with c.transaction():
            for row in self.accounts:
                c.execute("INSERT INTO customers(customer_id,customer_name) VALUES (%s,%s)",
                          (row['customer_id'], row['customer_name']))
                c.execute("INSERT INTO account_customer_mapping(account_id,customer_id,customer_name,bank) VALUES (%s,%s,%s,%s)",
                          tuple(row[field] for field in importer.ACCOUNT_FIELDS))
            for row, label in zip(self.transactions, self.labels):
                c.execute("""INSERT INTO transactions(transaction_ref,from_account_ref,from_account_masked,
                    from_customer_name,to_account_ref,to_account_masked,to_customer_name,amount,currency,
                    transaction_time,posted_at) VALUES (%s,'A','****A',%s,'B','****B',%s,100000,'VND',%s,%s)""",
                    (row['transaction_id'], row['sender_name'], row['receiver_name'],
                     importer.timestamp(row['transaction_datetime']), importer.timestamp(row['posting_datetime'])))
                c.execute("INSERT INTO transaction_source_data(transaction_ref,customer_id,reference_no,raw_data) VALUES (%s,%s,%s,%s)",
                          (row['transaction_id'], row['customer_id'], row['reference_no'], Jsonb(row)))
                c.execute("""INSERT INTO transaction_source_labels(transaction_ref,is_fraud,fraud_type,
                    fraud_event_id,suspicious_account_id,data_split,raw_data) VALUES (%s,%s,%s,%s,%s,%s,%s)""",
                    (row['transaction_id'], label['is_fraud']=='1', label['fraud_type'],
                     label['fraud_event_id'] or None, label['suspicious_account_id'] or None,
                     label['data_split'], Jsonb(label)))
            self.legacy_request = dict(transactionRef='TX0', senderAccountId='A', receiverAccountId='B',
                amountVnd=100000, currency='VND', transactionTime='2020-01-01T01:00:00Z',
                postedAt='2020-01-01T01:01:00Z')
            def result(ref, score):
                return dict(transactionRef=ref, riskScore=score, riskLevel='HIGH' if score>=60 else 'LOW',
                    evaluationStatus='PARTIAL', decision='FRAUD' if score>=60 else 'INSUFFICIENT_DATA',
                    ruleVersion='test-v1', triggeredRules=[], unevaluatedRules=[], fraudThreshold=60,
                    isFraud=True if score>=60 else None)
            self.legacy_result = result('TX0', 10)
            c.execute("""INSERT INTO risk_transfer_history(ingest_order,transaction_ref,sender_account_id,
                receiver_account_id,amount_vnd,currency,transaction_time,posted_at,evaluated_at,
                request_json,result_json,audit_json)
                VALUES (1,'TX0','A','B',100000,'VND','2020-01-01T01:00:00Z',
                  '2020-01-01T01:01:00Z','2020-01-02T00:00:00Z',%s,%s,%s)""",
                (json.dumps(self.legacy_request), json.dumps(self.legacy_result), '{"legacy":"keep"}'))
            c.execute("UPDATE risk_ingest_state SET last_ingest_order=1,last_transaction_time='2020-01-01T01:00:00Z',last_transaction_time_exact='2020-01-01T01:00:00Z'")
            self.new_snapshot = dict(transactionRef='TX1', fromAccountRef='A', fromAccountMasked='****A',
                fromCustomerName='Nguyễn An', toAccountRef='B', toAccountMasked='****B',
                toCustomerName='Trần Bình', amount=100000, currency='VND',
                transactionTime='2020-01-01T01:00:00Z', postedAt='2020-01-01T01:01:00Z')
            self.new_id = uuid.uuid4()
            c.execute("""INSERT INTO transaction_evaluations(id,transaction_ref,evaluated_at,rule_version,
                request_snapshot,result,audit) VALUES (%s,'TX1','2020-01-02T00:00:00Z','test-v1',%s,%s,%s)""",
                (self.new_id, Jsonb(self.new_snapshot), Jsonb(result('TX1',75)), Jsonb({'new':'keep'})))

    def apply_version(self, version):
        path = next(self.migration_directory.glob(f'V{version}__*.sql'))
        with self.connection.transaction():
            self.connection.execute(path.read_text(encoding='utf-8'))

    def test_merges_raw_labels_and_both_history_formats_losslessly(self):
        self.seed_legacy()
        self.apply_version(8)
        self.apply_version(9)
        c = self.connection
        self.assertEqual(self.count('transactions'), 2)
        self.assertEqual(self.count('risk_transfer_history'), 2)
        self.assertEqual(c.execute("SELECT request_json::jsonb,result_json::jsonb,audit_json::jsonb FROM risk_transfer_history WHERE transaction_ref='TX0'").fetchone(),
                         (self.legacy_request,self.legacy_result,{'legacy':'keep'}))
        audit = c.execute('SELECT audit_json::jsonb FROM risk_transfer_history WHERE evaluation_id=%s', (self.new_id,)).fetchone()[0]
        self.assertEqual(audit['sourceSnapshot'], self.new_snapshot)
        self.assertEqual(audit['new'], 'keep')
        self.assertEqual(c.execute("SELECT source_data,source_label,customer_id,processing_status FROM transactions WHERE transaction_ref='TX1'").fetchone(),
                         (self.transactions[1],self.labels[1],'CUS-A','COMPLETED'))
        for table in ['transaction_source_data','transaction_source_labels','transaction_evaluations']:
            self.assertIsNone(c.execute('SELECT to_regclass(%s)', (table,)).fetchone()[0])
        self.assertEqual(c.execute('SELECT last_ingest_order FROM risk_ingest_state').fetchone()[0], 2)

    def test_refuses_contract_when_copy_is_incomplete(self):
        import psycopg
        self.seed_legacy()
        self.apply_version(8)
        self.connection.execute("UPDATE transactions SET source_data='{}' WHERE transaction_ref='TX0'")
        self.connection.commit()
        with self.assertRaises(psycopg.errors.RaiseException):
            self.apply_version(9)
        self.assertEqual(self.count('transaction_source_data'), 2)
        self.assertEqual(self.count('transaction_evaluations'), 1)

    def test_conflicting_label_metadata_aborts_expand(self):
        import psycopg
        self.seed_legacy()
        self.connection.execute("UPDATE transaction_source_labels SET is_fraud=true WHERE transaction_ref='TX0'")
        self.connection.commit()
        with self.assertRaises(psycopg.errors.RaiseException):
            self.apply_version(8)
        self.assertEqual(self.count('transaction_source_labels'), 2)
        self.assertEqual(self.count('risk_transfer_history'), 1)
        self.assertIsNone(self.connection.execute("SELECT column_name FROM information_schema.columns WHERE table_name='transactions' AND column_name='source_data'").fetchone())

    def test_conflicting_existing_csv_column_aborts_expand(self):
        import psycopg
        self.seed_legacy()
        self.connection.execute('ALTER TABLE transactions ADD COLUMN channel varchar(50)')
        self.connection.execute("UPDATE transactions SET channel='MANUAL' WHERE transaction_ref='TX0'")
        self.connection.commit()
        with self.assertRaises(psycopg.errors.RaiseException):
            self.apply_version(8)
        self.assertEqual(self.connection.execute("SELECT channel FROM transactions WHERE transaction_ref='TX0'").fetchone()[0], 'MANUAL')
        self.assertEqual(self.count('transaction_source_data'), 2)


if __name__ == "__main__":
    unittest.main()
