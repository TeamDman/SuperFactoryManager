package ca.teamdman.sfm.common.localization;

import ca.teamdman.sfm.SFM;

import java.util.ArrayList;
import java.util.List;

public final class SFMDrawLocalizationKeys {
    public static final LocalizationEntry IDE_KEY_OPEN_DRAW = new LocalizationEntry(
            "key.sfm.ide.open_draw",
            "IDE - Open Draw"
    );
    public static final LocalizationEntry IDE_DRAW_TITLE = new LocalizationEntry(
            "gui.sfm.ide.draw.title",
            "SFM Draw"
    );
    public static final LocalizationEntry IDE_DRAW_SUBTITLE = new LocalizationEntry(
            "gui.sfm.ide.draw.subtitle",
            "Infinite canvas prototype"
    );
    public static final LocalizationEntry IDE_DRAW_HOTBAR_TITLE = new LocalizationEntry(
            "gui.sfm.ide.draw.hotbar.title",
            "Tools"
    );
    public static final LocalizationEntry IDE_DRAW_HOTBAR_AUXILIARY_TITLE = new LocalizationEntry(
            "gui.sfm.ide.draw.hotbar.auxiliary.title",
            "Locks"
    );
    public static final LocalizationEntry IDE_DRAW_HINTS = new LocalizationEntry(
            "gui.sfm.ide.draw.hints",
            "Ctrl+Scroll: zoom | Middle drag / Hand: pan | Drag tool strip to move"
    );
    public static final LocalizationEntry IDE_DRAW_WORLD_LABEL = new LocalizationEntry(
            "gui.sfm.ide.draw.world_label",
            "canvas-space"
    );
    public static final LocalizationEntry IDE_DRAW_SCREEN_LABEL = new LocalizationEntry(
            "gui.sfm.ide.draw.screen_label",
            "screen-space"
    );
    public static final LocalizationEntry IDE_DRAW_MINIMAP_TITLE = new LocalizationEntry(
            "gui.sfm.ide.draw.minimap.title",
            "Camera"
    );
    public static final LocalizationEntry IDE_DRAW_LAYER_WINDOW_TITLE = new LocalizationEntry(
            "gui.sfm.ide.draw.layer_window.title",
            "Layers"
    );
    public static final LocalizationEntry IDE_DRAW_ACTIVE_LAYER_LABEL = new LocalizationEntry(
            "gui.sfm.ide.draw.active_layer.label",
            "Layer: %s"
    );
    public static final LocalizationEntry IDE_DRAW_LAYER_LABEL_TEXT = new LocalizationEntry(
            "gui.sfm.ide.draw.layer.label_text",
            "Layer"
    );
    public static final LocalizationEntry IDE_DRAW_CAMERA_POSITION_LABEL = new LocalizationEntry(
            "gui.sfm.ide.draw.camera_position.label",
            "Cam"
    );
    public static final LocalizationEntry IDE_DRAW_ZOOM_LABEL = new LocalizationEntry(
            "gui.sfm.ide.draw.zoom.label",
            "Zoom"
    );
    public static final LocalizationEntry IDE_DRAW_CURSOR_LABEL = new LocalizationEntry(
            "gui.sfm.ide.draw.cursor.label",
            "Cursor"
    );
    public static final LocalizationEntry IDE_DRAW_LAYER_ELEMENTS = new LocalizationEntry(
            "gui.sfm.ide.draw.layer.elements",
            "Elements"
    );
    public static final LocalizationEntry IDE_DRAW_LAYER_CHROME = new LocalizationEntry(
            "gui.sfm.ide.draw.layer.chrome",
            "Chrome"
    );
    public static final LocalizationEntry IDE_DRAW_LAYER_MUTE = new LocalizationEntry(
            "gui.sfm.ide.draw.layer.mute",
            "Mute %s"
    );
    public static final LocalizationEntry IDE_DRAW_LAYER_UNMUTE = new LocalizationEntry(
            "gui.sfm.ide.draw.layer.unmute",
            "Unmute %s"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_CURSOR = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.cursor",
            "Cursor"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_RECTANGLE = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.rectangle",
            "Rectangle"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_ARROW = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.arrow",
            "Arrow"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_TEXT = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.text",
            "Text"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_FREEHAND = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.freehand",
            "Freehand"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_HAND = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.hand",
            "Hand"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_CAMERA = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.camera",
            "Camera"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_LAYER = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.layer",
            "Layers"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_ZEN = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.zen",
            "Zen"
    );
    public static final LocalizationEntry IDE_DRAW_TOOL_LOCK = new LocalizationEntry(
            "gui.sfm.ide.draw.tool.lock",
            "Lock"
    );

    public static List<LocalizationEntry> getEntries() {
        var rtn = new ArrayList<LocalizationEntry>();
        for (var field : SFMDrawLocalizationKeys.class.getFields()) {
            if (field.getType() == LocalizationEntry.class) {
                try {
                    rtn.add((LocalizationEntry) field.get(null));
                } catch (IllegalAccessException e) {
                    SFM.LOGGER.error("Failed reading entry field", e);
                }
            }
        }
        return rtn;
    }
}
