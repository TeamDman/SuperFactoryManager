#!/usr/bin/env bash
set -euo pipefail

mode=${1:-verify}
if [[ $# -gt 1 || ( "$mode" != prepare && "$mode" != verify ) ]]; then
    echo 'Usage: run.sh [prepare|verify]' >&2
    exit 2
fi

cd /workspace
artifacts=/workspace/container-artifacts
# These paths are disposable, container-owned outputs; never accept them as user input.
rm -rf "$artifacts" \
    /workspace/platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview \
    /workspace/platform/minecraft/runGameTestPreview/screenshots
mkdir -p "$artifacts"
cp source-revision.txt "$artifacts/"

collect_artifacts() {
    local status=$?
    trap - EXIT
    local previews=/workspace/platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview
    if [[ -d "$previews" ]]; then
        cp -a "$previews" "$artifacts/previews"
    fi
    # Do not copy the home directory or game-instance descriptors: they contain RPC tokens.
    local logs=/workspace/platform/minecraft/runGameTestPreview/logs
    if [[ -d "$logs" ]]; then cp -a "$logs" "$artifacts/game-logs"; fi
    printf '%s\n' "$status" > "$artifacts/exit-code.txt"
    exit "$status"
}
trap collect_artifacts EXIT

if [[ "$mode" == verify ]]; then
    python3 - <<'PY' | tee "$artifacts/isolation.txt"
import os
from pathlib import Path
assert os.getuid() == 10001, 'fixture must run as UID 10001'
status = dict(line.split(':', 1) for line in Path('/proc/self/status').read_text().splitlines())
assert int(status['CapEff'].strip(), 16) == 0, 'effective capabilities must be empty'
assert status['NoNewPrivs'].strip() == '1', 'no-new-privileges must be enabled'
assert status['Seccomp'].strip() == '2', 'seccomp filtering must be enabled'
interfaces = {p.name for p in Path('/sys/class/net').iterdir()}
assert interfaces == {'lo'}, f'expected network=none, found {interfaces}'
mounts = [line.split() for line in Path('/proc/mounts').read_text().splitlines()]
root = next(fields for fields in mounts if fields[1] == '/')
assert 'ro' in root[3].split(','), 'root filesystem must be read-only'
assert not Path('/var/run/docker.sock').exists(), 'Docker socket must not be mounted'
print('uid=10001 capabilities=none no_new_privileges=1 seccomp=filter network=loopback-only root=read-only')
PY
fi

# Keep one X server alive for both the renderer probe and the game; no host display/GPU.
xvfb-run --auto-servernum --server-args='-screen 0 1280x720x24 -nolisten tcp' \
    bash -euo pipefail -c '
        glxinfo -B | tee /workspace/container-artifacts/glxinfo.txt
        grep -qi llvmpipe /workspace/container-artifacts/glxinfo.txt
        sfm-propagate-changes puppet run title_screen_capture,game_test_orbit_capture \
            --game-test sfm:move_1_stack_direct --branch ci-container \
            --width 1280 --height 720 --variant preferred --require-portable-artifacts \
            2>&1 | tee /workspace/container-artifacts/console.log
    '

python3 /opt/sfm-container/verify.py \
    /workspace/platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview \
    "$artifacts/console.log" | tee "$artifacts/verification.json"
