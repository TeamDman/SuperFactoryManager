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

> r[draw.camera.tool.selection-toggles-minimap]
>
> Selecting the camera tool MUST immediately toggle the visibility of the camera/minimap overlay.

> r[draw.camera.hidden-minimap-not-interactive]
>
> A hidden minimap MUST not transform cursor coordinates or consume pointer input.

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

> r[draw.chrome.hotbar.secondary]
>
> The draw screen MUST provide a second screen-space toolbar for overflow draw actions when the primary hotbar is full.

> r[draw.chrome.hotbar.secondary.draggable]
>
> The secondary toolbar MUST be independently repositionable on the chrome layer.

> r[draw.chrome.hotbar.parts.independent]
>
> The hotbar title, the hotbar bar, and the hotbar subtitle MUST be independently positionable and resizable chrome widgets.

> r[draw.chrome.status.parts.independent]
>
> The screen title, subtitle, active-layer readout, camera-position readout, zoom readout, and cursor-position readout MUST each be composed from independently positionable and hideable chrome widgets.

> r[draw.chrome.status.labels.separate-from-values]
>
> Label text and value text in status readouts MUST be independently manipulable so a label can be hidden without hiding its associated values.

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

> r[draw.tool.creation.sticky_toggle.repeated-shortcut]
>
> Pressing the active creation tool's shortcut again SHOULD toggle sticky mode for that tool.

> r[draw.tool.creation.sticky_toggle.hotbar-shortcut]
>
> Number-key hotbar shortcuts SHOULD mirror the active tool shortcut behavior, including sticky-mode toggling when the same creation tool is chosen again.

> r[draw.tool.creation.snap]
>
> Creation tools that place anchored points or drag-sized geometry SHOULD snap their creation point and drag dimensions to the active movement increment: 8 by default, 32 while `Shift` is held, and 1 while `Ctrl` is held.

> r[draw.tool.rectangle.create]
>
> A rectangle tool MUST create rectangular canvas elements by dragging from a start point to an end point.

> r[draw.tool.arrow.create]
>
> An arrow tool MUST create arrow elements on the canvas.

> r[draw.tool.arrow.multisegment]
>
> The arrow tool MUST support building a multi-segment arrow path before finalizing the arrow.

> r[draw.tool.arrow.multisegment.preview]
>
> While a multi-segment arrow is in progress, the draw screen MUST render a dashed preview of the committed anchors and current cursor segment.

> r[draw.tool.arrow.multisegment.status]
>
> While a multi-segment arrow is in progress, the active arrow-tool status text SHOULD indicate that anchor placement is underway.

> r[draw.tool.arrow.line-selects-anchors]
>
> Clicking an arrow's line with the cursor tool MUST select that arrow's anchors so the whole arrow can be repositioned by dragging the resulting anchor selection.

> r[draw.tool.arrow.anchors.selectable]
>
> Arrow anchors MUST be individually selectable and draggable.

> r[draw.tool.arrow.anchors.render-when-selected]
>
> When any part of an arrow is selected, the draw screen MUST render all anchors of that arrow so each anchor can be grabbed and repositioned.

> r[draw.tool.arrow.anchors.delete]
>
> Selected arrow anchors MUST be deletable independently of the rest of the arrow.

> r[draw.tool.arrow.anchors.hide]
>
> Selected arrow anchors MUST support being hidden independently so hiding can act as a reversible stand-in for deletion.

> r[draw.tool.arrow.destroy-when-one-anchor-remains]
>
> If anchor deletion leaves an arrow with only one remaining anchor, the arrow MUST be destroyed.

> r[draw.tool.text.create]
>
> A text tool MUST create text elements anchored in canvas space.

> r[draw.tool.text.edit]
>
> Text elements MUST support inline text editing with caret placement, insertion, deletion, and finishing edit mode.

> r[draw.tool.text.select-existing]
>
> Clicking an existing editable text element with the text tool MUST select that text element and enter inline edit mode for it.

> r[draw.tool.text.edit.multi-selection]
>
> Switching to the text tool while editable text elements are selected MUST enter a shared edit mode for those selected text elements so typing, deletion, paste, and command acceptance are mirrored to each edited text element.

> r[draw.tool.text.edit.multi-selection.highlight]
>
> Shared text editing MUST support shift-extended text selection across the edited text elements and MUST render the corresponding text highlight for each edited text element.

> r[draw.tool.text.edit.grab-handle]
>
> While editing a text element with the text tool, a grab handle MUST appear at the text element's top-left so the text element can be repositioned without leaving the text tool.

