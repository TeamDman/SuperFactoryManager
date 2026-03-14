package ca.teamdman.sfm.gametest.tests.migrated;

import ca.teamdman.sfm.common.registry.SFMResourceTypes;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.BiConsumer;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMWithGameTests.validate_tags
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class ValidateTagsGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        BiConsumer<Item, String> assertTag = (item, findTag) -> {
            boolean hasTag = SFMResourceTypes.ITEM
                    .get()
                    .getTagsForStack(new ItemStack(item))
                    .anyMatch(tag -> tag.toString().equals(findTag) || !findTag.contains(":") && tag
                            .getPath()
                            .equals(findTag));
            assertTrue(hasTag, "Item " + item + " should have tag " + findTag);
        };

        // Assert mineable tags
        assertTag.accept(Items.DIRT, "minecraft:mineable/shovel");
        assertTag.accept(Items.STONE, "minecraft:mineable/pickaxe");
        assertTag.accept(Items.OAK_PLANKS, "minecraft:planks");
        assertTag.accept(Items.OAK_PLANKS, "minecraft:mineable/axe");
        assertTag.accept(Items.IRON_INGOT, "ingots");
        assertTag.accept(Items.GOLD_INGOT, "ingots");
        assertTag.accept(Items.GOLD_NUGGET, "nuggets");
        assertTag.accept(Items.CHEST, "chests");
        helper.succeed();
    }
}
