package ca.teamdman.sfm.client.jei;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollection;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentCollectionKind;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentEntry;
import ca.teamdman.sfm.common.enchantment.SFMEnchantmentKey;
import ca.teamdman.sfm.common.item.FormItem;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.registry.registration.SFMBlockTags;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import mezz.jei.api.recipe.RecipeType;
{% when '26.1.2' %}
{% endcase %}
import mezz.jei.api.recipe.category.IRecipeCategory;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.Holder;
{% endcase %}
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import java.util.Arrays;
{% when '26.1.2' %}
{% endcase %}
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
import java.util.stream.StreamSupport;
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}

public class FallingAnvilJEICategory implements IRecipeCategory<FallingAnvilRecipe> {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public static final RecipeType<FallingAnvilRecipe> RECIPE_TYPE = RecipeType.create(
{% when '26.1.2' %}
    public static final IRecipeType<FallingAnvilRecipe> RECIPE_TYPE = IRecipeType.create(
{% endcase %}
            SFM.MOD_ID,
            "falling_anvil",
            FallingAnvilRecipe.class
    );

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
    @MCVersionDependentBehaviour // Removed in later JEI versions in favour of just getWidth and getHeight
    private final IDrawable background;

{% when '1.21.1', '26.1.2' %}
{% endcase %}
    private final IDrawable icon;

    public FallingAnvilJEICategory(IJeiHelpers jeiHelpers) {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
        background = jeiHelpers.getGuiHelper().createBlankDrawable(getWidth(), getHeight());
{% when '1.21.1', '26.1.2' %}
{% endcase %}
        icon = jeiHelpers.getGuiHelper().createDrawableItemStack(new ItemStack(Blocks.ANVIL));
    }

    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public RecipeType<FallingAnvilRecipe> getRecipeType() {
{% when '26.1.2' %}
    public IRecipeType<FallingAnvilRecipe> getRecipeType() {
{% endcase %}

        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {

        return Localization.FALLING_ANVIL_JEI_CATEGORY_TITLE.getComponent();
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21' %}
    @Override
    public IDrawable getBackground() {

        return background;
    }

{% when '1.21.1', '26.1.2' %}
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
        return StreamSupport
                .stream(SFMWellKnownRegistries.ENCHANTMENTS.values().spliterator(), false)
                .map(SFMEnchantmentKey::new);
{% when '1.21', '1.21.1', '26.1.2' %}
        return SFMWellKnownRegistries.ENCHANTMENTS.holders().map(SFMEnchantmentKey::new);
{% endcase %}
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
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            setRecipeForFallingAnvilFormRecipe(builder, formRecipe, anvil);
{% when '26.1.2' %}
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 0, 0).addItemStacks(anvil);
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).add(formRecipe.PARENT.form());
            List<ItemStack> consumedCatalystBlocks = SFMWellKnownRegistries.BLOCKS.stream()
                    .filter(block -> SFMBlockTags.hasBlockTag(block, SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING))
                    .map(ItemStack::new)
                    .peek(stack ->
                                  SFMComponentUtils.appendLore(
                                          stack,
                                          Localization.FALLING_ANVIL_JEI_CONSUMED.getComponent()
                                  )
                    ).toList();
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).addItemStacks(consumedCatalystBlocks);


            builder
                    .addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
                    .addItemStacks(formRecipe.PARENT.form().getValues().stream()
                            .map(Holder::value)
                            .map(Item::asItem)
                            .map(ItemStack::new)
                            .map(FormItem::createFormFromReference)
                            .toList());
{% endcase %}
        } else if (recipe instanceof FallingAnvilDisenchantRecipe) {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
            setRecipeForFallingAnvilDisenchantRecipe(builder, focuses, anvil);
        } else if (recipe instanceof FallingAnvilExperienceShardRecipe) {
            setRecipeForFallingAnvilExperienceShardRecipe(builder, anvil);
        }
    }
{% when '26.1.2' %}
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static void setRecipeForFallingAnvilExperienceShardRecipe(
            IRecipeLayoutBuilder builder,
            List<ItemStack> anvil
    ) {
{% when '26.1.2' %}
            // If a focus is present for an input or output item, we want to only show those enchantments
            SFMEnchantmentCollection seekingEnchantments = new SFMEnchantmentCollection();
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        builder.addSlot(RecipeIngredientRole.CATALYST, 0, 0).addItemStacks(anvil);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).addIngredients(Ingredient.of(Items.ENCHANTED_BOOK));
{% when '26.1.2' %}
            // Create the list of input items to display
            List<ItemStack> enchantedInputItems = new ArrayList<>();
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        List<ItemStack> crushingCompatibleBlocks = getFallingAnvilCrushingObsidianCatalyst();
        builder
                .addSlot(RecipeIngredientRole.CATALYST, 0, 36)
                .addItemStacks(crushingCompatibleBlocks);
{% when '26.1.2' %}
            // Add any focused input items
            focuses.getFocuses(RecipeIngredientRole.INPUT)
                    .map(FallingAnvilJEICategory::getIngredientItemStack)
                    .filter(Predicate.not(ItemStack::isEmpty))
                    .forEach(inputItemStack -> {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        builder
                .addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
                .addItemStack(new ItemStack(SFMItems.EXPERIENCE_SHARD.get()));
    }
{% when '26.1.2' %}
                        // Get the enchantments on the input item
                        SFMEnchantmentCollection itemEnchantments = SFMEnchantmentCollection.fromItemStack(
                                inputItemStack,
                                SFMEnchantmentCollectionKind.EnchantedLikeATool
                        );
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static void setRecipeForFallingAnvilDisenchantRecipe(
            IRecipeLayoutBuilder builder,
            IFocusGroup focuses,
            List<ItemStack> anvil
    ) {
        // If a focus is present for an input or output item, we want to only show those enchantments
        SFMEnchantmentCollection seekingEnchantments = new SFMEnchantmentCollection();
{% when '26.1.2' %}
                        // Only track the item if it was enchanted, otherwise we will use the default list
                        if (itemEnchantments.isEmpty()) {
                            return;
                        }
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        // Create the list of input items to display
        List<ItemStack> enchantedInputItems = new ArrayList<>();
{% when '26.1.2' %}
                        // Track the input item's enchantments
                        seekingEnchantments.addAll(itemEnchantments);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        // Add any focused input items
        focuses.getFocuses(RecipeIngredientRole.INPUT)
                .map(FallingAnvilJEICategory::getIngredientItemStack)
                .filter(Predicate.not(ItemStack::isEmpty))
                .forEach(inputItemStack -> {
{% when '26.1.2' %}
                        // Track the item
                        enchantedInputItems.add(inputItemStack);
                    });
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    // Get the enchantments on the input item
                    SFMEnchantmentCollection itemEnchantments = SFMEnchantmentCollection.fromItemStack(
                            inputItemStack,
                            SFMEnchantmentCollectionKind.EnchantedLikeATool
                    );
{% when '26.1.2' %}
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
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    // Only track the item if it was enchanted, otherwise we will use the default list
                    if (itemEnchantments.isEmpty()) {
                        return;
{% when '26.1.2' %}
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
{% endcase %}
                    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    // Track the input item's enchantments
                    seekingEnchantments.addAll(itemEnchantments);
{% when '26.1.2' %}
                    // For each enchantment level
                    for (int level : levelsToDisplayIntArray) {
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                    // Track the item
                    enchantedInputItems.add(inputItemStack);
                });
{% when '26.1.2' %}
                        // Create the copy of the tool
                        ItemStack toolStack = checkStack.copy();
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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
{% when '26.1.2' %}
                        // Enchant the tool
                        SFMEnchantmentEntry enchantment = new SFMEnchantmentEntry(enchantmentKey, level);
                        SFMEnchantmentCollection collection = new SFMEnchantmentCollection();
                        collection.add(enchantment);
                        collection.write(toolStack, SFMEnchantmentCollectionKind.EnchantedLikeATool);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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
{% when '26.1.2' %}
                        // Create the enchanted book
                        ItemStack enchantedBook = enchantment.createEnchantedBook();
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        // Show all enchantments if no focused ingredients present
        // It means the user is looking at the entire recipe category
        boolean showingAllEnchantments = seekingEnchantments.isEmpty();
{% when '26.1.2' %}
                        // Track the enchanted book
                        outputEnchantedBookIngredients.add(enchantedBook);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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
{% when '26.1.2' %}
                        // Track the tool
                        inputEnchantedItemIngredients.add(toolStack);
                    }
{% endcase %}
                }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
            }

            // Track the anvil catalyst
            builder
                    .addSlot(RecipeIngredientRole.CRAFTING_STATION, 8, 0)
                    .addItemStacks(anvil);

            // Track the obsidian catalyst
            List<ItemStack> crushingCompatibleBlocks = getFallingAnvilCrushingObsidianCatalyst();
            builder
                    .addSlot(RecipeIngredientRole.CRAFTING_STATION, 8, 36)
                    .addItemStacks(crushingCompatibleBlocks);

            // Track the book ingredient
            builder
                    .addSlot(RecipeIngredientRole.INPUT, 18, 18)
                    .add(Items.BOOK);

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
{% endcase %}
            } else {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
                // Show only the specified level
                int level = seekingEnchantments.getLevel(enchantmentKey);
                if (level <= 0) {
                    continue;
                } else {
                    enchantmentLevelsToDisplay.add(level);
                }
{% when '26.1.2' %}
                SFM.LOGGER.warn("Input and output ingredient counts do not match! This should not happen!");
{% endcase %}
            }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
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
        List<ItemStack> crushingCompatibleBlocks = getFallingAnvilCrushingObsidianCatalyst();
        builder
                .addSlot(RecipeIngredientRole.CATALYST, 8, 36)
                .addItemStacks(crushingCompatibleBlocks);

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
{% when '26.1.2' %}
        } else if (recipe instanceof FallingAnvilExperienceShardRecipe) {
            builder.addSlot(RecipeIngredientRole.CRAFTING_STATION, 0, 0).addItemStacks(anvil);
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).add(Ingredient.of(Items.ENCHANTED_BOOK));
            List<ItemStack> crushingCompatibleBlocks = getFallingAnvilCrushingObsidianCatalyst();
            builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).addItemStacks(crushingCompatibleBlocks);
            builder
                    .addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
                    .add(SFMItems.EXPERIENCE_SHARD.get());
{% endcase %}
        }
    }

    private static List<ItemStack> getFallingAnvilCrushingObsidianCatalyst() {
        return SFMWellKnownRegistries.BLOCKS
                .stream()
                .filter(block -> SFMBlockTags.hasBlockTag(block, SFMBlockTags.ANVIL_DISENCHANTING))
                .map(ItemStack::new)
                .peek(stack -> SFMComponentUtils.appendLore(
                        stack,
                        Localization.FALLING_ANVIL_JEI_NOT_CONSUMED.getComponent()
                ))
                .toList();
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    private static void setRecipeForFallingAnvilFormRecipe(
            IRecipeLayoutBuilder builder,
            FallingAnvilFormRecipe formRecipe,
            List<ItemStack> anvil
    ) {

        builder.addSlot(RecipeIngredientRole.CATALYST, 0, 0).addItemStacks(anvil);
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 18).addIngredients(formRecipe.PARENT.form());
        List<ItemStack> consumedCatalystBlocks = SFMWellKnownRegistries.BLOCKS.stream()
                .filter(block -> SFMBlockTags.hasBlockTag(block, SFMBlockTags.ANVIL_PRINTING_PRESS_FORMING))
                .map(ItemStack::new)
                .peek(stack ->
                              SFMComponentUtils.appendLore(
                                      stack,
                                      Localization.FALLING_ANVIL_JEI_CONSUMED.getComponent()
                              )
                ).toList();
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 36).addItemStacks(consumedCatalystBlocks);
        builder
                .addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
                .addItemStacks(Arrays
                                       .stream(formRecipe.PARENT.form().getItems())
                                       .map(FormItem::createFormFromReference)
                                       .toList());
    }

{% when '26.1.2' %}
{% endcase %}
    @MCVersionDependentBehaviour
    private static ItemStack getIngredientItemStack(IFocus<?> focus) {

        return focus.getTypedValue().getIngredient(VanillaTypes.ITEM_STACK).orElse(ItemStack.EMPTY);
    }

    /// This indirection is necessary because the static fields in {@link FallingAnvilJEICategory} depend on JEI code
    /// which is not present in the classpath during datagen
    public static final class Localization {

        @SFMLocalizationDatagen
        public static final LocalizationEntry FALLING_ANVIL_JEI_CATEGORY_TITLE = new LocalizationEntry(
                "gui.jei.category.sfm.falling_anvil",
                "Falling Anvil"
        );

        @SFMLocalizationDatagen
        public static final LocalizationEntry FALLING_ANVIL_JEI_CONSUMED = new LocalizationEntry(
                "gui.jei.category.sfm.falling_anvil.consumed",
                "Gets consumed"
        );

        @SFMLocalizationDatagen
        public static final LocalizationEntry FALLING_ANVIL_JEI_NOT_CONSUMED = new LocalizationEntry(
                "gui.jei.category.sfm.falling_anvil.not_consumed",
                "Not consumed"
        );

    }

}
