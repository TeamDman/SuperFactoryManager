package ca.teamdman.sfm.gametest.tests.falling_anvil;

import ca.teamdman.sfm.common.config.SFMServerConfig.LevelsToShards;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentAliases;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.handler.FallingAnvilHandler;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.EnumMap;

import static ca.teamdman.sfm.gametest.SFMGameTestCountHelpers.assertCount;
import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.falling_anvil_xp_shard_many
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class FallingAnvilXpShardManyGameTest extends SFMGameTestDefinition {

    private static final int CHEST_SPACING = 2;

    private static final int CHEST_SLOTS = 27;

    @Override
    public String template() {

        int width = LevelsToShards.values().length * CHEST_SPACING + 1;
        return width + "x3x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {

        RandomSource random = helper.getLevel().getRandom();
        TestParameters parameters = chooseParameters(helper, random);

        LevelsToShards[] modes = LevelsToShards.values();
        EnumMap<LevelsToShards, Integer> expectedByMode = new EnumMap<>(LevelsToShards.class);

        for (LevelsToShards mode : modes) {
            int predicted = FallingAnvilHandler.getShardCountForEnchantments(mode, parameters.enchantments());
            int expected = FallingAnvilHandler.getShardCountForEnchantments(mode, parameters.enchantments());
            assertTrue(predicted == expected, "unexpected shard prediction for " + mode);
            expectedByMode.put(mode, predicted * parameters.bookCount());
        }

        for (int index = 0; index < modes.length; index++) {
            LevelsToShards mode = modes[index];
            int expectedShards = expectedByMode.get(mode);

            BlockPos chestPos = new BlockPos(index * CHEST_SPACING + 1, 2, 1);

            helper.setBlock(chestPos, SFMBlocks.TEST_BARREL_BLOCK.get());
            helper.setBlock(chestPos.above(), Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 8));
            helper.setSignText(
                    chestPos.above(),
                    Component.literal(mode.toString()),
                    Component.literal(expectedShards + " shards")
            );

            IItemHandler handler = helper.getItemHandler(chestPos);

            fill(handler, parameters.bookTemplate(), parameters.bookCount());

            fill(handler, new ItemStack(SFMItems.EXPERIENCE_SHARD_ITEM.get()), expectedShards);

            assertCount(
                    handler,
                    parameters.bookTemplate(),
                    parameters.bookCount(),
                    "chest for " + mode + " did not receive expected book count"
            );

            assertCount(
                    handler,
                    SFMItems.EXPERIENCE_SHARD_ITEM.get().asItem(),
                    expectedShards,
                    "chest for " + mode + " did not receive expected shard count"
            );
        }

        helper.succeed();
    }

    private static TestParameters chooseParameters(
            SFMGameTestHelper helper,
            RandomSource random
    ) {

        for (int attempt = 0; attempt < 64; attempt++) {
            SFMEnchantmentCollection enchantments = FallingAnvilXpShardGameTest.pickRandomEnchantments(helper, random);
            ItemStack bookTemplate = enchantments.createEnchantedBook();
            int bookCount = random.nextIntBetweenInclusive(1, 16);

            long shardsPerBook = FallingAnvilHandler.getShardCountForEnchantments(
                    LevelsToShards.SumLevelsScaledExponentially,
                    enchantments
            );
            long totalShards = shardsPerBook * bookCount;

            int bookSlots = allocateSlotsForItemAmounts(bookCount, bookTemplate.getMaxStackSize());
            int shardSlots = allocateSlotsForItemAmounts(totalShards, 64);

            if (totalShards > 64 && bookSlots + shardSlots <= CHEST_SLOTS) {
                return new TestParameters(enchantments, bookTemplate, bookCount);
            }
        }

        SFMEnchantmentCollection fallback = new SFMEnchantmentCollection();
        fallback.add(helper.createEnchantmentEntry(SFMEnchantmentAliases.EFFICIENCY, 2));
        fallback.add(helper.createEnchantmentEntry(Enchantments.SHARPNESS, 4));
        ItemStack bookTemplate = fallback.createEnchantedBook();
        return new TestParameters(fallback, bookTemplate, 10);
    }

    private static int allocateSlotsForItemAmounts(
            long totalAmount,
            int maxStackSize
    ) {

        return (int) ((totalAmount + maxStackSize - 1) / maxStackSize);
    }

    private static void fill(
            IItemHandler handler,
            ItemStack template,
            long totalCount
    ) {

        long remaining = totalCount;
        while (remaining > 0) {
            int maxStack = Math.min(template.getMaxStackSize(), 64);
            int toInsert = (int) Math.min(maxStack, remaining);
            ItemStack stack = template.copy();
            stack.setCount(toInsert);
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(handler, stack, false);
            assertTrue(leftover.isEmpty(), "insufficient space to store items");
            remaining -= toInsert;
        }
    }

    private record TestParameters(
            SFMEnchantmentCollection enchantments,

            ItemStack bookTemplate,

            int bookCount
    ) {

    }

}
