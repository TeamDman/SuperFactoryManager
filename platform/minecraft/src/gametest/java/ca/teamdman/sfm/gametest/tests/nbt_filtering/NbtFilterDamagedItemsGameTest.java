package ca.teamdman.sfm.gametest.tests.nbt_filtering;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Collections;

/**
 * Tests that NBT filtering can move only damaged items (Damage > 0).
 */
@SFMGameTest
public class NbtFilterDamagedItemsGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);

        // Create a damaged sword (has Damage NBT)
        ItemStack damagedSword = new ItemStack(Items.DIAMOND_SWORD);
        damagedSword.setDamageValue(50);

        // Create an undamaged sword (Damage = 0, but still has the tag)
        ItemStack undamagedSword = new ItemStack(Items.DIAMOND_SWORD);

        test.setProgram("""
            EVERY 20 TICKS DO
                -- Only move items with Damage > 0
                INPUT WITH NBT Damage > 0 FROM left
                OUTPUT TO right
            END
        """);

        // Put both swords in the left chest
        test.preContents("left", Arrays.asList(
                damagedSword,
                undamagedSword
        ));

        // Only the damaged sword should move to the right
        // The undamaged sword stays in the left
        test.postContents("left", Arrays.asList(
                ItemStack.EMPTY,
                undamagedSword.copy()
        ));
        test.postContents("right", Arrays.asList(
                damagedSword.copy()
        ));

        test.run();
    }
}
