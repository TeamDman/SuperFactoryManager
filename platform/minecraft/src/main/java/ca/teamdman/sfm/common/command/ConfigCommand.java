package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMConfigReadWriter;
import ca.teamdman.sfm.common.net.ClientboundShowConfigScreenPacket;
import ca.teamdman.sfm.common.registry.SFMPackets;
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
            case CLIENT -> {
                // The client handles reading the config

                // Tell the client to open the config editor
                SFMPackets.sendToPlayer(
                        player,
                        new ClientboundShowConfigScreenPacket(variant, behaviour, "")
                );
                yield FAILURE;
            }
            case TEXT -> {
                // The client handles reading the config

                // Tell the client to open the config editor
                SFMPackets.sendToPlayer(
                        player,
                        new ClientboundShowConfigScreenPacket(variant, behaviour, "")
                );
                yield SINGLE_SUCCESS;
            }
            case SERVER -> {
                // Read the config
                String configToml = SFMConfigReadWriter.getConfigToml(SFMConfig.SERVER_CONFIG_SPEC);

                if (configToml != null) {
                    // Tell the client to open the config editor
                    SFMPackets.sendToPlayer(
                            player,
                            new ClientboundShowConfigScreenPacket(variant, behaviour, configToml)
                    );
                } else {
                    // Give user feedback that config loading failed
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
                }
                yield SINGLE_SUCCESS;
            }
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

}