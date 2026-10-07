package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "26.1.2" %}
import ca.teamdman.sfm.client.render.FormItemRenderer;
{% else %}
{% endcase %}
import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.FancyCableBlock;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
{% case minecraft_version %}
{% when "26.1.2" %}
import ca.teamdman.sfm.common.registry.registration.SFMItems;
{% else %}
{% endcase %}
import ca.teamdman.sfm.common.util.SFMDirections;
{% case minecraft_version %}
{% when "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;
{% else %}
{% endcase %}
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticBlockStatesAndModelsDataGen;
{% case minecraft_version %}
{% when "26.1.2" %}
import com.mojang.math.Quadrant;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.*;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.renderer.item.SpecialModelWrapper;
{% else %}
{% endcase %}
import net.minecraft.core.Direction;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
{% else %}
{% endcase %}
import net.minecraft.world.level.block.Block;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.world.level.block.Blocks;
{% else %}
{% endcase %}
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.data.event.GatherDataEvent;
{% else %}
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.data.event.GatherDataEvent;
{% endcase %}
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
import java.util.Optional;

{% else %}
{% endcase %}
public class SFMBlockStatesAndModelsDatagen extends MCVersionAgnosticBlockStatesAndModelsDataGen {
{% case minecraft_version %}
{% when "26.1.2" %}
    private static final TextureSlot CABLE_SLOT = TextureSlot.create("cable", TextureSlot.ALL);
{% else %}
    public SFMBlockStatesAndModelsDatagen(GatherDataEvent event) {
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
    private static final ModelTemplate FANCY_CABLE_TEMPLATE = new ModelTemplate(
            Optional.empty(),
            Optional.empty(),

            TextureSlot.PARTICLE,
            CABLE_SLOT
    );

    public SFMBlockStatesAndModelsDatagen(PackOutput output) {
        super(output, SFM.MOD_ID);
{% else %}
        super(event, SFM.MOD_ID);
{% endcase %}
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    // Block Models
{% else %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    protected void populate(BlockModelGenerators blockModels) {
{% else %}
    protected void registerStatesAndModels() {
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
        registerManager(blockModels);
        registerTunnelledManager(blockModels);
        registerTestBarrelTank(blockModels);
        registerCableVariants(blockModels,
{% else %}
        registerManager();
{% if features.client_manager %}
        registerClientManager();
{% endif %}
        registerTunnelledManager();
        registerTestBarrelTank();
        registerCableVariants(
{% endcase %}
                SFMBlocks.CABLE,
                SFMBlocks.CABLE_FACADE,
                SFMBlocks.FANCY_CABLE,
                SFMBlocks.FANCY_CABLE_FACADE

        );
{% case minecraft_version %}
{% when "26.1.2" %}
        registerCableVariants(blockModels,
{% else %}
        registerCableVariants(
{% endcase %}
                SFMBlocks.TUNNELLED_CABLE,
                SFMBlocks.TUNNELLED_CABLE_FACADE,
                SFMBlocks.TUNNELLED_FANCY_CABLE,
                SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE

        );
{% case minecraft_version %}
{% when "26.1.2" %}
        registerCableVariants(blockModels,
{% else %}
        registerCableVariants(
{% endcase %}
                SFMBlocks.TOUGH_CABLE,
                SFMBlocks.TOUGH_CABLE_FACADE,
                SFMBlocks.TOUGH_FANCY_CABLE,
                SFMBlocks.TOUGH_FANCY_CABLE_FACADE

        );
{% case minecraft_version %}
{% when "26.1.2" %}
        registerPrintingPress(blockModels);
        registerWaterTank(blockModels);
        registerTestBarrel(blockModels);
        registerBuffer(blockModels);
{% else %}
        registerPrintingPress();
{% if features.touch_display %}
        registerTouchDisplay();
{% endif %}
        registerWaterTank();
        registerTestBarrel();
        registerBuffer();
{% endcase %}
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private void registerTestBarrel(BlockModelGenerators blockModels) {
        Identifier barrelModel = ModelLocationUtils.getModelLocation(Blocks.BARREL);
        Identifier barrelOpenModel = ModelLocationUtils.getModelLocation(Blocks.BARREL, "_open");
{% else %}
    private void registerTestBarrel() {

        ModelFile barrelModel = models().getExistingFile(mcLoc("block/barrel"));
        ModelFile barrelOpenModel = models().getExistingFile(mcLoc("block/barrel_open"));

        getVariantBuilder(SFMBlocks.TEST_BARREL.get())
                .forAllStates(state -> {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    boolean open = state.getValue(BlockStateProperties.OPEN);
                    int x;
                    int y;
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
                    switch (facing) {
                        case DOWN -> {
                            x = 180;
                            y = 0;
                        }
                        case NORTH -> {
                            x = 90;
                            y = 0;
                        }
                        case SOUTH -> {
                            x = 90;
                            y = 180;
                        }
                        case WEST -> {
                            x = 90;
                            y = 270;
                        }
                        case EAST -> {
                            x = 90;
                            y = 90;
                        }
                        default -> { // up
                            x = 0;
                            y = 0;
                        }
                    }
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
//        blockModels.blockStateOutput.accept(
//                MultiVariantGenerator.dispatch(SFMBlocks.TEST_BARREL.get(),
//                        BlockModelGenerators.variant(new Variant(barrelModel)))
//        );
{% else %}
                    return ConfiguredModel.builder()
                            .modelFile(open ? barrelOpenModel : barrelModel)
                            .rotationX(x)
                            .rotationY(y)
                            .build();
                });
{% endcase %}
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private void registerPrintingPress(BlockModelGenerators blockModels) {
        blockModels.createTrivialCube(SFMBlocks.PRINTING_PRESS.get());
{% else %}
    private void registerPrintingPress() {

        simpleBlock(SFMBlocks.PRINTING_PRESS.get(), models().getExistingFile(modLoc("block/printing_press")));
{% endcase %}
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private void registerTestBarrelTank(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.TEST_BARREL_TANK.get();
        Identifier model = ModelTemplates.CUBE_ALL.create(
                block,
                new TextureMapping()
                        .put(TextureSlot.ALL, TextureMapping.getBlockTexture(block))
                        .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(block)),
                blockModels.modelOutput
        );
{% else %}
{% if features.touch_display %}
    private void registerTouchDisplay() {

        // The model's top is the display face; rotate it to the block's FACING direction.
        ModelFile displayModel = models().cubeBottomTop(
                SFMBlocks.TOUCH_DISPLAY.getPath(),
{% if features.touch_display_custom_textures %}
                modLoc("block/touch_display_side"),
                modLoc("block/touch_display_bottom"),
                modLoc("block/touch_display_face")
{% else %}
                modLoc("block/manager_side"),
                modLoc("block/manager_bot"),
                modLoc("block/buffer_unknown")
{% endif %}
        ).texture("particle", "#top");

        getVariantBuilder(SFMBlocks.TOUCH_DISPLAY.get())
                .forAllStates(state -> {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    int x;
                    int y;

                    switch (facing) {
                        case DOWN -> {
                            x = 180;
                            y = 0;
                        }
                        case NORTH -> {
                            x = 90;
                            y = 0;
                        }
                        case SOUTH -> {
                            x = 90;
                            y = 180;
                        }
                        case WEST -> {
                            x = 90;
                            y = 270;
                        }
                        case EAST -> {
                            x = 90;
                            y = 90;
                        }
                        default -> { // up
                            x = 0;
                            y = 0;
                        }
                    }

                    return ConfiguredModel.builder()
                            .modelFile(displayModel)
                            .rotationX(x)
                            .rotationY(y)
                            .build();
                });
    }

{% endif %}
    private void registerTestBarrelTank() {
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block,
                        BlockModelGenerators.plainVariant(model)
                )
{% else %}
        simpleBlock(
                SFMBlocks.TEST_BARREL_TANK.get(), models().cubeAll(
                        SFMBlocks.TEST_BARREL_TANK.getPath(),
                        modLoc("block/test_barrel_tank")
                ).texture("particle", "#all")
{% endcase %}
        );
    }

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
    private void registerTunnelledManager() {
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block,
                        BlockModelGenerators.plainVariant(model)
                )
{% else %}
        simpleBlock(
                SFMBlocks.TUNNELLED_MANAGER.get(), models().cubeBottomTop(
                        SFMBlocks.TUNNELLED_MANAGER.getPath(),
                        modLoc("block/tunnelled_manager_side"),
                        modLoc("block/tunnelled_manager_bot"),
                        modLoc("block/tunnelled_manager_top")
                ).texture("particle", "#top")
{% endcase %}
        );
    }

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
    private void registerManager() {
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block,
                        BlockModelGenerators.plainVariant(model)
                )
{% else %}
        simpleBlock(
                SFMBlocks.MANAGER.get(), models().cubeBottomTop(
                        SFMBlocks.MANAGER.getPath(),
                        modLoc("block/manager_side"),
                        modLoc("block/manager_bot"),
                        modLoc("block/manager_top")
                ).texture("particle", "#top")
{% endcase %}
        );
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private void registerWaterTank(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.WATER_TANK.get();
{% else %}
{% if features.client_manager %}
    private void registerClientManager() {
        // Cyan body and familiar manager face make the logical execution side visible in-world.
        simpleBlock(
                SFMBlocks.CLIENT_MANAGER.get(), models().cubeBottomTop(
                        SFMBlocks.CLIENT_MANAGER.getPath(),
{% if features.client_manager_custom_textures %}
                        modLoc("block/client_manager_side"),
                        modLoc("block/client_manager_bottom"),
                        modLoc("block/client_manager_top")
{% else %}
                        mcLoc("block/cyan_concrete"),
                        modLoc("block/manager_bot"),
                        modLoc("block/manager_top")
{% endif %}
                ).texture("particle", "#top")
        );
    }

{% endif %}
    private void registerWaterTank() {
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
        ModelFile waterIntakeModelActive = models()
                .cubeAll(
                        SFMBlocks.WATER_TANK.getPath() + "_active",
                        modLoc("block/water_intake_active")
                );
        ModelFile waterIntakeModelInactive = models()
                .cubeAll(
                        SFMBlocks.WATER_TANK.getPath() + "_inactive",
                        modLoc("block/water_intake_inactive")
                );
        getVariantBuilder(SFMBlocks.WATER_TANK.get())
                .forAllStates(state -> ConfiguredModel
                        .builder()
                        .modelFile(
                                state.getValue(WaterTankBlock.IN_WATER)
                                ? waterIntakeModelActive
                                : waterIntakeModelInactive
{% endcase %}
                        )
{% case minecraft_version %}
{% when "26.1.2" %}
        );
{% else %}
                        .build());
{% endcase %}
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void registerCableVariants(
{% case minecraft_version %}
{% when "26.1.2" %}
            BlockModelGenerators blockModels,
            SFMRegistryObject<Block, ? extends Block> cableBlock,
            SFMRegistryObject<Block, ? extends Block> cableFacadeBlock,
            SFMRegistryObject<Block, ? extends Block> fancyCableBlock,
            SFMRegistryObject<Block, ? extends Block> fancyCableFacadeBlock
{% else %}
            SFMRegistryObject<Block, ?> cableBlock,
            SFMRegistryObject<Block, ?> cableFacadeBlock,
            SFMRegistryObject<Block, ?> fancyCableBlock,
            SFMRegistryObject<Block, ?> fancyCableFacadeBlock
{% endcase %}
    ) {

        SFM.LOGGER.info("Registering cable variants for \"{}\"", cableBlock.getId().get());
{% case minecraft_version %}
{% when "26.1.2" %}
        blockModels.createTrivialCube(cableBlock.get());
{% else %}
        simpleBlock(cableBlock.get());
{% endcase %}
        SFM.LOGGER.info("Registering cable facade variants for \"{}\"", cableFacadeBlock.getId().get());
{% case minecraft_version %}
{% when "26.1.2" %}
        Identifier cableModelId = ModelLocationUtils.getModelLocation(cableBlock.get());
        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(cableFacadeBlock.get(),
                        BlockModelGenerators.plainVariant(cableModelId)
                )
        );
{% else %}
        simpleBlock(cableFacadeBlock.get(), cubeAll(cableBlock.get()));
{% endcase %}
        SFM.LOGGER.info("Registering fancy cable variants for \"{}\"", fancyCableBlock.getId().get());
{% case minecraft_version %}
{% when "26.1.2" %}
        registerFancyCableVariant(blockModels, fancyCableBlock, fancyCableFacadeBlock);
{% else %}
        registerFancyCableVariant(fancyCableBlock, fancyCableFacadeBlock);
{% endcase %}
    }

    private void registerFancyCableVariant(
{% case minecraft_version %}
{% when "26.1.2" %}
            BlockModelGenerators blockModels,
            SFMRegistryObject<Block, ? extends Block> fancyCableBlock,
            SFMRegistryObject<Block, ? extends Block> fancyCableFacadeBlock
{% else %}
            SFMRegistryObject<Block, ?> fancyCableBlock,
            SFMRegistryObject<Block, ?> fancyCableFacadeBlock
{% endcase %}
    ) {

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
        String fancy_cable_name = fancyCableBlock.getPath();
        var coreModel = models().withExistingParent("block/" + fancy_cable_name + "_core", "block/block")
                .element()
                .from(4, 4, 4)
                .to(12, 12, 12)
                .shade(false)
                .allFaces((direction, faceBuilder) -> faceBuilder.uvs(8, 0, 16, 8).texture("#cable"))
                .end()
                .texture("cable", modLoc("block/" + fancy_cable_name))
                .texture("particle", modLoc("block/" + fancy_cable_name));
        var connectionModel = models()
                .withExistingParent("block/" + fancy_cable_name + "_connection", "block/block")
                .element()
                .from(5, 5, 0)
                .to(11, 11, 5)
                .shade(false)
                .allFaces((direction, faceBuilder) -> {
                    switch (direction) {
                        case NORTH:
                        case SOUTH: {
                            faceBuilder.uvs(9, 1, 15, 7);
                            break;
                        }
                        case EAST:
                        case WEST: {
                            faceBuilder.uvs(0, 0, 5, 6);
                            break;
                        }
                        case UP:
                        case DOWN: {
                            faceBuilder.uvs(0, 0, 5, 6)
                                    .rotation(ModelBuilder.FaceRotation.CLOCKWISE_90);
                            break;
                        }
                    }
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
                    faceBuilder.texture("#cable");
                })
                .end()
                .texture("cable", modLoc("block/" + fancy_cable_name));
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
        TextureMapping cableTexture = new TextureMapping()
                .put(CABLE_SLOT, TextureMapping.getBlockTexture(fancyCableBlock.get()))
                .copySlot(CABLE_SLOT, TextureSlot.PARTICLE);
{% else %}
        var multipartBuilder1 = getMultipartBuilder(fancyCableBlock.get());
        var multipartBuilder2 = getMultipartBuilder(fancyCableFacadeBlock.get());
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
        // Core
        multipartBuilder1.part()
                .modelFile(coreModel)
                .addModel()
                .end();
        multipartBuilder2.part()
                .modelFile(coreModel)
                .addModel()
                .end();
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
        for (Block block : new Block[]{fancyCableBlock.get(), fancyCableFacadeBlock.get()}) {
            MultiPartGenerator generator = MultiPartGenerator.multiPart(block)
                    .with(BlockModelGenerators.variant(new Variant(coreModelId)));
{% else %}
        // Parts (connections)
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            var rotX = 0;
            var rotY = 0;
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
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
{% else %}
            switch (direction) {
                case SOUTH -> rotY = 180;
                case EAST -> rotY = 90;
                case WEST -> rotY = 270;
                case UP -> rotX = 270;
                case DOWN -> rotX = 90;
{% endcase %}
            }

{% case minecraft_version %}
{% when "26.1.2" %}
            blockModels.blockStateOutput.accept(generator);
{% else %}
            multipartBuilder1.part()
                    .modelFile(connectionModel)
                    .rotationX(rotX)
                    .rotationY(rotY)
                    .uvLock(false)
                    .addModel()
                    .condition(FancyCableBlock.DIRECTION_PROPERTIES.get(direction), true)
                    .end();
            multipartBuilder2.part()
                    .modelFile(connectionModel)
                    .rotationX(rotX)
                    .rotationY(rotY)
                    .uvLock(false)
                    .addModel()
                    .condition(FancyCableBlock.DIRECTION_PROPERTIES.get(direction), true)
                    .end();
{% endcase %}
        }
    }

{% case minecraft_version %}
{% when "26.1.2" %}
    private void registerBuffer(BlockModelGenerators blockModels) {
        Block block = SFMBlocks.BUFFER_BLOCK.get();
{% else %}
    private void registerBuffer() {
        getVariantBuilder(SFMBlocks.BUFFER_BLOCK.get())
                .forAllStates(state -> {
                    BufferBlock.ContainedResource containedResource = state.getValue(BufferBlock.CONTAINED_RESOURCE);
{% if features.image_resources %}
                    // The image buffer uses the existing neutral texture until it has dedicated art.
                    String texture = containedResource == BufferBlock.ContainedResource.Image
                                     ? "unknown"
                                     : containedResource.getSerializedName();
{% endif %}
                    ModelFile modelFile = models().cubeAll(
                            SFMBlocks.BUFFER_BLOCK.getPath() + "_" + containedResource.getSerializedName(),
{% if features.image_resources %}
                            modLoc("block/buffer_" + texture)
{% else %}
                            modLoc("block/buffer_" + containedResource.getSerializedName())
{% endif %}
                    );
                    return ConfiguredModel.builder().modelFile(modelFile).build();
                });
{% endcase %}

{% case minecraft_version %}
{% when "26.1.2" %}
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

{% else %}
{% endcase %}
    }
{% case minecraft_version %}
{% when "26.1.2" %}

    // Item Models
    @Override
    protected void populate(ItemModelGenerators itemModels) {
        basicItem(itemModels, SFMItems.DISK);
        basicItem(itemModels, SFMItems.LABEL_GUN);
        basicItem(itemModels, SFMItems.EXPERIENCE_GOOP);
        basicItem(itemModels, SFMItems.EXPERIENCE_SHARD);
        basicItem(itemModels, SFMItems.NETWORK_TOOL);

        registerForm(itemModels);

        withParent(itemModels, SFMItems.MANAGER, SFMBlocks.MANAGER);
        withParent(itemModels, SFMItems.TUNNELLED_MANAGER, SFMBlocks.TUNNELLED_MANAGER);
        withParent(itemModels, SFMItems.PRINTING_PRESS, SFMBlocks.PRINTING_PRESS);
        withParent(itemModels, SFMItems.CABLE, SFMBlocks.CABLE);

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
    private void basicItem(
            ItemModelGenerators itemModels,
            SFMRegistryObject<Item, ? extends Item> item,
            String suffix
    ) {
        itemModels.createFlatItemModel(item.get(), suffix, ModelTemplates.FLAT_ITEM);
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

    private void registerForm(ItemModelGenerators itemModels) {
        Item form = SFMItems.FORM.get();
        Identifier formModelId = ModelTemplates.FLAT_ITEM.create(
                form,
                new TextureMapping().put(TextureSlot.LAYER0, TextureMapping.getItemTexture(form)),
                itemModels.modelOutput
        );

        itemModels.itemModelOutput.accept(
                form,
                new SpecialModelWrapper.Unbaked(
                        SFMResourceLocation.fromSFMPath("item/form"),
                        Optional.empty(),
                        new FormItemRenderer.Unbaked()
                )
        );
    }
{% else %}
{% endcase %}
}
