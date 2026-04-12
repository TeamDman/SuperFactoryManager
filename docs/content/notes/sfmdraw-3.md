# SFM Draw V3 Notes

This note captures the design information that emerged after the ideas in `sfmdraw-v1.md` and `sfmdraw-2.md`.

It is intentionally focused on the newer deltas rather than restating the earlier documents.

## Clarifications from implementation iteration

- When saying “remove the default elements”, the intent is only to remove the startup sample canvas content.
- This does **not** mean removing the rectangle, arrow, text, freehand, hand, camera, layer, or zen tools.
- Tool availability and tool hotkeys remain part of the normal draw experience.

## Layer model update

The layer model has now expanded beyond the original `elements`/`chrome` split.

Current conceptual ordering:

1. `elements`
2. `chrome`
3. `shell`

Important implications:

- `elements` remains the normal user-authored canvas layer.
- `chrome` remains the layer for editing draw-screen UI/chrome.
- `shell` is a canvas layer, but unlike `elements`, it is intended to be automatically populated from runtime/game state.
- `chrome` is intentionally between `elements` and `shell` in the layer order.

## Shell layer

The shell layer is not just another empty drawing layer.

It is a runtime-annotated canvas-space layer containing atomized text elements derived from the current game/session state.

Examples of shell atoms:

- current dimension key
- player X
- player Y
- player Z
- look yaw
- look pitch

Shell design intent:

- shell items should exist as real draw elements, not as a separate ad-hoc overlay system
- shell items should be positionable, selectable, hideable, and layer-managed like other draw elements
- shell items should keep their text content bound to live game state rather than behaving like ordinary free-authored text by default
- shell items are default content for the shell layer, not sample user-document content

This is the key conceptual difference from the old startup rectangle/text/arrow samples.

## Selection and grouping model

Persistent grouping is now part of the interaction model.

Design intent:

- `G` creates a persistent group relationship over the current canvas selection
- grouping is transitive
- selection inclusion and exclusion should respect transitive grouped components
- selection logic should operate on connected components, not just individually clicked leaf elements

Practical consequence:

- if `A` is grouped with `B`, and `B` is grouped with `C`, selecting or deselecting one should behave as if the whole connected component is implicated when group-aware selection is in effect

Grouping is intended as a structural document concept, not just a one-off transform convenience.

## Alignment and distribution

Arrangement actions are now first-class editing operations.

Shortcuts:

- `Ctrl+Shift+Arrow` = align
- `Ctrl+Alt+Shift+Arrow` = distribute

Design rules:

- arrangement acts on the active canvas selection
- grouped connected components are treated as a single arrangement unit
- alignment uses the full selection bounds on the chosen side
- distribution preserves the extreme members and spaces the interior members/components between them
- arrangement semantics should be based on component extents, not just element origins

This matters because once grouping exists, “align these three things” often really means “align these three grouped structures”.

## Arrow model update

The arrow model has effectively moved away from “single opaque arrow object” behavior and toward editable anchored polylines.

Important design points:

- clicking an arrow line can select its anchors
- anchors can be selected individually
- marquee selection can capture partial arrow-anchor subsets
- mixed selections of regular elements and arrow anchors are allowed
- selected anchors can be moved independently of unselected anchors on the same arrow
- anchors can be hidden or deleted individually
- if only one anchor remains after deletion, the arrow should be destroyed

This is a major conceptual shift relative to earlier draw notes and should be preserved in future redesigns.

## Hiddenness and reveal workflow

Hiddenness is now part of normal editing flow rather than just being a binary visibility toggle.

Key ideas:

- `X` on a selection toggles hiddenness by majority-state inversion
- `X` with no selection toggles reveal-hidden mode
- while hidden items are revealed, `Shift+X` can select hidden items on the active canvas layer
- hidden anchors and hidden elements are intended to support reversible editing, not only destructive delete flows

This is especially important for arrow anchors, where hiddenness can act as a softer alternative to deletion.

## Startup state expectations

Opening SFM Draw should now feel like:

- the user has tools immediately available
- the user is not forced to begin from sample rectangle/arrow/text content
- the shell layer may already contain live informational text atoms
- the `elements` layer should begin empty unless the document itself contains user-authored content

That distinction is intentional:

- no sample document clutter
- but still immediate useful context via shell atoms

## Open design direction

Topics that still deserve explicit future thought:

- whether shell atoms should be directly text-editable, templated, or fully runtime-owned
- whether grouping should later support ungroup, nested grouping, or named groups
- whether alignment/distribution should eventually gain center/equal-gap variants beyond edge-driven arrow shortcuts
- whether shell data should expand to include hit result, held item, manager focus, or selection context similar to the playground shell concepts

