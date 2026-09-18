package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.net.ClientboundClientInboxValuePacket;
import ca.teamdman.sfm.common.net.SFMClientInboxAddress;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;

import java.util.Optional;

/** Client-thread network adapter; the inbox itself never opens a screen or owns input. */
public final class SFMClientInboxTransport {
    private SFMClientInboxTransport() {
    }

    public static Optional<SFMClientInboxRuntime.Subscription> subscribe(SFMClientInboxAddress address) {
        observeCurrentWorld();
        return SFMClientInboxRuntime.get().subscribe(address, SFMPackets::sendToServer);
    }

    public static boolean receive(ClientboundClientInboxValuePacket packet) {
        observeCurrentWorld();
        return SFMClientInboxRuntime.get().receive(packet);
    }

    public static void observeCurrentWorld() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        LocalPlayer player = minecraft.player;
        SFMClientInboxRuntime.get().observeSessionIdentity(
                server,
                minecraft.level,
                player == null ? null : player.getUUID(),
                minecraft.level == null ? null : minecraft.level.dimension().location(),
                SFMClientPacketTransport.effectsAllowed(minecraft)
        );
    }
}
