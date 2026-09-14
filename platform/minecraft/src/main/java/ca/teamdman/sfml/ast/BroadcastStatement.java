package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramResourceObserver;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Non-consuming, best-effort observation of selected packet inputs by one player. */
public record BroadcastStatement(String playerAlias) implements Statement {
    public BroadcastStatement {
        Objects.requireNonNull(playerAlias);
    }

    @Override
    public void tick(ProgramContext context) {
        if (!context.getBehaviour().allowsRuntimeMaterialization()) {
            return;
        }
        String playerName = context.getProgram().definitions().player(playerAlias).orElse(null);
        MinecraftServer server = context.getLevel() == null ? null : context.getLevel().getServer();
        if (playerName == null || server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            return;
        }
        ProgramResourceObserver.observe(
                context,
                (resourceType, stack) -> resourceType == SFMResourceTypes.ITEM.get()
                                         && stack instanceof ItemStack itemStack
                                         && PacketItem.getValue(itemStack).isPresent()
        ).forEach(observation -> {
            ItemStack stack = (ItemStack) observation.stack();
            PacketItem.getValue(stack).ifPresent(value -> {
                for (long occurrence = 0; occurrence < observation.amount(); occurrence++) {
                    SFMPackets.sendPacketObservation(player, value);
                }
            });
        });
    }

    @Override
    public String toString() {
        return "BROADCAST TO " + playerAlias;
    }
}
