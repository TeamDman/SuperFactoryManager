#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
scratch=$(mktemp -d)
trap 'rm -rf -- "$scratch"' EXIT
mkdir -p "$scratch/bin" "$scratch/stale" "$scratch/hidden" "$scratch/empty"

# Stop immediately at Docker creation: accepted destinations must reach this
# stub, while rejected destinations must never invoke any Docker operation.
cat > "$scratch/bin/docker" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_CALL_LOG"
exit 73
SH
chmod +x "$scratch/bin/docker"

printf '{"passed":true}\n' > "$scratch/stale/verification.json"
printf 'previous screenshot bytes\n' > "$scratch/stale/screenshot.png"
printf 'preserve hidden results\n' > "$scratch/hidden/.receipt"

run_smoke() {
    local destination=$1
    local expected=$2
    local status=0
    PATH="$scratch/bin:$PATH" DOCKER_CALL_LOG="$scratch/docker-calls" \
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
    [[ ! -e "$scratch/docker-calls" ]]
done
cmp "$scratch/stale/verification.json" <(printf '{"passed":true}\n')
cmp "$scratch/stale/screenshot.png" <(printf 'previous screenshot bytes\n')
cmp "$scratch/hidden/.receipt" <(printf 'preserve hidden results\n')
[[ $(find "$scratch/stale" -mindepth 1 -maxdepth 1 | wc -l) -eq 2 ]]
[[ $(find "$scratch/hidden" -mindepth 1 -maxdepth 1 | wc -l) -eq 1 ]]

for destination in "$scratch/empty" "$scratch/new"; do
    run_smoke "$destination" 73
    [[ -d "$destination" ]]
done
[[ $(wc -l < "$scratch/docker-calls") -eq 2 ]]
[[ $(grep -c '^create ' "$scratch/docker-calls") -eq 2 ]]
echo 'PASS: stale and hidden output rejected unchanged; empty and new output reach Docker.'
