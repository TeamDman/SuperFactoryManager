# CLI AST refactoring suite plan

**Plan status:** Proposed  
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-07-21

## How to update this plan

- `[ ]` Not started
- `[~]` In progress
- `[x]` Complete
- `[!]` Blocked

Update a work item's heading and completion notes together. Record command
output, affected files, and propagation evidence beneath the item that it
proves. This plan describes an implementation; creating the plan does not
authorize source rewrites.

## Purpose

Build a safe, CLI-first Java refactoring suite on top of the source discovery,
audit, classpath, and version-propagation machinery already in
`sfm-propagate-changes`. The first motivating operation is:

```powershell
sfm-propagate-changes.exe symbol rename SFMBlockPosUtils.between betweenRange
```

The suite should provide a useful subset of IntelliJ-style refactorings while
remaining deterministic, reviewable, version-aware, and safe to run across the
Minecraft branches. It must never silently perform a textual global replace.

## Source-aware CLI surface

The shared symbol index should power both navigation and mutation. Use an
object–verb namespace:

```text
sfm-propagate-changes.exe symbol definition <symbol-query>
sfm-propagate-changes.exe symbol usage list <symbol-query>
sfm-propagate-changes.exe symbol rename <src> <dest>
sfm-propagate-changes.exe symbol move <src> <dest>
```

`definition` and `usage list` are read-only and must work without a build or
game launch. They return repository-relative paths, line/column and byte span,
qualified identity, kind, descriptor, source set, and resolution confidence.
Zero matches and ambiguous matches have distinct nonzero statuses. Output has
human-readable and Facet/Figue JSON forms and includes source hash, branch,
parser version, and classpath/index fingerprints.

There is no `refactor` compatibility alias: this surface has not been
implemented or released, so `symbol` is the initial public namespace.
Help/completions/documentation must present `symbol` as authoritative.

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

## Design decisions to confirm during implementation

| Area | Initial decision | Acceptance consequence |
| --- | --- | --- |
| Command shape | `symbol definition`, `symbol usage list`, and symbol mutation verbs such as `symbol rename`, `symbol move`, and `symbol apply`. | `--help` documents read-only navigation, dry-run/apply, branch/version selection, and failure policy. |
| Target syntax | Fully qualified type/member where possible; `Type.member` is accepted only when unique. | Ambiguous targets fail before any write and list candidates. |
| Safety default | Preview is the default; `apply` requires an explicit flag and clean/staged-state policy. | No source changes from a discovery or preview command. |
| Rewrite model | Parse once, resolve symbols, create edits against byte ranges, validate edits, then write atomically. | No overlapping edits or partial files. |
| Resolution | Imports, package declarations, lexical scopes, inheritance, overload descriptors, and project/classpath types. | Unresolved references are surfaced and block mutation unless explicitly overridden. |
| Output | Human-readable summary plus Facet/Figue-serializable JSON report. | Reports include target, files, edits, unresolved cases, and validation results. |
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
  command and an `invoke` method. A refactor command therefore needs a new
  `cli::refactor` module wired into `Command`, plus parser tests in the existing
  CLI test module. It should not be hidden inside `audit`.
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
- Support `--check` (validate only), `--diff`, `--output <dir>`, and an explicit
  `--allow-unresolved` escape hatch that still records every unresolved use.

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

**Validation:** fixture comparisons are byte-identical across repeated runs;
swapping before/after produces the expected inverse file/range operations;
ambiguous correspondence remains unknown; and the Java in-game viewer can load
the versioned fixture without implementing Rust parsing.

## Phase 3 — First refactoring: rename/move (`mv`)

### [ ] 3.1 Rename a member

- Implement `refactor mv Owner.oldName newName` for fields and methods.
- Update declarations, qualified calls, unqualified calls, method references,
  overrides, Javadocs where unambiguous, and relevant imports.
- Require a signature or unique resolution when overloads collide.

### [ ] 3.2 Move a member

- Define syntax for destination type/package and whether a forwarding method is
  requested.
- Update visibility, qualification, imports, overrides, static access, and
  references to `this`/private members.
- Refuse moves that change semantics unless the user selects a documented
  compatibility mode.

