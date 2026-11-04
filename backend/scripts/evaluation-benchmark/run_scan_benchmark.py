import argparse
import base64
import shutil
from urllib.parse import urlsplit
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import psycopg
from psycopg import sql

ROOT=Path(__file__).resolve().parents[3]
parser=argparse.ArgumentParser(description='Run the actual scanner on a fresh LOCAL PostgreSQL database; never writes to Neon.')
parser.add_argument('--snapshot', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--version', choices=['rule-score-v1','rule-score-v2','rule-score-v3-recall'], required=True)
parser.add_argument('--postgres-url', default='postgresql://minhnq:localonly@127.0.0.1:55439/postgres')
parser.add_argument('--jar', type=Path, default=ROOT/'backend/app-bootstrap/build/libs/scrum-ai-backend.jar')
args=parser.parse_args()
SNAPSHOT=args.snapshot
DB='banking_scan_benchmark_'+secrets.token_hex(4)
ADMIN=args.postgres_url
parsed=urlsplit(ADMIN)
if parsed.hostname not in ('127.0.0.1','localhost','::1'):
    raise ValueError('Benchmark may only create a local test database')
URL=parsed._replace(path='/'+DB).geturl()
data=json.loads(SNAPSHOT.read_text())
total=len(data['transactions'])
assert total > 0
with psycopg.connect(ADMIN,autocommit=True) as c:
    c.execute(sql.SQL('CREATE DATABASE {}').format(sql.Identifier(DB)))
with psycopg.connect(URL) as c:
    for migration in sorted((ROOT/'backend/database/src/main/resources/db/migration').glob('V*.sql'), key=lambda p: int(p.name.split('__')[0][1:])):
        c.execute(migration.read_text(),prepare=False)
    fields=['transaction_ref','from_account_ref','from_account_masked','from_customer_name','to_account_ref','to_account_masked','to_customer_name','amount','currency','transaction_time','posted_at']
    with c.cursor().copy('COPY transactions ('+','.join(fields)+') FROM STDIN') as cp:
        for row in data['transactions']: cp.write_row([row[f] for f in fields])
    fields=list(data['coverage'][0])
    with c.cursor().copy('COPY risk_account_coverage ('+','.join(fields)+') FROM STDIN') as cp:
        for row in data['coverage']: cp.write_row([row[f] for f in fields])
    assert c.execute("SELECT count(*) FROM transactions WHERE source_label<>'{}'::jsonb").fetchone()[0]==0
    assert c.execute('SELECT count(*) FROM risk_transfer_history').fetchone()[0]==0
    c.execute('ANALYZE transactions')

env=dict(os.environ,DATABASE_URL=URL,APP_JWT_SECRET=base64.b64encode(secrets.token_bytes(32)).decode(),
    APP_SERVICE_TOKEN=secrets.token_urlsafe(32),APP_FRAUD_RULE_VERSION=args.version,APP_FRAUD_SCAN_ENABLED='true',APP_FRAUD_SCAN_BATCH_SIZE='100',
    APP_FRAUD_SCAN_FIXED_DELAY_MS='100',SERVER_PORT='18091',SPRING_FLYWAY_ENABLED='false',
    APP_BOOTSTRAP_ADMIN_USERNAME='',APP_BOOTSTRAP_ADMIN_PASSWORD='')
args.output.parent.mkdir(parents=True, exist_ok=True)
log_path=args.output.with_suffix('.backend.log')
process=None
start=time.monotonic()
try:
    with log_path.open('w') as log:
        process=subprocess.Popen([str(Path(os.environ['JAVA_HOME'])/'bin/java') if os.environ.get('JAVA_HOME') else shutil.which('java'),'-Xmx768m','-jar',str(args.jar.resolve())],cwd='/tmp',env=env,stdout=log,stderr=subprocess.STDOUT)
        print(json.dumps({'local_database':DB,'source_rows':total,'coverage_rows':len(data['coverage']),'rule_version':args.version,'labels_excluded_from_engine':True,'initial_history':0}),flush=True)
        last=None
        while time.monotonic()-start<1200:
            if process.poll() is not None: raise RuntimeError('Benchmark backend exited; inspect local log')
            with psycopg.connect(URL) as c:
                state=dict(c.execute('SELECT processing_status,count(*) FROM transactions GROUP BY processing_status').fetchall())
            if state!=last:
                print(json.dumps({'elapsed_seconds':round(time.monotonic()-start),'states':state}),flush=True)
                last=state
            if state.get('COMPLETED',0)+state.get('FAILED',0)==total: break
            time.sleep(5)
        else: raise RuntimeError('Benchmark timed out')
        with psycopg.connect(URL) as c:
            results={ref:result for ref,result in c.execute('SELECT transaction_ref,result_json::jsonb FROM risk_transfer_history ORDER BY ingest_order').fetchall()}
            errors=c.execute("SELECT transaction_ref,last_error FROM transactions WHERE processing_status<>'COMPLETED'").fetchall()
            counts={table:c.execute('SELECT count(*) FROM '+table).fetchone()[0] for table in ['transactions','risk_transfer_history','fraud_transactions','fraud_rule_hits','transaction_chain']}
            audit_sources=c.execute("SELECT audit_json::jsonb->>'contextSource',count(*) FROM risk_transfer_history GROUP BY 1").fetchall()
            case_evidence=[{'root':ref,'rules':rules} for ref,rules in c.execute("SELECT transaction_ref,audit_json::jsonb->'rules' FROM risk_transfer_history WHERE result_json::jsonb->>'isFraud'='true'").fetchall()]
        args.output.write_text(json.dumps({'results':results,'caseEvidence':case_evidence,'errors':errors,'counts':counts,'auditSources':audit_sources,'elapsedSeconds':round(time.monotonic()-start),'localDatabase':DB},indent=2))
        print(json.dumps({'finished':True,'counts':counts,'errors':len(errors),'auditSources':audit_sources,'elapsed_seconds':round(time.monotonic()-start)}),flush=True)
finally:
    if process and process.poll() is None:
        process.terminate()
        try: process.wait(timeout=30)
        except subprocess.TimeoutExpired: process.kill();process.wait(timeout=10)
