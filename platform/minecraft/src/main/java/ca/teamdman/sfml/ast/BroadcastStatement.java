package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.SFMPacketEffectGate;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.SFMServerClientInboxTransport;
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramResourceObserver;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Non-consuming, best-effort observation of selected packet inputs by one player. */
public record BroadcastStatement(String playerAlias, @Nullable ResourceLocation channel) implements Statement {
    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_BROADCAST_PLAYER_NOT_CONNECTED = new LocalizationEntry(
            "log.sfm.statement.tick.broadcast.player_not_connected",
            "Packet broadcast target player %s is not connected"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_BROADCAST_EFFECTS_DISABLED = new LocalizationEntry(
            "log.sfm.statement.tick.broadcast.effects_disabled",
            "Packet broadcast to %s was skipped because packet effects are unavailable"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry LOG_PROGRAM_TICK_BROADCAST_DELIVERY_STOPPED = new LocalizationEntry(
            "log.sfm.statement.tick.broadcast.delivery_stopped",
            "Packet broadcast to %s stopped: %s"
    );

    public BroadcastStatement {
        Objects.requireNonNull(playerAlias);
    }

    public BroadcastStatement(String playerAlias) {
        this(playerAlias, null);
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
            context.getLogger().debug(LOG_PROGRAM_TICK_BROADCAST_PLAYER_NOT_CONNECTED.get(playerName));
            return;
        }
        if (!SFMPacketEffectGate.allowsServerEffects(player)
            && (channel == null || !ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerServerRuntime.negotiatedPathAvailable(player))) {
            context.getLogger().debug(LOG_PROGRAM_TICK_BROADCAST_EFFECTS_DISABLED.get(playerName));
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
                    if (channel == null) {
                        SFMPackets.sendPacketObservation(player, value);
                    } else {
                        SFMServerClientInboxTransport.Result result = SFMServerClientInboxTransport.publish(
                                context.getManager(),
                                player,
                                new SFMClientInboxAddress(
                                        player.getUUID(), context.getLevel().dimension().location(), channel
                                ),
                                value
                        );
                        if (result != SFMServerClientInboxTransport.Result.SENT) {
                            context.getLogger().debug(LOG_PROGRAM_TICK_BROADCAST_DELIVERY_STOPPED.get(
                                    playerName, result.name()
                            ));
                            break;
                        }
                    }
                }
            });
        });
    }

    @Override
    public String toString() {
        return "BROADCAST TO " + playerAlias + (channel == null ? "" : " CHANNEL " + channel);
    }
}
