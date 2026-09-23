#!/usr/bin/env bash
set -euo pipefail

image=${1:-sfm-ci:local}
artifacts=${2:-build/container-smoke}
engine=${SFM_CONTAINER_ENGINE:-docker}
if [[ $# -gt 2 ]]; then
    echo 'Usage: smoke.sh [image] [artifact-directory]' >&2
    exit 2
fi
case "$engine" in
    docker|podman) ;;
    *)
        echo 'SFM_CONTAINER_ENGINE must be docker or podman.' >&2
        exit 2
        ;;
esac
mkdir -p -- "$artifacts"
# Reusing output would merge a failed run with an older successful receipt.
shopt -s nullglob dotglob
existing_artifacts=("$artifacts"/*)
shopt -u nullglob dotglob
if (( ${#existing_artifacts[@]} != 0 )); then
    echo "Artifact directory is not empty: $artifacts. Choose a fresh or empty directory." >&2
    exit 2
fi
artifact_copy_destination=$artifacts
if [[ "$OSTYPE" == msys* || "$OSTYPE" == cygwin* ]]; then
    # Keep Linux mount/container paths intact for native Windows engine CLIs,
    # while making the one host-side copy destination an explicit native path.
    export MSYS_NO_PATHCONV=1
    artifact_copy_destination=$(cygpath -am "$artifacts")
fi
container=

cleanup() {
    local status=$?
    trap - EXIT
    if [[ -n "$container" ]]; then
        "$engine" logs "$container" > "$artifacts/$engine.log" 2>&1 || true
        "$engine" inspect "$container" > "$artifacts/$engine-inspect.json" || true
        "$engine" cp "$container:/workspace/container-artifacts/." "$artifact_copy_destination/" || true
        "$engine" rm -f -v "$container" >/dev/null || true
    fi
    exit "$status"
}
trap cleanup EXIT

# Anonymous volume copy-up preserves the prepared caches without mounting any host files.
shared_memory=(--shm-size 256m)
temporary_tmpfs=/tmp:rw,exec,nosuid,nodev,size=512m,mode=1777
home_tmpfs=/home/sfm:rw,nosuid,nodev,size=128m,uid=10001,gid=10001,mode=700
if [[ "$engine" == podman ]]; then
    # Podman's read-only mode otherwise adds writable /run and /var/tmp mounts.
    # Restore only the same bounded shared-memory mount used by Docker.
    shared_memory=(--read-only-tmpfs=false
        --tmpfs /dev/shm:rw,nosuid,nodev,noexec,size=256m,mode=1777,notmpcopyup)
    # Podman's remote tmpfs parser rejects uid/gid mount options. This private
    # container has one application UID; sticky permissions keep HOME writable.
    home_tmpfs=/home/sfm:rw,nosuid,nodev,size=128m,mode=1777,notmpcopyup
    # Build-time /tmp contains source-build output. Podman's default copy-up
    # would fill the bounded tmpfs before the worker even starts.
    temporary_tmpfs=/tmp:rw,exec,nosuid,nodev,size=512m,mode=1777,notmpcopyup
fi
container=$("$engine" create --network none --read-only --user 10001:10001 \
    --cap-drop ALL --security-opt no-new-privileges:true \
    --pids-limit 512 --memory 8g --memory-swap 8g --cpus 4 \
    "${shared_memory[@]}" --init \
    --tmpfs "$temporary_tmpfs" \
    --tmpfs "$home_tmpfs" \
    --mount type=volume,destination=/workspace \
    "$image" verify)

# Enforce wall time outside the game JVM. Cleanup kills and removes this container and its volume.
timeout --signal=TERM --kill-after=30s 35m "$engine" start --attach "$container"
status=$("$engine" inspect --format '{{.State.ExitCode}}' "$container")
if [[ "$status" != 0 ]]; then
    echo "Container fixture failed with exit code $status" >&2
    exit 1
fi
"$engine" cp "$container:/workspace/container-artifacts/." "$artifact_copy_destination/"
echo "Offline container fixture passed. Artifacts: $artifacts"
