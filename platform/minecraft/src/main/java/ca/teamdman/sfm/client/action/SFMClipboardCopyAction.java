package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Copies one canonical client-action command assembled from its action tail. */
public final class SFMClipboardCopyAction implements SFMClientAction<SFMClientActionContext> {
    private static final String ACTION_PREFIX = "sfm action invoke ";

    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE = new LocalizationEntry(
            "gui.sfm.client_action.clipboard.copy_action.title",
            "Copy action command"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry DESCRIPTION = new LocalizationEntry(
            "gui.sfm.client_action.clipboard.copy_action.description",
            "Copy a canonical SFM action invocation to the clipboard"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry COPIED = new LocalizationEntry(
            "gui.sfm.client_action.clipboard.copy_action.copied",
            "Copied action command: %s"
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
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "action_tail",
                        StringArgumentType.greedyString()
                )
                .executes(this::invoke));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) {
        String command = ACTION_PREFIX + StringArgumentType.getString(context, "action_tail").strip();
        Minecraft.getInstance().keyboardHandler.setClipboard(command);
        context.getSource().sendFeedback(COPIED.getComponent(Component.literal(command)));
        return 1;
    }
}
