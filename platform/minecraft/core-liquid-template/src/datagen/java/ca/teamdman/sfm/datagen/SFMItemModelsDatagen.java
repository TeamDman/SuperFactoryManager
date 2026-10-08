package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
{% endcase %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticItemModelsDataGen;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.data.PackOutput;
{% else %}
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.resources.ResourceKey;
{% endcase %}
import net.minecraft.world.item.Item;
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
import net.minecraft.world.level.block.Block;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.model.generators.ItemModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.data.event.GatherDataEvent;
{% else %}
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% endcase %}
{% endcase %}

public class SFMItemModelsDatagen extends MCVersionAgnosticItemModelsDataGen {
    public SFMItemModelsDatagen(
{% case minecraft_version %}
{% when "26.1.2" %}
            PackOutput output
{% else %}
            GatherDataEvent event
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        super(output, SFM.MOD_ID);
{% else %}
        super(event, SFM.MOD_ID);
{% endcase %}
    }


    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    protected void populate(ItemModelGenerators itemModels) {
//        justParent(SFMItems.MANAGER, SFMBlocks.MANAGER);
//        justParent(SFMItems.TUNNELLED_MANAGER, SFMBlocks.TUNNELLED_MANAGER);
//        justParent(SFMItems.CABLE, SFMBlocks.CABLE);
//        justParent(SFMItems.FANCY_CABLE, SFMBlocks.FANCY_CABLE, "_core");
//
//        // Tough cable models
//        justParent(SFMItems.TOUGH_CABLE, SFMBlocks.TOUGH_CABLE);
//        justParent(SFMItems.TOUGH_FANCY_CABLE, SFMBlocks.TOUGH_FANCY_CABLE, "_core");
//
//        // Tunnelled cable models
//        justParent(SFMItems.TUNNELLED_CABLE, SFMBlocks.TUNNELLED_CABLE);
//        justParent(SFMItems.TUNNELLED_FANCY_CABLE, SFMBlocks.TUNNELLED_FANCY_CABLE, "_core");
//
//        justParent(SFMItems.PRINTING_PRESS, SFMBlocks.PRINTING_PRESS);
//        justParent(SFMItems.WATER_TANK, SFMBlocks.WATER_TANK, "_active");
//        justParent(SFMItems.BUFFER, SFMBlocks.BUFFER_BLOCK, "_item");
        basicItem(itemModels, SFMItems.DISK);
        basicItem(itemModels, SFMItems.LABEL_GUN);
        basicItem(itemModels, SFMItems.EXPERIENCE_GOOP);
        basicItem(itemModels, SFMItems.EXPERIENCE_SHARD);
        basicItem(itemModels, SFMItems.NETWORK_TOOL);
{% else %}
    protected void registerModels() {
        justParent(SFMItems.MANAGER, SFMBlocks.MANAGER);
{% if features.client_manager %}
        justParent(SFMItems.CLIENT_MANAGER, SFMBlocks.CLIENT_MANAGER);
{% endif %}
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
{% if features.touch_display %}
        justParent(SFMItems.TOUCH_DISPLAY, SFMBlocks.TOUCH_DISPLAY);
{% endif %}
        justParent(SFMItems.WATER_TANK, SFMBlocks.WATER_TANK, "_active");
        justParent(SFMItems.BUFFER, SFMBlocks.BUFFER_BLOCK, "_item");
        basicItem(SFMItems.DISK);
        basicItem(SFMItems.LABEL_GUN);
        basicItem(SFMItems.EXPERIENCE_GOOP);
        basicItem(SFMItems.EXPERIENCE_SHARD);
        basicItem(SFMItems.NETWORK_TOOL);
{% if features.packet_values %}
        basicItem(SFMItems.PACKET);
{% endif %}
{% endcase %}

        // force custom renderer
{% case minecraft_version %}
{% when "26.1.2" %}
        basicItem(itemModels, SFMItems.FORM);
//        getBuilder(SFMItems.FORM)
//                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
//                .guiLight(BlockModel.GuiLight.FRONT);
//        getBuilder("form_base")
//                .parent(new ModelFile.UncheckedModelFile("item/generated"))
//                .texture("layer0", modLoc("item/form"));
{% else %}
        getBuilder(SFMItems.FORM)
                .parent(new ModelFile.UncheckedModelFile("builtin/entity"))
                .guiLight(BlockModel.GuiLight.FRONT);
        getBuilder("form_base")
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", modLoc("item/form"));
{% endcase %}
    }

{% case minecraft_version %}
{% when "26.1.2" %}
/*    @SuppressWarnings({"OptionalGetWithoutIsPresent", "SameParameterValue"})
{% else %}
    @SuppressWarnings({"OptionalGetWithoutIsPresent", "SameParameterValue"})
{% endcase %}
    private ItemModelBuilder getBuilder(SFMRegistryObject<Item, ? extends Item> item) {
        ResourceKey<? extends Item> resourceKey = item.getId().get();
{% case minecraft_version %}
{% when "26.1.2" %}
        return getBuilder(resourceKey.identifier()().toString());
{% else %}
        return getBuilder(resourceKey.location().toString());
{% endcase %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
    }*/
{% else %}
    }
{% endcase %}

    private void basicItem(
{% case minecraft_version %}
{% when "26.1.2" %}
            ItemModelGenerators itemModels,
{% else %}
{% endcase %}
            SFMRegistryObject<Item, ? extends Item> item) {
{% case minecraft_version %}
{% when "26.1.2" %}
        itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
{% else %}
        withExistingParent(
                item.getPath(),
                mcLoc("item/generated")
        ).texture(
                "layer0",
                modLoc("item/" + item.getPath())
        );
{% endcase %}
    }
}
