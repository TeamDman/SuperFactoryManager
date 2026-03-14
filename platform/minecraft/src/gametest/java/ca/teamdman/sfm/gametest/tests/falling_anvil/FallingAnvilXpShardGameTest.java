package ca.teamdman.sfm.gametest.tests.falling_anvil;

import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMServerConfig.LevelsToShards;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentAliases;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentEntry;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentKey;
import ca.teamdman.sfm.common.handler.FallingAnvilHandler;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.gametest.SFMGameTest;
import ca.teamdman.sfm.gametest.SFMGameTestDefinition;
import ca.teamdman.sfm.gametest.SFMGameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ca.teamdman.sfm.gametest.SFMGameTestMethodHelpers.assertTrue;

/**
 * Migrated from SFMCorrectnessGameTests.falling_anvil_xp_shard
 */
@SuppressWarnings({
        "RedundantSuppression",
        "DataFlowIssue",
        "OptionalGetWithoutIsPresent",
        "DuplicatedCode",
        "ArraysAsListWithZeroOrOneArgument"
})
@SFMGameTest
public class FallingAnvilXpShardGameTest extends SFMGameTestDefinition {

    @Override
    public String template() {
        return "3x4x3";
    }

    @Override
    public void run(SFMGameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.OBSIDIAN);

        RandomSource random = helper.getLevel().getRandom();
        SFMEnchantmentCollection enchantments = pickRandomEnchantments(helper, random);
        int bookCount = random.nextIntBetweenInclusive(1, 8);

        LevelsToShards config = SFMConfig.SERVER_CONFIG.levelsToShards.get();
        long shardsPerBook = FallingAnvilHandler.getShardCountForEnchantments(config, enchantments);
        long expectedShardTotal = shardsPerBook * bookCount;

        BlockPos dropPos = helper.absolutePos(new BlockPos(1, 4, 1));
        helper
                .getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(dropPos).inflate(3))
                .forEach(Entity::discard);

        Vec3 spawnPos = helper.absoluteVec(new Vec3(1.5, 3.5, 1.5));
        ItemStack bookTemplate = enchantments.createEnchantedBook();
        for (int i = 0; i < bookCount; i++) {
            helper.getLevel().addFreshEntity(new ItemEntity(
                    helper.getLevel(),
                    spawnPos.x, spawnPos.y, spawnPos.z,
                    bookTemplate.copy(),
                    0, 0, 0
            ));
        }

        helper.setBlock(new BlockPos(1, 3, 1), Blocks.AIR);
        helper.setBlock(new BlockPos(1, 4, 1), Blocks.ANVIL);

        helper.runAfterDelay(20, () -> {
            List<ItemEntity> found = helper
                    .getLevel()
                    .getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(helper.absolutePos(new BlockPos(1, 4, 1))).inflate(3)
                    );
            assertTrue(!found.isEmpty(), "expected shards to be produced");

            List<String> unexpectedItems = found
                    .stream()
                    .filter(item -> !item.getItem().is(SFMItems.EXPERIENCE_SHARD_ITEM.get()))
                    .map(item -> item.getItem().getDescriptionId())
                    .collect(Collectors.toList());
            assertTrue(
                    unexpectedItems.isEmpty(),
                    "conversion produced non-shard items: " + String.join(", ", unexpectedItems)
            );

            long actualShardTotal = found.stream().mapToLong(item -> item.getItem().getCount()).sum();
            assertTrue(
                    actualShardTotal == expectedShardTotal,
                    "expected " + expectedShardTotal + " shards but found " + actualShardTotal
            );

            helper.succeed();
        });
    }

    public static SFMEnchantmentCollection pickRandomEnchantments(
            SFMGameTestHelper helper,
            RandomSource random
    ) {

        List<SFMEnchantmentKey> ENCHANTMENT_POOL = List.of(
                helper.createEnchantmentKey(SFMEnchantmentAliases.EFFICIENCY),
                helper.createEnchantmentKey(Enchantments.SHARPNESS),
                helper.createEnchantmentKey(SFMEnchantmentAliases.FORTUNE),
                helper.createEnchantmentKey(Enchantments.UNBREAKING),
                helper.createEnchantmentKey(Enchantments.MENDING)
        );

        List<SFMEnchantmentKey> pool = new ArrayList<>(ENCHANTMENT_POOL);
        for (int i = pool.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            SFMEnchantmentKey tmp = pool.get(i);
            pool.set(i, pool.get(swapIndex));
            pool.set(swapIndex, tmp);
        }

        int selectionSize = random.nextIntBetweenInclusive(1, pool.size());
        SFMEnchantmentCollection enchantments = new SFMEnchantmentCollection();
        for (int i = 0; i < selectionSize; i++) {
            SFMEnchantmentKey enchantment = pool.get(i);
            int maxLevel = Math.max(1, enchantment.getMaxLevel());
            int level = random.nextIntBetweenInclusive(1, maxLevel);
            SFMEnchantmentEntry entry = new SFMEnchantmentEntry(enchantment, level);
            enchantments.add(entry);
        }
        return enchantments;
    }
}
