package ca.teamdman.sfm.common.net;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.block.CableBlock;
import ca.teamdman.sfm.common.blockentity.CableBlockEntity;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.util.FacadeType;
import ca.teamdman.sfm.common.util.SFMUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public record ServerboundFacadePacket(
        BlockHitResult pHitResult,
        InteractionHand pHand,
        boolean isCtrlKeyDown,
        boolean isAltKeyDown
) implements CustomPacketPayload {
    public static final Type<ServerboundFacadePacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
            SFM.MOD_ID,
            "serverbound_facade_packet"
    ));
    public static final StreamCodec<FriendlyByteBuf, ServerboundFacadePacket> STREAM_CODEC = StreamCodec.ofMember(
            ServerboundFacadePacket::encode,
            ServerboundFacadePacket::decode
    );
    private static final BlockState defaultCableState = SFMBlocks.CABLE_BLOCK.get().defaultBlockState();

    private static void encode(ServerboundFacadePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockHitResult(msg.pHitResult);
        buf.writeEnum(msg.pHand);
        buf.writeBoolean(msg.isCtrlKeyDown);
        buf.writeBoolean(msg.isAltKeyDown);
    }

    private static ServerboundFacadePacket decode(FriendlyByteBuf buf) {
        return new ServerboundFacadePacket(
                buf.readBlockHitResult(),
                buf.readEnum(InteractionHand.class),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    public static void handle(
            ServerboundFacadePacket msg,
            IPayloadContext ctx
    ) {
        // CTRL         :   Connected and Matching
        // ALT          :   Matching
        // CTRL + ALT   :   All
        if (!(ctx.player() instanceof ServerPlayer sender)) {
            return;
        }

        Level level = sender.level();
        BlockPos hitBlockPos = msg.pHitResult.getBlockPos();
        ItemStack itemInHand = sender.getItemInHand(msg.pHand);

        Block newFacadeBlock = itemStackToBlock(
                itemInHand,
                level,
                hitBlockPos
        );
        if (newFacadeBlock == null) return;

        BlockPlaceContext blockPlaceContext = new BlockPlaceContext(sender, msg.pHand, itemInHand, msg.pHitResult);
        BlockState placedFacadeState = Objects.requireNonNullElse(newFacadeBlock.getStateForPlacement(blockPlaceContext), newFacadeBlock.defaultBlockState());

        BlockState hitBlockState = level.getBlockState(hitBlockPos);
        { // Early return for trying to set the same facade to the same block state
            CableBlockEntity hitBlockEntity = (CableBlockEntity) level.getBlockEntity(hitBlockPos);
            BlockState hitFacadeState = hitBlockEntity != null ? hitBlockEntity.getFacadeState() : null;

            if (hitFacadeState == placedFacadeState ||
                    (hitBlockState == defaultCableState && newFacadeBlock == SFMBlocks.CABLE_BLOCK.get())
            ) {
                return;
            }
        }

        Stream<BlockPos> toSetFacade = gatherCableBlocksToFacade(msg, level, hitBlockPos);

        FacadeType facadeProperty = newFacadeBlock == SFMBlocks.CABLE_BLOCK.get() ?
                FacadeType.NONE :
                placedFacadeState.isSolidRender(level, hitBlockPos) ?
                        FacadeType.OPAQUE_FACADE : FacadeType.TRANSLUCENT_FACADE;
        BlockState newBlockState = hitBlockState.setValue(CableBlock.FACADE_TYPE_PROP, facadeProperty);

        toSetFacade
                .forEach(blockPos -> {
                    CableBlockEntity cableBlockEntity = getCableBlockEntity(level, blockPos);
                    if (cableBlockEntity == null) return;

                    if (newFacadeBlock == SFMBlocks.CABLE_BLOCK.get()) {
                        cableBlockEntity.setFacadeState(defaultCableState);
                        level.removeBlockEntity(blockPos);
                    } else {
                        cableBlockEntity.setFacadeState(placedFacadeState);
                    }
                    level.setBlock(blockPos, newBlockState, Block.UPDATE_IMMEDIATE);
                });
    }

    private static Stream<BlockPos> gatherCableBlocksToFacade(
            ServerboundFacadePacket pMsg,
            Level pLevel,
            BlockPos pPos
    ) {
        if (pMsg.isCtrlKeyDown && pMsg.isAltKeyDown) {
            // Return all cable blocks on network

            return filterCableNetwork(pLevel, pPos, (cableBlockPos) -> true);
        } else if (pMsg.isAltKeyDown) {
            // Match blocks with no block entity
            // If block entity exists then check block of facade state

            CableBlockEntity cableBlockEntity = (CableBlockEntity) pLevel.getBlockEntity(pPos);
            if (cableBlockEntity == null) {
                return filterCableNetwork(pLevel, pPos, (cableBlockPos) -> true)
                        .filter(cableBlockPos -> pLevel.getBlockEntity(cableBlockPos) == null);
            }

            Block oldFacadeBlock = cableBlockEntity.getFacadeState().getBlock();
            return filterCableNetwork(pLevel, pPos, (cableBlockPos) -> true)
                    .filter(cableBlockPos -> {
                        CableBlockEntity newCableBlockEntity = (CableBlockEntity) pLevel.getBlockEntity(cableBlockPos);
                        if (newCableBlockEntity == null) return false;
                        return oldFacadeBlock == newCableBlockEntity.getFacadeState().getBlock();
                    });
        } else if (pMsg.isCtrlKeyDown) {
            // Block must be connected to starting block
            // Match blocks with no block entity
            // If block entity exists then check block of facade state

            CableBlockEntity cableBlockEntity = (CableBlockEntity) pLevel.getBlockEntity(pPos);
            if (cableBlockEntity == null) {
                return filterCableNetwork(pLevel, pPos, (neighborPos) -> {
                    CableBlockEntity neighborCableBlockEntity = (CableBlockEntity) pLevel.getBlockEntity(neighborPos);
                    return neighborCableBlockEntity == null;
                });
            }

            Block facadeBlock = cableBlockEntity.getFacadeState().getBlock();
            return filterCableNetwork(pLevel, pPos, (neighborPos) -> {
                CableBlockEntity neighborCableBlockEntity = (CableBlockEntity) pLevel.getBlockEntity(neighborPos);
                if (neighborCableBlockEntity == null) return false;
                return neighborCableBlockEntity.getFacadeState().getBlock() == facadeBlock;
            });
        } else {
            return Stream.of(pPos);
        }
    }

    /**
     * @param pLevel             Level
     * @param pPos               Start position
     * @param filterSurroundings Filter to stop recursion early
     * @return Stream of BlockPos for network
     */
    private static Stream<BlockPos> filterCableNetwork(Level pLevel, BlockPos pPos, Predicate<BlockPos> filterSurroundings) {
        return SFMUtils.getRecursiveStream((current, nextQueue, results) -> {
                    results.accept(current);

                    SFMUtils.get3DNeighboursIncludingKittyCorner(current)
                            .filter(cableBlockPos -> pLevel.getBlockState(cableBlockPos)
                                    .getBlock() == SFMBlocks.CABLE_BLOCK.get())
                            .filter(filterSurroundings)
                            .forEach(nextQueue);
                }, pPos)
                .distinct();
    }

    private static @Nullable Block itemStackToBlock(ItemStack itemStack, Level pLevel, BlockPos pPos) {
        // Empty hand should just return an SFM Cable, lets us delete the block entity
        Item item = itemStack.getItem();
        if (item == Items.AIR)
            return SFMBlocks.CABLE_BLOCK.get();
        // Full block should return block resource, update facade
        Block block = Block.byItem(item);
        BlockState blockState = block.defaultBlockState();

        if (blockState.isCollisionShapeFullBlock(pLevel, pPos)) {
            return block;
        }
        // Non-full block or item should return null, do nothing
        return null;
    }

    private static @Nullable CableBlockEntity getCableBlockEntity(Level pLevel, BlockPos pPos) {
        BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
        // Return existing block entity
        if (blockEntity != null) return (CableBlockEntity) blockEntity;

        // Create new block entity
        CableBlockEntity cableBlockEntity = SFMBlockEntities.CABLE_BLOCK_ENTITY.get().create(pPos, defaultCableState);
        if (cableBlockEntity == null) return null;
        pLevel.setBlockEntity(cableBlockEntity);
        return cableBlockEntity;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
