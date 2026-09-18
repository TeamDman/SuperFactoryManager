# Mekanism warning coverage and energy investigation

Plan status: Complete for investigation and reproduction; production energy-transfer fixes require a separate authorized change
Primary implementation root: 1.19.2
Last updated: 2026-09-17
Intent audit: Passed against the current warning-coverage and energy-investigation request

## How to update this plan

Use `[ ]` not started, `[~]` active, `[x]` complete, and `[!]` blocked in task headings.
Update evidence beneath the affected task. Do not replace investigation limits with claims of a fix.

## User guidance and traceability

| ID | Requirement | Work and proof |
| --- | --- | --- |
| U1 | Check for GameTests validating null-side warnings for Mekanism; add missing coverage | Task 1: registered linter, positive and negative cases |
| U2 | Identify the open, possibly pinned Forge Energy issue | Task 2: issue 610 and linked reports |
| U3 | Try to reproduce and investigate the suspected rounding fault | Task 3: real capability boundaries, deterministic output order, written findings |
| U4 | Investigate insertion as well as extraction: an early output might consume the source or prevent later machines receiving energy, including small FE loss and unavailable modpack cases | Task 4: insertion adapter probe, exact warning quantities, 10-destination controls |
| U5 | Use the supplied issue 608 report: simulated 500000 FE, capacity 461100 FE, extraction 101 FE, insertion 100 FE, loss 1 FE | Task 4: retain-limited and partial-extraction variants; do not assume which explains the reporter's source |
| U6 | Prioritize complete downstream starvation over small insertion loss; compare the supplied Oceanblock 2/SFM 4.34.0 report, nearly full idle JDT droppers and some breakers, full Mekanism cube, label-order/source workarounds and proposed quantity/retention programs | Task 5: repeated-pass dropper sequence, nine consumers, workaround controls, exact-world limitations |
| U7 | Add Just Dire Things as a test dependency and reproduce starvation with real mod blocks | Task 6: narrowly pinned 1.21.1 GameTest dependency and end-to-end SFML/capability test |
| U8 | Make dependency discovery avoid opaque project/file IDs, using name, popularity and compatible releases | Task 6: exercise existing CurseForge search, files and dependency-add CLI; document exact commands |
| U9 | Allow exact Facet/Vox alignment with the working baseline pin to unblock compilation | Task 6: same source revision/hash, no additional library updates or scope expansion |
| U10 | Update issue 610 with the findings through gh outside the sandbox; commit and push the completed work | Publication follow-up in the linked build-health plan; preserve the original issue and distinguish reproduction from a production fix |

Original intent audit: extraction kept warning coverage separate from energy diagnosis; traceability maps each request above; adversarial review preserved rounding as a hypothesis and did not authorize a production energy fix, dependency change, GitHub comment, commit, or push. Later dependency approvals and U10 supersede only their named boundaries. The publication request is available in full.

Follow-up intent audit: pass 1 captured the output-side correction and exact user-supplied warning as U4 and U5. Pass 2 maps both to task 4 and its separate adapter, SFM, and modpack evidence. Pass 3 preserves uncertainty about the first destination, iteration order, missing centrifuge mods, buffer sizes, and the cause of the reduced extraction. Earlier completed work is summarized by this ledger; the follow-up messages and report were read in full.

Idle-dropper intent audit: pass 1 reread the supplied Discord transcript and captured its stronger failure mode as U6. Pass 2 maps the idle dropper, still-full source, repeated starvation and workarounds to task 5. Pass 3 preserves that the `HungryMachines` ordering and 1000-FE variants in the quoted reply were suggestions, not confirmed successes in that transcript. The exact JDT/Mekanism/modpack versions and raw stored FE are absent. The supplied rounded display does not prove a 1-FE gap. Do not conflate this failure with task 4's loss.

## Constraints and foundation

Tasks 1 to 5 used frozen dependencies. Task 6 authorizes a pinned JDT GameTest dependency and any required runtime dependencies. The user subsequently approved exact Facet/Vox pin alignment with baseline; all other dependencies remain frozen. Use the installed SFM CLI, not Gradle.
Preserve the existing uncommitted netherite pattern and changelog edits.
Only Java tests, their dependency declarations, a standalone diagnostic helper, and these notes change. No user world, cursor, or live client is required.
Use repository-relative paths in durable notes, not machine-specific checkout/cache paths.

