package ca.teamdman.sfm.gametest.tests.enchantment;

import ca.teamdman.sfm.common.enchantment.SFMEnchantmentAliases;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/// We want to make sure that the {@link SFMEnchantmentCollection} class is properly writing and reading enchantments.
/// It should clobber rather than append.
@SuppressWarnings({
        "DataFlowIssue",
        "RedundantSuppression",
        "OptionalGetWithoutIsPresent",
        "MismatchedQueryAndUpdateOfCollection"
})
@SFMGameTest
public class EnchantmentCollectionEqualityAndHashCodeGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {

        return "1x1x1";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        SFMEnchantmentCollection enchantments1 = new SFMEnchantmentCollection();
        enchantments1.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 3));
        enchantments1.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 2));

        SFMEnchantmentCollection enchantments2 = new SFMEnchantmentCollection();
        enchantments2.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 3));
        enchantments2.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 2));
        if (!enchantments1.equals(enchantments2)) {
            helper.fail("enchantments1 and enchantments2 must be equal");
        }
        if (enchantments1.hashCode() != enchantments2.hashCode()) {
            helper.fail("enchantments1 and enchantments2 must have the same hash code");
        }

        SFMEnchantmentCollection enchantments3 = new SFMEnchantmentCollection();
        enchantments3.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 5));
        enchantments3.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 3));
        enchantments3.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 2));
        enchantments3.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 3));
        enchantments3.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 2));
        if (enchantments1.equals(enchantments3)) {
            helper.fail("enchantments1 and enchantments3 must not be equal");
        }
        if (enchantments1.hashCode() == enchantments3.hashCode()) {
            helper.fail("enchantments1 and enchantments3 must not have the same hash code");
        }

        if (!enchantments1.canonicalize().equals(enchantments3.canonicalize())) {
            helper.fail("enchantments1 and enchantments3 must be equal after canonicalization");
        }
        if (enchantments1.canonicalize().hashCode() != enchantments3.canonicalize().hashCode()) {
            helper.fail("enchantments1 and enchantments3 must have the same hash code after canonicalization");
        }

        helper.succeed();
    }

}
