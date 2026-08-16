# CLI AST refactoring suite plan

**Plan status:** Active; Phase 0 through Phase 0.11 are complete; Phase 0.12 now owns location-definition coverage and location-usage/reference support before deferred Phase 1
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-08-16
**Intent audit:** Passed 2026-08-09 for Phase 0; extended 2026-08-09 for Phase 0.8, 2026-08-10 for Phase 0.9, 2026-08-11 for the in-game definition-at-location bridge, reconciled 2026-08-15 at Phase 0.10 completion, extended 2026-08-15 for Rust-owned Arborium Java highlighting, extended 2026-08-16 for JDK/local/member/import definition coverage plus location-aware usages, and post-compaction re-audited 2026-08-16 against the user's verbatim report

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Update a work item's heading and completion notes together. Record command
output, affected files, and propagation evidence beneath the item that it
proves. This plan describes an implementation; creating the plan does not
authorize source rewrites.

## Authoritative user guidance ledger for the next phase

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| JAVA-01 | The immediate purpose is to improve review of SFM release changes; do not expand this slice into a generic Java project/package manager, `sfm java init`, Gradle generator, or Gradle-edit library. | Phase 0 builds reusable internal Java-analysis primitives but keeps branch/worktree discovery and the public workflow SFM-specific. | — |
| JAVA-02 | Prove analysis/refactoring behavior with tiny Java scenarios independent of Minecraft compilation. | Add dedicated scenario integration tests under the CLI crate; isolated scenarios use ordinary `A.java`/`B.java` sources and never launch Gradle or Minecraft. | — |
| JAVA-03 | Keep each descriptive Rust scenario test next to its scenarios; avoid generic `test.rs` names that harm fuzzy finding. | Use `tests/java_analysis/java_analysis_scenario_test.rs` beside `tests/java_analysis/scenarios/`, registered as the explicit Cargo target `java_analysis_scenarios`. | — |
| JAVA-04 | A scenario is described by a manually runnable `command.ps1` that exercises the real public CLI contract. | The harness reads one restricted command from `command.ps1`, tokenizes it without launching PowerShell, parses the argv through Figue, and invokes the same typed command path as production. | — |
| JAVA-05 | Read-only analysis comes before mutation. | Phase 0 implemented definition/usage navigation; Phase 0.8 improves discovery and external dependency coverage before `symbol rename` and `symbol move` become mutating. | — |
| JAVA-06 | Keep navigation and symbol refactoring under `symbol`, but make each child command plainly verb-like. | Phase 0.8 replaces the experimental noun-shaped navigation commands with `symbol show-definition`, `symbol list-usages`, and `symbol list`; `symbol rename` and `symbol move` remain authoritative. | JAVA-17 |
| JAVA-07 | Borrow symbol-selector grammar from Forge Access Transformers. | A typed selector accepts a class name alone, class plus field name, or class plus method name/JVM descriptor; parentheses distinguish methods and `$` identifies nested classes. | — |
| JAVA-08 | `--branch` remains mandatory, including isolated scenarios and custom source roots. | Every symbol command resolves branch context first; a custom root changes source discovery, not the Java/toolchain version context. | — |
| JAVA-09 | Normal branch analysis indexes all Java source sets, not only `main`. | Discover every branch-declared source set/root, retain its identity and exclusions, and enforce its visibility graph instead of flattening all sources together. | — |
| JAVA-10 | Custom roots must state whether classpath behavior is `branch` or `isolated`; do not add a broad explicit-classpath system or hidden test-case resolver mode yet. | `--source-root` requires `--classpath-mode branch|isolated`; isolated mode excludes project/Minecraft/mod/ambient classpaths while retaining branch-selected Java/JDK settings. Scenarios use the same public flags as manual commands. | — |
| JAVA-11 | Adopt the `teamy-rust-cli` `CliOutput`/`OutputFormat` pattern so command output is typed and rendered once. | Add global `--output-format text|json|csv`; new symbol commands return Facet output structs and stdout contains only the selected report while logs remain on stderr. Legacy commands may temporarily return `CliOutput::none()`. | — |
| JAVA-12 | Structured output needs a stable, evolvable contract. | Each symbol report includes its own schema discriminator such as `sfm.symbol-usage-list/1`, branch/source-set context, selector, fingerprints, matches, and diagnostics. | — |
| JAVA-13 | Generated actual artifacts belong beside expected artifacts, not under `target/`. | Scenarios use `output-expected.json` and ignored `output-actual.json`; later mutation scenarios use tracked `before/` and `after-expected/` plus ignored `after-actual/`. | — |
| JAVA-14 | Snapshot acceptance must be explicit and safe. | Never rewrite expected output automatically. Refuse tracked/non-ignored actual destinations, write actual atomically, compare canonical bytes, and print an exact `Copy-Item` acceptance command on missing/different expectations. | — |
| JAVA-15 | Mutation must never rely on an implicit preview default. | Future `symbol rename`/`symbol move` require exactly one of `--dry-run` or `--apply`; `--apply --output-root` copies then edits, `--apply` without it edits in place, and `--dry-run --output-root` is invalid. | — |
| JAVA-16 | Mutation scenario equality is about source contents, not filesystem metadata. | Later mutation tests compare normalized relative paths and exact file bytes; timestamps, ACLs, directory enumeration order, and build outputs are excluded. | — |
| JAVA-17 | The public symbol surface should be immediately understandable in `help list --short`. | Canonical commands are `show-definition`, `list-usages`, `list`, `rename`, `move`, and `index`; `list-usage` is a Figue subcommand alias for `list-usages`, while the Phase 0 `definition` and `usage list` spellings are retired before release. | — |
| JAVA-18 | Users need to discover selectors rather than already knowing an exact fully qualified name and descriptor. | Add `symbol list [pattern]`; no pattern means all symbols, and a case-sensitive canonical-selector glob supports only `*` for zero-or-more characters and `?` for one character. Exact navigation/mutation commands continue using `JavaSymbolSelector`, never the glob parser. | — |
| JAVA-19 | Dependency symbols such as `net.minecraft.client.gui.components.MultiLineEditBox` must be discoverable and navigable. | Build a dependency-source symbol index from the existing lockfile-declared source providers, including the Minecraft platform-pipeline source tree, and compose it with live SFM sources for list/definition/usage queries. | — |
| JAVA-20 | Dependencies change rarely, while SFM sources change continuously during development. | Persist only dependency indexes; index branch/custom SFM sources live at query time. A query report records both the live workspace fingerprint and immutable dependency-index identity. | — |
| JAVA-21 | A dependency index must never be silently reused after its toolchain inputs change. | Key and validate the cache using a semantic effective-lockfile/source-provider projection plus parser/index-format fingerprints. Missing/stale indexes produce an explicit incomplete outcome and a typed Figue `ToArgs` recommendation for `symbol index refresh`, not a false no-match. | — |
| JAVA-22 | Source acquisition already exists and must not be reimplemented. | `symbol index refresh` reuses `DependencyInventory`, provider priority/status, `SourceProviderView::searchable_roots`, and existing Maven/Git/decompile/platform-pipeline acquisition machinery. It acquires the preferred declared source provider where needed and reports unavailable components. | — |
| JAVA-23 | Index lifecycle and location must be inspectable and automatable. | Add `symbol index refresh --branch <branch>` and `symbol index show --branch <branch>`; `show` emits typed ready/missing/stale/partial status and prints the concrete cache/manifest path in text output. Refresh uses a cache lock and atomic publication so cancellation/failure cannot expose a partial index. | — |
| JAVA-24 | Incomplete dependency coverage must remain visible in every query. | Workspace-only matches may still be returned, but reports distinguish complete success from incomplete results, enumerate missing/stale dependency inputs, and include exact typed refresh/acquisition recommendations. | — |
| JAVA-25 | A live `symbol show-definition DiskItem --branch 1.19.2` query taking about 10–11 seconds is too slow; measure the current work and parallelize it safely. | Phase 0.9 retains a reproducible baseline, adds per-stage/worker evidence, and delivers a materially faster real command rather than claiming improvement from synthetic microbenchmarks alone. | — |
| JAVA-26 | Do not optimize the current pipeline by preserving an `all Types -> barrier -> all Members -> barrier -> all Usages` shape. Work that finishes parsing one file/shard must be able to proceed to linking while other files are still being parsed. | Replace the live definition path with one parse-to-facts operation per source and a streaming linker; only a final snapshot seal may wait for every source, and only for completeness-dependent conclusions such as uniqueness, ambiguity, and unresolved diagnostics. | — |
| JAVA-27 | Avoid per-item two-phase work where every item must finish phase one before any item may begin phase two or later. | A worker emits package/import declarations, type declarations, raw member signatures, raw references, diagnostics, and source identity together as one `JavaFileFacts` result. The linker admits each result immediately, retains forward references as pending work, and wakes affected candidates as declarations arrive without reparsing the source. | — |
| JAVA-28 | A `JoinSet`-style fan-out machine is welcome, but parallelism must remain bounded and robust. | The coordinator has an explicit worker limit, aggregate and per-worker memory ceilings, cancellation/failure cleanup that kills and waits for every child, deterministic shard bisection, and deterministic result ordering independent of completion order. A concurrency primitive is an implementation detail, not permission for unbounded tasks or orphan processes. | — |
| JAVA-29 | Cached/reusable analysis objects and source facts should not be discarded between phases of one query. | The Phase 0.9 live path parses each selected source no more than once, owns each fact bundle until linking/sealing is complete, and does not serialize/re-read type/member TSV phase files. This does not introduce a persistent live-source cache, watcher, or daemon between separate CLI invocations. | — |
| JAVA-30 | Optimize from evidence without silently changing symbol-query correctness. | Retain the Phase 0.8 implementation as a test-only/diagnostic equivalence oracle during migration; canonical typed output, ambiguity, source-set visibility, dependency resolution, diagnostics, hashes, and deterministic ordering must agree before the production path switches. | — |
| JAVA-31 | Parallelizable implementation work should be identified so agents or multiple threads can work concurrently without conflicting edits. | Phase 0.9 names disjoint fact-extraction, worker-supervision, linker/equivalence, and integration tracks, with one owner for shared module wiring and production-path selection. | — |
| JAVA-32 | The in-game SFM editor must be able to jump from the symbol at its current cursor to the exact definition. | Phase 0.10 adds a definition-at-document-location request; callers supply document/source-set/workspace identity, exact current source snapshot/hash, and UTF-aware cursor location rather than only a guessed simple-name selector. | — |
| JAVA-33 | The Minecraft UI must reuse this Java-analysis/index engine rather than implement another resolver or synchronously parse Java itself. | Phase 0.10 extracts a typed reusable engine entry point consumed by both direct CLI and worker modes; the linked explorer plan owns only provider/process/UI adaptation. | — |
| JAVA-34 | Interactive use must not pay fresh process and full immutable setup cost for every F12 press. | Phase 0.10 adds a supervised long-lived `symbol serve` mode with framed versioned Facet requests/responses, bounded reusable state, cancellation, stale-request identity, and clean child/process shutdown. | — |
| JAVA-35 | Unsaved/current editor text must participate without being written to the source tree. | The location request may overlay the addressed document snapshot for that request; results record overlay/disk hashes and never mutate workspace files. | — |
| JAVA-36 | Zero, one, and multiple definition candidates plus incomplete dependency-index state must remain distinguishable. | The typed result returns canonical symbol ids, concrete source spans/root-relative paths, confidence/completeness, diagnostics, fingerprints, and typed recovery recommendations; no first-match fallback or false authoritative no-match is allowed. | — |
| JAVA-37 | The worker transport must remain replaceable by a later Vox adapter and safe to consume from Minecraft. | Protocol/process details stay behind the provider-neutral contract in the contextual explorer plan; stdout is framed protocol only, stderr is diagnostics, schemas handshake explicitly, paths/content stay out of default telemetry, and direct/worker results are equivalent. | — |

## Authoritative user guidance ledger — 2026-08-15 Arborium highlighting extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| JAVA-38 | Minecraft Java should send the exact document text to Rust and receive syntax-highlighting spans to apply, rather than parse Java in the UI. | Phase 0.11 adds typed direct/worker requests and results; the contextual explorer plan owns the provider adapter and renderer. | — |
| JAVA-39 | Use Arborium so new languages do not require one new Java-side ANTLR implementation each. | Phase 0.11 uses the pinned Arborium Java grammar/highlights query and Arborium flat-span normalization in Rust; it adds no Java grammar/parser. | — |
| JAVA-40 | Returned presentation should be ChatFormatting spans. | The result schema carries sorted non-overlapping UTF-8 ranges, stable Arborium tags, and canonical lower-case ChatFormatting names that Java validates/applies. | — |
| JAVA-41 | Java is required first; inspect SFM extensions to prioritize later languages. | Phase 0.11 enables Java only and records the tracked-file/local-Arborium audit: Rust, JSON, Gradle/Groovy, PowerShell, Markdown, TypeScript, TOML after Java. | — |
| JAVA-42 | Reusable font/parser/highlighter objects should not be discarded between requests or draws. | Compile the Java query once per worker, reuse bounded parser/query state, cache immutable results by language/source hash, expose hit/miss telemetry, and keep rendering independent from parsing. | — |
| JAVA-43 | Highlighting must be responsive and stale-safe. | Requests carry id/origin generation/source hash, are cancellable and bounded, run in a supervised long-lived process, and never apply a late result to changed text. | — |
| JAVA-44 | Arborium integration must not destabilize the existing pinned parser. | Add `arborium-highlight = 2.18.1` without its `tree-sitter` feature and keep `tree-sitter-patched-arborium = 0.25.10` as the sole native tree-sitter link provider. | — |

## Authoritative user guidance ledger — 2026-08-16 location intelligence extension

| ID | Active guidance | Required plan consequence | Superseded by |
| --- | --- | --- | --- |
| JAVA-45 | Definition-at-position must resolve `String`, `Object`, and `StringBuilder`, including Java's implicit `java.lang` import behavior. | Phase 0.12.1 composes branch-selected JDK sources with workspace/dependency symbols, keys them by JDK/source identity, and adds exact platform-type location scenarios. | — |
| JAVA-46 | Definition-at-position must handle variables, fields, methods, annotations, and import statements when statically resolvable. | Phase 0.12.1 adds lexical local/parameter declarations and reference-range classification while reusing existing type/member/import facts and overload/source-set semantics. | — |
| JAVA-47 | Known resolver-authorized files must not falsely fail with `no worker source root contains the document within its resolver authorization`. | Phase 0.12.3 plus contextual C-7 validate negotiated root/source-set/report-path identity and preserve a fixture for the exact Java-side failure; the Rust engine must expose sufficient canonical mapping evidence rather than invite path guessing. | — |
| JAVA-48 | An incomplete dependency/source index must remain visible, but it must not turn a resolvable symbol into `NoSymbol` merely because some unrelated components are missing. | Phase 0.12.1/0.12.3 separate target-domain completeness from global partial coverage, return matches plus scoped diagnostics, and preserve the exact false-negative text as a regression. | — |
| JAVA-49 | `Find References`/`Find Usages` should work from the editor position just as definition lookup does; the UI should not have to derive an exact selector first. | Phase 0.12.2 adds a mutually exclusive location form to `symbol list-usages`, a typed usage-at-position engine request/result, and a supervised worker capability sharing the definition context. | — |
| JAVA-50 | Reference results must be rich enough for a persistent explorer and repeated source navigation. | Phase 0.12.2 returns the resolved target plus deterministic categorized usages, concrete source spans/addresses, source-set/origin/hash identity, completeness, diagnostics, and recovery recommendations. | — |
| JAVA-51 | Interactive references must reuse the warm worker, remain cancellable/stale-safe, and not spawn one CLI process per invocation. | Phase 0.12.3 extends `symbol serve` negotiation/framing/cache/generation/cancellation with usage-at-position and proves direct/worker canonical parity and bounded lifecycle. | — |
| JAVA-52 | The new work is navigation/review infrastructure, not authorization to begin source mutation or refactoring Phase 1. | Phase 0.12 is read-only, changes no Java source, invokes no Gradle flow, and stops before rename/move/refactor implementation, propagation, publication, or release. | — |

## Guidance traceability

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| JAVA-01, JAVA-02 | Phase 0 scope/non-goals; 0.3; 0.7 | Scenario sources run without Minecraft, Gradle, or generic project initialization. |
| JAVA-03, JAVA-04 | 0.3 | Explicit Cargo target invokes restricted `command.ps1` through Figue and the production command path. |
| JAVA-05, JAVA-06, JAVA-17 | Source-aware CLI surface; 0.4; 0.8.1 | CLI/help tests prove the verb-first canonical commands and `list-usage` alias; mutation commands remain non-mutating/deferred. |
| JAVA-07 | Selector contract; 0.4 | Parser fixtures cover class, field, method descriptor, nested class, malformed, zero-match, and ambiguous selectors. |
| JAVA-08, JAVA-09, JAVA-10 | Source/classpath contract; 0.5 | Reports enumerate selected source sets/visibility and prove branch versus isolated behavior. |
| JAVA-11, JAVA-12 | 0.1; 0.2 | Text/JSON/CSV rendering tests and versioned JSON snapshots pass with logs separated from stdout. |
| JAVA-13, JAVA-14 | 0.3 | Harness safety tests and adjacent expected/actual artifacts pass. |
| JAVA-15, JAVA-16 | Confirmed mutation contract; later Phase 3 scenario work | CLI parser rejects invalid mutation modes; mutation application remains outside Phase 0. |
| JAVA-18 | 0.8.2 | Typed list scenarios prove no-pattern enumeration, canonical-selector glob filtering, stable ordering, and exact-selector separation. |
| JAVA-19, JAVA-20 | 0.8.3; 0.8.5; 0.8.6 | A cached dependency-source index plus live SFM index resolves and displays `MultiLineEditBox` without Gradle or a game launch. |
| JAVA-21, JAVA-23 | 0.8.3; 0.8.4 | Identity/status tests reject stale manifests; `index show` exposes paths; Figue round-trips the recommended refresh command; refresh publishes atomically. |
| JAVA-22 | 0.8.4 | Source acquisition integration tests use the existing provider selection and cache layouts rather than a second downloader. |
| JAVA-24 | 0.8.5 | Missing/stale dependency-index scenarios produce typed incomplete reports and recommendations rather than authoritative no-match results. |
| JAVA-25 | 0.9.1; 0.9.6 | A checked-in benchmark note records the 9.573-second baseline and warm-run distribution; the same real command meets the Phase 0.9 latency threshold after integration. |
| JAVA-26, JAVA-27, JAVA-29 | 0.9.2; 0.9.3; 0.9.5 | Instrumented tests prove one parse per live source, linker admission before producer completion, no live type/member phase files, and seal-only completeness decisions. |
| JAVA-28 | 0.9.4; 0.9.6 | Supervisor tests prove bounded peak workers, aggregate/per-process limits, cancellation and fail-fast child cleanup, deterministic bisection, and stable output under reversed completion order. |
| JAVA-30 | 0.9.1; 0.9.5; 0.9.6 | Scenario and generated-corpus equivalence tests compare the new path with the Phase 0.8 oracle before the production route is selected. |
| JAVA-31 | Phase 0.9 parallel work map | Disjoint ownership is recorded before parallel edits; integration remains with one owner. |
| JAVA-32, JAVA-35 | 0.10.1; 0.10.2 | Scenario fixtures prove context-aware resolution at exact cursor locations, including supplied snapshot overlays, Unicode/CRLF, ambiguity, fields/methods/types, and no file mutation. |
| JAVA-33, JAVA-36 | 0.10.2; 0.10.4 | Direct CLI and reusable engine return identical versioned results with spans, completeness, diagnostics, and recovery actions over workspace plus dependency sources. |
| JAVA-34, JAVA-37 | 0.10.3; 0.10.4 | Worker handshake/framing/cancellation/crash cleanup tests, warm latency evidence, privacy-safe telemetry, and byte-equivalence with direct invocation pass before the in-game provider integrates. |
| JAVA-38, JAVA-39, JAVA-40 | 0.11.1; 0.11.2; contextual C-4c/C-4d | Exact-text request/result snapshots, Arborium Java captures flattened to deterministic ChatFormatting spans, direct/worker parity, and Java rendering evidence |
| JAVA-41 | 0.11.1; developer documentation | Reproducible tracked-extension counts, local Arborium grammar availability, Java-only enabled feature set, and ordered deferred backlog |
| JAVA-42, JAVA-43 | 0.11.2; 0.11.3 | Query/parser compile counters, cache hit/miss, bounded reuse, cancellation/stale/hash tests, latency evidence, and clean process/child shutdown |
| JAVA-44 | 0.11.2; 0.11.3 | Cargo graph/check-all proof shows one native `links = "tree-sitter"` provider and the existing analysis suites remain green |
| JAVA-45, JAVA-46 | 0.12.1 | Exact JDK implicit-import plus local/parameter/field/method/annotation/import location scenarios, canonical selector agreement, source-span identity, and truthful unsupported cases |
| JAVA-47, JAVA-48 | 0.12.1; 0.12.3; contextual C-7 | Negotiated root-mapping fixtures, exact quoted-failure regressions, target-domain completeness tests, partial-index match retention, and Java/Rust integration evidence |
| JAVA-49, JAVA-50 | 0.12.2; contextual C-8 | Location-form CLI/parser scenarios, target-plus-categorized-usage schemas, zero/one/many/partial outcomes, stable spans/addresses, and persistent-explorer consumption fixtures |
| JAVA-51 | 0.12.3 | Worker capability negotiation, direct/worker parity, warm latency, cancellation/generation/cache bounds, crash/restart/EOF cleanup, and zero leaked processes |
| JAVA-52 | Phase 0.12 scope/exclusions | Read-only diff/source-tree equality proof, no Gradle/propagation/publication, and Phase 1 headings remain incomplete |

