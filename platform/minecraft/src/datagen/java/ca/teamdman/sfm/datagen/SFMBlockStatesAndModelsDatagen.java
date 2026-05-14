package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.FancyCableBlock;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticBlockStatesAndModelsDataGen;
import com.mojang.math.Quadrant;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.*;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.renderer.block.model.BlockStateModelWrapper;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Collections;
import java.util.Optional;

public class SFMBlockStatesAndModelsDatagen extends MCVersionAgnosticBlockStatesAndModelsDataGen {
    private static final TextureSlot CABLE_SLOT = TextureSlot.create("cable", TextureSlot.ALL);

    private static final ModelTemplate FANCY_CABLE_TEMPLATE = new ModelTemplate(
            Optional.empty(),
            Optional.empty(),

            TextureSlot.PARTICLE,
            CABLE_SLOT
    );

    public SFMBlockStatesAndModelsDatagen(PackOutput output) {
        super(output, SFM.MOD_ID);
    }

    // Block Models
    @Override
    protected void populate(BlockModelGenerators blockModels) {

        registerManager(blockModels);
        registerTunnelledManager(blockModels);
        registerTestBarrelTank(blockModels);
        registerCableVariants(blockModels,
                SFMBlocks.CABLE,
                SFMBlocks.CABLE_FACADE,
                SFMBlocks.FANCY_CABLE,
                SFMBlocks.FANCY_CABLE_FACADE

        );
        registerCableVariants(blockModels,
                SFMBlocks.TUNNELLED_CABLE,
                SFMBlocks.TUNNELLED_CABLE_FACADE,
                SFMBlocks.TUNNELLED_FANCY_CABLE,
                SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE

        );
        registerCableVariants(blockModels,
                SFMBlocks.TOUGH_CABLE,
                SFMBlocks.TOUGH_CABLE_FACADE,
                SFMBlocks.TOUGH_FANCY_CABLE,
                SFMBlocks.TOUGH_FANCY_CABLE_FACADE

        );
        registerPrintingPress(blockModels);
        registerWaterTank(blockModels);
        registerTestBarrel(blockModels);
        registerBuffer(blockModels);
    }

