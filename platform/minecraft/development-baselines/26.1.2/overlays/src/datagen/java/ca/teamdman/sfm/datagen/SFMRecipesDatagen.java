package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.common.recipe.DiskResetRecipe;
import ca.teamdman.sfm.common.recipe.LabelGunResetRecipe;
import ca.teamdman.sfm.common.recipe.PrintingPressRecipe;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.registry.registration.SFMRecipeSerializers;
import ca.teamdman.sfm.common.util.SFMResourceLocation;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticRecipeDataGen;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

import java.util.concurrent.CompletableFuture;

public class SFMRecipesDatagen extends MCVersionAgnosticRecipeDataGen {
    public SFMRecipesDatagen(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void populate(RecipeOutput writer) {

        beginShaped(SFMBlocks.CABLE.get(), 16)
                .define('D', Tags.Items.DYES_BLACK)
                .define('G', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .define('C', Tags.Items.CHESTS)
                .define('B', Items.IRON_BARS)
                .pattern("DGD")
                .pattern("BCB")
                .pattern("DGD")
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
                .save(writer);

        beginShapeless(SFMBlocks.FANCY_CABLE.get(), 1)
                .requires(SFMBlocks.CABLE.get(), 1)
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
                .save(writer);

        beginShapeless(SFMBlocks.CABLE.get(), 1)
                .requires(SFMBlocks.FANCY_CABLE.get(), 1)
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
                .save(writer, "fancy_to_cable");

        beginShaped(SFMBlocks.TOUGH_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.CABLE.get())
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer);

        beginShaped(SFMBlocks.TOUGH_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.CABLE.get())
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tough_cable_horizontal");

        beginShaped(SFMBlocks.TOUGH_FANCY_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.FANCY_CABLE.get())
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer, "tough_cable_vertical");

        beginShaped(SFMBlocks.TOUGH_FANCY_CABLE.get(), 1)
                .define('A', Blocks.OBSIDIAN)
                .define('B', SFMBlocks.FANCY_CABLE.get())
                .unlockedBy("has_obsidian", hasItem(Items.OBSIDIAN))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tough_fancy_cable_horizontal");

        beginShapeless(SFMBlocks.CABLE.get(), 1)
                .requires(SFMBlocks.TOUGH_CABLE.get(), 1)
                .unlockedBy("has_tough_cable", hasItem(SFMItems.TOUGH_CABLE.get()))
                .save(writer, "tough_to_cable");

        beginShapeless(SFMBlocks.FANCY_CABLE.get(), 1)
                .requires(SFMBlocks.TOUGH_FANCY_CABLE.get(), 1)
                .unlockedBy("has_tough_fancy_cable", hasItem(SFMItems.TOUGH_FANCY_CABLE.get()))
                .save(writer, "tough_fancy_to_fancy");

        beginShaped(SFMBlocks.TUNNELLED_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.CABLE.get())
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer, "tunnelled_cable_vertical");

        beginShaped(SFMBlocks.TUNNELLED_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.CABLE.get())
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_cable", hasItem(SFMItems.CABLE.get()))
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tunnelled_cable_horizontal");

