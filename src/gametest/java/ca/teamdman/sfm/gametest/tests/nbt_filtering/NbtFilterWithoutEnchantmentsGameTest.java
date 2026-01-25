package ca.teamdman.sfm.gametest.tests.nbt_filtering;

import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollectionKind;
import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.Arrays;

/**
 * Tests that WITHOUT NBT filtering can move only non-enchanted items.
 */
@SFMGameTest
public class NbtFilterWithoutEnchantmentsGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);

        // Create an enchanted sword
        ItemStack enchantedSword = new ItemStack(Items.DIAMOND_SWORD);
        SFMEnchantmentCollection enchantments = new SFMEnchantmentCollection();
        enchantments.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 5));
        enchantments.write(enchantedSword, SFMEnchantmentCollectionKind.EnchantedLikeATool);

        // Create a plain sword (no enchantments)
        ItemStack plainSword = new ItemStack(Items.DIAMOND_SWORD);

        test.setProgram("""
            EVERY 20 TICKS DO
                -- Only move items WITHOUT enchantments
                INPUT WITHOUT NBT enchantments[0] FROM left
                OUTPUT TO right
            END
        """);

        // Put both swords in the left chest
        test.preContents("left", Arrays.asList(
                enchantedSword,
                plainSword
        ));

        // Only the plain sword should move to the right
        test.postContents("left", Arrays.asList(
                enchantedSword.copy(),
                ItemStack.EMPTY
        ));
        test.postContents("right", Arrays.asList(
                plainSword.copy()
        ));

        test.run();
    }
}
