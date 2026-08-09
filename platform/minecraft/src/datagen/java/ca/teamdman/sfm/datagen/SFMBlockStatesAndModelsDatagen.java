package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.BufferBlock;
import ca.teamdman.sfm.common.block.FancyCableBlock;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.registry.SFMRegistryObject;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.util.SFMDirections;
import ca.teamdman.sfm.datagen.version_plumbing.MCVersionAgnosticBlockStatesAndModelsDataGen;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.data.event.GatherDataEvent;

public class SFMBlockStatesAndModelsDatagen extends MCVersionAgnosticBlockStatesAndModelsDataGen {
    public SFMBlockStatesAndModelsDatagen(GatherDataEvent event) {

        super(event, SFM.MOD_ID);
    }

    @Override
    protected void registerStatesAndModels() {

        registerManager();
        registerTunnelledManager();
        registerTestBarrelTank();
        registerCableVariants(
                SFMBlocks.CABLE,
                SFMBlocks.CABLE_FACADE,
                SFMBlocks.FANCY_CABLE,
                SFMBlocks.FANCY_CABLE_FACADE

        );
        registerCableVariants(
                SFMBlocks.TUNNELLED_CABLE,
                SFMBlocks.TUNNELLED_CABLE_FACADE,
                SFMBlocks.TUNNELLED_FANCY_CABLE,
                SFMBlocks.TUNNELLED_FANCY_CABLE_FACADE

        );
        registerCableVariants(
                SFMBlocks.TOUGH_CABLE,
                SFMBlocks.TOUGH_CABLE_FACADE,
                SFMBlocks.TOUGH_FANCY_CABLE,
                SFMBlocks.TOUGH_FANCY_CABLE_FACADE

        );
        registerPrintingPress();
        registerWaterTank();
        registerTestBarrel();
        registerBuffer();
    }

    private void registerTestBarrel() {

        ModelFile barrelModel = models().getExistingFile(mcLoc("block/barrel"));
        ModelFile barrelOpenModel = models().getExistingFile(mcLoc("block/barrel_open"));

        getVariantBuilder(SFMBlocks.TEST_BARREL.get())
                .forAllStates(state -> {
                    Direction facing = state.getValue(BlockStateProperties.FACING);
                    boolean open = state.getValue(BlockStateProperties.OPEN);
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
                            .modelFile(open ? barrelOpenModel : barrelModel)
                            .rotationX(x)
                            .rotationY(y)
                            .build();
                });
    }

    private void registerPrintingPress() {

        simpleBlock(SFMBlocks.PRINTING_PRESS.get(), models().getExistingFile(modLoc("block/printing_press")));
    }

    private void registerTestBarrelTank() {

        simpleBlock(
                SFMBlocks.TEST_BARREL_TANK.get(), models().cubeAll(
                        SFMBlocks.TEST_BARREL_TANK.getPath(),
                        modLoc("block/test_barrel_tank")
                ).texture("particle", "#all")
        );
    }

    private void registerTunnelledManager() {

        simpleBlock(
                SFMBlocks.TUNNELLED_MANAGER.get(), models().cubeBottomTop(
                        SFMBlocks.TUNNELLED_MANAGER.getPath(),
                        modLoc("block/tunnelled_manager_side"),
                        modLoc("block/tunnelled_manager_bot"),
                        modLoc("block/tunnelled_manager_top")
                ).texture("particle", "#top")
        );
    }

    private void registerManager() {

        simpleBlock(
                SFMBlocks.MANAGER.get(), models().cubeBottomTop(
                        SFMBlocks.MANAGER.getPath(),
                        modLoc("block/manager_side"),
                        modLoc("block/manager_bot"),
                        modLoc("block/manager_top")
                ).texture("particle", "#top")
        );
    }

    private void registerWaterTank() {

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
                        )
                        .build());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private void registerCableVariants(
            SFMRegistryObject<Block, ?> cableBlock,
            SFMRegistryObject<Block, ?> cableFacadeBlock,
            SFMRegistryObject<Block, ?> fancyCableBlock,
            SFMRegistryObject<Block, ?> fancyCableFacadeBlock
    ) {

        SFM.LOGGER.info("Registering cable variants for \"{}\"", cableBlock.getId().get());
        simpleBlock(cableBlock.get());
        SFM.LOGGER.info("Registering cable facade variants for \"{}\"", cableFacadeBlock.getId().get());
        simpleBlock(cableFacadeBlock.get(), cubeAll(cableBlock.get()));
        SFM.LOGGER.info("Registering fancy cable variants for \"{}\"", fancyCableBlock.getId().get());
        registerFancyCableVariant(fancyCableBlock, fancyCableFacadeBlock);
    }

    private void registerFancyCableVariant(
            SFMRegistryObject<Block, ?> fancyCableBlock,
            SFMRegistryObject<Block, ?> fancyCableFacadeBlock
    ) {

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

                    faceBuilder.texture("#cable");
                })
                .end()
                .texture("cable", modLoc("block/" + fancy_cable_name));

        var multipartBuilder1 = getMultipartBuilder(fancyCableBlock.get());
        var multipartBuilder2 = getMultipartBuilder(fancyCableFacadeBlock.get());

        // Core
        multipartBuilder1.part()
                .modelFile(coreModel)
                .addModel()
                .end();
        multipartBuilder2.part()
                .modelFile(coreModel)
                .addModel()
                .end();

        // Parts (connections)
        for (Direction direction : SFMDirections.DIRECTIONS_WITHOUT_NULL) {
            var rotX = 0;
            var rotY = 0;

            switch (direction) {
                case SOUTH -> rotY = 180;
                case EAST -> rotY = 90;
                case WEST -> rotY = 270;
                case UP -> rotX = 270;
                case DOWN -> rotX = 90;
            }

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
        }
    }

    private void registerBuffer() {
        getVariantBuilder(SFMBlocks.BUFFER_BLOCK.get())
                .forAllStates(state -> {
                    BufferBlock.ContainedResource containedResource = state.getValue(BufferBlock.CONTAINED_RESOURCE);
                    ModelFile modelFile = models().cubeAll(
                            SFMBlocks.BUFFER_BLOCK.getPath() + "_" + containedResource.getSerializedName(),
                            modLoc("block/buffer_" + containedResource.getSerializedName())
                    );
                    return ConfiguredModel.builder().modelFile(modelFile).build();
                });

    }
}
