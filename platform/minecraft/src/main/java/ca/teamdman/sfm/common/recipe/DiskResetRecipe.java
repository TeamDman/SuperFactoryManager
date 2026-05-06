package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/**
 * Clears all data from a program disk
 */
public class DiskResetRecipe extends CustomRecipe {
    @MCVersionDependentBehaviour
    public static final MapCodec<DiskResetRecipe> CODEC =
            MapCodec.unit(DiskResetRecipe::new);

    @MCVersionDependentBehaviour
    public static final StreamCodec<RegistryFriendlyByteBuf, DiskResetRecipe> STREAM_CODEC =
            StreamCodec.unit(new DiskResetRecipe());

    public DiskResetRecipe() {
        super();
    }

    public int countDisks(CraftingInput input) {
        int found = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof DiskItem) {
                found++;
            } else {
                return -1;
            }
        }
        return found;
    }

    @Override
    public boolean matches(
            CraftingInput craftingInput,
            Level pLevel
    ) {
        return countDisks(craftingInput) > 0;
    }

    @Override
    public ItemStack assemble(
            CraftingInput craftingInput
    ) {
        int foundDisks = countDisks(craftingInput);
        if (foundDisks > 0) {
            return new ItemStack(SFMItems.DISK.get(), foundDisks);
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SFMRecipeSerializers.DISK_RESET.get();
    }
}
