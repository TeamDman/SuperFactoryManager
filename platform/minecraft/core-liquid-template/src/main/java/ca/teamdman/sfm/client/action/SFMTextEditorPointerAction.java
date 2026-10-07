package ca.teamdman.sfm.client.action;

{% if features.canvas_text_editor %}
import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
{% else %}
import net.minecraft.client.gui.screens.Screen;
{% endif %}
{% if features.canvas_text_editor and features.canvas_pointer_defaults and features.editor_document_panels and features.workspace_panels %}
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
{% endif %}
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

{% if features.canvas_text_editor %}
public final class SFMTextEditorPointerAction implements SFMClientAction<SFMDrawCanvasScreen> {
{% else %}
public final class SFMTextEditorPointerAction implements SFMClientAction<Screen> {
{% endif %}
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
{% if features.canvas_text_editor %}
    @Override public SFMClientActionRequirement<SFMDrawCanvasScreen> requirement() {
{% else %}
    @Override public SFMClientActionRequirement<Screen> requirement() {
{% endif %}
        return context -> {
{% if features.canvas_text_editor and features.canvas_pointer_defaults and features.editor_document_panels and features.workspace_panels %}
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
{% endif %}
{% if features.canvas_text_editor and features.canvas_pointer_defaults and features.editor_document_panels and features.workspace_panels %}
{% if features.editor_search %}
            return SFMClientActionAvailability.unavailable(
                    ca.teamdman.sfm.client.search.SFMTextEditorSearchText.REQUIRED.getComponent());
{% else %}
            return SFMClientActionAvailability.unavailable(title());
{% endif %}
{% else %}
            return SFMClientActionAvailability.unavailable(Component.literal(
                    "Canvas pointer settings are unavailable for this feature selection"));
{% endif %}
        };
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) { node.executes(this::invoke); }
{% if features.canvas_text_editor %}
    @Override public int execute(SFMDrawCanvasScreen canvas, CommandContext<SFMClientActionSource> context) {
{% else %}
    @Override public int execute(Screen canvas, CommandContext<SFMClientActionSource> context) {
{% endif %}
{% if features.canvas_text_editor and features.canvas_pointer_defaults %}
        switch (kind) {
            case WHEEL -> canvas.setPointerSettings(canvas.pointerSettings().toggleWheel());
            case BUTTONS -> canvas.setPointerSettings(canvas.pointerSettings().toggleButtons());
            case SAVE_DEFAULTS -> canvas.savePointerDefaults();
        }
        return 1;
{% else %}
        throw new UnsupportedOperationException(
                "Canvas pointer settings require canvas_text_editor and canvas_pointer_defaults");
{% endif %}
    }
}
