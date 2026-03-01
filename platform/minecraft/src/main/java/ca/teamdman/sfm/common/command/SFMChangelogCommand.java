package ca.teamdman.sfm.common.command;

import ca.teamdman.sfm.common.net.ClientboundShowChangelogPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class SFMChangelogCommand {
    private SFMChangelogCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("changelog")
                .requires(source -> source.hasPermission(Commands.LEVEL_ALL))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) {
                        SFMPackets.sendToPlayer(player, new ClientboundShowChangelogPacket());
                    }
                    return SINGLE_SUCCESS;
                });
    }
}