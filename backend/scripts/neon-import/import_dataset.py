#!/usr/bin/env python3
"""Validate three CSV files offline; explicitly --apply to import a complete dataset."""

import argparse
import csv
import hashlib
import io
import ipaddress
import json
import os
import re
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import parse_qs, urlsplit


ACCOUNT_FIELDS = "account_id customer_id customer_name bank".split()
TRANSACTION_FIELDS = """transaction_id customer_id transaction_datetime posting_datetime
reference_no sender_account_id sender_name sender_bank receiver_account_id receiver_name
receiver_bank transaction_type transfer_method amount_vnd currency fee_vnd channel status
description ip_address device_hash device_used device_os location ip_geo_region
new_device_transaction device_seen_count ip_seen_count is_ip_shared txn_hour is_weekend
is_night_txn txn_count_last_1h txn_count_last_24h total_amount_last_1h_vnd
time_since_last_transaction_min user_avg_txn_amt_vnd""".split()
LABEL_FIELDS = "transaction_id is_fraud fraud_type fraud_event_id suspicious_account_id data_split".split()
SOURCE_FIELDS = """transaction_ref from_account_ref from_account_masked from_customer_name
to_account_ref to_account_masked to_customer_name amount currency transaction_time posted_at""".split()
EXTRA_FIELDS = {
    "sender_bank": ("text", 255), "receiver_bank": ("text", 255),
    "transaction_type": ("text", 50), "transfer_method": ("text", 50),
    "fee_vnd": ("number", None), "channel": ("text", 50), "status": ("text", 30),
    "description": ("text", None), "ip_address": ("ip", None),
    "device_hash": ("text", 128), "device_used": ("text", 50), "device_os": ("text", 100),
    "location": ("text", 255), "ip_geo_region": ("text", 100),
    "new_device_transaction": ("boolean", None), "device_seen_count": ("integer", None),
    "ip_seen_count": ("integer", None), "is_ip_shared": ("boolean", None),
    "txn_hour": ("hour", None), "is_weekend": ("boolean", None), "is_night_txn": ("boolean", None),
    "txn_count_last_1h": ("integer", None), "txn_count_last_24h": ("integer", None),
    "total_amount_last_1h_vnd": ("number", None),
    "time_since_last_transaction_min": ("number", None), "user_avg_txn_amt_vnd": ("number", None),
}
TABLE_FIELDS = {
    "customers": ["customer_id", "customer_name"],
    "account_customer_mapping": ACCOUNT_FIELDS,
    "transactions": SOURCE_FIELDS + ["customer_id", "reference_no", "source_data", "source_label"] + list(EXTRA_FIELDS),
}
SCAN_LOCK_KEY = 734920261104


class ImportProblem(Exception):
    """An actionable validation problem, without credentials or full source records."""


@dataclass
class Dataset:
    accounts: list
    transactions: list
    labels: list
    hashes: dict

    def summary(self):
        return {
            "accounts": len(self.accounts),
            "customers": len({row["customer_id"] for row in self.accounts}),
            "transactions": len(self.transactions),
            "labels": len(self.labels),
            "synthetic_positive_labels": sum(row["is_fraud"] == "1" for row in self.labels),
            "first_transaction": min(timestamp(row["transaction_datetime"]) for row in self.transactions).isoformat(),
            "last_transaction": max(timestamp(row["transaction_datetime"]) for row in self.transactions).isoformat(),
            "sha256": self.hashes,
        }


def read_csv(path, fields):
    raw = path.read_bytes()
    reader = csv.DictReader(io.StringIO(raw.decode("utf-8-sig"), newline=""))
    if reader.fieldnames is None or len(reader.fieldnames) != len(set(reader.fieldnames)):
        raise ImportProblem(f"{path.name}: missing or duplicate header")
    if set(reader.fieldnames) != set(fields):
        raise ImportProblem(f"{path.name}: header must contain exactly: {', '.join(fields)}")
    rows = list(reader)
    if not rows:
        raise ImportProblem(f"{path.name}: empty dataset")
    for number, row in enumerate(rows, 2):
        if None in row or any(value is None for value in row.values()):
            raise ImportProblem(f"{path.name}:{number}: wrong CSV column count")
    return rows, hashlib.sha256(raw).hexdigest()


def text(row, field, maximum, optional=False):
    value = row[field]
    if len(value) > maximum or "\x00" in value or (not optional and not value.strip()):
        raise ImportProblem(f"Invalid {field} (required={not optional}, maximum={maximum})")
    return value


def unique(rows, field):
    result = {}
    for row in rows:
        value = row[field]
        if value in result:
            raise ImportProblem(f"Duplicate {field}: {value}")
        result[value] = row
    return result


