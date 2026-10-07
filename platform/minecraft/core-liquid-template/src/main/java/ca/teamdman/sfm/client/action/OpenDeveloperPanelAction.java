package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/**
 * Transitional developer entry point that delegates to the canonical panel
 * action instead of replacing Minecraft's current screen.
 */
public final class OpenDeveloperPanelAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.text_editor.title",
            "Text Editor"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.text_editor.description",
            "Open an empty program in the preferred SFM text editor"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INPUT_DIAGNOSTICS_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.input_diagnostics.title",
            "Input Diagnostics"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry INPUT_DIAGNOSTICS_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.input_diagnostics.description",
            "Open the SFM input diagnostics panel"
    );

    private final Scene scene;

    public OpenDeveloperPanelAction(Scene scene) {
        this.scene = Objects.requireNonNull(scene);
    }

    @Override
    public Component title() {
        return switch (scene) {
            case TEXT_EDITOR -> TEXT_EDITOR_TITLE.getComponent();
            case INPUT_DIAGNOSTICS -> INPUT_DIAGNOSTICS_TITLE.getComponent();
        };
    }

    @Override
    public Component description() {
        return switch (scene) {
            case TEXT_EDITOR -> TEXT_EDITOR_DESCRIPTION.getComponent();
            case INPUT_DIAGNOSTICS -> INPUT_DIAGNOSTICS_DESCRIPTION.getComponent();
        };
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        return SFMClientActionExecutor.execute(
                "sfm action invoke sfm:panel/open " + scene.sceneId,
                target,
                context.getSource().feedback()
        );
    }

    public enum Scene {
        TEXT_EDITOR("sfm:text_editor"),
        INPUT_DIAGNOSTICS("sfm:input_diagnostics");

        private final String sceneId;

        Scene(String sceneId) {
            this.sceneId = sceneId;
        }
    }
}
