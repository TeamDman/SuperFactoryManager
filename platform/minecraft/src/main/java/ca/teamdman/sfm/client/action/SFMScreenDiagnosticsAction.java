package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMScreenDiagnosticsContributor;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Opens a bounded text snapshot of the current screen and overlay composition. */
public final class SFMScreenDiagnosticsAction implements SFMClientAction<SFMClientActionContext> {
    private static final ResourceLocation TEXT_EDITOR_SCENE = new ResourceLocation(SFM.MOD_ID, "text_editor");

    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.screen.diagnostics.title",
            "Open screen diagnostics"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.screen.diagnostics.description",
            "Open a read-only text snapshot of the current screen, focus, viewport, and overlays"
    );

    @Override
    public Component title() {
        return TITLE.getComponent();
    }

    @Override
    public Component description() {
        return DESCRIPTION.getComponent();
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) {
        String diagnostics = capture(Minecraft.getInstance());
        ResourceLocation editorId = preferredEditorId();
        SFMTextEditorPanelRecipe recipe = new SFMTextEditorPanelRecipe(
                TEXT_EDITOR_SCENE,
                editorId,
                new SFMTextDocumentSource.Literal(diagnostics, SFMTextDocumentLanguage.plainText()),
                true,
                "Screen Diagnostics"
        );
        return OpenPanelAction.openPanel(
                target,
                recipe.reopen(),
                OpenPanelAction.Direction.FOCUSED,
                recipe
        );
    }

    static String capture(Minecraft minecraft) {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("schema: sfm.screen-diagnostics/1");
        Screen screen = minecraft.screen;
        lines.add("screen.class: " + className(screen));
        lines.add("screen.title: " + (screen == null ? "unavailable" : screen.getTitle().getString()));
        lines.add("screen.logical-size: " + (screen == null ? "unavailable" : screen.width + "x" + screen.height));
        if (screen != null) {
            GuiEventListener focused = screen.getFocused();
            lines.add("screen.focused.class: " + className(focused));
            lines.add("screen.children: " + screen.children().size());
            if (screen instanceof SFMScreenDiagnosticsContributor contributor) {
                contributor.screenDiagnostics().forEach(value -> lines.add("screen.detail: " + value));
            }
        }

        var window = minecraft.getWindow();
        lines.add("window.framebuffer: " + window.getWidth() + "x" + window.getHeight());
        lines.add("window.screen-pixels: " + window.getScreenWidth() + "x" + window.getScreenHeight());
        lines.add("window.gui-logical: " + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight());
        lines.add("window.gui-scale: " + window.getGuiScale());
        lines.add("minecraft.overlay.class: " + className(minecraft.getOverlay()));
        lines.add("minecraft.level.present: " + (minecraft.level != null));
        lines.add("minecraft.player.present: " + (minecraft.player != null));

        SFMClientOverlayRuntime.RuntimeSnapshot overlays = SFMClientOverlayRuntime.get().snapshot();
        lines.add("sfm-overlays.schema: " + overlays.scene().schema());
        lines.add("sfm-overlays.revision: " + overlays.scene().revision());
        lines.add("sfm-overlays.focused: " + overlays.scene().focusedOverlay()
                .map(id -> id.value()).orElse("none"));
        lines.add("sfm-overlays.hosted: " + String.join(",", overlays.hostedOverlayIds()));
        lines.add("sfm-overlays.count: " + overlays.scene().overlays().size());
        overlays.scene().overlays().forEach(overlay -> {
            String prefix = "sfm-overlay[" + overlay.id().value() + "].";
            lines.add(prefix + "visible: " + overlay.visible());
            lines.add(prefix + "content: " + overlay.recipe().contentId());
            lines.add(prefix + "argument: " + overlay.recipe().argument());
            lines.add(prefix + "input-mode: " + overlay.inputMode());
            lines.add(prefix + "z-order: " + overlay.zOrder());
            lines.add(prefix + "resolved-bounds: " + overlays.resolvedBounds().get(overlay.id().value()));
            lines.add(prefix + "narration: " + overlays.contentNarration().getOrDefault(
                    overlay.id().value(), "unavailable"));
        });
        lines.add("sfm-overlays.last-lifecycle-reason: " + overlays.lastLifecycleReason());
        lines.add("sfm-overlays.input: " + overlays.input());
        return String.join("\n", lines) + "\n";
    }

    private static ResourceLocation preferredEditorId() {
        ISFMTextEditorRegistration preferred = SFMClientTextEditorConfig.getPreferredTextEditor();
        ResourceLocation configured = SFMTextEditors.registry().getId(preferred);
        return configured == null ? SFMTextEditors.V3.getId().orElseThrow().location() : configured;
    }

    private static String className(@Nullable Object value) {
        return value == null ? "none" : value.getClass().getName();
    }
}
