package ca.teamdman.sfm.common.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SFMHandUtils {
    public static @Nullable SFMHandUtils.ItemStackInHand getItemAndHand(
            Player player,
            Item seeking
    ) {
        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.getItem() == seeking) {
            return new ItemStackInHand(mainHandItem, InteractionHand.MAIN_HAND);
        } else {
            ItemStack offhandItem = player.getOffhandItem();
            if (offhandItem.getItem() == seeking) {
                return new ItemStackInHand(offhandItem, InteractionHand.OFF_HAND);
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

    public static @Nullable InteractionHand getHandHoldingItem(
            Player player,
            Item seeking
    ) {
        if (player.getMainHandItem().getItem() == seeking) {
            return InteractionHand.MAIN_HAND;
        } else if (player.getOffhandItem().getItem() == seeking) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    public record ItemStackInHand(
            ItemStack stack,
            InteractionHand hand
    ) {
    }
}
