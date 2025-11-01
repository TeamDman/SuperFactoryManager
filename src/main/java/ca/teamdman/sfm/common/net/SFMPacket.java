package ca.teamdman.sfm.common.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public abstract class SFMPacket<T extends SFMPacket<T>> implements IMessage, IMessageHandler<T, IMessage> {
    @Override
    public IMessage onMessage(T message, MessageContext ctx) {
        return null;
    }

    @Override
    public void fromBytes(ByteBuf buf) {

    }

    @Override
    public void toBytes(ByteBuf buf) {

    }
}