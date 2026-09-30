package ca.teamdman.sfm.common.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class InPlaceBlockPlaceContext extends BlockPlaceContext {
    public InPlaceBlockPlaceContext(
            Player pPlayer,
            InteractionHand pHand,
            ItemStack pItemStack,
            BlockHitResult pHitResult
    ) {
        super(pPlayer, pHand, pItemStack, pHitResult);
        this.replaceClicked = true;
    }

    @SuppressWarnings("unused")
    public InPlaceBlockPlaceContext(UseOnContext pContext) {
        super(pContext);
        this.replaceClicked = true;
    }

    @SuppressWarnings("unused")
    public InPlaceBlockPlaceContext(
            Level pLevel,
            @Nullable Player pPlayer,
            InteractionHand pHand,
            ItemStack pItemStack,
            BlockHitResult pHitResult
    ) {
        super(pLevel, pPlayer, pHand, pItemStack, pHitResult);
        this.replaceClicked = true;
    }
}
