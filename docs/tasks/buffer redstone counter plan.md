# Buffer redstone counter

**Plan status:** Complete
**Primary implementation root:** branch `feat/1.19.2/contraption-as-code`
**Last updated:** 2026-09-15
**Intent audit:** Passed against the available user requests and accepted counter proposal.

## How to update this plan

Use `[ ]` not started, `[~]` in progress, `[x]` complete, and `[!]` blocked.
Update task headings and their evidence together. Keep one implementation focus.

## User guidance and intent audit

| ID | Guidance | Coverage |
| --- | --- | --- |
| U1 | Explore redstone in a new SFM 1.19.2 worktree as a foundation for future contraptions. | Existing worktree and [labeled query foundation](labeled%20redstone%20queries%20plan.md); no propagation. |
| U2 | Implement a buffer counter whose stored `redstone::` quantity can increase and decrease, with a comparator measuring it. | C1-C3; real SFML transfers and comparator ticks. |
| U3 | Accepted proposal: sequence 0, 1, 2, 1, 0; exact SFML counts above 15; comparator capped at 15; save/load. | C2-C3; boundary, simulation, conservation, and persistence tests. |
| U4 | Functions, manager construction, and redstone blocks as sources/sinks are wider future ideas. | Preserve world-signal read-only behavior. Counter tests seed a donor buffer; production item enablement, material creation, and other buffer resource persistence remain separate work. |

- Extraction: reread the initial contraption/redstone requests, counter question, accepted proposal, and implementation authorization. Captured U1-U4.
- Traceability: mapped stored state, SFML IO, comparator output, save/load, and limits to implementation and runtime proof below.
- Adversarial omission: distinguished exact stored counts from 0-15 physical output, simulations from mutation, buffer transfers from creation, and 1.19.2 exploration from production enablement.
- Source limitation: some earlier work is compacted; use the existing query plan for its completed evidence, not a claim to have reread unavailable exchanges.

## Decisions and pre-implementation foundation

The existing storage has bounded integer insert/extract operations but no change
callback. Resource-level IO is stubbed. The world provider (priority 0) shadows
block-entity storage (priority -100). Buffers have neither comparator hooks nor
contents persistence. The previous 32 selected GameTests passed for world reads.

| Question | Decision | Proof |
| --- | --- | --- |
| Resource discovery | World signals become the fallback after actual storage capabilities. | Network queries and transfers on labeled buffers; existing world cases. |
| Arithmetic | Transfers conserve units between buffers, with no underflow or overflow and side-effect-free simulations. | Counter sequence, over-15 transfer, near-capacity remainder, simulations, single-resource exclusion. |
| Readout | `HAS` reads the exact integer; comparator reads min(count, 15), including zero after emptying/removal. | Actual adjacent comparators after scheduled ticks. |
| Persistence | Save/load redstone count in block-entity NBT; clamp malformed negative/over-capacity values; absent field means zero. | Registered block-entity round trip and live reload; retained handles see the new quantity. |
| Scope | Existing experimental buffer availability stays unchanged. Tests initialize donor storage; no infinite world-signal extraction. | Shipped example documents setup; read-only regression tests. |

## Tasks

### [x] C1 Wire mutable buffer storage and comparator notifications

**Completion notes:** Added storage change callbacks, per-handler resource IO,
actual capacity, comparator readout and removal notifications, and redstone NBT
save/load. World signal fallback now follows block-entity storage and declines
buffers when their redstone capability is temporarily unavailable. Filled
buffers remain viable labels/cable endpoints. The first runtime run passed all
storage, comparator strengths 0-16, removal, exclusivity, and NBT round-trip cases.

**Work:** Delegate resource IO to storage, use actual capacity, repair provider
priority, and notify the owning block entity only on real changes. Add redstone
NBT save/load and comparator hooks. Preserve single-resource ownership and
world-signal rejection. Mark version-dependent Minecraft hooks with adapters.

**Validation:** C2 runtime cases on the current compiled source.
**Completion:** Network-discovered buffer quantities can be changed and saved;
Minecraft comparators update without a test manually refreshing their output.

### [x] C2 Prove behavior with focused GameTests

**Evidence:** First current-source run compiled and passed 55/59 selected tests.
Four scheduled-transfer fixtures incorrectly used `OUTPUT TO` (the item default)
and therefore moved no redstone. Fixed them to `OUTPUT redstone:: TO`; the
sequence case now runs the shipped example itself. Added label cleanup and
resource-switch regressions for issues found in review, plus long NBT bounds.
Preserved `buffer-counter-first-game.log` and `buffer-counter-first-console.log`.
Final runtime verification passed on 2026-09-15 at 02:31:48 local time:
`SFM_CLIENT_PUPPET_TESTS_PASSED required=61 total=61`, recorded in
`buffer-counter-verified-game.log:5131`. All 29 buffer cases, 29 world-query
cases, and 3 existing regressions passed. This includes the shipped example's
0-1-2-1-0 sequence, transfers above 15 and at maximum integer capacity, actual
comparator strengths 0-15 with saturation at 16, registered block-entity NBT
round trip, retained handles after NBT changes, malformed/long NBT bounds,
single-resource ownership, label cleanup, resource switching, and world IO
rejection. Compile and runner output: `buffer-counter-verified-console.log`.