def extra_values(row):
    result = []
    for field, (kind, maximum) in EXTRA_FIELDS.items():
        value = row[field]
        if value == "":
            result.append(None)
            continue
        try:
            if kind == "text":
                if maximum and len(value) > maximum:
                    raise ValueError()
                result.append(value)
            elif kind == "boolean":
                if value not in {"0", "1"}:
                    raise ValueError()
                result.append(value == "1")
            elif kind == "ip":
                result.append(ipaddress.ip_address(value))
            else:
                number = Decimal(value)
                if not number.is_finite() or number < 0:
                    raise ValueError()
                if kind in {"integer", "hour"}:
                    if number != number.to_integral_value() or number > (23 if kind == "hour" else 2147483647):
                        raise ValueError()
                    result.append(int(number))
                else:
                    result.append(number)
        except (ValueError, InvalidOperation):
            raise ImportProblem(f"Invalid CSV field {field} for {row['transaction_id']}") from None
    return tuple(result)


def timestamp(value):
    # Python/PostgreSQL timestamps have microsecond precision: reject lost precision.
    fraction = re.search(r"\.(\d+)", value)
    if fraction and len(fraction.group(1).rstrip("0")) > 6:
        raise ImportProblem("Timestamp precision greater than microseconds")
    try:
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if result.utcoffset() is None:
            raise ValueError("offset required")
        return result.astimezone(timezone.utc)
    except ValueError:
        raise ImportProblem("Invalid timestamp: explicit UTC offset is required") from None


def validate_dataset(directory, now=None):
    now = now or datetime.now(timezone.utc)
    files = [
        ("account_customer_mapping.csv", ACCOUNT_FIELDS),
        ("transactions_completed.csv", TRANSACTION_FIELDS),
        ("transaction_labels.csv", LABEL_FIELDS),
    ]
    loaded, hashes = [], {}
    for name, fields in files:
        rows, digest = read_csv(directory / name, fields)
        loaded.append(rows)
        hashes[name] = digest
    accounts, transactions, labels = loaded
    account_map = unique(accounts, "account_id")
    customer_names = {}
    for row in accounts:
        for field, maximum in [("account_id", 80), ("customer_id", 100),
                               ("customer_name", 120), ("bank", 120)]:
            text(row, field, maximum)
        existing_name = customer_names.setdefault(row["customer_id"], row["customer_name"])
        if existing_name != row["customer_name"]:
            raise ImportProblem(f"Conflicting names for customer_id={row['customer_id']}")
    transaction_map = unique(transactions, "transaction_id")
    for row in transactions:
        ref = text(row, "transaction_id", 80)
        text(row, "customer_id", 100)
        text(row, "reference_no", 100)
        if row["status"] != "SUCCESS" or row["currency"] != "VND":
            raise ImportProblem(f"{ref}: require SUCCESS and VND")
        for side in ("sender", "receiver"):
            account_id = text(row, f"{side}_account_id", 80)
            account = account_map.get(account_id)
            if account is None:
                raise ImportProblem(f"{ref}: {side} is missing from account mapping")
            if (text(row, f"{side}_name", 120) != account["customer_name"]
                    or row[f"{side}_bank"] != account["bank"]):
                raise ImportProblem(f"{ref}: {side} name/bank differs from mapping")
        if account_map[row["sender_account_id"]]["customer_id"] != row["customer_id"]:
            raise ImportProblem(f"{ref}: customer_id differs from sender mapping")
        if row["sender_account_id"] == row["receiver_account_id"]:
            raise ImportProblem(f"{ref}: sender and receiver must differ")
        try:
            amount = Decimal(row["amount_vnd"])
            if (not amount.is_finite() or amount <= 0 or amount > 999999999999999
                    or amount != amount.to_integral_value()):
                raise InvalidOperation()
        except InvalidOperation:
            raise ImportProblem(f"{ref}: require positive whole VND up to 999999999999999") from None
        transferred = timestamp(row["transaction_datetime"])
        posted = timestamp(row["posting_datetime"]) if row["posting_datetime"] else None
        if transferred > now or posted and (posted < transferred or posted > now):
            raise ImportProblem(f"{ref}: invalid transaction/posting time relationship")
        if any("\x00" in value for value in row.values()):
            raise ImportProblem(f"{ref}: NUL is not supported by PostgreSQL JSONB")
        extra_values(row)
    label_map = unique(labels, "transaction_id")
    if transaction_map.keys() != label_map.keys():
        raise ImportProblem("Labels must match transaction IDs exactly, one label per transaction")
    for row in labels:
        ref = row["transaction_id"]
        if row["is_fraud"] not in {"0", "1"}:
            raise ImportProblem(f"{ref}: is_fraud must be 0 or 1")
        kind = text(row, "fraud_type", 80)
        text(row, "data_split", 30)
        event = text(row, "fraud_event_id", 100, optional=True)
        suspect = text(row, "suspicious_account_id", 80, optional=True)
        if suspect and suspect not in account_map:
            raise ImportProblem(f"{ref}: suspicious account is missing from mapping")
        if row["is_fraud"] == "1" and (kind == "NORMAL" or not event or not suspect):
            raise ImportProblem(f"{ref}: positive label requires type, event and suspicious account")
        if row["is_fraud"] == "0" and kind != "NORMAL":
            raise ImportProblem(f"{ref}: negative label requires fraud_type NORMAL")
    return Dataset(accounts, transactions, labels, hashes)


