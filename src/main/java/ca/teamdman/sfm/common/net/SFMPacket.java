package ca.teamdman.sfm.common.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.jetbrains.annotations.Nullable;

public abstract class SFMPacket<T extends SFMPacket<T>> implements IMessage, IMessageHandler<T, IMessage> {
    @Override
    @Nullable
    public IMessage onMessage(T message, MessageContext ctx) {
        return null;
    }


}