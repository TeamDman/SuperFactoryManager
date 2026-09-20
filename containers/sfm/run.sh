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
    printf '%s\n' "$status" > "$artifacts/exit-code.txt"
    if [[ "$status" != 0 ]]; then
        # A failed Docker RUN has no container to copy from. Keep bounded,
        # relevant evidence in the BuildKit log as well as in runtime artifacts.
        python3 - "$artifacts" <<'PY' || true
from collections import deque
import json
from pathlib import Path
import sys

root = Path(sys.argv[1])
print('SFM container fixture failed; captured evidence follows:', flush=True)
for puppet in ('title_screen_capture', 'game_test_orbit_capture'):
    run = root / puppet
    if not run.is_dir():
        continue
    for name in ('exit-code.txt', 'console.log', 'game-console.log', 'game-logs/latest.log'):
        path = run / name
        print(f'--- {puppet}/{name} (last 80 lines) ---', flush=True)
        if path.is_file():
            with path.open(encoding='utf-8', errors='replace') as stream:
                print(''.join(deque(stream, maxlen=80)), flush=True)
        else:
            print('(not produced)', flush=True)
    manifest = run / 'previews/preview-manifest.json'
    print(f'--- {puppet}/previews/preview-manifest.json capture inventory ---', flush=True)
    if manifest.is_file():
        try:
            for capture in json.loads(manifest.read_text(encoding='utf-8')).get('captures', []):
                # Print metadata only; do not follow arbitrary manifest paths.
                print(json.dumps({key: capture.get(key) for key in
                                  ('puppet', 'capture', 'path', 'width', 'height')}), flush=True)
        except (ValueError, TypeError, AttributeError) as error:
            print(f'Could not summarize manifest: {error}', flush=True)
    else:
        print('(not produced)', flush=True)
PY
    fi
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

# Each puppet gets a fresh client. The title fixture requires the startup loading
# overlay, which is not guaranteed when returning to the title after a world.
# Keep one X server alive throughout; no host display/GPU.
xvfb-run --auto-servernum --server-args='-screen 0 1280x720x24 -nolisten tcp' \
    bash -euo pipefail <<'BASH'
glxinfo -B | tee /workspace/container-artifacts/glxinfo.txt
grep -qi llvmpipe /workspace/container-artifacts/glxinfo.txt

previews=/workspace/platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview
game_dir=/workspace/platform/minecraft/runGameTestPreview
game_console=/workspace/platform/minecraft/build/sfm-toolchain/run/runGameTestPreview/console.log
current_artifacts=
snapshot_current() {
    if [[ -z "$current_artifacts" ]]; then return; fi
    # With non-TTY stdout the canonical CLI records raw JVM output here,
    # separately from its own console. Preserve it before the next launch.
    if [[ -f "$game_console" ]]; then
        cp "$game_console" "$current_artifacts/game-console.log"
    fi
    if [[ -d "$previews" ]]; then
        mkdir -p "$current_artifacts/previews"
        cp -a "$previews/." "$current_artifacts/previews/"
    fi
    # Never copy the home directory or instance descriptors containing RPC tokens.
    if [[ -d "$game_dir/logs" ]]; then
        mkdir -p "$current_artifacts/game-logs"
        cp -a "$game_dir/logs/." "$current_artifacts/game-logs/"
    fi
}
trap snapshot_current EXIT

for puppet in title_screen_capture game_test_orbit_capture; do
    current_artifacts=/workspace/container-artifacts/$puppet
    mkdir -p "$current_artifacts"
    # Both the manifest and screenshots must belong to this invocation.
    rm -rf "$previews" "$game_dir"
    rm -f "$game_console"
    options=(--branch ci-container --java-home /opt/java --width 1280 --height 720
             --variant preferred --require-portable-artifacts)
    if [[ "$puppet" == game_test_orbit_capture ]]; then
        options+=(--game-test sfm:move_1_stack_direct)
    fi
    status=0
    # Scope limits to Java launchers during puppet preparation/runtime. The
    # source-build recipe treats any jdeps output as unresolved dependencies;
    # JAVA_TOOL_OPTIONS would make jdeps emit an unrelated startup banner.
    JDK_JAVA_OPTIONS='-Xmx3g -XX:ActiveProcessorCount=4' \
        sfm-propagate-changes puppet run "$puppet" "${options[@]}" \
        2>&1 | tee "$current_artifacts/console.log" || status=$?
    printf '%s\n' "$status" > "$current_artifacts/exit-code.txt"
    snapshot_current
    current_artifacts=
    if [[ "$status" != 0 ]]; then exit "$status"; fi
done
BASH

python3 /opt/sfm-container/verify.py "$artifacts" | tee "$artifacts/verification.json"
