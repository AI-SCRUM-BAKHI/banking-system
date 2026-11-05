import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import json

spec = importlib.util.spec_from_file_location('ai_config', Path(__file__).resolve().parents[1] / 'configure_ai.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class AiConfigurationTest(unittest.TestCase):
    def test_updates_only_ai_settings_and_keeps_credentials_out_of_status(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env'
            original = '# existing server settings\nDATABASE_URL=postgresql://demo/db\nAPP_JWT_SECRET=keep-jwt\nAPP_SERVICE_TOKEN=keep-service\nOPENROUTER_MODEL=old/model\nOPENROUTER_MODEL=duplicate/model\n'
            path.write_text(original)
            status = module.configure(path, 'synthetic-secret', 'openrouter/free', 'https://openrouter.ai/api/v1', '60')
            updated = path.read_text()
            self.assertIn('DATABASE_URL=postgresql://demo/db', updated)
            self.assertIn('APP_JWT_SECRET=keep-jwt', updated)
            self.assertIn('APP_SERVICE_TOKEN=keep-service', updated)
            self.assertEqual(updated.count('OPENROUTER_MODEL='), 1)
            self.assertIn('OPENROUTER_MODEL="openrouter/free"', updated)
            self.assertNotIn('synthetic-secret', str(status))
            self.assertTrue(status['apiKeyConfigured'])

    def test_invalid_settings_never_modify_existing_environment(self):
        cases = [('', 'openrouter/free', 'https://openrouter.ai/api/v1', '60'),
                 ('secret', 'model\nINJECTED=true', 'https://openrouter.ai/api/v1', '60'),
                 ('secret', 'openrouter/free', 'https://unexpected.invalid/api/v1', '60'),
                 ('secret', 'openrouter/free', 'https://openrouter.ai/api/v1', '121')]
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env'
            original = 'APP_JWT_SECRET=preserve-this\n'
            for values in cases:
                path.write_text(original)
                with self.assertRaises(ValueError):
                    module.configure(path, *values)
                self.assertEqual(path.read_text(), original)

    def test_runtime_verification_reports_presence_without_disclosing_credentials(self):
        values = ['OPENROUTER_API_KEY=synthetic-secret', 'OPENROUTER_MODEL=openrouter/free',
                  'OPENROUTER_BASE_URL=https://openrouter.ai/api/v1', 'OPENROUTER_TIMEOUT_SECONDS=60',
                  'OPENROUTER_DISABLE_REASONING=true', 'APP_JWT_SECRET=other-secret']
        with patch.object(module.subprocess, 'run', return_value=SimpleNamespace(stdout=json.dumps(values))):
            status = module.verify_container('test-backend')
        self.assertTrue(all(status['configured'].values()))
        self.assertNotIn('synthetic-secret', str(status))
        self.assertNotIn('other-secret', str(status))
