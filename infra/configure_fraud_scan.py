"""Update only scanner settings, preserving the existing server environment."""

import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import urlsplit

VERSIONS = {'rule-score-v1', 'rule-score-v2', 'rule-score-v3-recall'}


def configure(path, version, enabled, backup_dir):
    if version not in VERSIONS or enabled not in ('true', 'false'):
        raise ValueError('Unsupported scanner configuration')
    original = path.read_text()
    values = {}
    for line in original.splitlines():
        match = re.match(r'^(?:export\s+)?([A-Z][A-Z0-9_]*)=(.*)$', line)
        if match:
            values[match[1]] = match[2].strip().strip('\"\x27')
    url = values.get('SPRING_DATASOURCE_URL') or values.get('DATABASE_URL', '')
    parsed = urlsplit(url)
    if parsed.scheme not in ('postgres', 'postgresql') or not parsed.hostname or not parsed.hostname.endswith('.neon.tech'):
        raise ValueError('Expected the existing Neon PostgreSQL connection')
    # Scanner leases use session advisory locks, which require a direct session.
    userinfo, separator, hostport = parsed.netloc.rpartition('@')
    hostport = hostport.replace(parsed.hostname, parsed.hostname.replace('-pooler.', '.'), 1)
    direct_url = parsed._replace(netloc=userinfo + separator + hostport).geturl()
    updates = {
        'APP_FRAUD_RULE_VERSION': version,
        'APP_FRAUD_SCAN_ENABLED': enabled,
        'APP_FRAUD_SCAN_BATCH_SIZE': values.get('APP_FRAUD_SCAN_BATCH_SIZE', '100'),
        'APP_FRAUD_SCAN_FIXED_DELAY_MS': values.get('APP_FRAUD_SCAN_FIXED_DELAY_MS', '30000'),
        'SPRING_DATASOURCE_URL': direct_url,
    }
    backup_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    backup = backup_dir / ('backend.env.' + datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
    with backup.open('x') as handle:
        os.chmod(backup, 0o600)
        handle.write(original)
    lines = []
    written = set()
    for line in original.splitlines():
        match = re.match(r'^(?:export\s+)?([A-Z][A-Z0-9_]*)=', line)
        if match and match[1] in updates:
            if match[1] not in written:
                lines.append(match[1] + '=' + updates[match[1]])
                written.add(match[1])
        else:
            lines.append(line)
    lines.extend(key + '=' + value for key, value in updates.items() if key not in written)
    with tempfile.NamedTemporaryFile('w', dir=path.parent, prefix='.scanner-env-', delete=False) as handle:
        handle.write('\n'.join(lines) + '\n')
        pending = Path(handle.name)
    os.chmod(pending, path.stat().st_mode & 0o777)
    os.replace(pending, path)
    return {'ruleVersion': version, 'scanEnabled': enabled == 'true', 'databaseSession': 'direct', 'environmentBackupCreated': True}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', required=True, choices=sorted(VERSIONS))
    parser.add_argument('--enabled', required=True, choices=['true', 'false'])
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    print(json.dumps(configure(root / 'backend/.env', args.version, args.enabled,
        Path.home() / '.local/state/banking-system/env-backups')))
