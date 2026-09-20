#!/usr/bin/env bash
set -euo pipefail

image=${1:-sfm-ci:local}
artifacts=${2:-build/container-smoke}
if [[ $# -gt 2 ]]; then
    echo 'Usage: smoke.sh [image] [artifact-directory]' >&2
    exit 2
fi
mkdir -p -- "$artifacts"
# Reusing output would merge a failed run with an older successful receipt.
shopt -s nullglob dotglob
existing_artifacts=("$artifacts"/*)
shopt -u nullglob dotglob
if (( ${#existing_artifacts[@]} != 0 )); then
    echo "Artifact directory is not empty: $artifacts. Choose a fresh or empty directory." >&2
    exit 2
fi
container=

cleanup() {
    local status=$?
    trap - EXIT
    if [[ -n "$container" ]]; then
        docker logs "$container" > "$artifacts/docker.log" 2>&1 || true
        docker inspect "$container" > "$artifacts/docker-inspect.json" || true
        docker cp "$container:/workspace/container-artifacts/." "$artifacts/" || true
        docker rm -f -v "$container" >/dev/null || true
    fi
    exit "$status"
}
trap cleanup EXIT

# Anonymous volume copy-up preserves the prepared caches without mounting any host files.
container=$(docker create --network none --read-only --user 10001:10001 \
    --cap-drop ALL --security-opt no-new-privileges:true \
    --pids-limit 512 --memory 8g --memory-swap 8g --cpus 4 \
    --shm-size 256m --init \
    --tmpfs /tmp:rw,exec,nosuid,nodev,size=512m,mode=1777 \
    --tmpfs /home/sfm:rw,nosuid,nodev,size=128m,uid=10001,gid=10001,mode=700 \
    --mount type=volume,destination=/workspace \
    "$image" verify)

# Enforce wall time outside the game JVM. Cleanup kills and removes this container and its volume.
timeout --signal=TERM --kill-after=30s 35m docker start --attach "$container"
status=$(docker inspect --format '{{.State.ExitCode}}' "$container")
if [[ "$status" != 0 ]]; then
    echo "Container fixture failed with exit code $status" >&2
    exit 1
fi
docker cp "$container:/workspace/container-artifacts/." "$artifacts/"
echo "Offline container fixture passed. Artifacts: $artifacts"
