package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticItemModelsDataGen;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.data.event.GatherDataEvent;

public class SFMItemModelsDatagen extends MCVersionAgnosticItemModelsDataGen {
    public SFMItemModelsDatagen(
            GatherDataEvent event
    ) {
        super(event, SFM.MOD_ID);
    }


    @Override
    protected void registerModels() {
        justParent(SFMItems.MANAGER, SFMBlocks.MANAGER);
        justParent(SFMItems.TUNNELLED_MANAGER, SFMBlocks.TUNNELLED_MANAGER);
        justParent(SFMItems.CABLE, SFMBlocks.CABLE);
        justParent(SFMItems.FANCY_CABLE, SFMBlocks.FANCY_CABLE, "_core");

        // Tough cable models
        justParent(SFMItems.TOUGH_CABLE, SFMBlocks.TOUGH_CABLE);
        justParent(SFMItems.TOUGH_FANCY_CABLE, SFMBlocks.TOUGH_FANCY_CABLE, "_core");

        // Tunnelled cable models
        justParent(SFMItems.TUNNELLED_CABLE, SFMBlocks.TUNNELLED_CABLE);
        justParent(SFMItems.TUNNELLED_FANCY_CABLE, SFMBlocks.TUNNELLED_FANCY_CABLE, "_core");

        justParent(SFMItems.PRINTING_PRESS, SFMBlocks.PRINTING_PRESS);
        justParent(SFMItems.WATER_TANK, SFMBlocks.WATER_TANK, "_active");
        justParent(SFMItems.BUFFER, SFMBlocks.BUFFER_BLOCK, "_item");
        basicItem(SFMItems.DISK);
        basicItem(SFMItems.LABEL_GUN);
        basicItem(SFMItems.EXPERIENCE_GOOP);
        basicItem(SFMItems.EXPERIENCE_SHARD);
        basicItem(SFMItems.NETWORK_TOOL);

        // force custom renderer
        getBuilder(SFMItems.FORM)
                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .guiLight(BlockModel.GuiLight.FRONT);
        getBuilder("form_base")
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", modLoc("item/form"));
    }

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "SameParameterValue"})
    private ItemModelBuilder getBuilder(SFMRegistryObject<Item, ? extends Item> item) {
        ResourceKey<? extends Item> resourceKey = item.getId().get();
        return getBuilder(resourceKey.location().toString());
    }

    private void justParent(
            SFMRegistryObject<Item, ? extends Item> item,
            SFMRegistryObject<Block, ? extends Block> block
    ) {
        justParent(item, block, "");
    }

    private void justParent(
            SFMRegistryObject<Item,? extends Item> item,
            SFMRegistryObject<Block, ? extends Block> block,
            String extra
    ) {
        withExistingParent(
                block.getPath(),
                SFM.MOD_ID + ":block/" + item.getPath() + extra
        );
    }

    private void basicItem(
            SFMRegistryObject<Item, ? extends Item> item
    ) {
        withExistingParent(
                item.getPath(),
                mcLoc("item/generated")
        ).texture(
                "layer0",
                modLoc("item/" + item.getPath())
        );
    }
}