> r[draw.tool.text.edit.grab-handle.cursor]
>
> Hovering the text-edit grab handle MUST update the mouse cursor to a four-arrow move cursor.

> r[draw.tool.text.edit.enter-commits]
>
> Pressing `Enter` while editing text MUST commit the edit, and pressing `Shift+Enter` MUST insert a newline instead.

> r[draw.tool.text.command-style.slash]
>
> Text elements whose content begins with `/` MUST render inside a black rectangle with a white border.

> r[draw.tool.text.command-style.shift-enter-submits]
>
> Pressing `Shift+Enter` while editing a slash-prefixed text element MUST execute that text as an SFM draw command instead of inserting a newline.

> r[draw.tool.text.command-style.prefix]
>
> Slash-prefixed draw text commands MUST be executed by translating the text after the leading slash into `/sfm draw <text>`.

> r[draw.tool.text.command-style.expanded-prefix]
>
> Text that already begins with `/sfm draw ` MUST also be accepted and executed as a draw command without adding a second draw-command prefix.

> r[draw.tool.text.command-style.suggestions]
>
> While editing a slash-prefixed text element, the draw screen MUST surface vanilla-style command suggestions for the translated draw command.

> r[draw.tool.text.command-style.suggestions.translated-prefix]
>
> Slash-command suggestions MUST be generated against the translated `/sfm draw ` prefix while still editing the visible slash text directly.

> r[draw.tool.text.command-style.suggestions.position]
>
> Slash-command suggestions MUST render at the edited text element rather than in a fixed screen corner.

> r[draw.tool.text.command-style.tab-priority]
>
> While slash-command suggestions are active, `Tab` MUST be consumed by command suggestion acceptance and navigation before the draw screen treats it as a layer-tool shortcut.

> r[draw.tool.text.command-output.appended]
>
> Output from executing a slash-prefixed draw text command MUST be appended as text elements below the command text element.

> r[draw.tool.text.command-output.hidden-ignored]
>
> Hidden command-output text elements MUST NOT push later appended command output farther down when determining the next output insertion position.

> r[draw.tool.text.command-style.cursor-submit]
>
> Pressing `Shift+Enter` while the cursor tool has slash-prefixed text elements selected MUST execute each selected command text element.

> r[draw.tool.text.edit.word-delete]
>
> While editing text, `Ctrl+Backspace` and `Ctrl+Delete` MUST delete contiguous characters that share the same `\w` versus `\W` character class as the character adjacent to the caret in the deletion direction.

> r[draw.tool.text.edit.word-jump]
>
> While editing text, `Ctrl+Left` and `Ctrl+Right` MUST move by contiguous `\w` versus `\W` character runs using the same word-boundary logic as word deletion, and `Shift` MUST extend the shared text selection while doing so.

> r[draw.tool.text.edit.home-end]
>
> While editing text, `Home` and `End` MUST move to the current line bounds, while `Ctrl+Home` and `Ctrl+End` MUST move to the start and end of the text, with `Shift` extending the shared text selection for those moves.

> r[draw.tool.text.edit.copy-multi]
>
> Copying a shared text selection MUST join the selected content from each edited text element with a separator whose consecutive newline count is one greater than the greatest consecutive newline count present in any copied chunk.

> r[draw.tool.text.edit.cut-multi]
>
> Cutting a shared text selection MUST copy using the same multi-selection clipboard encoding as copy, then remove the selected text from each edited text element.

> r[draw.tool.text.edit.paste-multi]
>
> Pasting into multiple edited text elements MUST split clipboard content on the greatest consecutive newline run and distribute the resulting chunks across the edited text elements.

> r[draw.tool.text.edit.select-all-max-length]
>
> Selecting all while shared text editing is active MUST span through the greatest text length among the edited text elements so longer mirrored texts remain fully selected.

> r[draw.tool.text.command-output.edited-detaches-source]
>
> When command-output text is manually edited, it MUST detach from its source-command metadata so slash-prefixed edits can become ordinary executable command text.

> r[draw.command.echo.exists]
>
> An `/sfm draw echo` command MUST exist and echo its message to the caller.

> r[draw.command.help.exists]
>
> An `/sfm draw help` command MUST exist and list the available `/sfm draw` subcommands.

> r[draw.tool.freehand.create]
>
> A freehand tool MUST create polyline-style strokes by sampling dragged cursor positions.

> r[draw.tool.layer.exists]
>
> A dedicated layer tool MUST exist.

> r[draw.tool.layer.opens-layer-window]
>
> Selecting the layer tool MUST make the layer window visible.

> r[draw.tool.layer.toggles-layer-window]
>
> Selecting the layer tool while the layer window is already visible MUST hide the layer window.

