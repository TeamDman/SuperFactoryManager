package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.blockentity.TouchDisplayBlockEntity;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.SFMPacketInventoryAddress;
import ca.teamdman.sfm.common.net.SFMPacketInventoryInserter;
import ca.teamdman.sfm.common.registry.registration.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.value.SFMTouchValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** A six-directional, in-world display with server-authoritative press output. */
public class TouchDisplayBlock extends BaseEntityBlock {
    /** The outward face shown to the player; the rear is its opposite. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUCH_DISPLAY_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.TOUCH_DISPLAY.get().getDescriptionId(),
            () -> "Touch Display"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry TOUCH_OUTPUT_UNAVAILABLE = new LocalizationEntry(
            "sfm.touch_display.output_unavailable",
            "Touch Display could not deliver a packet to its rear inventory"
    );

    public TouchDisplayBlock() {
        super(BlockBehaviour.Properties.of(Material.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL));
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return SFMBlockEntities.TOUCH_DISPLAY.get().create(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        // A consuming main-hand result prevents vanilla's offhand fallback.
        // A separately received offhand use never creates another packet.
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return InteractionResult.CONSUME;
        }
        Direction face = state.getValue(FACING);
        Optional<TouchDisplaySurface.UV> uv = TouchDisplaySurface.uvForHit(pos, face, hit);
        if (uv.isEmpty()) {
            return InteractionResult.CONSUME;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
            || !(level.getBlockEntity(pos) instanceof TouchDisplayBlockEntity display)) {
            return InteractionResult.CONSUME;
        }
        if (!hasServerLineOfSight(serverLevel, pos, face, player, hit)) {
            return InteractionResult.CONSUME;
        }

        // Only the hit coordinates come from the client. Content, destination,
        // dimension, and facing are chosen from server-owned world state.
        TouchDisplayBlockEntity.DisplayContent content = display.content();
        SFMPacketInventoryInserter.Result result;
        try {
            var packet = SFMTouchValue.press(
                    level.dimension().location(),
                    pos,
                    face,
                    uv.get().u(),
                    uv.get().v(),
                    content.revision(),
                    content.state()
            );
            var rear = new SFMPacketInventoryAddress(
                    level.dimension().location(),
                    pos.relative(face.getOpposite()),
                    Optional.of(face)
            );
            result = SFMPacketInventoryInserter.insert(serverLevel.getServer(), rear, packet);
        } catch (IllegalArgumentException invalidPacket) {
            // A large state can be valid alone yet exceed the complete touch
            // packet budget. In that case no inventory insertion is attempted.
            result = SFMPacketInventoryInserter.Result.INVENTORY_REJECTED;
        }
        if (result != SFMPacketInventoryInserter.Result.INSERTED) {
            player.displayClientMessage(TOUCH_OUTPUT_UNAVAILABLE.getComponent(), true);
        }
        return InteractionResult.CONSUME;
    }

    /** The vanilla use packet reports a hit but does not re-raycast its pixel. */
    static boolean hasServerLineOfSight(
            ServerLevel level,
            BlockPos pos,
            Direction face,
            Player player,
            BlockHitResult claimedHit
    ) {
        Vec3 eye = player.getEyePosition();
        Vec3 target = claimedHit.getLocation();
        double reach = player.getReachDistance() + 0.5;
        if (!Double.isFinite(reach) || reach <= 0.0
            || !player.canInteractWith(pos, 0.5)
            || eye.distanceToSqr(target) > reach * reach) {
            return false;
        }

        // End slightly inside the target so the server ray hits its front
        // collision face rather than ending exactly on the face boundary.
        Vec3 inside = target.subtract(
                face.getStepX() / 32.0,
                face.getStepY() / 32.0,
                face.getStepZ() / 32.0
        );
        BlockHitResult serverHit = level.clip(new ClipContext(
                eye,
                inside,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
        return serverHit.getType() == HitResult.Type.BLOCK
               && serverHit.getBlockPos().equals(pos)
               && serverHit.getDirection() == face;
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
