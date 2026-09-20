# Contextual ItemStack preview rule authoring

**Plan status:** Complete — bounded ER-S4/IPR-T0–T5 slice verified 2026-09-06; broader X-8e remains open
**Primary implementation root:** `D:/Repos/Minecraft/SFM/repos2/1.19.2`
**Last updated:** 2026-09-06
**Intent audit:** Passed 2026-09-06 against the customization/contextual-command discussion and clipboard-assisted rule-authoring follow-up; G1–G4 closed below
**Owning feature:** lazy-explorer X-8e; authorized overnight stretch ER-S4
**Execution owner:** [Release review overnight readiness](release%20review%20overnight%20readiness%20plan.md); core review work and preceding stretches must pass before claiming this slice
**Related plans:** [Explorer](typed%20selections%20relations%20and%20lazy%20explorers%20plan.md),
[theming](in-game%20theming%20item%20icons%20and%20color%20inputs%20plan.md),
[palette](contextual%20input%20actions%20and%20addressable%20explorer%20plan.md).

## How to update this plan

`[ ]` not started; `[~]` in progress; `[x]` verified complete; `[!]` blocked
with exact evidence and an unblocking condition. Keep one implementation focus.
Update task status and evidence together. G1–G4 were closed before public/storage
integration. The contract below is implemented; task evidence distinguishes pure
fixtures, runtime proof and the completed final acceptance checkpoint.
Earlier timestamped/task-stage notes preserve what was not yet proven then;
IPR-T5's final checkpoint supersedes those historical pending statements.

## Intent audit evidence

- **Closure audit, 2026-09-06:** Rechecked all 35 authoritative ledger rows
  against the final T5 proof map, including the separately retained T3a details/
  prompt exports, manual returned-command validation and zero registry-value
  enumeration. No requirement was dropped to finish the stretch. The seven
  work items are complete; broader provider/cache requirements remain explicitly
  outside this bounded completion. All links in this plan and the companion
  guide resolve, and both files have no trailing whitespace. Final operational
  evidence is in the owning overnight plan rather than inferred from test counts.
- **Pass 1 — extraction:** Reread the available user preference/theming question,
  the `abc.json` contextual-command proposal, the request to preserve every atom
  even if deferred tonight, and the clipboard/details/prompt follow-up. IPR-01–26
  separately retain suffix, exact name, basename, every finite prefix, generic
  operation solicitation, Boolean nesting, no `.g4`, deep discovery, flat aspect
  actions, theme authority, persistence and the optional timing qualifier.
  IPR-27–32 retain two distinct clipboard actions, shared evidence, manual chat
  handoff, executable-command instructions, operator-registry documentation and
  explicit exclusion of ItemStack-value enumeration. IPR-33–35 label proposed
  export safety/validation guardrails separately from the user's requirements.
- **Pass 2 — traceability:** Every IPR row maps to T0–T5 (including T3a), G1–G4 or an explicit
  scope boundary. Checked the actual literal-discovery caps, argument/history
  test names, immutable theme maps, picker/save entry points, existing immutable
  `SFMExplorerRowInspection` payload and `SFMExplorerRowCopyDetailsAction` tests.
  `SFMClientActionDispatcherCompiler` registers `sfm action invoke`, not the
  follow-up's illustrative `execute`; T3a exports the actual command contract.
  Public grammar,
  basename/case policy, schema migration and implication rules are gates, not
  invented facts. Origin U/D/A separates user requirements, accepted design and
  reversible agent-proposed guardrails. Clipboard handoff does not reopen the
  deferred in-process AI provider scope.
- **Pass 3 — adversarial omission:** Rechecked flat actions vs generic wrappers,
  exact filename vs exact path, file-kind constraints, finite exhaustive prefixes
  vs unbounded Boolean enumeration, selectable vs executable, cursor edits vs
  suffix loss, captured theme vs ambient focus, and planned vs implemented.
  Fixed the owning XEXP-35/XD-15/RUX-55 references so old Help wording cannot
  silently restore the superseded grouping. Both educational and diagnostic
  content survive. For the clipboard follow-up, separately checked operator
  schemas vs value catalogues, details-only vs instructional export, canonical
  invocation vs execution authorization, actual registry vs stale handwritten
  grammar, and detailed output vs silently truncated essential instructions.
  T3a's sentinel/counter fixtures must exclude ItemStack enumeration even when
  a provider can suggest every item. No changes to review meaning or implicit
  AI spend are allowed.
- **Known source limitation:** None for these current messages. This audit does
  not claim to reconstruct every older compacted exchange; broader intent is
  inherited explicitly from XEXP-27–37, XD-12–15 and the theming plan. No new
  runtime tests were run for this documentation-only change.
- **Documentation validation:** Confirmed 35 unique sequential requirement IDs,
  seven task headings (T0–T5 plus T3a), Work/Validation/Completion contracts for
  every task, and all local plan links. `git diff --check` passed for the
  five touched planning files. These checks prove document consistency, not
  implemented clipboard or rule-authoring behavior.

## Purpose and supersession

An Explorer icon should let the user construct an understandable, reusable
presentation rule through ordinary command-palette completion. For `abc.json`,
the useful shortcuts are predicates over its name, basename, suffix or prefix,
not a coordinate-specific preference or a separate special-purpose dialog.
This is also a proving ground for reusable context-derived argument suggestions.

This refines the earlier ER-S4 proposal, which only opened an existing theme
property through a generic **Customize icon** action. That shortcut is no longer
the intended main entry point. Likewise, the generic **Help** menu entry in
XEXP-35 is superseded as an information grouping: expose each subject-specific
operation directly. Its educational content, semantic commands, typed fallback
evidence, independent bounds action and accessible degraded indicator remain
required; they are not deleted by flattening the menu. Existing Theme Settings
remains useful and is not removed.

## Authoritative requirements and traceability

Origin **U** means explicit user direction from the preference/context-menu/clipboard
messages; **D** means the agent's technical proposal accepted as a good direction,
with exact public syntax still gated; **A** means a reversible agent-proposed
guardrail for the clipboard follow-up. Each row is an independently retained atom.

