#!/usr/bin/env bash
set -euo pipefail
# Run in this owning cloud executor. Uses existing approved tools and locked inputs.
source /workspace/.sfm-env/env.sh
export JAVA_HOME=/workspace/sfm-handshake-20261023/sdk/jdk-21.0.8+9
export PATH="$JAVA_HOME/bin:$PATH"
export VOX_JAVA_BUILD_JDK='Eclipse Adoptium|21.0.8+9-LTS'
export CARGO_NET_RETRY=0 RAYON_NUM_THREADS=1
repo=/workspace/SuperFactoryManager
launcher=/workspace/sfm-handshake-20261023/tools/sfm-propagate-changes-native
evidence=${1:-/workspace/sfm-terminal-readiness-20261024/manual-rerun}
mkdir -p "$evidence"
"$launcher" --output-format json source project sync --repo-root "$repo" \
  --projection sfm-dev/mc-1.19.2 > "$evidence/source-sync.json"
for suite in SFMVoxTerminalServiceTests SFMTerminalPanelInteractionTests; do
  timeout --signal=INT --kill-after=20s 1200 "$launcher" --output-format json \
    --log-filter info --log-file "$evidence/$suite.ndjson" \
    test run --repo-root "$repo" --projection sfm-dev/mc-1.19.2 \
    --java-home /workspace/.sfm-env/jdk17 --require-portable-artifacts \
    --filter "$suite" > "$evidence/$suite.json" 2> "$evidence/$suite.stderr"
done
