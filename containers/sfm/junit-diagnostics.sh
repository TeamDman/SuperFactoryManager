#!/usr/bin/env bash
# Capture a stalled JUnit JVM without changing the test command or its result.
set -uo pipefail

output_dir=${1:?usage: junit-diagnostics.sh OUTPUT_DIRECTORY}
interval=${SFM_JUNIT_DIAGNOSTIC_INTERVAL_SECONDS:-120}
snapshots=${SFM_JUNIT_DIAGNOSTIC_SNAPSHOTS:-6}
collector_pid=$BASHPID
active_child=

cleanup() {
    local status=$? key value
    trap - EXIT INT TERM
    if [[ -n "$active_child" ]]; then
        # A completed/reaped child PID must never select an unrelated process.
        if [[ -r "/proc/$active_child/status" ]]; then
            while read -r key value; do
                if [[ "$key" == 'PPid:' && "$value" == "$collector_pid" ]]; then
                    kill "$active_child" 2>/dev/null || true
                    break
                fi
            done < "/proc/$active_child/status"
        fi
        wait "$active_child" 2>/dev/null || true
    fi
    exit "$status"
}
trap cleanup EXIT
trap 'exit 0' INT TERM

if [[ ! "$interval" =~ ^[0-9]+([.][0-9]+)?$ || ! "$snapshots" =~ ^[1-6]$ ]]; then
    printf 'Invalid diagnostic interval or snapshot count\n' >&2
    exit 2
fi
mkdir -p "$output_dir" || exit 1
jcmd=${JAVA_HOME:?JAVA_HOME is required}/bin/jcmd
workspace=${GITHUB_WORKSPACE:?GITHUB_WORKSPACE is required}
junit_arg="@${workspace%/}/platform/minecraft/build/sfm-toolchain/run/runTest/junit.java.args"

run_bounded() {
    local limit=$1 status
    shift
    timeout --kill-after=2s "$limit" "$@" &
    active_child=$!
    wait "$active_child"
    status=$?
    active_child=
    return "$status"
}

for ((index = 1; index <= snapshots; index++)); do
    sleep "$interval" &
    active_child=$!
    wait "$active_child" || true
    active_child=

    printf -v prefix '%s/snapshot-%02d' "$output_dir" "$index"
    {
        printf 'snapshot=%s\nutc=%s\nexpected_argument=%s\n' \
            "$index" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$junit_arg"
        ps -eo pid,ppid,etimes,stat,comm
    } > "$prefix-metadata.log" 2>&1

    run_bounded 10s "$jcmd" -l > "$prefix-jcmd-list.log" 2>&1
    printf 'jcmd_list_exit=%s\n' "$?" >> "$prefix-metadata.log"
    while read -r pid main_class _remainder; do
        [[ "$pid" =~ ^[0-9]+$ && "$main_class" == 'dev.teamdman.sfm.toolchain.SfmJUnitRunner' ]] || continue
        # Match a complete NUL-delimited JVM argument, not a path substring.
        [[ -r "/proc/$pid/cmdline" ]] || continue
        grep -zFxq -- "$junit_arg" "/proc/$pid/cmdline" || continue
        run_bounded 20s "$jcmd" "$pid" Thread.print -l \
            > "$prefix-pid-$pid-threads.log" 2>&1
        printf 'thread_dump_pid=%s exit=%s\n' "$pid" "$?" >> "$prefix-metadata.log"
    done < "$prefix-jcmd-list.log"
done