| ID | Origin / requirement | Work and acceptance |
| --- | --- | --- |
| IPR-01 | U: User preferences and themes participate in selecting the ItemStack representing a path element. | T2/T4: user > mod > default authority; persist in the explicit theme, not an unrelated icon store. |
| IPR-02 | U: Flatten Help into an action for each aspect. | T3: winning-rule explanation, title-screen rendering explanation, item/rule/provider ID copy, geometry inspection and available cache evidence are separately discoverable. No required Help intermediary. |
| IPR-03 | U: The icon has its own contextual meaning, distinct from the row's text. | T3/T5: independent hit target plus equivalent keyboard/narration access; semantic commands, not opaque button-click IDs. |
| IPR-04 | U: Replace a generic Customize icon entry point with rule construction and useful partially specified rules. | T3: bare add-rule continuation and context-seeded continuations lead into the same palette argument workflow. |
| IPR-05 | U: Offer a rule for files ending in `.json`. | T0–T3: retain the literal dot and file-kind constraint; test `.json`, `.JSON`, compound suffixes and directories whose names end in `.json` under the explicit comparison policy. |
| IPR-06 | U: Offer a rule for files with exact name `abc.json`. | T0–T3: equality on the name field; this is not exact canonical path identity and may match files in several directories. |
| IPR-07 | U: Offer a rule for files with basename `abc`. | G2/T0–T3: basename is a distinct structured projection; freeze dotfile/multiple-dot/extensionless behavior with fixtures. |
| IPR-08 | U: Offer files starting with `a`, `ab`, and further prefixes; exhaustive suggestions are a useful possibility. | T1/T3: all finite context-derived prefix choices remain reachable through ranked/paged search, not only hard-coded examples. Generate on Unicode boundaries; do not enumerate unbounded Boolean combinations. |
| IPR-09 | U: A generic `sfm:explorer/itemstack_preview_rule/add` route can solicit operations such as `sfm:string/starts_with a`. | G1/T0/T3: retain these proposed semantic names; the canonical command must also identify the inspected string field and destination theme. |
| IPR-10 | U: Compose operations using `sfm:bool/and` and `sfm:bool/or`; clarify how parentheses work. | T0: fixed-arity typed prefix nesting is the working design; also include unary not. Test nested expressions and partial input. |
| IPR-11 | U: Grow suggestions through the command machinery rather than adding a custom `.g4` grammar. | T0/T1: operator signatures drive parsing/completion; Brigadier remains the command boundary. No `.g4` changes. This is still a small expression language, not a claim to need no syntax. |
| IPR-12 | U: Deep discovery like `sfm:panel/open sfm:input_diagnostics` should benefit the rule route as well. | T1: offer relevant descendant/partially filled continuations at the action boundary, while retaining the bare action and existing panel-open/history behavior. |
| IPR-13 | D: Useful filled examples are variations of one rule-creation operation, not many separately implemented actions. | T3/T5: generic and contextual paths produce the same typed request and saved rule. |
| IPR-14 | D: Selecting/completing an incomplete candidate builds a command; it does not execute a mutation. | T1/T4: selectable completion and executable command are distinct states; only a complete confirmed command writes. Cancel leaves theme bytes unchanged. |
| IPR-15 | D: Suggest according to the expected argument type, and edit an earlier parameter without erasing later ones. | T0/T1: cursor-aware expression frontier, named missing arguments, exact replacement spans and preserved trailing ItemStack/theme arguments. |
| IPR-16 | D: Keep the inspected subject structured rather than reducing it to a string. | T0/T2: typed path, resolver, file/container kind, name, basename, suffixes and known metadata remain separate; unavailable lazy facts are explicit. |
| IPR-17 | D: Registered operator signatures define result/argument types, arity, evaluation and completion. | T0: validated finite operator graph; quoted literal strings are distinguishable from operator IDs. No reflection/eval or arbitrary command execution. |
| IPR-18 | D: Persist a versioned typed expression tree with a canonical command printer. | G3/T0/T4: expression/command/TOML round trips preserve meaning and explainable rule identity; one authoritative theme representation. |
| IPR-19 | D: The destination theme is explicit even when a contextual entry supplies it. | T3/T4: capture its exact identity/revision; switching focus/theme while completing cannot retarget a write. Show the affected rule scope before confirmation. |
| IPR-20 | D: Reuse ItemStack solicitation/picking, theme preview, inheritance/reset and save/reload. | T4/T5: preview is draft-only; committed rules survive restart; reset removes the user override rather than overwriting a mod/default rule. |
| IPR-21 | D: Completion remains bounded and free of filesystem/network/content-analysis work. | T1: opt-in cheap context candidates, query/generation cancellation, depth/node/output budgets and no recursive invocation of arbitrary suggestion providers. |
| IPR-22 | D: Boolean composition requires conservative specificity, not clause counting. | G4/T2: and narrows, or broadens; only supported provable dominance wins. Unknown/incomparable precedence stays explicitly ambiguous; no float-priority or registration-order accident. |
| IPR-23 | D: Rule choice, actual rendering, cache provenance and element geometry are different facts. | T2/T3: separate actions/payloads; requested vs rendered ItemStack and fallback reason remain inspectable and the degraded marker remains non-colour-only. |
| IPR-24 | D: Presentation changes do not mutate paths, source files, actions, server state or review decisions. | T4/T5: unchanged identities and disposable review/source hashes; a new icon is never approval. |
| IPR-25 | U: Preserve this direction even if it is not pursued tonight. | Retain the ledger and owner links independently of execution timing. ER-S4 was explicitly claimed after core/preceding stretch checkpoints, never retroactively added to completed E-1–E-4. |
| IPR-26 | D: Bound the first rule-authoring slice; async/AI enrichment is later. | Scope/T5: deterministic Java-only baseline, no new dependencies, network inference, content crawling or general-purpose programming runtime. Broader X-8e remains open. |
| IPR-27 | U: Offer an independently useful **Copy entry details to clipboard** contextual action. | T3a: build on the existing row-inspection/copy action, enriching its versioned evidence with the typed presentation subject. Keep details-only export directly accessible from row/icon context with keyboard parity. |
| IPR-28 | U: Offer a distinct **Copy prompt for soliciting a new ItemStack preview rule to clipboard**, built from entry details. | T3a: embed the same captured evidence/serializer in a versioned prompt envelope; no competing entry representation or resampling another focused row. |
| IPR-29 | U: Clipboard lets the user paste a detailed payload into a chat application such as Codex, receive an add-rule command, and run it themselves. | T3a/T5: self-contained prewritten instructions, user-supplied desired change, copy/paste/inspect/execute guide; no automatic model call, transmission, response execution or theme write by export. |
| IPR-30 | U: Tell the agent exactly how to form the rule-add command, including its available subcommands, rather than guess. | G1/T0/T3a: derive the current canonical command route and argument contract from registrations/printer. The user's `sfm action execute ...` is illustrative; verified current root is `sfm action invoke ...`. Do not add an alias or teach an unregistered route. |
| IPR-31 | U: Walk available string and Boolean action/operator registries to give the receiving agent deep, detailed instructions. | T0/T3a: export all registered rule-usable operator IDs, descriptions, named/typed operands, arity, composition/quoting rules and supporting subject projections; stable order and schema/registry revision identify the contract. Document finite signatures, not the Cartesian product of expression values. |
| IPR-32 | U: Do **not** include the list of registry ItemStacks or all Brigadier variants of the ItemStack positional parameter. | T0/T3a: explicit schema-only export policy for registry-valued arguments. Describe the item parameter's accepted syntax/type and runtime validation; never enumerate registry members or call its value-suggestion provider to build this prompt. Existing captured icon IDs may appear as evidence, not an item catalogue. |
| IPR-33 | A: Detailed generated instructions must remain bounded, deterministic and honest about completeness. | T3a: cycle/redirect-safe schema traversal, stable ordering, size/node budgets, explicit unsupported-descriptor/over-budget failure rather than silently incomplete grammar. No filesystem/network/content-analysis work; item-registry growth cannot grow prompt output. |
| IPR-34 | A: Entry names/paths/labels and contributed descriptions are data, not authority to instruct a chat agent. | T3a: separated, escaped evidence sections and explicit untrusted-data wording; disclose included metadata/paths, no source contents or secrets acquired automatically, and no clipboard payload in logs. Absent desired scope/item is a question, not permission to invent a broad rule. |
| IPR-35 | A: Export is a snapshot, and a model-produced command remains untrusted user input. | T3a/T4/T5: capture/revision checks and honest copy feedback; expired captures cannot retarget focus. Returned commands undergo normal Brigadier/operator/ItemStack validation and explicit theme confirmation; malformed/unknown/stale input cannot write. Tests use deterministic response fixtures, not a live LLM as the correctness oracle. |