### [ ] 3.3 CLI UX and catalog

- Add `refactor list`, `refactor show <operation>`, `refactor preview`, and
  `refactor apply` using typed Facet/Figue command models.
- Scaffold the complete canonical operation catalog (`rename`, `move`,
  `extract-method-args`, `extract-variable`, `extract-method`, `inline-method`,
  `replace-method-call`, and `change-method-signature`) in the typed CLI model.
- Add preflight target lookup so a missing or ambiguous symbol fails before
  compilation or file mutation.

### [ ] 3.4 Scaffold and document operation capabilities

- Ensure `refactor --help`, `refactor list`, and `refactor show <operation>`
  expose the catalog, syntax, status, supported source languages, and safety
  restrictions without loading or modifying project sources.
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

- Add a small Java fixture containing `SFMBlockPosUtils.between`, qualified and
  unqualified calls, a method reference, an overload, an override, a Javadoc
  mention, and an unrelated same-spelled symbol.
- Prove `rename` changes only the intended declaration/references and that
  `move` additionally updates qualification/imports and rejects private-state
  dependencies.
- Keep this fixture independent of Minecraft compilation so parser/resolver
  regressions are fast and deterministic.

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

- Compare planned edits across supported branches and identify files/methods
  whose differences escape `@MCVersionDependentBehaviour`.
- Require explicit per-version adapters when a symbol's signature differs.
- Run version-surface audit before propagation, after each merge, and after
  conflict resolution.

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
  unintended audit/version-surface violations, reproducible diffs, and a
  rollback path via the generated report/commit.

## Initial acceptance criteria

The first milestone is complete when all of the following are true:

1. `refactor mv SFMBlockPosUtils.between betweenRange --branch 1.19.2` resolves
   one symbol, previews exact declaration/reference edits, and writes nothing.
2. `refactor apply` applies the same plan only after hash validation and emits a
   structured report plus unified diff.
3. Ambiguous overloads, unresolved `var` types, generated sources, and
   reflection are reported with actionable diagnostics and do not mutate files
   by default.
4. The resulting tree compiles and passes focused tests, audit, and the
   version-surface check, then propagates cleanly through supported branches.
5. Two immutable snapshot fixtures produce a deterministic versioned
   `SourceComparison` JSON with line/range fallback, snapshot-scoped
   correspondences, explicit equivalence levels, and unknown ambiguity that the
   in-game comparison viewer can load without Vox.

## Detailed implementation contracts

### CLI lifecycle

Every mutating command follows this sequence:

1. Resolve branch/worktree and source-set policy.
2. Snapshot selected sources and compute hashes.
3. Parse/index sources and resolve the target query.
4. Produce a pure plan and diagnostics.
5. Print the plan/diff and, unless `apply` was explicitly requested, stop.
6. Recheck hashes, worktree policy, and plan preconditions.
7. Apply all files transactionally, then reparse changed files.
8. Run configured postconditions (audit, compile, or tests) and write the
   structured report.

`preview` and `--check` must never create a temporary source tree inside the
worktree. `apply` must retain a failed-application recovery directory until
the report is successfully finalized. A second apply of the same plan should
be a no-op or fail with a clear “already applied” status, never duplicate edits.

### Symbol identity and queries

The external query language must distinguish a display name from an identity:

- `pkg.Type.member` is a display query and may be ambiguous.
- A descriptor-qualified method query includes parameter types and return type
  when required to disambiguate overloads.
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

## Open questions

- Should `mv` mean rename-only until a destination type is supplied, or should
  move syntax be a separate explicit operation from the beginning?
- Which parser/edit library revision should be pinned after the Arborium/Fable
  spike, and can it preserve comments and malformed-but-recoverable Java?
- Should applying a plan require a clean worktree, or permit unrelated staged
  changes when all target hashes still match?
- Should compatibility overload generation be part of the initial change
  signature operation or a later release?
- Should comments/Javadocs be opt-in rewrite targets, or should each operation
  expose a typed reference-category policy?
- Should the first `move` implementation require a forwarding method to avoid
  changing call sites, with direct moves deferred until dependency closure is
  complete?
