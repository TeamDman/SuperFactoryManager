package ca.teamdman.sfm.datagen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.WaterTankBlock;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.data.event.GatherDataEvent;

public class SFMBlockStatesAndModels extends BlockStateProvider {
    public SFMBlockStatesAndModels(GatherDataEvent event) {
        super(event.getGenerator().getPackOutput(), SFM.MOD_ID, event.getExistingFileHelper());
    }

    @Override
    protected void registerStatesAndModels() {
        simpleBlock(SFMBlocks.MANAGER_BLOCK.get(), models().cubeBottomTop(
                BuiltInRegistries.BLOCK.getKey(SFMBlocks.MANAGER_BLOCK.get()).getPath(),
                modLoc("block/manager_side"),
                modLoc("block/manager_bot"),
                modLoc("block/manager_top")
        ).texture("particle", "#top"));

        simpleBlock(SFMBlocks.CABLE_BLOCK.get());
        simpleBlock(SFMBlocks.PRINTING_PRESS_BLOCK.get(), models().getExistingFile(modLoc("block/printing_press")));


        ModelFile waterIntakeModelActive = models()
                .cubeAll(
                        BuiltInRegistries.BLOCK.getKey(SFMBlocks.WATER_TANK_BLOCK.get()).getPath() + "_active",
                        modLoc("block/water_intake_active")
                );
        ModelFile waterIntakeModelInactive = models()
                .cubeAll(
                        BuiltInRegistries.BLOCK.getKey(SFMBlocks.WATER_TANK_BLOCK.get()).getPath() + "_inactive",
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
}
