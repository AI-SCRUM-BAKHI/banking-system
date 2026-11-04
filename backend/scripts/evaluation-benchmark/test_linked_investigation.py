import unittest
from linked_investigation import linked_cases


class InvestigationEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.results = {'early': {'isFraud': False}, 'now': {'isFraud': True}, 'baseline': {'isFraud': False}}
        self.times = {'early': 1, 'now': 2, 'baseline': 0}

    def case(self, code='STRUCTURED_SPLIT', status='MATCHED', refs=None):
        return [{'root': 'now', 'rules': [{'ruleCode': code, 'status': status,
            'evidence': ['baseline', 'early', 'now'],
            'features': {'patternTransactionRefs': refs or ['early', 'now']}}]}]

    def test_only_pattern_members_are_linked_not_old_baseline(self):
        links = linked_cases(self.results, self.case(), self.times)
        self.assertEqual(set(links), {'early', 'now'})
        self.assertEqual(links['early'], {'now'})
        self.assertFalse(self.results['early']['isFraud'])

    def test_amount_audit_cannot_promote_its_baseline_into_fraud(self):
        links = linked_cases(self.results, self.case('AMOUNT_SPIKE'), self.times)
        self.assertEqual(set(links), {'now'})

    def test_unmatched_pattern_cannot_link_old_transactions(self):
        links = linked_cases(self.results, self.case(status='NOT_MATCHED'), self.times)
        self.assertEqual(set(links), {'now'})

    def test_future_transaction_is_rejected(self):
        self.times['early'] = 3
        with self.assertRaises(ValueError):
            linked_cases(self.results, self.case(), self.times)

    def test_missing_reference_or_nonpositive_root_is_rejected(self):
        with self.assertRaises(ValueError):
            linked_cases(self.results, self.case(refs=['missing', 'now']), self.times)
        self.results['now']['isFraud'] = None
        with self.assertRaises(ValueError):
            linked_cases(self.results, self.case(), self.times)


if __name__ == '__main__':
    unittest.main()
