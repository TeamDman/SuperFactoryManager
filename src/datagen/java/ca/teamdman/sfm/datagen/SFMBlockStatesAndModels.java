package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.FancyCableBlock;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.data.event.GatherDataEvent;

public class SFMBlockStatesAndModels extends BlockStateProvider {
    public SFMBlockStatesAndModels(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), SFM.MOD_ID, event.getExistingFileHelper());
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlock(SFMBlocks.MANAGER_BLOCK.get(), models().cubeBottomTop(
                SFMBlocks.MANAGER_BLOCK.getId().getPath(),
                modLoc("block/manager_side"),
                modLoc("block/manager_bot"),
                modLoc("block/manager_top")
        ).texture("particle", "#top"));

        simpleBlock(SFMBlocks.CABLE_BLOCK.get());
        simpleBlock(SFMBlocks.PRINTING_PRESS_BLOCK.get(), models().getExistingFile(modLoc("block/printing_press")));
        registerFancyCableBlock();

        ModelFile waterIntakeModelActive = models()
                .cubeAll(
                        SFMBlocks.WATER_TANK_BLOCK.getId().getPath() + "_active",
                        modLoc("block/water_intake_active")
                );
        ModelFile waterIntakeModelInactive = models()
                .cubeAll(
                        SFMBlocks.WATER_TANK_BLOCK.getId().getPath() + "_inactive",
                        modLoc("block/water_intake_inactive")
                );
        getVariantBuilder(SFMBlocks.WATER_TANK_BLOCK.get())
                .forAllStates(state -> ConfiguredModel
                        .builder()
                        .modelFile(
                                state.getValue(WaterTankBlock.IN_WATER)
                                ? waterIntakeModelActive
                                : waterIntakeModelInactive
                        )
                        .build());

        {
            ModelFile barrelModel = models().getExistingFile(mcLoc("block/barrel"));
            ModelFile barrelOpenModel = models().getExistingFile(mcLoc("block/barrel_open"));

            getVariantBuilder(SFMBlocks.TEST_BARREL_BLOCK.get())
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
    }

    private void registerFancyCableBlock() {
        var coreModel = models().withExistingParent(modLoc("block/fancy_cable_core").getPath(), "block/block")
                .element()
                .from(4, 4, 4)
                .to(12, 12, 12)
                .shade(false)
                .allFaces((direction, faceBuilder) -> faceBuilder.uvs(8, 0, 16, 8).texture("#cable"))
                .end()
                .texture("cable", modLoc("block/fancy_cable"))
                .texture("particle", modLoc("block/fancy_cable"));
        var connectionModel = models().withExistingParent(modLoc("block/fancy_cable_connection").getPath(), "block/block")
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
                .texture("cable", modLoc("block/fancy_cable"));

        var multipartBuilder1 = getMultipartBuilder(SFMBlocks.FANCY_CABLE_BLOCK.get());

        // Core
        multipartBuilder1.part()
                .modelFile(coreModel)
                .addModel()
                .end();

        // Parts (connections)
        for (Direction direction: Direction.values()) {
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
        }
    }
}
