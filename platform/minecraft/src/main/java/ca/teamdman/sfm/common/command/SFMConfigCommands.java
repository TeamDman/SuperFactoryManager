package ca.teamdman.sfm.common.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.server.command.EnumArgument;

public final class SFMConfigCommands {
    private SFMConfigCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("config")
                .then(Commands.literal("show")
                        .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                        .then(Commands
                                .argument(
                                        "variant",
                                        EnumArgument.enumArgument(ConfigCommandVariantInput.class)
                                )
                                .executes(ctx -> new ConfigCommand(
                                        ConfigCommandBehaviourInput.SHOW,
                                        ctx.getArgument(
                                                "variant",
                                                ConfigCommandVariantInput.class
                                        )
                                ).run(ctx))
                        )
                )
                .then(Commands.literal("edit")
                        .then(
                                Commands.literal(ConfigCommandVariantInput.SERVER.name())
                                        .requires(source -> source.hasPermission(Commands.LEVEL_OWNERS))
                                        .executes(new ConfigCommand(
                                                ConfigCommandBehaviourInput.EDIT,
                                                ConfigCommandVariantInput.SERVER
                                        ))
                        )
                        .then(
                                Commands.literal(ConfigCommandVariantInput.CLIENT.name())
                                        .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                                        .executes(new ConfigCommand(
                                                ConfigCommandBehaviourInput.EDIT,
                                                ConfigCommandVariantInput.CLIENT
                                        ))
                        )
                );
    }
}