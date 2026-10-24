#!/usr/bin/env bash
set -euo pipefail
source /workspace/.sfm-env/env.sh
export JAVA_HOME=/workspace/sfm-handshake-20261023/sdk/jdk-21.0.8+9
export PATH="$JAVA_HOME/bin:$PATH"
export VOX_JAVA_BUILD_JDK='Eclipse Adoptium|21.0.8+9-LTS'
export CARGO_NET_RETRY=0 RAYON_NUM_THREADS=1
# Existing release-review test storage setting, scoped to this process.
export LOCALAPPDATA=/workspace/sfm-terminal-readiness-review-20261024/localappdata
mkdir -p "$LOCALAPPDATA"
repo=/workspace/SuperFactoryManager
launcher=/workspace/sfm-handshake-20261023/tools/sfm-propagate-changes-native
evidence=${1:-/workspace/sfm-terminal-readiness-review-20261024/manual-rerun}
mkdir -p "$evidence"
"$launcher" --output-format json source project sync --repo-root "$repo" \
  --projection sfm-dev/mc-1.19.2 > "$evidence/source-sync.json"
timeout --signal=INT --kill-after=20s 1200 "$launcher" --output-format json \
  --log-filter info --log-file "$evidence/aggregate.ndjson" \
  test run --repo-root "$repo" --projection sfm-dev/mc-1.19.2 \
  --java-home /workspace/.sfm-env/jdk17 --require-portable-artifacts \
  > "$evidence/aggregate.json" 2> "$evidence/aggregate.stderr"
