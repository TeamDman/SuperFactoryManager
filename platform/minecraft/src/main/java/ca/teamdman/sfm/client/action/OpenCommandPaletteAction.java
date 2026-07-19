package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;

public final class OpenCommandPaletteAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return SFMCommandPaletteScreen.TITLE.getComponent();
    }

    @Override
    public Component description() {
        return SFMCommandPaletteScreen.INPUT_PLACEHOLDER.getComponent();
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return SFMClientActionAvailability::available;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(this::invoke);
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                "initial_query",
                StringArgumentType.greedyString()
        ).executes(this::invoke));
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) {
        String initialQuery;
        try {
            initialQuery = StringArgumentType.getString(context, "initial_query");
        } catch (IllegalArgumentException ignored) {
            initialQuery = "";
        }
        SFMCommandPaletteScreen.open(target, initialQuery);
        return 1;
    }
}
