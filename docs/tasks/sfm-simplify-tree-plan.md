# Finish source simplification

Plan status: Active. Branch: `main`. Updated: 2026-10-08.
Starting checkpoint: `8f06dcef8`. Use `[ ]`, `[~]`, `[x]` for task status.

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
unchanged. The installed CLI is revision `3de9f3629`; no Rust changes were needed.

## [~] 2 Audit and simplify with three workers

The first three-worker batch (ToughCableBlock, ToughFancyCableBlock and
ClientManagerBlock) passed all 60 pinned contexts and reached zero candidate
pairs. Dispatch through manifestation took 72.958 seconds, including coordinator
audit setup. Subsequent three-worker batches continue at the session limit.
The first 31 templates and their affected outputs are committed at `59283c48a`.
A further 45 templates, committed at `640dba853`, passed a coordinator check of all
900 pinned contexts. Their selected manifestation changed 471 outputs and left
259 unchanged, with no removals. Further disjoint worker queues remain active.

Initial full inventory: 2,610 Java files, including 889 Liquid templates and
1,721 directive-free identity inputs. Metadata has no Java project-file
overrides; identical copies cannot have inter-context whitespace differences.
The updated full audit checked all 889 templates in 257.52 seconds, reporting
176 candidate files (including failed inputs) and 13 verifier failures. This
discovery snapshot predates subsequent worker edits; it is not a final burndown.
The scanner keeps CRLF line terminators outside line-comment tokens and admits
preserved Unicode literal spellings only when they cannot change boundaries.
Twelve remaining failures are historical CRLF/LF differences inside block
comments or text blocks; the thirteenth is Java 26 flexible-constructor syntax.

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
their 260 pinned contexts. Four retain real whitespace candidates and return to
the ordinary worker queue. WaterTankNetworkFormation and SFMPackFinders also
passed their 40 contexts with zero pairs after comment-only EOL normalization.
Current Clippy passes; broad validation and final installation remain pending.
The line-ending rule is grounded in the
[Java language specification](https://docs.oracle.com/javase/specs/jls/se26/html/jls-3.html#jls-3.10.6);
the constructor boundary follows
[JLS 8.8.7](https://docs.oracle.com/javase/specs/jls/se26/html/jls-8.html#jls-8.8.7).
The tree audit script emits bounded failure summaries; its initial version
printed excessively large failure comparisons and was corrected.

The coordinator discovers candidates, assigns disjoint single-file tasks and
manifests completed batches. Workers use the compact command below, edit only
their assigned template, and repeat until both outcome flags are true. They do
not scan the tree or write projections. Record batch files, elapsed time and
verification counts here. Keep independent files running concurrently up to
the three-worker limit. Unsupported audit inputs must be investigated.

```powershell
sfm-propagate-changes.exe --output-format json source simplify verify --repo-root . --file src/main/java/ca/teamdman/sfm/common/block/CableFacadeBlock.java --summary
```

## [ ] 3 Certify and checkpoint the complete tree

Run a fresh complete inventory audit after all edits. Require zero candidate
files and no unexplained failures. Manifest affected outputs and confirm they
match current templates. Commit reviewable batches locally. Tool installation
is unnecessary if no executable sources or runtime inputs change; otherwise
run the required Rust gate and installer after the last such edit. Record the
final tool identity, audit coverage, validation and manual command here.

No game process has been stopped or started. The testing boundary is complete
rendered Java compared with pinned Git source, not Minecraft gameplay.
