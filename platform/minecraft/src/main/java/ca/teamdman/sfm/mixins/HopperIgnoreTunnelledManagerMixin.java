package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.common.block.TunnelledManagerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HopperBlockEntity.class, priority = 0)
public class HopperIgnoreTunnelledManagerMixin {
    @Inject(method = "getBlockContainer", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onHopperTargetTunnelledManager(
            Level level,
            BlockPos pos,
            BlockState state,
            CallbackInfoReturnable<@Nullable Container> cir
    ) {
        Block block = state.getBlock();
        if (block instanceof TunnelledManagerBlock) {
            cir.setReturnValue(null);
        }
    }
}
