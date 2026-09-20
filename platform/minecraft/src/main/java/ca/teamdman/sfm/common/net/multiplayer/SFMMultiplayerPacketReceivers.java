package ca.teamdman.sfm.common.net.multiplayer;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Common-side callback avoids resolving physical-client classes during dedicated packet registration. */
public final class SFMMultiplayerPacketReceivers {
    private static BiConsumer<Object, byte[]> client = (connection, frame) -> {};
    private SFMMultiplayerPacketReceivers() {}
    public static void setClient(BiConsumer<Object, byte[]> receiver) { client = Objects.requireNonNull(receiver); }
    public static void receiveClient(Object connection, byte[] frame) { client.accept(connection, frame); }
}
