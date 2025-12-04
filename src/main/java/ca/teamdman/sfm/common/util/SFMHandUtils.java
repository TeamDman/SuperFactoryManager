package ca.teamdman.sfm.common.util;

import com.github.bsideup.jabel.Desugar;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.item.ItemStack.EMPTY;
import static net.minecraft.util.EnumHand.MAIN_HAND;
import static net.minecraft.util.EnumHand.OFF_HAND;

public class SFMHandUtils {
    public static @Nullable ItemStackInHand getItemAndHand(
            EntityPlayer player,
            Item seeking
    ) {
        ItemStack mainHandItem = player.getHeldItemMainhand();
        if (mainHandItem.getItem() == seeking) {
            return new ItemStackInHand(mainHandItem, MAIN_HAND);
        } else {
            ItemStack offhandItem = player.getHeldItemOffhand();
            if (offhandItem.getItem() == seeking) {
                return new ItemStackInHand(offhandItem, OFF_HAND);
            }
        }
        return null;
    }

    public static ItemStack getItemInEitherHand(
            EntityPlayer player,
            Item seeking
    ) {
        if (player.getHeldItemMainhand().getItem() == seeking) {
            return player.getHeldItemMainhand();
        } else if (player.getHeldItemOffhand().getItem() == seeking) {
            return player.getHeldItemOffhand();
        }
        return EMPTY;
    }

    public static @Nullable EnumHand getHandHoldingItem(
            EntityPlayer player,
            Item seeking
    ) {
        if (player.getHeldItemMainhand().getItem() == seeking) {
            return MAIN_HAND;
        } else if (player.getHeldItemOffhand().getItem() == seeking) {
            return OFF_HAND;
        }
        return null;
    }

    @Desugar public record ItemStackInHand(
        ItemStack stack,
            EnumHand hand
) {}
}
