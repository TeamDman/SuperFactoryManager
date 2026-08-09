# CLI AST refactoring suite plan

**Plan status:** Active; Phase 0 is the approved next implementation phase
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-08-09
**Intent audit:** Passed 2026-08-09 for Phase 0

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
| JAVA-05 | Read-only analysis comes before mutation. | Phase 0 implements `symbol definition` and `symbol usage list`; `symbol rename` and `symbol move` have frozen contracts but remain in later phases. | — |
| JAVA-06 | Use the object–verb symbol namespace rather than splitting navigation and refactoring across `java`, `symbol`, and `refactor`. | The authoritative surface is `symbol definition`, `symbol usage list`, `symbol rename`, and `symbol move`; no compatibility alias is introduced. | — |
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

## Guidance traceability for Phase 0

| Guidance | Plan coverage | Evidence when complete |
| --- | --- | --- |
| JAVA-01, JAVA-02 | Phase 0 scope/non-goals; 0.3; 0.7 | Scenario sources run without Minecraft, Gradle, or generic project initialization. |
| JAVA-03, JAVA-04 | 0.3 | Explicit Cargo target invokes restricted `command.ps1` through Figue and the production command path. |
| JAVA-05, JAVA-06 | Source-aware CLI surface; 0.4; 0.6 | CLI parsing and end-to-end tests prove both read-only commands; mutation commands remain non-mutating/deferred. |
| JAVA-07 | Selector contract; 0.4 | Parser fixtures cover class, field, method descriptor, nested class, malformed, zero-match, and ambiguous selectors. |
| JAVA-08, JAVA-09, JAVA-10 | Source/classpath contract; 0.5 | Reports enumerate selected source sets/visibility and prove branch versus isolated behavior. |
| JAVA-11, JAVA-12 | 0.1; 0.2 | Text/JSON/CSV rendering tests and versioned JSON snapshots pass with logs separated from stdout. |
| JAVA-13, JAVA-14 | 0.3 | Harness safety tests and adjacent expected/actual artifacts pass. |
| JAVA-15, JAVA-16 | Confirmed mutation contract; later Phase 3 scenario work | CLI parser rejects invalid mutation modes; mutation application remains outside Phase 0. |

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
- **Known source limitation:** None for the Phase 0 discussion; the original
  user messages were available in this conversation. Earlier broad plan history
  remains represented by the pre-existing sections and linked plans.

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

The shared symbol index should power both navigation and mutation. Use an
object–verb namespace:

```text
sfm-propagate-changes.exe symbol definition <at-selector> --branch <branch>
sfm-propagate-changes.exe symbol usage list <at-selector> --branch <branch>
sfm-propagate-changes.exe symbol rename <at-selector> <new-name> --branch <branch> (--dry-run|--apply)
sfm-propagate-changes.exe symbol move <at-selector> <qualified-destination> --branch <branch> (--dry-run|--apply)
```

`definition` and `usage list` are read-only and must work without a build or
game launch. They return repository-relative paths, line/column and byte span,
qualified identity, kind, descriptor, source set, and resolution confidence.
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
sfm-propagate-changes.exe symbol definition ca.teamdman.sfm.SomeType --branch 1.19.2
sfm-propagate-changes.exe symbol usage list ca.teamdman.sfm.SomeType MY_FIELD --branch 1.19.2
sfm-propagate-changes.exe --output-format json symbol usage list ca.teamdman.sfm.SomeType 'between(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/util/stream/Stream;' --branch 1.19.2
```

The CLI owns one typed `JavaSymbolSelector`; definition, usage, rename, move,
scenario parsing, reports, and later review selectors must not each reinterpret
the raw tokens. Rename changes only the selected identifier (and a top-level
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

Read-only `definition` and `usage list` accept neither `--dry-run` nor
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
usage, rename, and move. The separate `refactor` namespace may host
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
| Command shape | `symbol definition`, `symbol usage list`, `symbol rename`, and `symbol move`; no `java`/`refactor` alias for these operations. | `--help` documents navigation, exact selector syntax, explicit mutation mode, branch/source selection, and failure policy. |
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

## Phase 0 — Read-only symbol navigation and scenario harness (next phase)

This is the next implementation goal. Complete `0.1` through `0.7` before
starting mutation, broad refactoring catalogs, cross-lane equivalence, or the
in-game review provider. The phase deliberately delivers a useful vertical
slice: a real user/agent can ask where a Java symbol is defined and used in an
SFM branch, while tiny isolated scenarios prove the same public contract.

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
machine-readable; no source file is changed; and the next safe phase is symbol
rename dry-run/apply rather than more foundational redesign.

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

### [ ] 1.4 Decide the classpath/type-index strategy

- Prototype source-only indexing first, then add classpath stubs/signatures from
  the existing compile classpath resolver.
- Define how Minecraft/Forge mappings, missing optional-mod classes, generated
  sources, and multiple source roots are represented.
- Establish a hard distinction between `Resolved`, `PartiallyResolved`, and
  `Unresolved`; only operations with a proven safety rule may proceed with the
  latter two states.

**Validation:** index SFMBlockPosUtils and one Minecraft/Forge external type;
report overloads and inheritance without launching Gradle or the game.

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