## Pre-implementation foundation (historical source inspection, not runtime acceptance)

- `client/action/SFMClientActionCommandTree.java` already combines action
  boundaries, history, argument frontier/usage and literal-descendant discovery.
  `literalContinuationSuggestions` caps depth at 8 and candidates at 256 and
  deliberately does not call arbitrary argument suggestion providers. It does
  not already generate `.json`/`abc`/`ab` from an inspected entry.
- `SFMClientActionPaletteSuggestionTests` includes
  `fuzzyActionSlotQueryFindsCompleteLiteralContinuationPaths`,
  `compatiblePanelOpenHistoryBoostsTheSceneAcrossDirections`,
  `quotedAndGreedyArgumentHistoryUsesBrigadierRangesWithoutWhitespaceSplitting`
  and `unsuggestedRequiredArgumentPublishesNamedNonActivatableUsage`.
- `client/theme/SFMClientTheme.java` is immutable and has file/action icon maps.
  `matchingFileIcon` currently does case-insensitive longest-suffix lookup;
  `.sfm-review.json` and `.json` are distinct defaults. It is not yet a general
  predicate registry.
- `SFMClientThemeService`, `SFMClientThemeLoader`, `SFMClientThemeTomlWriter`,
  `screen/theme_settings/SFMThemeSettingsModel` and `SFMThemeSettingsPanel`
  provide typed settings, ItemStack picker callbacks, validated save/reload and
  last-valid-theme behavior. They currently edit resolved maps; retained
  per-rule override provenance requires an explicit storage decision.
- `client/screen/explorer/SFMExplorerRowInspection.java` already exports
  `sfm.explorer-row-details/1`: canonical row identity/label, view/filter/focus,
  relation/loading/pagination evidence and diagnostics. The immutable payload
  escapes strings, but does not yet contain the planned full presentation
  subject/operator contract. Preserve existing fields when extending it.
- `client/action/SFMExplorerRowCopyDetailsAction.java` is registered as
  `sfm:explorer/row/details/copy`; it retains bounded immutable captures and has
  an injectable clipboard writer. `SFMExplorerRowCopyDetailsActionTests` proves
  deterministic capture copying and expired-capture rejection;
  `SFMExplorerRowInspectionTests` covers the payload. Reuse this seam rather
  than implement a second unrelated details serializer. The current action
  acknowledges after the writer returns; that is not proof of OS readback.
- `SFMClientActionDispatcherCompiler` registers `sfm action invoke` and named
  action nodes. Rule-add/operator descriptors and prompt export are planned,
  not existing generic help/Brigadier features inferred from that registration.
- `SFMExplorerPresentationRegistry`, `SFMFileExtensionExplorerPresenter`,
  `SFMItemIconRenderer`, `SFMExplorerContextActionRegistry`,
  `SFMPaletteCandidate`, `SFMCommandFrontierAnalysis` and `SFMCommandPaletteScreen`
  are the integration entry points. Paths above are under
  `platform/minecraft/src/main/java/ca/teamdman/sfm/` unless qualified.

## Implemented command and data contract

```text
sfm action invoke sfm:explorer/itemstack_preview_rule/add <theme-target> <predicate> <itemstack>
```

The generic action first solicits the exact theme target, then compatible
predicate operations. The `.json` contextual
candidate pre-fills a predicate and then solicits the ItemStack, using the same
command model. Example predicate:

```text
sfm:bool/and sfm:entry/is_file sfm:entry/has_suffix ".json"
```

This is `and(is_file(subject), has_suffix(subject, ".json"))`. Canonical
`has_suffix` shares the legacy map's non-leading-dot suffix semantics and
allows explicit replacement of that preference. `ends_with(name, ".json")`
remains available as a general string predicate; it is not silently treated
as equivalent for precedence.
`and`/`or` consume two Boolean expressions, `not` one; `ends_with`/`starts_with`
consume two strings; subject projections consume no child expressions. Arity
provides boundaries without parentheses. The printer may show an indented or
parenthesized explanation without changing the canonical one-line command.
The rule subject is each entry being evaluated, not whichever panel happens to
have focus. The input example seeds constants; it is not a permanent binding
to the originating widget or just that one file.

Keep a typed IR carrying stable operator IDs, literal values, argument trees,
schema/version and rule identity. Operators must be pure over declared subject
facts. Do not treat strings as commands. Unknown operators or unavailable facts
produce typed diagnostics/fallback, not a silently true/false coercion. Metadata
enrichment and cache acquisition remain the separate X-8e provider work.

## Clipboard-assisted authoring contract

Keep **Copy entry details** and **Copy rule-generation prompt** as two flat
actions. The latter wraps the former's immutable evidence in instructions; it
does not replace inspection with a model-specific integration. The user chooses
what to share, pastes into their chosen chat application, describes the desired
icon/rule scope, reviews the returned command and explicitly runs it in SFM.
No chat service, SDK, account, background inference or automatic execution is
required. This is distinct from the deferred AI content-to-icon provider.

The envelope contains a purpose/request section, entry-evidence schema and
capture identity, the explicit destination theme and scope constraints, the
canonical rule-add invocation/argument layout, a registry-derived reference,
and the expected response (one copyable command plus a short explanation of
what it will match). Include parser/printer-derived examples of literal escaping
and nested Boolean composition. When intent or a required value is missing,
instruct the recipient to ask rather than manufacture a desired rule. A
complete command is the desired result; catalogue omission is not permission
to invent a valid-looking ItemStack ID.