### [x] 1. Replace vacuous warning coverage

The old `MekanismNullIoDirectionGameTest` immediately succeeded with a TODO.
It now asserts warnings stored on the manager disk. The new `MekanismSidednessLinterGameTestGenerator` covers implicit and explicit null for FE, items, fluids and infusion; input/output separately; physical sides; mixed null/physical sides; read-only conditions; and non-Mekanism blocks. All 15 cases passed through runtime registration and manager/disk warning collection, including exact warning keys and label/statement arguments.

Validation: `sfm-propagate-changes.exe game-test run-server --branch 1.19.2 --filter "mekanism_null_io_direction,mekanism_sidedness_linter_*"`

### [x] 2. Identify the report

Read [issue 610](https://github.com/TeamDman/SuperFactoryManager/issues/610), 'Machines not receiving energy', through GitHub CLI and the issue page. It explicitly proposes FE/internal-unit conversion rounding and requests GameTests. [Issue 609](https://github.com/TeamDman/SuperFactoryManager/issues/609) reports position-dependent starvation on 1.21.1; moving the manager or round robin can help. [Issue 575](https://github.com/TeamDman/SuperFactoryManager/issues/575) reports apparent whole-manager stalls under load. These are reports, not yet a confirmed common cause. Pin status is not needed to identify the issue.

### [x] 3. Reproduce and reduce energy behavior

The 14 real-cube boundary cases in `Issue610ForgeEnergyGameTestGenerator` passed with the 4 existing `mek_energy_*` tests. They cover gaps of 0, 0.1, 1, 2.4, 2.5, 2.6 and 5 joules, both destination orders, non-mutating simulation, progress and conservation. These 1.19.2 cases did not reproduce starvation. An initial run used the reserved word `empty` as a label and failed parsing; the corrected label is `hungry`. Do not count that fixture mistake as reproduction evidence.

#### Pinned 1.21.1 adapter reproduction

Read the exact existing 1.21.1 lockfile and cached Mekanism jar (CurseForge file 5656332, BLAKE3 `1a7f3e07579cd712e3d8599a696f51119107889b`, manifest version 10.7.4). `javap` confirms integer-joule conversions and a default `feConversionRate` of 2.5. Do not substitute the managed Git source provider here: that checkout uses a newer transaction API than this binary.

`scripts/diagnostics/issue-610/MekanismRoundingProbe.java` executes the actual pinned `ForgeEnergyIntegration` class with an in-memory joule store and an explicit 2.5 converter. It uses the package-private converter constructor to avoid a full game bootstrap. Source energy starts at 2500 J (1000 FE). Observed results:

| Requested FE | Simulated FE | Executed FE | Remaining J |
| --- | --- | --- | --- |
| 1 | 0 | 0 | 2500 |
| 2 | 2 | 2 | 2495 |
| 3 | 2 | 2 | 2495 |
| 4 | 4 | 4 | 2490 |
| 100 | 100 | 100 | 2250 |

Simulation does not mutate storage. A 1 FE request converts to 2 J after truncation. Conversion back yields 0 FE, so the adapter extracts nothing. This is a minimum transfer quantum, not exhausted energy or integer overflow.

Run using Java 21 and the existing 1.21.1 compile classpath (no downloads or lockfile changes):

```pwsh
./scripts/diagnostics/issue-610/run.ps1 -JavaHome '<jdk-21>' -CompileArgs '<1.21.1-root>/platform/minecraft/build/sfm-toolchain/project/javac-gametest.args' -OutputDirectory '<scratch>/issue-610-classes'
```

#### SFM source exhaustion investigation

Both the baseline and 1.21.1 `OutputStatement.moveTo` mark a source done after any zero extraction. The outer output loop then skips later destinations for that source. A non-Mekanism destination with room for 1 FE can therefore exhaust the *iteration*, even when the Mekanism source could serve a larger request to another destination.

`Issue610QuantizedSourceCharacterizationGameTest` uses SFM's real transfer method with a reduced two-FE-granularity source, based on the binary probe. It checks the 1-FE-gap starvation, a 2-FE-gap control, a completely full first destination, and reversed output order, plus conservation. It deliberately describes current faulty behavior; it must become a forward-progress regression when production code is fixed. All 4 assertions passed in the diagnostic GameTest. The source remains at 100 FE but is marked done, leaving the later destination empty; controls transfer the complete 100 FE budget without loss.

Final combined validation passed 34 required GameTests in 2m27s, exit 0:

```pwsh
sfm-propagate-changes.exe game-test run-server --branch 1.19.2 --filter "mekanism_null_io_direction,mekanism_sidedness_linter_*,issue_610_*,mek_energy_*"
```

The reusable standalone probe script also passed after its final edit, with all 5 request sizes asserted against the table above. Logs from the runtime live under `platform/minecraft/build/sfm-toolchain/run/runGameTestServer/console.log`; the CLI reports 'Validated 34 required game tests passed'. Do not treat the diagnostic test passing as a bug fix.

Suggested fix for a separate implementation task: a zero result for this requested amount must not mark the source globally exhausted unless a broader extraction probe also returns zero. Bound retries and preserve conservation; do not repeatedly retry the same untransferable pair.

Limits: no full 1.21.1 user-world reproduction, Applied Flux runtime, or proof that every report shares this cause. This mechanism explains later energy starvation, not by itself the whole-manager halt reported in issue 575. No production energy logic was changed.

Validation: `sfm-propagate-changes.exe game-test run-server --branch 1.19.2 --filter "issue_610_*,mek_energy_*"`

Completion means tested warning coverage plus a reproducible energy finding or a precise account of negative results and the next diagnostic boundary. A production energy change needs separate authorization.

### [x] 4. Characterize insertion loss and later destinations

Completion notes: the final standalone probe passed all 26 scenarios against the pinned adapter. The combined runtime passed all 42 required GameTests, including 8 new output characterizations. Test source compiled through the installed SFM CLI; no production transfer behavior changed. These tests deliberately assert current defects and need new conservation expectations when a fix is authorized.

Work: extend the pinned-adapter probe with insertion sizes, capacity boundaries and the exact issue 608 quantities. Add separately named diagnostic GameTests using SFM's real transfer method and resource trackers. Keep production code unchanged.

Source inspection: SFM simulates insertion using stored source energy, then applies input/output quantity and retention limits. It extracts for real, then inserts the extracted amount. A rejected remainder is logged and discarded; this branch does not itself stop the output loop. Both 1.19.2 and 1.21.1 have the same transfer sequence. The warning calls stored source energy 'Simulated extraction', although it was not an extraction simulation.

Read [issue 608](https://github.com/TeamDman/SuperFactoryManager/issues/608), including the comment about `RETAIN` below machine capacity after adding energy upgrades. The report names Mekanism 10.7.19.85, newer than our pinned 10.7.4 binary. Read the exact upstream [10.7.19.85 adapter source](https://github.com/mekanism/Mekanism/blob/v1.21.1-10.7.19.85/src/main/java/mekanism/common/integration/energy/forgeenergy/ForgeEnergyIntegration.java): it retains the same conversion sequence. No dependency was updated or acquired.

Pinned-binary results: simulation and execution agree for the same insertion request. An empty destination accepts 100 of 100 FE, 98 of 99 FE, and 0 of 1 FE. Therefore a truthful receiver can leave a remainder when SFM simulates a different quantity from the actual offer. The probe reproduced every quantity in the issue report: offer 500000, acceptance 461100, simulated remainder 38900, later offer 101, exact simulation 100, actual insertion 100, remainder 1 FE.

`Issue608OutputEnergyCharacterizationGameTestGenerator` carries the measured receiver behavior into SFM's actual transfer method, using a normal Forge source and real SFM retention/quantity trackers. Its report-retention case checks all 5 numeric warning lines against issue 608. A synthetic capacity of 500000 FE with 38900 FE stored and a retain target of 39001 FE provides the exact numbers. Those fixture capacity/retention values are a consistent reproduction, not values supplied by the reporter.

| Case | First destination gain | Later 9 destinations | Lost FE |
| --- | --- | --- | --- |
| Exact report quantities, 101 FE retention gap | 100 | 11 FE each | 1 |
| Nearby 100 FE retention gap | 100 | 11 FE each | 0 |
| Source returns at most 101 FE, no output limit | 100 | 11 FE each | 1 |
| First output quantity limited to 1 FE | 0 | 11 FE each | 1 |
| First destination rejects the simulation | 0 | 11 FE each | 0 |
| Source contains only 1 FE; first destination needs multiples of 2 | 0 | Next receives 1 FE | 0 |
| Unrestricted first destination with sufficient capacity | 1000 | 0, source legitimately exhausted | 0 |
| All 10 destinations limited to 1 FE each | 0 | 0 | 10 |

All measured amounts and warning counts matched. No simulation mutated energy. The test supplies a fixed order and mirrors the outer loop's done guards; it does not test world slot discovery. These results rule out an unconditional output-rejection bail in the tested transfer path. They demonstrate that small offers can repeatedly lose resources across destinations without a capability lying. The loss branch itself continues; the extraction-zero branch in task 3 separately marks the source done and can starve later destinations.

The report's label 'the output block lied here' is misleading for the reproduced case. SFM compares different request amounts, and exact-amount simulation predicts the 1 FE remainder. Input collection retains references to source slots; there is no pre-extracted whole-source energy buffer to redistribute or refund here. Rejected energy after actual extraction has no recovery path in `OutputStatement.moveTo`.

Recommended separate fix scope: apply limits and negotiate a feasible amount with both capabilities before extraction; account for source extraction granularity as well as destination insertion granularity; bound negotiation; preserve source retention and later-destination progress. Handle unexpected execute-time remainders without silently discarding them. Improve the warning to distinguish stored amount, requested amount, simulated extraction, limited offer, and actual results rather than automatically blaming the output. This is not authorization to implement that change.

Validation: run `scripts/diagnostics/issue-610/run.ps1` as documented above, then `sfm-propagate-changes.exe game-test run-server --branch 1.19.2 --filter "issue_608_*,issue_610_*,mek_energy_*,mekanism_null_io_direction,mekanism_sidedness_linter_*"`.

Completion criteria: preserve exact report quantities and nearby non-loss controls; measure conservation and progress across 10 ordered destinations; distinguish truthful quantization from a dishonest capability; state that the full Applied Flux/ExtendedAE and Productive Bees runtime remains untested. Characterization tests describe current defects, not fixed behavior.

Limits: the exact reason the reporter extracted 101 FE is not established without their program/world. Their retention comment supports one reproduced route; partial source extraction is a separately tested route. No full runtime test uses their Mekanism 10.7.19.85 jar, Applied Flux or ExtendedAE. The pinned older binary and the exact reported version's source agree on the relevant conversion. No conclusion is established for Productive Bees centrifuges or every report of whole-manager stalling.

Final run: the server reported 'All 42 required tests passed' and the CLI validated 42 tests, exit 0. The deliberate loss cases emit resource-loss diagnostics. The combined run also contains slot-pool warnings outside these reduced cases; passing assertions are not a claim of warning-free logs.

### [x] 5. Explain and reproduce idle-dropper downstream starvation

Completion notes: the pinned-adapter probe now passes 27 scenarios, including simulated and executed extraction of 24 FE for a 25-FE request. All 5 new GameTests passed; the combined suite validated 47 required tests, exit 0, in 1m47s. This reproduces repeated full-source/downstream-empty behavior in the reduced SFM transfer path without any resource loss. It remains distinct from task 4 and is not a production fix.

Work: use the user-supplied August 23 Discord transcript as the report, not inaccessible Discord content or an unseen video. It describes Oceanblock 2/SFM 4.34.0, a correctly sided full Mekanism cube, an idle advanced dropper near 100000 FE, downstream starvation immediately after adding its label, and recovery when removing it. Cache rebuilds did not help. Later, two of eight advanced breakers showed similar behavior. LaserIO worked; another source was suggested. Preserve those observations without claiming all have the same proven cause.

Original program:

```sfml
Every tick do
  Input fe:: from PowerSource top side
  Output fe:: to Machines
End
```

Read Just Dire Things' 1.21.1 source at commit `506416a9c7675cacc246a47d2b0c9d6b27eff9a8`: `MachineEnergyStorage` inherits ordinary NeoForge `EnergyStorage`, `PoweredMachineBE` defaults to 100000 FE, and `DropperT2BE` spends 25 FE when spawning an item. [Source](https://github.com/Direwolf20-MC/JustDireThings/blob/506416a9c7675cacc246a47d2b0c9d6b27eff9a8/src/main/java/com/direwolf20/justdirethings/common/blockentities/DropperT2BE.java). This inspected version is not established as the reporter's exact jar. No local locked JDT dependency was found or added.

Reproduced sequence with a natural initial condition: one operation takes a full dropper from 100000 to 99975 FE. A two-FE-granularity source supplies 24 of the requested 25 FE, leaving 99999. The next pass requests 1 FE, extracts zero, and SFM marks the still-full source done, skipping all later destinations. The same failure repeated for the remaining 4 passes while the dropper stayed idle. This needs no dishonest output, resource loss, or continued dropper consumption. Its application to the exact user world remains a hypothesis until raw storage/version/config evidence is available.

Validation: the existing pinned-adapter probe adds the 25-FE request. `Issue610DropperStarvationGameTestGenerator` checks five fresh-input passes across 10 destinations using real SFM `moveTo`, trackers and `RoundRobin` label ordering. Cases: dropper first; healthy label first; ordinary FE source; quantity 1000; retention 1000. Source refilling and consumer draining are explicit synthetic load conditions. These tests do not instantiate JDT blocks, run the SFML parser, or test cable/capability discovery.

Inspecting `RoundRobin.getPositionsForLabels` shows labels processed in written order. `HungryMachines` has no special semantics; separating the dropper into the last label protects preceding consumers. `InputStatement.gatherSlots` caches source slots across output statements. Splitting outputs but putting the problematic one first does not independently reset a source marked done. Trace logs for the reduced failure should show a one-FE proposed move, zero extracted, and source iteration ending; there need not be a resource-loss warning.

The exact existing trace message is `extracted nothing, marking this input slot as done` (`log.sfm.statement.tick.io.move_to.extracted_nothing`). This is a diagnostic signature to check in the reporter's small setup, not a claim that their trace was supplied.

| Tested variant | Idle dropper after first pass | Later passes for 9 consumers | Source after a starving pass |
| --- | --- | --- | --- |
| Dropper first, quantized source | 99999 FE | All receive zero | 1000000 / 1000000 FE, incorrectly marked done |
| Healthy label before dropper | 99999 FE | All receive 1000 FE each | No starving pass |
| Ordinary FE source | 100000 FE | All receive 1000 FE each | No starving pass |
| `OUTPUT 1000` per destination | 99999 FE | All receive zero | 1000000 / 1000000 FE, incorrectly marked done |
| `OUTPUT RETAIN 1000` per destination | 99975 FE, deliberately skipped | All receive 1000 FE each | No starving pass |

Quantity 1000 is a maximum, not a minimum; the destination's 1-FE gap still reduces the actual request. Retention 1000 skips the already-near-full dropper, avoiding that request. Moving the dropper last does not fix its one-FE top-up, but protects earlier consumers. These controls are now verified in the reduced test, not retroactively reported as successful in the Discord transcript.

Fix priority: do not globally retire a source merely because this destination's requested quantity extracted zero. Preserve later-destination attempts and bounded iteration. Add a regression requiring progress in the currently starving cases. Keep the distinct insertion-loss/negotiation work from task 4 in scope for a separately authorized production change. Rebuilding network caches does not address this arithmetic/control-flow failure; changes in within-label iteration can move which machines precede it.

Completion criteria: establish the transition from a legitimate 25-FE deficit to a persistent one-FE gap; assert a full source and zero allocation to all nine subsequent machines over repeated passes; test the proposed workaround forms without claiming them proven in the reporter's exact world. Production code remains unchanged.

Validation command: `sfm-propagate-changes.exe game-test run-server --branch 1.19.2 --filter "issue_608_*,issue_610_*,mek_energy_*,mekanism_null_io_direction,mekanism_sidedness_linter_*"` plus the pinned-adapter probe in task 3.

### [x] 6. Reproduce with real Just Dire Things blocks

Completed September 16 on rebuilt current 1.21.1 source: the dedicated server reported **all 6 required tests passed**, and the CLI validated that count before exiting 0. JDT 1.5.7 and the existing exact Mekanism runtime jar were loaded. Added GameTest-runtime scope to that Mekanism main component only; no normal-runtime/bundle requirement or mod version changed. The prerequisite release-build plan records the cross-version compilation and physical-client registration fixes. No production transfer code was changed.

Measured real-block outcomes over five SFML passes:

| Case | Culprit FE after pass 1 | Later consumer, pass 1 | Later consumer, passes 2–5 |
| --- | --- | --- | --- |
| Single label, culprit first in actual iterator | 99999 | 100000 FE | 0 FE; source stays full at 256000000 J |
| Ordered dropper-first labels | 99999 | 100000 FE | 0 FE; source stays full at 256000000 J |
| Healthy label first | 99999 | 100000 FE | 100000 FE every pass |
| Already-full dropper | 100000 | 100000 FE | 100000 FE every pass |
| `OUTPUT 1000` per destination | 99999 | 1000 FE | 0 FE; source stays full at 256000000 J |
| `OUTPUT RETAIN 1000` per destination | 99975, skipped | 1000 FE | 1000 FE every pass |

The natural 25-FE operation, discovered world capabilities, manager/cables, parsing, label iteration, actual transfers and conservation assertions all ran. Source refill and healthy-consumer drain remain explicit synthetic load, not hidden automatic machine work. This confirms the proposed failure mechanism with pinned real mods, not every unknown version/configuration in the original modpack. Characterization passes demonstrate a bug; they are not a production fix. Keep the exact command at the end of this task for reruns.

Follow-up authority: the user approved compilation repair and broadened it to all release branches, after historical cleanup research and explicit CurseForge discovery authentication. Continue through `discovery auth and release build health.md`; the earlier permission blocker below is superseded, not evidence of a runtime pass.

Historical checkpoint, superseded by the completion evidence above: dependency and six real-block fixture cases were added on 1.21.1. Fixture-only compilation against cached classes was initially only an API check; the canonical build then failed before launch. It was not counted as runtime validation.

Intent audit: pass 1 captured the latest request as U7 and U8, including development-only rather than mandatory mod dependencies. Pass 2 maps both to this task. Pass 3 preserves the distinction between adding a reproduction and fixing production transfers, and between this pinned fixture and the reporter's unknown exact modpack versions. No commit, push, unrelated dependency update, or external publication is authorized.

Target exception: 1.21.1 is the oldest checked target combining an available JDT release with the integer-joule Mekanism adapter under investigation. CurseForge search returned no 1.19.2 Forge match. Place the target-specific runtime fixture and lockfile entry on 1.21.1, keep investigation notes on baseline 1.19.2, and do not propagate this dependency to unsupported branches. Existing baseline tests remain unchanged.

Discovery already exists in the installed CLI; no new Rust implementation or installer is needed:

```pwsh
sfm-propagate-changes.exe curseforge mod search "Just Dire Things" --minecraft 1.21.1 --loader neoforge
sfm-propagate-changes.exe curseforge mod files 1002348 --minecraft 1.21.1 --loader neoforge
```

The first command returns project name, slug, summary, download count and popularity rank. The second returns exact file IDs, filenames, release types, dates, game versions and loaders. On September 16, the matching project had 22415472 downloads; the newest listed compatible release was 1.5.7, file 7463040. Validate the jar's dependency metadata before the test run, without upgrading existing Mekanism or NeoForge.

Added using the existing CLI:

```pwsh
sfm-propagate-changes.exe dependency add justdirethings --branch 1.21.1 --curseforge-project 1002348 --curseforge-file 7463040 --kind mod --role test --scope gametest-compile --scope gametest-runtime --display-name "Just Dire Things" --project-url "https://www.curseforge.com/minecraft/mc-mods/just-dire-things"
```

Locked artifact: `curse.maven:just-dire-things-1002348:7463040`, BLAKE3 `1ed165a8e5b14fa10b4dc70e900f96466b1b41d8`. Inspected the actual jar: required dependencies are only Minecraft `[1.21,1.22)` and NeoForge `[21.0.138,)`; existing 1.21.1/21.1.206 satisfies them. No additional mod was added. JDT has no ordinary compile/runtime/bundle scopes and no required SFM mod-metadata entry was introduced.

CLI follow-up: `dependency add` rewrote this schema-v4 lockfile to schema v3 and removed its empty feature/profile declarations. Restored those exact original declarations with a targeted patch. Final semantic diff preserves schema/profiles and changes only JDT plus the separately approved Vox entry. Investigate this writer behavior separately; the discovery CLI itself already meets the requested name/popularity/compatible-file workflow.

Fixture: `platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/justdirethings/Issue610JdtStarvationGameTestGenerator.java`. Cases: the report's single-label program, ordered dropper-first, healthy-first, full dropper, quantity 1000, retention 1000. The single-label fixture uses the persisted label iterator to place the culprit first and asserts that ordering before execution. All cases use a manager, cable, real Mekanism cube and two real JDT advanced droppers. The culprit performs its actual `spawnItem` operation once, costing 25 FE, and remains idle. The second empty dropper is a real FE consumer with a synthetic between-pass load. Five full SFML passes inspect raw FE, joules and conservation; source refill is explicit. No reduced energy capability or direct SFM `moveTo` call replaces the integration path.

Original characterization expectations, now verified by the runtime table above: the first pass leaves the culprit at 99999 FE while serving the second machine; later dropper-first passes leave the source full and second machine empty. Healthy-first and full-dropper controls serve the second machine every pass. Quantity 1000 still starves; retention 1000 skips the nearly full dropper.

Build blocker: the first canonical attempt reported 28 production compilation errors. Four came from missing Vox `TerminalContentRequest`/`TerminalContentResult`. With explicit user approval, aligned only Vox source acquisition, revision and hash to baseline: commit `f2afdece6c79e64085d2f8c047e22fe16b2c8c54`, BLAKE3 `4d1e88353f941be926fdf84f1dd8da9bd594b60f`. Existing compile/runtime scopes were preserved; baseline's separate bundling change was not copied. This removed the four Vox errors.

The second historical attempt reported 24 pre-existing source-porting errors: Forge event/event-bus imports and removed GuiComponent imports across Explorer/review/terminal panels. The user subsequently authorized repair across the full release matrix. Those errors are now fixed, all source sets compile, and a further dedicated-server Screen classloading failure was repaired before the successful runtime run. No bypassed stale SFM classes supplied the final result.

Work: add exact JDT coordinates using `dependency add`, role `test`, scopes `gametest-compile` and `gametest-runtime`. Build an isolated real manager/cable/Mekanism cube/JDT dropper fixture. Use explicit label order for deterministic failure and reversed-order/full-dropper controls. Assert the dropper's raw FE, later-consumer FE and available source energy over repeated program executions. No reduced capability is sufficient proof for this task.

Validation: run the focused fixture through `sfm-propagate-changes.exe game-test run-server --branch 1.21.1 --filter "issue_610_jdt_*"`. Check that ordinary runtime/bundling scopes and required published mod metadata are unchanged. Confirm only intended lockfile components changed. Passing characterization assertions reproduce a defect; they do not mean production behavior is repaired.

Completion: record exact dependency IDs/hash, required transitive dependencies, actual runtime results, rerun command and final process state. If this version does not reproduce, preserve that result and distinguish it from the proven reduced mechanism. Keep the single-label user program's unspecified order separate from ordered-label controls.

Risks: JDT version/configuration may differ from the report; capture measured values. Loader requirements may block a pinned release; inspect them rather than silently upgrading. Automatic dropper activity or cube ejection could bypass SFM; keep the dropper idle and disable cube ejection. Test-only scopes must exclude JDT from the distributed SFM jar and normal runtime requirements.

## Operational readiness

Task 6 final state: 1.21.1 at `0f89d40be` plus uncommitted API/client-registration repairs, test fixture, approved JDT/Vox records and Mekanism GameTest-runtime scope. The installed CLI reports revision ea4dcc9aa, SHA-256 `a044ab89b35768e5981b8fff1c0685240386ce23d22b880b3cc43249104dceea`; native authentication was installed during the linked build-health task and needs no user install. Current-source compilation, 430 JUnit tests and six real-mod GameTests pass. Test logs are under `platform/minecraft/build/sfm-toolchain/run/runGameTestServer/console.log`. All launched servers/JVMs exited; final process inspection found none remaining. No Minecraft client/user world, public issue mutation, commit or push. The linked build-health plan records the ten-target matrix, dependency comparisons and separate existing Rust snapshot limitation.

The following record applies to completed tasks 1 to 5, before task 6's authorized dependency changes:

Target: 1.19.2 at 9e3d197a8 plus preserved local changes. Installed CLI reports revision 9e3d197a8; SHA-256 `be3d494c4cff699f236fc000dbb234ddf9d60c77029f70862377cfbf7f0f629b`.
No tooling source changes: no install required. No SFM Java/client/test processes at preflight. Only isolated GameTest servers and short-lived probe JVMs launched; they exited after validation. Build progress used `--log-file` and `--log-filter info`. Dependencies and lockfiles are unchanged, no new repositories or dependencies were acquired, and no client was launched or hot-reloaded. The command above recompiles the tests for manual reruns. No commit, push, or GitHub issue mutation was performed.
