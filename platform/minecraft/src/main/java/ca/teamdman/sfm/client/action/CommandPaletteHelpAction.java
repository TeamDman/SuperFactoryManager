package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.text_editor.SFMTextEditScreenOverlayOpenContext;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import ca.teamdman.sfm.client.registry.SFMClientActions;
import com.google.gson.JsonObject;

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
    public void configureCommandNode(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<SFMClientActionSource> node
    ) {
        node.executes(this::invoke);
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "action_id", StringArgumentType.word()).executes(this::invoke));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) {
        String text = HELP_TEXT;
        String actionId = null;
        try { actionId = StringArgumentType.getString(context, "action_id"); } catch (IllegalArgumentException ignored) { }
        if (actionId != null) {
            ResourceLocation id = ResourceLocation.tryParse(actionId);
            SFMClientAction<?> action = id == null ? null : SFMClientActions.registry().get(id);
            if (action == null) throw new IllegalArgumentException("Unknown SFM client action: " + actionId);
            text = "SFM Client Action\n\n"
                    + "id: " + id + "\n"
                    + "title: " + action.title().getString() + "\n"
                    + "description: " + action.description().getString() + "\n"
                    + "programmatic: " + action.programmaticDescriptor().isPresent() + "\n";
            JsonObject result = new JsonObject();
            result.addProperty("schema", "sfm.action-help/1");
            result.addProperty("id", id.toString());
            result.addProperty("title", action.title().getString());
            result.addProperty("description", action.description().getString());
            result.addProperty("programmatic", action.programmaticDescriptor().isPresent());
            context.getSource().publishStructuredResult(SFMClientActionStructuredResult.of(
                    "sfm.action-help/1", result));
            context.getSource().sendFeedback(Component.literal("help: " + id));
        }
        SFMScreenChangeHelpers.showProgramEditScreen(new SFMTextEditScreenOverlayOpenContext(
                text,
                LabelPositionHolder.empty(),
                ignored -> {
                }
        ));
        return 1;
    }
}
