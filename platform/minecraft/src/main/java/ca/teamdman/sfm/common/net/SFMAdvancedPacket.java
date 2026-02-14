package ca.teamdman.sfm.common.net;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.SFM;

abstract public class SFMAdvancedPacket<T extends SFMAdvancedPacket> implements IMessage  {

    abstract void handle(
                         T msg,
                         SFMPacketHandlingContext context);

    @Nullable
    public IMessage onMessage(T message, MessageContext ctx) {
        var context = new SFMPacketHandlingContext(ctx);
        context.enqueueAndFinish(() -> {
            try {
                handle(message, context);
            } catch (Throwable t) {
                SFM.LOGGER.warn("Encountered exception while handling packet", t);
                throw t;
            }
        });
        return null;
    }

    abstract public void fromBytes(ByteBuf buf) ;

    abstract public void toBytes(ByteBuf buf);

    public static String truncate(
                                  String input,
                                  int maxLength) {
        if (input.length() > maxLength) {
            SFM.LOGGER.warn(
                    "input too big, truncation has occurred! (len={}, max={}, over={})",
                    input.length(),
                    maxLength,
                    maxLength - input.length());
            String truncationWarning = "\n...truncated";
            return input.substring(0, maxLength - truncationWarning.length()) + truncationWarning;
        }
        return input;
    }
}