**Work:** Add a buffer generator testing the 0-1-2-1-0 sequence through scheduled
SFML, exact over-15 reads/transfers, output saturation, capacity/remainder and
simulation invariants, mixed-resource rejection, NBT restoration and malformed
values, reload visibility, and removal. Compile the shipped example.

**Validation:** Run from the feature worktree:

```powershell
sfm-propagate-changes.exe --log-filter info --log-file buffer-counter-verified.log game-test run-client --branch feat/1.19.2/contraption-as-code --filter 'buffer_redstone_*,redstone_query_*,circle_redstone,side_resolve_direction,move_1_stack_direct'
```

**Completion:** All required focused and regression cases pass in the real client.
Record counts and failure/fix evidence, without claiming fixture failures as bugs.

### [x] C3 Document and hand off

**Completion notes:** Added `buffer_redstone_counter.sfml`; the sequence test
executes this shipped example. Updated the existing signal template, changelog,
parent exploration, and historical query plan. The final compile after CRLF
normalization passed; all 14 affected production bytecode hashes match the
runtime-tested build. `git -c core.whitespace=cr-at-eol diff --check` and all 19
local documentation links passed review. CLI hash/revision match preflight,
both clients exited, and the final process scan found zero in-scope processes.
Changes remain local and uncommitted in this feature worktree.

**Work:** Update examples, 4.35.0 PRE changelog, and exploration status. Review
diffs and whitespace; record final tooling and process state with a runnable
manual test command. No commits, releases, pushes, or version propagation.

**Completion:** Documentation matches tested behavior, scope is explicit, and
the user can run the C2 command with `--keep-open` to inspect the fixtures.

## Operational readiness

- Follow [repository testing guidance](goal%20execution%20and%20testing%20readiness%20guidelines.md); no Gradle.
- Base commit: `8794d96fd0f74306d70131c9b4a858db74984a89` plus prior local query changes.
- Dependencies frozen: no declarations or lockfiles change; canonical pinned cache rehydration is allowed. No new clones.
- Installed CLI: resolved from `Get-Command sfm-propagate-changes.exe`; revision `8794d96fd`, built 2026-09-15 01:38:19 -04:00; initial SHA256 `5FAB81DC99EF2BEFE8E636EE229C8CF8A51DCA15B49F95C28547686C065ADDAD`.
- CLI/tooling source unchanged; installer not required for Java-only edits. Recheck after final edits.
- Initial process scan: no Java/SFM CLI/Cargo/Terminal process tied to this worktree. Bounded process lifecycle authority from repository guidance applies only to proven test processes.
- Test profile: integrated client `runClientPuppet`; generated files under `platform/minecraft/build/sfm-toolchain` and the tool's pinned shared cache.
- Dedicated-server startup at this base has a previously reproduced unrelated client-class loading failure. Use integrated client; other versions unverified.
- Build commands: C2's integrated-client command exited 0 after 61/61 required passes. Final format verification used `sfm-propagate-changes.exe --log-filter info --log-file buffer-counter-format-compile.log run compile --branch feat/1.19.2/contraption-as-code`, exit 0.
- Bytecode evidence: `buffer-counter-bytecode-before.log` contains hashes from the successful runtime build. All 14 affected production class hashes matched after the final compile. Java CRLF preservation made no bytecode change.
- User must run install script: no. CLI/native tooling sources and generated tooling inputs did not change. Final resolved executable is the same `sfm-propagate-changes.exe` in `<CARGO_HOME>/bin`, with the exact revision/hash recorded above.
- Dependency declarations/lockfiles and CLI source changes: zero. Reused the pinned build cache; no new repositories or arbitrary local artifact substitutions. No cache/lock reset or process termination was needed.
- Both launched clients exited normally. Final compile exited 0; final scan found zero Java/SFM CLI/Cargo/Terminal processes tied to this worktree. No unrelated process was touched.
- A Git check in the elevated shell encountered worktree ownership protection. Source review ran in the owning sandbox; no global Git trust settings were changed.
- Manual test: run C2's command from this worktree, adding `--keep-open` to inspect fixtures. It launches the integrated client in a fresh test world; expect 61 required passes. The shipped `buffer_redstone_counter.sfml` includes command placement/seeding instructions and runs the exact counter sequence tested here.
- Runtime evidence: `buffer-counter-verified-game.log` preserves game output; `buffer-counter-verified-console.log` records compilation and the successful runner. Final format compilation output is `buffer-counter-format-compile-console.log`.
- Scope limits: only 1.19.2 integrated client is verified. Buffer production item/recipe enablement, persistence for other buffer resources, world-signal writes, and newer-version propagation remain separate work. This bounded request has no continuation ladder.

## Risks and acceptance

Cached handlers must preserve live state across count changes and NBT reload.
Simulations must not change icons, dirty chunks, or comparator output. Storage
must remain exclusive when another resource occupies a buffer. Actual comparator
ticks and scheduled programs provide evidence beyond direct API assertions.
Persistence claims cover redstone counters only, not all experimental resources.

## Integration follow-up

The user subsequently requested a documented checkpoint merged into the main
1.19.2 branch. See the [integration plan](redstone%20checkpoint%20integration%20plan.md)
for the later commit/merge authority and evidence. The no-commit restrictions
above describe this earlier implementation stage only.
