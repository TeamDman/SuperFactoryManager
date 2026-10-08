package ca.teamdman.sfm.common.recipe;

import ca.teamdman.sfm.common.item.DiskItem;
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
 * Clears all data from a program disk
{% endcase %}
 */
public class DiskResetRecipe extends CustomRecipe {
{% case minecraft_version %}
{% when '1.19.2' %}
    public DiskResetRecipe(ResourceLocation id) {
        super(id);
{% when '1.19.4', '1.20', '1.20.1' %}
    public DiskResetRecipe(ResourceLocation id, CraftingBookCategory pGroup) {
        super(id, pGroup);
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public DiskResetRecipe(CraftingBookCategory pGroup) {
        super(pGroup);
{% when '26.1.2' %}
    private static final DiskResetRecipe INSTANCE = new DiskResetRecipe();

    @MCVersionDependentBehaviour
    public static final MapCodec<DiskResetRecipe> CODEC =
            MapCodec.unit(INSTANCE);

    @MCVersionDependentBehaviour
    public static final StreamCodec<RegistryFriendlyByteBuf, DiskResetRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    public DiskResetRecipe() {
        super();
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    @Override
    public boolean matches(CraftingContainer pContainer, Level pLevel) {
        int foundDisks = 0;
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
{% when '1.21', '1.21.1', '26.1.2' %}
    public int countDisks(CraftingInput input) {
        int found = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) continue;
{% endcase %}
            if (stack.getItem() instanceof DiskItem) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                foundDisks++;
            } else if (!stack.isEmpty()) {
                return false;
{% when '1.21', '1.21.1', '26.1.2' %}
                found++;
            } else {
                return -1;
{% endcase %}
            }
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return foundDisks > 0;
{% when '1.21', '1.21.1', '26.1.2' %}
        return found;
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2' %}
    public ItemStack assemble(CraftingContainer pContainer) {
        int foundDisks = 0;
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
            if (stack.getItem() instanceof DiskItem) {
                foundDisks++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
{% when '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public ItemStack assemble(CraftingContainer pContainer, RegistryAccess registryAccess) {
        int foundDisks = 0;
        for (int i = 0; i < pContainer.getContainerSize(); i++) {
            ItemStack stack = pContainer.getItem(i);
            if (stack.getItem() instanceof DiskItem) {
                foundDisks++;
            } else if (!stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
{% when '1.21', '1.21.1' %}
    public boolean matches(
            CraftingInput craftingInput,
            Level pLevel
    ) {
        return countDisks(craftingInput) > 0;
    }

    @Override
    public ItemStack assemble(
            CraftingInput craftingInput,
            HolderLookup.Provider provider
    ) {
        int foundDisks = countDisks(craftingInput);
        if (foundDisks > 0) {
            return new ItemStack(SFMItems.DISK.get(), foundDisks);
        } else {
            return ItemStack.EMPTY;
{% when '26.1.2' %}
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
{% endcase %}
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return foundDisks > 0 ? new ItemStack(SFMItems.DISK.get(), foundDisks) : ItemStack.EMPTY;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public boolean canCraftInDimensions(
            int pWidth,
            int pHeight
    ) {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
{% when '1.21', '1.21.1' %}
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
        return SFMRecipeSerializers.DISK_RESET.get();
    }
}
