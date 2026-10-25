# Plain release source isolation

Plain `sfm-4.34.0/*` is a static historical reference. Run the issue 610 tests,
puppets, Explorer, title hooks, and recording workflow through explicitly named
extension projections such as `sfm-4.34.0-issue610-visual/*` or
`sfm-4.34.0-issue610-portable/*`. Do not add runnable-demo conveniences to the
plain historical projection.

## Repair and integration boundary

Branch: `feature/plain-release-isolation-20261025`, based on
`c969392e9e4b1eb77e70755778fad5dd43f01f06`.
Implementation commit: `a684272e19d7378cfdea4b80b333fe92d7d061fe`.
The follow-up branch `feature/historical-434-lock-selector-20261025` restores
the historical 26.1.2 lock selection without changing runnable inputs.
Follow-up implementation: `2d7521e10f00870dab7883695936b564b95fae6d`.

- Move two blank lines inside their existing feature guards. This restores
  feature-off bytes in `SFMScreenRenderUtils` and
  `SFMGlobalBlockCapabilityProviders` while preserving feature-on bytes.
- Require `game_puppet_runtime` in the source membership rules for
  `ManagedSessionPuppetAction` and `ManagedSuperflatSessionGamePuppet`. Inactive
  contexts now omit the files instead of emitting package-only compilation units.
- Introduce `projection_test_directory_search` for the test helper's search to
  the filesystem root. All 34 existing Dev/extension contexts explicitly select
  it. The 20 plain release feature lists remain unchanged and retain the bounded
  five-iteration historical search.
- Regenerate only these five affected paths across the 20 plain projections:
  11 test-file restorations, 22 package-only files removed. No dependency lock,
  transfer implementation, title/blur hook, launcher, or puppet implementation
  body changes.

**Desktop overlap:** `SFMScreenRenderUtils.java` has one narrow overlapping
template edit: move the blank line immediately after the inspection overload's
`endif` to immediately before that `endif`. Preserve any concurrent desktop
rendering changes. No desktop main or game operations were performed here.

## Baselines are per release and target

The 4.34.0 witness is the complete canonical projection tree at
`25fec27456508a0bc2e70569e15a9bf2fc1d2d17`, before demo integration. Each target's
own output tree is compared, including main, GameTest, test, datagen, ANTLR, and
resource inputs. The ten pinned release-tag commits and tree IDs from
`projection-oracles.json` were also verified locally; their directory-search
methods match the restored methods and neither managed-puppet source exists.
Existing canonical formatting differences from original tags are not relabeled
as byte identity with those tags.

4.35.0 first appears at `122e4b31`, already containing the two package-only files
and extended test search. Consequently neither `0918604` nor the initial 4.35.0
tree is a clean witness for those three paths. Its expected output is the complete
matching-target tree at `c969392e`, with exactly these exceptions:

1. Omit the two managed-puppet files.
2. Restore `SFMLTests.java` from the matching 4.34.0 target at `25fec274`.

Every other 4.35.0 output is preserved, including the FE fix, recipe changes,
version metadata, and capability-invalidation version selection. Source equality
does not establish compiled-binary or behavioral equivalence.

## Verification

- All 20 source/resource sets, **19,916 files**, match those explicit witnesses
  by full path set and SHA-256; stored and freshly rendered bytes both match.
- Full project comparison covers **22,996 files**. The historical-lock follow-up
  makes all 20 projections match completely. The earlier failed receipt is retained.
- All **204 render pairs across 34 Dev/extension contexts** pass: 158 are exact;
  the remaining 46 remove only the inactive guard's blank line. Active puppet
  bodies, inspection overload, fixture registration, extended test search, and
  every extension's `OutputStatement` retain their bytes.
- Rust `demo_instrumentation_and_project_search_require_explicit_features` and
  `current_catalog_selections_stay_distinct_from_historical_feature_off_controls`
  each pass. The new test checks actual membership and loop selection across all
  54 catalog entries. Python syntax and `git diff --check` pass.
- Initial attempt to gate on `environment` was rejected by the canonical source
  validator before writes; explicit feature selection replaced it. No validator
  or policy was weakened.
- Independent read-only history/source review found no remaining source issue.

No Java compilation, JAR rebuild, GameTest execution, or client validation was
performed for this repair. JBR toolchain qualification remains a separate task.
Full `check-all.ps1` is unrun: PowerShell and the requested Rust formatter are not
installed here. Existing Rust compilation warnings remain.

## Historical lock selection restored

