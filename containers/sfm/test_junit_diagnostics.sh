#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
if [[ $(uname -s) != Linux || ! -d /proc/self ]]; then
    echo 'These watchdog tests require Linux /proc.' >&2
    exit 2
fi

scratch=$(mktemp -d)
fixture_pids=()
watchdog_pid=
watchdog_children=()

process_running() {
    local pid=$1 key value
    [[ -r /proc/$pid/status ]] || return 1
    while read -r key value; do
        if [[ $key == State: ]]; then
            [[ $value != Z* && $value != X* ]]
            return
        fi
    done < "/proc/$pid/status"
    return 1
}

cleanup() {
    local pid attempt
    trap - EXIT
    # Every PID here was started by this test or observed beneath its watchdog.
    for pid in "${watchdog_pid:-}" "${watchdog_children[@]}" "${fixture_pids[@]}"; do
        [[ -n $pid ]] || continue
        if process_running "$pid"; then kill -TERM "$pid" 2>/dev/null || true; fi
    done
    for pid in "${watchdog_pid:-}" "${watchdog_children[@]}" "${fixture_pids[@]}"; do
        [[ -n $pid ]] || continue
        for ((attempt = 0; attempt < 50; attempt++)); do
            if ! process_running "$pid"; then break; fi
            sleep 0.02
        done
        if process_running "$pid"; then kill -KILL "$pid" 2>/dev/null || true; fi
    done
    for pid in "${watchdog_pid:-}" "${fixture_pids[@]}"; do
        [[ -n $pid ]] || continue
        wait "$pid" 2>/dev/null || true
    done
    rm -rf -- "$scratch"
}
trap cleanup EXIT

fail() {
    echo "FAIL: $*" >&2
    if [[ -f $scratch/watchdog.log ]]; then cat "$scratch/watchdog.log" >&2; fi
    exit 1
}

wait_until() {
    local attempt
    for ((attempt = 0; attempt < 250; attempt++)); do
        if "$@"; then return 0; fi
        sleep 0.02
    done
    fail "timed out waiting for $*"
}

process_stopped() { ! process_running "$1"; }
file_nonempty() { [[ -s $1 ]]; }

mkdir -p "$scratch/java/bin" "$scratch/workspace with spaces"
mkfifo "$scratch/fixture-input"
workspace="$scratch/workspace with spaces"
argument="@$workspace/platform/minecraft/build/sfm-toolchain/run/runTest/junit.java.args"

start_fixture() {
    # A Bash builtin blocks on our FIFO, so fixtures create no child processes.
    bash -c 'trap "exit 0" TERM INT; while :; do read -r -t 1 -u 3 line || :; done' \
        junit-fixture "$1" 3<> "$scratch/fixture-input" &
    fixture_pids+=("$!")
}
start_fixture "$argument"
selected_pid=${fixture_pids[0]}
start_fixture "prefix$argument"
prefix_pid=${fixture_pids[1]}
start_fixture "$argument.suffix"
suffix_pid=${fixture_pids[2]}
start_fixture "$argument"
wrong_class_pid=${fixture_pids[3]}

cat > "$scratch/java/bin/jcmd" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "$STUB_CALL_LOG"
if [[ $1 == -l ]]; then
    printf '%s dev.teamdman.sfm.toolchain.SfmJUnitRunner\n' \
        "$SELECTED_PID" "$PREFIX_PID" "$SUFFIX_PID"
    printf '%s unrelated.Main\n' "$WRONG_CLASS_PID"
    printf 'jcmd list stderr evidence\n' >&2
    if [[ $STUB_MODE == list-fails ]]; then exit 73; fi
