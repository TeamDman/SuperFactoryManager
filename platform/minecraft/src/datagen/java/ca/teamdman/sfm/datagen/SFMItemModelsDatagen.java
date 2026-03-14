package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
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
        justParent(SFMItems.MANAGER_ITEM, SFMBlocks.MANAGER_BLOCK);
        justParent(SFMItems.TUNNELLED_MANAGER_ITEM, SFMBlocks.TUNNELLED_MANAGER_BLOCK);
        justParent(SFMItems.CABLE_ITEM, SFMBlocks.CABLE_BLOCK);
        justParent(SFMItems.FANCY_CABLE_ITEM, SFMBlocks.FANCY_CABLE_BLOCK, "_core");
        justParent(SFMItems.PRINTING_PRESS_ITEM, SFMBlocks.PRINTING_PRESS_BLOCK);
        justParent(SFMItems.WATER_TANK_ITEM, SFMBlocks.WATER_TANK_BLOCK, "_active");
        justParent(SFMItems.BUFFER_ITEM, SFMBlocks.BUFFER_BLOCK, "_item");
        basicItem(SFMItems.DISK_ITEM);
        basicItem(SFMItems.LABEL_GUN_ITEM);
        basicItem(SFMItems.EXPERIENCE_GOOP_ITEM);
        basicItem(SFMItems.EXPERIENCE_SHARD_ITEM);
        basicItem(SFMItems.NETWORK_TOOL_ITEM);

        // force custom renderer
        getBuilder(SFMItems.FORM_ITEM)
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