## Intent audit evidence

- **Pass 1 — extraction:** Reread the available user messages from the release
  review/refactoring discussion through the 2026-08-09 selector, source-set,
  classpath, output, and scenario clarifications. Split the guidance into
  `JAVA-01` through `JAVA-16`, including examples, ordering, safety constraints,
  and explicit non-goals.
- **Pass 2 — traceability:** Mapped every active ID to the public contracts,
  Phase 0 work items, validation commands, or an explicitly deferred mutation
  phase. Replaced conflicting older wording about implicit preview, dotted
  member queries, main-only indexing, and `refactor` aliases.
- **Pass 3 — adversarial omission:** Rechecked the plan against the original
  messages for easy-to-lose details: descriptive test filenames, adjacency of
  scenarios and actual output, no PowerShell process execution, mandatory
  branch context in isolated mode, source-set visibility rather than flattening,
  Access Transformer method descriptors, and explicit `--dry-run|--apply`.
- **Pass 4 — Phase 0.8 extension:** Captured the observed noun/verb CLI
  confusion, both required usage-command spellings, optional glob discovery,
  real Minecraft dependency navigation, existing source acquisition reuse,
  offline dependency versus live SFM indexing, semantic lockfile pinning,
  inspectable cache paths, atomic refresh, stale-index recommendations rendered
  from typed Figue values, and the rule that incomplete coverage cannot become
  a false no-match.
- **Pass 5 — Phase 0.9 extraction:** Reread the 2026-08-10 performance
  discussion and retained both the observed 10–11 second `DiskItem` latency
  and the architectural correction that parallelizing the existing global
  Types/Members barriers would be insufficient. Added `JAVA-25` through
  `JAVA-31` for measurement, one-parse fact bundles, streaming forward-reference
  linking, bounded child supervision, within-query reuse, equivalence, and
  explicitly parallel work ownership.
- **Pass 6 — Phase 0.9 traceability:** Mapped every new guidance ID to a
  numbered work item, focused proof, and closeout criterion. Checked the
  inverse mapping: the initial four-worker bound, 6 GiB aggregate Windows
  worker budget, live-definition-only production switch, and retained oracle
  are reversible implementation decisions grounded in the measured 64 GiB,
  32-logical-CPU development machine and existing 1.5 GiB per-worker ceiling.
- **Pass 7 — Phase 0.9 adversarial omission:** Rechecked that “parallel” did
  not collapse into a faster global phase barrier; that a completed shard can
  enter the linker while producers remain active; that forward references and
  ambiguity wait for a seal without reparsing; and that process handles, Job
  Objects, cancellation, failure, memory, output order, and deterministic
  bisection remain explicit. Also retained the no-persistent-live-cache rule
  and deferred usage/dependency-refresh migration instead of silently widening
  this latency slice.
- **Pass 8 — Phase 0.10 in-game navigation extension:** Preserved the complete
  handoff from a path-addressed in-game editor and exact cursor/source snapshot
  to the existing live/dependency symbol engine and back to one/many/no typed
  source spans. Explicitly retained unsaved overlays, source-set/classpath/index
  identity, render-thread isolation, warm reuse, cancellation/stale rejection,
  framed stdout versus stderr logs, process cleanup, privacy-safe telemetry,
  direct/worker equivalence, and provider/transport replaceability. The linked
  contextual explorer plan owns workspace authority, editor panels, actions,
  palette choice, and live Minecraft proof; this plan owns analysis and worker
  correctness so neither reimplements the other.
- **Known source limitation:** None for the Phase 0 discussion; the original
  user messages were available in this conversation. Earlier broad plan history
  remains represented by the pre-existing sections and linked plans.

## Intent audit evidence — 2026-08-15 Arborium highlighting extension

- **Pass 1 — extraction:** Preserved Java-to-Rust exact-text highlighting,
  Arborium rather than per-language Java ANTLR, ChatFormatting spans, Java
  first, repository-extension prioritization, cached/reused objects, current
  progress reconciliation, and the requirement that this work join C-5.
- **Pass 2 — traceability:** Mapped JAVA-38 through JAVA-44 to Phase 0.11,
  contextual C-4c/C-4d/C-6, exact direct/worker tests, Cargo link validation,
  extension-audit evidence, and no-render-thread Java acceptance. Local sources
  verify Arborium Java 2.18.1 exposes `HIGHLIGHTS_QUERY`, Arborium Highlight
  2.18.1 exposes non-overlapping flat tokens, and this CLI already owns the
  patched tree-sitter dependency and supervised-worker patterns.
- **Pass 3 — adversarial omission:** Checked that Rust receives the actual
  document snapshot rather than re-reading a path, returned spans are
  source-hash/generation bound, ChatFormatting names are explicit rather than
  HTML/ANSI, grammar/query compilation is not repeated per frame/request,
  unsupported service leaves readable text, the dependency cannot add a
  second `links = "tree-sitter"`, and the language audit is not mistaken for
  permission to ship every grammar in this goal.
- **Known source limitation:** None. The complete request, linked plan,
  current CLI/Java sources, local Arborium repository, cached 2.18.1 crate
  sources, and tracked SFM file list were available.

## Intent audit evidence — 2026-08-16 location intelligence extension

- **Pass 1 — extraction:** Preserved the three exact JDK examples, every named
  Java symbol category (variable, field, method, annotation, import), both
  quoted failure outcomes, the distinction between find usages/references and
  definition, the requirement for a persistent UI consumer, and the warm push/
  cancellation expectation. Kept UI gestures and panel placement in the linked
  contextual plan rather than misassigning them to the CLI.
- **Pass 2 — traceability:** Mapped JAVA-45 through JAVA-48 to Phase 0.12.1 and
  0.12.3, JAVA-49/JAVA-50 to 0.12.2, JAVA-51 to 0.12.3, and the read-only scope
  boundary to JAVA-52. Verified that the existing CLI has exact-selector
  `list-usages`, definition-at-position, live workspace facts, cached dependency
  sources, and one supervised symbol worker, but no usage-at-position capability
  and no explicit JDK-source domain. The extension reuses those facts instead
  of adding another parser/index or making Minecraft synthesize selectors.
- **Pass 3 — adversarial omission:** Checked that implicit `java.lang` does not
  become a text-name guess; locals/parameters retain lexical scope and shadowing;
  method calls retain overload/dynamic-dispatch honesty; import clicks target
  imported declarations rather than the import token itself; partial unrelated
  dependencies do not suppress a valid target; JDK/dependency/workspace origins
  remain distinguishable; usage categories and source hashes survive transport;
  worker stdout stays framed; cancellation reaps child work; and Phase 0.12 does
  not begin rename/move/refactoring or Java-source writes.
- **Fresh-agent resumption check:** A new agent can freeze the JDK/location
  identity and result schemas, complete 0.12.1, then integrate 0.12.2/0.12.3
  through the existing engine/worker while leaving Phase 1 untouched. The
  public location forms, validation layers, parallel ownership, and handoff to
  contextual C-7/C-8 are explicit enough to resume without conversation
  history.
- **Known source limitation:** None. The complete user report and current
  definition/index/worker sources were available. Exact JDK source acquisition
  storage and result-schema version numbers remain implementation details to be
  frozen in 0.12.1/0.12.2 fixtures, not untracked assumptions.

## Purpose

Build a safe, CLI-first Java refactoring suite on top of the source discovery,
audit, classpath, and version-propagation machinery already in
`sfm-propagate-changes`. The first motivating operation is:

```powershell
sfm-propagate-changes.exe symbol rename ca.teamdman.sfm.common.util.SFMBlockPosUtils between betweenRange --branch 1.19.2 --dry-run
```

The suite should provide a useful subset of IntelliJ-style refactorings while
remaining deterministic, reviewable, version-aware, and safe to run across the
Minecraft branches. It must never silently perform a textual global replace.

## Source-aware CLI surface

The shared symbol index should power both navigation and mutation. Use one
`symbol` object namespace with plainly verb-like children:

```text
sfm-propagate-changes.exe symbol show-definition <at-selector> --branch <branch>
sfm-propagate-changes.exe symbol list-usages <at-selector> --branch <branch>
sfm-propagate-changes.exe symbol list-usage <at-selector> --branch <branch>  # alias
sfm-propagate-changes.exe symbol list [canonical-selector-glob] --branch <branch>
sfm-propagate-changes.exe symbol index refresh --branch <branch>
sfm-propagate-changes.exe symbol index show --branch <branch>
sfm-propagate-changes.exe symbol rename <at-selector> <new-name> --branch <branch> (--dry-run|--apply)
sfm-propagate-changes.exe symbol move <at-selector> <qualified-destination> --branch <branch> (--dry-run|--apply)
```

Phase 0.12 retains the exact-selector positional forms and gives both
definition and usage commands one mutually exclusive document-location form:

```text
sfm-propagate-changes.exe symbol show-definition --source-path <root-relative-java-path> --line <n> --column <n> --branch <branch> [--source-root-id <id>]
sfm-propagate-changes.exe symbol list-usages --source-path <root-relative-java-path> --line <n> --column <n> --branch <branch> [--source-root-id <id>]
```

The `list-usage` alias accepts the identical location form. Typed worker calls
may additionally carry the exact in-memory document overlay/hash; the public
manual CLI reads the declared source unless a separately documented stdin
overlay is later approved. Figue rejects a mixed positional selector plus
location request before analysis.

`show-definition`, `list-usages`/`list-usage`, and `list` are read-only and
must work without a Gradle build or game launch. They return stable
source-origin-relative paths, line/column and byte span, qualified identity,
kind, descriptor, source set, and resolution confidence.
Zero matches and ambiguous matches have distinct nonzero statuses. Output has
human-readable and Facet/Figue-rendered forms and includes source hash, branch,
source-set identity, parser version, and classpath/index fingerprints. A global
`--output-format text|json|csv` is rendered exactly once after command
invocation; report bytes go to stdout and diagnostics/logging go to stderr.

### Access-Transformer-derived selector contract

The external selector grammar deliberately follows Forge Access Transformer
targets rather than inventing dotted member syntax:

```text
<fully.qualified.Class>
<fully.qualified.Class> <fieldName>
<fully.qualified.Class> <methodName><JVM-method-descriptor>
```

Parentheses after the member name distinguish a method from a field, JVM
descriptors disambiguate overloads, and `$` names nested classes. Examples:

```powershell
sfm-propagate-changes.exe symbol show-definition ca.teamdman.sfm.SomeType --branch 1.19.2
sfm-propagate-changes.exe symbol list-usages ca.teamdman.sfm.SomeType MY_FIELD --branch 1.19.2
sfm-propagate-changes.exe --output-format json symbol list-usage ca.teamdman.sfm.SomeType 'between(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/util/stream/Stream;' --branch 1.19.2
sfm-propagate-changes.exe symbol list 'ca.teamdman.sfm.*clientRun*' --branch 1.19.2
```

