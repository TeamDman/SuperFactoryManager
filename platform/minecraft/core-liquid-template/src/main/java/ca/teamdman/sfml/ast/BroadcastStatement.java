package ca.teamdman.sfml.ast;

import ca.teamdman.sfm.common.item.PacketItem;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.SFMPacketEffectGate;
{% if features.client_inbox %}
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.net.SFMServerClientInboxTransport;
{% endif %}
import ca.teamdman.sfm.common.program.ProgramContext;
import ca.teamdman.sfm.common.program.ProgramResourceObserver;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.registry.registration.SFMResourceTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
{% if features.client_inbox %}
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
{% endif %}

import java.util.Objects;

/** Non-consuming, best-effort observation of selected packet inputs by one player. */
{% if features.client_inbox %}
public record BroadcastStatement(String playerAlias, @Nullable ResourceLocation channel) implements Statement {
{% else %}
public record BroadcastStatement(String playerAlias) implements Statement {
{% endif %}
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

{% if features.client_inbox %}
    public BroadcastStatement(String playerAlias) {
        this(playerAlias, null);
    }
{% endif %}

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
{% if features.multiplayer_packets %}
        if (!SFMPacketEffectGate.allowsServerEffects(player)
            && (channel == null || !ca.teamdman.sfm.common.net.multiplayer.SFMMultiplayerServerRuntime.negotiatedPathAvailable(player))) {
{% else %}
        if (!SFMPacketEffectGate.allowsServerEffects(player)) {
{% endif %}
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
{% if features.client_inbox %}
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
{% else %}
                    SFMPackets.sendPacketObservation(player, value);
{% endif %}
                }
            });
        });
    }

    @Override
    public String toString() {
{% if features.client_inbox %}
        return "BROADCAST TO " + playerAlias + (channel == null ? "" : " CHANNEL " + channel);
{% else %}
        return "BROADCAST TO " + playerAlias;
{% endif %}
    }
}
