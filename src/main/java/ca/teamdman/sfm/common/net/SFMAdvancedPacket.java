package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.Nullable;

abstract public class SFMAdvancedPacket<T extends SFMPacket<T>> extends SFMPacket<T> {
    abstract void handle(
            T msg,
            SFMPacketHandlingContext context
    );

    @Override
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

    public static String truncate(
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
