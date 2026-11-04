#!/usr/bin/env python3
"""Offline investigation coverage from actual matched case evidence, never an online score."""

import argparse
from collections import defaultdict
import csv
from datetime import datetime
import json
from pathlib import Path

from compare_results import compare, grouped, load_labels, metrics

PATTERN_RULES = {'FREQUENCY_SPIKE', 'STRUCTURED_SPLIT', 'RAPID_PASS_THROUGH', 'MULTI_ACCOUNT_FLOW'}


def linked_cases(results, case_evidence, source_times):
    """Determine linked transactions using no labels, baseline membership or future inputs."""
    links = defaultdict(set)
    for root, result in results.items():
        if result['isFraud'] is True:
            links[root].add(root)
    for case in case_evidence:
        root = case['root']
        if root not in results or results[root]['isFraud'] is not True:
            raise ValueError('Linked case must have a positive online root')
        for rule in case['rules']:
            if rule['status'] != 'MATCHED' or rule['ruleCode'] not in PATTERN_RULES:
                continue
            # Deliberately NOT rule.evidence: split/amount audit includes old baseline rows.
            refs = rule['features'].get('patternTransactionRefs', [])
            if not refs:
                continue
            if root not in refs or any(ref not in results for ref in refs):
                raise ValueError('Pattern evidence is not part of the evaluated population')
            if any(source_times[ref] > source_times[root] for ref in refs):
                raise ValueError('Pattern evidence contains a future transaction')
            for ref in refs:
                links[ref].add(root)
    return links


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--snapshot', type=Path, required=True)
    parser.add_argument('--results', type=Path, required=True)
    parser.add_argument('--labels', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    source = json.loads(args.snapshot.read_text())
    run = json.loads(args.results.read_text())
    times = {row['transaction_ref']: datetime.fromisoformat(row['transaction_time'].replace('Z', '+00:00')) for row in source['transactions']}
    links = linked_cases(run['results'], run['caseEvidence'], times)
    # Ground truth is loaded only after the linking decisions have been made.
    labels = load_labels(args.labels)
    rows = compare(labels, run['results'])
    linked_rows = []
    for online in rows:
        row = dict(online)
        ref = row['transaction_ref']
        if ref in links:
            row['outcome'] = 'TP' if row['label'] else 'FP'
        linked_rows.append(row)
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / 'investigation_links.csv').open('w', newline='') as handle:
        writer = csv.DictWriter(handle, fieldnames=['transaction_ref', 'label', 'fraud_type', 'data_split', 'online_predicted_fraud', 'online_score', 'linked_alert', 'known_by_source_time', 'case_roots', 'outcome'])
        writer.writeheader()
        for online, linked in zip(rows, linked_rows):
            ref = online['transaction_ref']
            roots = sorted(links.get(ref, []))
            writer.writerow({
                'transaction_ref': ref, 'label': online['label'], 'fraud_type': online['fraud_type'], 'data_split': online['data_split'],
                'online_predicted_fraud': online['predicted_fraud'], 'online_score': online['risk_score'],
                'linked_alert': bool(roots), 'known_by_source_time': min((times[root] for root in roots), default=''),
                'case_roots': ';'.join(roots), 'outcome': linked['outcome'],
            })
    online_positives = {key for key, value in run['results'].items() if value['isFraud'] is True}
    report = {
        'mode': 'OFFLINE_CASE_EVIDENCE_COVERAGE_NOT_ONLINE_PREDICTION',
        'online': metrics(rows), 'investigation_coverage': metrics(linked_rows),
        'investigation_by_split': grouped(linked_rows, 'data_split'),
        'investigation_by_type': grouped(linked_rows, 'fraud_type'),
        'newly_linked_transactions': sum(ref not in online_positives for ref in links),
        'note': 'Online scores/decisions are unchanged. A linked alert can first exist only when a later matching case has formed; absence of linkage does not certify safety.',
    }
    (args.output / 'investigation_metrics.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({key: report[key] for key in ['mode', 'online', 'investigation_coverage']}, indent=2))


if __name__ == '__main__':
    main()
