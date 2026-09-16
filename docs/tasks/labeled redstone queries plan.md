# Labeled redstone queries

**Plan status:** Complete for the labeled-read slice
**Primary implementation root:** `feat/1.19.2/contraption-as-code` (1.19.2)
**Base commit:** `8794d96fd0f74306d70131c9b4a858db74984a89`
**Last updated:** 2026-09-15
**Intent audit:** Passed against the initial contraption proposal and the follow-up asking for progress on redstone first.

## How to update this plan

Use `[ ]` not started, `[~]` active, `[x]` complete, and `[!]` blocked with the
exact blocker. Update completion evidence under the affected task. Keep one
implementation focus. The existing [exploration](../contraption-as-code-exploration.md)
retains the broader contraption design.

## User guidance and traceability

| ID | Guidance | Coverage |
| --- | --- | --- |
| U1 | Explore functions that could translate to item-producing physical contraptions. Item creation and building/dismantling are proposed future capabilities. | Preserve the exploration; use a live read-only signal port as this first foundation. Function syntax, construction, and item creation stay future design work. |
| U2 | Support a condition like `IF abc HAS GT 0 redstone:: THEN`, beyond manager-local `IF REDSTONE > 0`. | R1-R3: exercise actual SFML queries and scheduled item routing. |
| U3 | Consider redstone blocks as sources/sinks, and the unfinished buffer and its log issue. | Reading redstone blocks is covered by R1; writable signals and buffer release are deferred explicitly. The separate #583 fix branch already exists. |
| U4 | Work in a new 1.19.2 exploration worktree. | Existing branch above; no propagation or merging in this slice. |
| U5 | Make progress on redstone first to inform later contraption design. | Complete and test labeled reads before expanding the language or introducing emitters. |

**Audit evidence:** Extraction retained the speculative status of creation and
construction and the specific labeled condition. Traceability maps every entry
above to implementation or explicit future scope. The adversarial pass checked
that redstone-first does not become authorization to implement all contraptions,
release the buffer, or change manager-local redstone. No source limitation.

## Foundation and contract

The grammar, resource registration, and capability provider already exist.
`BoolHas` sums resource amounts using existing set/side selection semantics.
The provider currently snapshots `BlockState.getSignal` and can receive null as
a direction. The capability cache can retain that snapshot. `insert`/`extract`
on the resource type are placeholders.

Working implementation decisions, grounded in that existing provider:

- Labeled queries read a block's own emitted weak signal, 0-15. They do not
  read incoming neighbor power or infer power relayed by an ordinary solid block.
- An omitted/NULL side reads the maximum of six faces. An explicit side means
  that face of the labeled block; adapt Minecraft's opposite query direction.
- Reads observe current state and block-entity output even through cached handles.
- Signal-emitting blocks remain viable labels even while switched off. The
  shared viability check also lets fancy cables connect to these sources;
  ordinary stone and air must remain non-viable. This closes the discovered
  warning/cleanup path that would otherwise remove working signal labels.
- Keep existing multi-label/set and multi-side summation semantics. `EACH SIDE`
  includes NULL in SFML and can count the same source more than once; show the
  default and single-side forms in the example.
- Signal reads do not consume or insert resources. Keep IO unavailable and make
  insertion return the unaccepted remainder honestly. Writable signal lifetime,
  redstone material placement, tunnel forwarding, and buffer enablement need
  later contracts.

## Tasks

### [x] R1 Prove the existing failures

**Completion evidence:** The original production code compiled successfully.
Dedicated startup failed before any tests in `SFMCommandPaletteActions.<clinit>`
via `SFM.<init>`: loading `Screen` on `DEDICATED_SERVER`. The integrated-client
runner successfully ran 27 cases: 1 passed, 26 failed, including all nonzero
strengths, all physical face orientations, cached comparator changes, label
cleanup, unsupported IO, source replacement, and scheduled routing. Preserved
runtime output: `redstone-baseline-game.log`; build/runner output:
`redstone-baseline-client-console.log`. This is a runtime baseline, not merely
an expected failure inferred from source. Later fixture review found that the
six observer cases initially placed observers powered, which Minecraft resets
on placement. Those six original failures are not independent evidence of a
production bug; the corrected fixtures switch them on after placement.

**Work:** Add focused GameTests using real labeled blocks and the capability
network: strengths 0-15, faces, live comparator output, source replacement,
read-only operations, multi-label conditions, scheduled item routing, and label
warning/cleanup behavior for active and inactive signal sources.
Keep cache tests on the same context/handle rather than `helper.assertExpr`.

**Validation:** Run from the exploration worktree:

```powershell
sfm-propagate-changes.exe --log-filter info --log-file redstone-baseline-client.log game-test run-client --branch feat/1.19.2/contraption-as-code --filter 'redstone_query_*'
```

**Completion:** Record reproducible baseline failures against unmodified production code.

### [x] R2 Implement live read-only signal queries

