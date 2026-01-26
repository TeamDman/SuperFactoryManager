package ca.teamdman.sfm.common.handler;

import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.config.SFMServerConfig;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollectionKind;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentEntry;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.SFMBlockTags;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMRecipeTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;

import java.util.ArrayList;
import java.util.List;

public class FallingAnvilHandler {

    @SuppressWarnings("ConstantValue")
    @SFMSubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {

        // Get the level
        Level level = event.getLevel();

        // Only proceed on the server
        if (level.isClientSide()) return;

        // Only proceed if the entity was a falling block
        if (!(event.getEntity() instanceof FallingBlockEntity fbe)) return;

        // Only proceed if the falling block was an anvil
        if (!(fbe.getBlockState().getBlock() instanceof AnvilBlock)) return;

        // Determine where the anvil landed
        var anvilPos = fbe.blockPosition();

        // Do not proceed if the server is shutting down
        // https://github.com/TeamDman/SuperFactoryManager/issues/114
        if (!level.isLoaded(anvilPos)) return;

        // Get the block that was landed on
        Block blockLandedUpon = level.getBlockState(anvilPos.below()).getBlock();


        // Check if the landed-on block can be turned into a printing press form
        boolean tryFormCreation = SFMBlockTags.blockHasTag(blockLandedUpon, SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING);

        // Check if the landed-on block can be used for anvil-disenchanting
        boolean tryCrushing = SFMBlockTags.blockHasTag(blockLandedUpon, SFMBlockTags.ANVIL_DISENCHANTING);

        // Only proceed if the landed-upon block matches our recipes
        if (!tryCrushing && !tryFormCreation) return;

        // Gather the item entities in the landing area
        List<ItemEntity> itemEntities = level.getEntitiesOfClass(ItemEntity.class, new AABB(anvilPos));

        // Remove any empty or dead entities
        itemEntities.removeIf(itemEntity -> !itemEntity.isAlive() || itemEntity.getItem().isEmpty());

        // Do work
        if (tryFormCreation) {

            // Turn items into forms
            handlePrintingPressFormCreation(level, anvilPos, itemEntities);

        } else if (tryCrushing) {

            // Crush enchanted books
            crushEnchantedBooksIntoXpShards(level, anvilPos, itemEntities);

            // Remove any empty or dead entities
            itemEntities.removeIf(itemEntity -> !itemEntity.isAlive() || itemEntity.getItem().isEmpty());

            // Disenchant items
            removeEnchantmentsFromItems(level, anvilPos, itemEntities);
        }
    }

    public static int getShardCountForEnchantments(
            SFMServerConfig.LevelsToShards config,
            SFMEnchantmentCollection enchantments
    ) {

        return switch (config) {
            case JustOne -> 1;
            case EachOne -> enchantments.size();
            case SumLevels -> {
                int sum = 0;
                for (SFMEnchantmentEntry enchantment : enchantments) {
                    sum += enchantment.level();
                }
                yield sum;
            }
            case SumLevelsScaledExponentially -> {
                int sum = 0;
                for (SFMEnchantmentEntry enchantment : enchantments) {
                    int incr = 1 << Math.max(0, enchantment.level() - 1);
                    if (sum + incr > 0) {
                        sum += incr;
                    } else {
                        sum = Integer.MAX_VALUE; // lol
                    }
                }
                yield sum;
            }
        };
    }

