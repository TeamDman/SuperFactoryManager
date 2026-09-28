package ca.teamdman.sfm.gametest.tests.general;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashMap;
import java.util.List;
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
        List<CraftingRecipe> craftingRecipes = helper
                .getLevel()
                .getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING);

        // We will track the SFM items whose recipes we have observed
        Map<ResourceLocation, Object> seenSFMItemIds = new HashMap<>();

        // Populate the tracker
        // For each recipe
        for (CraftingRecipe recipe : craftingRecipes) {
            // If the resulting item is from SFM
            ResourceLocation resultItemId = SFMWellKnownRegistries.ITEMS.getId(recipe.getResultItem(helper.getLevel().registryAccess()).getItem());
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
        for (var exemption : exemptions.entrySet()) {
            var old = seenSFMItemIds.put(exemption.getKey().getId().get().location(), exemption.getValue());
            if (old != null) {
                helper.fail("Exempted item "
                            + exemption.getKey().getId().get().location()
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
            ResourceLocation itemId = itemEntry.getKey().location();
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
