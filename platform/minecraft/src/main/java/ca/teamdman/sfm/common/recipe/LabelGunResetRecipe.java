package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/**
 * Clears all data from label guns
 */
public class LabelGunResetRecipe extends CustomRecipe {
    public LabelGunResetRecipe() {}

    @MCVersionDependentBehaviour
    public static final MapCodec<LabelGunResetRecipe> CODEC =
            MapCodec.unit(LabelGunResetRecipe::new);

    @MCVersionDependentBehaviour
    public static final StreamCodec<RegistryFriendlyByteBuf, LabelGunResetRecipe> STREAM_CODEC =
            StreamCodec.unit(new LabelGunResetRecipe());

    @Override
    public boolean matches(
            CraftingInput craftingInput,
            Level level
    ) {
        int foundLabelGuns = 0;
        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack stack = craftingInput.getItem(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return foundLabelGuns > 0;
    }

    @Override
    public ItemStack assemble(
            CraftingInput craftingInput
    ) {
        int foundLabelGuns = 0;
        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack stack = craftingInput.getItem(i);
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return foundLabelGuns > 0 ? new ItemStack(SFMItems.LABEL_GUN.get(), foundLabelGuns) : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return SFMRecipeSerializers.LABEL_GUN_RESET.get();
    }
}
