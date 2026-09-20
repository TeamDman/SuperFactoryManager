package ca.teamdman.sfm.common.net;

import java.util.Objects;
import java.util.function.Consumer;

/** Client controller installs its receiver explicitly. Common packet registration loads no client UI/key class. */
public final class ClientManagerSigningResponses {
    private static volatile Consumer<ClientboundClientManagerSigningResponsePacket> receiver = ignored -> { };
    private ClientManagerSigningResponses() { }
    public static void setReceiver(Consumer<ClientboundClientManagerSigningResponsePacket> value) {
        receiver = Objects.requireNonNull(value);
    }
    public static void receive(ClientboundClientManagerSigningResponsePacket response) { receiver.accept(response); }
}
