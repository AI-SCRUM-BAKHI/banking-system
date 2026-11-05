"""Configure the existing backend environment without printing provider credentials."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


AI_KEYS = ('OPENROUTER_API_KEY', 'OPENROUTER_MODEL', 'OPENROUTER_BASE_URL', 'OPENROUTER_TIMEOUT_SECONDS', 'OPENROUTER_DISABLE_REASONING')


def configure(path, api_key, model, base_url, timeout_seconds):
    api_key = api_key.strip()
    if not api_key or any(ord(character) < 32 or ord(character) == 127 for character in api_key):
        raise ValueError('OPENROUTER_API_KEY must be a nonempty single-line credential')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:/-]{0,199}', model) or model.startswith('sk-or-'):
        raise ValueError('Invalid OPENROUTER_MODEL')
    if base_url.rstrip('/') != 'https://openrouter.ai/api/v1':
        raise ValueError('Expected the official OpenRouter API URL')
    try:
        timeout = int(timeout_seconds)
    except (ValueError, TypeError):
        raise ValueError('Timeout must be an integer from 1 to 120') from None
    if not 1 <= timeout <= 120:
        raise ValueError('Timeout must be an integer from 1 to 120')
    updates = dict(zip(AI_KEYS, (api_key, model, base_url.rstrip('/'), str(timeout), 'true')))
    original = path.read_text(encoding='utf-8-sig')
    lines = []
    for line in original.splitlines():
        match = re.match(r'^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)\s*=', line)
        if match and match[1] in updates:
            continue
        lines.append(line)
    for key, value in updates.items():
        # Compose expands dollar signs even inside double quotes.
        lines.append(key + '=' + json.dumps(value.replace('$', '$$')))
    pending = None
    try:
        with tempfile.NamedTemporaryFile('w', encoding='utf-8', dir=path.parent, prefix='.ai-env-', delete=False) as handle:
            pending = Path(handle.name)
            handle.write('\n'.join(lines) + '\n')
        os.chmod(pending, 0o600)
        os.replace(pending, path)
    finally:
        if pending is not None and pending.exists():
            pending.unlink()
    return {'apiKeyConfigured': True, 'model': model, 'baseUrl': base_url.rstrip('/'), 'timeoutSeconds': timeout}


def verify_container(name):
    result = subprocess.run(['docker', 'inspect', name, '--format', '{{json .Config.Env}}'], check=True, capture_output=True, text=True)
    values = dict(item.split('=', 1) for item in json.loads(result.stdout) if '=' in item)
    present = {key: bool(values.get(key, '').strip()) for key in AI_KEYS}
    if not all(present.values()):
        raise ValueError('Container is missing AI settings: ' + ', '.join(key for key, value in present.items() if not value))
    return {'container': name, 'configured': present}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify-container')
    args = parser.parse_args()
    if args.verify_container:
        print(json.dumps(verify_container(args.verify_container)))
    else:
        root = Path(__file__).resolve().parents[1]
        print(json.dumps(configure(root / 'backend/.env',
            os.environ.get('AI_OPENROUTER_API_KEY', ''),
            os.environ.get('AI_OPENROUTER_MODEL', ''),
            os.environ.get('AI_OPENROUTER_BASE_URL', 'https://openrouter.ai/api/v1'),
            os.environ.get('AI_OPENROUTER_TIMEOUT_SECONDS', '60'))))
