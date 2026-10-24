# Release inspection Explorer — cloud handoff

Status: implementation and headless evidence available; **not recording-ready**.
The native game build/launch has a toolchain prerequisite, so UI screenshots and live
interaction acceptance are still outstanding. No screenshot or simulated UI is offered
as a substitute for the requested in-game evidence.

## Source and scope

Feature branch: `feature/release-inspection-explorer-20261024`.
Base: published `migration/vox-sfm-cloud-cutover-20261023` at
`0918604ac558906c93a9c4ab449400e8f75792ce`, verified with remote refs before checkout.
Published main at that point was `e2ba833b2b235835d69475b641e24b22bfd9551a`.
The starting checkout was clean. Unpublished handshake commits were not assumed present.
No pushes, merges, package installs, dependency upgrades, security/network configuration
changes or desktop access occurred. Dependency declarations and lockfiles are unchanged.

Read `docs/AGENTS.md`, the goal execution/readiness guidelines, existing Explorer,
source-comparison, release-report/class-evidence and review-surface implementations
before designing. No applicable local `.agents/skills` files were present. Teamy Docs
was inspected through the authorized TeamDman/teamy-docs remote (`AGENTS.md`,
`src/SUMMARY.md`, `src/minecraft-client-automation.md`), without changing environment setup.

The implementation uses the contributed-path resolver, projected Explorer scenes,
existing owned preview placement, existing source comparison and diff surface engines,
and existing javap report producer. The hierarchy is comparison → strategy → pair →
representation. Prepared release roots use this general feature. Pointer selection
does not reveal; representation clicks open previews without taking Explorer focus.
External selection/reveal retains its previous scrolling behavior.

`scripts/release-inspection/README.md` records preparation, interactive commands,
classification limitations and the recording acceptance procedure. The instructions
strategy compares printed Code bodies separately from declarations/signatures; neither
it nor printed-debug-only or source-whitespace classification asserts semantic equality.
Missing inputs are visible error roots and never successful empty comparisons.

## Evidence and commands

Evidence directory in this environment: `/workspace/scratch/release-inspection`.
Use `source /workspace/.sfm-env/env.sh` before native commands. The modified CLI is built
directly at `/workspace/.sfm-env/target/debug/sfm-propagate-changes`; the installed wrapper
and its older executable were intentionally not replaced. User must run install.ps1:
**no for the explicit local executable commands below**. Installing this feature as the
default CLI was not authorized and was not performed.

Passed checks include:

- Locked offline Rust CLI build; focused inspection tests (three), existing source
  comparison tests (16), and existing run-client hotswap test (one).
- Real javac/JAR fixture through the existing producer and new native importer:
  instruction/debug/declaration/exact/unknown partitions, added/removed classes and
  resources, text/binary distinction, structured source mappings, independent missing
  input, output preservation and rejection of mismatched archive evidence.
- Production Java tree/resolver probe (69 assertions), compiled against generated development sources
  and the already-cached Forge installertools Gson. It verifies strategies, pagination,
  representation text, validated mappings, invalid input and mount disposal. This is
  headless evidence, not a full Minecraft compilation or rendering test.
  The actual release workspace also parsed successfully: two roots, 3,825 projection
  nodes and 108 validated mapped views, plus the explicit missing-JAR error root.
- Development projection sync/check and parser-backed Java syntax highlighting.
- Python syntax checks and `git diff --check`.

Reproduce backend checks:

```bash
source /workspace/.sfm-env/env.sh
cargo build --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml --locked --offline --bin sfm-propagate-changes
cargo test --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml --locked --offline --lib inspection_cli::
python3 scripts/release-inspection/verify.py \
  --cli /workspace/.sfm-env/target/debug/sfm-propagate-changes \
  --java-home /workspace/.sfm-env/jdk17 \
  --output /workspace/scratch/release-inspection/new-fixture-checkpoint
```

