# M1 bounds and frozen input inventory — 2026-10-30

Status: read-only measurement and lock-side inventory, not M1.1 or M1.5 completion. No cache migration, retirement, download, build or link creation occurred in this slice. Current catalog source is `platform/minecraft/projections.json`; the report generator is `platform/cli/sfm-propagate-changes/scripts/report-projection-locked-artifacts.py`.

## Physical budget evidence

- The last four-root NTFS snapshot measured **191,151,836,624 allocated bytes** across old C:, old D:, checkout shared and projection roots, with zero scan errors. It predates subsequent source-index publications and is a dated baseline, not today's combined allocation.
- The projection root then occupied **146,183,177,120 allocated bytes**. Its retained dev 1.19.2 `native-project` cohort occupied **17,632,148,816**, including dependencies **7,244,349,440**, Forge **3,669,140,008**, MCP **2,777,997,016**, project output **2,396,813,512**, snapshots **932,307,360**, run data **593,060,008**, and state **18,219,008** bytes. These categories are disjoint within that cohort, but names alone do not prove safe reclamation.
- Nine exactly listed shallow MCP roots were freshly remeasured at **246,804,480 unique allocated bytes**, 27 files, with no reparse or hardlink aliases. Their shared replacement outputs and historical receipt references are in `sfm-cache-cutover-decision-20261030.md`. This is a conditional first-slice lower bound, not a global substantial-savings target; zero bytes have been retired.
- A new eligible dev 1.19.2 project used **55,700,192 allocated bytes**, versus **99,244,768** for the prior copy-based isolated project: **43,544,576 future bytes avoided** for that key. An unchanged repeat added zero allocated bytes in the measured project roots. These are prospective non-recurrence results, not old-data reclamation.
- Two controlled changed-selector headless sessions each retained **958,832 allocated bytes** of compact receipts/logs while retiring their private inputs and preserving a separately saved world/options byte-for-byte. A proposed **dev 1.19.2 headless-session budget of at most 1,048,576 retained bytes per unchanged run plus zero new shared immutable bytes** is rounded from those observations. It is not yet the global G1 budget: three unchanged runs and other applicable version/session categories must establish whether this envelope holds. Preserve failure diagnostics and selected evidence even when they exceed a success-run budget.
- The earlier fresh Cargo test profile consumed about **14.1 GB** of D: headroom. That observation is not an estimate for every later job. Each write-heavy operation still needs its own expected peak and current free-space preflight; stop if it cannot fit. The latest read-only SFM checks left D: near **7.35 GB free**. No cold profile or full-root rescan was started for this report.

**G1 decision still needed:** classify enough of the measured 133,395,183,096-byte projection-local `build/sfm-toolchain` cohort by actual owner, recoverability and current consumer to set a substantial total-reduction target that is credible against the user's storage goal. Count old plus new physical roots and a shared target once, and set the final numerical target before claiming M6 success. Do not promote 246.8 MB into the whole-plan target merely because that subset has the cleanest owner packet.

## Frozen lock-side inventory

The current G6 reporter classifies **74 catalog keys and 1,792 key/profile/operation rows**: 578 required, one blocked, 239 external, 974 N/A. All 74 catalog locks are now materialized; this differs from the older 56-lock receipt and is not a count of passed operations. The new inventory joins those keys and their required/blocked rows to actual lock SHA-256 values and preserves raw pinned artifact, declared dependency, and source-provider records. Its evidence file is `action-services-evidence/sfm-m15-locked-artifacts-current-20261030.json`, SHA-256 `782408EFE08325595A900110A2F9AA4045200DA064E5C73C267970AC20F439D1`.

| Lock-side population | Current result |
| --- | ---: |
| Catalog keys / required-or-blocked rows | 74 / 579 |
| Schema-2 historical / schema-4 locks | 20 / 54 |
| Artifact records / distinct recorded hashes | 7,015 / 414 |
| Artifact records without a hash | 0 |
| URL-less artifact records | 54, all `source-build` |
| Artifact records owned by declared `kind=tool` dependencies | 69 |
| Source provider declarations: decompile / Git / Maven sources / platform pipeline | 15 / 44 / 30 / 108 |

Spot checks: dev 1.19.2 has 121 artifact records and eight providers; dev 1.20.4 has 105/four; dev 1.21.0 has 110/four; dev 26.1.2 has 89/two and three source-built artifact records; plain 4.34.0/1.19.2 retains historical schema 2 with 108 artifact records and no schema-4 provider declaration. The inventory reports 69 **declared tool-owned** records; it does not misclassify every `purposes=[build]` artifact as a Java tool. Transitive tool JARs can still appear outside logical tool ownership.

`python -B platform/cli/sfm-propagate-changes/scripts/test-report-projection-locked-artifacts.py` passed 2/2 drift and ambiguous-provider regressions. The report is reproducible by running `python -B platform/cli/sfm-propagate-changes/scripts/report-projection-locked-artifacts.py <checkout-root>` from this checkout. It performs no acquisition or preparation.

**M1.5 remains open:** lock metadata cannot establish actual ordered compile, annotation-processor, codegen, decompiler or runtime classpaths. Join the 579 required/blocked rows to planner invocation read-sets, tool/JDK identities, mapping namespaces and generated/upstream source-JAR choice; compare one early Forge, one later Forge and one NeoForm invocation to the lock report before claiming all-row consumer coverage. Keep historical schema-2 recipe semantics and source-built output provenance separate. G10 native substitutions remain deferred unless that join finds a correctness blocker; do not replace a pinned Java tool as part of this inventory.