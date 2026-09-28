package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.common.util.AtomicIdExtension;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FluidStack.class)
public abstract class FluidStackMixin implements AtomicIdExtension {
    @Shadow
    public abstract Fluid getFluid();

    @Override
    public int sfm$getAtomicId() {
        return ((AtomicIdExtension) this.getFluid()).sfm$getAtomicId();
    }
}
