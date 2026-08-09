package ca.teamdman.sfm.gametest.tests.nbt_filtering;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Arrays;

/**
 * Tests that NBT filtering can move only enchanted items.
 */
@SFMGameTest
public class NbtFilterEnchantedItemsGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);

        // Create an enchanted sword
        ItemStack enchantedSword = new ItemStack(Items.DIAMOND_SWORD);
        enchantedSword.enchant(Enchantments.SHARPNESS, 5);

        // Create a plain sword (no enchantments)
        ItemStack plainSword = new ItemStack(Items.DIAMOND_SWORD);

        test.setProgram("""
            EVERY 20 TICKS DO
                -- Only move items with enchantments (Enchantments array is non-empty)
                INPUT WITH NBT Enchantments[0] FROM left
                OUTPUT TO right
            END
        """);

        // Put both swords in the left chest
        test.preContents("left", Arrays.asList(
                enchantedSword,
                plainSword
        ));

        // Only the enchanted sword should move to the right
        test.postContents("left", Arrays.asList(
                ItemStack.EMPTY,
                plainSword.copy()
        ));
        test.postContents("right", Arrays.asList(
                enchantedSword.copy()
        ));

        test.run();
    }
}
