package ca.teamdman.sfm.common.tutorial.chamber;

import ca.teamdman.sfm.common.tutorial.lobby.LobbyId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class SFMTutorialTestChamberHelper {
    private final ServerLevel level;
    private final BlockPos origin;
    private final LobbyId lobbyId;

    public SFMTutorialTestChamberHelper(ServerLevel level, BlockPos origin, LobbyId lobbyId) {
        this.level = level;
        this.origin = origin;
        this.lobbyId = lobbyId;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getOrigin() {
        return origin;
    }

    public LobbyId getLobbyId() {
        return lobbyId;
    }

    public BlockPos absolutePos(BlockPos relativePos) {
        return origin.offset(relativePos);
    }

    public void setBlock(BlockPos relativePos, Block block) {
        setBlock(relativePos, block.defaultBlockState());
    }

    public void setBlock(BlockPos relativePos, BlockState state) {
        level.setBlock(absolutePos(relativePos), state, 3);
    }

    public @Nullable BlockEntity getBlockEntity(BlockPos relativePos) {
        return level.getBlockEntity(absolutePos(relativePos));
    }

    public ItemFrame placeItemFrameOnWall(
            BlockPos relativePos,
            Direction facing,
            ItemStack displayedItem
    ) {
        ItemFrame frame = new ItemFrame(level, absolutePos(relativePos), facing);
        frame.setItem(displayedItem.copy(), false);
        level.addFreshEntity(frame);
        return frame;
    }

    public void setCommandBlockCommand(BlockPos relativePos, String command) {
        BlockPos absolutePos = absolutePos(relativePos);
        level.setBlockAndUpdate(absolutePos, Blocks.COMMAND_BLOCK.defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(absolutePos);
        if (blockEntity instanceof CommandBlockEntity commandBlockEntity) {
            commandBlockEntity.getCommandBlock().setCommand(command);
            commandBlockEntity.getCommandBlock().setTrackOutput(false);
            commandBlockEntity.setChanged();
            level.sendBlockUpdated(absolutePos, commandBlockEntity.getBlockState(), commandBlockEntity.getBlockState(), 3);
        }
    }

    public void placeWallSign(BlockPos relativePos, Direction facing, Component... text) {
        BlockPos absolutePos = absolutePos(relativePos);
        BlockState signState = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
        level.setBlockAndUpdate(absolutePos, signState);

        BlockEntity blockEntity = level.getBlockEntity(absolutePos);
        if (!(blockEntity instanceof SignBlockEntity signBlockEntity)) {
            return;
        }
        int lines = Math.min(text.length, 4);
        for (int i = 0; i < lines; i++) {
            signBlockEntity.setMessage(i, text[i]);
        }
        signBlockEntity.setChanged();
        level.sendBlockUpdated(absolutePos, signState, signState, 3);
    }

    public void setContainerSlot(BlockPos relativePos, int slot, ItemStack stack) {
        BlockEntity blockEntity = getBlockEntity(relativePos);
        if (!(blockEntity instanceof Container container)) {
            return;
        }
        if (slot < 0 || slot >= container.getContainerSize()) {
            return;
        }
        container.setItem(slot, stack.copy());
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
    }
}