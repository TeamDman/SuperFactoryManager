# Java Vim-style editor exploration

A limited Java modal editor is feasible as a small isolated prototype. A full
Java implementation of Neovim's important behavior is a separate compatibility
project, even with plugins removed. This change adds the explicitly named
`sfm:vim_modal_experimental` registry choice on development 1.19.2/1.19.4.
It is not actual Neovim and remains opt-in: the preferred default is unchanged.
The picker commit is independent; the modal commit can be reviewed separately.

## Reference and licensing inspection

The user explicitly authorized cloning the official Neovim repository for
reference. `/workspace/neovim-reference` is a shallow clone of
`https://github.com/neovim/neovim.git` at
`7b87ce822e6fd45a4d9993599c2854afdb0dc4dd` (2026-10-23,
`fix(treesitter): injection clipped to an empty region parses whole buffer`).
The reference was read, not built or installed. No source was copied or
translated into this implementation.

The pinned [LICENSE.txt](https://github.com/neovim/neovim/blob/7b87ce822e6fd45a4d9993599c2854afdb0dc4dd/LICENSE.txt)
declares Apache-2.0 except for contributions under the included Vim license;
it also identifies runtime files with their own header conditions. SFM's root
license is MPL-2.0. This is a record of the declarations, not a blanket finding
that every Neovim file may be reused under SFM's license. Any future source reuse
requires file-level provenance and applicable notice/license review first.

## What the reference shows

- [Development architecture](https://github.com/neovim/neovim/blob/7b87ce822e6fd45a4d9993599c2854afdb0dc4dd/runtime/doc/dev_arch.txt)
  describes C core, Lua core/stdlib and runtime components. Removing optional
  plugins does not remove the native core or its state machinery.
- [normal.c](https://github.com/neovim/neovim/blob/7b87ce822e6fd45a4d9993599c2854afdb0dc4dd/src/nvim/normal.c),
  `state.c`, `insert.c`, `ops.c`, `input.c` and `input_cmdatom.c` divide mode
  dispatch, insertion, operators, input processing and repeatable command
  capture. Normal-mode behavior is more than remapping letter keys to arrows.
- [Motions/operators](https://github.com/neovim/neovim/blob/7b87ce822e6fd45a4d9993599c2854afdb0dc4dd/runtime/doc/motion.txt)
  include multiplied counts, doubled line operators, inclusive/exclusive
  ranges and exceptions at line boundaries. These semantics need explicit
  tests rather than inferred compatibility.
- [Undo](https://github.com/neovim/neovim/blob/7b87ce822e6fd45a4d9993599c2854afdb0dc4dd/runtime/doc/undo.txt)
  and `src/nvim/undo.c` describe grouped changes, cursor restoration, alternate
  branches and persistence. A bounded linear snapshot history is a useful
  prototype but not that system.
- `src/nvim/api/ui.c`, `buffer.c` and `memline.c` provide further boundaries for
  an actual engine/frontend design. An external embedded Neovim process would
  offer real semantics but introduces a runtime, RPC, UI-grid mapping, lifecycle
  and distribution decisions. That path was not authorized for implementation.

## Reuse within SFM

V1 exposes a protected multiline widget with public text/caret/selection
operations. It already owns SFML highlighting, intellisense, scroll behavior,
settings access, disk save callbacks and unsaved-close confirmation. It does not
expose a reusable undo API. V2's separate `TextEditContext` has cursor/action
machinery and unfinished multiline handling. V3 has an indexed glyph/document
model, exact document checkout, selection and document-history integrations;
those are wider shared surfaces used by Explorer.

The prototype subclasses V1 and leaves all those existing implementation files
unchanged. A pure Java `SFMVimModalState` owns modes, motions and bounded history.
Insert delegates to the ordinary V1 input handlers and captures their exact
result. Normal consumes printable commands through `charTyped`, avoiding a
second insertion from the same key event. Save and close use V1's existing
context. Reinitialization preserves text, caret and modal state. Normal-mode
paste, delete, tab and completion shortcuts are suppressed; read-only contexts
cannot enter Insert or apply edits. The registration does not claim V3's
read-only panel capability.

Shared touchpoints are only `SFMTextEditors` for the extra registration,
feature/source catalogs, language entries and changelog. There are no Explorer,
autocomplete-ranking, V3-widget, or V1-widget implementation edits.

## Reviewable prototype scope

| Input | Implemented behavior |
| --- | --- |
| `i`, `a`, `I`, `A` | Enter Insert at cursor, after character, first nonblank, or line end |
| Escape | Leave Insert or cancel a pending `d`; keep the document open |
| `h j k l`, arrows | Code-point horizontal and logical-line vertical motions |
| `0`, `$`, Home, End | Start/end of logical line |
| `w`, `b` | Small word/punctuation/whitespace-class motions |
| `x` | Delete one code point without crossing a newline |
| `dd` | Delete a logical line; unsupported `d` continuations cancel |
| `u`, Ctrl+Z | Undo; an Insert session is one group |
| Ctrl+R | Redo |
| Shift+Enter / Done | Existing save-and-close flow |
| Cancel button | Existing unsaved-close confirmation |

History retains at most 100 edits, clears redo on a new edit, and is in-memory.
The model handles UTF-16 surrogate pairs as code points, not grapheme clusters
or terminal display cells. Vertical columns are code-point columns, not Vim's
tab-expanded screen columns. Insert-mode editing follows V1's shortcuts.

Not implemented: general operators (`dw`, `cw`, `yy`), counts, registers/put,
visual modes, text objects, dot repeat, macros, search, Ex commands (`:w`, `:q`),
mappings, options/`iskeyword`, marks, splits, persistent or branching undo,
Neovim RPC, Lua, plugins, terminal emulation, or full Unicode grapheme/display
semantics. Unknown Normal commands do nothing; no compatibility is implied.
Normal mode currently uses mouse access for footer controls rather than Tab
focus traversal. The ordinary selection/caret is rendered, not a Vim block
cursor. Unsupported multi-key command sequences are not parsed as Vim grammar.

## Validation and next decision

Fourteen pure Java JUnit regressions pass: grouping and undo/redo, exact
whitespace and Unicode retention, surrogate boundaries, line motions, word
classes, first/middle/last/empty-line deletion, pending-command cancellation,
read-only commands, redo invalidation, native widget result capture and the
100-entry history bound. These run the production controller. They do not run
Minecraft input dispatch or rendering, and no differential parity with a running
Neovim executable is claimed.

The native build blocker and unchanged CLI/lock posture are recorded in
[dynamic-editor-picker.md](dynamic-editor-picker.md). Full screen compilation,
actual registration, keyboard/character event ordering, resize and live save
behavior remain unverified in a client. Before accepting the prototype, launch
that documented development client, choose `sfm:vim_modal_experimental` from
the manager and exercise each supported command, saving/reopening a multiline
Unicode program and switching back to V1/the third-party editor. In particular,
verify that `i` enters Insert without inserting an extra `i`, Escape retains
the document, one `u` undoes the Insert session, and Cancel warns about edits.

The next implementation decision should choose between a deliberately bounded
Vim-style subset with a published behavior contract, or actual Neovim process
integration. The latter needs separate explicit approval for a native runtime,
dependencies and distribution strategy. No runtime or dependency was installed
for this exploration.
