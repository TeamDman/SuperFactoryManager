# Read-only simplification candidates from manifested Java

Plan status: Complete. Primary implementation: `main`. Updated: 7 October 2026.
Starting checkpoint: `3f9f8ab01`. Intent audit: passed against the user's
SFM.java whitespace example, manifested-projection correction and goal approval.

## Update rule

Use `[ ]`, `[~]`, `[x]`, `[!]` for pending, active, complete and blocked work.
Keep evidence beside its work item. Only one implementation focus is active.

## Guidance and traceability

| ID | User guidance | Coverage |
| --- | --- | --- |
| U1 | Simplify redundant whitespace-driven Liquid branches with tools rather than manual file-by-file work. | Candidate scanner, SFM.java acceptance; rewriting is later. |
| U2 | Analyze complete manifested Java, not isolated Liquid branches or a reimplemented Liquid parser. | Engine inputs and CLI provenance boundary. |
| U3 | Preserve release integrity while distinguishing whitespace from code changes. | Conservative token/structure comparison; oracle policy unchanged. |
| U4 | Region differences matter even when imports or other code differ. | Token-aligned candidate spans separate from whole-file result. |
| U5 | IDEA codestyles inform eventual formatting; normalization and AST diffs are useful. | Retain as future formatter input, not a formatter implementation in this slice. |
| U6 | Start with source simplify and install usable tooling. | CLI, adversarial tests, full Rust gate, install and real smoke. |

Intent audit: extraction captured U1–U6 from the available original messages;
traceability maps each to the bounded work below; adversarial review preserves
the distinction between candidate discovery and certified rewriting. In
particular, selected projections are not all possible feature combinations.
No source limitation applies to these recent messages.

## Design and boundaries

Verified foundation: Arborium Java/tree-sitter and gix histogram diff are already
pinned. The catalog and generated provenance already identify context and output
hashes. The existing structural review engine uses rename/comment heuristics;
do not reuse those heuristics as equivalence evidence.

`source simplify scan --repo-root . --file src/main/java/ca/teamdman/sfm/SFM.java`
reads that complete file from each selected manifested projection. Repeated
`--projection` limits the selection; default is all catalog entries. This first
slice deliberately requires a file, not a whole-tree background job.

Parse each distinct source once. Treat comments and literals as opaque exact
tokens; allow only ordinary Java whitespace in gaps. Reject Unicode escapes,
parse errors, missing syntax and unexplained gaps conservatively. No identifier
renaming, comment removal or literal normalization. Compare token-aligned equal
runs for differing whitespace gaps; local candidates are not standalone semantic
equivalence claims. Whole-file whitespace equivalence additionally requires
identical token sequence and syntax-tree shape.

Reports include catalog/provenance/source identities, projection contexts,
presence/ownership diagnostics, whole-file classifications, local byte/line
ranges and before/after snippets. Missing, edited, unsafe or unsupported inputs
must not become successful equivalence claims. No output is written except CLI
stdout and explicitly requested CLI logs. Existing manifests, core templates,
feature selections, oracle rules and dependencies remain unchanged.

Limits and cancellation bound file/context/token/report work. Deterministic
ordering and explicit truncation prevent misleading incomplete reports. Target
coverage is the selected catalog contexts; parser limitations are reported per
file, not hidden or fixed by changing Java sources.

## Work

### [x] 1 Implement the conservative comparison engine

Add pure complete-file parsing and token-aligned whitespace candidates, using
existing pinned parser/diff crates. Test literals, text blocks, comments, Unicode
escapes, operator boundaries, changed syntax, malformed files and local matches
in otherwise different files. Exact bytes alone do not bypass unsupported syntax.

Evidence: the focused `cargo test --offline --locked --all-features --lib simplify`
run passed all 9 engine and CLI tests in 0.06 seconds after compilation. The engine
uses existing Arborium Java and gix dependencies. No Minecraft inputs changed.

### [x] 2 Add the read-only catalog CLI