> r[draw.tool.layer.shortcut.tab]
>
> The default shortcut for selecting the layer tool SHOULD be `Tab`.

> r[draw.tool.zen.exists]
>
> A zen action MUST exist for soloing the current layer.

> r[draw.tool.zen.shortcut]
>
> Pressing `Z` MUST toggle zen solo mode for the current layer.

# Selection and transforms

> r[draw.tool.cursor.selection]
>
> The cursor tool MUST support selecting existing canvas elements.

> r[draw.tool.cursor.marquee]
>
> The cursor tool MUST support marquee selection across multiple elements.

> r[draw.tool.cursor.marquee.partial-arrow-anchors]
>
> Marquee selection MUST be able to select arrow anchors without requiring the entire arrow to be selected.

> r[draw.tool.cursor.transform_selection]
>
> Selected elements MUST support moving and resizing from the cursor tool.

> r[draw.tool.cursor.transform_selection.mixed-elements-and-arrow-anchors]
>
> The active selection MUST be able to contain both normal elements and individual arrow anchors so they can be moved together while unselected arrow anchors remain fixed.

> r[draw.tool.cursor.transform_selection.drag-snap]
>
> Drag-repositioning a canvas selection MUST snap movement to 8-unit increments by default, to 32-unit increments while `Shift` is held, and to 1-unit increments while `Ctrl` is held.

> r[draw.tool.cursor.transform_selection.keyboard-nudge]
>
> The arrow keys MUST support nudging the active selection using the same default, `Shift`, and `Ctrl` movement increments as drag repositioning.

> r[draw.tool.cursor.transform_selection.resize-snap]
>
> Selection resizing MUST snap the resized width and height to the same default, `Shift`, and `Ctrl` movement increments used by repositioning.

> r[draw.tool.cursor.transform_selection.handles-hidden-during-move]
>
> Selection resize handles MUST be hidden while a selected object is actively being drag-repositioned.

> r[draw.tool.cursor.transform_selection.handle-cursor]
>
> Hovering a resize handle for a rectangular selection MUST update the mouse cursor to match the handle orientation.

> r[draw.tool.cursor.transform_selection.move-cursor]
>
> Hovering a movable selection region in the cursor tool MUST update the mouse cursor to a four-arrow move cursor, and locked selections MUST NOT show that move cursor.

> r[draw.tool.cursor.selection.additive]
>
> Holding `Shift` while using the cursor tool MUST make click and marquee selection additive.

> r[draw.tool.cursor.selection.subtractive]
>
> Holding `Ctrl` while using the cursor tool MUST make click and marquee selection subtractive.

> r[draw.tool.cursor.selection.modifier-indicator]
>
> While additive or subtractive selection is active, the draw screen MUST render a visible `+` or `-` cursor indicator.

> r[draw.tool.cursor.selection.escape-clears]
>
> Pressing `Escape` while a canvas selection exists MUST clear that selection instead of closing the draw screen.

> r[draw.tool.cursor.enter-text-target]
>
> Pressing `Enter` while the cursor tool has canvas elements selected MUST find or create editable text elements near the selected elements' midpoints, reduce the selection to those text elements, and enter text edit mode for them.

> r[draw.tool.cursor.copy-selected-text]
>
> Pressing `Ctrl+C` while the cursor tool has text elements selected MUST copy their text using the same multi-selection clipboard encoding as shared text editing.

> r[draw.tool.cursor.cut-selection]
>
> Pressing `Ctrl+X` while the cursor tool has a canvas selection MUST copy any selected text elements using the same encoding as copy, then delete the selected elements.

> r[draw.screen.escape-closes-without-selection]
>
> Pressing `Escape` with no canvas selection MUST close the draw screen.

> r[draw.tool.lock.exists]
>
> A lock action MUST exist for toggling element locked state.

> r[draw.tool.lock.shortcut]
>
> Pressing `L` MUST invoke the lock action.

> r[draw.tool.cursor.locked.select-toggle]
>
> If the canvas selection is empty when the lock action is invoked, the cursor tool MUST toggle whether locked elements are selectable and reflect that mode in the cursor-tool status text.

> r[draw.tool.cursor.locked.selection-toggle]
>
> If a canvas selection exists when the lock action is invoked, every selected owning element MUST assume the minority locked state of the selection, with ties treated as locking the full selection.

> r[draw.tool.cursor.duplicate_selection]
>
> The draw screen MUST support duplicating the current selection.

> r[draw.tool.cursor.duplicate_selection.alt-drag]
>
> Holding `Alt` while starting a drag on a selected element SHOULD duplicate the selection and drag the duplicate.

