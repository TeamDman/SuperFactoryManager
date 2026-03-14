package ca.teamdman.sfm.gametest.tests.enchantment;

import ca.teamdman.sfm.common.enchantment.SFMEnchantmentAliases;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollectionKind;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/// We want to make sure that the {@link SFMEnchantmentCollection} class is properly writing and reading enchantments.
/// It should clobber rather than append.
@SuppressWarnings({
        "DataFlowIssue",
        "RedundantSuppression",
        "OptionalGetWithoutIsPresent",
        "MismatchedQueryAndUpdateOfCollection"
})
@SFMGameTest
public class EnchantmentCollectionWriteToToolGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {

        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        // Create two unique enchantment collections
        SFMEnchantmentCollection enchantments1 = new SFMEnchantmentCollection();
        enchantments1.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 3));
        enchantments1.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 2));

        SFMEnchantmentCollection enchantments2 = new SFMEnchantmentCollection();
        enchantments2.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 3));
        enchantments2.add(helper.createEnchantmentEntry(Enchantments.UNBREAKING, 2));

        assertTrue(!enchantments1.equals(enchantments2), "Enchantment collections must not be equal");

        // Create an item
        ItemStack axeStack1 = new ItemStack(Items.GOLDEN_AXE);
        ItemStack axeStack2 = axeStack1.copy();


        // Writes should overwrite, not append
        enchantments1.write(axeStack1, SFMEnchantmentCollectionKind.EnchantedLikeATool);
        enchantments2.write(axeStack1, SFMEnchantmentCollectionKind.EnchantedLikeATool);

        assertTrue(
                SFMEnchantmentCollection
                        .fromItemStack(axeStack1, SFMEnchantmentCollectionKind.EnchantedLikeATool)
                        .canonicalize()
                        .equals(enchantments2.canonicalize()),
                "Enchantment collections must be equal after writing to an item stack (EnchantedLikeATool) and reading from it (EnchantedLikeATool)"
        );

        enchantments2.write(axeStack2, SFMEnchantmentCollectionKind.EnchantedLikeATool);
        assertTrue(
                SFMItemUtils.isSameItemSameTags(axeStack2, axeStack1),
                "Item stacks must be equal after writing to an item stack (EnchantedLikeATool) and reading from it (EnchantedLikeATool)"
        );


        helper.succeed();
    }

}
