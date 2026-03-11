# SFM Draw V1

## Goal

Build a separate experimental screen that treats layout spatially instead of as dock-only panel carving.

`SfmDrawScreen` is the first sandbox for that idea.

## Core model

- Two worlds exist at once:
	- canvas-space: infinite scene, primitives, document origins, embeddings
	- screen-space: tool chrome, overlays, minimaps, presets, handles
- A layer is a canvas plus a camera, not a destructive layout rewrite.
- Camera motion should preserve spatial memory.
- Screen chrome should remain readable while the canvas zooms and pans underneath it.

## V1 interaction slice

- `Alt+D` toggles the draw prototype.
- Fixed-but-draggable screen-space tool hotbar.
- Vanilla hotbar texture reused for the tool strip.
- Initial tools:
	- cursor (`V`)
	- rectangle (`R`)
	- arrow (`A`)
	- text (`T`)
	- freehand (`F`)
	- hand (`H`)
- `Ctrl+Scroll` zooms around the cursor.
- Middle drag pans regardless of tool.
- Hand tool pans with left drag.
- Background grid and origin axes visualize the infinite canvas.
- Minimap shows scene extents and current camera viewport rectangle.

## Representation direction

- The long-term document model should support origins and embedded scenes.
- Elements belong to canvas-space.
- Toolbars, palettes, inspectors, and presets belong to screen-space.
- Screen presets should eventually swap screen-space chrome without mutating canvas data.

## Near-term follow-ups

- Make screen-space widgets themselves dockable/repositionable in a structured way.
- Add selection bounds, move/resize handles, and canvas transforms for existing primitives.
- Add richer text editing and proper arrow/shape style controls.
- Add document tabs / layer presets that each own camera state.
- Define serialization once the interaction model feels stable.