The CLI owns one typed `JavaSymbolSelector`; definition, usage, rename, move,
scenario parsing, reports, and later review selectors must not each reinterpret
the raw tokens. `symbol list` alone accepts a separate typed canonical-selector
glob; it never weakens exact selector parsing. Rename changes only the selected identifier (and a top-level
type's filename where required). Move accepts a qualified destination and may
change owner/package/name. Source-selector arity is parsed before the trailing
rename/move destination, and malformed or ambiguous selectors fail without
falling back to text search.

### Branch, source-root, and classpath contract

- `--branch` is mandatory for every symbol command. It selects the worktree,
  Minecraft/Java version context, JDK/release, source-set catalog, exclusions,
  and default classpath policy.
- Without a custom root, index every Java source set and generated Java root
  declared for the branch. Keep source-set identity and directed visibility;
  for example, production code must not resolve test-only declarations merely
  because both were indexed.
- `--source-root <path>` is repeatable. Supplying any custom root requires an
  explicit `--classpath-mode branch|isolated`.
- `branch` mode overlays the custom roots on the selected branch's ordinary
  dependency/classpath context. `isolated` mode retains the branch-selected
  JDK and Java release but excludes SFM/project outputs, Minecraft/loader/mod
  dependencies, ambient `CLASSPATH`, and undeclared jars.
- The first implementation does not add arbitrary classpath path flags or a
  generic Java project manifest. Reports always state source roots, source-set
  identities/visibility, classpath mode, and fingerprints so isolation is
  observable rather than assumed.

### Explicit mutation mode contract

Read-only `show-definition`, `list-usages`/`list-usage`, and `list` accept
neither `--dry-run` nor
`--apply`. Every future mutating command requires exactly one of those flags:

| Mode | Filesystem behavior |
| --- | --- |
| `--dry-run` | Produce the complete edit plan, diff, projected hashes, diagnostics, and unresolved references without changing source files. |
| `--apply` | Revalidate source hashes and apply transactionally in place. |
| `--apply --output-root <path>` | Copy the complete input source tree to the output root, then apply there; the source root remains unchanged. |

Neither flag, both flags, or `--dry-run --output-root` is a CLI error. There is
no implicit preview mode and no separate generic `refactor apply` for symbol
operations.

There is no `refactor` compatibility alias for this symbol surface: it has not
been implemented or released, so `symbol` is authoritative for definition,
usage discovery, listing, rename, and move. The separate `refactor` namespace may host
expression/statement operations and typed plan application, but it must not
silently alias or duplicate the `symbol` commands. Help, completions, and
documentation must keep those namespaces distinct.

## Release code-review integration — 2026-08-08

This suite is also the CLI-side producer for the release code-review gate in
the [global comment selection and review sessions plan](global%20comment%20selection%20and%20review%20sessions%20plan.md)
and the [release checkpoint and slim artifact plan](release%20checkpoint%20and%20slim%20artifact%20plan.md).
It must consume the same immutable source snapshots, symbol indexes,
comparison artifacts, selectors, and equivalence proofs; it must not create a
second review or approval model.

For every supported Minecraft lane, the release review compares that lane's
`previous_release` selector with the candidate `HEAD`. The report records the
lane identity, snapshot and source hashes, parser/index/classpath/configuration
fingerprints, and all unresolved diagnostics. The review surface is divided
into inspectable units: a changed method/function where safely identifiable,
otherwise a type, field, import, file, or diff-operation unit. A method unit
includes its declaration/signature, annotations, imports/static imports,
superclass/interface contracts, referenced fields/types/methods, affected
callers or method references, and the resolved environment that can alter its
meaning.

Cross-lane deduplication is a proof-producing comparison, not a textual shortcut.
An identical unannotated method body is only a candidate for shared review. The
candidate must also have an equivalent dependency/import/inheritance/mapping/
annotation/configuration/feature/classpath closure. `@MCVersionDependentBehaviour`
is an explicit seam that blocks silent shared approval; its absence is not a
proof of equivalence. Accepted morphisms, initially including a narrowly scoped
Forge-to-NeoForge import/package adaptation, are versioned rules with exact
scope, tests, witnesses, and an equivalence level. Unknown, ambiguous,
content-changed, dependency-changed, or out-of-scope cases remain separate or
suspend approval.

The comparison derives review selectors after the before/after snapshots exist.
For example, a field rename produces a definition unit plus old/new usage
units; a signature change includes callers and method references; an import
change includes the affected type-resolution closure. These selectors and
their provenance are projected into ordinary comments/tags such as `#added`,
`#removed`, `#modified`, `#renamed`, `#problem`, and `#approved`, so the
in-game explorer and exported report review the same objects.

The CLI must make this review useful to agents and maintainers without an IDE
plugin: it emits human-readable and Facet/Figue JSON reports, supports
lane → unit → span → dependency/usage → equivalence/morphism drill-down, and
offers preview-first, hash-checked, transactional symbol operations such as
renaming a field and all statically resolved usages. A mutation is not a
review approval; it must emit the same comparison/provenance evidence and run
the configured compile, test, audit, and propagation postconditions.

## Canonical refactoring operation catalog

This is the authoritative list of planned operations. The CLI scaffold should
expose every name below from the beginning, even when an operation is initially
reported as `not implemented`. That makes the supported surface discoverable,
keeps help and documentation synchronized, and gives each operation a stable
place for capability/version reporting.

| Operation | Initial syntax | Meaning | First safety boundary |
| --- | --- | --- | --- |
| `rename` | `symbol rename <src> <dest>` | Rename a symbol in place; its declaring parent does not change. | Resolve exactly one declaration and all statically resolvable references; reject collisions. |
| `move` | `symbol move <src> <dest>` | Rename plus move the declaration to a new parent/type/package. | Verify visibility, imports, inheritance, private dependencies, and destination collisions. |
| `extract-method-args` | `refactor extract-method-args <method-query> [--name <ArgsType>]` | Create an args record for the selected parameters, create an args-accepting method, move the original logic there, and make the old method delegate with a new record. | Preserve overload behavior, evaluation order, visibility, generic types, and checked exceptions. |
| `extract-variable` | `refactor extract-variable <expression-query> <name>` | Bind an expression to a local variable and replace the selected occurrence(s). | Respect scope, evaluation count, mutability, and declaration placement. |
| `extract-method` | `refactor extract-method <selection-query> <name>` | Extract a statement/expression selection into a method and replace it with a call. | Infer inputs/outputs, return type, throws, control-flow exits, and captured state. |
| `inline-method` | `refactor inline-method <method-query>` | Replace statically resolvable calls with the method body and optionally remove the declaration. | Require a single safe implementation; reject recursion, dynamic dispatch, and incompatible control flow. |
| `replace-method-call` | `refactor replace-method-call <call-query> <replacement>` | Replace selected calls with a typed expression/template or another method invocation. | Validate receiver/argument types and make replacement count explicit. |
| `change-method-signature` | `refactor change-method-signature <method-query> <signature-spec>` | Add, remove, reorder, rename, or retype parameters and update callers/references. | Resolve overloads and support an explicit compatibility-overload option. |

`rename` is intentionally distinct from `move`: rename changes only the
identifier, while move changes the declaration's parent and therefore may
change qualification, imports, access, inheritance, and generated descriptors.
`extract-method-args` and `inline-method-usage` are designed as composable
steps; the latter may be exposed as an alias for `inline-method` if the
implementation needs a more precise name for call-site-only inlining.

Every operation must report one of `planned`, `previewable`, `applicable`, or
`unsupported` for the selected language/version and must identify why an
operation is unsupported rather than silently falling back to text replacement.

## Scope and constraints

- Implement on 1.19.2 first; propagate with `sfm-propagate-changes git merge`.
- Do not invoke Gradle. Use the CLI's compile, test, audit, and propagation
  commands for validation.
- The immediate Phase 0 is read-only. It proves definition/usage navigation,
  source selection, structured output, and scenario infrastructure before any
  source mutation is implemented.
- Do not turn the CLI into a general Java package manager in this plan: no
  `sfm java init`, Gradle build-file generator/editor, generic dependency
  manifest, or promise to support arbitrary external Java projects. Generic
  parser/index/edit primitives remain welcome internally because SFM review and
  tiny isolated scenarios need them.
- Reuse the existing source-set exclusions and dependency/source acquisition
  rules so the refactorer sees exactly the sources compiled for a branch.
- Keep Java parsing and symbol resolution in Rust. Existing audit parsing and
  classpath knowledge are the starting point; Arborium/Fable (`G:\Programming\Repos\arborium`,
  `G:\Programming\Repos\facet\fable`) are candidates to accelerate or
  structure the implementation, not assumptions that must become dependencies.
- Prefer gix for repository operations. Every write must be previewable and
  produce a machine-readable change report.
- Refactorings are source-to-source transformations. Reflection, generated
  bytecode, annotation processors, and dynamic dispatch are not claimed to be
  complete; the tool must report those limitations instead of guessing.
- Preserve formatting and comments wherever possible. A formatter fallback is
  allowed only as an explicit option.
- Version-dependent Java differences must remain bounded by
  `@MCVersionDependentBehaviour`; a refactor must not erase or move that
  boundary without reporting it.

## Confirmed design decisions

| Area | Confirmed decision | Acceptance consequence |
| --- | --- | --- |
| Command shape | `symbol show-definition`, canonical `symbol list-usages` with Figue alias `symbol list-usage`, `symbol list`, `symbol index refresh|show`, `symbol rename`, and `symbol move`; no `java`/`refactor` alias. | `help list --short` is verb-like; both usage spellings parse identically; exact selectors and list globs remain separate typed grammars. |
| Target syntax | Access-Transformer-derived class/field/method targets with JVM descriptors and `$` nested classes, parsed once by `JavaSymbolSelector`. | Ambiguous targets fail and list candidates; no dotted-member or textual-search fallback. |
| Safety mode | Read-only commands have no mode; mutation requires exactly one of `--dry-run` or `--apply`. | Neither, both, and `--dry-run --output-root` fail before source discovery or writes. |
| Rewrite model | Parse once, resolve symbols, create edits against byte ranges, validate edits, then write atomically. | No overlapping edits or partial files. |
| Resolution | Index all declared Java source sets with directed visibility, imports, packages, lexical scopes, inheritance, overload descriptors, and selected classpath mode. | Unresolved references are surfaced; mutation remains blocked unless a later typed policy explicitly permits them. |
| Custom source roots | `--branch` remains required and `--source-root` requires `--classpath-mode branch|isolated`. | Scenarios prove that isolated mode cannot accidentally resolve SFM/Minecraft/ambient classes. |
| Output | Teamy-style `CliOutput` plus global `--output-format text|json|csv`; each symbol report has a versioned Facet schema. | stdout is one report, stderr contains logs, and snapshots prove deterministic rendering. |
| Propagation | Apply only to the oldest branch; later branches receive the commit through the propagation CLI. | Branch audit runs before and after propagation. |

## Implementation reconnaissance (2026-07-16)

The following facts were verified in the current tree and constrain the design:

- `arborium-java = 2.18.1` and `tree-sitter-patched-arborium = 0.25.10` are
  already pinned dependencies of the CLI. The existing
  `source_audit::font_render_surface_audit` creates a parser, reports parse
  gaps, walks Arborium nodes, and preserves byte/row/column locations. The
  first implementation should extract this parser setup and source-span
  utilities instead of adding another Java parser.
- The audit resolver is deliberately lexical and demand-driven: it resolves
  package/import names, fields, parameters, locals, `var` aliases, and selected
  inheritance paths only after a method name matches a deny rule. It is not yet
  a complete Java symbol solver. Refactoring requires a separate indexed symbol
  layer with declaration identities, overload descriptors, inheritance edges,
  and classpath types; silently treating the audit resolver as sufficient would
  produce unsafe rewrites.
- Arborium/tree-sitter nodes expose ranges into the original source, so a
  lossless edit engine can replace identifier/list/body spans without printing
  the entire file. Tree-sitter error recovery means parse errors must be a
  blocking diagnostic for mutation unless a future operation explicitly proves
  that its target is outside the error subtree.
- The CLI uses a top-level `Command` enum with one `Facet`/Figue args module per
  command and an `invoke` method. A symbol command therefore needs a new
  `cli::symbol` module wired into `Command`, plus parser tests. It should not be
  hidden inside `audit` or duplicated under `refactor`.
- Facet/Figue already provide the typed command/report pattern and
  `facet_json`/`facet-pretty` provide structured output. Fable is not a Java
  parser or Java symbol solver: it reflects Facet-described Rust values and
  executes a small typed language over those values. It may later be useful for
  querying a serialized refactoring index, but it cannot replace Arborium or
  Java resolution for this suite.
- The current dependency set already includes gix and the project policy
  prefers gix. The refactorer should use the existing repository/worktree
  context and gix object/index APIs for status, file hashes, and commit-aware
  safety checks rather than shelling out to `git.exe`.
- `engine_sources` owns source-set enumeration and compile classpath assembly.
  Refactoring must consume the same source exclusions and branch/version
  context, but should not invoke a build merely to discover Java files.
- The current build path explicitly compiles `main`, `gametest`, `datagen`, and
  `test`, while lockfile feature membership names Java source surfaces such as
  `main-java` and `test-java`. Phase 0 must reconcile these existing catalogs
  into one typed analysis inventory and record visibility rather than hard-code
  only the lockfile feature list or only `main`.
- The current CLI returns `eyre::Result<()>` and has no global output format.
  `G:\Programming\Repos\teamy-rust-cli\src\cli\output.rs` is the verified
  reference for `CliOutput::none`, `CliOutput::facet`, terminal-sensitive
  defaults, and one-time text/JSON/CSV emission. Migration may adapt legacy SFM
  commands with `CliOutput::none()` while new symbol commands use typed output
  immediately.
- The CLI crate currently has no `tests/` directory or explicit `[[test]]`
  target. Phase 0 therefore creates the descriptive integration-test layout
  instead of adding another large inline module to `cli.rs`.

These observations turn “AST rewrite” into four explicit layers: a lossless
syntax layer, a project symbol index, an operation planner, and a transactional
source/repository writer. Each layer needs independent fixtures and diagnostics.

## Snapshot, comparison, and equivalence contract

The versioned full-filesystem interchange and episode history are owned by the
[snapshot episodes and deterministic action environments plan](snapshot%20episodes%20and%20deterministic%20action%20environments%20plan.md).
The refactoring suite consumes immutable snapshot identities and may produce a
new snapshot plus explicit operation lineage. It must not invent a competing
repository snapshot format.

### Snapshot-scoped symbol identity

A symbol id identifies a declaration inside one immutable source/index
snapshot. Rename, move, file rename, signature change, extraction, and inlining
can invalidate paths, qualified names, descriptors, and node positions.
Cross-snapshot identity is therefore represented by a proposed or proven
correspondence produced by an operation/comparison, not by claiming that an id
is eternally stable.

Queries may be set-valued and composable. An operation that requires one target
applies an explicit exactly-one cardinality gate and reports zero or ambiguous
matches with candidates. Renaming ten methods is an outer iteration over ten
single-symbol operation plans rather than making the primitive rename operation
implicitly multi-target.

### Structured source comparison

The comparison producer feeds the [global comment selection and review
sessions plan](global%20comment%20selection%20and%20review%20sessions%20plan.md).
It retains structured operations and correspondences as evidence, while also
supporting a deterministic projection into generated comments containing
conventional derived tags such as `#added`, `#removed`, `#modified`, and
`#renamed`. Selection rules target the exact before/after glyph ranges. The
comment projection does not replace the structured comparison artifact and
must be reproducible from it.

Add a versioned machine-readable comparison artifact suitable for source review:

```text
SourceComparison {
    before_snapshot,
    after_snapshot,
    files,
    operations,
    correspondences,
    diagnostics,
    assumptions
}
```

File operations distinguish add, delete, rename, modify, and unchanged.
Source operations begin with deterministic line/range insertion, deletion, and
replacement, then add formatting-only change, symbol rename, unchanged body
under rename, renamed-and-modified body, and explicit ambiguous/unknown
correspondence. Reports retain before/after source spans, hashes, bounded
excerpts, confidence/proof level, and parent operation relationships.

The in-game workspace remains a downstream consumer and displays source code
with annotations. A generic AST tree is not the primary review UI. The first
viewer may consume fixture JSON before this producer is implemented; Vox is not
required because the CLI can prepare an ordinary file on disk.

### Equivalence ladder and reversibility

Reports name byte, syntax, formatting-insensitive, alpha, bounded-symbolic,
observational-under-policy, task, or unknown equivalence. They never collapse
these into an unqualified boolean. A conservative unknown result is correct.

Alpha-renaming, normalization, IR lowering, and semantic amalgamation may erase
original names, formatting, or implementation structure. If exact
deamalgamation is advertised, the artifact stores witnesses/source maps and
operation lineage sufficient to restore those distinctions. The canonical full
snapshot remains available even when an optimized or normalized form exists.

## Phase 0 implementation references

- CLI root and current `Result<()>` dispatch:
  `platform/cli/sfm-propagate-changes/src/cli/cli.rs`
- Global arguments and top-level emission boundary:
  `platform/cli/sfm-propagate-changes/src/cli/global_args.rs` and
  `platform/cli/sfm-propagate-changes/src/lib.rs`
- Source discovery/compilation catalog:
  `platform/cli/sfm-propagate-changes/src/jar_build/engine_sources.rs`,
  `engine_source_catalog.rs`, and `engine_run.rs`
- Lockfile feature/source membership:
  `platform/minecraft/sfm-toolchain.lock.json`
- Existing Arborium parser and source index seed:
  `platform/cli/sfm-propagate-changes/src/source_audit/font_render_surface_audit.rs`
- Required Rust validation:
  `platform/cli/sfm-propagate-changes/check-all.ps1`
- Output architecture reference:
  `G:\Programming\Repos\teamy-rust-cli\src\cli\output.rs`
- Selector grammar authority:
  `G:\Programming\Repos\Minecraft\MinecraftForge\AccessTransformers\FMLAT.md`

## Phase 0 decision gate

| Question | Closed decision | Acceptance consequence |
| --- | --- | --- |
| First deliverable | Read-only definition and usage navigation plus its public-CLI scenario harness. | Mutation is not required to complete Phase 0 and cannot occur accidentally. |
| Project boundary | SFM branch mode plus isolated tiny scenarios; no generic Java project manager. | No Gradle editor/init/schema work is introduced. |
| Selector | Forge Access Transformer class/field/method target grammar. | One typed parser and descriptor-focused tests define the public contract. |
| Source coverage | Every branch-declared Java source set/root with directed visibility. | Inventory tests detect omissions and cross-source-set leakage. |
| Custom roots | Mandatory branch plus explicit `branch|isolated` classpath mode. | Isolation and branch overlay each have end-to-end scenarios. |
| Output | Teamy-style `CliOutput`, global text/JSON/CSV, versioned Facet structs. | Reports are deterministic and stdout-safe for agents. |
| Scenarios | Restricted `command.ps1`, in-process Figue invocation, adjacent expected/actual files. | The public CLI is tested without shell execution or hidden harness-only request structs. |
| Mutation safety | Future commands require exactly one of `--dry-run|--apply`. | Phase 0 parser tests freeze the contract; implementation waits for Phase 3. |

No unresolved Phase 0 choice changes architecture, public syntax, storage, or
acceptance. Small implementation details such as internal module splitting and
diagnostic enum names may be chosen reversibly while preserving these contracts.

**Reversible Phase 0 assumption:** each `command.ps1` contains exactly one CLI
invocation. This keeps the tokenizer and failure attribution small without
changing the public CLI; a later scenario can add an explicitly modeled command
sequence if a real test needs one.

## Phase 0 — Read-only symbol navigation and scenario harness (complete)

This was the initial implementation goal. Items `0.1` through `0.7` completed
the source-only foundation before mutation, broad refactoring catalogs,
cross-lane equivalence, or the in-game review provider. The phase deliberately delivered a useful vertical
slice: a real user/agent can ask where a Java symbol is defined and used in an
SFM branch, while tiny isolated scenarios prove the same public contract.

The command spellings recorded in Phase 0 completion notes are historical
evidence. Phase 0.8 owns and replaces the unreleased public navigation
spellings without changing the completed source-only implementation evidence.

**Final-audit follow-up (2026-08-09):** Reopened the affected items after the
first green full-suite run. The closeout rerun additionally proves that
unknown external/JDK-looking names are not guessed as resolved; all parse gaps
that can conceal usages remain located in reports; conflicting direct imports
are ambiguous; array dimensions participate in overload resolution; CSV keeps
report context and distinguishes empty outcomes; source-root inventory is
shared with the build catalog rather than duplicated; and the scenario harness
asserts the public exit status as well as the rendered snapshot.

### Phase 0 scope and non-goals

**In scope:** one-time typed output rendering; versioned definition/usage
reports; Access-Transformer-derived selectors; all-source-set branch discovery;
explicit custom-root classpath modes; a deterministic source-only Java symbol
index sufficient for the declared scenarios; adjacent scenario snapshots; and
non-mutating rename/move command scaffolding that proves the future CLI safety
contract.

**Out of scope:** applying edits; complete bytecode/classpath symbol solving;
arbitrary classpath flags; Java project initialization; Gradle generation or
editing; generic package management; release comparison/equivalence;
Forge-to-NeoForge morphisms; comment projection; and Minecraft UI work. Those
remain later phases and must reuse, not replace, the Phase 0 output, selector,
workspace, index, and scenario contracts.

### [x] 0.1 Add typed CLI output without regressing legacy commands

**Completion notes (2026-08-09):** Added
`src/cli/output.rs` with `OutputFormat::{Text,Json,Csv}` and one-time
`CliOutput` rendering/capture, added global `--output-format`, migrated the
top-level invocation boundary to return/emit `CliOutput`, and adapted legacy
commands through `CliOutput::none()` without rewriting their existing output.
Added the matching pinned `facet-csv` dependency. Validation passed:
`cargo test --all-features cli_output` (3 tests),
`cargo test --all-features parses_global_output_format` (1 test), and
`cargo test --all-features cli::cli::tests` (49 tests).

**Work:**

- Adapt the proven pattern in
  `G:\Programming\Repos\teamy-rust-cli\src\cli\output.rs` into
  `platform/cli/sfm-propagate-changes/src/cli/output.rs`: add
  `OutputFormat::{Text,Json,Csv}`, `CliOutput::none()`,
  `CliOutput::facet(value)`, and one terminal-aware `emit` boundary.
- Add optional global `--output-format`; retain text for an interactive terminal
  and JSON for redirected typed output when the flag is absent.
- Change top-level invocation to return `eyre::Result<CliOutput>`. Adapt legacy
  command arms with `CliOutput::none()` so their existing direct behavior is not
  mechanically rewritten in this phase. New symbol commands must not print
  their report internally.
- Keep logs/diagnostics on stderr. A typed command emits exactly one report on
  stdout after invocation succeeds.

**Validation:**

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features cli_output
```

**Completion criteria:** Facet values render deterministically as text, JSON,
and CSV; redirected default output is JSON; no-output legacy adapters emit no
extra stdout; and the existing CLI parsing tests still pass.

### [x] 0.2 Define versioned definition and usage report schemas

**Completion notes (2026-08-09):** Added Facet report models for
`sfm.symbol-definition/1`, `sfm.symbol-usage-list/1`, and the Phase 0 mutation
scaffold. Reports carry normalized selector/symbol identities, one-based and
byte spans, source hashes, source roots, directed source-set visibility,
exclusions, branch/Java/JDK context, parser/index/classpath fingerprints,
typed diagnostics, deterministic ordering, and explicit success/no-match/
ambiguous/unsupported outcomes. `CliOutput` preserves typed reports while
assigning statuses 0/2/3/4 respectively. Definition and usage reports
round-trip through Facet JSON and render as deterministic text, JSON, and
report-specific CSV. CSV now begins with a typed report row, so even empty
no-match and ambiguous outputs retain distinct outcomes/statuses, selectors,
workspace context, and fingerprints; result and diagnostic rows retain their
source hashes, spans, confidence, and identity. Validation passed: `cargo test --all-features
symbol_output` (4 tests) and `cargo test --all-features cli_output` (4 tests).

**Work:**

- Add Facet structs `SymbolDefinitionOutput` and `SymbolUsageListOutput` rather
  than anonymous JSON values. Each includes a schema discriminator
  (`sfm.symbol-definition/1` or `sfm.symbol-usage-list/1`), branch, Java/JDK
  context, source roots, source sets and visibility, classpath mode/fingerprint,
  parser/index fingerprint, normalized selector, matches, diagnostics, and
  source hashes.
- A definition/reference span includes repository- or source-root-relative path,
  source-set ID, symbol kind and qualified owner, JVM descriptor where
  applicable, byte span, one-based line/column display coordinates, and
  resolution confidence.
- Define deterministic ordering and explicit zero-match and ambiguous-match
  outcomes. Do not encode ambiguity by silently choosing the first result.
- Use typed diagnostics for parse gaps, inaccessible source-set references,
  unresolved external types, and unsupported constructs. Schema evolution is
  owned by the discriminator; `CliOutput` only renders the value.

**Validation:**

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features symbol_output
```

**Completion criteria:** representative outputs round-trip through Facet JSON,
render in all three output formats, remain byte-deterministic after path
normalization, and distinguish success, no match, and ambiguity.

### [x] 0.3 Build the adjacent, public-CLI scenario harness

**Completion notes (2026-08-09):** Added the explicit
`java_analysis_scenarios` Cargo target and the adjacent descriptive scenario
layout. The harness accepts a deliberately restricted single-command
PowerShell-compatible syntax, requires the exact executable basename, rejects
shell control/interpolation/redirection, Figue-parses and invokes the production
CLI in-process through `Cli::invoke_in`, canonicalizes JSON, atomically writes
only ignored/untracked adjacent actual files, and prints an explicit
scenario-local `Copy-Item` acceptance command without changing expected files.
It also derives the expected public process status from each typed snapshot
outcome and compares it with `CliOutput::exit_code()`. The initial typed
unsupported-mutation scenario proves the harness independent of the
still-integrating index. Validation passed: `cargo test --all-features --test
java_analysis_scenarios` (7 tests exercising 11 scenarios).

**Work:**

- Add this exact discoverable layout and explicit Cargo test target:

  ```text
  platform/cli/sfm-propagate-changes/tests/java_analysis/
    java_analysis_scenario_test.rs
    scenarios/
      <descriptive-scenario>/
        command.ps1
        source/
        output-expected.json
        output-actual.json       # generated, ignored
  ```

  Register it as `[[test]] name = "java_analysis_scenarios"`; do not create
  ambiguous `tests/<name>/test.rs` files.
- Treat `command.ps1` as executable documentation of the public CLI, for
  example:

  ```powershell
  sfm-propagate-changes.exe --output-format json symbol usage list example.A --branch 1.19.2 --source-root source --classpath-mode isolated
  ```

- Do not start PowerShell or the installed executable. Parse a deliberately
  restricted, one-command PowerShell-compatible token syntax, require the exact
  executable basename `sfm-propagate-changes.exe`, reject variables,
  interpolation, pipelines, redirection, separators, control flow, command
  substitution, and trailing commands, then pass argv to Figue and invoke the
  production typed command through an in-process output-capture seam. Quoted JVM
  descriptors and Windows paths remain representable.
- Place `tests/java_analysis/scenarios/.gitignore` beside the scenarios with
  `**/output-actual.json`. Before writing, prove the actual path is ignored and
  untracked and the expected path is not ignored. Write actual output atomically
  and never modify expected output.
- Canonicalize the JSON report before byte comparison. On missing/different
  expected output, retain actual and print an exact scenario-local PowerShell
  command such as
  `Copy-Item output-actual.json output-expected.json`; do not auto-accept or
  depend on interactive `cargo test` stdin.

**Validation:**

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features --test java_analysis_scenarios
```

**Completion criteria:** a passing scenario exercises the production Figue and
command invocation path without spawning a shell; malformed/multi-command
scripts are rejected; snapshot mismatch produces an adjacent actual plus the
acceptance command; and tracked/non-ignored overwrite attempts fail safely.

### [x] 0.4 Implement the symbol command tree and selector parser

**Completion notes (2026-08-09):** Added the dedicated `symbol definition`,
`symbol usage list`, `symbol rename`, and `symbol move` Figue tree with required
branch selection, repeatable custom roots, explicit custom-root classpath
modes, and an Access-Transformer-derived selector parser for type, field, and
descriptor-qualified method targets including `$` nested classes. Rename/move
freeze the future arity and `--dry-run|--apply`/`--output-root` contract, then
return a typed status-4 unsupported report before source discovery or writes.
Validation passed: `cargo test --all-features java_symbol_selector` (3 tests)
and `cargo test --all-features symbol_cli` (4 tests).

**Work:**

- Add a dedicated `cli::symbol` command tree for `definition`, `usage list`,
  `rename`, and `move`, with `--branch` required on every leaf. Do not add a
  `java` command or a `refactor` alias for these operations.
- Implement one typed `JavaSymbolSelector` parser using the selector contract
  above: class, class plus field, or class plus method/JVM descriptor;
  parentheses identify methods and `$` identifies nested classes.
- Add repeatable `--source-root` and conditional
  `--classpath-mode branch|isolated`. Figue validation must reject a custom
  source root without an explicit mode.
- Parse `rename`/`move` now to freeze arity and require exactly one of
  `--dry-run|--apply`, but return a typed, deterministic `unsupported in this
  phase` result before source discovery or mutation. Validate `--output-root`
  mode combinations now so later implementation cannot drift.

**Validation:**

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features java_symbol_selector
cargo test --all-features parses_symbol_cli
```

**Completion criteria:** help and parser tests cover exact class/field/method,
overload descriptor, nested-class, malformed, missing-branch, custom-root,
classpath-mode, and mutation-mode cases; no symbol command can mutate files in
Phase 0.

### [x] 0.5 Build the branch-aware source workspace and isolation boundary

**Completion notes (2026-08-09):** Added a deterministic read-only Java source
workspace over branch and custom roots. The branch inventory resolves the
selected worktree, Java release, all `main`, `gametest`, `datagen`, `test`, and
generated roots, version exclusions, inactive-feature exclusions, directed
source-set visibility, and a lockfile-derived classpath fingerprint without
building or acquiring artifacts. Isolated mode has an empty dependency surface
and never reads ambient `CLASSPATH`; custom roots ending in `<source-set>/java`
retain known main/test-style visibility while arbitrary roots use `custom`.
The checked-in v4 feature projection now exposes inactive-feature source
exclusions through the same lockfile model used by the build. A shared
declarative Java source catalog now owns source sets, visibility, declared and
generated roots, build roles, and exclusion policy for both analysis and jar
source collection; a synthetic added generated root proves both consumers see
catalog additions. Validation passed: `cargo test --all-features
java_analysis_workspace` (7 tests), `cargo test --all-features
java_source_catalog` (2 tests), the real 1.19.2 inventory assertion, and
`inactive_features_contribute_source_excludes` (1 test).

**Work:**

- Extract a reusable analysis source catalog from existing lockfile/build-engine
  discovery instead of creating a second hard-coded list. Reconcile the current
  `main`, `gametest`, `datagen`, and `test` compile roots with lockfile feature
  source membership and generated Java roots declared by a branch.
- Enumerate all active Java roots deterministically, apply the same exclusions
  used by compilation, retain source-set identity, and model directed
  visibility. Indexing all roots must not make test-only declarations visible to
  production sources.
- In ordinary branch mode, resolve the selected worktree and branch classpath
  context without invoking a build. In custom-root `branch` mode, use the branch
  dependency context. In `isolated` mode, allow only the custom roots plus the
  branch-selected JDK/Java release and prove that ambient `CLASSPATH`, project
  outputs, Minecraft, loader, and mod jars cannot leak in.
- Report selected roots, exclusions, visibility edges, classpath mode, and
  stable fingerprints in every output.

**Validation:**

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features java_analysis_workspace
```

**Completion criteria:** a branch inventory includes every configured Java
source set/root in deterministic order; a visibility fixture rejects a
main-to-test reference while permitting a declared test-to-main reference; and
an isolated scenario cannot resolve an SFM/Minecraft class unless its source is
present in the scenario.

### [x] 0.6 Implement deterministic source definition and usage indexing

**Completion notes (2026-08-09):** Added reusable Arborium syntax/span parsing
and a deterministic two-pass source index for packages/imports, classes and
nested `$` types, fields, constructors, JVM-descriptor methods, declarations,
type references, field references, invocations, and method references. The
index models lexical shadowing and source-set visibility, excludes comments and
strings, retains relevant unresolved/ambiguous/inaccessible/parse diagnostics,
suppresses unrelated non-parse workspace diagnostic detail with an explicit
count, and emits stable source/index hashes. Every located parse gap remains in
usage reports because it can conceal a usage. Unknown external names remain
unresolved in both classpath modes rather than being guessed; conflicting
direct imports become ambiguity diagnostics; and array dimensions participate
in overload matching. Definition queries use a declaration-only build while
usage queries perform the expression traversal. Validation passed: `cargo test
--all-features java_symbol_index` (10 tests), the syntax tests (3 tests,
included by the full suite), and `cargo test
--all-features source_audit` (39 existing regression tests).

**Work:**

- Extract Arborium parser setup, byte-span/line-map handling, traversal, and
  parse-gap diagnostics from the existing source audit without changing audit
  results.
- Build the smallest reusable source index that resolves declarations and
  references needed for class, field, and descriptor-qualified method queries
  across packages/imports and visible source sets. Preserve unresolved edges as
  diagnostics; do not hide them or guess from spelling alone.
- Implement `symbol definition` and `symbol usage list` against that index.
  Usage categories include declaration separately from imports, type
  references, field references, invocations, and method references where the
  source model proves them. Strings/comments are not semantic usages.
- Sort paths, symbols, spans, diagnostics, and hashes deterministically. Return
  distinct command outcomes for no match and ambiguous match while retaining
  their typed reports for automation.

**Validation:**

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features java_symbol_index
cargo test --all-features source_audit
```

**Completion criteria:** tiny multi-file sources resolve imported type, field,
method invocation, and method-reference usages without Minecraft compilation;
same-spelled unrelated symbols are excluded; overload descriptors disambiguate;
and audit parser behavior remains unchanged.

### [x] 0.7 Prove the end-to-end read-only vertical slice

**Completion notes (2026-08-09):** Added and reviewed adjacent golden
scenarios for type definition, imported type usages, field usages, overloaded
descriptor-qualified method usages, method references, nested classes, zero
match, duplicate-definition ambiguity, directed main/test visibility,
classpath isolation, and the frozen unsupported mutation contract. Added
`docs/java symbol analysis.md` with public commands, selector grammar,
source/classpath modes, schemas/statuses, scenario acceptance, and mutation
deferral. A real branch query was run in both JSON and text:
`symbol definition ca.teamdman.sfm.common.util.SFMBlockPosUtils --branch
1.19.2`. It resolved the main-source declaration with classpath fingerprint
`blake3:19aebf76bc1a3e829a08306e2207d82a9cd6404519eadae6dd0efdf6b66701e4`
and declaration-only index fingerprint
`blake3:3048629fd59efd62e4af2d30b83ce7c12ebdd1f469aa7be773f0fbea99c12bc1`;
all report paths were worktree-relative. The declaration-only path reduced the
unoptimized real definition query from roughly 76 seconds (when it also built
all usages) to roughly 18 seconds on the warm audited text run;
persistent/incremental indexing remains a
later optimization, not a Phase 0 correctness dependency.

Validation passed: `cargo test --all-features --test java_analysis_scenarios`
(7 harness tests exercising 11 scenarios) and the required `check-all.ps1`
(dependency policy, format, Clippy with warnings denied, build, 407 unit tests
passed, 1 ignored, and all 7 integration-harness tests passed). The
first sandboxed full-suite attempt reached 397 passing tests before the known
Codex `os_error=5` denied an existing ripgrep temp-directory subprocess test;
the identical required script passed outside that sandbox restriction. No
Gradle or propagation command was run.

**Work:**

- Add descriptive scenarios for type definition, imported type usages,
  field usages, overloaded method/descriptor usages, nested classes,
  zero-match, ambiguity, source-set visibility, and classpath isolation. Keep
  each scenario minimal enough that its expected JSON explains one behavior.
- Run at least one real 1.19.2 branch query over the ordinary SFM source catalog
  in both human-readable and JSON output. Record the exact selector and observed
  source-set/classpath fingerprints in completion notes; do not commit a
  machine-specific absolute path.
- Document the public Phase 0 syntax, selector grammar, source/classpath modes,
  output schemas, scenario acceptance workflow, and explicit mutation deferral.
- Run the repository-required Rust validation from the CLI crate. Do not invoke
  Gradle and do not propagate during this phase unless separately authorized.

**Validation:**

```powershell
Set-Location platform/cli/sfm-propagate-changes
cargo test --all-features --test java_analysis_scenarios
.\check-all.ps1
```

**Completion criteria:** all `0.1`–`0.7` evidence is recorded in-place; a user
or agent can execute the documented definition/usage commands against both an
SFM branch and an isolated tiny scenario; outputs are deterministic and
machine-readable; and no source file is changed. Phase 0.8 supersedes the
experimental navigation spellings and adds discovery/dependency coverage before
symbol rename dry-run/apply.

### Phase 0 risk register

| Risk | Guardrail and proof |
| --- | --- |
| Migrating invocation to `CliOutput` duplicates or captures legacy stdout. | Legacy arms return `CliOutput::none()`; focused tests assert no extra bytes before the full `check-all.ps1` run. |
| `command.ps1` becomes an accidental shell execution surface. | The harness never starts PowerShell, accepts one exact executable/command, rejects shell constructs, and has adversarial tokenizer tests. |
| “All source sets” is implemented as one flat namespace. | The workspace model records source-set IDs and directed visibility; main-to-test rejection and test-to-main acceptance are fixtures. |
| Isolated scenarios accidentally resolve ambient/project classes. | Clear ambient `CLASSPATH` influence by construction and prove SFM/Minecraft names remain unresolved unless supplied as scenario source. |
| Snapshot updates overwrite trusted expectations. | Actual must be ignored/untracked, expected must be tracked-capable/non-ignored, writes are atomic, and acceptance is a printed manual `Copy-Item`. |
| A spelling match is presented as a resolved usage. | Reports retain confidence and unresolved diagnostics; semantic usages require declaration identity, and ambiguous/unknown cases never mutate. |
| The slice expands into package-manager or Gradle tooling. | `JAVA-01` and Phase 0 non-goals prohibit init/manifest/Gradle generation; tiny scenarios need only custom roots plus branch/isolated mode. |

## Phase 0.8 — Verb-first discovery and lockfile-pinned dependency indexes (complete)

This is the next implementation goal. Complete `0.8.1` through `0.8.6` as one
read-only vertical slice before beginning mutation or the broader Phase 1
architecture inventory. It closes the usability gap observed immediately after
Phase 0: users can discover an exact selector, query dependency classes such as
Minecraft's `MultiLineEditBox`, and understand how to prepare or refresh the
offline dependency index without already knowing internal command grammar.

### Phase 0.8 confirmed design

| Area | Decision |
| --- | --- |
| Canonical navigation commands | `symbol show-definition`, `symbol list-usages`, and `symbol list`; `list-usage` is a Figue alias on the `list-usages` enum variant. The unreleased `symbol definition` and `symbol usage list` spellings are removed rather than retained as hidden compatibility paths. |
| Discovery pattern | `symbol list [pattern]` matches the complete canonical selector case-sensitively. Omitted means `*`; `*` matches zero or more characters and `?` exactly one character. No character classes, recursive-glob special case, regex mode, or fuzzy scoring is introduced in this slice. |
| Exact versus discovery grammar | `show-definition`, `list-usages`/`list-usage`, `rename`, and `move` continue to use exact `JavaSymbolSelector`. Only `symbol list` accepts `JavaSymbolGlob`; wildcard input never falls back from an exact query. |
| Cached/live split | Dependency source symbols are prepared offline and persisted; SFM branch/custom-root sources are parsed and indexed live on every query. No SFM source index cache, file watcher, or background daemon is introduced. |
| Dependency source authority | Reuse lockfile-declared source providers and their priority. Refresh materializes the preferred provider through the existing Maven/Git/decompile/platform-pipeline machinery and indexes its `SourceProviderView::searchable_roots`; it never invents a second download/cache system. |
| Cache identity | A semantic, path-portable projection of the effective dependency lock, selected provider declarations/derived checks, Minecraft/Java context, parser fingerprint, and dependency-index format version is hashed. Whitespace-only lockfile changes and absolute worktree/cache paths do not affect identity; any semantic dependency/provider/index-format change does. |
| Cache layout and publication | Store a versioned typed `manifest.json` and authenticated `payload.ndjson` stream beneath `$sfm-cache/symbol-index/v3/<identity>/`. Resolve the portable path through `CacheHome`, acquire a scoped cache lock, stream/hash/validate a sibling prepared payload, publish through a sibling temporary directory, then atomically replace the current artifact. Failed or cancelled refreshes leave the previous valid index untouched. |
| Query completeness | Reports have independent `outcome` and `completeness`. A missing/stale/partial dependency index may return live SFM matches but uses `completeness: incomplete` and process status `5`; it may never claim authoritative no-match. Existing success/no-match/ambiguous statuses remain `0/2/3` only when coverage is complete. |
| Source precedence/provenance | Live SFM declarations take precedence over dependency declarations for the same source identity; distinct competing definitions remain ambiguous. Every span records workspace or dependency origin. Dependency report paths use `dependency/<dependency>/<component>/<provider>/<relative-path>`, never machine-local absolute paths. |
| Lifecycle UX | `symbol index show --branch <branch>` is read-only and prints typed ready/missing/stale/partial state plus expected identity, concrete manifest/payload paths, counts, and missing inputs. `symbol index refresh --branch <branch>` acquires required preferred sources and rebuilds. Missing/stale query diagnostics render an exact typed refresh command with Figue `ToArgs`; source-acquisition failures also reuse typed acquisition recommendations. |
| Storage implementation | Phase 0.8 uses versioned Facet record models behind `DependencySymbolIndexStore`, but the v3 payload is an authenticated routed NDJSON stream (`sfm.dependency-java-symbol-index-stream/2`) rather than one retained Facet collection. Each definition/usage line prefixes its JSON body with kind, owner, name, descriptor, qualified name, and source-set routing fields. The store hashes, copies, probes, and validates the prepared payload incrementally; query loading scans routes with one reusable byte buffer and Facet-decodes only matching full definitions/usages while retaining only the compact resolution vocabulary required by live SFM sources. A regression places invalid JSON behind unmatched routes to prove unrelated bodies are not decoded. Format changes remain behind manifest/index fingerprint bumps without changing command/report contracts. |
| Parser memory boundary | Dependency refresh and live branch queries run type, member, and usage passes in at-most-32-source shards through short-lived instances of the current executable and a private line-based request/output protocol. Shared analysis context is written once; requests carry only pass, paths, and file records. Workers emit tagged definition/paired-resolution/usage/diagnostic records. The parent copies records with one reusable byte buffer and performs no Facet decode during refresh. A compact versioned six-column TSV carries only Java identity and source-set visibility between passes; workers stream it with a reusable buffer and retain only types named in the source plus fields/methods with both owner-type and dot-qualified member evidence (constructors use owner-name evidence). Failed aggregate shards bisect deterministically to one source. On Windows both coordinator and each child have independent 1.5 GiB Job Object process-memory ceilings; children are kill-on-close, and worker/pass output also has explicit byte ceilings. Because per-process Job Objects do not impose a combined machine budget, all memory-intensive live branch queries acquire one cancellable cache-scoped lock before scanning/indexing; concurrent top-level CLI invocations serialize instead of running native parsers simultaneously. Isolated tiny-root scenarios do not take that lock. The bound is evidence-based: `LevelRenderer.java` peaks near 546 MiB and `BlockModelGenerators.java` near 1.106 GiB with combined dependency resolution. Each shard retains at most eight deterministic representative diagnostics plus a suppressed count. Full spans/hashes live only in `payload.ndjson`; the parent calls no shell, Gradle task, public worker command, or external downloader. |

**In scope:** command migration and alias; typed symbol enumeration/glob;
dependency source acquisition integration; lockfile-pinned index identity,
manifest, storage, locking and atomic publication; index status/refresh commands;
live-workspace plus cached-dependency query composition; report completeness and
source provenance; scenarios, docs, help, and a real `MultiLineEditBox` smoke.

**Out of scope:** source mutation; SFM-source persistence/incremental watching;
JDK source indexing; a general bytecode parser for components lacking declared
source/decompile providers; inheritance/dispatch completeness beyond what the
combined source index proves; Gradle; propagation; Minecraft UI; and release
review equivalence. Missing capabilities remain explicit diagnostics.

### Parallel work map

- **Track A — command/discovery:** `0.8.1` and the live-source portion of
  `0.8.2` can proceed together in the symbol CLI/report/scenario surface.
- **Track B — immutable index store:** `0.8.3` can proceed independently in new
  identity/layout/manifest/store modules with synthetic source trees.
- **Track C — source-provider adapter:** the reusable acquisition/preflight and
  typed-recommendation extraction in `0.8.4` can proceed beside Track B without
  changing semantic index code.
- **Integration gate:** `0.8.5` begins only after A/B/C contracts compile. One
  owner integrates precedence, provenance, completeness, and schema migration;
  `0.8.6` is the shared closeout rather than a parallel write surface.

### [x] 0.8.1 Migrate to the verb-first command surface

**Work:**

- Replace the Figue `Definition` variant with canonical `ShowDefinition` and
  replace the nested `Usage(List(...))` tree with canonical `ListUsages`.
- Add `#[facet(args::alias = "list-usage")]` to the `ListUsages` variant so
  both spellings parse to the same typed value, invocation path, help model,
  and output. Do not implement the alias with duplicate match arms.
- Add `List` and nested `Index { Refresh, Show }` command shapes now so
  `symbol help list --short` exposes the final verb-like surface.
- Remove the Phase 0 `definition` and `usage list` forms; update all scenario
  commands, docs, parser/help snapshots, examples, and error suggestions.
- Preserve `rename` and `move` argument/mutation-mode contracts unchanged.

**Validation:** Figue parser tests prove canonical and alias argv produce the
same `SymbolCommand::ListUsages`; `ToArgs` renders the canonical spelling;
`symbol help list --short` lists `show-definition`, `list-usages`, `list`,
`index refresh`, `index show`, `rename`, and `move`; retired forms fail with a
useful nearby-command suggestion.

### [x] 0.8.2 Add typed symbol enumeration and canonical-selector globs

**Work:**

- Add a small typed `JavaSymbolGlob` parser/matcher with only the confirmed
  `*` and `?` behavior. Match against `JavaSymbolIdentityOutput`'s canonical
  exact-selector spelling, not source text, paths, display labels, or
  descriptors interpreted separately.
- Add versioned `sfm.symbol-list/1` output carrying context, dependency-index
  status/identity, optional original pattern, stable symbol summaries,
  definition spans/origins, confidence, diagnostics, outcome, and
  completeness. Text, JSON, and CSV preserve the same report identity.
- No pattern enumerates all known symbols. Sort by canonical selector, kind,
  source origin, path, and span; deduplicate only identical definitions.
- Build the first scenario against isolated tiny sources so list semantics do
  not depend on a machine cache. Cover all, prefix/suffix/infix `*`, `?`, zero
  match, nested `$` (PowerShell-quoted), fields, overloaded methods, and stable
  bytes under reversed input order.

**Validation:** focused glob/report tests plus public `command.ps1` scenarios
prove that discovery returns selectors accepted unchanged by
`show-definition` and `list-usages`.

### [x] 0.8.3 Build the immutable dependency-index identity and store

**Work:**

- Extract a `DependencySymbolIndexIdentity` from the same effective lockfile
  and selected branch/toolchain context used by analysis. Include dependency,
  component and preferred-provider identity; provider-derived source checks;
  Java/Minecraft context; parser fingerprint; and an explicit store/index
  format version. Exclude formatting and absolute local paths.
- Add `DependencySymbolIndexManifest` with schema, identity, creation metadata,
  source inputs and statuses, portable origins, definition/usage/diagnostic
  counts, payload hash/size, completeness, and format fingerprint.
- Add a `DependencySymbolIndexStore` that computes portable/concrete paths,
  probes ready/missing/stale/partial status without writing, validates manifest
  identity and payload hash before load, and atomically publishes under a
  scoped artifact lock. Never deserialize or use a stale payload.
- Serialize dependency index data through typed Facet models; keep store format
  behind the manifest version. Record refresh duration, payload size, warm load
  duration, and representative query duration in completion notes.

**Validation:** synthetic tests prove semantic lock/provider/index changes
invalidate identity; whitespace and relocated cache/worktree paths do not;
corrupt/truncated payloads are rejected; cancellation/failure preserves the
last valid index; concurrent refresh cannot publish mixed files.

### [x] 0.8.4 Implement `symbol index refresh|show` by reusing source acquisition

**Completion notes (2026-08-09):** Extracted the existing provider preflight,
selection, acquisition, and typed recommendation paths and reused them from the
new index lifecycle commands. The production 1.19.2 refresh acquired/indexed
7,613 source files through the declared providers, then atomically published a
613,428,113-byte routed payload with 117,694 definitions, 632,052 usages, and
5,361 retained diagnostics. The manifest records semantic identity
`blake3:7f259c18738075c2a9e7f034b48e0bcacb37c2a9147c39b9dc6f3e2aaa940d17`,
payload hash
`blake3:5d34e2ade2a537b6b7a2f0f108625a04133697d4b228902afc21c09c185454f9`,
and 628,606 ms refresh duration. Coverage is deliberately `partial` because
lockfile components without usable source providers remain visible; warm
`index show` validated and reported the artifact in 1.425 seconds with status
5 rather than hiding those missing inputs. Before refresh, the same command
reported `missing`, process status 5, the expected identity/path, and an exact
Figue-rendered `symbol index refresh --branch 1.19.2` recommendation.

The final v3 implementation uses an authenticated routed NDJSON stream and
short-lived private worker passes with deterministic shard bisection. The
parent streams/copies worker records without retaining the complete dependency
AST or decoded payload. Synthetic identity/store/publication/provider tests,
private-worker schema tests, and the full library suite pass; no shell, Gradle,
or recursive public CLI acquisition path was introduced.

**Work:**

- Extract reusable source preflight, preferred-provider selection,
  acquisition coordination, and typed recommendation rendering from
  `dependency source acquire/search`; keep those public commands' behavior and
  tests unchanged.
- `symbol index refresh --branch` resolves the branch's active dependency
  components, acquires each preferred declared provider when missing/stale,
  indexes its searchable roots, and publishes one immutable dependency index.
  Components with no usable source/decompile provider remain explicit
  unavailable inputs and make the manifest partial rather than disappearing.
- Refresh never invokes a shell, Gradle, ripgrep, or a public recursive CLI
  command. The parent calls shared Rust acquisition APIs with cancellation and
  normal cache locking. For bounded parser memory only, it starts the current
  executable with a private versioned typed worker request for fixed-size
  source shards; this protocol is not user-facing and cannot acquire sources
  or publish the cache. Platform-pipeline acquisition runs once even when
  Minecraft and loader components share it.
- `symbol index show --branch` performs no acquisition or writes. Its typed
  report includes expected identity, readiness/completeness, concrete index
  directory/manifest/payload paths, counts, source-provider statuses, and
  generated refresh/acquisition command strings.
- Construct recommendations as typed `Cli` values and use Figue
  `ToArgs::to_args_string_with_current_exe`; tests round-trip the produced argv.

**Validation:** fixture inventories cover acquired, missing, stale, partial,
provider priority, shared platform pipeline, unavailable provider, cancellation,
exact command recommendations, private-worker schema rejection, deterministic
shard merging, and bounded per-worker source counts. Existing dependency-source
tests remain green.

### [x] 0.8.5 Compose live SFM and cached dependency symbols for every query

**Completion notes (2026-08-09):** Branch queries now route-scan only relevant
dependency records, pass a compact external-resolution TSV to short-lived live
SFM parser workers, and merge the two typed bodies with live-definition
precedence. Definition and list queries run type/member passes; usage queries
add the usage pass. Resolution filtering retains dependency types named in live
source, constructors whose owner is named, and fields/methods only when both
the owner and dot-qualified member are evidenced. Exact imports/descriptors,
source-set visibility, dependency provenance, incomplete status 5, and stale or
missing recommendations survive composition.

The production `*MultiLineEditBox*` list query completed in 54.0 seconds with a
measured 664 MiB parent peak, 808 MiB largest worker, and about 1,405 MiB
combined peak; allocation-free routed glob matching reduced the dependency-only
glob path from about 3.9 GiB/8.9 seconds to 288 MiB/1.9 seconds. An exact
definition query returned the one Minecraft declaration at
`dependency/forge/userdev/loader-pipeline/net/minecraft/client/gui/components/MultiLineEditBox.java`.
Canonical `list-usages` and aliased `list-usage` each completed in about 109
seconds, returned 8 usages, and produced byte-identical 11,016-byte JSON with
SHA-256 `2AA53D2EA05BAF15BE39980DC9E48BA16D7DAEF6E6C025120AEF94F9796C7626`.

A temporary unique field added to `SFMPerformanceTweaks.java` appeared as one
resolved live definition on the next 53.0-second list query without refreshing
the dependency index; the exact line was then removed and the Java file had no
remaining diff. Two accidentally concurrent real usage proofs exposed that
independent per-process Job Object ceilings did not bound aggregate machine
memory and produced a native Windows access violation. Branch queries now
acquire one cancellable cache-scoped live-query lock before scanner/worker work;
a regression proves the second query waits and proceeds after release while
isolated scenarios remain uncoordinated.

**Work:**

- Introduce a query-time symbol universe that builds the selected SFM/custom
  workspace index live, probes/loads only the exact current dependency index,
  and combines definitions/usages without copying dependency sources into the
  workspace or persisting SFM data.
- Serialize memory-intensive branch-mode query indexing across top-level CLI
  processes with one cancellable cache-scoped lock. Per-worker and parent Job
  Object limits are necessary but insufficient because Windows enforces them
  per process; two simultaneous real usage queries must wait rather than run
  native parser workers concurrently. Keep isolated tiny-root scenarios free
  of this machine-wide coordination.
- Extend type lookup so explicit imports such as Minecraft `BlockPos` resolve
  against cached dependency declarations. This must allow SFM methods with
  dependency types to retain exact JVM descriptors while preserving confidence
  and diagnostics; spelling alone is still not `Resolved`.
- Define workspace-versus-dependency precedence and ambiguity exactly as in the
  confirmed design. Attach typed source origin to definitions, usages, and
  diagnostics and emit stable dependency-relative paths.
- Add `complete|incomplete` independently from command outcome and bump changed
  definition/usage schemas to `/2`. Status `5` means incomplete coverage;
  success/no-match/ambiguous statuses `0/2/3` apply only to complete reports.
- Apply the same universe/completeness contract to `show-definition`, both
  usage spellings, and `list`. If the index is missing/stale, return live SFM
  rows plus diagnostics and the typed refresh command; never load the stale
  bytes or report an authoritative no-match.

**Validation:** deterministic synthetic integration tests cover a dependency
type referenced by live SFM-like source, external method descriptors, duplicate
source/dependency definitions, dependency-only usages, missing/stale index,
complete no-match, and identical canonical/alias output.

### [x] 0.8.6 Prove the dependency-backed vertical slice and update guidance

**Completion notes (2026-08-09):** Canonical help, dependency lifecycle, real
list/definition/usage/alias queries, live-edit freshness, measured
memory/timing, and durable user guidance are complete. Focused and synthetic
tests cover glob routing, exact selector separation, lockfile identity/store
validation, provider acquisition recommendations, routed stream filtering,
live/dependency composition and precedence, incomplete status, alias parsing,
worker bisection, and top-level live-query serialization.

The required `check-all.ps1` passed dependency policy, formatting, Clippy with
warnings denied, build, 450 library tests (3 intentionally ignored), and all 7
scenario-harness tests. The first sandboxed run reached 449 passing tests before
the documented Codex `os_error=5` denied the ripgrep temporary-directory
subprocess; the identical script passed with ordinary Windows temp/process
access. `git diff --check` and format checks are clean. No Gradle, propagation,
game launch, or persistent Java-source mutation occurred.

**Work and evidence:**

- Update `docs/java symbol analysis.md`, command help/examples, and every
  existing scenario to the canonical names; document `list-usage`, glob rules,
  cache lifecycle, source acquisition, status `5`, stable origins, and the
  cached-dependency/live-workspace split.
- Run `symbol index show --branch 1.19.2` before refresh and preserve the typed
  missing/stale evidence and generated recommendation.
- Run `symbol index refresh --branch 1.19.2`. For the current missing Minecraft
  source tree, verify refresh reuses the existing `minecraft-pipeline`
  acquisition equivalent to `dependency source acquire minecraft/main
  --provider any --branch 1.19.2`; do not add a second platform pipeline.
- Run `symbol index show --branch 1.19.2` after refresh and record the concrete
  path, semantic identity, source counts, payload size, refresh time, warm-load
  time, and completeness.
- Prove all of these real commands in text and JSON without Gradle or a game:

  ```powershell
  sfm-propagate-changes.exe symbol list '*MultiLineEditBox*' --branch 1.19.2
  sfm-propagate-changes.exe symbol show-definition net.minecraft.client.gui.components.MultiLineEditBox --branch 1.19.2
  sfm-propagate-changes.exe symbol list-usages net.minecraft.client.gui.components.MultiLineEditBox --branch 1.19.2
  sfm-propagate-changes.exe symbol list-usage net.minecraft.client.gui.components.MultiLineEditBox --branch 1.19.2
  ```

- Prove a live SFM edit is visible on the next query without refreshing the
  dependency index, then restore the fixture/edit safely. Prove a semantic
  lockfile identity change makes the prior index stale in a synthetic test and
  generates a round-trippable refresh recommendation.
- Run focused tests, `cargo test --all-features --test java_analysis_scenarios`,
  the existing source-audit regressions, and required `check-all.ps1`. Do not
  run Gradle or propagation.

**Completion criteria:** `help list --short` is verb-like; both usage spellings
are equivalent; exact selectors are discoverable through typed glob listing;
`MultiLineEditBox` lists and resolves from the cached Minecraft source index;
queries always use live SFM sources plus only an identity-valid dependency
index; stale/missing coverage is explicit with typed recommendations; cache
location/lifecycle is inspectable; all validation passes; no Java source is
mutated.

### Phase 0.8 risk register

| Risk | Guardrail and proof |
| --- | --- |
| A wildcard silently weakens exact navigation/refactoring safety. | `JavaSymbolGlob` exists only in `symbol list`; exact commands continue to reject wildcard selectors. |
| A stale dependency index returns convincing but wrong answers. | Exact semantic identity and payload-hash validation precede load; incomplete status `5` replaces false success/no-match and includes a typed refresh command. |
| Refresh duplicates source downloading/build logic. | Shared provider selection/acquisition is extracted from existing dependency-source commands and regression-tested through both entry points. |
| Refresh corrupts a previously useful cache. | Scoped lock, sibling temporary publication, manifest/payload validation, and atomic replacement preserve the previous index on failure/cancellation. |
| Machine paths make indexes unreproducible or unshareable. | Identity and persisted origins are portable; absolute paths appear only in local `index show` presentation, never identity or payload provenance. |
| Caching SFM sources creates difficult invalidation bugs. | SFM/custom roots are always read and indexed live; only immutable dependency-source inputs are persisted. |
| Indexing all dependency sources is too slow or large. | Store/load/query timing and payload size are mandatory evidence; versioned storage abstraction permits later format/sharding changes without CLI/schema drift. |
| Concurrent real queries exceed memory despite per-process Job Object limits. | One cancellable cache-scoped live-query lock serializes branch-mode scanner/worker pipelines across CLI processes; a regression proves a second invocation waits and proceeds after release, while isolated tiny-root scenarios remain unblocked. |
| Missing provider sources disappear from results. | Manifest and every query report enumerate unavailable inputs and mark completeness incomplete. |

## Phase 0.9 — Single-parse streaming live-definition index (complete)

Complete `0.9.1` through `0.9.6` as one vertical slice before beginning Phase
1. This phase addresses the measured latency of live branch definition queries
without introducing a persistent SFM-source index. It replaces the production
live `show-definition` path only after the new single-parse fact/link pipeline
has proved equivalent to the completed Phase 0.8 implementation.

The motivating evidence is the installed command:

```powershell
sfm-propagate-changes.exe symbol show-definition DiskItem --branch 1.19.2
```

On the 2026-08-10 development machine (32 logical CPUs, approximately 64 GiB
RAM), one measured run took 9,573 ms; user-observed runs took about 11 seconds.
The selected workspace contained 1,318 Java files, split into 42 shards of at
most 32 files. A type query still launched 42 `Types` workers followed by a
global barrier and 42 `Members` workers. The first pass occupied about 5.1
seconds from query start and the member pass ended about 9.4 seconds after
start. `show-definition` does not request usages, so those 84 serial child
processes and the second parse of every live source are the immediate target.

### Phase 0.9 confirmed design

| Area | Decision |
| --- | --- |
| Production scope | Optimize every selector accepted by `symbol show-definition` against live branch/custom sources. Keep `symbol list-usages`, dependency-index refresh, and persistent dependency payload construction on the Phase 0.8 implementation in this slice; record their migration as follow-up rather than coupling it to the `DiskItem` latency proof. |
| Parse unit | A selected Java source is read and parsed no more than once per query. One worker invocation emits a complete, versioned `JavaFileFacts` bundle for each file: source identity/hash/set, package/import context, type declarations, raw field/method/constructor signatures, raw references required by declaration linking, and parse diagnostics. It does not emit an already-resolved Phase 0.8 index fragment that requires a second pass. |
| Streaming topology | The coordinator starts a bounded producer set and a linker consumer together. As soon as one shard completes, its file-fact bundles are validated and admitted to the linker while other workers remain active. There is no all-types, all-members, or all-files barrier between parsing and linking. |
| Link and seal semantics | The linker interns declarations immediately, attempts resolution against live facts already seen plus the immutable dependency-resolution vocabulary, and retains unresolved/ambiguous candidates keyed by the names that can wake them. New declarations retry only affected candidates. End-of-snapshot `seal()` performs completeness-dependent decisions once: stable ambiguity/uniqueness, final unresolved diagnostics, source-set visibility enforcement, canonical ordering, and index fingerprint. It never reparses a source. |
| Query semantics | A class-only query may collect a matching type early but still waits for `seal()` before claiming uniqueness. Field/method/constructor selectors resolve their raw signatures as declarations arrive and likewise wait for seal. The typed output schema, status, completeness, provenance, source hashes, short-name ambiguity rules, exact-selector precedence, and dependency behavior remain unchanged. Definition fingerprints cover the canonical query-visible definitions and diagnostics; the unrelated-diagnostic notice does not embed an implementation-dependent suppressed count. This stabilization was required for byte equivalence after the old bounded sharding and new linker legitimately collected different quantities of diagnostics that neither report exposed. |
| Worker supervision | Use a bounded asynchronous/scoped fan-out abstraction (a Tokio `JoinSet` plus channel is acceptable, but not required) with an initial default of four active child workers. Begin with one balanced shard per worker so process startup is not again the dominant cost; hard limits and deterministic bisection are the resource boundary. The supervisor owns every child handle and memory guard until exit, stops scheduling on first infrastructure failure/cancellation, kills and waits for all active children, then returns the first error with cleanup context. No detached tasks or child processes are allowed. |
| Memory boundary | Retain the 1.5 GiB per-worker ceiling and output caps. Concurrent Windows children share a kill-on-close Job Object with both process-memory and 6 GiB aggregate-job ceilings for the initial four-worker bound. A test queries Win32 to verify all flags and values. Empirical implementation evidence rejected retroactively assigning the already-running coordinator to the refresh-only 1.5 GiB parent job: the real query then terminated with Windows `0xC0000005`. The live coordinator therefore remains outside that job, retains bounded worker outputs plus the machine-wide live-query lock, and records a safely-at-process-creation parent cap as follow-up. Platforms without an enforceable aggregate child limit run one worker. |
| Failure bisection | A failed multi-file shard is deterministically bisected and its halves are requeued through the same bounded supervisor. Successful siblings are retained; no source is reparsed merely because another shard failed. A one-file failure terminates the query after active children have been cleaned up. |
| Determinism | Worker completion order is deliberately nondeterministic; published definitions and diagnostics are not. Fact bundles carry stable shard/file sequence identities. Linker output is canonicalized by semantic identity, source set, report path, and span at seal. Reversed/delayed completion tests must produce byte-identical typed reports and fingerprints. |
| Equivalence oracle | Keep the Phase 0.8 multi-pass builder callable only from tests or an explicit developer diagnostic during this phase. Compare canonical reports on the existing scenarios plus generated import, forward-reference, overload, nested-type, source-set, and ambiguity cases. Do not silently fall back to the old path in production when the new path disagrees. |
| Persistence boundary | Reuse immutable dependency resolution data within the query, but do not persist `JavaFileFacts`, add a live source cache, watch files, or start a daemon. A subsequent CLI invocation rereads current SFM sources by design. |
| Latency acceptance | Preserve raw timing evidence and compare installed release builds. Over five warm real `DiskItem` runs on the same machine and unchanged source/index state, Phase 0.9 targets median wall time at or below 4.0 seconds and no run above 6.0 seconds. If the target is missed, do not call the phase complete: record worker/link/dependency-scan timing and continue against the measured dominant stage. |

**In scope:** fact schema and extraction; streaming forward-reference linker;
bounded parallel child supervisor; aggregate memory/cancellation/failure
lifecycle; deterministic bisection/fan-in; production integration for
`show-definition`; equivalence, instrumentation, real latency evidence,
documentation, full CLI checks, and installed-binary verification.

**Out of scope:** persistent live SFM indexes, file watching, a daemon,
incremental reuse between CLI invocations, usage collection, dependency-index
refresh migration, Gradle, Minecraft/game launch, Java mutation, propagation,
and changing any public symbol report schema or selector semantics.

### Phase 0.9 parallel work map

- **Track A — file facts:** owns new fact-model/extraction modules and focused
  parser tests. It does not edit the worker supervisor or production CLI route.
- **Track B — bounded supervisor:** owns a new generic/private live-worker
  supervisor module and lifecycle tests using synthetic child commands. It does
  not edit Java extraction, linker logic, or symbol CLI routing.
- **Track C — linker and oracle:** one owner builds the streaming linker,
  generated-corpus equivalence harness, and seal semantics after the fact
  contract is frozen. This track owns semantic integration decisions.
- **Integration owner:** alone edits shared module exports, the private worker
  entry point, `build_java_index_sharded` routing, and `symbol_index_cli.rs`.
  It can proceed on instrumentation and contract tests while A/B run, then
  reviews and integrates both tracks. No two agents edit the same file.

### [x] 0.9.1 Freeze evidence, contracts, and equivalence fixtures

**Work:**

- Preserve the 2026-08-10 baseline in a repository-adjacent benchmark note or
  test artifact, including exact command, source-file/shard counts, installed
  executable identity/build mode, wall time, and observed pass timing.
- Add query instrumentation for source discovery, dependency-route scan, worker
  startup/parse, fact transfer, linker admission, seal, and report rendering.
  Logs remain on stderr and do not change typed stdout.
- Define the versioned `JavaFileFacts` and linker input/output contracts in Rust
  types before parallel implementation. Include source hash, stable file/shard
  identity, source-set visibility context, raw type/member/reference data, and
  bounded diagnostics.
- Add an equivalence harness capable of invoking the old and new builders over
  the same in-memory or tiny-root workspace and canonicalizing only explicitly
  volatile timing fields. The old builder is an oracle, not a production
  fallback.

**Validation:** focused tests reject wrong fact schemas, missing/duplicate file
identities, mismatched hashes, and out-of-snapshot records; instrumentation
tests prove every named stage appears while JSON stdout remains unchanged.

**Completion criteria:** the baseline and typed contracts are durable; Track A,
Track B, and Track C have disjoint write surfaces; and equivalence can report a
field-level mismatch before the new path is selected in production.

**Evidence (2026-08-10):** `docs/java symbol analysis.md` records the 9,573 ms
baseline, 1,318 sources, 42 shards/pass, and 84 old child invocations.
`live_query_timing.rs` emits stable stage events. `JavaFileFacts` is schema
`sfm.java-file-facts/1`, and `definition_equivalence.rs` reports the first
typed field mismatch while the legacy route is available only through the
developer environment switch.

### [x] 0.9.2 Parse each live source once into reusable facts

**Work:**

- Extract a `JavaFileFacts` builder from `JavaSyntaxFile`/`JavaSymbolIndex`
  internals. Traverse one retained syntax tree to collect package/import/type
  declarations, raw member signatures, declaration-reference evidence, and
  parse diagnostics needed by definition queries.
- Keep raw type syntax and lookup context until the linker can resolve it;
  missing declarations in an early bundle are pending references, not guessed
  unresolved diagnostics.
- Change the private live worker protocol to return one fact bundle per source
  in the shard. Validate that every requested source has exactly one result and
  that no unrequested source appears. Keep request/output byte ceilings.
- Add a test-only parse counter around the extraction boundary. The count for a
  successful definition snapshot equals the selected source count even for
  field/method selectors and forward references.

**Validation:** parser tests cover top-level/nested types, fields, constructors,
overloaded methods/descriptors, imports, same-package names, cross-source-set
visibility, parse gaps, hashes, and reversed file order. A multi-file test
proves one parse per file and no type/member TSV intermediate.

**Completion criteria:** a worker produces complete reusable facts for every
requested live source after exactly one parse, without requiring declarations
from shards that have not completed.

**Evidence (2026-08-10):** `java_file_facts.rs` performs one Arborium parse and
one declaration walk for owned type/member/import/reference facts; parser
counter, schema/hash/span, nested-type, import, visibility, diagnostic, and
Facet round-trip tests pass. The private NDJSON worker emits one validated
record per stable source sequence and rejects missing/duplicate protocol data.

### [x] 0.9.3 Link facts incrementally and seal deterministically

**Work:**

- Add a query-local streaming linker that admits complete file bundles one at a
  time, indexes declarations immediately, and resolves raw signatures against
  admitted live declarations plus filtered immutable dependency definitions.
- Key pending candidates by package/import/simple/qualified names so only
  candidates affected by a newly admitted declaration are retried. Preserve
  source-set visibility and never resolve by spelling alone when ambiguity
  remains.
- Implement an explicit `seal(expected_files)` transition. It rejects missing
  or duplicate files, resolves remaining candidates once, emits final bounded
  unresolved diagnostics, decides exact/short-name uniqueness or ambiguity,
  canonicalizes output, and computes the same semantic fingerprint without
  reading or parsing source files.
- Prove the consumer observes at least one admitted bundle while producers are
  still active; a test that only sees data after all workers finish is a global
  barrier and fails.

**Validation:** incremental-order tests cover declaration-before-reference,
reference-before-declaration, ambiguous short names, dependency types, member
descriptors, nested classes, visibility rejection, duplicate/missing facts,
and randomized/delayed arrival. Every order produces canonical equivalent
output after seal.

**Completion criteria:** linking overlaps fact production, forward references
resolve without reparsing, and only seal waits for snapshot completeness.

**Evidence (2026-08-10):** `definition_linker.rs` retains name-keyed watchers,
revises early candidates after late declarations, enforces visibility, and
seals an exact contiguous sequence. Reordered-arrival, late ambiguity,
dependency type, descriptor, and missing-sequence tests pass. The installed
trace admitted shard `660..990` at 545 ms while three producers were active;
fan-out/link/seal completed at 934 ms.

### [x] 0.9.4 Supervise bounded parallel workers without exceeding lifecycle limits

**Work:**

- Replace serial shard launching on the new live-definition route with a
  bounded supervisor. Start with four active workers and make the bound visible
  in tracing/test observations; do not derive it implicitly from task count.
- Own child handles and Job Object guards in the supervisor. On cancellation,
  channel/decoder failure, spawn failure, output overflow, or a one-file worker
  failure, stop scheduling, kill all active children, wait for each, close the
  channel, and return the first failure plus cleanup diagnostics.
- Put concurrent Windows workers in one shared kill-on-close job with 1.5 GiB
  per-process and 6 GiB aggregate worker-memory limits. Preserve the parent
  ceiling and machine-wide live-query lock. Add a documented conservative
  non-Windows/unsupported-platform policy.
- Requeue deterministic halves after a multi-file worker failure without
  exceeding the active bound. Preserve successful outputs and stable shard
  identities; bisection order must not define final output order.

**Validation:** synthetic supervisor tests record peak active workers, delayed
completion, reversed completion, cancellation, spawn/decode/output failures,
multi-file bisection, one-file terminal failure, and process cleanup. On
Windows, query Job Object configuration to prove both process and aggregate
limits are active; after every failure no test child remains running.

**Completion criteria:** useful worker concurrency occurs with a hard bound,
all process/memory guards outlive their children, and every exit path joins or
kills-and-waits all active work.

**Evidence (2026-08-10):** scheduler tests prove a peak of four, deterministic
bisection, terminal one-file failure, reversed completion, duplicate/unknown
completion rejection, and exact partition seal. A real long-running child test
proves cleanup kills, waits, and empties ownership. Windows queries the shared
Job Object and verifies kill-on-close, active-process four, 1.5 GiB process,
and 6 GiB aggregate flags/values. Unsupported platforms select one worker.

### [x] 0.9.5 Integrate the new path for every `show-definition` selector

**Work:**

- Route live branch/custom-root definition queries through the parallel
  fact/link pipeline. Continue composing only the current identity-valid
  dependency index and retain status `5` for incomplete dependency coverage.
- Support class, nested class, field, constructor, and method descriptor
  selectors, including unqualified/suffix lookup and exact-match precedence.
  Do not special-case `DiskItem` or class-only selectors.
- Run the old and new builders through the equivalence harness across existing
  scenarios and generated cases. Compare canonical typed output, source-set
  context, definitions, spans/hashes, diagnostics, provenance, ambiguity,
  completeness, and fingerprints. Resolve every mismatch explicitly.
- Keep the old multi-pass builder available only to tests/developer diagnostics
  needed by the equivalence proof. Production disagreement is an error during
  development, never a silent fallback.

**Validation:** all definition scenarios pass on the new production route;
the equivalence matrix is byte-identical after permitted timing normalization;
tests prove one parse per live source, linker activity before producer finish,
and deterministic output with worker concurrency greater than one.

**Completion criteria:** every public `show-definition` selector uses the new
single-parse streaming path with Phase 0.8 behavior preserved.

**Evidence (2026-08-10):** branch queries with ready/partial or unavailable
dependency indexes and isolated custom roots select the fact/link path for
every definition selector. The ready/partial route retains all compact
dependency types as resolution vocabulary without rereading live sources.
Real release-build class and method reports were byte-identical to the Phase
0.8 oracle, including status 5, spans, hashes, completeness, diagnostics, and
fingerprints. SHA-256 values are recorded in `docs/java symbol analysis.md`.

### [x] 0.9.6 Prove latency, correctness, and operational closeout

**Work:**

- Build an optimized executable from the current source, install it with
  `platform/cli/sfm-propagate-changes/install.ps1`, and run five warm
  `DiskItem` definition queries against unchanged 1.19.2 sources/index state.
  Record every wall time plus source, worker, peak-concurrency, linker, seal,
  dependency-scan, and render timing.
- Require median wall time at or below 4.0 seconds and every run at or below
  6.0 seconds on the baseline machine. If missed, use stage evidence to improve
  the dominant work before completion; do not lower the target retroactively
  without recording a new user decision.
- Run focused fact/link/supervisor/oracle tests, all Java-analysis scenarios,
  and `check-all.ps1`. Verify cancellation/failure leaves no worker process or
  temporary live-query artifact and does not alter the immutable dependency
  index.
- Update `docs/java symbol analysis.md` with the live-query topology, bounds,
  persistence boundary, diagnostics, and measured performance. Record follow-up
  work for applying the same architecture to `list-usages` and dependency
  refresh, without marking those deferred routes complete.

**Validation:**

```powershell
cargo test --all-features java_analysis
cargo test --all-features --test java_analysis_scenarios
.\check-all.ps1
.\install.ps1
sfm-propagate-changes.exe symbol show-definition DiskItem --branch 1.19.2
```

**Completion criteria:** `0.9.1`–`0.9.5` have durable evidence; the real command
meets the latency distribution; each live source is parsed at most once;
linking overlaps production; worker count/memory are bounded; failure and
cancellation clean up; reports remain deterministic/equivalent; full checks
pass; documentation and installed executable match the implementation; and no
Java source, Gradle flow, dependency payload, or other Minecraft branch is
changed.

**Evidence (2026-08-10):** `check-all.ps1` passed 481 unit tests with three
documented opt-in ignores plus all seven scenario-harness tests; focused Java
analysis passed 98 tests with two opt-in ignores; strict all-feature/all-target
Clippy, formatting, build, scenario snapshots, and `git diff --check` passed.
`install.ps1` installed the checked release executable. After one warm-up, five
installed `DiskItem` runs took 2,237, 2,163, 2,128, 2,101, and 2,088 ms
(median 2,128 ms; maximum 2,237 ms), all with expected status 5. Installed
class and method reports remained byte-identical to the legacy oracle, and the
post-query process audit found zero surviving `sfm-propagate-changes` workers.

### Phase 0.9 risk register

| Risk | Guardrail and proof |
| --- | --- |
| Parallel execution hides the same two global barriers behind concurrent loops. | `0.9.3` requires linker admission while producers are active and tests fail if all facts arrive only after producer completion. |
| Forward references become order-dependent. | Pending candidates wake on declaration admission; randomized/delayed arrival must produce byte-identical sealed output. |
| Four 1.5 GiB workers plus parent exceed a safe machine budget. | Shared Windows Job Object applies a 6 GiB aggregate worker cap as well as per-process caps; the existing global query lock remains, and unsupported enforcement reduces concurrency explicitly. |
| Cancellation or one worker error leaks children. | The supervisor owns every handle/guard, stops scheduling, kills, waits, and is tested with process-liveness assertions on each failure path. |
| A one-parse counter passes while internal code reparses source text. | Extraction owns one `JavaSyntaxFile`; tests instrument the actual parser boundary and prohibit the old type/member worker protocols on the production route. |
| Early match return reports false uniqueness. | Even class-only queries wait for snapshot seal; ambiguity and complete no-match are decided only after every expected file is admitted. |
| The optimized report subtly differs from the current implementation. | The Phase 0.8 builder remains a test oracle over scenarios and generated corpora; production has no silent compatibility fallback. |
| The slice grows into persistent indexing or usage migration. | Scope explicitly limits the production switch to `show-definition`; live facts die with the query and follow-ups remain incomplete. |

## Phase 0.10 — Definition at an editor location and supervised worker bridge

This phase is the CLI-side dependency of C-4 through C-6 in
`docs/tasks/contextual input actions and addressable explorer plan.md`. It does
not implement Minecraft panels, workspace persistence, keybindings, or result
presentation. It makes the existing symbol engine safely reusable by those
features and keeps direct/manual invocation as an independently testable
contract.

**Goal completion bookkeeping (2026-08-15):** Phase 0.10.1 through 0.10.4 are
complete together with contextual-plan B-2/C-4. The delivered boundary includes
the direct location form, reusable engine entry point, supervised framed worker,
Minecraft provider handoff, installed latency/process-liveness evidence, and
documentation. It excludes F12/Alt+Enter result navigation, live Minecraft UI
proof, fuzzy search, source mutation, propagation, publication, and release.

### [x] 0.10.1 Freeze the definition-at-location request/result and direct CLI form

**Work:** Add versioned Facet `DefinitionAtPositionRequest` and
`DefinitionAtPositionResult` values. A request identifies branch/classpath,
ordered source roots/source-set context, one root-relative document path,
current source text/hash, UTF-aware one-based line/column plus derived byte
offset, dependency-index identity, and request generation. A result identifies
zero/one/many canonical symbols and root-relative definition spans, confidence,
completeness, fingerprints, diagnostics, and typed recovery actions. Extend
`symbol show-definition` with a mutually exclusive location form:

```powershell
sfm-propagate-changes.exe symbol show-definition --source-path <root-relative-java-path> --line <n> --column <n> --branch <branch> [--source-root <path> ...] [--classpath-mode branch|isolated]
```

The existing exact-selector positional form remains unchanged. Location mode
requires source text to be read from its declared root for ordinary manual use;
the typed engine/worker request additionally supports an in-memory overlay.
Invalid mixed selector/location arguments fail in Figue before analysis.

**Validation:** Parser/output tests and scenarios cover both forms, required
flags, Windows/Unicode paths, line/column bounds, UTF-8 byte derivation, CRLF,
unknown root/source set, schema round trips, and stable text/JSON/CSV output.

**Completion criteria:** A human/agent can query an exact source location with
the normal CLI, while the same versioned typed request/result can be carried by
the worker; selector and location modes are unambiguous and no raw token guess
is part of the contract.

**Completion evidence (2026-08-15):** The CLI and shared engine now use
`sfm.definition-at-position-request/2` and
`sfm.definition-at-position-result/2`. Location mode accepts root-relative
`--source-path`, one-based `--line`/`--column`, and optional
`--source-root-id`; Figue rejects mixed selector/location forms. The request
retains root/source-set/address identity, exact source text and hashes,
Unicode-scalar coordinates plus UTF-8 byte offset, branch/classpath/index
context, origin-scoped request generation, and worker-global workspace
generation. An installed direct query resolved `DiskItem.java` line 43,
column 14 to `ca.teamdman.sfm.common.item.DiskItem`; it correctly returned
status 5 with `completeness: incomplete` because dependency coverage is partial.

### [x] 0.10.2 Resolve the symbol at the location through the existing engine

**Work:** Add a reusable engine entry point over the Phase 0.9 fact/link path
and Phase 0.8 dependency index. Resolve the syntax/reference at the exact
location using package/import/owner/source-set/classpath context; support types,
fields, methods/overloads, constructors, nested types, and dependency symbols
to the degree the current index represents them, with explicit unsupported or
ambiguous diagnostics otherwise. Overlay supplied current-document text in
memory for one request and retain disk/overlay hashes; never write it. Preserve
all-source-set visibility and incomplete-index semantics.

**Validation:** Adjacent scenario directories cover imported SFM and Minecraft
types, same simple name in two packages, qualified and member references,
overloads, nested names, declaration self-navigation, whitespace/comments/
strings/no-symbol, invalid positions, source-set visibility, dependency index
ready/missing/stale, current overlay differing from disk, Unicode/CRLF, and
zero/one/many outcomes. Direct location results are deterministic and agree
with exact-selector results when both identify the same symbol.

**Completion criteria:** Definition-at-location is a thin contextual entry to
the existing symbol universe, not a second parser/resolver; exact current text
can be analyzed without mutation; ambiguity/completeness remain truthful.

**Completion evidence (2026-08-15):** `DefinitionAtPositionEngine` reuses the
existing Java facts, linker, source-set visibility, and immutable dependency
index. It supports request-scoped in-memory overlays without writing source,
keeps disk/overlay hashes distinct, and returns root-authoritative spans with
typed stale, invalid-position, unavailable, ambiguous, incomplete, and recovery
outcomes. Adjacent scenarios cover imported/project/dependency types, fields,
methods, constructors, declaration self-navigation, Unicode/CRLF, no-symbol,
ambiguity, overlays, and duplicate relative paths in different roots. Direct
and worker modes consume the same engine and canonical result model.

**User-testing boundary correction (2026-08-16):** The completed scenario set
did not establish branch-JDK source/implicit `java.lang` resolution, lexical
locals/parameters, every annotation/import/member cursor shape, or the Java
resolver-authorization composition used by all live explorer roots. Those gaps
do not invalidate the reusable engine/worker boundary, but they do invalidate
any broader claim that all F12 positions are covered. Phase 0.12 preserves the
reported false negatives as regressions and extends this same engine.

### [x] 0.10.3 Add `symbol serve` with framed requests, reuse, and cancellation

**Work:** Add `sfm-propagate-changes.exe symbol serve --branch <branch>` as a
long-lived worker mode. Reserve stdout for `[u32 little-endian byte length][UTF-8
JSON payload]` frames and stderr for logs. Handshake schemas/capabilities before
queries. Support definition requests, cancellation by request id/generation,
workspace-generation updates, ping/clean shutdown, and typed fatal/nonfatal
errors. Reuse immutable dependency indexes and live source facts by exact
branch/root/content identity within the worker lifetime; invalidate changed
roots/files precisely, bound entries/bytes/workers, and never persist the live
cache after exit. Own and reap all Phase 0.9 child workers on cancellation,
client disconnect, crash, and shutdown.

**Validation:** Protocol tests cover fragmented/coalesced frames, embedded
newlines/NUL/Unicode source text, oversized/malformed frames, schema mismatch,
out-of-order request completion, cancel before/during/after completion,
workspace-generation replacement, stale response ids, cache hit/invalidation,
memory/entry limits, client EOF, child failure, clean shutdown, and no orphan
processes/temp artifacts. Worker and direct engine results are canonical-byte
equivalent after removing transport telemetry.

**Completion criteria:** One supervised process serves many independent
definition requests safely, amortizes reusable setup, never mixes logs with
protocol bytes, invalidates by identity rather than hope, and exits without
leaked children or persistent live-source state.

**Completion evidence (2026-08-15):** `symbol serve` reserves stdout for
little-endian length-framed JSON and stderr/log files for diagnostics. Its
versioned handshake negotiates schemas, capabilities, frame/pending limits,
and workspace identity; definition, cancellation, workspace-generation, ping,
and shutdown frames are covered by fragmented/coalesced/malformed/Unicode/NUL,
late-response, cancellation, crash/restart, and cleanup tests. The worker keeps
a bounded immutable fact cache and at most two content-keyed resolution
surfaces; it re-hashes selected sources for external-edit detection and only
publishes a rebuilt surface after successful completion. Minecraft now has the
provider-neutral request/result adapter, supervised process implementation,
origin-scoped supersession, daemon I/O/state/timer threads, bounded pending
work, typed recovery, privacy-safe telemetry, and deterministic close/reap.

### [x] 0.10.4 Prove interactive latency, cancellation, and handoff documentation

**Work:** Build/install the release CLI, start one worker against 1.19.2, and
measure cold first query plus at least twenty warm definition-at-location
queries spanning same file, another SFM source set, and a dependency source.
Record parse/fact/link/dependency/lookup/render/cache timing, peak workers and
memory, cancellations, and cache identities without raw source/path telemetry.
Target a warm median at or below 250 ms, warm p95 at or below 750 ms, and no
warm run above 1 second on the baseline machine. If missed, profile and improve
the measured dominant stage rather than weakening the target silently. Update
`docs/java symbol analysis.md` and the contextual explorer plan with protocol,
discovery/configuration, lifecycle, privacy, and direct/manual examples.

**Validation:** Run all Java-analysis/scenario tests, strict CLI checks, direct
and worker equivalence, the installed live benchmark, cancellation/process
liveness audit, and `git diff --check`. Do not launch Minecraft or propagate in
this CLI phase; C-4/C-6 own consumer/live-game proof.

**Completion criteria:** The installed worker meets recorded interactive warm
latency and cleanup bounds, documentation is sufficient for the Minecraft
provider to integrate without reading implementation details, all JAVA-32
through JAVA-37 evidence is durable, and Phase 1 remains untouched.

**Completion evidence (2026-08-15):** The installed executable identifies
revision `c32607d2f`. Its cold 1.19.2 query took 3,219.436 ms. Twenty-four warm
queries rotating through `DiskItem`, gametest `SFMGameTestHelper`, and indexed
Minecraft `BlockPos` measured 72.001 ms median, 103.068 ms p95, and 107.290 ms
maximum, passing the unchanged 250/750/1000 ms limits. Peak working set was
501,850,112 bytes; cancellation returned both `cancelled` and
`definition-cancelled`; shutdown was acknowledged; one descendant was observed
and zero leaked. The initial warm implementation measured about 1.8 seconds
because every request re-expanded 117,694 dependency definitions; retaining a
content-keyed immutable resolution surface fixed the measured stage rather than
weakening acceptance. Durable raw evidence is in
`docs/architecture/evidence/symbol-server-installed-probe-1.19.2.json`.
Strict Rust checks, 144 Java-analysis tests (2 ignored), all eight scenarios,
the Java provider/protocol/context tests, the real installed-process Java
integration test, and canonical compile passed. The complete Java suite found
891 tests: 890 passed, zero failed, and the installed-process test was the sole
expected opt-in abort when its properties were absent. `audit --branch 1.19.2`
exited 0 with 30 pre-existing grouped unresolved font-render-rule warnings.

### Phase 0.10 risk register

| Risk | Guardrail and proof |
| --- | --- |
| Location mode weakens exact selector grammar or guesses from a token. | Mutually exclusive Figue input forms and an engine request containing source path/snapshot/position/context; no fallback from one grammar to the other. |
| Overlay text contaminates disk/index state. | Request-scoped immutable overlay keyed by explicit disk/overlay hashes; no writes and post-query disk equality scenarios. |
| Long-lived reuse serves stale facts after edits/root changes. | Workspace generation plus per-file content identities, precise invalidation tests, stale-response rejection, and bounded cache introspection. |
| Framed stdout is corrupted by logs or panics. | Protocol-only stdout writer, stderr tracing, length/schema limits, malformed-frame tests, and typed fatal shutdown frame where possible. |
| Worker mode leaks Phase 0.9 child processes. | One supervisor owns job/process handles and kills/waits on request cancellation, client EOF, panic/failure, and shutdown; process-liveness tests cover every path. |
| “Interactive” remains a multi-second fresh analysis behind a daemon. | Cold/warm stage telemetry and explicit warm median/p95/max acceptance; optimize measured cache/link bottleneck before completion. |
| Transport details become Minecraft action API. | Provider-neutral request/result in the contextual plan; direct engine, CLI, and worker share typed values; Vox remains a replaceable future adapter. |
| Protocol/telemetry leaks source text or absolute workspace paths. | Source content exists only in explicit request frames; default logs/telemetry use request/provider/root ids, hashes, counts, and durations. |

## Phase 0.11 — Rust-owned Arborium Java syntax highlighting

This phase is additive to the completed symbol-analysis engine. It supplies the
Rust contract/service consumed by C-4c/C-4d in
`docs/tasks/contextual input actions and addressable explorer plan.md`; it does
not begin refactoring Phase 1, change symbol correctness, or enable additional
languages.

### [x] 0.11.1 Freeze syntax request/result schemas and the language backlog

**Parallel owner:** coordinator/main lane.

**Work:** Add versioned Facet types for an exact-source syntax request and
result. The request owns positive request id, origin/request generation,
explicit language id, exact UTF-8 source, SHA-256, and maximum bounds. The
result repeats identity and includes outcome/completeness, parser/highlighter
fingerprint, elapsed/cache evidence, bounded diagnostics, and sorted
non-overlapping UTF-8 spans containing an Arborium flat tag plus ordered
canonical lower-case ChatFormatting names. Reject malformed hashes, unknown
languages, invalid UTF-8 boundaries/ranges/styles, overlapping/out-of-order
spans, and unbounded payloads.

Add the canonical direct surface
`syntax highlight --language java --stdin`; stdout is normal `CliOutput`, logs
stay on stderr. Record the 2026-08-15 `git ls-files` counts and Java-first
priority decision in `docs/java syntax highlighting.md`: after Java, Rust,
JSON, Gradle/Groovy, PowerShell, Markdown, TypeScript, and TOML are candidates;
plain text stays plain and existing SFML/G4 behavior is preserved.

**Validation:** Facet/text/JSON snapshots cover empty and representative Java,
identity/hash/bounds, all result outcomes, deterministic span/style order, and
malformed input. CLI help/list exposes the verb-first direct and serve forms.

```pwsh
cargo test --all-features syntax_highlight_contract
cargo test --all-features cli_help
```

**Completion criteria:** A fresh agent can implement either side from the
versioned types/docs alone; the exact source/hash/offset/style/language/fallback
contract and Java-only support boundary are unambiguous.

**Completion evidence (2026-08-15):** The direct request/result,
ChatFormatting, worker protocol/hello/highlight/cancel/ping/shutdown/error, and
parser-fingerprint schemas are implemented as versioned Facet values and
documented in `docs/java syntax highlighting.md`. Direct `syntax highlight
--language java --stdin` is discoverable through CLI help and produces bounded,
source-hash-bound UTF-8 spans without echoing source text. The tracked extension
audit is recorded with Java as the sole enabled new grammar and the later
language order preserved as backlog only.

### [x] 0.11.2 Implement cached Arborium Java highlighting

**Work:** Add `arborium-highlight = "=2.18.1"` with no `tree-sitter` feature.
Compile `arborium_java::HIGHLIGHTS_QUERY` once against the existing
`tree-sitter-patched-arborium` language, parse exact request text, convert raw
captures to Arborium `Span`, use `spans_to_flat_tokens`, and map stable flat
tags to canonical ChatFormatting lists. Reuse bounded parser/query context;
cache immutable results by `(language, source_sha256, formatting-schema)` with
an explicit byte/entry bound and hit/miss/eviction counters. Never cache source
under the wrong hash or emit raw source/path in telemetry.

**Validation:** Cover Java declarations, imports, annotations, comments,
strings/text blocks, numbers, types/methods/fields, malformed Java, exact
overlap precedence/coalescing, empty/trailing newline, CRLF, combining marks,
astral Unicode, stable formatting, unsupported language, source/hash mismatch,
cache reuse/eviction, one query compilation, and no duplicate native
tree-sitter package/link.

```pwsh
cargo test --all-features syntax_highlight_engine
cargo tree --manifest-path .\platform\cli\sfm-propagate-changes\Cargo.toml --duplicates
```

**Completion criteria:** Repeated Java requests produce deterministic flat
spans from one compiled Arborium query and bounded reusable state; Cargo keeps
the existing patched tree-sitter as the sole native link provider.

**Completion evidence (2026-08-15):** The engine compiles the pinned Arborium
Java query once, flattens overlapping captures deterministically, maps stable
tags to canonical Minecraft formatting, and retains bounded immutable results
by language/hash/format schema. Contract/engine fixtures cover representative
and malformed Java, overlap/coalescing, Unicode/combining/astral/CRLF bounds,
hash/size/style failures, unsupported languages, reuse, eviction, and one query
compilation. A real `SFM.java` cold request produced 356 spans across 9 tags and
10 formatting values in 1,776 microseconds; the repeated cache hit took 75
microseconds and returned the same semantic surface. Cargo retains the existing
patched tree-sitter provider; no Java ANTLR or second native tree-sitter ships.

### [x] 0.11.3 Add supervised `syntax serve` and prove direct/worker parity

**Work:** Add a lightweight framed `syntax serve` process with explicit
protocol/hello capabilities, maximum frame/pending limits, cancellation,
request generations, ping, shutdown, request terminal outcomes, and
stdout-only frames. Reuse/extract the proven frame codec, child/process
supervision, timeout, crash/restart, and cleanup patterns without requiring a
symbol workspace/dependency index and without starting one process per
request. Keep protocol details behind the Minecraft provider boundary.

**Validation:** Prove byte-equivalent direct/worker results, fragmented and
coalesced frames, handshake/schema/capability mismatch, pending/frame limits,
pre/during/post cancellation, stale ids/generations, malformed frames, timeout,
crash/restart, EOF, stderr logging, acknowledged shutdown, no leaked child,
one process across multiple source hashes, cache hit on repetition, and warm
request latency evidence. Then run strict Rust checks and existing Java-analysis
regression suites.

```pwsh
cargo test --all-features syntax_highlight_server
cargo test --all-features java_analysis
cargo test --all-features --test java_analysis_scenarios
& .\platform\cli\sfm-propagate-changes\check-all.ps1
```

**Completion criteria:** Minecraft can supervise one lightweight Rust syntax
worker and obtain current-source Java spans with cancellation/stale safety;
direct and worker output agree; warm reuse is measured; all Rust checks and
existing symbol-analysis tests pass.

**Completion evidence (2026-08-15):** `syntax serve` performs capability
negotiation and bounded framed I/O, preserves protocol-only stdout, reuses one
process/query/cache, acknowledges cancellation/ping/shutdown, and terminates or
restarts cleanly on EOF, malformed input, timeout, and crash. Direct/worker
parity, frame fragmentation/coalescing/bounds, pending accounting, repeated
supersession, result-size fallback, lifecycle, and real Java-to-installed-Rust
interop tests passed. `check-all.ps1` completed 569 tests with 3 ignored and all
8 scenario tests; canonical Java compile and the full Java suite also exited
successfully with the installed-worker integration enabled.

### Phase 0.11 risk register

| Risk | Guardrail and proof |
| --- | --- |
| A source-valid highlight result serializes beyond the negotiated frame and kills the session. | Preflight encoded result size and replace only that request with a compact typed failure that itself fits; preserve worker liveness and prove the reproduced boundary. |
| A source under the byte limit expands beyond the frame after JSON escaping. | Both clients and the server enforce the negotiated encoded-frame budget before admission; an oversized request fails locally/typed without tearing down unrelated work. |
| Cancellation releases client capacity before the single Rust engine releases its remote slot. | Track cancelled-but-remotely-in-flight identities through terminal acknowledgement/result and test rapid supersession beyond the pending limit without `syntax.server-busy`. |
| Span/cache limits are checked only after expensive unbounded allocation or differ on cache hit. | Bound capture/event queues, check cancellation during capture normalization, apply identical request limits on hit/miss, and use conservative retained-memory accounting. |
| Repeated worker failure spawns a process for every editor mutation. | Bounded restart backoff/circuit breaking reset by a valid hello, with explicit retry and deterministic lifecycle tests. |
| Language-local protocol tests drift together while Rust and Java disagree. | At least one installed cross-runtime test writes Java frames to the real Rust worker and decodes Rust hello/result/shutdown frames. |

## Phase 0.12 — Complete location definitions and add location-aware usages

This read-only phase is the CLI-side dependency of contextual-plan C-7 through
C-9. It improves one symbol universe and one supervised worker; it does not
implement Minecraft gestures/panels, mutate Java, invoke Gradle, propagate, or
begin Phase 1 refactoring architecture.

### [ ] 0.12.1 Resolve JDK, local, member, annotation, and import definitions at position

**Work:** Extend the branch symbol universe with a first-class JDK-source
domain selected by the branch's existing JDK/release configuration. Prefer the
selected JDK's validated `src.zip`/source inventory, publish/index it under an
identity containing JDK release/provider/parser/index format and source bytes,
and report missing/stale platform sources explicitly. Model implicit
`java.lang` and ordinary import/package precedence semantically. Do not search
ambient JDK installations or accept spelling alone as a resolved declaration.

Extend per-document lexical facts/range classification for local variables and
parameters, including nested scopes, shadowing, lambdas/catches/patterns only
where modeled, and declaration self-navigation. Reuse existing field/method/
constructor/import/type facts for member calls, method references, annotation
types, import declarations, static imports, and overloads. Dynamic or
insufficiently modeled dispatch returns typed ambiguous/unsupported evidence,
not a guessed first match. Separate target-domain completeness from unrelated
global dependency partiality so a valid match is returned with scoped warnings.

**Validation:** Adjacent scenarios use exact source positions for `String`,
`Object`, `StringBuilder`, explicit/wildcard/static imports, locals/parameters
with shadowing, fields, overloaded methods, constructors, annotations, import
tokens, declaration names, inherited/dynamic cases, all source sets, and
workspace/dependency/JDK origins. Missing/stale JDK/dependency sources and an
unrelated partial component remain visible without suppressing a known match.
Direct selector and location forms agree where one exact selector exists.

**Completion criteria:** Every JAVA-45/JAVA-46 fixture resolves or returns a
precise semantically justified ambiguity/unsupported outcome; no JDK symbol is
resolved by name guessing; target matches survive unrelated partial coverage;
and index/source identities make the answer reproducible.

### [ ] 0.12.2 Add typed usage-at-position and the `list-usages` location form

**Work:** Add versioned `UsageAtPositionRequest`/`UsageAtPositionResult`
(exact schema names/version frozen by Facet snapshots). Reuse the definition
request's branch/classpath, ordered roots/source sets, exact current source/hash,
UTF-aware position, dependency/JDK identities, and generations. Resolve one
target semantically, then return that target plus deterministic categorized
references: declaration, read/write/local use, field access, invocation,
constructor call, method reference, override/implementation, annotation use,
import/qualification, Javadoc/string/reflection-like candidates where already
classified. Categories with insufficient semantic certainty remain reported as
skipped/uncertain and are never silently promoted to safe refactoring usages.

Extend canonical `symbol list-usages` and alias `list-usage` with the mutually
exclusive location form shown above. Preserve exact-selector behavior and
status codes. Each returned usage carries canonical symbol identity, portable
resolver address, root-relative/report path, source set/origin, source hash,
UTF-8 byte plus line/column span, confidence, completeness, diagnostics, and
typed refresh/acquisition recommendations. Direct/manual and reusable-engine
paths return the same canonical semantic body.

**Validation:** Figue and scenario tests cover mixed-form rejection, alias
parity, zero/one/many usages, every supported category, locals with shadowing,
overloads, dependency/JDK/project references, current overlay versus disk,
Unicode/CRLF, ambiguous/unsupported target, incomplete indexes, stable ordering,
bounded output, text/JSON/CSV, and exact direct-engine parity.

**Completion criteria:** A caller can ask “what symbol is under this exact
cursor, and where is it referenced?” without manufacturing a selector; the
typed answer is sufficient to build a persistent explorer and remains honest
about origin, certainty, completeness, and skipped categories.

### [ ] 0.12.3 Extend `symbol serve`, prove root/completeness correctness, and hand off

**Work:** Negotiate `usage-at-position` as an additive capability and add
framed request/result/cancel terminal messages. Reuse the worker's immutable
workspace/dependency/JDK resolution surfaces, file-fact cache, generations,
bounded pending work, cancellation, restart/backoff, and owned-child cleanup.
Definition and usage requests over one unchanged workspace must share reusable
state; neither may invalidate the other by request type alone.

Expose enough canonical source-root/JDK/dependency mapping evidence for the
Minecraft adapter to compose its resolver authorization without absolute-path
guessing. Add cross-runtime regressions for the exact messages
`No worker source root contains the document within its resolver authorization`
and `No symbol is present at the captured editor position (dependency/source index is incomplete)`:
known fixtures must succeed, while real unmapped/partial cases retain truthful
typed diagnostics. Measure cold and at least twenty warm mixed definition/
usage requests, cancellation, crash/restart, and process liveness. Keep the
existing definition warm bounds; record a separate usage warm median/p95/max
before the consumer goal and optimize any multi-second dominant stage rather
than hiding it.

**Validation:** Protocol fragmentation/coalescing/schema/capability/limits,
out-of-order mixed requests, cancellation and generation replacement,
cache hit/invalidation, definition regression parity, direct/worker canonical
parity, Java installed-process decoding, root mapping, missing executable,
EOF/shutdown, memory/process bounds, and zero leaked workers. Run required
`check-all.ps1`, scenarios, canonical Java integration tests, and diff checks.

**Completion criteria:** One supervised worker serves correct definition and
usage-at-position queries over workspace/dependency/JDK sources with measured
interactive reuse, clean cancellation/lifecycle, canonical direct parity, and
sufficient typed mapping/completeness evidence for C-7/C-8. Java sources and
Phase 1 remain untouched.

### Phase 0.12 parallel work map

After the request/result and JDK-source identity are frozen by one integration
owner, disjoint lanes may proceed in parallel:

- **JDK/location-definition lane:** JDK source provider/index identity,
  implicit imports, lexical declarations, and 0.12.1 scenarios.
- **Usage engine lane:** usage-at-position DTOs, target/category extraction,
  exact-selector equivalence, and direct CLI scenarios.
- **Worker protocol lane:** additive frames/capabilities, mixed request
  lifecycle, cache/cancellation/process tests against frozen fixtures.
- **Minecraft consumer lane (linked plan):** codecs/provider/reference-result
  adapters against checked-in protocol fixtures; it does not edit Rust engine
  internals before integration.
- **Integration owner:** shared module exports, Figue registration, Facet schema
  snapshots, worker wiring, cross-runtime probe, benchmarks, docs, and plans.

### Phase 0.12 risk register

| Risk | Guardrail and proof |
| --- | --- |
| JDK lookup searches an ambient installation different from the branch JDK | Resolve only through branch-selected JDK/provider identity; fingerprint source bytes/release; missing source is explicit |
| Implicit `java.lang` becomes a same-simple-name guess | Apply Java package/import precedence against indexed JDK declarations and preserve ambiguity/conflict scenarios |
| Local-variable support ignores shadowing or crosses lexical scopes | Scope-tree/range fixtures for blocks, parameters, lambdas/catches where modeled; unsupported constructs stay typed |
| Clicking an import resolves the token `import` or a partial segment | Syntax-range classification maps the qualified imported subject/reference to its declaration with exact spans |
| Global partial dependency coverage suppresses a valid target | Target-domain completeness is separate; return valid matches plus scoped missing-input diagnostics |
| Usage-at-position duplicates the parser/index or first performs a public selector query | One internal semantic target-resolution entry point feeds definition and usage collection over the same fact universe |
| Reference categories overstate certainty needed for later refactors | Every category/confidence is explicit; uncertain textual/Javadoc/reflection candidates remain separate and uneditable |
| Mixed definition/usage requests exceed worker memory or leak processes | Shared bounded immutable surfaces, pending/output limits, owned child handles, cancellation/crash/EOF cleanup, and liveness probes |
| Protocol change breaks the completed Minecraft definition client | Additive capability negotiation, old definition frame compatibility tests, checked-in Java fixture parity, and schema mismatch diagnostics |

## Phase 1 — Inventory and architecture

### [ ] 1.1 Map the existing foundations

- Document the audit parser, source enumeration, source exclusions, Java
  language model, classpath resolution, gix repository abstraction, and CLI
  command registration points.
- Identify which audit code can be extracted into reusable parse-tree,
  source-span, symbol, and diagnostic modules without changing audit output.
- Inspect Fable's AST/reflection and edit-oriented facilities and Arborium's
  Java parser API. Record version, license, incremental-parsing support, error
  recovery, and whether source byte ranges remain stable.

**Validation:** an architecture note names concrete modules and a dependency
decision; no new parser dependency is added solely by inspection. Record the
reconnaissance above as the initial decision log.

### [ ] 1.2 Define the refactoring intermediate representation

- Add typed models for `SymbolId`, `TypeId`, `MethodSignature`, source spans,
  edits, file plans, diagnostics, confidence, and validation status.
- Make ids explicitly snapshot-scoped. Add correspondence/lineage models for
  relating declarations and operations across before/after snapshots.
- Add versioned `SourceComparison`, file-operation, source-operation,
  equivalence-level, assumption, witness, and unknown/ambiguity models shared
  by human-readable and JSON reports.
- Add Facet derives and stable external names for reports and command options.
- Model edit preconditions with the original file hash and expected source
  span so stale plans fail safely.

**Validation:** round-trip tests serialize/deserialize a preview report and a
stale-plan test refuses to apply after the source changes.

### [ ] 1.3 Extract reusable Arborium infrastructure from audit

- Introduce a shared Java parse module that owns parser construction, source
  encoding/line-map handling, node traversal helpers, error-node reporting,
  and byte-span validation.
- Keep audit behavior unchanged while moving it onto the shared module.
- Define a stable `SourceSnapshot` containing branch, source-set, relative path,
  content hash, bytes, parse tree, and line map.

**Validation:** existing audit tests pass unchanged; a parser fixture can locate
the same method invocation and declaration spans used by the audit.

### [ ] 1.4 Extend the external type strategy beyond the Phase 0.8 source index

- Treat the lockfile-pinned dependency-source index from Phase 0.8 as the
  authoritative external source layer when providers are available. Evaluate
  bytecode stubs/signatures only for JDK or dependency components with no usable
  source/decompile provider; do not replace the source index.
- Extend how Minecraft/Forge mappings, missing optional-mod classes, generated
  sources, inheritance, and multiple source roots are represented.
- Establish a hard distinction between `Resolved`, `PartiallyResolved`, and
  `Unresolved`; only operations with a proven safety rule may proceed with the
  latter two states.

**Validation:** retain the Phase 0.8 `MultiLineEditBox` source-backed proof,
then report overloads and inheritance for one source-unavailable external type
without launching Gradle or the game.

## Phase 2 — Parser, resolver, and edit engine

### [ ] 2.0 Parser/edit feasibility spike

- Parse a real SFM Java file and a deliberately malformed fixture with the
  already-pinned Arborium crates.
- Print declaration, invocation, comment, annotation, and byte-span ranges;
  apply one identifier replacement while preserving all untouched bytes.
- Measure parser/index cost over the production source set and record memory
  use before committing to whole-project indexing.
- Decide whether incremental parsing is needed for previews; do not introduce
  Fable or another parser unless this spike demonstrates a concrete gap.

**Exit criteria:** the spike proves lossless spans, stable line mapping, and a
clear error policy for parse gaps; its findings are recorded before resolver
work begins.

### [ ] 2.1 Build lossless Java parsing

- Parse production and test Java source sets with error recovery.
- Retain comments, whitespace, annotations, imports, and exact source ranges.
- Add fixtures for nested classes, records, enums, generic methods, lambdas,
  method references, `var`, static imports, inherited members, and overloads.

### [ ] 2.2 Build project symbol resolution

- Resolve package/import/simple names, lexical declarations, fields, methods,
  constructors, parameter types, inheritance, and interfaces.
- Use compiled/classpath metadata for external Minecraft/Forge/mod classes;
  source symbols take precedence when available.
- Resolve `var` from initializer expressions where type information is
  available, and emit an explicit unresolved diagnostic otherwise.
- Track version/source-set identity and annotation boundaries, including
  `@MCVersionDependentBehaviour`.

**Validation:** resolver tests cover `this.font`, inherited methods, static
imports, overloaded `between`, and `var a = this.font; a.drawString(...)`.

### [ ] 2.2a Build an indexed symbol graph before operation-specific logic

- Index declarations and references in deterministic path/order, assigning
  snapshot-scoped IDs based on source snapshot, file hash, qualified owner, and
  descriptor. Never infer cross-snapshot continuity from id equality alone.
- Store scopes and parent relationships for packages, types, methods,
  constructors, fields, parameters, locals, lambdas, and anonymous classes.
- Add import and inheritance resolution as graph edges; retain unresolved edges
  as diagnostics rather than dropping them.
- Make queries return declarations plus all statically resolved reference spans;
  operations must consume this query API rather than walking raw nodes ad hoc.

**Reason:** rename/move and signature changes need a consistent identity for a
symbol across files. The existing audit visitor is file-local and cannot safely
provide that identity.

### [ ] 2.3 Implement transactional edits

- Normalize edits, reject overlaps, preserve line endings, and write through a
  temporary file plus atomic rename.
- Generate a unified diff and a structured edit report before applying.
- Enforce the Phase 0 contract: exactly one of `--dry-run` or `--apply`.
  `--dry-run` returns the complete diff/report with zero writes; `--apply`
  writes in place; `--apply --output-root <dir>` copies the complete source tree
  then edits the copy. Reject `--dry-run --output-root`.
- Block mutation when unresolved references could be affected. A future escape
  policy, if justified, must be typed and operation-specific rather than a
  blanket spelling-based `--allow-unresolved` switch.

### [ ] 2.4 Define operation planning as a pure step

- Inputs are an immutable source index, typed operation request, and policy
  options; output is a plan of edits and diagnostics with no filesystem writes.
- Validate all preconditions, collisions, scope changes, annotation boundaries,
  and source hashes before producing an applicable plan.
- Make plan application idempotence and deterministic ordering testable.

**Validation:** the same snapshot/request produces byte-identical reports and
diffs across repeated runs.

### [ ] 2.5 Produce structured source comparisons

- Compare two immutable full snapshots without requiring either to be the
  current Git worktree.
- Emit deterministic file add/delete/rename/modify/unchanged operations plus a
  line/range fallback for changed files.
- Add conservative Java-aware correspondences incrementally: formatting-only,
  type/file rename, member rename with references, unchanged method body under
  rename, rename plus body modification, and ambiguous/unknown.
- Preserve exact before/after spans, hashes, excerpts, diagnostics,
  assumptions, and equivalence proof level.
- Support human-readable output and versioned Facet JSON such as
  `source compare --before <snapshot> --after <snapshot> --output <file>`.
- Keep report production read-only. Do not require Vox, Minecraft, Gradle, or a
  materialized Git patch.
- Emit or support a deterministic comment projection whose ordinary comment
  strings, provenance, and `DiffRegion` selection rules can drive the in-game
  diff colours and review queries without hard-coded presentation state.
- Preserve enough symbol/span witnesses for later comment-rule migration across
  rename, move, signature change, body modification, and ambiguous candidates.

### [ ] 2.5a Project the release review surface

- Build typed review records for `ReviewUnit`, `ReviewSurface`,
  `CrossLaneCorrespondence`, `EquivalenceProof`, `MorphismRule`, and
  `ImpactClosure` on top of `SourceComparison`; do not duplicate the comment
  session or snapshot schemas.
- Produce method/function units with declaration, signature, annotation,
  import, inheritance, referenced-symbol, caller/method-reference, and
  environment closures. Fall back to type/field/import/file/diff-operation
  units when safe method identity is unavailable.
- Compare every supported lane's `previous_release..HEAD` and emit candidate
  deduplication groups only with explicit proof. Re-open an unchanged body when
  its dependency closure or relevant environment differs, and always surface
  `@MCVersionDependentBehaviour` as a version seam.
- Implement versioned, tested, witness-producing morphism rules. The first
  useful rule may be Forge-to-NeoForge import/package adaptation, but the rule
  must not hide the transformed source or its resolution closure.
- Derive old/new definition and usage selectors for field/method renames,
  caller/reference selectors for signature changes, and affected resolution
  selectors for import changes. Project them into the shared comment kernel
  with provenance, tags, and suspended/ambiguous status where proof is absent.
- Emit a deterministic human-readable plus structured/HTML-compatible report
  that can be inspected outside Minecraft and consumed by the in-game review
  explorer. Include parser/index/classpath/configuration fingerprints, hashes,
  spans, proof witnesses, unresolved boundaries, and approval state.

**Validation:** two or more version fixtures demonstrate one safely shared
method, one body-identical method whose environment reopens review, one
`@MCVersionDependentBehaviour` seam, one accepted import morphism, one
ambiguous correspondence, and one field rename whose definition and usages are
all selectable. The report and comment projection are byte-deterministic.

**Validation:** fixture comparisons are byte-identical across repeated runs;
swapping before/after produces the expected inverse file/range operations;
ambiguous correspondence remains unknown; and the Java in-game viewer can load
the versioned fixture without implementing Rust parsing.

## Phase 3 — First symbol refactoring: rename/move

### [ ] 3.1 Rename a member

- Implement `symbol rename <at-selector> <new-name> --branch <branch>
  (--dry-run|--apply)` for fields and methods.
- Update declarations, qualified calls, unqualified calls, method references,
  overrides, Javadocs where unambiguous, and relevant imports.
- Require a signature or unique resolution when overloads collide.

### [ ] 3.2 Move a member

- Implement `symbol move <at-selector> <qualified-destination> --branch
  <branch> (--dry-run|--apply)` and define the explicit forwarding policy.
- Update visibility, qualification, imports, overrides, static access, and
  references to `this`/private members.
- Refuse moves that change semantics unless the user selects a documented
  compatibility mode.

### [ ] 3.3 CLI UX and catalog

- Complete the Phase 0 `symbol` scaffolds for rename/move using typed
  Facet/Figue command and output models; there is no separate symbol-plan apply
  command.
- Keep expression/statement operations (`extract-method-args`,
  `extract-variable`, `extract-method`, `inline-method`,
  `replace-method-call`, and `change-method-signature`) discoverable under the
  separate `refactor` namespace without duplicating rename/move.
- Add preflight target lookup so a missing or ambiguous symbol fails before
  compilation or file mutation.

### [ ] 3.4 Scaffold and document operation capabilities

- Ensure `symbol --help` and the separate `refactor --help`/catalog expose
  syntax, status, supported source languages, and safety restrictions without
  loading or modifying project sources.
- Add typed operation descriptors rather than duplicating names in match arms,
  help text, and documentation. Serialize the descriptors through Facet/Figue
  for machine-readable tooling.
- Add CLI parsing tests for every operation, including malformed queries,
  missing destination/name/signature values, and `--branch`/`--version`
  selection.
- Add a capability report that distinguishes parser-supported constructs from
  resolver-supported constructs and from rewrite-supported constructs.

**Completion criteria:** all operation names parse and appear in help/list
output; unsupported operations fail deterministically with exit code and
structured diagnostics; no operation mutates files until its implementation
phase is complete.

### [ ] 3.5 Establish the first end-to-end fixture before broad implementation

- Extend the Phase 0 scenario convention under
  `tests/java_refactoring/java_refactoring_scenario_test.rs` and adjacent
  `tests/java_refactoring/scenarios/`. Each mutation scenario contains
  `command.ps1`, tracked `before/`, tracked `after-expected/`, ignored
  `after-actual/`, tracked `output-expected.json`, and ignored
  `output-actual.json`.
- Add a small Java scenario containing `SFMBlockPosUtils.between`, qualified and
  unqualified calls, a method reference, an overload, an override, a Javadoc
  mention, and an unrelated same-spelled symbol. Its command uses
  `--source-root before --classpath-mode isolated --branch 1.19.2 --apply
  --output-root after-actual`.
- Prove `rename` changes only the intended declaration/references and that
  `move` additionally updates qualification/imports and rejects private-state
  dependencies.
- Keep the scenario independent of Minecraft compilation. Compare normalized
  relative paths and exact bytes against `after-expected/`; ignore timestamps,
  ACLs, directory enumeration order, and build outputs. Never mutate `before/`
  or auto-accept expected files.

## Phase 4 — IntelliJ-style operations

### [ ] 4.1 Rename-symbol as a first-class operation

Implement `rename` and separate type, package, method, field, parameter, and
local-variable targets; define collision checks and comment/string policies for
each.

### [ ] 4.2 Pull-up member

Move a field/method/constant to a selected superclass or interface, calculate
required visibility and abstract/default method changes, and reject unresolved
private dependencies.

### [ ] 4.3 Change signature

Add/remove/reorder/rename parameters, update invocations and method references,
handle overload selection, and optionally generate compatibility overloads.

### [ ] 4.4 Extract operations

- Implement `extract-variable` with expression selection, scope/lifetime
  analysis, and evaluation-count preservation.
- Implement `extract-method` with data-flow inference for parameters, return
  values, checked exceptions, and captured fields/locals.
- Implement `extract-method-args` by generating a nested or peer record using a
  deterministic naming policy, preserving annotations/defaults where legal,
  and delegating the old signature to the new args signature.

### [ ] 4.5 Inline and replacement operations

- Implement `inline-method` and the documented `inline-method-usage` alias,
  including a policy for removing the now-unused declaration.
- Implement `replace-method-call` with typed replacement templates, explicit
  receiver handling, and a dry-run count of changed call sites.
- Add composition tests proving `extract-method-args` followed by
  `inline-method` produces a stable, compilable result.

Each operation must have focused fixtures, a preview diff, an apply test, and a
compile/test validation path.

## Phase 5 — Audit integration and version awareness

### [ ] 5.1 Reuse audit diagnostics

- Allow audit rules to identify protected seams and forbidden direct calls that
  refactorings must preserve.
- Add a refactoring validation pass that runs source audit on the planned tree
  and reports newly introduced violations before apply.

### [ ] 5.2 Version-surface checks

- Compare each supported branch's `previous_release..HEAD` review lane and
  identify files/methods whose differences escape `@MCVersionDependentBehaviour`.
- Compute the dependency/import/inheritance/mapping/configuration/feature/
  classpath closure before allowing cross-lane deduplication. An unchanged body
  with a changed closure is reopened for review.
- Validate accepted morphism rules by version, scope, witnesses, and tests;
  report unknown or out-of-scope transformations as approval blockers.
- Require explicit per-version adapters when a symbol's signature differs.
- Run version-surface audit and review-report generation before propagation,
  after each merge, and after conflict resolution. Preserve the report's lane,
  symbol, span, and proof lineage when the propagation CLI changes a file.

## Phase 6 — Verification, documentation, and rollout

### [ ] 6.1 Test matrix

- Unit-test parser, resolver, edit conflict detection, serialization, and each
  operation.
- Add golden fixtures for SFMBlockPosUtils's `between -> betweenRange` example.
- Run CLI integration tests on representative production/test source sets,
  including a preview with zero writes and an apply with exact expected hashes.
- Compile and run relevant Java tests through `sfm-propagate-changes`; run the
  required `check-all.ps1` after Rust changes.

### [ ] 6.2 Propagation and recovery

- Commit only the 1.19.2 implementation and use the propagation CLI for later
  branches.
- At each conflict, preserve newer-version APIs and graft the semantic edit;
  never blindly replay byte offsets from 1.19.2.
- Verify `git status`, version-surface audit, and refactor smoke tests across
  all supported branches.

### [ ] 6.3 User documentation and release gate

- Document command syntax, target qualification, dry-run/apply safety,
  unresolved-symbol behavior, supported Java constructs, and recovery from a
  stale plan.
- Add examples for rename, move, pull-up, and change-signature.
- Update the developer documentation/changelog only if the CLI behavior is
  user-visible; record the feature in release notes when shipped.
- Define release acceptance: clean worktrees, passing CLI/Java tests, zero
  unintended audit/version-surface violations, reproducible diffs, a complete
  AST-aware `previous_release..HEAD` review report with explicit maintainer
  approval, and a rollback path via the generated report/commit. The prospective
  release JAR's Prism-harness experiential review remains a separate required
  gate; passing automated checks or generating a report cannot substitute for
  human code or experiential approval.

## Longer refactoring/review acceptance criteria

The first milestone is complete when all of the following are true:

1. `symbol rename ca.teamdman.sfm.common.util.SFMBlockPosUtils between
   betweenRange --branch 1.19.2 --dry-run` resolves one symbol, previews exact
   declaration/reference edits, and writes nothing.
2. The same `symbol rename ... --apply` request applies only after hash
   validation and emits a structured report plus unified diff; no separate
   generic `refactor apply` is required.
3. Ambiguous overloads, unresolved `var` types, generated sources, and
   reflection are reported with actionable diagnostics and do not mutate files
   by default.
4. The resulting tree compiles and passes focused tests, audit, and the
   version-surface check, then propagates cleanly through supported branches.
5. Two immutable snapshot fixtures produce a deterministic versioned
   `SourceComparison` JSON with line/range fallback, snapshot-scoped
   correspondences, explicit equivalence levels, and unknown ambiguity that the
   in-game comparison viewer can load without Vox.
6. A multi-lane release-review fixture produces deterministic review units,
   dependency/import/reference closures, deduplication proofs, accepted
   morphism witnesses, suspended ambiguities, and shared comment selectors; a
   maintainer can inspect the resulting report without an IDE.

## Detailed implementation contracts

### CLI lifecycle

Every mutating command follows this sequence:

1. Resolve branch/worktree and source-set policy.
2. Snapshot selected sources and compute hashes.
3. Parse/index sources and resolve the target query.
4. Produce a pure plan and diagnostics.
5. In `--dry-run`, return the full plan/diff and stop with zero writes. In
   `--apply`, continue only after all blocking diagnostics are clear.
6. Recheck hashes, worktree policy, and plan preconditions.
7. Apply all files transactionally, then reparse changed files.
8. Run configured postconditions (audit, compile, or tests) and write the
   structured report.

`--dry-run` must never create a temporary source tree inside the worktree.
`--apply --output-root` copies the complete selected source tree and writes only
to that explicit destination; in-place `--apply` must retain a failed-application
recovery directory until the report is successfully finalized. A second apply
of the same plan should be a no-op or fail with a clear “already applied”
status, never duplicate edits.

### Symbol identity and queries

The external selector language must distinguish a display target from an
internal snapshot-scoped identity:

- `pkg.Type`, `pkg.Type field`, and `pkg.Type method(JVMDescriptor)` are the
  Access-Transformer-derived display selectors. Dotted member syntax is not an
  alias.
- Methods always carry a JVM descriptor in the selector, including parameter
  and return types, so overload selection is explicit rather than heuristic.
- A future stable symbol URI should include branch/source path, owner, kind,
  name, and JVM-like descriptor, but must not use line numbers as identity.
- Query results must show candidate declarations, source locations, visibility,
  and resolution confidence before an operation is planned.

References are categorized as declaration, invocation, method reference,
override/implementation, import/qualification, Javadoc, string literal, or
reflection-like text. Only categories proven safe by the operation are edited;
the others are reported as skipped candidates.

### Operation-specific hazards

- `rename`: do not alter string literals or comments by default; Javadocs need
  a separate policy because `{@link}` names can be semantically meaningful.
- `move`: calculate the declaration's dependency closure (private fields,
  package-private types, enclosing-instance access, and static imports) before
  planning. A forwarding method is safer than changing callers but must be
  explicit.
