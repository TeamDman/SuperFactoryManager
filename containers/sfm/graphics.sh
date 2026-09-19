#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
import os
from pathlib import Path
assert os.getuid() == 10001
status = dict(line.split(':', 1) for line in Path('/proc/self/status').read_text().splitlines())
assert int(status['CapEff'].strip(), 16) == 0
assert status['NoNewPrivs'].strip() == '1'
assert status['Seccomp'].strip() == '2'
assert {p.name for p in Path('/sys/class/net').iterdir()} == {'lo'}
mounts = [line.split() for line in Path('/proc/mounts').read_text().splitlines()]
assert 'ro' in next(fields for fields in mounts if fields[1] == '/')[3].split(',')
print('Isolation: nonroot, no capabilities, no new privileges, seccomp, no network, read-only root')
PY
xvfb-run --auto-servernum --server-args='-screen 0 1280x720x24 -nolisten tcp' \
    bash -euo pipefail -c 'glxinfo -B | tee /tmp/glxinfo.txt; grep -qi llvmpipe /tmp/glxinfo.txt'
echo 'GRAPHICS_PROBE_PASSED renderer=llvmpipe'
