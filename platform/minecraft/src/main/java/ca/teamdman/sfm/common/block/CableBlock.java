package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.client.ClientFacadeWarningHelper;
import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.client.screen.SFMWidgetUtils;
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.ICableBlock;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundFacadePacket;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CableBlock extends Block implements ICableBlock, IFacadableBlock {
    @SFMLocalizationDatagen
    public static final LocalizationEntry CABLE_BLOCK = new LocalizationEntry(
            () -> SFMBlocks.CABLE.get().getDescriptionId(),
            () -> "Inventory Cable"
    );

    public CableBlock(Properties properties) {

        super(properties);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(
            BlockState state,
            Level world,
            BlockPos pos,
            BlockState oldState,
            boolean isMoving
    ) {
        // does nothing but keeping for symmetry
        super.onPlace(state, world, pos, oldState, isMoving);

        if (!(oldState.getBlock() instanceof ICableBlock)) {
            CableNetworkManager.onCablePlaced(world, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);

//        if (!(state.getBlock() instanceof ICableBlock)) {
            CableNetworkManager.onCableRemoved(level, pos);
//        }
    }

    @Override
    protected InteractionResult useItemOn(
            ItemStack pStack,
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
            BlockHitResult pHitResult
    ) {

        if (pPlayer.getOffhandItem().getItem() == SFMItems.NETWORK_TOOL.get()) {
            if (pLevel.isClientSide() && pHand == InteractionHand.MAIN_HAND) {
                ServerboundFacadePacket msg = new ServerboundFacadePacket(
                        pHitResult,
                        FacadeSpreadLogic.fromParts(SFMWidgetUtils.hasCtrlDown(), SFMWidgetUtils.hasAltDown()),
                        pPlayer.getMainHandItem(),
                        InteractionHand.MAIN_HAND
                );
                if (SFMKeyMappings.isKeyDown(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY)) {
                    // we don't want to toggle the overlay if we're using alt-click behaviour
                    NetworkToolKeyMappingHandler.setExternalDebounce();
                }
                ClientFacadeWarningHelper.sendFacadePacketFromClientWithConfirmationIfNecessary(msg);
                return InteractionResult.CONSUME;
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {

        return SFMBlocks.CABLE.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {

        return SFMBlocks.CABLE_FACADE.get();
    }

    @Override
    public BlockState getStateForPlacementByFacadePlan(
            LevelAccessor level,
            BlockPos pos
    ) {

        return defaultBlockState();
    }

}