    private void registerTestBarrel(BlockModelGenerators blockModels) {
        Identifier barrelModel = ModelLocationUtils.getModelLocation(Blocks.BARREL);
        Identifier barrelOpenModel = ModelLocationUtils.getModelLocation(Blocks.BARREL, "_open");

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(SFMBlocks.TEST_BARREL.get())
                        .with(PropertyDispatch.initial(BlockStateProperties.OPEN)
                                .select(false, BlockModelGenerators.plainVariant(barrelModel))
                                .select(true, BlockModelGenerators.plainVariant(barrelOpenModel)))
                        .with(PropertyDispatch.modify(BlockStateProperties.FACING)
                                .generate(direction -> switch (direction) {
                                    case UP -> BlockModelGenerators.NOP;
                                    case DOWN -> BlockModelGenerators.X_ROT_180;
                                    case NORTH -> BlockModelGenerators.X_ROT_90;
                                    case SOUTH -> BlockModelGenerators.X_ROT_90.then(BlockModelGenerators.Y_ROT_180);
                                    case WEST -> BlockModelGenerators.X_ROT_90.then(BlockModelGenerators.Y_ROT_270);
                                    case EAST -> BlockModelGenerators.X_ROT_90.then(BlockModelGenerators.Y_ROT_90);
                                }))
        );

//        blockModels.blockStateOutput.accept(
//                MultiVariantGenerator.dispatch(SFMBlocks.TEST_BARREL.get(),
//                        BlockModelGenerators.variant(new Variant(barrelModel)))
//        );
    }

    private void registerPrintingPress(BlockModelGenerators blockModels) {
        blockModels.createTrivialCube(SFMBlocks.PRINTING_PRESS.get());
    }

    private void registerTestBarrelTank(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.TEST_BARREL_TANK.get();
        Identifier model = ModelTemplates.CUBE_ALL.create(
                block,
                new TextureMapping()
                        .put(TextureSlot.ALL, TextureMapping.getBlockTexture(block))
                        .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block)),
                blockModels.modelOutput
        );

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block,
                        BlockModelGenerators.plainVariant(model)
                )
        );
    }

    private void registerTunnelledManager(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.TUNNELLED_MANAGER.get();
        Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(
                block,
                new TextureMapping()
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                        .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bot"))
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                        .copySlot(TextureSlot.TOP, TextureSlot.PARTICLE),
            blockModels.modelOutput
        );

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block,
                        BlockModelGenerators.plainVariant(model)
                )
        );
    }

    private void registerManager(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.MANAGER.get();
        Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(
                block,
                new TextureMapping()
                        .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"))
                        .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(block, "_bot"))
                        .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                        .copySlot(TextureSlot.TOP, TextureSlot.PARTICLE),
                blockModels.modelOutput
        );

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block,
                        BlockModelGenerators.plainVariant(model)
                )
        );
    }

    private void registerWaterTank(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.WATER_TANK.get();

        Identifier activeModelId = ModelTemplates.CUBE_ALL.create(
                ModelLocationUtils.getModelLocation(block, "_active"),
                new TextureMapping()
                        .put(TextureSlot.ALL, TextureMapping.getBlockTexture(block, "_active"))
                        .copySlot(TextureSlot.ALL, TextureSlot.PARTICLE),
                blockModels.modelOutput
        );

        Identifier inactiveModelId = ModelTemplates.CUBE_ALL.create(
                ModelLocationUtils.getModelLocation(block, "_inactive"),
                new TextureMapping()
                        .put(TextureSlot.ALL, TextureMapping.getBlockTexture(block, "_inactive"))
                        .copySlot(TextureSlot.ALL, TextureSlot.PARTICLE),
                blockModels.modelOutput
        );

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(PropertyDispatch.initial(WaterTankBlock.IN_WATER)
                                .select(true, BlockModelGenerators.plainVariant(activeModelId))
                                .select(false, BlockModelGenerators.plainVariant(inactiveModelId))
                        )
        );
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void registerCableVariants(
            BlockModelGenerators blockModels,
            SFMRegistryObject<Block, ? extends Block> cableBlock,
            SFMRegistryObject<Block, ? extends Block> cableFacadeBlock,
            SFMRegistryObject<Block, ? extends Block> fancyCableBlock,
            SFMRegistryObject<Block, ? extends Block> fancyCableFacadeBlock
    ) {

        SFM.LOGGER.info("Registering cable variants for \"{}\"", cableBlock.getId().get());
        blockModels.createTrivialCube(cableBlock.get());
        SFM.LOGGER.info("Registering cable facade variants for \"{}\"", cableFacadeBlock.getId().get());
        blockModels.copyModel(cableBlock.get(), cableFacadeBlock.get());
        SFM.LOGGER.info("Registering fancy cable variants for \"{}\"", fancyCableBlock.getId().get());
        registerFancyCableVariant(blockModels, fancyCableBlock, fancyCableFacadeBlock);
    }

    private void registerFancyCableVariant(
            BlockModelGenerators blockModels,
            SFMRegistryObject<Block, ? extends Block> fancyCableBlock,
            SFMRegistryObject<Block, ? extends Block> fancyCableFacadeBlock
    ) {

        ModelTemplate coreTemplate = FANCY_CABLE_TEMPLATE
                .extend()
                .parent(Identifier.withDefaultNamespace("block/block"))
                .element(el -> el
                        .from(4, 4, 4)
                        .to(12, 12, 12)
                        .allFaces((dir, face) -> face
                                .uvs(8, 0, 16, 8)
                                .texture(CABLE_SLOT)
                        )
                )
                .requiredTextureSlot(CABLE_SLOT)
                .build();

        ModelTemplate connectionTemplate = FANCY_CABLE_TEMPLATE
                .extend()
                .parent(Identifier.withDefaultNamespace("block/block"))
                .element(el -> el
                        .from(5, 5, 0)
                        .to(11, 11, 5)
                        .allFaces((dir, face) -> {
                            switch (dir) {
                                case NORTH, SOUTH -> face.uvs(9, 1, 15, 7);
                                case EAST, WEST   -> face.uvs(0, 0, 5, 6);
                                case UP, DOWN     -> face.uvs(0, 0, 5, 6)
                                        .rotation(Quadrant.R90);
                            }
                            face.texture(CABLE_SLOT);
                        })
                )
                .requiredTextureSlot(CABLE_SLOT)
                .build();

        TextureMapping cableTexture = new TextureMapping()
                .put(CABLE_SLOT, TextureMapping.getBlockTexture(fancyCableBlock.get()))
                .copySlot(CABLE_SLOT, TextureSlot.PARTICLE);

        Identifier coreModelId = coreTemplate.create(
                ModelLocationUtils.getModelLocation(fancyCableBlock.get(), "_core"),
                cableTexture,
                blockModels.modelOutput
        );
        Identifier connectionModelId = connectionTemplate.create(
                ModelLocationUtils.getModelLocation(fancyCableBlock.get(), "_connection"),
                cableTexture,
                blockModels.modelOutput
        );

        for (Block block : new Block[]{fancyCableBlock.get(), fancyCableFacadeBlock.get()}) {
            MultiPartGenerator generator = MultiPartGenerator.multiPart(block)
                    .with(BlockModelGenerators.variant(new Variant(coreModelId)));

            for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
                int rotX = 0;
                int rotY = 0;
                switch (direction) {
                    case SOUTH -> rotY = 180;
                    case EAST  -> rotY = 90;
                    case WEST  -> rotY = 270;
                    case UP    -> rotX = 270;
                    case DOWN  -> rotX = 90;
                }

                Variant connectionVariant = new Variant(connectionModelId)
                        .with(VariantMutator.X_ROT.withValue(Quadrant.values()[rotX / 90]))
                        .with(VariantMutator.Y_ROT.withValue(Quadrant.values()[rotY / 90]));

                generator.with(
                        BlockModelGenerators.condition()
                                .term(FancyCableBlock.DIRECTION_PROPERTIES.get(direction), true),
                        BlockModelGenerators.variant(connectionVariant)
                );
            }

            blockModels.blockStateOutput.accept(generator);
        }
    }

    private void registerBuffer(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.BUFFER_BLOCK.get();

        var dispatch = PropertyDispatch.initial(BufferBlock.CONTAINED_RESOURCE);

        for (BufferBlock.ContainedResource value : BufferBlock.ContainedResource.values()) {
            String name = "_" + value.getSerializedName();

            Identifier modelId = ModelTemplates.CUBE_ALL.create(
                    ModelLocationUtils.getModelLocation(block, name),
                    new TextureMapping().put(TextureSlot.ALL, TextureMapping.getBlockTexture(block, name)),
                    blockModels.modelOutput
            );
            dispatch = dispatch.select(value, BlockModelGenerators.plainVariant(modelId));
        }

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(dispatch)
        );

    }

    // Item Models
    @Override
    protected void populate(ItemModelGenerators itemModels) {
        basicItem(itemModels, SFMItems.DISK);
        basicItem(itemModels, SFMItems.LABEL_GUN);
        basicItem(itemModels, SFMItems.EXPERIENCE_GOOP);
        basicItem(itemModels, SFMItems.EXPERIENCE_SHARD);
        basicItem(itemModels, SFMItems.NETWORK_TOOL);

        basicItem(itemModels, SFMItems.FORM); // Apparently do something special with this?

        withParent(itemModels, SFMItems.MANAGER, SFMBlocks.MANAGER);
        withParent(itemModels, SFMItems.TUNNELLED_MANAGER, SFMBlocks.TUNNELLED_MANAGER);
        withParent(itemModels, SFMItems.CABLE, SFMBlocks.CABLE);
        withParent(itemModels, SFMItems.PRINTING_PRESS, SFMBlocks.PRINTING_PRESS);

        withParent(itemModels, SFMItems.FANCY_CABLE, SFMBlocks.FANCY_CABLE, "_core");
        withParent(itemModels, SFMItems.TOUGH_FANCY_CABLE, SFMBlocks.TOUGH_FANCY_CABLE, "_core");
        withParent(itemModels, SFMItems.TUNNELLED_FANCY_CABLE, SFMBlocks.TUNNELLED_FANCY_CABLE, "_core");

        withParent(itemModels, SFMItems.BUFFER, SFMBlocks.BUFFER_BLOCK, "_item");
        withParent(itemModels, SFMItems.WATER_TANK, SFMBlocks.WATER_TANK, "_active");
    }

    private void basicItem(
            ItemModelGenerators itemModels,
            SFMRegistryObject<Item, ? extends Item> item
    ) {
        itemModels.generateFlatItem(item.get(), ModelTemplates.FLAT_ITEM);
    }

    private void withParent(
            ItemModelGenerators itemModels,
            SFMRegistryObject<Item, ? extends Item> item,
            SFMRegistryObject<Block, ? extends Block> block
    ) {
        this.withParent(itemModels, item, block, "");
    }

    private void withParent(
            ItemModelGenerators itemModels,
            SFMRegistryObject<Item, ? extends Item> item,
            SFMRegistryObject<Block, ? extends Block> block,
            String suffix
    ) {
        Identifier modelLocation = ModelLocationUtils.getModelLocation(block.get(), suffix);
        itemModels.itemModelOutput.accept(
                item.get(),
                ItemModelUtils.plainModel(modelLocation)
        );
    }
}
