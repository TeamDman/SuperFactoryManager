package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Clears all data from a program disk
 */
public class DiskResetRecipe extends CustomRecipe {
    private static final DiskResetRecipe INSTANCE = new DiskResetRecipe();

    @MCVersionDependentBehaviour
    public static final MapCodec<DiskResetRecipe> CODEC =
            MapCodec.unit(INSTANCE);

    @MCVersionDependentBehaviour
    public static final StreamCodec<RegistryFriendlyByteBuf, DiskResetRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

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