**Implementation:** `RedstoneSignalCapabilityProvider.WorldSignal` reads current
block state/output, aggregates unspecified sides by maximum, converts physical
faces to Minecraft query direction, and rejects mutation. `RedstoneResourceType`
no longer advertises IO and returns the full insertion remainder. The shared
viability check recognizes `isSignalSource()` without making air/stone valid.
Added lever toggle and shipped-template compilation coverage after the baseline.
The first fix run passed 24/32 cases, including all strengths, live comparator
reads, lever toggling, resource queries, template compilation, and the existing
regressions. Remaining test issues were corrected: observers now switch on after
placement; label cleanup asserts removed positions rather than pruned names;
scheduled routing uses a stable lever instead of manually setting comparator
output that Minecraft may recalculate. Final runtime validation passed on
2026-09-15: `SFM_CLIENT_PUPPET_TESTS_PASSED required=32 total=32`.
The 29 focused cases and 3 existing regressions all passed. This proves actual
off/on/off/on item routing while manager-local power stays zero, all strengths
0-15, six physical faces and relative face queries, cached block-entity changes,
lever state changes, source replacement/removal, multi-label conditions,
read-only IO contracts, label cleanup, and shipped-template compilation.
Production bytecode hashes were unchanged by preserving original CRLF endings.

**Work:** Replace snapshots with a read-only live capability, define null-side
aggregation and physical face orientation, correct unsupported IO reporting,
and recognize actual signal sources in the shared label/cable viability check.
Retain generic capability discovery and inventory semantics.

**Validation:**

```powershell
sfm-propagate-changes.exe --log-filter info --log-file redstone-verified.log game-test run-client --branch feat/1.19.2/contraption-as-code --filter 'redstone_query_*,circle_redstone,side_resolve_direction,move_1_stack_direct'
```

**Completion:** All focused runtime cases pass on current sources, with no
dependency/lockfile mutations or changes to the normal 1.19.2 checkout.

### [x] R3 Document and hand off the tested slice

**Completion evidence:** Updated `redstone_signals.sfml`, the `4.35.0 PRE`
changelog, and the parent exploration. The template is compiled by the runtime
suite. `git -c core.whitespace=cr-at-eol diff --check` passes (the original Java
files use CRLF). Changes are local and uncommitted in this exploration worktree;
no version propagation, release, buffer enablement, or dependency edits occurred.

**Work:** Update the redstone template, gameplay changelog, and exploration note.
Record commands, results, unchanged tool evidence, remaining design questions,
and final process state. Check whitespace/diff and example syntax.

**Completion:** A player can copy the labeled condition and run the named tests.
Only 1.19.2 is claimed as verified; other versions remain unpropagated.

## Operational readiness

- Follow [repository testing guidance](goal%20execution%20and%20testing%20readiness%20guidelines.md); use no Gradle.
- Dependency posture: frozen. No declaration/lockfile edits, new dependencies,
  or reference clones; only canonical rehydration of pinned caches is allowed.
- Installed CLI: `<CARGO_HOME>/bin/sfm-propagate-changes.exe`,
  rev `8794d96fd`, SHA256 `23E0329C19FBADD9F05203CAA5A3A6A541A4126E0C438C3FA7EBA24D120750EE`.
- CLI/tooling source changes: none planned. Its revision matches the worktree
  base; no installer is needed for Java-only work.
- Initial process scan: no java/javaw/SFM CLI/Cargo/rustc/Teamy Terminal processes.
- Test cache/profile: feature-branch integrated client (`runClientPuppet`) after
  the unrelated dedicated-server startup failure above. Per-worktree outputs are
  under `platform/minecraft/build/sfm-toolchain`; pinned artifact cache is
  `%LOCALAPPDATA%/teamdman/sfm-propagate-changes/cache/minecraft-toolchain`.
  First run generated/remapped Minecraft/Forge sources and pinned dependencies;
  96/96 artifacts had portable provenance, with local fallback disabled.
- The dedicated-server run and failing baseline client exited; no process was
  forcibly terminated. The temporary log-reader shell also exited.
- Verified runner logs: `redstone-verified-console.log` and
  `redstone-verified-game.log`. The latter preserves the 32/32 runtime result;
  future launches overwrite the tool's own `run/runClientPuppet/console.log`.
- User install required: no. No CLI/native worker source or generated tooling
  input changed; the installed CLI revision matches this worktree's base.
- Final runner exited with code 0 after confirming 32 required passes. The
  final process scan found zero exploration Minecraft/CLI processes; clients
  and servers are stopped. No unrelated process was stopped. The installed
  CLI hash/revision was rechecked and exactly matches the initial values above.
- Manual test: from this worktree, run the R2 command. It creates a fresh test
  world, runs 32 cases, reports the required pass count, and closes the client.
  Add `--keep-open` to that command to explore the test world after the tests.
- Dedicated-server validation remains unavailable at this base because of the
  independently reproduced client-class loading failure; integrated-client
  validation is complete. Other Minecraft versions are unpropagated/unverified.

## Risks and later scope

The subsequent [buffer counter plan](buffer%20redstone%20counter%20plan.md)
extends this slice with writable stored units in buffers. World signals remain
read-only; the earlier resource-wide IO restriction now delegates to each
handler's capabilities. The query results above describe the prior checkpoint.

Tests must cover the actual network and timer path, not just a direct provider.
Directional cases must use a directional source. Comparator block-entity output
must change without replacing its block state to expose cached snapshots.
Chunk unload/reload and tunnelled redstone forwarding remain follow-up work;
this slice does not claim either behavior verified. Future writable signals need
an explicit reset/lifetime rule before they can serve as contraption ports.

## Integration follow-up

The user subsequently requested a documented checkpoint merged into the main
1.19.2 branch. See the [integration plan](redstone%20checkpoint%20integration%20plan.md)
for the later commit/merge authority and evidence. The no-commit restrictions
above describe this earlier implementation stage only.
