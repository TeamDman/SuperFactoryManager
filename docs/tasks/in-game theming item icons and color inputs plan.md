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
- The structured theme/icon-scheme editor, semantic role inspection, and TOML
  write-back that connect those primitives remain future Phase 3 work.
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

## Acceptance criteria

- No file type or client action is forced to use an ASCII pseudo-icon.
- User schemes can customize file/action item icons, syntax styles, and named
  application colours without modifying Java source.
- Every rendered icon retains a textual accessible identity.
- The structured colour and item pickers edit the same typed data represented
  by the raw TOML file.
- Theme changes are previewable and reloadable in game, and invalid changes
  cannot destroy the last valid configuration.
