package ca.teamdman.sfm.common.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;

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

    public FriendlyByteBuf writeItem(ItemStack stack) {
         this.writeItemStack(stack);
         return this;
    }

    public ItemStack readItem()  {
        try {
            return this.readItemStack();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
