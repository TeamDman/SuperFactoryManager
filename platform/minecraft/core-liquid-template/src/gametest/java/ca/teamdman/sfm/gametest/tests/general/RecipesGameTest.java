package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
{% else %}
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import net.minecraft.world.item.Item;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
{% when "26.1.2" %}
import net.minecraft.world.item.crafting.*;
{% else %}
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.Collection;
import java.util.HashMap;
{% else %}
import java.util.HashMap;
import java.util.List;
{% endcase %}
import java.util.Map;

@SuppressWarnings({"DataFlowIssue", "RedundantSuppression", "OptionalGetWithoutIsPresent"})
@SFMGameTest
public class RecipesGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {

        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        // Identify all crafting recipes
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        List<CraftingRecipe> craftingRecipes = helper
                .getLevel()
                .getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING);
{% when "26.1.2" %}
        Collection<RecipeHolder<CraftingRecipe>> craftingRecipes = helper
                .getLevel()
                .recipeAccess()
                .recipeMap()
                .byType(RecipeType.CRAFTING);
{% else %}
        List<RecipeHolder<CraftingRecipe>> craftingRecipes = helper
                .getLevel()
                .getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING);
{% endcase %}

        // We will track the SFM items whose recipes we have observed
{% case minecraft_version %}
{% when "26.1.2" %}
        Map<Identifier, Object> seenSFMItemIds = new HashMap<>();
{% else %}
        Map<ResourceLocation, Object> seenSFMItemIds = new HashMap<>();
{% endcase %}

        // Populate the tracker
        // For each recipe
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        for (CraftingRecipe recipe : craftingRecipes) {
{% else %}
        for (RecipeHolder<CraftingRecipe> recipeHolder : craftingRecipes) {
            CraftingRecipe recipe = recipeHolder.value();
{% endcase %}
            // If the resulting item is from SFM
{% case minecraft_version %}
{% when "1.19.2" %}
            ResourceLocation resultItemId = SFMWellKnownRegistries.ITEMS.getId(recipe.getResultItem().getItem());
{% when "26.1.2" %}
            if (recipe.isSpecial() || recipe instanceof ImbueRecipe) continue; // Imbue recipes throw an exception when used with .assemble(CraftingInput.EMPTY)
            Identifier resultItemId = SFMWellKnownRegistries.ITEMS.getId(recipe.assemble(CraftingInput.EMPTY).getItem());
{% else %}
            ResourceLocation resultItemId = SFMWellKnownRegistries.ITEMS.getId(recipe.getResultItem(helper.getLevel().registryAccess()).getItem());
{% endcase %}
            if (resultItemId.getNamespace().equals(SFM.MOD_ID)) {
                // Track it as seen
                seenSFMItemIds.put(resultItemId, recipe);
            }
        }

        // Exemptions must not be seen
        var exemptions = new HashMap<SFMRegistryObject<Item, ? extends Item>, Object>();
        exemptions.put(SFMItems.EXPERIENCE_SHARD, "xp shards are acquired through falling anvil crafting");
        exemptions.put(SFMItems.FORM, "forms are acquired through falling anvil crafting");
        exemptions.put(SFMItems.BUFFER, "buffer item is WIP");
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.packet_values %}
        exemptions.put(SFMItems.PACKET, "data packets are created by computation, touch events and authorised packet delivery");
{% endif %}
{% endcase %}
        for (var exemption : exemptions.entrySet()) {
{% case minecraft_version %}
{% when "26.1.2" %}
            var old = seenSFMItemIds.put(exemption.getKey().getId().get().identifier(), exemption.getValue());
{% else %}
            var old = seenSFMItemIds.put(exemption.getKey().getId().get().location(), exemption.getValue());
{% endcase %}
            if (old != null) {
                helper.fail("Exempted item "
{% case minecraft_version %}
{% when "26.1.2" %}
                            + exemption.getKey().getId().get().identifier()
{% else %}
                            + exemption.getKey().getId().get().location()
{% endcase %}
                            + " was seen twice: "
                            + old
                            + " and "
                            + exemption.getValue());
            }
        }

        // Accumulator for error messages, determines the test outcome
        StringBuilder failureMessage = new StringBuilder();

        // For each item
        for (Map.Entry<ResourceKey<Item>, Item> itemEntry : SFMWellKnownRegistries.ITEMS.entries()) {
            // If it is an SFM item
{% case minecraft_version %}
{% when "26.1.2" %}
            Identifier itemId = itemEntry.getKey().identifier();
{% else %}
            ResourceLocation itemId = itemEntry.getKey().location();
{% endcase %}
            if (!itemId.getNamespace().equals(SFM.MOD_ID)) {
                continue;
            }

            // Get the recipe for it
            var recipe = seenSFMItemIds.get(itemId);

            // If the recipe isn't present
            if (recipe != null) {
                continue;
            }

            // Fail describing the missing entry
            failureMessage.append("Missing recipe for ").append(itemId).append("\n");
        }

        if (failureMessage.isEmpty()) {
            helper.succeed();
        } else {
            helper.fail(failureMessage.toString());
        }
    }

}
