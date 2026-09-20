# Lazy review core acceptance audit — 2026-09-08

Status: core checkpoint verified; not a whole-goal completion claim. Branch 1.19.2 at
16328629fa60c45a4f525b6f20aaa77715f91077 with preserved local changes.

| Requirement | Current evidence | Remaining gate |
| --- | --- | --- |
| One small target-only file | New CLI ledger 1557 bytes, no content evidence; GUI2 opened both diffs without size growth | Final handoff |
| Exact displayed evidence on human comment | Real companion Java store test edits source after display, saves old displayed body, reopens with both historical and current source; full suite passed | Current GUI4 persisted-comment check |
| Deduplication and bounded selector closure | Evidence-table tests reuse equal bytes across paths and reject missing/changed evidence and invalid UTF-8; Rust/Java ledger closure validation passed | None at unit/integration scope |
| Atomic/conflicting saves | Store tests inject failure before replacement, reject external edits, retain prior authority and retry successfully | None at store scope |
| Conservative approval | Rust resolver test approves source x=2, changes to x=3, observes zero effective approvals; historical content survives deletion/reopen | UI remaining-work feedback is stretch 2 |
| Git references and legacy isolation | Immutable commit/blob membership regression; missing objects explicit; legacy schema rejects historical unbound lanes; existing legacy suite passed | Preserve repository availability caveat in guide |
| Source self-exclusion | Creation adds ledger path to exclusions; resolver test with whole-root scope sees only source unit | None at tested scope |
| Precise structured backgrounds | Rust unchanged annotation/Unicode/common-island tests; Java fragmented split-row tests; real GUI2 inline/split @Override neutral with changed call fragments colored | Current GUI4 |
| Syntax and exact selection mappings | Full surface validation and split Unicode tests; GUI2 source syntax projected through refined mappings | Current GUI4 |
| Quiet freshness age | Boundary tests and visible GUI2 10-second buckets | None |
| Current tools and generated resources | Rust707 passed/3 ignored plus10/12/40 integrations; Java1970 passed/3 opt-in aborted; datagen passed; installed CLI A1145E14FF8668A1652991B91A3FF842794E478625DD03B18B469B29212A0FEF | Recheck if any runtime inputs change |
| Original reviews and pinned dependencies | Both original SHA values unchanged; dependency declaration diff empty | Final recheck |
| User guide and process state | Live review guide records create/open/comment/refresh and portability limits; GUI2 closed normally | Current GUI4 finish and final guide |

GUI2 console ERROR classification: two third-party Mixin minVersion omissions,
AE2 guide startup page configuration, Industrial Foregoing missing texture.
No SFM review failure appeared in that scan. Do not describe the entire modpack
log as error-free merely because puppet completion passed.

Current GUI4 proof is session0b721c02-1e83-43c2-92fc-dfae51f9bd01: saved comment
and exact match/value visible in steps4–6; refined split syntax/neutral annotation
and changed backgrounds visible in18/19. Launcher84919 exited0 normally. Gallery:
Repository-relative artifact path:
`platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview/runs/sfm-title_screen-20260908-040237-191/index.html`.
The current CLI query after reopening reports approved_effective1/remaining0;
the ledger SHA remains7B2E2C73381FE6BE3962606C5B59A4654465BC36EB170CD831B7C36BB7519E0C.
The guide now contains creation, launch, refresh, diff navigation and limitations.
This closes the GUI4/final-handoff gates listed above for the core checkpoint.
All core-owned processes ended normally. No source/runtime edit followed the
conclusive A1145E14... installation. Future stretch edits require fresh verification.

Stretch1 evidence/storage inspector is now claimed. UI at half-width with two long split columns is
cramped; Fit Width preserves both columns but can make text tiny. This is a known
presentation limitation, not evidence of comfortable reading at every width.
