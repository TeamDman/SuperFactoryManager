# CLI AST refactoring suite plan

**Plan status:** Proposed  
**Primary implementation root:** `D:\Repos\Minecraft\SFM\repos2\1.19.2`  
**Last updated:** 2026-07-16

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
sfm-propagate-changes.exe refactor mv SFMBlockPosUtils.between betweenRange
```

The suite should provide a useful subset of IntelliJ-style refactorings while
remaining deterministic, reviewable, version-aware, and safe to run across the
Minecraft branches. It must never silently perform a textual global replace.

## Canonical refactoring operation catalog

This is the authoritative list of planned operations. The CLI scaffold should
expose every name below from the beginning, even when an operation is initially
reported as `not implemented`. That makes the supported surface discoverable,
keeps help and documentation synchronized, and gives each operation a stable
place for capability/version reporting.

| Operation | Initial syntax | Meaning | First safety boundary |
| --- | --- | --- | --- |
| `rename` | `refactor rename <src> <dest>` | Rename a symbol in place; its declaring parent does not change. | Resolve exactly one declaration and all statically resolvable references; reject collisions. |
| `move` | `refactor move <src> <dest>` | Rename plus move the declaration to a new parent/type/package. | Verify visibility, imports, inheritance, private dependencies, and destination collisions. |
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
| Command shape | `refactor preview`, `refactor apply`, and shorthand `refactor mv`; future `rename`, `pull-up`, `move`, and `change-signature`. | `--help` documents dry-run/apply, branch/version selection, and failure policy. |
| Target syntax | Fully qualified type/member where possible; `Type.member` is accepted only when unique. | Ambiguous targets fail before any write and list candidates. |
| Safety default | Preview is the default; `apply` requires an explicit flag and clean/staged-state policy. | No source changes from a discovery or preview command. |
| Rewrite model | Parse once, resolve symbols, create edits against byte ranges, validate edits, then write atomically. | No overlapping edits or partial files. |
| Resolution | Imports, package declarations, lexical scopes, inheritance, overload descriptors, and project/classpath types. | Unresolved references are surfaced and block mutation unless explicitly overridden. |
| Output | Human-readable summary plus Facet/Figue-serializable JSON report. | Reports include target, files, edits, unresolved cases, and validation results. |
| Propagation | Apply only to the oldest branch; later branches receive the commit through the propagation CLI. | Branch audit runs before and after propagation. |

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
decision; no new parser dependency is added solely by inspection.

### [ ] 1.2 Define the refactoring intermediate representation

- Add typed models for `SymbolId`, `TypeId`, `MethodSignature`, source spans,
  edits, file plans, diagnostics, confidence, and validation status.
- Add Facet derives and stable external names for reports and command options.
- Model edit preconditions with the original file hash and expected source
  span so stale plans fail safely.

**Validation:** round-trip tests serialize/deserialize a preview report and a
stale-plan test refuses to apply after the source changes.

## Phase 2 — Parser, resolver, and edit engine

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

### [ ] 2.3 Implement transactional edits

- Normalize edits, reject overlaps, preserve line endings, and write through a
  temporary file plus atomic rename.
- Generate a unified diff and a structured edit report before applying.
- Support `--check` (validate only), `--diff`, `--output <dir>`, and an explicit
  `--allow-unresolved` escape hatch that still records every unresolved use.

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
- Make `refactor mv` a temporary compatibility alias that maps to `rename` or
  `move` only when its target syntax is unambiguous; print the canonical
  operation in diagnostics and deprecate the alias once callers migrate.
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

## Open questions

- Should `mv` mean rename-only until a destination type is supplied, or should
  move syntax be a separate explicit operation from the beginning?
- Which parser/edit library revision should be pinned after the Arborium/Fable
  spike, and can it preserve comments and malformed-but-recoverable Java?
- Should applying a plan require a clean worktree, or permit unrelated staged
  changes when all target hashes still match?
- Should compatibility overload generation be part of the initial change
  signature operation or a later release?
