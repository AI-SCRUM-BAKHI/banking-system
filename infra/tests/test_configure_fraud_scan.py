import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('scanner_config', Path(__file__).resolve().parents[1] / 'configure_fraud_scan.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ScannerConfigurationTest(unittest.TestCase):
    def test_preserves_credentials_cors_and_other_environment_settings(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = root / '.env'
            original = '# Keep this comment\nDATABASE_URL=postgresql://owner:example@ep-test-pooler.c-4.ap-southeast-1.aws.neon.tech/db?sslmode=require\nAPP_CORS_ALLOWED_ORIGINS=http://localhost:5173\nAPP_SERVICE_TOKEN=unchanged-example\nAPP_FRAUD_SCAN_ENABLED=false\n'
            env.write_text(original)
            status = module.configure(env, 'rule-score-v3-recall', 'true', root / 'backups')
            text = env.read_text()
            self.assertIn('DATABASE_URL=postgresql://owner:example@ep-test-pooler.', text)
            self.assertIn('SPRING_DATASOURCE_URL=postgresql://owner:example@ep-test.c-4.', text)
            self.assertIn('APP_CORS_ALLOWED_ORIGINS=http://localhost:5173', text)
            self.assertIn('APP_SERVICE_TOKEN=unchanged-example', text)
            self.assertIn('APP_FRAUD_SCAN_ENABLED=true', text)
            self.assertNotIn('owner:example', str(status))
            self.assertEqual(next((root / 'backups').iterdir()).read_text(), original)

    def test_duplicate_settings_are_replaced_once_and_disable_is_supported(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = root / '.env'
            env.write_text('DATABASE_URL=postgresql://u:p@ep-test.neon.tech/db\nAPP_FRAUD_SCAN_ENABLED=true\nAPP_FRAUD_SCAN_ENABLED=true\n')
            module.configure(env, 'rule-score-v3-recall', 'false', root / 'backups')
            self.assertEqual(env.read_text().count('APP_FRAUD_SCAN_ENABLED='), 1)
            self.assertIn('APP_FRAUD_SCAN_ENABLED=false', env.read_text())

    def test_unexpected_database_does_not_modify_the_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            env = root / '.env'
            original = 'DATABASE_URL=postgresql://u:p@localhost/db\n'
            env.write_text(original)
            with self.assertRaises(ValueError):
                module.configure(env, 'rule-score-v3-recall', 'true', root / 'backups')
            self.assertEqual(env.read_text(), original)