The registry reference is generated from the same finite typed descriptors
used by T0/T1, not duplicated prose about a fixed built-in list. Include
contributed rule-usable operators when their signatures can be described safely.
Each descriptor states ID, meaning, result type, operand names/types, arity and
literal/composition rules; include relevant subject projections and theme-target
syntax. Enumerating registered **operators/subcommands** is necessary here;
enumerating all values accepted by a registry-valued **argument** is not.
Use an explicit argument-role export policy rather than a fragile spelling
check for a parameter named `itemstack`. Do not call arbitrary suggestion
providers, walk registry members even if represented as command leaves, or
expand all valid command variants. For the ItemStack argument, publish its
actual syntax/constraints and validation boundary only; the inspected requested
and rendered item IDs are allowed as evidence, not a catalogue.

Bound descriptor traversal and output separately from on-screen suggestion
limits: a deliberately detailed clipboard reference must not silently inherit
the palette's short visible-candidate cap. Detect cycles/redirects, cache only
against descriptor/registry revisions, and fail explicitly before replacing the
clipboard if essential grammar cannot be exported completely. Quote/delimit
entry and contributed descriptive text as untrusted data; missing lazy metadata
stays unavailable. No source-file reads, secret discovery, model calls or
clipboard-content logging. Copy feedback must describe what actually succeeded;
revalidate the captured destination and returned command through T4 before any
write. Exact prompt/action names and envelope version are frozen in T0/T3a;
the envelope is `sfm.itemstack-preview-rule-prompt/1`.

## Decision gates before implementation

| Gate | Required decision | Acceptance consequence |
| --- | --- | --- |
| IPR-G1 | Freeze exact action/operator IDs, explicit theme-target addressing, quoted constants and fixed-arity parsing boundaries. The suggested spelling is not an existing API. | Canonical parse/print and partial-input fixtures; no hidden focused-theme write or operator/literal ambiguity. |
| IPR-G2 | Freeze comparison case policy, basename/compound suffix/dotfile rules and Unicode normalization. Preserve current theme suffix behavior or explicitly specify the intentional migration. | Fixtures for `abc.json`, `a.b.json`, `.gitignore`, extensionless names, `.JSON`, surrogate pairs and canonically distinct names. No locale-dependent surprise. |
| IPR-G3 | Choose a versioned rule section in the existing TOML authority and how existing map entries lower into rules. Preserve unrelated fields and old file semantics; identify user overrides vs inherited/resolved values. | Round-trip/compatibility fixtures, atomic save, invalid-edit retention, reset and revision-conflict tests. No parallel competing preference store. |
| IPR-G4 | Define the supported implication/specificity relation and explicit ambiguity display/fallback for Boolean rules. | Permutation fixtures; `A or B` cannot win merely by having more nodes; unknown dominance is not asserted. |

Numeric parser/evaluator/completion limits are a reversible implementation
choice to record at T0/T1 with representative fixtures. They must be explicit
and bounded before runtime exposure. A general solver or arbitrary executable
user functions is outside this slice.

### Gate decisions — 2026-09-06, before implementation

- **G1:** Canonical add route is `sfm action invoke
  sfm:explorer/itemstack_preview_rule/add <theme-target> <predicate> <itemstack>`.
  Theme target is a quoted `file:` URI plus captured SHA-256 revision in one
  argument (`<file-uri>#sha256=<hex>`); it must match the active authority, never
  ambient focus. String literals are double-quoted with JSON escaping; unquoted
  operator IDs are fixed-arity. Built-ins: `sfm:bool/{and,or,not}`,
  `sfm:string/{equals,starts_with,ends_with}`, and
  `sfm:entry/{name,basename,path,resolver,is_file,is_container,has_suffix,is_extensionless}`. Boolean and
  string types are explicit; unknown metadata is unavailable, not guessed.
  Expressions are limited to 8,192 UTF-16 units, 128 nodes, depth 16 and
  1,024-code-point literals; limits apply to parsing and loaded trees alike.
- **G2:** Comparisons use `Locale.ROOT` case folding, preserving existing
  case-insensitive suffix behavior. No Unicode normalization: canonically
  distinct strings remain distinct. Basename removes only the final non-leading,
  non-trailing extension: `a.b.json -> a.b`, `.gitignore -> .gitignore`,
  `a. -> a.`. Suffix candidates include each non-leading non-trailing dot suffix;
  prefixes advance by Unicode code point. File kind comes from structured
  resolver metadata, not a dot in the name.
- **G3:** Keep outer theme schema 1; add an optional version-1
  `itemstack_preview_rules` section storing stable rule IDs, typed recursive
  expression trees and ItemStack representations. Old suffix/kind maps keep
  their meaning, lowered through the evaluator with explicit inherited/user
  provenance. New rule writes change only this section and preserve unrelated
  TOML values. Atomic persistence validates the exact captured file revision;
  malformed/stale/unknown-item writes retain the last valid theme. Reset removes
  only the selected user rule. Tests use a new explicitly selected disposable
  theme authority; existing preview/manual theme files remain untouched.
- **G4:** Layers are user > contributed mod > defaults. Within one layer,
  supported implication proves narrowing: conjunction implies its components,
  disjunction requires both alternatives to imply a target, and same-field
  equality/prefix/suffix relationships are checked directly. Equal predicates
  are equivalent, not strictly more specific. Incomparable matches with different
  icons produce explicit ambiguity and retain a deterministic baseline fallback;
  no clause counts, floating priorities or incidental registration-order wins.
  Unsupported implication is unknown, not a theorem. Tests must exercise rule
  permutations and OR broadening before this is considered proven.

## Work items and validation

All commands below run from the primary repository root with the existing
installed CLI. `test run --filter` is a substring filter, not a regex. Task
completion evidence names the actual test families and commands run.

### [x] IPR-T0 Freeze typed rule contracts and operator parsing

**Completion evidence:** `SFMItemstackPreview{Subject,Operators,Expression,Rules,RuleCodec}`
now define the gate contract. Eight focused tests pass (`stretch-s4-t0-storage`,
53.3 s, zero failures/skips/aborts), covering quoted nested Unicode, trailing item
boundary, malformed/partial/type/budget checks, structured kind/basename/suffix,
permutation-independent authority/implication, explicit ambiguity/unavailability,
contributed operators and versioned typed TOML round trips. Earlier attempts
retain the sandbox cache denial and package-private Unicode helper compile
failure; the helper use was replaced with local strict surrogate validation.
The seven-test predecessor also passed. No runtime registration/theme write yet;
storage conflict/reset integration remains T4, not inferred from codec tests.

**Claim:** ER-S4 begins after the verified review core and preceding stretches.
Inspect current theme/palette/presentation seams, close G1–G4 with exact fixtures,
then implement pure typed contracts before any runtime theme mutation.

**Work:** Close G1–G4 in fixtures; define the minimum X-8e structured subject,
pure typed operators, bounded prefix parser, canonical printer and versioned
IR. Include equality, name/basename projections, file kind, starts/ends-with and
and/or/not. Keep type errors, missing operands and absent metadata explicit.
Give these same descriptors exportable signatures and explicit argument-role
policies so T3a can document grammar without invoking value suggestions.

