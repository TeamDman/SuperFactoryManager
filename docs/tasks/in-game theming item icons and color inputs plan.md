# In-game theming, item icons, and color inputs plan

## Purpose

Make SFM's developer workspace look and behave like a Minecraft-native editor
without baking one visual opinion into every screen. Minecraft `ItemStack`
rendering gives us a large, recognizable icon vocabulary that ordinary code
editors do not have. SFM should use that vocabulary for files and actions while
allowing players and modpacks to replace the defaults. The same preference
layer should own syntax styles and programmatically drawn UI colours.

This plan is client-local presentation work. Theme data must never change SFML
program semantics, server state, action ids, file paths, or review decisions.

## Contextual rule-authoring refinement — 2026-09-06

[Contextual ItemStack preview rule authoring](contextual%20itemstack%20preview%20rule%20authoring%20plan.md)
owns IPR-01–IPR-35/IPR-T0–T5 (including IPR-T3a), the authorized optional ER-S4 first slice of lazy-explorer
X-8e. From a file icon, offer specific rule continuations for suffix, exact name,
basename and prefixes, alongside a generic rule-add command with typed operator
solicitation. Flatten explanations/inspection into individual contextual actions;
the generic Customize icon shortcut is no longer the intended main entry point.
IPR-T3a reuses captured entry details to export a user-shareable rule-generation
prompt with the current operator/command contract and explicit destination theme.
It omits the ItemStack catalogue; copying or obtaining a suggested command never
changes preferences until the user explicitly submits a validated rule.

The existing theme/picker UI remains available. IPR-G3/T4 now store versioned
typed rules in the same TOML authority, preserve prior mappings/unrelated
fields, and expose exact theme identity/revision, inherited vs user rules,
reset, draft-only preview, atomic save/reload and stale-revision rejection.
Completion alone never writes a preference. IPR-T0–T5/T3a are complete under
ER-S4 of [Release review overnight readiness](release%20review%20overnight%20readiness%20plan.md):
live authoring at GUI 2/4, separate-JVM theme hash equality, reset/legacy-map
compatibility and protected-user-theme checks passed. The
[rule guide](../contextual%20itemstack%20preview%20rules%20guide.md) explains the
actual context-menu/picker/prompt flow. Broader asynchronous provider/cache work
is still future work; this is not blanket completion of all theming ideas.

## Integration checkpoint — 2026-07-22

Canonical `1.19.2` now contains the independently developed runtime-theme,
colour-input, and ItemStack-picker tracks through merge commits `15c310234`,
`d2f37933e`, and `aa89e4893`.

- Phases 1 and 2 have a working runtime foundation: immutable theme snapshots,
  semantic colour and syntax roles, registry-safe item icons, TOML loading,
  atomic reload, diagnostics, fallbacks, and last-valid-theme retention.
- Phase 3 now has both reusable typed input primitives. The colour panel accepts
  ARGB/RGBA, HSV, hex, channel, recent-colour, reset, and callback input. The
  item picker supports detailed and dense views, keyboard navigation, tooltips,
  registry search, and SFML wildcard/tag matchers.
- The literal-safe wildcard correction for those shared SFML matchers is tracked
  as Phase 0 of the global comment/review-session plan. It changes AST
  conversion and tests, not the `.g4` grammar.
- The structured theme/icon-scheme editor and TOML write-back were completed in
  the later structured-settings checkpoint below. Semantic click-to-inspect and
  structured syntax-flag editing remain future Phase 3 work.
- Canonical compile and the full Java suite passed after integration; the two
  Windows symlink tests aborted on their existing privilege assumptions.
- Fresh canonical puppet runs accepted runtime-theme, malformed-theme,
  colour-input, and ItemStack-picker/multiplexer captures at 1200x720.

No propagation to later Minecraft-version branches was performed at this
checkpoint.

### Structured settings checkpoint — 2026-07-22

Canonical merge `7e730b206` adds the first end-to-end structured Theme Settings
workflow. All semantic colour and syntax roles plus current file/action icon
mappings are listed; colour and ItemStack roles open the reusable typed
pickers; edits update a live draft preview; and save validates a deterministic
full TOML snapshot before atomically replacing and installing it. Reset,
restore-defaults, and raw-TOML controls share the same runtime service.

