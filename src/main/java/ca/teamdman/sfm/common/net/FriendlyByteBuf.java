package ca.teamdman.sfm.common.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;

public class FriendlyByteBuf extends PacketBuffer {
    public FriendlyByteBuf(ByteBuf wrapped) {
        super(wrapped);
    }

    public FriendlyByteBuf writeEnum(Enum<?> value) {
        this.writeEnumValue(value);
        return this;
    }

    public <T extends Enum<T>> T readEnum(Class<T> enumClass) {
        return this.readEnumValue(enumClass);
    }

    public String readUtf(int maxLength) {
        return this.readString(maxLength);
    }

    public FriendlyByteBuf writeUtf(String value, int maxLength) {
        this.writeString(SFMPacketDaddy.truncate(value, maxLength));
        return this;
    }

}
