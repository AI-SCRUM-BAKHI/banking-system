#!/usr/bin/env python3
"""Compare engine results to CSV ground truth without using labels as features."""

import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import json
from pathlib import Path


def ratio(numerator, denominator):
    return numerator / denominator if denominator else None


def metrics(rows):
    counts = Counter(row['outcome'] for row in rows)
    tp, fp, fn, tn = (counts[key] for key in ('TP', 'FP', 'FN', 'TN'))
    decided = tp + fp + fn + tn
    positives = sum(row['label'] for row in rows)
    return {
        'total': len(rows), 'positive_labels': positives,
        'negative_labels': len(rows) - positives,
        'TP': tp, 'FP': fp, 'FN': fn, 'TN': tn,
        'unknown_fraud': counts['UNKNOWN_FRAUD'],
        'unknown_normal': counts['UNKNOWN_NORMAL'],
        'decided': decided, 'decision_coverage': ratio(decided, len(rows)),
        'complete': sum(row['evaluation_status'] == 'COMPLETE' for row in rows),
        'partial': sum(row['evaluation_status'] == 'PARTIAL' for row in rows),
        'precision': ratio(tp, tp + fp),
        'recall_on_decided': ratio(tp, tp + fn),
        'f1_on_decided': ratio(2 * tp, 2 * tp + fp + fn),
        'f2_on_decided': ratio(5 * tp, 5 * tp + fp + 4 * fn),
        'accuracy_on_decided': ratio(tp + tn, decided),
        'detection_rate_all_fraud_labels': ratio(tp, positives),
    }


def compare(labels, results):
    if set(labels) != set(results):
        raise ValueError('Label/result transaction references do not match exactly')
    rows = []
    for ref, label in sorted(labels.items()):
        result = results[ref]
        actual = int(label['is_fraud'])
        if actual not in (0, 1):
            raise ValueError('Ground truth must be binary')
        predicted = result.get('isFraud')
        status = result['evaluationStatus']
        decision = result['decision']
        if predicted is not None and type(predicted) is not bool:
            raise ValueError('isFraud must be boolean or null')
        if predicted is None:
            if status != 'PARTIAL' or decision != 'INSUFFICIENT_DATA':
                raise ValueError('Abstention must be PARTIAL/INSUFFICIENT_DATA')
            outcome = 'UNKNOWN_FRAUD' if actual else 'UNKNOWN_NORMAL'
        elif predicted:
            if decision != 'FRAUD' or result['riskScore'] < result['fraudThreshold']:
                raise ValueError('Positive prediction conflicts with engine decision/threshold')
            outcome = 'TP' if actual else 'FP'
        else:
            if status != 'COMPLETE' or decision != 'NOT_FRAUD':
                raise ValueError('Negative prediction must be COMPLETE/NOT_FRAUD')
            outcome = 'FN' if actual else 'TN'
        if result['transactionRef'] != ref:
            raise ValueError('Result transaction reference mismatch')
        rows.append({
            'transaction_ref': ref, 'label': actual,
            'fraud_type': label['fraud_type'], 'data_split': label['data_split'],
            'predicted_fraud': predicted, 'risk_score': result['riskScore'],
            'risk_level': result['riskLevel'], 'decision': decision,
            'evaluation_status': status, 'rule_version': result['ruleVersion'],
            'outcome': outcome,
            'triggered_rules': ';'.join(rule['ruleCode'] for rule in result['triggeredRules']),
            'unevaluated_rules': ';'.join(rule['ruleCode'] for rule in result['unevaluatedRules']),
        })
    return rows


def grouped(rows, field):
    groups = defaultdict(list)
    for row in rows:
        groups[row[field]].append(row)
    return {key: metrics(value) for key, value in sorted(groups.items())}


