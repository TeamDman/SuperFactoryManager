# Finish source simplification

Plan status: Complete. Branch: `main`. Updated: 2026-10-08.
Starting checkpoint: `8f06dcef8`. Use `[ ]`, `[~]`, `[x]` for task status.

Outcome: 207 templates simplified and their projections manifested. The fresh
full-tree audit reports zero candidates and zero failures. Current-source Rust
validation passed, the updated CLI is installed, and all changes are committed
locally without pushing. Other feature plans are unchanged by this goal.

## Requirements and intent audit

| ID | User direction | Work and acceptance |
| --- | --- | --- |
| S1 | Finish remaining source simplify operations. | Audit the complete Java template inventory and resolve all reported whitespace candidates. |
| S2 | Begin with two parallel agents, one file each. | Batch 1 and its measured result below. |
| S3 | Increase beyond two after success, continuing to scale where useful. | Batch 2 uses three workers; four session slots include the coordinator, so three is the current worker limit. |
| S4 | Continue until the audit has no remaining whitespace issues. | Final fresh audit, pinned Git verification, and manifestation checks; no silent skipped errors. |

Intent audit: extraction covered S1-S4 from the current goal; traceability maps
them to the three tasks below; the adversarial pass retained gradual scaling,
one-file ownership, full-tree completion and measured timing. Prior constraints
remain: preserve Java tokens, comments and literals; retain oracle pins; stop
on disk-space errors. Dependency posture is frozen. No pushes are planned.

## [x] 1 Prove a fresh two-worker batch

CableFacadeBlock.java and FancyCableFacadeBlock.java completed in 40.700 seconds
from first dispatch through combined manifestation. Each worker changed only
its assigned template. Each passed all 20 pinned contexts and reduced 36
candidate pairs to zero. Manifestation changed four outputs and left 36
unchanged. This pilot used CLI revision `3de9f3629`; no Rust changes were needed
for that batch.

## [x] 2 Audit and simplify with three workers

The first three-worker batch (ToughCableBlock, ToughFancyCableBlock and
ClientManagerBlock) passed all 60 pinned contexts and reached zero candidate
pairs. Dispatch through manifestation took 72.958 seconds, including coordinator
audit setup. Subsequent disjoint queues used three workers, the available session
limit. Workers edited one assigned template at a time and verified in memory;
the coordinator alone discovered candidates, manifested outputs and committed.
All queues are finished: 207 edited templates and 1,108 changed tracked Java
projections. Checkpoints preserve the batches:

| Templates in batch | Local checkpoint |
| --- | --- |
| 31 | `59283c48a` |
| 45 | `640dba853` |
| 36 | `da4a46bbf` |
| 42 | `cb8da2f48` |
| 38 | `df471768d` |
| 14 | `e0ab05c5a` |
| 1 | `99cb5ef3d` |

A fresh discovery audit caught the last missed candidate, `SFMSubscribeEvent.java`
(100 pairs). Its repair passed all 20 pinned contexts with zero remaining pairs.
The subsequent final audit is recorded in task 3, not inferred from worker queues.
An independent inventory review confirmed that all Java source-rule inputs are
same-path physical files, there are no Java project-file overrides, and all 20
catalog contexts have matching pinned oracles.
The identity shortcut certifies identical inter-context bytes, not historical
oracle parity for untouched directive-free files.

Initial full inventory: 2,610 Java files, including 889 Liquid templates and
1,721 directive-free identity inputs. Metadata has no Java project-file
overrides; identical copies cannot have inter-context whitespace differences.
An early discovery audit exposed 13 verifier failures rather than silently
skipping unsupported inputs. These were historical physical line-ending
differences in 12 files and Java 26 flexible-constructor syntax in one file.
The scanner keeps CRLF line terminators outside line-comment tokens and admits
preserved Unicode literal spellings only when they cannot change boundaries.

