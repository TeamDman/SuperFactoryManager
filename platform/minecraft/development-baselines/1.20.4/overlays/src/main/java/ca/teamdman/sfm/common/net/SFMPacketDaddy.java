package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.PlayPayloadContext;

public interface SFMPacketDaddy<T extends SFMPacket> {
    enum PacketDirection {
        SERVERBOUND,
        CLIENTBOUND
    }

    PacketDirection getPacketDirection();

    Class<T> getPacketClass();

    void encode(
            T msg,
            FriendlyByteBuf friendlyByteBuf
    );

    T decode(FriendlyByteBuf friendlyByteBuf);

    void handle(
            T msg,
            SFMPacketHandlingContext context
    );

    default void handleOuter(
            T msg,
            PlayPayloadContext outerContext
    ) {
        SFMPacketHandlingContext context = new SFMPacketHandlingContext(outerContext);
        context.enqueueAndFinish(() -> {
            try {
                handle(msg, context);
            } catch (Throwable t) {
                SFM.LOGGER.warn("Encountered exception while handling packet", t);
                throw t;
            }
        });
    }

    static String truncate(
            String input,
            int maxLength
    ) {
        if (input.length() > maxLength) {
            SFM.LOGGER.warn(
                    "input too big, truncation has occurred! (len={}, max={}, over={})",
                    input.length(),
                    maxLength,
                    maxLength - input.length()
            );
            String truncationWarning = "\n...truncated";
            return input.substring(0, maxLength - truncationWarning.length()) + truncationWarning;
        }
        return input;
    }
}