The release preparation uses catalog keys `sfm-4.34.0/mc-1.19.2` and
`sfm-4.35.0/mc-1.19.2`, rendering current catalog inputs with their actual provenance.
This is not an invented historical checkout. Its projection root has 1,151 pairs:
1,113 exact, 22 moved, nine changed, six added and one removed. The JAR root is explicitly
unavailable because the two release artifacts are absent locally; fixture JARs are never
substituted for release JARs. Preparation without JAR arguments intentionally returns 2
after preserving the independent projection result.

Prepare a complete release checkpoint when the exact JARs are available:

```bash
python3 scripts/release-inspection/prepare.py \
  --cli /workspace/.sfm-env/target/debug/sfm-propagate-changes \
  --output /workspace/scratch/release-inspection/new-release-checkpoint \
  --old-jar "$OLD_JAR" --new-jar "$NEW_JAR" --javap "$JDK/bin/javap"
```

Launch command shape was executed and accepted by the modified CLI, but failed before
Minecraft startup at the pinned Vox package producer:

```bash
/workspace/.sfm-env/target/debug/sfm-propagate-changes run client \
  --repo-root /workspace/SuperFactoryManager --projection sfm-dev/mc-1.19.2 \
  --java-home /workspace/.sfm-env/jdk17 \
  --inspection-workspace /workspace/scratch/release-inspection/release-final/release.sfm-inspection.json
```

The exact failure is `Java package SDK mismatch: expected Eclipse Adoptium|21.0.8+9-LTS,
found Eclipse Adoptium|17.0.20.1+1`. The native package producer's pinned SDK is absent.
Installing/upgrading it is outside this task's authorization; no bypass was attempted.
After the build environment provides that exact prerequisite, repeat the launch using
the appropriate SDK selections required by the pinned producer and Minecraft 1.19.2.
A usable cloud game display/capture environment is also needed for screenshots; no
desktop session was accessed and no Xvfb installation was performed.

## Failed and unrun gates

- Native development JAR build and interactive launch: failed at the SDK mismatch above.
- `pwsh -NoProfile -File platform/cli/sfm-propagate-changes/check-all.ps1`: cannot execute,
  PowerShell absent. Separate fmt/clippy attempts cannot run: components absent. None
  was installed. The full all-feature gate is unrun; focused checks do not replace it.
- Direct javac of the full panel model cannot resolve Minecraft classes in the available
  classpath. Only the production tree/resolver subset compiled successfully.
- Native JUnit interaction tests, real-game navigation/selection/rendering, parser colors
  on screen, long diff/strategy scrolling and screenshots: **unrun**. The new tests
  `pointerSelectionPreservesScrollWhileExternalSelectionReveals` and
  `inspectionRepresentationClickOpensOwnedPreviewWithoutReveal` are included for the
  native suite; they must not be described as executed in this environment.
- Full release JAR comparison: pending the actual 4.34.0/4.35.0 JAR pair. Local artifact
  discovery found neither. No artifact paths or releases were inferred.

These are missing prerequisites, not approval denials or uncertain executed mutations.
All owned build/preparation/launch processes exit; no game or inspection worker is left
running. Existing caches and installed tools were preserved. No broad process cleanup
or cache deletion occurred.

## Coordination

Editor task `01a16ca9-296e-705c-abf1-a42d313d3010`: the only editor-panel change is an
inspection source-map branch in `SFMTextEditorPanel.refreshReviewCommentDecorations`.
Existing review syntax-session/presentation helpers gained reusable constructors/methods.
No registered-editor picker, editor registry or keyboard/modal editor code was changed.

Nucleo task `01a16cac-37e4-75ad-919c-7e25f7574037`: the UI/API boundary is recorded in the
script README. Existing `~` is legacy fuzzy and remains unchanged. A distinct matcher
mode with original UTF-16 spans, explicit score direction and budget/cancellation behavior
is needed before wiring an honest Nucleo option. Nucleo button integration/parity tests
remain pending that matcher API; no parity is claimed here.