**Validation:** Add `SFMItemstackPreviewRuleTests`; run
`sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMItemstackPreview`.
Round-trip nested/partial/malformed/oversized expressions and quoted Unicode
constants; prove predicate parsing stops before the following ItemStack arg.

**Completion:** A new agent can implement later tasks from the frozen examples
and typed contract without inventing syntax or storage policy.

### [x] IPR-T1 Extend reusable typed argument and descendant completion

**Completion evidence:** Focused current-source runs passed: typed rules/frontier
12/12, palette suggestions 14/14, choice sessions 7/7 and palette screen 11/11,
all zero failures/skips/aborts (`stretch-s4-t1-*` NDJSON). The first frontier
run exposed an incorrect expected string length in its test (16 vs 17), corrected
to the operator ID length before the green rerun. The opt-in
`SFMClientActionCompletion` seam preserves bare actions and bounds contextual
continuations; typed replacement spans preserve the following ItemStack. An
incomplete choice opens ordinary construction and is absent from the executable
choice dispatcher. Expired choices cannot retarget, and unrelated value-provider
counters remain zero. These are contract/UI-model tests; live icon integration
and runtime evidence remain T3/T5.

**Claim:** Add an opt-in pure action-completion seam and typed cursor frontier;
keep ordinary Brigadier/history behavior unchanged for actions that do not opt in.
Incomplete contextual choices transition into the ordinary command builder rather
than being dropped or made executable. No persistence during completion.

**Work:** Add an opt-in cheap context-candidate seam; preserve literal discovery,
bare-action ranking and history. Traverse only bounded permitted continuations.
Track the cursor's typed expression frontier and replacement range, including
middle-argument edits, incomplete nesting and preserved later arguments.

**Validation:** Extend `SFMClientActionPaletteSuggestionTests` and
`SFMCommandPaletteScreenTests`:

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientActionPaletteSuggestionTests
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMCommandPaletteScreenTests
```

Add zero-I/O counters, unavailable-action checks, stale-provider
reply tests and invalidation on context change. Verify `a` and `ab` choices are
reachable without generating the Cartesian product of all expression trees.

**Completion:** Incomplete continuations are selectable/completable, missing
operands are named, Execute remains unavailable, and existing panel-open
discovery/history remains correct. Candidate generation never mutates a theme.

### [x] IPR-T2 Evaluate local rules with explainable authority and precedence

**Completion evidence:** Final rule family 19/19 passed in 54.6 s
(`stretch-s4-t2-final`); presentation registry 5/5, file presentation 14/14,
theme loader 8/8 and settings model 2/2 passed (`stretch-s4-t2-green-*`).
Two earlier presentation regressions exposed generic marker takeover; default
file rules now preserve unrelated resolver presentations. Legacy maps are
restricted to filesystem or explicitly declared file/container subjects.
Immutable theme snapshots retain authored rules and explicit map provenance;
raw writer/loader and structured drafts preserve them. Review entries publish
semantic name/kind separately from ordering prefixes. A 2,048-entry in-memory
decision cache invalidates on theme/registry replacement; no resolver reads.
Ambiguity/unavailability retains the baseline with an exclamation/narration.
All protected baseline hashes still match. Repository-wide diff whitespace
check only reports the preexisting generated datagen cache CRLF lines.
New-rule mutation UI/persistence remains T3/T4; no user theme was changed.

**Claim:** Integrate immutable rules and explicit legacy-map provenance into the
theme snapshot, then resolve presentation without filesystem work. Add pure
`sfm:entry/has_suffix <string>` and `sfm:entry/is_extensionless` descriptors to
preserve legacy compound-suffix/dotfile semantics exactly. Legacy maps first
select their own most-specific rule; only that result participates in the
user/mod/default comparison, so a user `unknown` fallback does not override known
extensions. Explicit new rules replace an equivalent lowered user predicate.
Old explicitly written map values count as user choices; new full-snapshot
serialization writes only retained explicit map keys, not inherited defaults.

**Work:** Adapt existing suffix/kind defaults into the common evaluation path;
resolve user/mod/default layers and supported predicate dominance. Retain
unavailable/ambiguous candidate evidence and requested/resolved icon distinction.
Invalidate presentation on committed theme revision without resolver reads in
render/completion paths. No async inference provider is required here.

**Validation:** Extend the planned `SFMItemstackPreviewRuleTests`, existing
`SFMExplorerPresentationRegistryTests` and `SFMExplorerFilePresentationTests`.
Prove file vs directory, exact name vs exact path, basename/prefix/suffix overlap,
or/and behavior, registration-order invariance and theme replacement.

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMItemstackPreview
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerPresentationRegistryTests
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerFilePresentationTests
```

**Completion:** Each icon choice has an inspectable typed rationale; unknown
facts or precedence never masquerade as established truth.

### [x] IPR-T3 Expose flat icon actions and context-seeded rule continuations

**Completion evidence:** 27/27 rule/action/persistence tests passed in 58.4 s
(`stretch-s4-t3-json-boundary`). The preceding run caught a real JSON-vs-Brigadier
escape mismatch in theme tokens; the theme reader now shares the JSON literal
contract. Palette suggestions 14/14, palette screen 11/11 and explorer context
2/2 passed; the choice-session regression command also exited zero. Separate
icon and text hit regions, keyboard parity, immutable details, typed middle
argument edits and exact captured authority are covered. Icon aspects expose
rule/rendering/IDs/geometry/cache separately. Runtime screenshots, localization
datagen and complete draft/picker/reset acceptance remain T4/T5, not inferred
from these tests. Context menus show at most 16 initial Unicode prefixes;
typed literal completion reaches the remaining representable prefixes (up to
the 1,024-code-point literal limit), with no Cartesian expansion.

**Claim:** Wire captured icon facts, separate semantic actions and typed command
construction. Explicit authority capture and the narrow revision-checked theme
write adapter from T4 are integration prerequisites of a real add action, so
implement those under this focus rather than exposing a placeholder executable.
T4 still owns its complete picker/preview/reset/failure acceptance checkpoint;
T3a still owns prompt generation and its non-enumeration proof.

**Work:** Add individual explanation/copy/bounds/cache actions to icon targets,
plus the generic and finite context-derived rule continuations. Localize labels
without changing canonical commands. Capture the exact entry and theme identity;
show what set of entries the proposed rule means. Pre-filled suggestions enter
command construction rather than running incomplete actions or disappearing
from constrained context menus because they are not yet executable.

**Validation:** Add `SFMItemstackPreviewRuleActionTests` and extend existing
palette/context-action tests. Assert equivalence of generic and pre-filled
requests, complete copyable commands, icon/text hit separation, keyboard access,
Unicode prefixes and no captured-target retargeting after focus/theme changes.
Run `sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMItemstackPreview`
and both T1 regression commands after the context integration.

