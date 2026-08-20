package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.common.util.AtomicIdExtension;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.atomic.AtomicInteger;

@Mixin(Item.class)
public class ItemMixin implements AtomicIdExtension {
    @Unique
    private static final AtomicInteger sfm$counter = new AtomicInteger(0);

    @Unique
    private final int sfm$id = sfm$counter.getAndAdd(1);

    @Override
    public int sfm$getAtomicId() {
        return sfm$id;
    }
}