    private static void removeEnchantmentsFromItems(
            Level level,
            BlockPos anvilPos,
            List<ItemEntity> items
    ) {

        // Identify book entities
        List<ItemEntity> bookItemEntities = new ArrayList<>();
        List<ItemEntity> nonBookItemEntities = new ArrayList<>();
        for (ItemEntity itemEntity : items) {
            if (itemEntity.getItem().is(Items.BOOK)) {
                bookItemEntities.add(itemEntity);
            } else {
                nonBookItemEntities.add(itemEntity);
            }
        }

        // Compute the total number of books available
        int booksAvailable = 0;
        for (ItemEntity itemEntity : bookItemEntities) {
            int count = itemEntity.getItem().getCount();
            booksAvailable += count;
        }

        // Rip enchantments off of the item entities
        for (ItemEntity itemEntity : nonBookItemEntities) {

            // Get the stack from the item entity
            ItemStack stack = itemEntity.getItem();

            // Get the enchantments off the stack
            SFMEnchantmentCollection enchantments = SFMEnchantmentCollection.fromItemStack(
                    stack,
                    SFMEnchantmentCollectionKind.EnchantedLikeATool
            );

            // Skip if no enchantments
            if (enchantments.isEmpty()) {
                continue;
            }

            // Determine how large the stack is
            int stackSize = stack.getCount();

            // Rip enchantments off the stack until we run out of books
            var iter = enchantments.iterator();

            // Todo: strip partial stacks by decrementing instead of despawning
            while (iter.hasNext() && booksAvailable >= stackSize) {

                // Get the next enchantment to transfer
                var enchantment = iter.next();

                // Create an enchanted book
                ItemStack enchantedBookToSpawn = enchantment.createEnchantedBook();
                enchantedBookToSpawn.setCount(stackSize);

                // Spawn the book in the world
                level.addFreshEntity(new ItemEntity(
                        level,
                        anvilPos.getX(),
                        anvilPos.getY(),
                        anvilPos.getZ(),
                        enchantedBookToSpawn
                ));

                // Remove the enchantment from the collection
                iter.remove();

                // Store the adjusted collection back onto the stack
                enchantments.write(stack, SFMEnchantmentCollectionKind.EnchantedLikeATool);

                // Track book consumption
                booksAvailable -= stackSize;
            }
        }

        // Despawn the books
        for (ItemEntity bookEntity : bookItemEntities) {
            bookEntity.discard();
        }

        // Respawn any remaining books
        while (booksAvailable > 0) {
            int toSpawn = Math.min(booksAvailable, 64);
            level.addFreshEntity(new ItemEntity(
                    level,
                    anvilPos.getX(),
                    anvilPos.getY(),
                    anvilPos.getZ(),
                    new ItemStack(Items.BOOK, toSpawn)
            ));
            booksAvailable -= toSpawn;
        }
    }

    private static void crushEnchantedBooksIntoXpShards(
            Level level,
            BlockPos anvilPos,
            List<ItemEntity> items
    ) {

        // For each item
        for (ItemEntity itemEntity : items) {

            // Get the item stack
            ItemStack stack = itemEntity.getItem();

            // Only proceed if the item is an enchanted book
            if (!stack.is(Items.ENCHANTED_BOOK)) {
                continue;
            }

            // Get the enchantments stored in the book
            SFMEnchantmentCollection enchantments = SFMEnchantmentCollection.fromItemStack(
                    stack,
                    SFMEnchantmentCollectionKind.HoldingLikeABook
            );

            // Determine how many shards the book is worth
            long shardsForEnchantments = getShardCountForEnchantments(
                    SFMConfig.SERVER_CONFIG.levelsToShards.get(),
                    enchantments
            );

            // Determine the total number of shards based on the stack size
            long shardsToSpawn = (long) stack.getCount() * shardsForEnchantments;

            // Kill the book item
            itemEntity.discard();

            // Spawn the shards
            while (shardsToSpawn > 0) {

                // Determine stack size
                int shardStackSize = (int) Math.min(64, shardsToSpawn);

                // Create the stack
                ItemStack shardStack = new ItemStack(SFMItems.EXPERIENCE_SHARD_ITEM.get(), shardStackSize);

                // Spawn the stack
                ItemEntity shardItemEntity = new ItemEntity(
                        level,
                        anvilPos.getX(),
                        anvilPos.getY(),
                        anvilPos.getZ(),
                        shardStack
                );
                level.addFreshEntity(shardItemEntity);

                // Decrement count
                shardsToSpawn -= shardStackSize;
            }
        }
    }

    private static void handlePrintingPressFormCreation(
            Level level,
            BlockPos anvilPos,
            List<ItemEntity> itemEntities
    ) {

        // Gather the printing press recipes
        List<PrintingPressRecipe> recipes = level
                .getRecipeManager()
                .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get());

        // Mark the block for consumption only if work is done
        boolean consumeBlock = false;

        // For each item entity
        for (ItemEntity itemEntity : itemEntities) {

            // For each recipe
            for (PrintingPressRecipe recipe : recipes) {

                // Only continue if the item matches the recipe
                if (!recipe.form().test(itemEntity.getItem())) {
                    continue;
                }

                // Create the form stack
                ItemStack formStack = FormItem.createFormFromReference(itemEntity.getItem());

                // Spawn the new item
                level.addFreshEntity(new ItemEntity(
                        level,
                        anvilPos.getX(),
                        anvilPos.getY(),
                        anvilPos.getZ(),
                        formStack
                ));

                // Consume the item
                // SAFETY: this MUST happen AFTER spawning the new item because we are in an EntityLeaveLevelEvent handler
                // [TeamDman - SFM Bug Hunt](https://www.youtube.com/watch?v=8GtBrKhSWq0)
                itemEntity.discard();


                // Mark the block for consumption
                consumeBlock = true;

                // Move to the next item entity
                break;
            }
        }

        if (consumeBlock) {
            // Consume the block below
            level.setBlockAndUpdate(anvilPos.below(), Blocks.AIR.defaultBlockState());
        }

    }

}
