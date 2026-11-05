import unittest
from compare_results import compare, metrics


def fixture(actual, prediction):
    label = {'is_fraud': str(actual), 'fraud_type': 'TEST', 'data_split': 'test'}
    result = {'transactionRef': 'TX1', 'isFraud': prediction,
              'riskScore': 90 if prediction else 0, 'fraudThreshold': 60,
              'riskLevel': 'HIGH' if prediction else 'LOW',
              'decision': 'INSUFFICIENT_DATA' if prediction is None else 'FRAUD' if prediction else 'NOT_FRAUD',
              'evaluationStatus': 'PARTIAL' if prediction is None else 'COMPLETE',
              'ruleVersion': 'v1', 'triggeredRules': [], 'unevaluatedRules': []}
    return {'TX1': label}, {'TX1': result}


class ComparisonTest(unittest.TestCase):
    def test_abstention_is_not_a_true_negative_or_false_negative(self):
        for actual in (0, 1):
            labels, results = fixture(actual, None)
            m = metrics(compare(labels, results))
            self.assertEqual(m['TN'] + m['FN'], 0)
            self.assertEqual(m['decided'], 0)
            self.assertEqual(m['unknown_fraud' if actual else 'unknown_normal'], 1)

    def test_confusion_matrix_and_metrics(self):
        rows = []
        for actual, predicted in [(1, True), (0, True), (1, False), (0, False), (1, None)]:
            rows.extend(compare(*fixture(actual, predicted)))
        m = metrics(rows)
        self.assertEqual((m['TP'], m['FP'], m['FN'], m['TN']), (1, 1, 1, 1))
        self.assertEqual(m['precision'], .5)
        self.assertEqual(m['recall_on_decided'], .5)
        self.assertEqual(m['f1_on_decided'], .5)
        self.assertEqual(m['detection_rate_all_fraud_labels'], 1 / 3)
        self.assertEqual(m['decision_coverage'], .8)

    def test_zero_denominators_are_undefined(self):
        m = metrics([])
        self.assertIsNone(m['precision'])
        self.assertIsNone(m['recall_on_decided'])
        self.assertIsNone(m['f1_on_decided'])

    def test_missing_results_cannot_silently_reduce_test_population(self):
        labels, _ = fixture(1, True)
        with self.assertRaises(ValueError):
            compare(labels, {})

    def test_inconsistent_prediction_and_threshold_are_rejected(self):
        labels, results = fixture(1, True)
        results['TX1']['riskScore'] = 59
        with self.assertRaises(ValueError):
            compare(labels, results)

    def test_partial_fraud_still_counts_as_positive(self):
        labels, results = fixture(1, True)
        results['TX1']['evaluationStatus'] = 'PARTIAL'
        m = metrics(compare(labels, results))
        self.assertEqual(m['TP'], 1)
        self.assertEqual(m['partial'], 1)

    def test_f2_penalizes_missed_fraud_more_than_a_false_alarm(self):
        tp = compare(*fixture(1, True))
        with_fn = metrics(tp + compare(*fixture(1, False)))
        with_fp = metrics(tp + compare(*fixture(0, True)))
        self.assertLess(with_fn['f2_on_decided'], with_fp['f2_on_decided'])


if __name__ == '__main__':
    unittest.main()
