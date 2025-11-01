package ca.teamdman.sfm.common.handler;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.SFMBlockTags;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMRecipeTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FallingAnvilHandler {
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof FallingBlockEntity fbe) {
            if (fbe.getBlockState().getBlock() instanceof AnvilBlock) {
                var landPosition = fbe.blockPosition();
                Level level = event.getLevel();
                if (!level.isLoaded(landPosition.below())) {
                    // avoid problems when the server is shutting down
                    // https://github.com/TeamDman/SuperFactoryManager/issues/114
                    return;
                }
                Block block = level.getBlockState(landPosition.below()).getBlock();
                if (SFMBlockTags.blockHasTag(block, SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING)) { // create a form
                    List<PrintingPressRecipe> recipes = level
                            .getRecipeManager()
                            .getAllRecipesFor(SFMRecipeTypes.PRINTING_PRESS.get());
                    List<ItemEntity> items = new ArrayList<>();
                    for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(landPosition))) {
                        if (e.isAlive() && !e.getItem().isEmpty()) {
                            items.add(e);
                        }
                    }
                    boolean didForm = false;

                    for (ItemEntity item : items) {
                        for (PrintingPressRecipe recipe : recipes) {
                            // check if the item can be turned into a form
                            if (recipe.FORM.test(item.getItem())) {
                                didForm = true;
                                item.setItem(FormItem.createFormFromReference(item.getItem()));
                                break;
                            }
                        }
                    }
                    if (didForm) {
                        level.setBlockAndUpdate(landPosition.below(), Blocks.AIR.defaultBlockState());
                    }
                } else if (SFMBlockTags.blockHasTag(block, SFMBlockTags.ANVIL_DISENCHANTING)) { // crush and disenchant items
                    List<ItemEntity> items = new ArrayList<>();
                    for (ItemEntity itemEntity : level.getEntitiesOfClass(ItemEntity.class, new AABB(landPosition))) {
                        if (itemEntity.isAlive() && !itemEntity.getItem().isEmpty()) {
                            items.add(itemEntity);
                        }
                    }

                    { // crush enchanted books into xp shards
                        for (ItemEntity e : items) {
                            if (!e.getItem().is(Items.ENCHANTED_BOOK)) {
                                continue;
                            }

                            var item = e.getItem();
                            var enchantments = EnchantedBookItem.getEnchantments(item);

                            long shardsForEnchantments = switch (SFMConfig.SERVER_CONFIG.levelsToShards.get()) {
                                case JustOne -> 1;
                                case EachOne -> enchantments.size();
                                case SumLevels -> {
                                    int sum = 0;
                                    for (int i = 0; i < enchantments.size(); i++) {
                                        var ench = enchantments.getCompound(i);
                                        sum += ench.getInt("lvl");
                                    }
                                    yield sum;
                                }
                                case SumLevelsScaledExponentially -> {
                                    int sum = 0;
                                    for (int i = 0; i < enchantments.size(); i++) {
                                        var ench = enchantments.getCompound(i);
                                        int incr = 1 << Math.max(0, ench.getInt("lvl") - 1);
                                        if (sum + incr > 0) {
                                            sum += incr;
                                        } else {
                                            sum = Integer.MAX_VALUE; // lol
                                        }
                                    }
                                    yield sum;
                                }
                            };
                            long count = (long) item.getCount() * shardsForEnchantments;

                            e.setItem(new ItemStack(
                                    SFMItems.EXPERIENCE_SHARD_ITEM.get(),
                                    (int) Math.min(64, count)
                            ));

                            count -= 64;
                            while (count > 0) {
                                e.spawnAtLocation(new ItemStack(
                                        SFMItems.EXPERIENCE_SHARD_ITEM.get(),
                                        (int) Math.min(64, count)
                                ));
                                count -= 64;
                            }
                        }
                    }
                    { // remove enchantments from items
                        List<ItemEntity> bookEntities = new ArrayList<>();
                        for (ItemEntity item : items) {
                            if (item.getItem().is(Items.BOOK)) {
                                bookEntities.add(item);
                            }
                        }
                        int booksAvailable = 0;
                        for (ItemEntity itemEntity : bookEntities) {
                            int count = itemEntity.getItem().getCount();
                            booksAvailable += count;
                        }
                        List<ItemEntity> enchanted = new ArrayList<>(items.size());
                        for (ItemEntity e : items) {
                            if (!e.getItem().getEnchantmentTags().isEmpty()) {
                                enchanted.add(e);
                            }
                        }


                        for (ItemEntity enchItemEntity : enchanted) {
                            ItemStack enchStack = enchItemEntity.getItem();
                            int enchStackSize = enchStack.getCount();
                            Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(enchStack);
                            var enchIter = enchantments.entrySet().iterator();
                            while (enchIter.hasNext()) {
                                var entry = enchIter.next();
                                if (booksAvailable < enchStackSize) break;

                                // Create an enchanted book with the enchantment
                                ItemStack toSpawn = new ItemStack(Items.ENCHANTED_BOOK, enchStackSize);
                                EnchantedBookItem.addEnchantment(
                                        toSpawn,
                                        new EnchantmentInstance(entry.getKey(), entry.getValue())
                                );
                                level.addFreshEntity(new ItemEntity(
                                        level,
                                        landPosition.getX(),
                                        landPosition.getY(),
                                        landPosition.getZ(),
                                        toSpawn
                                ));

                                // Remove the enchantment from the item
                                enchIter.remove();
                                EnchantmentHelper.setEnchantments(enchantments, enchStack);
                                booksAvailable -= enchStackSize;
                                if (enchantments.isEmpty()) {
                                    break;
                                }
                            }
                        }

                        for (ItemEntity bookEntity : bookEntities) {
                            bookEntity.kill();
                        }
                        while (booksAvailable > 0) {
                            int toSpawn = Math.min(booksAvailable, 64);
                            level.addFreshEntity(new ItemEntity(
                                    level,
                                    landPosition.getX(),
                                    landPosition.getY(),
                                    landPosition.getZ(),
                                    new ItemStack(Items.BOOK, toSpawn)
                            ));
                            booksAvailable -= toSpawn;
                        }
                    }
                }
            }
        }
    }
}
