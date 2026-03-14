package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollectionKind;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentEntry;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentKey;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import ca.teamdman.sfm.common.util.SFMComponentUtils;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class FallingAnvilJEICategory implements IRecipeCategory<FallingAnvilRecipe> {

    public static final RecipeType<FallingAnvilRecipe> RECIPE_TYPE = RecipeType.create(
            SFM.MOD_ID,
            "falling_anvil",
            FallingAnvilRecipe.class
    );

    @MCVersionDependentBehaviour // Removed in later JEI versions in favour of just getWidth and getHeight
    private final IDrawable background;

    private final IDrawable icon;

    public FallingAnvilJEICategory(IJeiHelpers jeiHelpers) {

        background = jeiHelpers.getGuiHelper().createBlankDrawable(getWidth(), getHeight());
        icon = jeiHelpers.getGuiHelper().createDrawableItemStack(new ItemStack(Blocks.ANVIL));
    }

    @Override
    public RecipeType<FallingAnvilRecipe> getRecipeType() {

        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {

        return LocalizationKeys.FALLING_ANVIL_JEI_CATEGORY_TITLE.getComponent();
    }

    @Override
    public IDrawable getBackground() {

        return background;
    }

    @Override
    public int getWidth() {

        return 80;
    }

    @Override
    public int getHeight() {

        return 54;
    }

    @Override
    public IDrawable getIcon() {

        return icon;
    }

    public static Stream<SFMEnchantmentKey> streamEnchantments() {

        return StreamSupport
                .stream(SFMWellKnownRegistries.ENCHANTMENTS.values().spliterator(), false)
                .map(SFMEnchantmentKey::new);
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            FallingAnvilRecipe recipe,
            IFocusGroup focuses
    ) {

        var anvil = List.of(
                new ItemStack(Items.ANVIL),
                new ItemStack(Items.CHIPPED_ANVIL),
                new ItemStack(Items.DAMAGED_ANVIL)
        );
        if (recipe instanceof FallingAnvilFormRecipe formRecipe) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 0, 0).addItemStacks(anvil);
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).addIngredients(formRecipe.PARENT.form());
            ItemStack ironBlock = new ItemStack(Blocks.IRON_BLOCK);
            SFMComponentUtils.appendLore(ironBlock, LocalizationKeys.FALLING_ANVIL_JEI_CONSUMED.getComponent());
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).addItemStack(ironBlock);
            builder
                    .addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
                    .addItemStacks(Arrays
                                           .stream(formRecipe.PARENT.form().getItems())
                                           .map(FormItem::createFormFromReference)
                                           .toList());
        } else if (recipe instanceof FallingAnvilDisenchantRecipe) {

            // If a focus is present for an input or output item, we want to only show those enchantments
            SFMEnchantmentCollection seekingEnchantments = new SFMEnchantmentCollection();

            // Create the list of input items to display
            List<ItemStack> enchantedInputItems = new ArrayList<>();

            // Add any focused input items
            focuses.getFocuses(RecipeIngredientRole.INPUT)
                    .map(FallingAnvilJEICategory::getIngredientItemStack)
                    .filter(Predicate.not(ItemStack::isEmpty))
                    .forEach(inputItemStack -> {

                        // Get the enchantments on the input item
                        SFMEnchantmentCollection itemEnchantments = SFMEnchantmentCollection.fromItemStack(
                                inputItemStack,
                                SFMEnchantmentCollectionKind.EnchantedLikeATool
                        );

                        // Only track the item if it was enchanted, otherwise we will use the default list
                        if (itemEnchantments.isEmpty()) {
                            return;
                        }

                        // Track the input item's enchantments
                        seekingEnchantments.addAll(itemEnchantments);

                        // Track the item
                        enchantedInputItems.add(inputItemStack);
                    });

            // Populate using default items if no focused items present
            if (enchantedInputItems.isEmpty()) {
                List<Item> defaultTools = List.of(
                        Items.DIAMOND_HELMET,
                        Items.DIAMOND_CHESTPLATE,
                        Items.DIAMOND_LEGGINGS,
                        Items.DIAMOND_BOOTS,
                        Items.DIAMOND_PICKAXE,
                        Items.DIAMOND_SHOVEL,
                        Items.DIAMOND_AXE,
                        Items.DIAMOND_HOE,
                        Items.DIAMOND_SWORD,
                        Items.GOLDEN_HELMET,
                        Items.GOLDEN_CHESTPLATE,
                        Items.GOLDEN_LEGGINGS,
                        Items.GOLDEN_BOOTS,
                        Items.GOLDEN_PICKAXE,
                        Items.GOLDEN_SHOVEL,
                        Items.GOLDEN_AXE,
                        Items.GOLDEN_HOE,
                        Items.GOLDEN_SWORD,
                        Items.IRON_HELMET,
                        Items.IRON_CHESTPLATE,
                        Items.IRON_LEGGINGS,
                        Items.IRON_BOOTS,
                        Items.IRON_PICKAXE,
                        Items.IRON_SHOVEL,
                        Items.IRON_AXE,
                        Items.IRON_HOE,
                        Items.IRON_SWORD,
                        Items.LEATHER_HELMET,
                        Items.LEATHER_CHESTPLATE,
                        Items.LEATHER_LEGGINGS,
                        Items.LEATHER_BOOTS,
                        Items.CHAINMAIL_HELMET,
                        Items.CHAINMAIL_CHESTPLATE,
                        Items.CHAINMAIL_LEGGINGS,
                        Items.CHAINMAIL_BOOTS,
                        Items.WOODEN_PICKAXE,
                        Items.WOODEN_SHOVEL,
                        Items.WOODEN_AXE,
                        Items.WOODEN_HOE,
                        Items.WOODEN_SWORD,
                        Items.BOW,
                        Items.CROSSBOW,
//                        Items.MACE,
                        Items.TRIDENT,
                        Items.FISHING_ROD,
                        Items.STICK
                );
                for (Item defaultTool : defaultTools) {
                    enchantedInputItems.add(new ItemStack(defaultTool));
                }
            }

            // Add enchanted book enchantments from focused output items
            focuses
                    .getFocuses(RecipeIngredientRole.OUTPUT)
                    .map(FallingAnvilJEICategory::getIngredientItemStack)
                    .filter(Predicate.not(ItemStack::isEmpty))
                    .map(stack -> SFMEnchantmentCollection.fromItemStack(
                            stack,
                            SFMEnchantmentCollectionKind.HoldingLikeABook
                    ))
                    .flatMap(Collection::stream)
                    .forEach(seekingEnchantments::add);

            // Show all enchantments if no focused ingredients present
            // It means the user is looking at the entire recipe category
            boolean showingAllEnchantments = seekingEnchantments.isEmpty();

            // Prepare ingredient collections
            var inputEnchantedItemIngredients = new ArrayList<ItemStack>();
            var outputEnchantedBookIngredients = new ArrayList<ItemStack>();

            // For each enchantment from the registry
            Iterable<SFMEnchantmentKey> holders = streamEnchantments()::iterator;
            for (SFMEnchantmentKey enchantmentKey : holders) {

                // Determine the max level of the enchantment
                int maxLevel = enchantmentKey.getMaxLevel();

                // Determine which levels to show
                IntArraySet enchantmentLevelsToDisplay = new IntArraySet();
                if (showingAllEnchantments) {
                    // Show each level
                    for (int level = 1; level <= maxLevel; level++) {
                        enchantmentLevelsToDisplay.add(level);
                    }
                } else {
                    // Show only the specified level
                    int level = seekingEnchantments.getLevel(enchantmentKey);
                    if (level <= 0) {
                        continue;
                    } else {
                        enchantmentLevelsToDisplay.add(level);
                    }
                }

                // If not showing any levels for this enchantment, skip it
                if (enchantmentLevelsToDisplay.isEmpty()) {
                    continue;
                }

                // Convert to an int array to reduce boxing allocations when iterating via enhanced-for
                int[] levelsToDisplayIntArray = enchantmentLevelsToDisplay.toIntArray();


                // Create (enchanted item, book) pairs
                for (ItemStack checkStack : enchantedInputItems) {

                    // Only track if the tool supports the enchantment or if the tool is a stick as a catch-all
                    if (!enchantmentKey.canEnchant(checkStack) && checkStack.getItem() != Items.STICK) {
                        continue;
                    }

                    // For each enchantment level
                    for (int level : levelsToDisplayIntArray) {

                        // Create the copy of the tool
                        ItemStack toolStack = checkStack.copy();

                        // Enchant the tool
                        SFMEnchantmentEntry enchantment = new SFMEnchantmentEntry(enchantmentKey, level);
                        SFMEnchantmentCollection collection = new SFMEnchantmentCollection();
                        collection.add(enchantment);
                        collection.write(toolStack, SFMEnchantmentCollectionKind.EnchantedLikeATool);

                        // Create the enchanted book
                        ItemStack enchantedBook = enchantment.createEnchantedBook();

                        // Track the enchanted book
                        outputEnchantedBookIngredients.add(enchantedBook);

                        // Track the tool
                        inputEnchantedItemIngredients.add(toolStack);
                    }
                }
            }

            // Track the anvil catalyst
            builder
                    .addSlot(RecipeIngredientRole.CATALYST, 8, 0)
                    .addItemStacks(anvil);

            // Track the obsidian catalyst
            ItemStack obsidian = new ItemStack(Blocks.OBSIDIAN);
            SFMComponentUtils.appendLore(obsidian, LocalizationKeys.FALLING_ANVIL_JEI_NOT_CONSUMED.getComponent());
            builder
                    .addSlot(RecipeIngredientRole.CATALYST, 8, 36)
                    .addItemStack(obsidian);

            // Track the book ingredient
            builder
                    .addSlot(RecipeIngredientRole.INPUT, 18, 18)
                    .addItemStack(new ItemStack(Items.BOOK));

            // Track the enchanted item input ingredient
            IRecipeSlotBuilder inputEnchantedItemSlot = builder
                    .addSlot(RecipeIngredientRole.INPUT, 0, 18)
                    .addItemStacks(inputEnchantedItemIngredients);

            // Track the enchanted book output ingredient
            IRecipeSlotBuilder outputEnchantedBookSlot = builder
                    .addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
                    .addItemStacks(outputEnchantedBookIngredients);

            if (inputEnchantedItemIngredients.size() == outputEnchantedBookIngredients.size()) {
                builder.createFocusLink(inputEnchantedItemSlot, outputEnchantedBookSlot);
            } else {
                SFM.LOGGER.warn("Input and output ingredient counts do not match! This should not happen!");
            }

        } else if (recipe instanceof FallingAnvilExperienceShardRecipe) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 0, 0).addItemStacks(anvil);
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).addIngredients(Ingredient.of(Items.ENCHANTED_BOOK));
            ItemStack obsidian = new ItemStack(Blocks.OBSIDIAN);
            SFMComponentUtils.appendLore(obsidian, LocalizationKeys.FALLING_ANVIL_JEI_NOT_CONSUMED.getComponent());
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).addItemStack(obsidian);
            builder
                    .addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
                    .addItemStack(new ItemStack(SFMItems.EXPERIENCE_SHARD_ITEM.get()));
        }
    }

    @MCVersionDependentBehaviour
    private static ItemStack getIngredientItemStack(IFocus<?> focus) {

        return focus.getTypedValue().getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
    }

}
