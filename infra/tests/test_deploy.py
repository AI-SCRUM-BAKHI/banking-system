import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]


class DeploymentReadinessTest(unittest.TestCase):
    def run_deploy(self, backend='ready', frontend='ready'):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            log = directory / 'calls'
            commands = {
                'docker': '#!/bin/bash\necho "docker $*" >> "$CALL_LOG"\n',
                'sleep': '#!/bin/bash\nexit 0\n',
                'curl': '''#!/bin/bash
echo "curl $*" >> "$CALL_LOG"
if [[ "$*" == *":8080/"* ]]; then
  count_file="$TEST_DIR/backend_attempts"
  count=0
  [[ ! -f "$count_file" ]] || read -r count < "$count_file"
  count=$((count+1))
  echo "$count" > "$count_file"
  mode="$BACKEND_MODE"
  if [[ "$mode" == ready && "$count" -eq 1 ]]; then exit 7; fi
else
  mode="$FRONTEND_MODE"
fi
case "$mode" in
  ready) echo '{"status":"UP"}';;
  html) echo '<html>frontend index</html>';;
  *) exit 7;;
esac
''',
            }
            for name, script in commands.items():
                file = directory / name
                file.write_text(script)
                file.chmod(0o755)
            env = dict(os.environ, PATH=str(directory) + os.pathsep + os.environ['PATH'],
                       CALL_LOG=str(log), TEST_DIR=str(directory), BACKEND_MODE=backend,
                       FRONTEND_MODE=frontend, DEPLOY_HEALTH_ATTEMPTS='3', DEPLOY_HEALTH_DELAY_SECONDS='0')
            result = subprocess.run(['bash', str(ROOT / 'infra/deploy.sh')], env=env,
                                    capture_output=True, text=True, timeout=10)
            return result, log.read_text().splitlines()

    def test_backend_must_be_ready_before_nginx_reload_and_frontend_probe(self):
        result, calls = self.run_deploy()
        self.assertEqual(result.returncode, 0, result.stderr)
        backend_checks = [i for i, call in enumerate(calls) if ':8080/' in call]
        reload = next(i for i, call in enumerate(calls) if 'nginx -s reload' in call)
        proxy_check = next(i for i, call in enumerate(calls) if ':5173/' in call)
        self.assertEqual(len(backend_checks), 2)
        self.assertLess(backend_checks[-1], reload)
        self.assertLess(reload, proxy_check)
        self.assertIn('backend and frontend API are ready', result.stdout)

    def test_backend_failure_cannot_report_success_or_reload_frontend(self):
        result, calls = self.run_deploy(backend='down')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any('nginx -s reload' in call for call in calls))
        self.assertNotIn('Deployment completed:', result.stdout)

    def test_frontend_proxy_failure_fails_deployment_after_backend_ready(self):
        result, calls = self.run_deploy(frontend='down')
        self.assertNotEqual(result.returncode, 0)
        self.assertTrue(any('nginx -s reload' in call for call in calls))
        self.assertNotIn('Deployment completed:', result.stdout)

    def test_http_200_html_is_not_an_api_readiness_success(self):
        result, _ = self.run_deploy(frontend='html')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('Deployment completed:', result.stdout)


if __name__ == '__main__':
    unittest.main()