The isolated seven-frame puppet proves overview, colour editing and preview,
icon editing and preview, persisted save/reload, and invalid-theme retention.
Syntax style flags are preserved and displayed but do not yet have structured
boolean editors. Semantic click-to-inspect/customize remains future Phase 3
work.

### Comment colourization follow-up

The [global comment selection and review sessions
plan](global%20comment%20selection%20and%20review%20sessions%20plan.md) adds a
new theme consumer: an ordered, user-editable list of comment-style rules.
Rules query hashtags derived from comment strings and independently assign
foreground, background, underline, border, gutter, and overview-marker styles.
Priority is resolved per visual channel so overlapping `#approved`, `#problem`,
`#needs-change`, `#added`, and `#removed` comments remain visible together.

Theme Settings and the reusable colour picker own editing these mappings. The
comment engine owns tag extraction, selection evaluation, interval indexes,
and semantic meaning. Accessibility requires textual/shape indicators and a
hover/details list; colour is never the only carrier of review state.

## Confirmed direction

- Replace textual file markers such as `[J]`, `[CFG]`, and `[DIR]` with rendered
  `ItemStack` icons. Keep an accessible text label/tooltip; the item is not the
  only carrier of meaning.
- Let client actions contribute an item icon or resolve one contextually. File
  actions and file-explorer rows resolve through the same file-presentation
  registry, so an action concerning `Example.java` uses the configured Java
  icon.
- Extension matching remains case-insensitive and longest-suffix-first, so
  `.tar.gz` wins over `.gz`.
- Unknown files, extensionless files, directories, unavailable registry items,
  and invalid theme entries have deterministic fallbacks.
- Syntax styles are mappings from lexer/token ids or stable semantic style ids
  to presentation, rather than a `switch` returning hard-coded
  `ChatFormatting` values.
- Application colours are named semantic roles such as `panel.background`,
  `panel.border.focused`, `text.muted`, `diff.added`, and `timeline.track`, not
  screen-specific anonymous integers.
- Store user-editable schemes as TOML. The repository already contains
  `TomlLexer.g4`/`TomlParser.g4`, NightConfig-based config handling, and a TOML
  text editor; choose one authoritative typed loading path and do not maintain
  two subtly different TOML interpretations.
- Provide both raw TOML editing and structured in-game editors. A malformed
  edit reports diagnostics and leaves the last valid active scheme intact.
- Themes support defaults plus sparse user overrides. Missing keys inherit;
  explicit reset returns to the shipped defaults.

## Proposed configuration shape

```toml
schema_version = 1

[icons.files]
directory = "minecraft:chest"
unknown = "minecraft:paper"
extensionless = "minecraft:name_tag"
".sfml" = "sfm:program_disk"
".java" = "minecraft:book"
".json" = "minecraft:map"
".toml" = "minecraft:comparator"

[icons.actions]
"sfm:palette/open" = "minecraft:compass"
"sfm:file/open" = "minecraft:writable_book"

[syntax.sfml]
keyword = { colour = "gold", bold = true }
string = { colour = "green" }
number = { colour = "aqua" }
comment = { colour = "dark_gray", italic = true }

[colours]
"panel.background" = "#F0202020"
"panel.border.focused" = "#FF55FFFF"
"text.primary" = "#FFFFFFFF"
"text.muted" = "#FFB0B0B0"
"timeline.track" = "#FF59636E"
```

The concrete item choices above are illustrative. Item references are registry
ids with optional future NBT/component data; v1 should prefer plain ids so a
theme cannot smuggle executable or oversized data through an icon.

## Phase 1 — Extract presentation roles

- Inventory hard-coded `ChatFormatting` and ARGB constants in the command
  palette, file explorer, text editors, multiplexer, Draw canvas, timeline,
  source review, and console.
- Introduce immutable `SFMClientTheme`, `SFMColourRole`, `SFMSyntaxStyle`, and
  `SFMItemIcon` values. Rendering code consumes a resolved theme snapshot and
  does not read files while drawing.
- Change `SFMFilePresentation` from a textual icon string to an item-icon spec
  plus accessible short label, description, text style, and fallback.
