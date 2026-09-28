package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOverlayOpenContext;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

public final class CommandPaletteHelpAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.help.title",
            "Command palette help"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.help.description",
            "Open a short command palette help document"
    );

    // TODO: Move this document body into localized entries when the help screen
    // supports structured, translated content.
    private static final String HELP_TEXT = """
            SFM Command Palette

            Open this palette with Ctrl+K from any SFM client screen.

            Type a local command, use the completion list, and press Enter to run it.
            Commands run locally on this client; they are not sent to a server.

            Available command groups include:
              sfm action list [available|all]
              sfm action help <action-id>
              sfm action invoke <action-id>

            Press Escape to return to the screen that opened the palette.
            """.stripTrailing();

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
        SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenOverlayOpenContext(
                HELP_TEXT,
                LabelPositionHolder.empty(),
                ignored -> {
                }
        ));
        return 1;
    }
}
