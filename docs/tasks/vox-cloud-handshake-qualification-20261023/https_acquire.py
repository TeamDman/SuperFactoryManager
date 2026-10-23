#!/usr/bin/env python3
"""Bounded verified-HTTPS download; redirects remain private and host-checked."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urljoin, urlsplit

p = argparse.ArgumentParser()
p.add_argument('url')
p.add_argument('output', type=Path)
p.add_argument('receipt', type=Path)
p.add_argument('--host', action='append', required=True)
p.add_argument('--max-time', default=120, type=int)
a = p.parse_args()
receipt = {'source_url': a.url, 'authorized_hosts': a.host,
           'started_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
           'ca_file': os.environ['SSL_CERT_FILE'], 'ca_path': os.environ['SSL_CERT_DIR'],
           'tls_verification': True, 'retries': 0, 'redirects': [], 'result': 'pending'}
partial = a.output.with_suffix(a.output.suffix + '.partial')
url = a.url
try:
    if a.output.exists() or partial.exists():
        raise RuntimeError('Output already exists; inspect before another acquisition')
    for step in range(6):
        parsed = urlsplit(url)
        if parsed.scheme != 'https' or parsed.username or parsed.password or parsed.port not in (443, None):
            raise RuntimeError('Refusing redirect outside ordinary credential-free HTTPS')
        if parsed.hostname not in a.host:
            receipt['blocked_host'] = parsed.hostname
            raise RuntimeError('Redirect host is outside authorized host set')
        # Signed redirect URLs are passed on stdin, never the command line/logs.
        config = 'url = "' + url.replace('\\', '\\\\').replace('"', '\\"') + '"\n'
        cmd = ['/usr/bin/curl', '-q', '--cacert', receipt['ca_file'], '--capath', receipt['ca_path'],
               '--proto', '=https', '--proto-redir', '=https', '--max-redirs', '0',
               '--connect-timeout', '20', '--max-time', str(a.max_time), '--fail',
               '--silent', '--show-error', '--dump-header', '-', '--output', str(partial),
               '--write-out', '\nCURL_METADATA:%{json}', '--config', '-']
        result = subprocess.run(cmd, input=config, text=True, capture_output=True)
        head, marker, meta_text = result.stdout.rpartition('\nCURL_METADATA:')
        meta = json.loads(meta_text) if marker else {}
        safe = {k: meta.get(k) for k in ('http_code', 'http_connect', 'ssl_verify_result',
                                       'size_download', 'time_total', 'http_version')}
        safe.update({'host': parsed.hostname, 'curl_exit': result.returncode})
        # Save only non-sensitive response metadata; omit all Location and cookie headers.
        for key in ('content-type', 'content-length', 'etag', 'last-modified'):
            matches = re.findall(r'^' + key + r':\s*([^\r\n]*)', head, re.I | re.M)
            if matches:
                safe[key] = matches[-1]
        receipt['redirects'].append(safe)
        print(json.dumps(safe), flush=True)
        if result.returncode:
            # curl may include a signed URL in error text: report code and host only.
            raise RuntimeError(f'HTTPS acquisition stopped at {parsed.hostname}; curl exit {result.returncode}, HTTP {meta.get("http_code")}, CONNECT {meta.get("http_connect")}')
        if meta.get('ssl_verify_result') != 0:
            raise RuntimeError('TLS verification result was not zero')
        status = int(meta.get('http_code', 0))
        if status in (301, 302, 303, 307, 308):
            locations = re.findall(r'^location:\s*([^\r\n]*)', head, re.I | re.M)
            if not locations:
                raise RuntimeError('Redirect missing Location')
            url = urljoin(url, locations[-1])
            safe['next_host'] = urlsplit(url).hostname
            continue
        if status != 200:
            raise RuntimeError(f'Unexpected final HTTP status {status}')
        partial.rename(a.output)
        receipt.update({'result': 'downloaded', 'bytes': a.output.stat().st_size,
                        'sha256': hashlib.file_digest(a.output.open('rb'), 'sha256').hexdigest(),
                        'output': str(a.output), 'final_host': parsed.hostname})
        break
    else:
        raise RuntimeError('Redirect limit reached')
except Exception as e:
    receipt['result'] = 'blocked'
    receipt['error'] = str(e)
finally:
    receipt['finished_utc'] = datetime.datetime.now(datetime.timezone.utc).isoformat()
    a.receipt.write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps({k: v for k, v in receipt.items() if k != 'redirects'}, indent=2))
sys.exit(0 if receipt['result'] == 'downloaded' else 1)
