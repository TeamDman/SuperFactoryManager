# Initiation

> r[draw.hotkey.open_screen]
>
> A hotkey MUST exist to open and close the draw screen while the player is in-game.

> r[draw.hotkey.default_binding]
>
> The default draw-screen hotkey SHOULD be `Alt+D`.

> r[draw.screen.main]
>
> A dedicated screen for the draw feature MUST exist.

# Canvas space

> r[draw.canvas.grid]
>
> The draw screen MUST render a background grid that adapts to zoom level so the canvas remains legible while zooming.

> r[draw.canvas.origin]
>
> The draw screen MUST render visible origin axes so the canvas-space origin can be located.

> r[draw.camera.zoom.cursor]
>
> `Ctrl+Scroll` MUST zoom the canvas around the cursor position rather than around the screen center.

> r[draw.camera.pan.middle_drag]
>
> Middle mouse dragging MUST pan the canvas regardless of the active tool.

> r[draw.camera.pan.hand_tool]
>
> The hand tool MUST pan the canvas with left mouse dragging.

> r[draw.camera.frame_tool]
>
> The camera tool MUST support dragging a frame to reposition and zoom the viewport to the framed canvas region.

# Screen-space chrome

> r[draw.chrome.hotbar]
>
> The draw screen MUST provide a screen-space hotbar for draw tools.

> r[draw.chrome.hotbar.draggable]
>
> The tool hotbar MUST be draggable without changing canvas-space content.

> r[draw.chrome.hotbar.shortcuts]
>
> The tool hotbar MUST expose keyboard shortcuts for the available tools and number-key selection for the hotbar slots.

> r[draw.chrome.minimap]
>
> A minimap overlay MUST show scene extents, drawn content, and the current viewport rectangle.

> r[draw.chrome.minimap.draggable]
>
> The minimap overlay MUST be draggable independently of the canvas camera.

# Tools and creation

> r[draw.tool.creation.sticky_toggle]
>
> The draw screen MUST support toggling creation tools between sticky and one-shot modes.

> r[draw.tool.rectangle.create]
>
> A rectangle tool MUST create rectangular canvas elements by dragging from a start point to an end point.

> r[draw.tool.arrow.create]
>
> An arrow tool MUST create arrow elements on the canvas.

> r[draw.tool.arrow.multisegment]
>
> The arrow tool MUST support building a multi-segment arrow path before finalizing the arrow.

> r[draw.tool.text.create]
>
> A text tool MUST create text elements anchored in canvas space.

> r[draw.tool.text.edit]
>
> Text elements MUST support inline text editing with caret placement, insertion, deletion, and finishing edit mode.

> r[draw.tool.freehand.create]
>
> A freehand tool MUST create polyline-style strokes by sampling dragged cursor positions.

# Selection and transforms

> r[draw.tool.cursor.selection]
>
> The cursor tool MUST support selecting existing canvas elements.

> r[draw.tool.cursor.marquee]
>
> The cursor tool MUST support marquee selection across multiple elements.

> r[draw.tool.cursor.transform_selection]
>
> Selected elements MUST support moving and resizing from the cursor tool.

> r[draw.tool.cursor.duplicate_selection]
>
> The draw screen MUST support duplicating the current selection.

> r[draw.tool.cursor.delete_selection]
>
> The draw screen MUST support deleting the current selection.

> r[draw.tool.cursor.select_all]
>
> The draw screen MUST support selecting all current elements.

# Planned follow-ups

> r[draw.future.chrome.docking]
>
> Screen-space widgets SHOULD support structured repositioning or docking rather than only free dragging.

> r[draw.future.style.controls]
>
> Draw tools SHOULD expose richer style controls for text, arrows, and shapes.

> r[draw.future.documents.camera_presets]
>
> The draw feature SHOULD support document tabs or layer presets that each preserve their own camera state.

> r[draw.future.serialization]
>
> The draw feature SHOULD support serializing the document model once the interaction model is stable.