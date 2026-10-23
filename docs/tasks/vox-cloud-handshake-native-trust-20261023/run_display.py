import json
import os
from pathlib import Path
import secrets
import signal
import subprocess

root = Path('/workspace/sfm-handshake-20261023/continuation')
assert not Path('/tmp/.X93-lock').exists()
auth = root / 'display.Xauthority'
auth.touch(mode=0o600, exist_ok=False)
subprocess.run(['xauth', '-f', str(auth)],
               input='add :93 . ' + secrets.token_hex(16) + '\n',
               text=True, check=True, capture_output=True)
command = ['/usr/bin/Xorg', ':93', '-config', str(root/'xorg-dummy.conf'),
           '-auth', str(auth), '-logfile', str(root/'xorg.log'),
           '-nolisten', 'tcp', '-noreset']
with (root/'xorg.stderr').open('w') as log:
    process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT,
                               start_new_session=True)
    (root/'display-process.json').write_text(json.dumps({
        'pid': process.pid, 'pgid': process.pid, 'command': command,
        'display': ':93', 'maximum_seconds': 1800,
        'authentication': 'task-local Xauthority cookie; not included in evidence',
    }, indent=2)+'\n')
    try:
        result = process.wait(timeout=1800)
    except subprocess.TimeoutExpired:
        process.terminate()
        try:
            result = process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            result = process.wait()
    (root/'display.exit').write_text(str(result)+'\n')
    print('Task display exited:', result)
