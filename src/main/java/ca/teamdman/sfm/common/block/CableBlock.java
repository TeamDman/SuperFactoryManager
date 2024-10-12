package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import ca.teamdman.sfm.common.net.ServerboundFacadePacket;
import ca.teamdman.sfm.common.registry.SFMBlockEntities;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.FacadeType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;


public class CableBlock extends Block implements ICableBlock, EntityBlock {
    public static final ModelProperty<BlockState> FACADE_BLOCK_STATE = new ModelProperty<>();
    public static final EnumProperty<FacadeType> FACADE_TYPE_PROP = FacadeType.FACADE_TYPE;

    public CableBlock() {
        super(Properties.of()
                .instrument(NoteBlockInstrument.BASS)
                .destroyTime(1f)
                .sound(SoundType.METAL)
        );

        registerDefaultState(stateDefinition.any()
                .setValue(FACADE_TYPE_PROP, FacadeType.NONE)
        );
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(BlockState state, Level world, BlockPos pos, BlockState oldState, boolean isMoving) {
        CableNetworkManager.onCablePlaced(world, pos);
    }

/*
    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        if (pLevel.isClientSide() || pPlacer == null) return;
        if (pPlacer instanceof Player pPlayer) {
            ItemStack offHandItemStack = pPlacer.getItemInHand(InteractionHand.OFF_HAND);

            setFacade(offHandItemStack, pLevel, pState, pPos, pPlayer, InteractionHand.OFF_HAND, null);
        }
    }
*/

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);

        if (!(pNewState.getBlock() instanceof ICableBlock))
            CableNetworkManager.onCableRemoved(pLevel, pPos);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack pStack, BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHitResult) {
        Item offHandItem = pPlayer.getItemInHand(InteractionHand.OFF_HAND).getItem();
        if (offHandItem == SFMItems.NETWORK_TOOL_ITEM.get()) {
            return setFacade(pLevel, pHand, pHitResult);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private ItemInteractionResult setFacade(
            Level pLevel,
            InteractionHand pHand,
            @Nullable BlockHitResult pHitResult
    ) {
        if (pLevel.isClientSide) {
            PacketDistributor.sendToServer(new ServerboundFacadePacket(
                    pHitResult, pHand, Screen.hasControlDown(), Screen.hasAltDown()
            ));
            return ItemInteractionResult.SUCCESS;

        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> pBuilder) {
        pBuilder.add(FACADE_TYPE_PROP);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        // Create block entity if FACADE_TYPE is not NONE
        return blockState.getValue(FACADE_TYPE_PROP) != FacadeType.NONE ?
                SFMBlockEntities.CABLE_BLOCK_ENTITY.get().create(blockPos, blockState) :
                null;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        // Translucent blocks should have no occlusion
        return pState.getValue(FACADE_TYPE_PROP) == FacadeType.TRANSLUCENT_FACADE ?
                Shapes.empty() :
                Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState pState, BlockGetter pLevel, BlockPos pPos) {
        return pState.getValue(FACADE_TYPE_PROP) == FacadeType.TRANSLUCENT_FACADE;
    }
}
