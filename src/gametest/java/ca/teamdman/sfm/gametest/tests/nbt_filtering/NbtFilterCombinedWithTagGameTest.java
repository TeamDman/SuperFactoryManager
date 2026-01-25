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
 * Tests combining NBT filtering with TAG filtering.
 * Move only damaged swords (not other damaged items).
 */
@SFMGameTest
public class NbtFilterCombinedWithTagGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);

        // Create a damaged sword
        ItemStack damagedSword = new ItemStack(Items.DIAMOND_SWORD);
        damagedSword.setDamageValue(50);

        // Create a damaged pickaxe
        ItemStack damagedPickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        damagedPickaxe.setDamageValue(50);

        test.setProgram("""
            EVERY 20 TICKS DO
                -- Only move damaged items that are also swords (have the sword tag)
                INPUT WITH NBT Damage > 0 AND TAG forge:tools/swords FROM left
                OUTPUT TO right
            END
        """);

        // Put both items in the left chest
        test.preContents("left", Arrays.asList(
                damagedSword,
                damagedPickaxe
        ));

        // Only the damaged sword should move (matches both NBT and TAG filters)
        // The damaged pickaxe stays because it doesn't have the sword tag
        test.postContents("left", Arrays.asList(
                ItemStack.EMPTY,
                damagedPickaxe.copy()
        ));
        test.postContents("right", Arrays.asList(
                damagedSword.copy()
        ));

        test.run();
    }
}
