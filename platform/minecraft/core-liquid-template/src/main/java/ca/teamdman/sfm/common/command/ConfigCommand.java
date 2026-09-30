package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import ca.teamdman.sfm.common.net.ClientboundClientConfigCommandPacket;
import ca.teamdman.sfm.common.net.ClientboundServerConfigCommandPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public record ConfigCommand(
        ConfigCommandBehaviourInput behaviour,
        ConfigCommandVariantInput variant
) implements Command<CommandSourceStack> {
    public static final int FAILURE = 0;

    @Override
    public int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = getPlayer(ctx);
        if (player == null) return FAILURE;

        return switch (variant) {
            case CLIENT -> handleClientConfigCommand(player);
            case SERVER -> handleServerConfigCommand(player);
        };
    }

    private @Nullable ServerPlayer getPlayer(
            CommandContext<CommandSourceStack> ctx
    ) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            SFM.LOGGER.error(
                    "Received config command ({} {}) from null player!?",
                    variant,
                    behaviour
            );
            return null;
        } else {
            SFM.LOGGER.info(
                    "Received config command ({} {}) from player {}",
                    variant,
                    behaviour,
                    player
            );
        }
        return player;
    }

    private int handleClientConfigCommand(ServerPlayer player) {
        SFMPackets.sendToPlayer(
                player,
                new ClientboundClientConfigCommandPacket(behaviour)
        );
        return FAILURE;
    }

    private int handleServerConfigCommand(ServerPlayer player) {
        String configToml = SFMConfigReadWriter.getConfigToml(
                SFMConfig.SERVER_CONFIG_SPEC);
        if (configToml == null) {
            SFM.LOGGER.warn(
                    "Unable to get server config for player {} to {}",
                    player.getName().getString(),
                    behaviour
            );
            player.sendSystemMessage(
                    SFMConfigReadWriter.ConfigSyncResult.FAILED_TO_FIND
                            .component()
                            .withStyle(ChatFormatting.RED)
            );
        } else {
            SFMPackets.sendToPlayer(
                    player,
                    new ClientboundServerConfigCommandPacket(
                            configToml,
                            behaviour
                    )
            );
        }
        return SINGLE_SUCCESS;
    }
}