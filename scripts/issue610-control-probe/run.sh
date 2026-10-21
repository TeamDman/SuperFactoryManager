#!/usr/bin/env bash
set -euo pipefail

# Compile only the pure fixture scheduler. No game, external JAR or JUnit agent.
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)"
repo_root="$(git -C "$script_dir" rev-parse --show-toplevel)"
: "${JAVA_HOME:?Set JAVA_HOME to a full JDK (21 for the cloud fixture)}"
probe_tmp_root="$(cd -- "${TMPDIR:-/tmp}" && pwd -P)"
probe_classes="$(mktemp -d "$probe_tmp_root/sfm-issue610-controls.XXXXXX")"
cleanup() {
  case "$probe_classes" in
    "$probe_tmp_root"/sfm-issue610-controls.*) rm -rf -- "$probe_classes" ;;
    *) printf 'Refusing cleanup outside the probe temporary directory\n' >&2 ;;
  esac
}
trap cleanup EXIT
"$JAVA_HOME/bin/javac" --release 17 -proc:none -d "$probe_classes" \
  "$repo_root/platform/minecraft/core-liquid-template/src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/justdirethings/Issue610VisualControls.java" \
  "$script_dir/ControlProbe.java"
"$JAVA_HOME/bin/java" -cp "$probe_classes" ControlProbe
