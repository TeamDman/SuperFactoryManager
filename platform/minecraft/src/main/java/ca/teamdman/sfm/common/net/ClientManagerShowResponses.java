package ca.teamdman.sfm.common.net;

import java.util.Objects;
import java.util.function.Consumer;

/** Client-only receiver registration; common packet setup does not load client classes. */
public final class ClientManagerShowResponses {
    private static volatile Consumer<ClientboundManagerShowPacket> receiver = ignored -> { };

    private ClientManagerShowResponses() { }

    public static void setReceiver(Consumer<ClientboundManagerShowPacket> value) {
        receiver = Objects.requireNonNull(value);
    }

    public static void receive(ClientboundManagerShowPacket response) {
        receiver.accept(response);
    }
}