- `extract-method-args`: choose nested-record versus peer-record placement;
  preserve parameter annotations, generic bounds, nullability annotations,
  evaluation order, and overload resolution. Reject varargs/receiver cases
  until modeled explicitly.
- `extract-variable`: never duplicate an expression with side effects; account
  for short-circuiting, loop headers, pattern variables, and final/effectively
  final capture.
- `extract-method`: reject selections containing unmodeled `break`, `continue`,
  `return`, `yield`, labels, synchronized regions, or partially selected
  declarations. Infer input/output data flow before generating a signature.
- `inline-method`: reject recursion, mutual recursion, dynamic dispatch,
  synchronized/native/abstract methods, and bodies whose control flow cannot
  be embedded at the call site without changing semantics.
- `replace-method-call`: replacement templates must be typed and must state
  whether the receiver and arguments are evaluated once, eagerly, or lazily.
- `change-method-signature`: update overrides and interface implementations as
  one graph operation; preserve binary/source compatibility only when the user
  requests generated forwarding overloads.

### Postconditions and audit integration

The report must distinguish “all references resolved and rewritten” from “plan
applied with skipped/unresolved references.” After mutation, reparse and rerun
the relevant declarative audit rules. A newly introduced audit warning is a
failed postcondition even when the Java compiler would accept the code. For
version-dependent files, compare the planned symbol boundary with
`@MCVersionDependentBehaviour` before allowing propagation.

### Recovery and review artifacts

Each plan receives a stable ID derived from the source snapshot, operation
request, and tool version. Store:

- the request and resolved candidates;
- source hashes before and after;
- structured edits and skipped references;
- unified diff;
- audit/compile/test postconditions;
- tool/parser/classpath versions.

The report must be sufficient for a reviewer to understand and manually
reproduce the change without rerunning discovery. Applying an old plan after a
branch merge must fail hash/precondition checks and require a fresh preview.

## Deferred design gates (not Phase 0 blockers)

- Keep the already pinned Arborium parser for Phase 0. Reconsider its revision
  only if the read-only span/error fixtures demonstrate a concrete gap.
- Should applying a plan require a clean worktree, or permit unrelated staged
  changes when all target hashes still match?
- Should compatibility overload generation be part of the initial change
  signature operation or a later release?
- Should comments/Javadocs be opt-in rewrite targets, or should each operation
  expose a typed reference-category policy?
- Should the first `move` implementation require a forwarding method to avoid
  changing call sites, with direct moves deferred until dependency closure is
  complete?
