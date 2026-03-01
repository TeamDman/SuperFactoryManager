package ca.teamdman.sfm.common.tutorial.chamber;

import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfm.common.localization.SFMTutorialLocalizationKeys;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public class Move1StackDirectTutorialTestChamberDefinition extends SFMTutorialTestChamberDefinition {
    @Override
    public void run(SFMTutorialTestChamberHelper helper) {
        BlockPos managerPos = new BlockPos(3, 0, 1);
        BlockPos rightPos = new BlockPos(2, 0, 1);
        BlockPos leftPos = new BlockPos(4, 0, 1);

        buildExitRoomAndDoor(helper);

        helper.setBlock(managerPos, SFMBlocks.MANAGER.get());
        helper.setBlock(rightPos, SFMBlocks.TEST_BARREL.get());
        helper.setBlock(leftPos, SFMBlocks.TEST_BARREL.get());
        helper.setContainerSlot(leftPos, 0, new ItemStack(Items.DIRT, 64));

        ItemStack programDisk = new ItemStack(SFMItems.DISK.get());
        DiskItem.setProgram(
                programDisk,
                """
                        EVERY 20 TICKS DO
                            INPUT FROM a
                            OUTPUT TO b
                        END
                        """.stripTrailing().stripIndent()
        );
        LabelPositionHolder.empty()
                .add("a", helper.absolutePos(leftPos))
                .add("b", helper.absolutePos(rightPos))
                .save(programDisk);

        helper.placeItemFrameOnWall(new BlockPos(3, 1, 0), Direction.SOUTH, programDisk);
        helper.placeWallSign(
            new BlockPos(2, 1, 0),
            Direction.SOUTH,
            SFMTutorialLocalizationKeys.TUTORIAL_CHAMBER_MOVE_1_STACK_SIGN_PLACE_IN_MANAGER.getComponent()
        );

        BlockState resetButtonState = Blocks.STONE_BUTTON.defaultBlockState()
            .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, AttachFace.WALL)
            .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.NORTH);
        helper.setBlock(new BlockPos(3, 1, 6), resetButtonState);
        helper.placeWallSign(
            new BlockPos(2, 1, 6),
            Direction.NORTH,
            SFMTutorialLocalizationKeys.TUTORIAL_CHAMBER_MOVE_1_STACK_SIGN_RESET.getComponent()
        );
        helper.setCommandBlockCommand(
            new BlockPos(3, 1, 8),
            "sfm tutorial lobby chamber restart"
        );
    }

    private static void buildExitRoomAndDoor(SFMTutorialTestChamberHelper helper) {
        int minX = 8;
        int maxX = 12;
        int minZ = 1;
        int maxZ = 5;
        int floorY = -1;
        int ceilingY = 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = floorY; y <= ceilingY; y++) {
                    boolean boundaryX = x == minX || x == maxX;
                    boolean boundaryZ = z == minZ || z == maxZ;
                    boolean boundaryY = y == floorY || y == ceilingY;
                    boolean shouldPlaceWall = boundaryX || boundaryZ || boundaryY;

                    if (shouldPlaceWall) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.WHITE_CONCRETE);
                    } else {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                    }
                }
            }
        }

        BlockState lowerDoor = Blocks.IRON_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState upperDoor = lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        helper.setBlock(new BlockPos(7, 0, 3), lowerDoor);
        helper.setBlock(new BlockPos(7, 1, 3), upperDoor);

        helper.setBlock(new BlockPos(8, 0, 3), Blocks.AIR);
        helper.setBlock(new BlockPos(8, 1, 3), Blocks.AIR);

        helper.setCommandBlockCommand(
                new BlockPos(10, -1, 3),
            "sfm tutorial lobby chamber succeed @p"
        );
        helper.setBlock(new BlockPos(10, 0, 3), Blocks.STONE_PRESSURE_PLATE);
    }
}