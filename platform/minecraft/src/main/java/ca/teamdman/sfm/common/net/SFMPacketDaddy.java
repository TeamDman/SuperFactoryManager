package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nullable;


public interface SFMPacketDaddy<T> extends IMessageHandler<SFMPacket.Wrapper<T>, IMessage> {
    enum PacketDirection {

        SERVERBOUND {
            @Override
            public Side toSide() {
                return Side.SERVER;
            }
        },
        CLIENTBOUND {
            @Override
            public Side toSide() {
                return Side.CLIENT;
            }
        };

        abstract public Side toSide();
    }

    Class<? extends SFMPacket.Wrapper<T>> getPacketClass();

    PacketDirection getPacketDirection();

    void encode(
            T msg,
            FriendlyByteBuf friendlyByteBuf
    );

    T decode(FriendlyByteBuf friendlyByteBuf);

    void handle(
            T msg,
            SFMPacketHandlingContext context
    );

    @Override
    @Nullable
    default IMessage onMessage(SFMPacket.Wrapper<T> msg, MessageContext ctx) {
        SFMPacketHandlingContext context = new SFMPacketHandlingContext(ctx);
        context.enqueueAndFinish(() -> {
            try {
                handle(msg.ourRecord, context);
            } catch (Throwable t) {
                SFM.LOGGER.warn("Encountered exception while handling packet", t);
                throw t;
            }
        });
        return null;
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