def database_url():
    url = os.environ.get("NEON_IMPORT_DATABASE_URL", "")
    parsed = urlsplit(url)
    if parsed.scheme not in {"postgres", "postgresql"} or not parsed.hostname:
        raise ImportProblem("Set NEON_IMPORT_DATABASE_URL to a PostgreSQL connection URI")
    if "-pooler" in parsed.hostname:
        raise ImportProblem("Use a direct connection (disable pooling in Neon Connect)")
    return url


def check_schema(connection):
    from psycopg import sql
    with connection.cursor() as cursor:
        for table, columns in TABLE_FIELDS.items():
            cursor.execute(sql.SQL("SELECT {} FROM public.{} LIMIT 0").format(
                sql.SQL(", ").join(map(sql.Identifier, columns)), sql.Identifier(table)))
        cursor.execute("SELECT processing_status, attempt_count, evaluation_status, evaluated_at "
                       "FROM public.transactions LIMIT 0")
        cursor.execute("SELECT id, file_sha256, row_counts, inserted_counts FROM public.dataset_import_runs LIMIT 0")
        cursor.execute("SELECT current_database(), current_user")
        database, role = cursor.fetchone()
    return {"database": database, "role": role, "schema": "public"}


def stage_rows(dataset):
    from psycopg.types.json import Jsonb
    customers = list({row["customer_id"]: (row["customer_id"], row["customer_name"])
                      for row in dataset.accounts}.values())
    accounts = [tuple(row[field] for field in ACCOUNT_FIELDS) for row in dataset.accounts]
    transactions = []
    labels = {row["transaction_id"]: row for row in dataset.labels}
    for row in dataset.transactions:
        transactions.append((
            row["transaction_id"], row["sender_account_id"], "****" + row["sender_account_id"][-4:],
            row["sender_name"], row["receiver_account_id"], "****" + row["receiver_account_id"][-4:],
            row["receiver_name"], Decimal(row["amount_vnd"]), row["currency"],
            timestamp(row["transaction_datetime"]),
            timestamp(row["posting_datetime"]) if row["posting_datetime"] else None,
            row["customer_id"], row["reference_no"], Jsonb(row), Jsonb(labels[row["transaction_id"]]),
        ) + extra_values(row))
    return {"customers": customers, "account_customer_mapping": accounts,
            "transactions": transactions}