> r[draw.tool.cursor.delete_selection]
>
> The draw screen MUST support deleting the current selection.

> r[draw.tool.cursor.select_all]
>
> The draw screen MUST support selecting all current elements.

> r[draw.tool.cursor.group]
>
> Pressing `G` with a canvas selection MUST persistently group the selected elements so later selection and transform operations can treat the connected component as a unit.

> r[draw.tool.cursor.selection.group-transitive]
>
> Selection inclusion and exclusion on canvas elements SHOULD expand through the transitive closure of persistent groups on the active layer.

> r[draw.tool.cursor.align]
>
> `Ctrl+Shift+Arrow` MUST align the active canvas selection to the corresponding outer edge of the full selection bounds.

> r[draw.tool.cursor.distribute]
>
> `Ctrl+Alt+Shift+Arrow` MUST distribute the active canvas selection along the corresponding axis while preserving the extreme members.

> r[draw.tool.cursor.arrange.group-components]
>
> Alignment and distribution MUST treat each transitive persistent-group component as a single arrangement unit.

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

> r[draw.layer.default.shell]
>
> A built-in shell layer for runtime game-state annotations MUST exist.

> r[draw.layer.default.shell.autopopulated]
>
> The shell layer MUST be automatically populated with atomized text elements derived from live game state such as the current dimension, player position, and look direction.

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

> r[draw.layer.chrome.selection-handles]
>
> In the chrome layer, selectable chrome widgets MUST expose the same eight resize handles used by rectangular canvas selections.

> r[draw.layer.chrome-selection.marquee]
>
> In the chrome layer, the cursor tool MUST support marquee selection across multiple chrome widgets.

> r[draw.layer.widgets-owned-by-layer]
>
> Editable chrome widgets MUST belong to the chrome layer rather than the normal content layer.

> r[draw.layer.muting.exists]
>
> The draw feature MUST support muting layers.

> r[draw.layer.muting.affects-rendering]
>
> Muted layers MUST not be rendered.

> r[draw.layer.muting.affects-interaction]
>
> Muted layers MUST not receive pointer interaction.

> r[draw.layer.zen.solo-current]
>
> Activating zen solo mode MUST mute every layer except the current layer.

> r[draw.layer.zen.toggle-restores]
>
> Activating zen solo mode while only the current layer is unmuted MUST unmute all layers.

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

> r[draw.layer-window.layer-entry-mute-toggle]
>
> Each layer entry in the layer window MUST provide a mute toggle control.

> r[draw.layer-window.layer-entry-mute-toggle.hover]
>
> The layer-entry mute toggle MUST have a visually distinct hover state and a cursor that indicates it can be clicked.

> r[draw.layer-window.layer-entry-mute-toggle.states]
>
> The layer-entry mute toggle MUST have visually distinct muted and unmuted states.

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

> r[draw.layer-window.interact-any-layer]
>
> The layer window close, drag, and resize interactions MUST remain available regardless of the currently active layer.

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

> r[draw.element.hidden.toggle-selection]
>
> If a selection exists, pressing `X` MUST toggle the hidden state of that selection by making every selected item match the opposite of the selection's majority hidden state.

> r[draw.element.hidden.reveal-toggle]
>
> If no selection exists, pressing `X` MUST toggle reveal-hidden-elements mode.

> r[draw.element.hidden.select-revealed]
>
> While reveal-hidden-elements mode is active, pressing `Shift+X` MUST select all hidden elements on the active canvas layer.

> r[draw.element.hidden.dim-when-revealed]
>
> Hidden elements that are revealed for editing MUST be rendered in a visually dimmed state.

> r[draw.tool.cursor.hidden-omitted-unless-revealed]
>
> Hidden elements MUST be omitted from cursor hit-testing and marquee selection unless reveal-hidden-elements mode is active.

> r[draw.tool.cursor.locked-omitted-unless-enabled]
>
> Locked elements MUST be omitted from cursor hit-testing and marquee selection unless locked-element selection mode is enabled.

> r[draw.property.locked.exists]
>
> A `locked` property MUST exist for draw elements.

> r[draw.property.locked.constraint]
>
> The `locked` property MUST accept boolean values.

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

> r[draw.future.chrome.edge-positioning.relaxed]
>
> Customizable chrome widgets SHOULD be placeable flush to screen edges rather than being forced away from them by large padding clamps.

> r[draw.future-layer-thumbnails.live]
>
> The layer window SHOULD support live-updating layer thumbnails.

> r[draw.future-widget-library]
>
> The chrome layer SHOULD support a reusable library of customizable draw widgets.