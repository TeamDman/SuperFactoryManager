package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.LabelGunItem;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
{% case minecraft_version %}
{% when '1.19.2' %}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
{% when '1.19.4', '1.20', '1.20.1' %}
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
{% when '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
{% when '1.21', '1.21.1' %}
import net.minecraft.core.HolderLookup;
{% when '26.1.2' %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
{% endcase %}
import net.minecraft.world.item.ItemStack;
{% case minecraft_version %}
{% when '1.19.2' %}
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import net.minecraft.world.item.crafting.CraftingBookCategory;
{% when '1.21', '1.21.1' %}
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
{% when '26.1.2' %}
import net.minecraft.world.item.crafting.CraftingInput;
{% endcase %}
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
 * Printing press copies a form using ink and paper.
{% when '26.1.2' %}
 * Clears all data from label guns
{% endcase %}
 */
public class LabelGunResetRecipe extends CustomRecipe {
{% case minecraft_version %}
{% when '1.19.2' %}
    public LabelGunResetRecipe(
            ResourceLocation id
    ) {
        super(id);
    }
{% when '1.19.4', '1.20', '1.20.1' %}
    public LabelGunResetRecipe(
            ResourceLocation pId,
            CraftingBookCategory pCategory
    ) {
        super(pId, pCategory);
    }
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public LabelGunResetRecipe(
            CraftingBookCategory pCategory
    ) {
        super(pCategory);
    }
{% when '26.1.2' %}
    private static final LabelGunResetRecipe INSTANCE = new LabelGunResetRecipe();

    @MCVersionDependentBehaviour
    public static final MapCodec<LabelGunResetRecipe> CODEC =
            MapCodec.unit(INSTANCE);

    @MCVersionDependentBehaviour
    public static final StreamCodec<RegistryFriendlyByteBuf, LabelGunResetRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public LabelGunResetRecipe() {}
{% endcase %}

    @Override
    public boolean matches(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            CraftingContainer pContainer,
            Level pLevel
{% when '1.21', '1.21.1', '26.1.2' %}
            CraftingInput craftingInput,
            Level level
{% endcase %}
    ) {
        int foundLabelGuns = 0;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
{% when '1.21', '1.21.1', '26.1.2' %}
        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack stack = craftingInput.getItem(i);
{% endcase %}
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return foundLabelGuns > 0;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    public ItemStack assemble(
            CraftingContainer pContainer
    ) {
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public ItemStack assemble(
            CraftingContainer craftingContainer,
            RegistryAccess registryAccess
    ) {
{% when '1.21', '1.21.1' %}
    public ItemStack assemble(
            CraftingInput craftingInput,
            HolderLookup.Provider provider
    ) {
{% when '26.1.2' %}
    public ItemStack assemble(
            CraftingInput craftingInput
    ) {
{% endcase %}
        int foundLabelGuns = 0;
{% case minecraft_version %}
{% when '1.19.2' %}
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        for (int i = 0; i < craftingContainer.getContainerSize(); i++) {
            ItemStack stack = craftingContainer.getItem(i);
{% when '1.21', '1.21.1', '26.1.2' %}
        for (int i = 0; i < craftingInput.size(); i++) {
            ItemStack stack = craftingInput.getItem(i);
{% endcase %}
            if (stack.getItem() instanceof LabelGunItem) {
                foundLabelGuns++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return foundLabelGuns > 0 ? new ItemStack(SFMItems.LABEL_GUN.get(), foundLabelGuns) : ItemStack.EMPTY;
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public boolean canCraftInDimensions(
            int pWidth,
            int pHeight
    ) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
{% when '26.1.2' %}
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
{% endcase %}
        return SFMRecipeSerializers.LABEL_GUN_RESET.get();
    }
}
