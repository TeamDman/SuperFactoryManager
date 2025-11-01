package ca.teamdman.sfm.common.util;

import net.minecraft.util.EnumHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SFMHandUtils {
    public static @Nullable ItemStackInHand getItemAndHand(
            Player player,
            Item seeking
    ) {
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() == seeking) {
            return new ItemStackInHand(mainHandItem, EnumHand.MAIN_HAND);
        } else {
            ItemStack offhandItem = player.getOffhandItem();
            if (offhandItem.getItem() == seeking) {
                return new ItemStackInHand(offhandItem, EnumHand.OFF_HAND);
            }
        }
        return null;
    }

    public static ItemStack getItemInEitherHand(
            Player player,
            Item seeking
    ) {
        if (player.getMainHandItem().getItem() == seeking) {
            return player.getMainHandItem();
        } else if (player.getOffhandItem().getItem() == seeking) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    public static @Nullable EnumHand getHandHoldingItem(
            Player player,
            Item seeking
    ) {
        if (player.getMainHandItem().getItem() == seeking) {
            return EnumHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() == seeking) {
            return EnumHand.OFF_HAND;
        }
        return null;
    }

    public record ItemStackInHand(
            ItemStack stack,
            EnumHand hand
    ) {
    }
}