The comparator now recognizes only physical line-terminator equivalence inside
block comments and text blocks. JLS 3.4 and 3.10.6 establish that CRLF/CR line
terminators, including text-block normalization before indentation/escape
processing, do not change the text-block value. Comment text, literal content,
escapes and text-block indentation remain exact; source is not rewritten before
parsing. The Java 26 adapter is limited to same-width parser-only placeholders
for validated direct constructor delegation statements; original token bytes and
positions are retained. It rejects misplaced/duplicate delegation and requires
the complete adapted tree to parse without errors. Sixteen focused regressions
pass, including the actual released constructor body, malformed-input rejection,
and rejection of changed comment text, values, escapes and indentation. The new
optimized CLI rechecked all 13 previously failing files successfully across
their 260 pinned contexts; their remaining candidates were then repaired.
WaterTankNetworkFormation and SFMPackFinders also
passed their 40 contexts with zero pairs after comment-only EOL normalization.
Current Clippy passes; task 3 records broad validation and final installation.
The line-ending rule is grounded in the
[Java language specification](https://docs.oracle.com/javase/specs/jls/se26/html/jls-3.html#jls-3.10.6);
the constructor boundary follows
[JLS 8.8.7](https://docs.oracle.com/javase/specs/jls/se26/html/jls-8.html#jls-8.8.7).
The tree audit script emits bounded failure summaries; its initial version
printed excessively large failure comparisons and was corrected.

The compact worker command retains complete checks while bounding its output:

```powershell
sfm-propagate-changes.exe --output-format json source simplify verify --repo-root . --file src/main/java/ca/teamdman/sfm/common/block/CableFacadeBlock.java --summary
```

## [x] 3 Certify and checkpoint the complete tree

All final runtime checks used the installed executable after the last template
edit, at source checkpoint `99cb5ef3d`:

| Check | Final evidence |
| --- | --- |
| Full Java inventory audit | 2,610 inputs: 881 conditional templates across 17,620 contexts and 1,729 identical directive-free inputs; zero candidates/failures, 248.73s |
| Explicit changed-file oracle pass | All 207 edited templates across 4,140 pinned contexts; both outcome flags true and zero candidates, 74.25s |
| Full manifestation dry-run | All 29,544 outputs current across 20 projects; zero changes/removals/writes, 53.53s |
| Current-source Rust gate | `check-all.ps1 -TestWorkers 4` exited 0; dependency policy, formatting, Clippy, all-features build, 98 test shards, binary/doc checks and final default-feature build passed |

The Rust partition covers 1,668 listed library entries and all four integration
targets (70 integration tests passed). Existing opt-in ignored tests were not
enabled or changed. The dedicated promotion fixture passed in 241.93s.
This is a complete current-source gate, not a result borrowed from an older
test binary. The changed-file pass also covers newly directive-free templates,
which the full audit correctly treats as identical inter-context inputs.

The final scope check found only the 207 core Java templates, their 1,108 tracked
Java projections, the bounded audit script, the verifier/scanner implementation
and this plan. Dependency declarations/locks, feature defaults, catalog entries,
oracle pins and non-Java projected resources have no diff from `8f06dcef8`.

### Operational readiness

| Field | Result |
| --- | --- |
| Branch and source checkpoint | `main`, `99cb5ef3d`; the final documentation-only commit does not change executable inputs |
| Tool changes | Java whitespace certification, bounded audit reporting and Java 26 constructor support |
| Installer | `platform/cli/sfm-propagate-changes/install.ps1`, locked/offline; successful final run 1m58s |
| Installed executable | PATH-resolved Cargo `bin/sfm-propagate-changes.exe`, revision `99cb5ef3d` |
| SHA-256 | `439c45fa157ca14f249828de85b8a9dcd0a1e43b488fe09fea1fe2adf993d0b7` |
| User must run installer | No; final hash/revision and 20-context smoke check reverified after validation |
| Dependencies and acquisition | Frozen; no declarations/locks changed, no new dependency or reference clone, no cache rehydration needed |
| Processes and locks | No game started/stopped; validation workers exited normally; installer executable lock resolved by waiting for our read-only checks, not killing processes |
| Manual-only limitation or stretch item | None for this goal; Minecraft gameplay is not the certification boundary |

Run this compact smoke check from the selected SFM checkout root. Both outcome
flags should be true, with 20 contexts and zero remaining candidate pairs:

```powershell
sfm-propagate-changes.exe --output-format json source simplify verify --repo-root . --file src/main/java/ca/teamdman/sfm/common/event_bus/SFMSubscribeEvent.java --summary
```

The coordinator's full-tree check, not a per-file worker command, is:

```powershell
./platform/cli/sfm-propagate-changes/scripts/audit-simplify-tree.ps1
```
