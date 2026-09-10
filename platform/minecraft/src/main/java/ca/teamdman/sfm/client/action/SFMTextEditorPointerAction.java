package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

public final class SFMTextEditorPointerAction implements SFMClientAction<SFMDrawCanvasScreen> {
    public enum Kind { WHEEL, BUTTONS, SAVE_DEFAULTS }
    @SFMLocalizationDatagen public static final LocalizationEntry WHEEL = new LocalizationEntry(
            "gui.sfm.editor.pointer.wheel", "Toggle mouse wheel: zoom or scroll");
    @SFMLocalizationDatagen public static final LocalizationEntry BUTTONS = new LocalizationEntry(
            "gui.sfm.editor.pointer.buttons", "Swap middle/right buttons: pan or actions");
    @SFMLocalizationDatagen public static final LocalizationEntry SAVE = new LocalizationEntry(
            "gui.sfm.editor.pointer.save", "Use these mouse settings for new editors");
    @SFMLocalizationDatagen public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.editor.pointer.description", "Changes only this editor. Save defaults explicitly to affect newly opened editors. Shift+wheel scrolls horizontally in scroll mode.");
    private final Kind kind;
    @SFMLocalizationDatagen public static final LocalizationEntry ZOOM_STATE = new LocalizationEntry(
            "gui.sfm.editor.pointer.zoom_state", "Mouse wheel zooms. Click to switch to scrolling. Right-click for settings and saving defaults.");
    @SFMLocalizationDatagen public static final LocalizationEntry SCROLL_STATE = new LocalizationEntry(
            "gui.sfm.editor.pointer.scroll_state", "Mouse wheel scrolls; Shift+wheel scrolls horizontally. Click to switch to zooming. Right-click for settings and saving defaults.");
    @SFMLocalizationDatagen public static final LocalizationEntry MIDDLE_STATE = new LocalizationEntry(
            "gui.sfm.editor.pointer.middle_state", "Middle drag pans; right-click opens actions. Click to swap. Alt+middle remains panel movement. Right-click for settings and saving defaults.");
    @SFMLocalizationDatagen public static final LocalizationEntry RIGHT_STATE = new LocalizationEntry(
            "gui.sfm.editor.pointer.right_state", "Right drag pans; middle-click opens actions. Click to swap. Alt+middle remains panel movement. Right-click for settings and saving defaults.");

    public static java.util.List<ca.teamdman.sfm.client.screen.SFMActionChoice> choices() {
        return java.util.Arrays.stream(Kind.values()).map(kind ->
                ca.teamdman.sfm.client.screen.SFMActionChoice.invoke(
                        ca.teamdman.sfm.common.util.SFMResourceLocation.fromSFMPath(
                                "document/pointer/" + kind.name().toLowerCase(java.util.Locale.ROOT)),
                        "", new SFMTextEditorPointerAction(kind).title().getString())).toList();
    }
    public SFMTextEditorPointerAction(Kind kind) { this.kind = kind; }
    @Override public Component title() { return (switch (kind) {
        case WHEEL -> WHEEL; case BUTTONS -> BUTTONS; case SAVE_DEFAULTS -> SAVE;
    }).getComponent(); }
    @Override public Component description() { return DESCRIPTION.getComponent(); }
    @Override public SFMClientActionRequirement<SFMDrawCanvasScreen> requirement() {
        return context -> {
            if (context.originatingHostIsCurrent().getAsBoolean()
                    && context.originatingHost() instanceof SFMScreenMultiplexer workspace
                    && context.originatingPanelId() != null) {
                // Addressed source/diff previews retain their deferred host after loading.
                // Resolve its loaded delegate without accepting a loading or closed panel.
                var canvas = SFMSpatialCoverageRuntime.resolveTextEditorPanel(
                        workspace.panelInstance(context.originatingPanelId()))
                        .flatMap(SFMTextEditorPanel::pointerCanvas);
                if (canvas.isPresent()) return SFMClientActionAvailability.available(canvas.orElseThrow());
            }
            return SFMClientActionAvailability.unavailable(
                    ca.teamdman.sfm.client.search.SFMTextEditorSearchText.REQUIRED.getComponent());
        };
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) { node.executes(this::invoke); }
    @Override public int execute(SFMDrawCanvasScreen canvas, CommandContext<SFMClientActionSource> context) {
        switch (kind) {
            case WHEEL -> canvas.setPointerSettings(canvas.pointerSettings().toggleWheel());
            case BUTTONS -> canvas.setPointerSettings(canvas.pointerSettings().toggleButtons());
            case SAVE_DEFAULTS -> canvas.savePointerDefaults();
        }
        return 1;
    }
}
