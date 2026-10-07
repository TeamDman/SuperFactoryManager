package ca.teamdman.sfm.datagen;

{% case minecraft_version %}
{% when "26.1.2" %}
import ca.teamdman.sfm.common.recipe.DiskResetRecipe;
import ca.teamdman.sfm.common.recipe.LabelGunResetRecipe;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
{% else %}
import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
import ca.teamdman.sfm.common.recipe.DiskResetRecipe;
import ca.teamdman.sfm.common.recipe.LabelGunResetRecipe;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
{% else %}
import ca.teamdman.sfm.common.recipe.PrintingPressFinishedRecipe;
{% endcase %}
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticRecipeDataGen;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
{% else %}
{% case minecraft_version %}
{% when "1.20.2" %}
import net.minecraft.data.recipes.RecipeOutput;
{% else %}
import net.minecraft.data.recipes.FinishedRecipe;
{% endcase %}
{% endcase %}
{% endcase %}
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.neoforged.neoforge.common.Tags;
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% else %}
{% case minecraft_version %}
{% when "1.20.2" %}
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% else %}
import net.minecraftforge.common.Tags;
import net.minecraftforge.data.event.GatherDataEvent;

import java.util.function.Consumer;
{% endcase %}
{% endcase %}
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.concurrent.CompletableFuture;