elif [[ $# == 3 && $1 == "$SELECTED_PID" && $2 == Thread.print && $3 == -l ]]; then
    if [[ $STUB_MODE == thread-blocks ]]; then
        printf '%s\n' "$$" > "$STUB_BLOCKED_PID_FILE"
        exec sleep 60
    fi
    printf 'thread dump evidence for %s\n' "$1"
    printf 'jcmd thread stderr evidence\n' >&2
    if [[ $STUB_MODE == thread-fails ]]; then exit 73; fi
else
    echo "Unexpected jcmd target or command: $*" >&2
    exit 91
fi
SH
chmod +x "$scratch/java/bin/jcmd"

start_watchdog() {
    local mode=$1 interval=$2 snapshots=$3
    output_dir="$scratch/$mode-output"
    : > "$scratch/jcmd-calls"
    rm -f -- "$scratch/blocked-pid"
    watchdog_children=()
    env JAVA_HOME="$scratch/java" GITHUB_WORKSPACE="$workspace/" \
        SFM_JUNIT_DIAGNOSTIC_INTERVAL_SECONDS="$interval" \
        SFM_JUNIT_DIAGNOSTIC_SNAPSHOTS="$snapshots" \
        SELECTED_PID="$selected_pid" PREFIX_PID="$prefix_pid" SUFFIX_PID="$suffix_pid" \
        WRONG_CLASS_PID="$wrong_class_pid" STUB_MODE="$mode" \
        STUB_CALL_LOG="$scratch/jcmd-calls" STUB_BLOCKED_PID_FILE="$scratch/blocked-pid" \
        bash "$script_dir/junit-diagnostics.sh" "$output_dir" \
        > "$scratch/watchdog.log" 2>&1 &
    watchdog_pid=$!
}

finish_watchdog() {
    local status=0
    wait_until process_stopped "$watchdog_pid"
    wait "$watchdog_pid" || status=$?
    watchdog_pid=
    [[ $status == 0 ]] || fail "watchdog exited $status"
}

assert_fixtures_alive() {
    local pid
    for pid in "${fixture_pids[@]}"; do
        process_running "$pid" || fail "watchdog stopped fixture $pid"
    done
}

start_watchdog success 0.01 2
finish_watchdog
[[ $(grep -Fxc -- '-l' "$scratch/jcmd-calls") == 2 ]] || fail 'snapshot list count'
[[ $(grep -Fxc -- "$selected_pid Thread.print -l" "$scratch/jcmd-calls") == 2 ]] || fail 'selected dump count'
[[ $(wc -l < "$scratch/jcmd-calls") == 4 ]] || fail 'attached to an unrelated process'
for snapshot in 01 02; do
    [[ -s $output_dir/snapshot-$snapshot-metadata.log ]] || fail 'missing metadata'
    grep -Fq 'jcmd list stderr evidence' "$output_dir/snapshot-$snapshot-jcmd-list.log"
    grep -Fq "thread dump evidence for $selected_pid" "$output_dir/snapshot-$snapshot-pid-$selected_pid-threads.log"
    grep -Fq 'jcmd thread stderr evidence' "$output_dir/snapshot-$snapshot-pid-$selected_pid-threads.log"
done
[[ $(find "$output_dir" -type f | wc -l) == 6 ]] || fail 'snapshot limit or unexpected target evidence'
assert_fixtures_alive

for mode in list-fails thread-fails; do
    start_watchdog "$mode" 0.01 2
    finish_watchdog
    [[ $(grep -Fxc -- '-l' "$scratch/jcmd-calls") == 2 ]] || fail 'failure prevented next snapshot'
    for snapshot in 01 02; do
        if [[ $mode == list-fails ]]; then
            grep -Fq 'jcmd_list_exit=73' "$output_dir/snapshot-$snapshot-metadata.log"
            grep -Fq 'jcmd list stderr evidence' "$output_dir/snapshot-$snapshot-jcmd-list.log"
        else
            grep -Fq "thread_dump_pid=$selected_pid exit=73" "$output_dir/snapshot-$snapshot-metadata.log"
            grep -Fq 'jcmd thread stderr evidence' "$output_dir/snapshot-$snapshot-pid-$selected_pid-threads.log"
        fi
    done
done
assert_fixtures_alive

children_of() {
    local pid=$1 children=
    if [[ -r /proc/$pid/task/$pid/children ]]; then
        read -r children < "/proc/$pid/task/$pid/children" || true
        printf '%s\n' "$children"
    fi
}

find_sleep_child() {
    local pid name
    for pid in $(children_of "$watchdog_pid"); do
        if [[ -r /proc/$pid/comm ]]; then
            read -r name < "/proc/$pid/comm" || true
            if [[ $name == sleep ]]; then
                watchdog_children=("$pid")
                return 0
            fi
        fi
    done
    return 1
}

terminate_watchdog() {
    local pid status=0
    kill -TERM "$watchdog_pid"
    wait_until process_stopped "$watchdog_pid"
    wait "$watchdog_pid" || status=$?
    watchdog_pid=
    [[ $status == 0 || $status == 143 ]] || fail "TERM exit status $status"
    for pid in "${watchdog_children[@]}"; do
        [[ ! -e /proc/$pid ]] || fail "watchdog left child $pid running or unreaped"
    done
    watchdog_children=()
    assert_fixtures_alive
}

start_watchdog idle 60 2
wait_until find_sleep_child
terminate_watchdog

start_watchdog thread-blocks 0.01 2
wait_until file_nonempty "$scratch/blocked-pid"
read -r blocked_pid < "$scratch/blocked-pid"
read -r -a watchdog_children <<< "$(children_of "$watchdog_pid")"
[[ ${#watchdog_children[@]} == 1 ]] || fail 'expected one active timeout child'
read -r timeout_name < "/proc/${watchdog_children[0]}/comm"
[[ $timeout_name == timeout ]] || fail 'jcmd is not bounded by timeout'
[[ " $(children_of "${watchdog_children[0]}") " == *" $blocked_pid "* ]] || fail 'jcmd is not a timeout child'
watchdog_children+=("$blocked_pid")
terminate_watchdog

# Execute the checked-in workflow block with only its canonical CLI stubbed.
workflow_dir="$scratch/workflow"
mkdir -p "$workflow_dir/containers/sfm" "$workflow_dir/build/ci"
awk '
    { sub(/\r$/, "") }
    /^      - name: Run Java unit tests$/ { step = 1; next }
    step && /^        run: \|$/ { body = 1; next }
    body && /^          / { sub(/^          /, ""); print; next }
    body && /^[[:space:]]*$/ { print; next }
    body { exit }
' "$script_dir/../../.github/workflows/ci.yml" > "$workflow_dir/step.sh"
[[ -s $workflow_dir/step.sh ]] || fail 'could not locate the workflow unit-test block'
cat > "$workflow_dir/containers/sfm/junit-diagnostics.sh" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$$" > "$WORKFLOW_WATCHDOG_PID_FILE"
exec bash "$DIAGNOSTICS_SCRIPT" "$@"
SH
cat > "$scratch/cli" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
# Let the real watchdog enter its first sleep before completing the CLI stub.
for ((attempt = 0; attempt < 250; attempt++)); do
    if [[ -s $WORKFLOW_WATCHDOG_PID_FILE ]]; then
        read -r collector < "$WORKFLOW_WATCHDOG_PID_FILE"
        children=
        if [[ -r /proc/$collector/task/$collector/children ]]; then
            read -r children < "/proc/$collector/task/$collector/children" || true
        fi
        for child in $children; do
            if [[ -r /proc/$child/comm ]]; then
                read -r name < "/proc/$child/comm"
                if [[ $name == sleep ]]; then
                    printf '%s\n' "$child" > "$WORKFLOW_CHILD_PID_FILE"
                    printf 'canonical CLI fixture exit=%s\n' "$STUB_CLI_EXIT"
                    exit "$STUB_CLI_EXIT"
                fi
            fi
        done
    fi
    sleep 0.02
done
echo 'CLI stub never observed the diagnostic sleep' >&2
exit 92
SH
chmod +x "$scratch/cli"

for expected_status in 0 37; do
    rm -f -- "$scratch/workflow-watchdog-pid" "$scratch/workflow-child-pid"
    (
        cd "$workflow_dir"
        exec env JAVA_HOME="$scratch/java" GITHUB_WORKSPACE="$workspace" \
            SFM_CI_CLI="$scratch/cli" STUB_CLI_EXIT="$expected_status" \
            SFM_JUNIT_DIAGNOSTIC_INTERVAL_SECONDS=60 \
            DIAGNOSTICS_SCRIPT="$script_dir/junit-diagnostics.sh" \
            WORKFLOW_WATCHDOG_PID_FILE="$scratch/workflow-watchdog-pid" \
            WORKFLOW_CHILD_PID_FILE="$scratch/workflow-child-pid" \
            bash --noprofile --norc -eo pipefail "$workflow_dir/step.sh"
    ) > "$scratch/watchdog.log" 2>&1 &
    watchdog_pid=$!
    wait_until file_nonempty "$scratch/workflow-child-pid"
    read -r collector < "$scratch/workflow-watchdog-pid"
    read -r child < "$scratch/workflow-child-pid"
    watchdog_children=("$collector" "$child")
    wait_until process_stopped "$watchdog_pid"
    status=0
    wait "$watchdog_pid" || status=$?
    watchdog_pid=
    [[ $status == "$expected_status" ]] || fail "workflow changed CLI exit $expected_status to $status"
    for pid in "${watchdog_children[@]}"; do
        [[ ! -e /proc/$pid ]] || fail "workflow left diagnostic process $pid running or unreaped"
    done
    watchdog_children=()
    grep -Fq "canonical CLI fixture exit=$expected_status" "$workflow_dir/build/ci/test.log"
done
assert_fixtures_alive

echo 'PASS: exact runner selection, capped evidence, best-effort failures, TERM cleanup, and CI exit preservation.'
