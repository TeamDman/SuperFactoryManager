package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

public final class OpenTitleScreenDevScreenAction implements SFMClientAction<TitleScreen> {
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
            "Open the SFM input diagnostics screen"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DRAW_CANVAS_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.draw_canvas.title",
            "Draw Canvas"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DRAW_CANVAS_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.draw_canvas.description",
            "Open an empty SFM Draw canvas"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FILE_EXPLORER_TITLE = new LocalizationEntry(
            "gui.sfm.client_action.developer.file_explorer.title",
            "File Explorer"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry FILE_EXPLORER_DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.developer.file_explorer.description",
            "Open the read-only SFM file explorer experiment"
    );

    private final SFMTitleScreenDevScreen devScreen;

    public OpenTitleScreenDevScreenAction(SFMTitleScreenDevScreen devScreen) {
        this.devScreen = Objects.requireNonNull(devScreen);
    }

    @Override
    public Component title() {
        return switch (devScreen) {
            case TEXT_EDITOR -> TEXT_EDITOR_TITLE.getComponent();
            case INPUT_DIAG -> INPUT_DIAGNOSTICS_TITLE.getComponent();
            case DRAW_CANVAS -> DRAW_CANVAS_TITLE.getComponent();
            case FILE_EXPLORER -> FILE_EXPLORER_TITLE.getComponent();
        };
    }

    @Override
    public Component description() {
        return switch (devScreen) {
            case TEXT_EDITOR -> TEXT_EDITOR_DESCRIPTION.getComponent();
            case INPUT_DIAG -> INPUT_DIAGNOSTICS_DESCRIPTION.getComponent();
            case DRAW_CANVAS -> DRAW_CANVAS_DESCRIPTION.getComponent();
            case FILE_EXPLORER -> FILE_EXPLORER_DESCRIPTION.getComponent();
        };
    }

    @Override
    public SFMClientActionRequirement<TitleScreen> requirement() {
        return SFMDeveloperActionRequirement::resolve;
    }

    @Override
    public int execute(
            TitleScreen target,
            CommandContext<SFMClientActionSource> context
    ) {
        SFMScreenChangeHelpers.setScreen(devScreen.create(target));
        return 1;
    }
}