The follow-up isolates the original 26.1.2 lock as
`build/lockfiles/26.1.2/schema-2-historical-4.34.0.json`. It is an exact 50,153-byte
copy authenticated against release tag commit
`fe32b29453b13b4f3050ad441677c7eb79e80814`, with SHA-256
`475d2e71d7d640d6520a62e109e647308f5fff309bc8d57b02b45ce0c1aac0de`.
The stored historical projection lock is unchanged.

The three mutually exclusive 26.1.2 selectors are:

| Features | Authored lock |
| --- | --- |
| Neither `locked_dependency_projection` nor `release_version_4_35_0` | `schema-2-historical-4.34.0.json` |
| `release_version_4_35_0`, without `locked_dependency_projection` | Existing `schema-2.json` |
| `locked_dependency_projection`, with either release flag value | Existing `schema-4.json` |

Actual catalog selection changes only for plain `sfm-4.34.0/mc-26.1.2` across all
54 contexts. No existing lock, producer receipt, build configuration, dependency
pin, or native guard is modified. New tests cover all 54 entries, all four flag
combinations, exact historical bytes, and refusal of the historical lock by the
current native recipe. Test-only historical fixtures explicitly retain their
former reviewed path binding; that remap is absent from production code.

The follow-up frozen-recipe suite passes **26 tests**, with one pre-existing
manual tracing test ignored. The broader `cargo test --lib historical_` attempt
had **11 passes and one failure**: the unchanged release-review ledger capture
test requires Windows handle-relative source protection and refuses this Linux
host. That failed log is preserved; no capture guard was changed or bypassed.
The new selector and native-refusal tests both passed in that run.

The prior mismatch was a real Mekanism artifact-identity change in `122e4b31`,
not merely metadata formatting: the later producer receipt changes main/API
hashes, cache paths, tasks, and provenance. Current runnable configuration remains
bound to SHA-256 `2f4fbd00de200a7ba089d3dc5c19994de1ffb00b7b1a9ae56afc045a9de7f37f`.
The producer receipt's own SHA-256 and its consistency with current lock records
were checked. Old/new JAR bytes are absent from expected cloud cache paths and
were **not freshly compared**. No artifact admission or hash-check relaxation
occurs in this fix.

Plain 4.34.0 is a **static historical reference**. The existing native recipe
requires its reviewed authored path and stronger lock hash; it must continue to
reject this historical copy. Use explicitly named Dev/issue610 projections for
runnable demos. Restoring source/project identity is not native-build acceptance.

Follow-up evidence is under
`docs/tasks/evidence/historical-434-lock-selector-20261025/`. Original failed
receipts under `plain-release-isolation-20261025/` are preserved unchanged.

## Reproduce

Use a new output directory on each run. These commands require existing Git
witness objects and the existing CLI; they perform no installs or downloads.

```bash
python3 scripts/issue610-cross-version/verify-release-isolation.py \
  --cli /workspace/.sfm-env/target/debug/sfm-propagate-changes \
  --repo-root "$PWD" --output /tmp/sfm-plain-isolation

python3 scripts/issue610-cross-version/verify-extension-isolation.py \
  --cli /workspace/.sfm-env/target/debug/sfm-propagate-changes \
  --repo-root "$PWD" --output /tmp/sfm-extension-isolation

cd platform/cli/sfm-propagate-changes
cargo test --locked --offline --lib demo_instrumentation_and_project_search_require_explicit_features
cargo test --locked --offline --lib current_catalog_selections_stay_distinct_from_historical_feature_off_controls
cargo test --locked --offline --lib historical_2612_lock_is_isolated_from_all_runnable_catalog_contexts
cargo test --locked --offline --lib source_projection::frozen_recipe_project::tests
```

The plain comparison command now exits 0 with both `source_isolation_status` and
`full_project_status` equal to `passed`. The earlier exit-1 evidence remains a
historical record of the resolved mismatch. The extension command also exits 0.

CLI SHA-256:
`f7cd93ef9f561b2c9ceaca8bbd635a94bacc31d810b55bd75a93bdbb77996dc7`.
The production CLI is unchanged; only Rust test code changed. No installer is
required for this source audit. No game/server was started; task-owned check
processes finish before handoff.

Compact receipts, exact original mismatch diffs, and test results are under
`docs/tasks/evidence/plain-release-isolation-20261025/`. Full per-file CLI reports
and original logs remain in `/workspace/scratch/plain-release-isolation-fix-20261025/`
and `/workspace/scratch/plain-release-isolation-audit-20261024/`; those raw scratch
files are not preserved by Git.