**Completion:** Right-clicking `abc.json` visibly offers its suffix, exact name,
basename and prefixes plus the generic route; information aspects are separate
actions, with no mandatory Help or Customize wrapper.

### [x] IPR-T3a Export entry evidence and a registry-informed rule-authoring prompt

**Completion evidence:** 32/32 focused tests pass (`stretch-s4-t3a-built-descriptor`,
58.3 s). Shared row inspection 2/2, row copy 2/2, theme loader 8/8 and structured
theme model 2/2 pass (`stretch-s4-t3a-SFM*`). Export tests cover exact common
evidence, hostile quoted Unicode, contributed typed signatures, immutable capture,
clipboard failure/expiry, cycle/redirect/unknown-schema/size rejection, real
Brigadier example round trips and identical payloads against instrumented
1/100,000-value registries whose children/suggestion providers throw if touched.
No item enumeration/evaluator calls occurred. The first compile attempt used a
Brigadier builder instead of its built node; corrected before the green run.
Live clipboard and returned-command acceptance remain T5.

**Claim / frozen envelope:** `sfm.itemstack-preview-rule-prompt/1`, copied by
`sfm:explorer/itemstack_preview_rule/prompt/copy <icon_capture>`. The payload is
JSON with instructions, desired-change placeholder, exact shared details string,
finite typed operator descriptors and canonical argument schemas. Explicit
REGISTRY_VALUE policy stops traversal before children/value suggestions;
redirects/unknown layouts fail explicitly. Maximum payload is 262,144 UTF-16
units and descriptor traversal is bounded to 128 nodes. No export cache, model
calls or automatic command execution. These implementation decisions are now
under test; they are not yet a completed clipboard/runtime checkpoint.

**Work:** Reuse `SFMExplorerRowInspection`/`SFMExplorerRowCopyDetailsAction` for
the details-only action and common captured evidence. Add the separate localized
prompt-copy action through `SFMExplorerContextActionRegistry`, available on the
same subject via mouse and keyboard. Freeze a versioned prompt envelope and
derive its command/operator reference from T0 descriptors and canonical printer.
Include destination, scope, quoting/composition instructions, unknown-value
handling and user-run handoff. Keep untrusted data separate from instructions.
Implement bounded schema traversal and schema-only ItemStack policy; perform no
enumeration or value-suggestion calls. Preserve capture identity across focus
changes, diagnose expiry/unsupported descriptors/size limits, and emit success
only after a successful clipboard write. Never mutate the theme or call a model.

