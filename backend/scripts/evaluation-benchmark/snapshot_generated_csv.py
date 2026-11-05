#!/usr/bin/env python3
"""Prepare an offline benchmark snapshot from a COMPLETE synthetic generator export."""

import argparse
import csv
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path


def prepare(transactions, labels, configuration):
    start = datetime.fromisoformat(configuration['dataset']['start_date']).replace(
        tzinfo=timezone(timedelta(hours=7)))
    end = start + timedelta(days=configuration['dataset']['history_days'] + configuration['dataset']['test_days'])
    if len({row['transaction_id'] for row in transactions}) != len(transactions):
        raise ValueError('Duplicate transaction reference')
    if {row['transaction_id'] for row in transactions} != set(labels):
        raise ValueError('Transaction and label sets must match')
    converted = []
    accounts = set()
    for row in transactions:
        if row['status'] != 'SUCCESS' or row['currency'] != 'VND':
            raise ValueError('Benchmark requires completed VND transfers')
        time = datetime.fromisoformat(row['transaction_datetime'])
        posting = datetime.fromisoformat(row['posting_datetime'])
        if not start <= time < end or posting < time:
            raise ValueError('Transaction/posting time outside generator contract')
        accounts.update((row['sender_account_id'], row['receiver_account_id']))
        converted.append({
            'transaction_ref': row['transaction_id'],
            'from_account_ref': row['sender_account_id'],
            'from_account_masked': '****' + row['sender_account_id'][-4:],
            'from_customer_name': None,
            'to_account_ref': row['receiver_account_id'],
            'to_account_masked': '****' + row['receiver_account_id'][-4:],
            'to_customer_name': None,
            'amount': row['amount_vnd'], 'currency': row['currency'],
            'transaction_time': row['transaction_datetime'],
            'posted_at': row['posting_datetime'],
            'source_label': labels[row['transaction_id']],
            'original_input': None, 'legacy_result': None,
        })
    # Only valid for a full generator export: this is never written to production.
    through = max([end] + [datetime.fromisoformat(row['posted_at']) for row in converted])
    coverage = [{
        'account_id': account, 'opened_at': None,
        'outbound_from': start.isoformat(), 'outbound_through': through.isoformat(),
        'inbound_posted_from': start.isoformat(), 'inbound_posted_through': through.isoformat(),
        'posting_complete': True,
    } for account in sorted(accounts)]
    return {'transactions': converted, 'coverage': coverage}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--transactions', type=Path, required=True)
    parser.add_argument('--labels', type=Path, required=True)
    parser.add_argument('--generator-config', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    with args.transactions.open(encoding='utf-8-sig', newline='') as handle:
        transactions = list(csv.DictReader(handle))
    with args.labels.open(encoding='utf-8-sig', newline='') as handle:
        rows = list(csv.DictReader(handle))
    if len({row['transaction_id'] for row in rows}) != len(rows):
        raise ValueError('Duplicate label reference')
    labels = {row['transaction_id']: row for row in rows}
    snapshot = prepare(transactions, labels, json.loads(args.generator_config.read_text(encoding='utf-8-sig')))
    args.output.write_text(json.dumps(snapshot))
    print(json.dumps({'transactions': len(snapshot['transactions']), 'coverage': len(snapshot['coverage'])}))


if __name__ == '__main__':
    main()