def load_labels(path):
    labels = {}
    with path.open(encoding='utf-8-sig', newline='') as handle:
        for row in csv.DictReader(handle):
            ref = row['transaction_id']
            if ref in labels:
                raise ValueError('Duplicate label reference')
            labels[ref] = row
    return labels


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--labels', type=Path, required=True)
    parser.add_argument('--snapshot', type=Path, required=True)
    parser.add_argument('--scan-results', type=Path, required=True)
    parser.add_argument('--baseline-results', type=Path,
                        help='Optional v1 runner output for snapshots without stored legacy evaluations')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    labels = load_labels(args.labels)
    snapshot = json.loads(args.snapshot.read_text())
    scan = json.loads(args.scan_results.read_text())
    if any(row['source_label'] != labels.get(row['transaction_ref']) for row in snapshot['transactions']):
        raise ValueError('Stored source labels differ from the provided CSV')
    if scan['errors']:
        raise ValueError('Scanner has failed/incomplete transactions')
    legacy = {row['transaction_ref']: row['legacy_result'] for row in snapshot['transactions']}
    if args.baseline_results:
        baseline = json.loads(args.baseline_results.read_text())
        if baseline['errors']:
            raise ValueError('Baseline scanner has failed/incomplete transactions')
        legacy = baseline['results']
    if any(result is None for result in legacy.values()):
        raise ValueError('Snapshot has no legacy evaluations; supply --baseline-results')
    scan_rows = compare(labels, scan['results'])
    legacy_rows = compare(labels, legacy)
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    for name, rows in [('scan_predictions', scan_rows), ('legacy_predictions', legacy_rows),
                       ('false_predictions', [row for row in scan_rows if row['outcome'] in ('FP', 'FN')]),
                       ('insufficient_data', [row for row in scan_rows if row['predicted_fraud'] is None])]:
        with (output / (name + '.csv')).open('w', newline='') as handle:
            writer = csv.DictWriter(handle, fieldnames=list(scan_rows[0]))
            writer.writeheader()
            writer.writerows(rows)
    differences = []
    for ref, result in sorted(scan['results'].items()):
        previous = legacy[ref]
        fields = ['isFraud', 'riskScore', 'riskLevel', 'decision', 'evaluationStatus']
        changes = {field: {'legacy': previous[field], 'scan': result[field]}
                   for field in fields if previous[field] != result[field]}
        if changes:
            differences.append({'transaction_ref': ref, 'changes': changes})
    (output / 'flow_differences.json').write_text(json.dumps(differences, indent=2))
    reasons = Counter()
    skipped = Counter()
    for result in scan['results'].values():
        for rule in result['unevaluatedRules']:
            skipped[rule['ruleCode']] += 1
            reasons[rule['ruleCode'] + ': ' + rule['reason']] += 1
    report = {
        'label_sha256': hashlib.sha256(args.labels.read_bytes()).hexdigest(),
        'snapshot_sha256': hashlib.sha256(args.snapshot.read_bytes()).hexdigest(),
        'scan': metrics(scan_rows), 'legacy': metrics(legacy_rows),
        'scan_by_split': grouped(scan_rows, 'data_split'),
        'scan_by_fraud_type': grouped(scan_rows, 'fraud_type'),
        'scan_by_evaluation_status': grouped(scan_rows, 'evaluation_status'),
        'legacy_by_split': grouped(legacy_rows, 'data_split'),
        'unevaluated_rule_counts': dict(skipped),
        'unevaluated_reasons': dict(reasons),
        'different_predictions_or_scores': len(differences),
        'scan_execution': {key: value for key, value in scan.items() if key != 'results'},
    }
    event_groups = defaultdict(list)
    for row in scan_rows:
        event_id = labels[row['transaction_ref']]['fraud_event_id']
        if row['label'] and event_id:
            event_groups[event_id].append(row)
    report['event_detection'] = {
        'total_labelled_events': len(event_groups),
        'events_with_at_least_one_positive_prediction': sum(
            any(row['predicted_fraud'] is True for row in rows) for rows in event_groups.values()),
        'note': 'Supplementary event metric: a positive prediction on any labelled leg detects the event.',
    }
    report['rule_matches_by_label_type'] = {}
    for kind in sorted({row['fraud_type'] for row in scan_rows if row['label']}):
        selected = [row for row in scan_rows if row['fraud_type'] == kind]
        report['rule_matches_by_label_type'][kind] = {
            'labelled_transactions': len(selected),
            'same_named_rule_matched': sum(kind in row['triggered_rules'].split(';') for row in selected),
            'score_distribution': dict(Counter(row['risk_score'] for row in selected)),
        }
    (output / 'metrics.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