{% else %}
{% endcase %}
public class SFMRecipesDatagen extends MCVersionAgnosticRecipeDataGen {
{% case minecraft_version %}
{% when "26.1.2" %}
    public SFMRecipesDatagen(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
{% else %}
    public SFMRecipesDatagen(GatherDataEvent event) {

        super(event, SFM.MOD_ID);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    protected void populate(RecipeOutput writer) {
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
    protected void populate(RecipeOutput writer) {
{% else %}
{% case minecraft_version %}
{% when "1.20.2" %}
    protected void populate(RecipeOutput writer) {
{% else %}
    protected void populate(Consumer<FinishedRecipe> writer) {
{% endcase %}
{% endcase %}
{% endcase %}

        beginShaped(SFMBlocks.CABLE.get(), 16)
                .define('D', Tags.Items.DYES_BLACK)
                .define('G', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .define('C', Tags.Items.CHESTS)
                .define('B', Items.IRON_BARS)
                .pattern("DGD")
                .pattern("BCB")
                .pattern("DGD")
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
{% else %}
                .unlockedBy("has_iron_ingot", RecipeProvider.has(Items.IRON_INGOT))
                .unlockedBy("has_chest", RecipeProvider.has(Tags.Items.CHESTS))
{% endcase %}
                .save(writer);

        beginShapeless(SFMBlocks.FANCY_CABLE.get(), 1)
                .requires(SFMBlocks.CABLE.get(), 1)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
{% else %}
                .unlockedBy("has_iron_ingot", RecipeProvider.has(Items.IRON_INGOT))
                .unlockedBy("has_chest", RecipeProvider.has(Tags.Items.CHESTS))
{% endcase %}
                .save(writer);

        beginShapeless(SFMBlocks.CABLE.get(), 1)
                .requires(SFMBlocks.FANCY_CABLE.get(), 1)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
                .save(writer, "fancy_to_cable");
{% else %}
                .unlockedBy("has_iron_ingot", RecipeProvider.has(Items.IRON_INGOT))
                .unlockedBy("has_chest", RecipeProvider.has(Tags.Items.CHESTS))
                .save(writer, SFMResourceLocation.fromSFMPath("fancy_to_cable"));
{% endcase %}

        beginShaped(SFMBlocks.TOUGH_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
{% else %}
                .unlockedBy("has_obsidian", RecipeProvider.has(Items.OBSIDIAN))
                .unlockedBy("has_cable", RecipeProvider.has(SFMItems.CABLE.get()))
{% endcase %}
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer);

        beginShaped(SFMBlocks.TOUGH_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
{% else %}
                .unlockedBy("has_obsidian", RecipeProvider.has(Items.OBSIDIAN))
                .unlockedBy("has_cable", RecipeProvider.has(SFMItems.CABLE.get()))
{% endcase %}
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tough_cable_horizontal");

        beginShaped(SFMBlocks.TOUGH_FANCY_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.FANCY_CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
{% else %}
                .unlockedBy("has_obsidian", RecipeProvider.has(Items.OBSIDIAN))
                .unlockedBy("has_fancy_cable", RecipeProvider.has(SFMItems.FANCY_CABLE.get()))
{% endcase %}
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer, "tough_cable_vertical");

        beginShaped(SFMBlocks.TOUGH_FANCY_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.FANCY_CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
{% else %}
                .unlockedBy("has_obsidian", RecipeProvider.has(Items.OBSIDIAN))
                .unlockedBy("has_fancy_cable", RecipeProvider.has(SFMItems.FANCY_CABLE.get()))
{% endcase %}
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tough_fancy_cable_horizontal");

        beginShapeless(SFMBlocks.CABLE.get(), 1)
                .requires(SFMBlocks.TOUGH_CABLE.get(), 1)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_tough_cable", hasItem(SFMItems.TOUGH_CABLE.get()))
                .save(writer, "tough_to_cable");
{% else %}
                .unlockedBy("has_tough_cable", RecipeProvider.has(SFMItems.TOUGH_CABLE.get()))
                .save(writer, SFMResourceLocation.fromSFMPath("tough_to_cable"));
{% endcase %}

        beginShapeless(SFMBlocks.FANCY_CABLE.get(), 1)
                .requires(SFMBlocks.TOUGH_FANCY_CABLE.get(), 1)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_tough_fancy_cable", hasItem(SFMItems.TOUGH_FANCY_CABLE.get()))
                .save(writer, "tough_fancy_to_fancy");
{% else %}
                .unlockedBy("has_tough_fancy_cable", RecipeProvider.has(SFMItems.TOUGH_FANCY_CABLE.get()))
                .save(writer, SFMResourceLocation.fromSFMPath("tough_fancy_to_fancy"));
{% endcase %}

        beginShaped(SFMBlocks.TUNNELLED_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
{% else %}
                .unlockedBy("has_fence", RecipeProvider.has(Tags.Items.FENCES))
                .unlockedBy("has_cable", RecipeProvider.has(SFMItems.CABLE.get()))
{% endcase %}
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer, "tunnelled_cable_vertical");

        beginShaped(SFMBlocks.TUNNELLED_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
{% else %}
                .unlockedBy("has_fence", RecipeProvider.has(Tags.Items.FENCES))
                .unlockedBy("has_cable", RecipeProvider.has(SFMItems.CABLE.get()))
{% endcase %}
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tunnelled_cable_horizontal");

        beginShaped(SFMBlocks.TUNNELLED_FANCY_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.FANCY_CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
{% else %}
                .unlockedBy("has_fence", RecipeProvider.has(Tags.Items.FENCES))
                .unlockedBy("has_fancy_cable", RecipeProvider.has(SFMItems.FANCY_CABLE.get()))
{% endcase %}
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer, "tunnelled_fancy_cable_vertical");

        beginShaped(SFMBlocks.TUNNELLED_FANCY_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.FANCY_CABLE.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
{% else %}
                .unlockedBy("has_fence", RecipeProvider.has(Tags.Items.FENCES))
                .unlockedBy("has_fancy_cable", RecipeProvider.has(SFMItems.FANCY_CABLE.get()))
{% endcase %}
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tunnelled_fancy_cable_horizontal");

        beginShaped(SFMBlocks.MANAGER.get(), 1)
                .define('A', Tags.Items.CHESTS)
                .define('B', SFMBlocks.CABLE.get())
                .define('C', Items.REPEATER)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
{% else %}
                .unlockedBy("has_iron_ingot", RecipeProvider.has(Items.IRON_INGOT))
                .unlockedBy("has_chest", RecipeProvider.has(Tags.Items.CHESTS))
{% endcase %}
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .save(writer);

{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
{% if features.client_manager %}
        beginShaped(SFMBlocks.CLIENT_MANAGER.get(), 1)
                .define('A', Tags.Items.CHESTS)
                .define('B', SFMBlocks.CABLE.get())
                .define('C', Items.COMPARATOR)
                .unlockedBy("has_comparator", RecipeProvider.has(Items.COMPARATOR))
                .unlockedBy("has_cable", RecipeProvider.has(SFMItems.CABLE.get()))
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .save(writer);

{% endif %}
{% if features.touch_display %}
        beginShaped(SFMBlocks.TOUCH_DISPLAY.get(), 1)
                .define('I', Items.IRON_BARS)
                .define('G', Tags.Items.GLASS)
                .define('C', SFMBlocks.CABLE.get())
                .define('R', Items.REDSTONE)
                .unlockedBy("has_cable", RecipeProvider.has(SFMItems.CABLE.get()))
                .pattern("IGI")
                .pattern("GCG")
                .pattern("IRI")
                .save(writer);

{% endif %}
{% endcase %}
        beginShaped(SFMBlocks.TUNNELLED_MANAGER.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.MANAGER.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_manager", hasItem(SFMItems.MANAGER.get()))
{% else %}
                .unlockedBy("has_manager", RecipeProvider.has(SFMItems.MANAGER.get()))
{% endcase %}
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer);

        beginShaped(SFMBlocks.TUNNELLED_MANAGER.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.MANAGER.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_manager", hasItem(SFMItems.MANAGER.get()))
{% else %}
                .unlockedBy("has_manager", RecipeProvider.has(SFMItems.MANAGER.get()))
{% endcase %}
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tunnelled_manager_horizontal");

        beginShapeless(SFMBlocks.MANAGER.get(), 1)
                .requires(SFMItems.TUNNELLED_MANAGER.get())
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_manager", hasItem(SFMItems.TUNNELLED_MANAGER.get()))
{% else %}
                .unlockedBy("has_manager", RecipeProvider.has(SFMItems.TUNNELLED_MANAGER.get()))
{% endcase %}
                .save(writer, "uncraft_tunnelled_manager");

        beginShaped(SFMItems.LABEL_GUN.get(), 1)
                .define('S', Tags.Items.RODS_WOODEN)
                .define('B', Tags.Items.DYES_BLACK)
                .define('L', Tags.Items.DYES_BLUE)
                .define('C', ItemTags.SIGNS)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_ink", hasItem(Tags.Items.DYES_BLACK))
{% else %}
                .unlockedBy("has_ink", RecipeProvider.has(Tags.Items.DYES_BLACK))
{% endcase %}
                .pattern(" LC")
                .pattern(" SB")
                .pattern("S  ")
                .save(writer, "tunnelled_manager_vertical");


        beginShaped(SFMItems.NETWORK_TOOL.get(), 1)
                .define('S', Items.IRON_INGOT)
                .define('L', Items.REDSTONE_LAMP)
                .define('P', Items.HEAVY_WEIGHTED_PRESSURE_PLATE)
                .define('C', ItemTags.SIGNS)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_redstone_lamp", hasItem(Items.REDSTONE_LAMP))
{% else %}
                .unlockedBy("has_redstone_lamp", RecipeProvider.has(Items.REDSTONE_LAMP))
{% endcase %}
                .pattern(" LC")
                .pattern(" SP")
                .pattern("S  ")
                .save(writer);


        beginShaped(SFMItems.DISK.get(), 1)
                .define('R', Blocks.REDSTONE_BLOCK)
                .define('e', Items.REDSTONE)
                .define('d', Items.REPEATER)
                .define('a', Tags.Items.DYES_RED)
                .define('b', Tags.Items.DYES_GREEN)
                .define('c', Tags.Items.DYES_BLUE)
                .define('p', Items.PAPER)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_redstone", hasItem(Items.REDSTONE))
{% else %}
                .unlockedBy("has_redstone", RecipeProvider.has(Items.REDSTONE))
{% endcase %}
                .pattern("pbp")
                .pattern("aRc")
                .pattern("ede")
                .save(writer);

        beginShaped(SFMItems.WATER_TANK.get(), 1)
                .define('b', Items.WATER_BUCKET)
                .define('g', Items.IRON_BARS)
                .define('p', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_water", hasItem(Items.WATER_BUCKET))
{% else %}
                .unlockedBy("has_water", RecipeProvider.has(Items.WATER_BUCKET))
{% endcase %}
                .pattern("gbg")
                .pattern("gpg")
                .pattern("gbg")
                .save(writer);

        beginShapeless(SFMItems.EXPERIENCE_GOOP.get(), 1)
                .requires(SFMItems.EXPERIENCE_SHARD.get(), 9)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_experience_shard", hasItem(SFMItems.EXPERIENCE_SHARD.get()))
{% else %}
                .unlockedBy("has_experience_shard", RecipeProvider.has(SFMItems.EXPERIENCE_SHARD.get()))
{% endcase %}
                .save(writer);


        beginShaped(SFMItems.PRINTING_PRESS.get(), 1)
                .define('a', Items.ANVIL)
                .define('i', Tags.Items.DYES_BLACK)
                .define('p', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .define('s', Items.STONE)
                .define('x', Items.PISTON)
                .define('g', Items.IRON_BARS)
{% case minecraft_version %}
{% when "26.1.2" %}
                .unlockedBy("has_iron", hasItem(Items.IRON_INGOT))
{% else %}
                .unlockedBy("has_iron", RecipeProvider.has(Items.IRON_INGOT))
{% endcase %}
                .pattern("pip")
                .pattern("sas")
                .pattern("gxg")
                .save(writer);

        addPrintingPressRecipe(
                writer,
                SFMResourceLocation.fromSFMPath("written_book_copy"),
                Ingredient.of(Items.WRITTEN_BOOK),
{% case minecraft_version %}
{% when "26.1.2" %}
                ingredientFromTag(Tags.Items.DYES_BLACK),
{% else %}
                Ingredient.of(Tags.Items.DYES_BLACK),
{% endcase %}
                Ingredient.of(Items.BOOK)
        );

        addPrintingPressRecipe(
                writer,
                SFMResourceLocation.fromSFMPath("enchanted_book_copy"),
                Ingredient.of(Items.ENCHANTED_BOOK),
                Ingredient.of(SFMItems.EXPERIENCE_GOOP.get()),
                Ingredient.of(Items.BOOK)
        );

        addPrintingPressRecipe(
                writer,
                SFMResourceLocation.fromSFMPath("map_copy"),
                Ingredient.of(Items.FILLED_MAP),
{% case minecraft_version %}
{% when "26.1.2" %}
                ingredientFromTag(Tags.Items.DYES_BLACK),
{% else %}
                Ingredient.of(Tags.Items.DYES_BLACK),
{% endcase %}
                Ingredient.of(Items.MAP)
        );

        addPrintingPressRecipe(
                writer,
                SFMResourceLocation.fromSFMPath("program_copy"),
                Ingredient.of(SFMItems.DISK.get()),
{% case minecraft_version %}
{% when "26.1.2" %}
                ingredientFromTag(Tags.Items.DYES_BLACK),
{% else %}
                Ingredient.of(Tags.Items.DYES_BLACK),
{% endcase %}
                Ingredient.of(SFMItems.DISK.get())
        );

        //noinspection DataFlowIssue
        SpecialRecipeBuilder
{% case minecraft_version %}
{% when "26.1.2" %}
                .special(DiskResetRecipe::new)
                .save(
                        writer,
                        BuiltInRegistries.RECIPE_SERIALIZER.getKey(SFMRecipeSerializers.DISK_RESET.get()).getPath()
                );
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
                .special(DiskResetRecipe::new)
                .save(
                        writer,
                        BuiltInRegistries.RECIPE_SERIALIZER.getKey(SFMRecipeSerializers.DISK_RESET.get()).getPath()
                );
{% else %}
                .special(SFMRecipeSerializers.DISK_RESET.get())
                .save(writer, SFMRecipeSerializers.DISK_RESET.getPath());

{% endcase %}
{% endcase %}
        //noinspection DataFlowIssue
        SpecialRecipeBuilder
{% case minecraft_version %}
{% when "26.1.2" %}
                .special(LabelGunResetRecipe::new)
                .save(
                        writer,
                        BuiltInRegistries.RECIPE_SERIALIZER.getKey(SFMRecipeSerializers.LABEL_GUN_RESET.get()).getPath()
                );
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
                .special(LabelGunResetRecipe::new)
                .save(
                        writer,
                        BuiltInRegistries.RECIPE_SERIALIZER.getKey(SFMRecipeSerializers.LABEL_GUN_RESET.get()).getPath()
                );
{% else %}
                .special(SFMRecipeSerializers.LABEL_GUN_RESET.get())
                .save(writer, SFMRecipeSerializers.LABEL_GUN_RESET.getPath());
{% endcase %}
{% endcase %}
    }

    private void addPrintingPressRecipe(
{% case minecraft_version %}
{% when "26.1.2" %}
            RecipeOutput consumer,
            Identifier id,
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
            RecipeOutput consumer,
{% else %}
{% case minecraft_version %}
{% when "1.20.2" %}
            RecipeOutput consumer,
{% else %}
            Consumer<FinishedRecipe> consumer,
{% endcase %}
{% endcase %}
            ResourceLocation id,
{% endcase %}
            Ingredient form,
            Ingredient ink,
            Ingredient paper
    ) {

{% case minecraft_version %}
{% when "26.1.2" %}
        consumer.accept(ResourceKey.create(Registries.RECIPE, id), new PrintingPressRecipe(form, ink, paper), null);
{% else %}
{% case minecraft_version %}
{% when "1.20.3", "1.20.4", "1.21", "1.21.0", "1.21.1" %}
        consumer.accept(id, new PrintingPressRecipe(form, ink, paper), null);
{% else %}
        consumer.accept(new PrintingPressFinishedRecipe(id, form, ink, paper));
{% endcase %}
{% endcase %}
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    public static class Runner extends MCVersionAgnosticRecipeDataGen.Runner {
        public Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
            super(packOutput, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new SFMRecipesDatagen(registries, output);
        }

        @Override
        public String getName() {
            return "SFM Recipes";
        }
    }
{% else %}
{% endcase %}
}
