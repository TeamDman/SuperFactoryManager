package ca.teamdman.sfm.gametest.tests.falling_anvil;

import ca.teamdman.sfm.common.enchantment.SFMEnchantmentAliases;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollectionKind;
import ca.teamdman.sfm.common.util.SFMItemUtils;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Migrated from SFMCorrectnessGameTests.falling_anvil_disenchant
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class FallingAnvilDisenchantGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {

        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        // Place blocks
        placeObsidian(helper);
        placeAnvil(helper); // the anvil will naturally fall

        // Spawn items
        Vec3 itemSpawnPos = helper.absoluteVec(new Vec3(1.5, 3.5, 1.5));
        int bookCount = helper.getLevel().getRandom().nextIntBetweenInclusive(2, 256);

        spawnBookItemEntities(helper, itemSpawnPos, bookCount);
        spawnEnchantedItem(helper, itemSpawnPos);

        // Schedule assertion
        helper.runAfterDelay(20, () -> assertExpectedItemEntitiesFound(helper, itemSpawnPos, bookCount));
    }

    private static void placeAnvil(SFMGameTestHelper helper) {

        helper.setBlock(new BlockPos(1, 4, 1), Blocks.ANVIL);
    }

    private static void spawnEnchantedItem(
            SFMGameTestHelper helper,
            Vec3 itemSpawnPos
    ) {

        var axe = new ItemStack(Items.GOLDEN_AXE);

        SFMEnchantmentCollection enchantments = new SFMEnchantmentCollection();
        enchantments.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 3));
        enchantments.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 2));
        enchantments.write(axe, SFMEnchantmentCollectionKind.EnchantedLikeATool);

        helper.getLevel().addFreshEntity(new ItemEntity(
                helper.getLevel(),
                itemSpawnPos.x, itemSpawnPos.y, itemSpawnPos.z,
                axe,
                0, 0, 0
        ));
    }

    private static void spawnBookItemEntities(
            SFMGameTestHelper helper,
            Vec3 itemSpawnPos,
            int bookCount
    ) {
        int remaining = bookCount;
        while (remaining > 0) {
            int toSpawn = Math.min(remaining, 64);
            helper
                    .getLevel()
                    .addFreshEntity(new ItemEntity(
                            helper.getLevel(),
                            itemSpawnPos.x, itemSpawnPos.y, itemSpawnPos.z,
                            new ItemStack(Items.BOOK, toSpawn),
                            0, 0, 0
                    ));
            remaining -= toSpawn;
        }

        BlockPos signPos = new BlockPos(1, 2, 0);
        helper.setBlock(signPos, Blocks.ACACIA_WALL_SIGN);
        helper.setSignText(
                signPos,
                Component.literal("Spawned " + bookCount),
                Component.literal("books")
        );
    }

    private static void placeObsidian(SFMGameTestHelper helper) {

        helper.setBlock(new BlockPos(1, 2, 1), Blocks.OBSIDIAN);
    }

    private static void assertExpectedItemEntitiesFound(
            SFMGameTestHelper helper,
            Vec3 itemSpawnPos,
            int bookCount
    ) {

        List<ItemStack> foundItemStacks = new ArrayList<>();
        AABB seekArea = new AABB(new BlockPos((int) itemSpawnPos.x(), (int) itemSpawnPos.y(), (int) itemSpawnPos.z()));
        helper
                .getLevel()
                .getEntitiesOfClass(ItemEntity.class, seekArea.inflate(3))
                .forEach(e -> foundItemStacks.add(e.getItem()));

        boolean foundDisenchantedAxe = false;
        boolean foundEfficiencyBook = false;
        boolean foundSharpnessBook = false;
        int foundBookCount = 0;
        for (ItemStack stack : foundItemStacks) {
            if (SFMItemUtils.isSameItemSameTags(
                    stack,
                    new ItemStack(Items.GOLDEN_AXE)
            )) {
                foundDisenchantedAxe = true;
            } else if (SFMItemUtils.isSameItemSameTags(
                    stack,
                    helper.createEnchantmentEntry(
                            SFMEnchantmentAliases.EFFICIENCY,
                            3
                    ).createEnchantedBook()
            )) {
                foundEfficiencyBook = true;
            } else if (SFMItemUtils.isSameItemSameTags(
                    stack,
                    helper.createEnchantmentEntry(
                            Enchantments.SHARPNESS,
                            2
                    ).createEnchantedBook()
            )) {
                foundSharpnessBook = true;
            } else if (stack.is(Items.BOOK)) {
                foundBookCount += stack.getCount();
            }
        }
        if (!foundDisenchantedAxe) {
            helper.fail("Disenchanted axe not found");
        }
        if (!foundEfficiencyBook) {
            helper.fail("Efficiency book not found");
        }
        if (!foundSharpnessBook) {
            helper.fail("Sharpness book not found");
        }
        int expectedBookCount = bookCount - 2;
        if (foundBookCount != expectedBookCount) {
            helper.fail("Wrong number of books found: " + foundBookCount + " instead of " + expectedBookCount);
        }
        helper.succeed();
    }

}
