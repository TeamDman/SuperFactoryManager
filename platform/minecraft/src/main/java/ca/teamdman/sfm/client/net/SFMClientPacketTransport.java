package ca.teamdman.sfm.client.net;

import ca.teamdman.sfm.common.net.SFMPacketEffectGate;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.SFMPacketValueDispatch;
import ca.teamdman.sfm.common.net.SFMPacketValueEnvelope;
import ca.teamdman.sfm.common.net.ServerboundPacketInsertionPacket;
import ca.teamdman.sfm.common.registry.registration.SFMPackets;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.value.SFMValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import ca.teamdman.sfm.client.program.ClientProgramIdentity;
import java.util.Optional;

/** Client-thread transport boundary for the packet-computation MVP. */
public final class SFMClientPacketTransport {
    private SFMClientPacketTransport() {
    }

    public static SFMPacketValueDispatch.Result receiveObservation(
            SFMPacketValueEnvelope value
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer session = minecraft.getSingleplayerServer();
        return SFMPacketValueDispatch.dispatch(
                effectsAllowed(minecraft),
                value,
                decoded -> acceptValidatedObservation(session, decoded)
        );
    }

    public static boolean sendInsertion(
            SFMPacketInventoryAddress target,
            SFMValue value
    ) {
        return sendInsertion(target, value, Optional.empty());
    }

    public static boolean sendInsertion(SFMPacketInventoryAddress target, SFMValue value,
                                        Optional<ClientProgramIdentity> caller) {
        if (!effectsAllowed(Minecraft.getInstance())) {
            return SFMMultiplayerClientRuntime.sendInsertion(target, value, caller);
        }
        SFMPackets.sendToServer(ServerboundPacketInsertionPacket.fromValue(target, value));
        return true;
    }

    /** The local optimistic check; the server independently rechecks before mutation. */
    public static boolean effectsAllowedNow() {
        return effectsAllowed(Minecraft.getInstance()) || SFMMultiplayerClientRuntime.available();
    }

    @MCVersionDependentBehaviour
    static boolean effectsAllowed(Minecraft minecraft) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        LocalPlayer player = minecraft.player;
        return server != null
               && player != null
               && SFMPacketEffectGate.allowsPrivateIntegratedWorld(
                       server.isSingleplayer(),
                       server.isPublished(),
                       server.isSingleplayerOwner(player.getGameProfile())
               );
    }

    private static void acceptValidatedObservation(
            IntegratedServer session,
            SFMValue value
    ) {
        if (session != null) {
            SFMPacketObservationRuntime.get().append(session, value);
        }
    }
}
