package ca.teamdman.sfm.common.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public interface SFMPacket<T> {
    public Wrapper<T> wrap();

    abstract class Wrapper<T> implements IMessage {
        abstract SFMPacketDaddy<T> getDaddy();

        public T ourRecord;

        @Override
        public void fromBytes(ByteBuf buf) {
            ourRecord = getDaddy().decode(new FriendlyByteBuf(buf));
        }

        @Override
        public void toBytes(ByteBuf buf) {
            getDaddy().encode(ourRecord, new FriendlyByteBuf(buf));
        }
    }
}
