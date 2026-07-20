package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Writes a caller-provided message to the client console output. */
public final class EchoAction implements SFMClientAction<SFMClientActionContext> {
    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.echo.title",
            "Echo"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.echo.description",
            "Write a message to the SFM console"
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
    public void configureCommandNode(
            com.mojang.brigadier.builder.LiteralArgumentBuilder<SFMClientActionSource> node
    ) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "message",
                        StringArgumentType.greedyString()
                )
                .executes(this::invoke));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) {
        String message = StringArgumentType.getString(context, "message");
        context.getSource().sendFeedback(
                Component.literal("echo: ")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(message))
        );
        return 1;
    }
}
