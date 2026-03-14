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

> r[draw.screen.layered-editing-goal]
>
> The draw feature MUST support both sketching document content and customizing draw-screen chrome from within the draw screen itself.

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

> r[draw.canvas.shared-tools]
>
> The same core manipulation tools MUST be usable for both document content and editable chrome content.

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

> r[draw.chrome.editable]
>
> Draw-screen chrome MUST be representable as editable content inside the draw system rather than as hard-coded non-editable widgets only.

> r[draw.chrome.widgets.represented-as-elements]
>
> Customizable chrome widgets MUST be representable using the same draw-element system used for document content.

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

> r[draw.tool.layer.exists]
>
> A dedicated layer tool MUST exist.

> r[draw.tool.layer.opens-layer-window]
>
> Selecting the layer tool MUST make the layer window visible.

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

# Layers

> r[draw.layer.system.exists]
>
> The draw feature MUST support multiple named layers.

> r[draw.layer.active.exists]
>
> The draw feature MUST track a single active layer that receives creation and editing input.

> r[draw.layer.default.elements]
>
> A built-in content layer for normal drawing MUST exist.

> r[draw.layer.default.chrome]
>
> A built-in chrome layer for user-interface customization MUST exist.

> r[draw.layer.default-active]
>
> Opening the draw screen MUST activate the normal content layer by default.

> r[draw.layer.switch.hotkeys]
>
> The active layer MUST be switchable with `Alt` plus a layer index hotkey.

> r[draw.layer.switch.chrome]
>
> The chrome layer MUST be directly reachable through layer-switching hotkeys.

> r[draw.layer.edit-routing]
>
> Element creation, selection, and transformation tools MUST operate on the active layer.

> r[draw.layer.visibility.feedback]
>
> The draw screen MUST provide visible feedback identifying the active layer.

> r[draw.layer.chrome-customization-mode]
>
> The chrome layer MUST allow the user to reposition and resize customizable interface elements.

> r[draw.layer.widgets-owned-by-layer]
>
> Editable chrome widgets MUST belong to the chrome layer rather than the normal content layer.

# Layer window

> r[draw.layer-window.exists]
>
> A layer window MUST exist.

> r[draw.layer-window.title]
>
> The layer window MUST display a title identifying it as the layer window.

> r[draw.layer-window.layer-list]
>
> The layer window MUST display a list of available layers.

> r[draw.layer-window.active-highlight]
>
> The list entry for the active layer MUST have a visually distinct appearance from inactive layer entries.

> r[draw.layer-window.layer-entry-name]
>
> Each layer entry in the layer window MUST display the layer name.

> r[draw.layer-window.layer-entry-thumbnail]
>
> Each layer entry in the layer window MUST display a thumbnail or preview region.

> r[draw.layer-window.border]
>
> The layer window MUST render a border.

> r[draw.layer-window.draggable]
>
> The layer window MUST be repositionable by dragging its title area.

> r[draw.layer-window.resize-handle]
>
> The layer window MUST provide a resize handle.

> r[draw.layer-window.resizable]
>
> The layer window MUST be resizable by dragging its resize handle.

> r[draw.layer-window.close-priority]
>
> If a close button is present on the layer window, activating that close button MUST take priority over drag-to-reposition behavior.

> r[draw.layer-window.chrome-element]
>
> The layer window MUST be representable as chrome-layer content.

# Registries and tool metadata

> r[draw.registry.tools.exists]
>
> A registry for draw tools MUST exist.

> r[draw.registry.tools.id]
>
> The draw tool registry MUST use the registry id `draw_tools`.

> r[draw.registry.naming-prefix]
>
> Registries introduced for the draw system MUST use registry key paths prefixed with `draw_`.

> r[draw.tool.name]
>
> Each draw tool MUST have a name.

> r[draw.tool.name.i18n]
>
> Draw tool names MUST support localization.

> r[draw.tool.icon]
>
> Each draw tool MUST have an icon.

> r[draw.tool.icon.small-readable]
>
> Draw tool icons MUST remain visually recognizable when rendered at small sizes.

> r[draw.tool.icon-sets.exists]
>
> The draw system MUST support icon sets as a concept for tool presentation.

> r[draw.tool.icon-sets.cohesive]
>
> An icon set MUST consist of visually cohesive icons.

# Element and property model

> r[draw.element.model.property-dictionary]
>
> Draw element state MUST be expressible as key-value properties.

> r[draw.element.model.id]
>
> Each draw element MUST have an element id.

> r[draw.element.model.id.unique]
>
> Element ids MUST be unique within a draw document.

> r[draw.element.model.id.max-bytes]
>
> Element ids MUST support a maximum encoded length of 256 bytes.

> r[draw.element.model.id.stable]
>
> An element id MUST remain stable for the lifetime of that element.

> r[draw.registry.properties.exists]
>
> A registry for draw property definitions MUST exist.

> r[draw.registry.properties.id]
>
> The draw property definition registry MUST use the registry id `draw_properties`.

> r[draw.property-definition.id]
>
> Each property definition MUST have an id.

> r[draw.property-definition.constraint]
>
> Each property definition MUST declare a constraint for valid property values.

> r[draw.property.x.exists]
>
> An `x` property MUST exist for draw elements.

> r[draw.property.x.float]
>
> The `x` property MUST accept floating-point values.

> r[draw.property.y.exists]
>
> A `y` property MUST exist for draw elements.

> r[draw.property.y.float]
>
> The `y` property MUST accept floating-point values.

> r[draw.property.layer.exists]
>
> A `layer` property MUST exist for draw elements.

> r[draw.property.layer.constraint]
>
> The `layer` property MUST accept UTF-8 strings up to 256 bytes.

> r[draw.property.selection.exists]
>
> A `selection` property MUST exist in the draw property model.

> r[draw.property.z.exists]
>
> A `z` property MUST exist for draw elements.

> r[draw.property.z.float]
>
> The `z` property MUST accept floating-point values.

> r[draw.property.children.exists]
>
> A `children` property MUST exist for draw elements.

> r[draw.property.name.exists]
>
> A `name` property MUST exist for draw elements.

> r[draw.property.name.constraint]
>
> The `name` property MUST accept UTF-8 strings up to 256 bytes.

> r[draw.property.text.exists]
>
> A `text` property MUST exist for draw elements.

> r[draw.property.text.constraint]
>
> The `text` property MUST accept UTF-8 strings up to 8192 bytes.

> r[draw.element.depth-ordering]
>
> Draw elements MUST support deterministic depth ordering.

> r[draw.element.layer-ownership]
>
> Every persisted draw element MUST belong to exactly one layer.

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

> r[draw.future.chrome-presets]
>
> The draw feature SHOULD support saving and restoring chrome layouts.

> r[draw.future-layer-thumbnails.live]
>
> The layer window SHOULD support live-updating layer thumbnails.

> r[draw.future-widget-library]
>
> The chrome layer SHOULD support a reusable library of customizable draw widgets.