def upload(connection, dataset):
    """Caller owns transaction. All public writes roll back on any mismatch/error."""
    from psycopg import sql
    from psycopg.types.json import Jsonb
    check_schema(connection)
    with connection.cursor() as cursor:
        cursor.execute("SET LOCAL lock_timeout = '15s'")
        cursor.execute("SET LOCAL statement_timeout = '120s'")
        cursor.execute("SELECT pg_try_advisory_xact_lock(%s)", (SCAN_LOCK_KEY,))
        if not cursor.fetchone()[0]:
            raise ImportProblem("Scanner/importer is active; stop it and retry")
        # Stabilize conflict checks against other writers, not just this importer.
        cursor.execute("LOCK TABLE public.customers, public.account_customer_mapping, public.transactions "
                       "IN SHARE ROW EXCLUSIVE MODE")
        for table, columns in TABLE_FIELDS.items():
            cursor.execute(sql.SQL("CREATE TEMP TABLE {} ON COMMIT DROP AS "
                                   "SELECT {} FROM public.{} WITH NO DATA").format(
                sql.Identifier("stage_" + table),
                sql.SQL(", ").join(map(sql.Identifier, columns)), sql.Identifier(table)))
        staged = stage_rows(dataset)
        for table, rows in staged.items():
            with cursor.copy(sql.SQL("COPY {} ({}) FROM STDIN").format(
                    sql.Identifier("stage_" + table),
                    sql.SQL(", ").join(map(sql.Identifier, TABLE_FIELDS[table])))) as copy:
                for row in rows:
                    copy.write_row(row)
        inserted = {}
        for table, columns in TABLE_FIELDS.items():
            key = columns[0]
            comparisons = sql.SQL(" OR ").join(
                sql.SQL("t.{} IS DISTINCT FROM s.{}").format(sql.Identifier(c), sql.Identifier(c))
                for c in columns[1:])
            cursor.execute(sql.SQL("SELECT s.{} FROM {} s JOIN public.{} t ON s.{} = t.{} "
                                   "WHERE {} LIMIT 1").format(
                sql.Identifier(key), sql.Identifier("stage_" + table), sql.Identifier(table),
                sql.Identifier(key), sql.Identifier(key), comparisons))
            conflict = cursor.fetchone()
            if conflict:
                raise ImportProblem(f"Existing {table} differs for {key}={conflict[0]}; no overwrite allowed")
            fields = sql.SQL(", ").join(map(sql.Identifier, columns))
            extra_columns = sql.SQL(", source_imported_at, source_label_imported_at") if table == "transactions" else sql.SQL("")
            extra_values = sql.SQL(", CURRENT_TIMESTAMP, CURRENT_TIMESTAMP") if table == "transactions" else sql.SQL("")
            cursor.execute(sql.SQL("INSERT INTO public.{} ({}{}) SELECT {}{} FROM {} "
                                   "ON CONFLICT ({}) DO NOTHING").format(
                sql.Identifier(table), fields, extra_columns, fields, extra_values,
                sql.Identifier("stage_" + table),
                sql.Identifier(key)))
            inserted[table] = cursor.rowcount
        row_counts = {"customers": len(staged["customers"]),
                      "accounts": len(dataset.accounts), "transactions": len(dataset.transactions),
                      "labels": len(dataset.labels)}
        cursor.execute("INSERT INTO public.dataset_import_runs "
                       "(file_sha256, row_counts, inserted_counts) VALUES (%s, %s, %s) RETURNING id",
                       (Jsonb(dataset.hashes), Jsonb(row_counts), Jsonb(inserted)))
        run_id = str(cursor.fetchone()[0])
    return {"run_id": run_id, "inserted": inserted,
            "skipped_identical": {table: len(rows) - inserted[table]
                                  for table, rows in staged.items()}}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", type=Path, required=True,
                        help="Directory containing the three original CSV files")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check-db", action="store_true", help="Validate files and read-only schema preflight")
    mode.add_argument("--apply", action="store_true", help="Import atomically after validation; requires V9")
    args = parser.parse_args(argv)
    try:
        dataset = validate_dataset(args.data_dir)
        print(json.dumps({"validation": "PASS", **dataset.summary()}, ensure_ascii=False, indent=2))
        if not args.check_db and not args.apply:
            print("OFFLINE ONLY: no database connection or writes. Use --check-db or --apply.")
            return 0
        import psycopg
        url = database_url()
        local = urlsplit(url).hostname in {"localhost", "127.0.0.1", "::1"}
        sslmode = parse_qs(urlsplit(url).query).get("sslmode", ["require"])[0]
        if not local and sslmode not in {"require", "verify-ca", "verify-full"}:
            raise ImportProblem("Remote connections require SSL (require, verify-ca or verify-full)")
        with psycopg.connect(url, connect_timeout=15,
                             application_name="banking_csv_import",
                             **({} if local else {"sslmode": sslmode})) as connection:
            if args.check_db:
                connection.execute("SET TRANSACTION READ ONLY")
                print(json.dumps({"preflight": "PASS", **check_schema(connection)}))
            else:
                outcome = upload(connection, dataset)
                connection.commit()
                print(json.dumps({"upload": "COMMITTED", **outcome}, indent=2))
        return 0
    except ImportProblem as error:
        print(f"ERROR: {error}", file=sys.stderr)
    except (OSError, UnicodeError, csv.Error):
        print("ERROR: cannot read CSV files; check paths, UTF-8 encoding and permissions", file=sys.stderr)
    except ImportError:
        print("ERROR: install dependencies: python -m pip install -r requirements.txt", file=sys.stderr)
    except Exception as error:
        # Driver messages may contain a URI, password, SQL values, or full source rows.
        code = getattr(error, "sqlstate", None)
        print(f"ERROR: {type(error).__name__}; SQLSTATE={code}; "
              "upload not confirmed. Check connection, V1-V9 schema, privileges and import runs.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