**Validation:** Add `SFMItemstackPreviewRulePromptTests` and extend the existing
row-inspection/copy and context-action tests:

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMItemstackPreview
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerRowInspectionTests
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMExplorerRowCopyDetailsActionTests
```

Fixtures must prove shared evidence equivalence, escaped hostile/Unicode names,
immutable target/theme capture, localization without canonical-ID translation,
clipboard-write failure/expiry, and zero mutation/network/content-read counters.
Register an extra string/Boolean operator and assert its actual signature appears;
rename/change a descriptor and verify no stale handwritten grammar survives.
Use instrumented registries with 1 vs 100,000 synthetic ItemStack values: prompt
content/size stays unchanged (same captured icon), sentinel item values are
absent, and registry enumeration/value-suggestion counters stay zero. Include a
provider that throws if queried, renamed registry-argument nodes, redirects,
cycle/size limits and explicit incomplete-export failure. Parse/printer-round-trip
all executable prompt examples and a deterministic sample agent response through
the real command contract without executing; unknown items/operators fail normal
validation. No external LLM is needed to prove the generator correct.

**Completion:** Either clipboard action works independently; the prompt is
self-contained for a recipient without repository access, teaches only the
available rule contract, omits the ItemStack catalogue, and cannot apply a rule
or disclose source contents merely because the user requested an export.

### [x] IPR-T4 Persist confirmed rules through the existing theme/picker service

**Completion evidence:** 36/36 focused rules/draft/persistence tests pass in
55.8 s (`stretch-s4-t4-contracts`), including an injected failure at the actual
theme-writer boundary with unchanged bytes, authority and active snapshot.
Shared picker 28/28, palette screen 11/11, theme 8/8 and settings 2/2 pass
(`stretch-s4-t4-SFM*`). Typed draft creation does not publish a theme; controls
construct canonical pick/copy/add/close commands; reset rejects inherited rules.
Closed pickers suppress later confirmation. The incomplete palette handoff now
rebinds its transient validity witness while retaining the immutable icon capture.
Actual mouse/clipboard/save/cancel/reset and independent-JVM durability remain
the explicit T5 acceptance gate; no live claim is inferred from these fixtures.

**Claim:** Add optional typed draft preview and existing ItemStack picker routes,
plus exact single-authored-rule reset. A confirmed add/reset is an exact file
transaction: normal dismissal of its transient palette is not cancellation.
Unconfirmed picker/draft callbacks must still revalidate the captured origin,
registry and authority. No worker thread may inspect mutable Minecraft UI state.
The earlier T3 narrow adapter already proves stale/cancelled/malformed retention;
this checkpoint adds actual write-failure and UI lifecycle evidence.

**Work:** Reuse ItemStack solicitation/picker and typed theme draft preview.
Validate the complete command, explicit destination revision and rule before
atomic persistence. Expose inherited vs user values and reset. Revalidate after
async picker return or panel closure; a stale operation cannot overwrite a
different/newer theme. Preserve unrelated theme properties and last-valid state.

**Validation:** Run the new rule family and existing theme families:

```powershell
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMClientThemeTests
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMThemeSettingsModelTests
sfm-propagate-changes.exe test run --branch 1.19.2 --filter SFMItemstackPreview
```

Fault-inject
cancellation, invalid items, stale revisions, malformed storage and write
failure. Test existing map compatibility and persisted reset/reload.

**Completion:** Only explicit successful Execute commits; cancel leaves bytes
unchanged. Both authoring routes and raw/structured theme editing share one
authority and survive restart with the same rule meaning.

### [x] IPR-T5 Prove the bounded user journey and update readiness

**Final checkpoint, 2026-09-06 15:44 UTC:** All T0–T5/T3a contracts and IPR-01–35
are covered by the proof map below. The configured installed-worker integration
passed **1/1**, zero failures/skips/aborts, in 1m11s; five real worker requests
completed and the worker closed normally (`final-installed-worker.ndjson`,
`final-installed-worker-java.log`). Together with the full run below, all
1,820 tests passed; the opt-in abort is not counted as a pass by itself.

After all production changes, a fresh adaptive read-only review run passed
31 virtual-input steps in 5m41s (JVM **11048**, normal exit 0), gallery
`sfm-title_screen-20260906-114145-150`. It reopened the 2,921-comment core copy,
read the full Unicode note, followed its exact SFM.java match, reused both
existing preview tabs, and used the saved-work menu/toolbar repeatedly while
retaining queue generation 10 and all 131 materialized queue rows. Warm
Show current dispatches were 1.540/1.513 ms; those are observations, not a global
performance guarantee. Figures 16/20/27/30 were inspected with View Image.
Value text remains an unwrapped canvas: long lines require horizontal panning.
The duplicate Escape inputs at steps 3/13 opened the normal close-choice menu;
Cancel returned to the workspace. These were exploration corrections, not
application failures or hidden model mutations.

`final-review-smoke.ndjson`, `final-review-smoke-java.log` and
`final-readiness.json` under `platform/minecraft/build/rno-readiness-20260906`
record the final proof. All 19 protected dependency/review/theme files match
baseline. The disposable review itself retains SHA-256
`48EBC5DD13FCCDC8A0A6B3D8F985C0C014CEE48BD517851D3C88C099FA5A9B58`.
The final scoped process inventory is empty. Installed CLI PATH/hash and version
were reverified after the last source edit; **User install required: no**.
See the overnight plan's final operational checkpoint for full executable hashes.
Datagen ran after the last localization edit, and the final tests/puppet rebuilt
current Java afterward. Guides and the in-game changelog are updated. No commit,
propagation, dependency change, model call, OS cursor injection or human review
approval occurred.

**Requirement-to-proof map:** Test names below are actual families in
`platform/minecraft/src/test/java`; live authoring/resume means the completed
three-game proof immediately below, not a mock-only assertion.

| Requirements | Evidence |
| --- | --- |
| IPR-01, IPR-18–20 | Theme, Persistence, AtomicWrite and Draft families under `SFMItemstackPreview*`; populated legacy-theme replacement/reset regression; live Save/Cancel/reset and independent JVM hash equality. |
| IPR-02–04, IPR-09, IPR-13 | `SFMItemstackPreviewRuleActionTests`, Explorer context/inspection families; live flat icon menu, generic/context continuation, picker and common draft/command route. |
| IPR-05–08, IPR-10–11, IPR-16–17, IPR-22 | `SFMItemstackPreviewRuleTests`, Completion/Theme families: typed fields, Unicode prefixes, case/suffix/basename, fixed arity, bounded JSON literals, unavailable facts and permutation-independent conservative specificity. Live suffix/narrowed-name/reset exercise. |
| IPR-12, IPR-14–15, IPR-21 | `SFMItemstackPreviewCompletionTests`, palette suggestion/screen and choice-session families; live Tab continuation and earlier-literal edit retaining ItemStack; incomplete choices do not execute. |
| IPR-23 | Rule/presentation/inspection tests and separate live rule explanation; requested/rendered IDs, fallback, geometry and cache remain separate actions. |
| IPR-24 | Theme/persistence source-identity tests, live source hashes, all protected review hashes and final read-only review hash; icon writes are presentation-only. |
| IPR-25–26 | Preserved 35-row ledger, core-first claim at 13:10 UTC, local Java implementation, frozen dependency evidence; async/content/AI/cache expansion explicitly deferred. |
| IPR-27–32 | `SFMItemstackPreviewRulePromptTests` plus shared row inspection/copy families; actual clipboard readback of both exports and common evidence in live journey. Real command/operator descriptors; 1 vs 100,000 registry sentinel fixtures prove zero catalogue/value-provider enumeration. |
| IPR-33–35 | Prompt/action/persistence fixtures for export budgets, cycles/redirects, unknown schemas, hostile data, clipboard failure/expiry, stale authority and invalid returned input; live copied command refined/submitted through ordinary validation and explicit Save. No external LLM oracle. |

**Runtime acceptance, 15:31 UTC:** Complete authoring passes at `1920x1080@2`
(1m46s, gallery `sfm-title_screen-20260906-112347-188`, 12 figures plus three
JSON artifacts) and `1920x1080@4` (1m43s, gallery
`sfm-title_screen-20260906-112840-996`). Each uses its own new DEFAULT_TOML-based
fixture, real icon menu/clipboard/picker/palette/Save/Cancel/reset inputs, an
earlier-parameter edit retaining the later item argument, and two split
Explorers. View Image inspection of the menu, draft/saved and split figures
confirms readable large-scale controls and actual title-screen chest/bell
rendering. The long canonical command is scrollable body content, not a
replacement for the independently visible scope/destination/Save controls.

An independent resume run passes in 1m34s, gallery
`sfm-title_screen-20260906-112640-790`: source JVM **36732**, resume JVM **41948**,
same theme SHA-256 `8379ddaeb73a3439ea7713ca214f88e9c89bdd9a0ab5ad9a768b4c641370b6ad`,
same one surviving JSON rule, unchanged abc/abd/notes source hashes. The
unrelated TOML `keep = "retained"` and explicit legacy mappings survive writes.
All three games exit normally. Existing third-party startup diagnostics
(Mixin minVersion, AE2 guide page, Industrial Foregoing texture) remain visible;
the runtime success is not a claim that all modpack logs are error-free.

Final full suite after production edits: **1,820 found, 1,819 passed, 0 failed,
0 skipped, 1 opt-in abort**, CLI exit 0 in 1m20s (`final-full.ndjson` and
`final-full-java.log`). The read-only panel family is **13/13** and the complete
rule family **38/38**. The configured worker, adaptive review and final
protected-data/tool/process checks are completed in the final checkpoint above.

**Read-only inspection close, 15:21 UTC:** Live-5 identified the supposedly
nested palette as a vanilla ConfirmScreen left by Escape from the read-only
rule explanation. Its glyph projection omits a terminal newline, so the
general text close callback incorrectly treated the view as unsaved. The
panel adapter now closes read-only documents directly, consistent with its
existing clean-read-only lifecycle state; editable contexts still use the
unchanged confirmation/save callback. A focused regression covers the trailing
newline and panel-only closure, and the puppet now asserts immediate return to
the Explorer after closing the explanation. This is a production fix, not a
test that auto-confirms discarding content. Live-5 Java log is retained.

**Acceptance follow-up, 15:17 UTC:** The first full suite passed 1,817 of 1,818
tests with zero failures and the separately opt-in installed-worker test
aborted, not counted as a pass (`stretch-s4-full.ndjson`). Datagen regenerated
the neutral draft heading (`stretch-s4-final-datagen.ndjson`). Live-3/4 reach
picker, draft, actual Save, explanation, Cancel and earlier-parameter editing;
the new puppet's synthetic Shift selection assumption was replaced by ordinary
cursor/Backspace inputs. Live-4 then found a nested-palette dismissal assumption;
the fixture now drains visible palettes through their normal Escape route and
records each resulting screen. These remain partial runs, not completion.

Inspection also found a production compatibility gap: contextual suffix rules
used `ends_with(name, suffix)` while explicit legacy theme maps lower to
`has_suffix(suffix)`, producing conservative ambiguity in a populated theme.
The new `contextualSuffixReplacesPopulatedThemePreferenceAndResetRestoresIt`
fixture fails red for `abc.json` (`stretch-s4-populated-theme-red.ndjson`,
7 passed/1 failed). Context suffix generation now uses canonical lowercased
`has_suffix`, so equivalent replacement/reset needs no new priority rule.
The runtime fixture now starts from DEFAULT_TOML, including explicit legacy
preferences and an unrelated table. Focused green passed **38/38**, zero
failed/skipped/aborted (`stretch-s4-populated-theme-green.ndjson`); complete
runtime proof remains pending. All evidence paths are under
`build/rno-readiness-20260906`.

**Runtime checkpoint, 14:59 UTC:** Datagen completed normally in 1m03s
(`stretch-s4-datagen-green.ndjson`); new main/gametest sources compile. The first
puppet stopped before interaction because its newly created theme fixture used
`schema` instead of `schema_version`; corrected without modifying any user
theme. The second independent run proves the actual icon context menu and both
clipboard actions, including exact OS clipboard readback and the common entry
payload embedded in the generated prompt. It then exposed a puppet assumption:
choice suggestions contain the command tail, not the full canonical invocation.
The assertion/selection was corrected; this is not a production parser defect.
`stretch-s4-live-1/2` logs and live-2 Java log retain failures. The inspected
`rules-icon-menu` screenshot shows localized flat actions and the title-screen
directory chest. The complete authoring/restart journey remains pending; do not
mark it complete from these partial results. The draft guide is
`docs/contextual itemstack preview rules guide.md`.

**Claim:** Build a new virtual-input journey and independent resume puppet over
new ignored theme/source fixtures. Validate real context actions, detailed
clipboard exports, continuation-to-builder, existing picker, draft Save/Cancel,
middle-argument refinement, exact reset, split rendering and restart. Preserve
all manual/previous acceptance themes and reviews. Regenerate localization and
run full checks after the final relevant edit before advertising readiness.

**Work:** Add a focused virtual-input puppet using the existing screenshot
seams; no OS cursor movement. In a disposable theme/review, create a `.json`
rule, refine/replace a name or prefix operand while retaining the selected
ItemStack, inspect why it wins, cancel another draft, reset and restart/reload.
Copy details and the prompt from the icon context, assert the clipboard payload,
and manually submit a deterministic returned-command fixture through normal
palette validation. This proves the user-mediated route without a chat-service
dependency; document which evidence/paths the user is about to share.
Include title-screen and supported GUI-scale/split evidence. Build after the
last change, update the in-game changelog and write a short guide.

**Validation:** `sfm-propagate-changes.exe run compile --branch 1.19.2`;
`sfm-propagate-changes.exe test run --branch 1.19.2` plus focused families.
The registered authoring puppet is invoked as follows; its resume companion
requires a prior successful authoring witness in the same preview instance:

```powershell
sfm-propagate-changes.exe puppet run sfm:title_screen_itemstack_preview_rules --branch 1.19.2 --variant 1920x1080@2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/itemstack-preview-rules.ndjson
sfm-propagate-changes.exe puppet run sfm:title_screen_itemstack_preview_rules_resume --branch 1.19.2 --variant 1920x1080@2 --wait-for-build-lock --log-filter info --log-file platform/minecraft/build/itemstack-preview-rules-resume.ndjson
```

Both commands were run successfully with the unique `stretch-s4-*` log paths
recorded above. The authoring command also passed at `1920x1080@4`.

**Completion:** All IPR rows have proof, the theme is durable, source/review
identities are unchanged, and the normal mouse/keyboard route works without a
companion. Record existing test-environment failures separately. No later-MC
support or broader X-8e completion is inferred from 1.19.2 acceptance.

## Scope, sequencing and overnight relationship

T0 -> T1/T2 (serial unless explicitly assigned independent owners) -> T3 -> T3a -> T4
-> T5. ER-S4 now means this bounded first X-8e rule-authoring slice, not merely
editing an existing icon-role value. It is larger than the original proposed
shortcut. The September 6 overnight goal now explicitly includes T0–T5 and the
separately addressable T3a clipboard slice as its final optional stretch. Claim
the slice only after the core and preceding stretch checkpoints pass; otherwise
leave it unstarted. The completed exploratory goal stays complete. This
supersedes the earlier planning-only/no-goal status, not the bounded scope.

The review core ER-F1–ER-F4 was completed first. General rule
authoring beyond the listed operators, arbitrary code evaluation, AI/content
inference, cache browsing where no provider exists, structured archives X-8f,
review approval/migration changes, and cross-version propagation are not part
of this slice. Keep optional provider extension points without implementing or
silently paying for those providers.

## Risks and operational acceptance

- Predicate explosion: T0/T1 bound depth, nodes, completion work and visible
  results; finite prefix candidates stay discoverable without infinite trees.
- Misleading specificity: T2 proves only supported dominance and reports ties;
  AND/OR node counts are not entropy or authority measures.
- Theme data loss/retargeting: G3/T4 use one versioned authority, captured
  identity/revision, atomic writes and cancellation/conflict fixtures.
- UI mutation through suggestions: T1/T3 separate completion from execution.
  No string evaluation or opaque widget command stands in for the rule.
- Misleading or bloated agent prompt: T3a derives the finite operator contract,
  excludes registry-value catalogues even behind Brigadier providers, and fails
  honestly on incomplete export. Registry-count/zero-enumeration fixtures prove
  the boundary. Quoted data is not instruction; generated commands still need
  normal validation and explicit user execution.
- Scope inflation: T5 closes only this bounded authoring slice; async provider,
  cache and broader presentation requirements stay under X-8e.
- Apply [goal execution and testing readiness guidelines](goal%20execution%20and%20testing%20readiness%20guidelines.md)
  throughout implementation: frozen dependency declarations/lockfiles, no new dependencies
  or clones, no Gradle, current tooling proof after the final edit, scoped process
  lifecycle, disposable user data and no OS pointer injection. No push, commit,
  propagation or release approval is authorized by the overnight goal.

## Overall completion criteria

- [x] G1–G4 are closed with exact canonical, data and comparison fixtures.
- [x] T0–T5, including T3a, each carry focused and appropriate runtime evidence.
- [x] IPR-01–35 map to proof or a user-approved explicit scope revision.
- [x] Contextual shortcuts and generic entry produce identical typed rules.
- [x] Details/prompt exports share captured evidence; generated instructions
  match registered grammar without enumerating ItemStack values or calling AI.
- [x] Cancellation/restart/conflict preserve theme and review integrity.
- [x] Final guide, changelog, installed-tool status and support scope are accurate.
- [x] Broader X-8e provider/cache work is not mislabeled complete by this slice.
