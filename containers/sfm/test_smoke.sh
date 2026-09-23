#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
mkdir -p "$scratch/bin" "$scratch/stale" "$scratch/hidden" "$scratch/empty"

# Stop at creation by default. The lifecycle mode also checks engine routing,
# isolation flags, evidence collection, and cleanup without any real engine.
cat > "$scratch/bin/docker" <<'SH'
#!/usr/bin/env bash
printf '%s %s\n' "${0##*/}" "$*" >> "$ENGINE_CALL_LOG"
if [[ "$STUB_MODE" == create-fails ]]; then exit 73; fi
case "$1" in
    create) printf 'fixture-container\n' ;;
    start) exit "${STUB_START_STATUS:-0}" ;;
    inspect)
        if [[ "${2:-}" == --format ]]; then printf '0\n'; else printf '{}\n'; fi
        ;;
    logs) printf 'fixture log\n' ;;
    cp|rm) ;;
    *) exit 64 ;;
esac
SH
cp "$scratch/bin/docker" "$scratch/bin/podman"
chmod +x "$scratch/bin/docker" "$scratch/bin/podman"

printf '{"passed":true}\n' > "$scratch/stale/verification.json"
printf 'previous screenshot bytes\n' > "$scratch/stale/screenshot.png"
printf 'preserve hidden results\n' > "$scratch/hidden/.receipt"

run_smoke() {
    local destination=$1
    local expected=$2
    local engine=${3:-}
    local mode=${4:-create-fails}
    local start_status=${5:-0}
    local status=0
    local selection=(env -u SFM_CONTAINER_ENGINE)
    if [[ -n "$engine" ]]; then selection+=("SFM_CONTAINER_ENGINE=$engine"); fi
    "${selection[@]}" PATH="$scratch/bin:$PATH" ENGINE_CALL_LOG="$scratch/engine-calls" \
        STUB_MODE="$mode" STUB_START_STATUS="$start_status" \
        bash "$script_dir/smoke.sh" sfm-test:fixture "$destination" \
        > "$scratch/output.log" 2>&1 || status=$?
    if [[ "$status" != "$expected" ]]; then
        cat "$scratch/output.log" >&2
        echo "Expected exit $expected, got $status for $destination" >&2
        exit 1
    fi
}

for destination in "$scratch/stale" "$scratch/hidden"; do
    run_smoke "$destination" 2
    grep -q 'Choose a fresh or empty directory' "$scratch/output.log"
    [[ ! -e "$scratch/engine-calls" ]]
done
run_smoke "$scratch/stale" 2 podman
[[ ! -e "$scratch/engine-calls" ]]
cmp "$scratch/stale/verification.json" <(printf '{"passed":true}\n')
cmp "$scratch/stale/screenshot.png" <(printf 'previous screenshot bytes\n')
cmp "$scratch/hidden/.receipt" <(printf 'preserve hidden results\n')
[[ $(find "$scratch/stale" -mindepth 1 -maxdepth 1 | wc -l) -eq 2 ]]
[[ $(find "$scratch/hidden" -mindepth 1 -maxdepth 1 | wc -l) -eq 1 ]]

for destination in "$scratch/empty" "$scratch/new"; do
    run_smoke "$destination" 73
    [[ -d "$destination" ]]
done
[[ $(wc -l < "$scratch/engine-calls") -eq 2 ]]
[[ $(grep -c '^docker create ' "$scratch/engine-calls") -eq 2 ]]

for engine in docker podman; do
    : > "$scratch/engine-calls"
    selection=$engine
    if [[ "$engine" == docker ]]; then selection=; fi
    destination="$scratch/$engine-lifecycle"
    run_smoke "$destination" 0 "$selection" lifecycle
    [[ -f "$destination/$engine.log" && -f "$destination/$engine-inspect.json" ]]
    [[ $(grep -c "^$engine " "$scratch/engine-calls") -eq 8 ]]
    [[ $(wc -l < "$scratch/engine-calls") -eq 8 ]]
    for arguments in '--network none' '--read-only --user 10001:10001' \
        '--cap-drop ALL' '--security-opt no-new-privileges:true' \
        '--pids-limit 512 --memory 8g --memory-swap 8g --cpus 4' \
        '--mount type=volume,destination=/workspace'; do
        grep "^$engine create " "$scratch/engine-calls" | grep -Fq -- "$arguments"
    done
    if [[ "$engine" == podman ]]; then
        grep -Fq -- '--read-only-tmpfs=false' "$scratch/engine-calls"
        grep -Fq -- '--tmpfs /dev/shm:rw,nosuid,nodev,noexec,size=256m,mode=1777,notmpcopyup' "$scratch/engine-calls"
        grep -Fq -- '--tmpfs /home/sfm:rw,nosuid,nodev,size=128m,mode=1777,notmpcopyup' "$scratch/engine-calls"
        grep -Fq -- '--tmpfs /tmp:rw,exec,nosuid,nodev,size=512m,mode=1777,notmpcopyup' "$scratch/engine-calls"
        ! grep -Fq -- 'uid=10001,gid=10001' "$scratch/engine-calls"
    else
        grep -Fq -- '--shm-size 256m' "$scratch/engine-calls"
        grep -Fq -- '--tmpfs /home/sfm:rw,nosuid,nodev,size=128m,uid=10001,gid=10001,mode=700' "$scratch/engine-calls"
        grep -Fq -- '--tmpfs /tmp:rw,exec,nosuid,nodev,size=512m,mode=1777' "$scratch/engine-calls"
        ! grep -Fq -- '--read-only-tmpfs' "$scratch/engine-calls"
        ! grep -Fq -- 'notmpcopyup' "$scratch/engine-calls"
    fi
    grep -Fxq "$engine start --attach fixture-container" "$scratch/engine-calls"
    copy_destination=$destination
    if [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
        copy_destination=$(cygpath -am "$destination")
    fi
    [[ $(grep -Fxc "$engine cp fixture-container:/workspace/container-artifacts/. $copy_destination/" \
        "$scratch/engine-calls") -eq 2 ]]
    grep -Fxq "$engine rm -f -v fixture-container" "$scratch/engine-calls"
done

: > "$scratch/engine-calls"
run_smoke "$scratch/podman-failure" 42 podman lifecycle 42
grep -Fxq 'podman rm -f -v fixture-container' "$scratch/engine-calls"
[[ -f "$scratch/podman-failure/podman.log" ]]
! grep -q '^docker ' "$scratch/engine-calls"

: > "$scratch/engine-calls"
run_smoke "$scratch/invalid-engine" 2 'podman --privileged'
grep -q 'SFM_CONTAINER_ENGINE must be docker or podman' "$scratch/output.log"
[[ ! -s "$scratch/engine-calls" && ! -e "$scratch/invalid-engine" ]]
echo 'PASS: fresh outputs, Docker default, Podman isolation/routing/cleanup, and invalid-engine rejection.'
