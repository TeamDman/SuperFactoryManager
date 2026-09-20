package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.net.ClientboundClientInboxValuePacket;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;

import java.util.Optional;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;

/** Client-thread network adapter; the inbox itself never opens a screen or owns input. */
public final class SFMClientInboxTransport {
    private SFMClientInboxTransport() {
    }

    public static Optional<SFMClientInboxRuntime.Subscription> subscribe(SFMClientInboxAddress address) {
        return subscribe(address, Optional.empty());
    }

    public static Optional<SFMClientInboxRuntime.Subscription> subscribe(SFMClientInboxAddress address,
                                                                        Optional<ClientProgramIdentity> caller) {
        observeCurrentWorld();
        if (!SFMClientPacketTransport.effectsAllowed(Minecraft.getInstance())) {
            if (!SFMMultiplayerClientRuntime.available()) return Optional.empty();
            return SFMClientInboxRuntime.get().subscribe(address, caller, packet -> {
                if (!SFMMultiplayerClientRuntime.sendSubscription(packet.session(), address, caller, packet.subscribe())) {
                    throw new IllegalStateException("Remote inbox request was not accepted locally");
                }
            });
        }
        return SFMClientInboxRuntime.get().subscribe(address, SFMPackets::sendToServer);
    }

    public static boolean receive(ClientboundClientInboxValuePacket packet) {
        if (!SFMClientPacketTransport.effectsAllowed(Minecraft.getInstance())) return false;
        observeCurrentWorld();
        return SFMClientInboxRuntime.get().receive(packet);
    }

    public static void observeCurrentWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        LocalPlayer player = minecraft.player;
        SFMClientInboxRuntime.get().observeSessionIdentity(
                SFMClientPacketTransport.effectsAllowed(minecraft) ? server : SFMMultiplayerClientRuntime.session().orElse(null),
                minecraft.level,
                player == null ? null : player.getUUID(),
                minecraft.level == null ? null : minecraft.level.dimension().location(),
                SFMClientPacketTransport.effectsAllowed(minecraft) || SFMMultiplayerClientRuntime.available()
        );
    }
}