Validate paths, catalog ownership/context and actual generated hashes. Report
missing projections, absent members, contributor edits and unsupported syntax.
Test CLI parsing, fixture scans and no-write behavior. A real SFM.java scan must
show whitespace candidates near the event-bus constructor without claiming that
all versions of the whole file are equivalent.

CLI and fixture checks are implemented and pass the focused run. Strict linting
prompted extraction of catalog selection and provenance loading from the scan
function. An early real scan with the gate's all-features debug CLI verified all
20 SFM.java inputs, parsing 14 distinct sources for 190 pairs: 6 exact, 3
whitespace-only and 181 code/comment differences. There are local whitespace
candidates in 54 pairs. No inputs were unverified.

The released 1.21.1 versus 26.1.2 pair reports exactly one whitespace gap, at
constructor line 34: `{\n\n        SFMEventBus` versus
`{\n        SFMEventBus`. Whole-file tokens and syntax-tree shape match. This
is the user's concrete example. Task 3 records the final installed-tool proof;
the all-features debug product is not used for operational timing.

### [x] 3 Validate, install and document

Run focused tests before `check-all.ps1 -TestWorkers 2`, with process-local
`CARGO_PROFILE_TEST_OPT_LEVEL=1`. Install with `install.ps1`, verify PATH/hash/help
and the real scan, and record measured scan duration. Update contributor guidance.
No Gradle/game/JAR builds are needed: no Minecraft inputs change. Checkpoint
locally; do not push. No extra continuation item is claimed.

Contributor guidance now documents the command, classifications and evidence
limits. With 2 workers and test optimization level 1, the full gate passed all
213 test shards: 2,507 tests passed, none failed and 18 remained ignored. This
includes 2,437 library tests and 70 integration tests. Binary and doc checks also
passed. The final default-feature build passed and `check-all.ps1` exited 0.
Implementation checkpoint: `cdbbd89c8df96091d2b77f3a9d1b9bec8770e671`.
`install.ps1` completed its offline locked release build and replaced the Cargo
bin executables. The PATH-resolved `sfm-propagate-changes.exe` reports revision
`cdbbd89c8`, built 7 October 2026 at 20:59:06 -04:00. Installed SHA-256:
`4e38a566ef4e1f57e0cfd471fdc55f40404daa997d4c2b69adfec80db63bf24e`.
The installed location is the existing `CARGO_HOME/bin` directory; user install
required: no. The installer also refreshed the package's `source-jar-absence`
executable, as defined by the existing installer.

Installed `source simplify scan --help` exposes the documented file, projection
and region options. The documented all-context SFM.java command completed in
633 ms in one measured run, including process launch and JSON capture. It
verified all 20 inputs and 190 pairs, reproducing the earlier candidate counts
and exact constructor blank-line evidence. This is an observed duration, not a
timing assertion or general performance guarantee. Git status stayed clean after
the scan. The final documentation receipt does not change executable inputs.

Completion audit: complete-file parsing, exact literal/comment preservation,
local candidates versus whole-file classifications, rejected unsafe/unsupported
inputs, provenance identities, bounded reports and repeated projection selection
are covered by the focused and full tests. Real installed output proves the
SFM.java acceptance case. Commit inspection confirms no Minecraft input,
projection, oracle policy, dependency declaration or lock changed. Documentation
states the core-freshness and feature-combination limits. No automatic rewrite,
formatter, publication or push was performed.

All launched build, test and scan processes exited. No game, server or external
helper was started or stopped. No disk-space error occurred. For manual use, run
the command above from the repository root; existing manifested projects are the
only required inputs. The next possible feature, applying reviewed simplification
edits, needs a separate scope and is not part of this completed goal.

## Operational readiness

Dependencies frozen; no declarations/locks or external repositories may change.
Use the repository goal-execution/readiness policy for bounded build processes.
Stop on any disk-space error and wait for the user. Initial tree is clean.
Build/profile/cache and final installed identity will be recorded with task 3.
No formatter, automatic application, Liquid parser work, oracle-policy change,
all-feature-combination proof or publication is part of this goal.
