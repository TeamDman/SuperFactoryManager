package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.common.util.AtomicIdExtension;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements AtomicIdExtension {
    @Shadow
    public abstract Item getItem();

    @Override
    public int sfm$getAtomicId() {
        return ((AtomicIdExtension) this.getItem()).sfm$getAtomicId();
    }
}