        beginShaped(SFMBlocks.TUNNELLED_FANCY_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.FANCY_CABLE.get())
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer, "tunnelled_fancy_cable_vertical");

        beginShaped(SFMBlocks.TUNNELLED_FANCY_CABLE.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.FANCY_CABLE.get())
                .unlockedBy("has_fence", hasItem(Tags.Items.FENCES))
                .unlockedBy("has_fancy_cable", hasItem(SFMItems.FANCY_CABLE.get()))
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tunnelled_fancy_cable_horizontal");

        beginShaped(SFMBlocks.MANAGER.get(), 1)
                .define('A', Tags.Items.CHESTS)
                .define('B', SFMBlocks.CABLE.get())
                .define('C', Items.REPEATER)
                .unlockedBy("has_iron_ingot", hasItem(Items.IRON_INGOT))
                .unlockedBy("has_chest", hasItem(Tags.Items.CHESTS))
                .pattern("ABA")
                .pattern("BCB")
                .pattern("ABA")
                .save(writer);

        beginShaped(SFMBlocks.TUNNELLED_MANAGER.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.MANAGER.get())
                .unlockedBy("has_manager", hasItem(SFMItems.MANAGER.get()))
                .pattern("A A")
                .pattern("ABA")
                .pattern("A A")
                .save(writer);

        beginShaped(SFMBlocks.TUNNELLED_MANAGER.get(), 1)
                .define('A', Tags.Items.FENCES)
                .define('B', SFMBlocks.MANAGER.get())
                .unlockedBy("has_manager", hasItem(SFMItems.MANAGER.get()))
                .pattern("AAA")
                .pattern(" B ")
                .pattern("AAA")
                .save(writer, "tunnelled_manager_horizontal");

        beginShapeless(SFMBlocks.MANAGER.get(), 1)
                .requires(SFMItems.TUNNELLED_MANAGER.get())
                .unlockedBy("has_manager", hasItem(SFMItems.TUNNELLED_MANAGER.get()))
                .save(writer, "uncraft_tunnelled_manager");

        beginShaped(SFMItems.LABEL_GUN.get(), 1)
                .define('S', Tags.Items.RODS_WOODEN)
                .define('B', Tags.Items.DYES_BLACK)
                .define('L', Tags.Items.DYES_BLUE)
                .define('C', ItemTags.SIGNS)
                .unlockedBy("has_ink", hasItem(Tags.Items.DYES_BLACK))
                .pattern(" LC")
                .pattern(" SB")
                .pattern("S  ")
                .save(writer, "tunnelled_manager_vertical");


        beginShaped(SFMItems.NETWORK_TOOL.get(), 1)
                .define('S', Items.IRON_INGOT)
                .define('L', Items.REDSTONE_LAMP)
                .define('P', Items.HEAVY_WEIGHTED_PRESSURE_PLATE)
                .define('C', ItemTags.SIGNS)
                .unlockedBy("has_redstone_lamp", hasItem(Items.REDSTONE_LAMP))
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
                .unlockedBy("has_redstone", hasItem(Items.REDSTONE))
                .pattern("pbp")
                .pattern("aRc")
                .pattern("ede")
                .save(writer);

        beginShaped(SFMItems.WATER_TANK.get(), 1)
                .define('b', Items.WATER_BUCKET)
                .define('g', Items.IRON_BARS)
                .define('p', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .unlockedBy("has_water", hasItem(Items.WATER_BUCKET))
                .pattern("gbg")
                .pattern("gpg")
                .pattern("gbg")
                .save(writer);

        beginShapeless(SFMItems.EXPERIENCE_GOOP.get(), 1)
                .requires(SFMItems.EXPERIENCE_SHARD.get(), 9)
                .unlockedBy("has_experience_shard", hasItem(SFMItems.EXPERIENCE_SHARD.get()))
                .save(writer);


        beginShaped(SFMItems.PRINTING_PRESS.get(), 1)
                .define('a', Items.ANVIL)
                .define('i', Tags.Items.DYES_BLACK)
                .define('p', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .define('s', Items.STONE)
                .define('x', Items.PISTON)
                .define('g', Items.IRON_BARS)
                .unlockedBy("has_iron", hasItem(Items.IRON_INGOT))
                .pattern("pip")
                .pattern("sas")
                .pattern("gxg")
                .save(writer);

        addPrintingPressRecipe(
                writer,
                SFMResourceLocation.fromSFMPath("written_book_copy"),
                Ingredient.of(Items.WRITTEN_BOOK),
                ingredientFromTag(Tags.Items.DYES_BLACK),
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
                ingredientFromTag(Tags.Items.DYES_BLACK),
                Ingredient.of(Items.MAP)
        );

        addPrintingPressRecipe(
                writer,
                SFMResourceLocation.fromSFMPath("program_copy"),
                Ingredient.of(SFMItems.DISK.get()),
                ingredientFromTag(Tags.Items.DYES_BLACK),
                Ingredient.of(SFMItems.DISK.get())
        );

        //noinspection DataFlowIssue
        SpecialRecipeBuilder
                .special(DiskResetRecipe::new)
                .save(
                        writer,
                        BuiltInRegistries.RECIPE_SERIALIZER.getKey(SFMRecipeSerializers.DISK_RESET.get()).getPath()
                );
        //noinspection DataFlowIssue
        SpecialRecipeBuilder
                .special(LabelGunResetRecipe::new)
                .save(
                        writer,
                        BuiltInRegistries.RECIPE_SERIALIZER.getKey(SFMRecipeSerializers.LABEL_GUN_RESET.get()).getPath()
                );
    }

    private void addPrintingPressRecipe(
            RecipeOutput consumer,
            Identifier id,
            Ingredient form,
            Ingredient ink,
            Ingredient paper
    ) {

        consumer.accept(ResourceKey.create(Registries.RECIPE, id), new PrintingPressRecipe(form, ink, paper), null);
    }

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
}
