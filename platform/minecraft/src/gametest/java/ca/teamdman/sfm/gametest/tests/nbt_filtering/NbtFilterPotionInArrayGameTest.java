package ca.teamdman.sfm.gametest.tests.nbt_filtering;

import ca.teamdman.sfm.gametest.LeftRightManagerTest;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Arrays;

/**
 * Tests that NBT IN array filtering can exclude basic potions.
 * Move only non-basic potions (not water, mundane, awkward, or thick).
 */
@SFMGameTest
public class NbtFilterPotionInArrayGameTest extends SFMGameTestDefinition {
    @Override
    public String template() {
        return "3x2x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        var test = new LeftRightManagerTest(helper);

        // Create basic potions (should NOT be moved)
        ItemStack waterPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        ItemStack mundanePotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.MUNDANE);
        ItemStack awkwardPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.AWKWARD);
        ItemStack thickPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.THICK);

        // Create non-basic potions (should be moved)
        ItemStack healingPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.HEALING);
        ItemStack strengthPotion = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.STRENGTH);

        test.setProgram("""
            EVERY 20 TICKS DO
                -- Only move potions that are NOT basic (water, mundane, awkward, thick)
                INPUT WITHOUT NBT Potion IN [
                    "minecraft:water",
                    "minecraft:mundane",
                    "minecraft:awkward",
                    "minecraft:thick"
                ] FROM left
                OUTPUT TO right
            END
        """);

        // Put all potions in the left chest
        test.preContents("left", Arrays.asList(
                waterPotion,
                mundanePotion,
                awkwardPotion,
                thickPotion,
                healingPotion,
                strengthPotion
        ));

        // Only healing and strength potions should move to the right
        // Basic potions stay in the left
        test.postContents("left", Arrays.asList(
                waterPotion.copy(),
                mundanePotion.copy(),
                awkwardPotion.copy(),
                thickPotion.copy(),
                ItemStack.EMPTY,
                ItemStack.EMPTY
        ));
        test.postContents("right", Arrays.asList(
                healingPotion.copy(),
                strengthPotion.copy()
        ));

        test.run();
    }
}
