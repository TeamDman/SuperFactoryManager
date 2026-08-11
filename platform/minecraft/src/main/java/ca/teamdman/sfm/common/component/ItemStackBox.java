package ca.teamdman.sfm.common.component;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record ItemStackBox(ItemStack stack) {
    public static final ItemStackBox EMPTY = new ItemStackBox(ItemStack.EMPTY);
    public static final Codec<ItemStackBox> CODEC = ItemStack.OPTIONAL_CODEC.xmap(ItemStackBox::new, ItemStackBox::stack);
    public static final StreamCodec<? super RegistryFriendlyByteBuf, ItemStackBox> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC.map(
            ItemStackBox::new,
            ItemStackBox::stack
    );

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ItemStackBox other) {
            return ItemStack.isSameItemSameComponents(stack(), other.stack());
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return stack().hashCode();
    }

    @Override
    public String toString() {
        return "ItemStackBox{" +
               "stack=" + stack() +
               '}';
    }
}