- Add a reusable item-icon renderer with predictable 16x16 layout, hover
  tooltip, disabled tint, and no stack count unless deliberately configured.
- Let action presentation metadata expose an icon resolver without coupling
  the Brigadier command tree to Minecraft rendering types.

## Phase 2 — Load, validate, and reload TOML schemes

- Define versioned typed schemas for icon, syntax, and colour schemes.
- Resolve item ids only after registries are available. Unknown ids generate a
  precise diagnostic and use the fallback icon.
- Validate ARGB/hex values, semantic role names, token ids, duplicate suffixes,
  and inheritance cycles before installing a scheme.
- Save atomically and retain the previous valid theme on failure.
- Add client actions to open the active scheme, reload it, select another
  scheme, reveal its file, and restore defaults.
- Decide whether the existing ANTLR TOML grammar becomes the shared document
  model or whether NightConfig remains authoritative while ANTLR is used for
  editor diagnostics. Record the choice before implementing write-back.

## Phase 3 — Structured customization panels

- Add a reusable colour-input panel supporting hexadecimal entry, ARGB/RGBA
  channels, preview swatch, recent colours, and a mouse-accessible colour
  field/wheel. The result is a typed colour value; the panel is not coupled to
  a particular preference.
- Refine that reusable panel before the Vox colour-picker flow: expose a
  composable header slot above the editing controls. Preserve the current
  default two-line header as vertically composed centered formatted-text
  panels, including its existing spacing/alignment and compact behavior, while
  allowing a caller to supply another Java-owned header panel.
- Keep the Vox prompt portable and presentation-safe. Rust sends structured
  prompt content only; the Java bridge validates it and renders a local
  centered-text header panel for the slot. The wire contract must not accept
  arbitrary remote Minecraft panels, widgets, renderers or layout instructions.
  Java owns composition, bounds, formatting, accessibility and theme handling;
  Rust owns semantic prompt content and the typed ARGB result.
- Validate the default-header regression, custom Vox-header rendering,
  bounded/centered long and multi-line content at supported GUI scales,
  invalid/oversized prompt handling, typed ARGB round-trip, and terminal
  cancellation/timeout/disconnect behavior with no stale callback. Capture
  both default and Vox-header states in the colour-input puppet.
- Add an item-icon picker backed by the item registry with search, current
  stack preview, fallback/reset, and keyboard navigation.
- Add theme and icon-scheme panels that list semantic properties, open the
  appropriate typed input, preview changes against representative widgets,
  apply atomically, and offer a raw-TOML action.
- Provide an inspect/customize action for UI elements. It identifies the
  semantic colour/icon role under the pointer and opens the corresponding
  property rather than storing a coordinate-specific override.
- Make these panels composable under the screen multiplexer.

## Phase 4 — Observable proofs

- File explorer puppet: directories and representative `.sfml`, `.java`,
  `.json`, `.toml`, compound, extensionless, and unknown files render distinct
  `ItemStack` icons.
- Command palette puppet: an action icon renders as an item and a file action
  inherits the selected file type's icon.
- Theme puppet: switching schemes updates palette, multiplexer, editor syntax,
  timeline, and review colours without restarting Minecraft.
- Colour-input puppet: select a semantic role, modify it through the structured
  picker, apply it, then open the equivalent TOML.
- Invalid-theme puppet: an unknown item and malformed colour show diagnostics
  while the prior valid theme remains active.
- Tests cover longest suffix, fallbacks, token-id lookup, sparse inheritance,
  atomic reload, registry failure, round-trip stability, and accessibility
  labels independent of icon choice.
- Comment-style puppet: edit the colours and channel precedence for overlapping
  `#approved`, `#problem`, `#added`, and `#removed` comments and verify that the
  review workspace updates without changing comment semantics.

## Acceptance criteria

- No file type or client action is forced to use an ASCII pseudo-icon.
- User schemes can customize file/action item icons, syntax styles, and named
  application colours without modifying Java source.
- Every rendered icon retains a textual accessible identity.
- The structured colour and item pickers edit the same typed data represented
  by the raw TOML file.
- Theme changes are previewable and reloadable in game, and invalid changes
  cannot destroy the last valid configuration.
