package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.client.ClientFacadeWarningHelper;
import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import ca.teamdman.sfm.client.screen.SFMWidgetUtils;
{% endcase %}
import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.ICableBlock;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.net.ServerboundFacadePacket;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import net.minecraft.client.gui.screens.Screen;
{% when '26.1.2' %}
{% endcase %}
import net.minecraft.core.BlockPos;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.server.level.ServerLevel;
{% endcase %}
import net.minecraft.world.InteractionHand;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
import net.minecraft.world.InteractionResult;
{% when '1.21', '1.21.1' %}
import net.minecraft.world.ItemInteractionResult;
{% endcase %}
import net.minecraft.world.entity.player.Player;
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
{% when '1.21', '1.21.1', '26.1.2' %}
import net.minecraft.world.item.ItemStack;
{% endcase %}
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

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    @SuppressWarnings("deprecation")
{% when '26.1.2' %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        // purges block entity
        super.onRemove(state, level, pos, newState, isMoving);
{% when '26.1.2' %}
    protected void affectNeighborsAfterRemoval(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            boolean movedByPiston
    ) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
{% endcase %}

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        if (!(newState.getBlock() instanceof ICableBlock)) {
{% when '26.1.2' %}
//        if (!(state.getBlock() instanceof ICableBlock)) {
{% endcase %}
            CableNetworkManager.onCableRemoved(level, pos);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        }
{% when '26.1.2' %}
//        }
{% endcase %}
    }

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    @SuppressWarnings("deprecation")
{% when '1.21', '1.21.1', '26.1.2' %}
{% endcase %}
    @Override
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
    public InteractionResult use(
{% when '1.21', '1.21.1' %}
    protected ItemInteractionResult useItemOn(
            ItemStack pStack,
{% when '26.1.2' %}
    protected InteractionResult useItemOn(
            ItemStack pStack,
{% endcase %}
            BlockState pState,
            Level pLevel,
            BlockPos pPos,
            Player pPlayer,
            InteractionHand pHand,
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
            BlockHitResult pHit
{% when '1.21', '1.21.1', '26.1.2' %}
            BlockHitResult pHitResult
{% endcase %}
    ) {

        if (pPlayer.getOffhandItem().getItem() == SFMItems.NETWORK_TOOL.get()) {
            if (pLevel.isClientSide() && pHand == InteractionHand.MAIN_HAND) {
                ServerboundFacadePacket msg = new ServerboundFacadePacket(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4' %}
                        pHit,
                        FacadeSpreadLogic.fromParts(Screen.hasControlDown(), Screen.hasAltDown()),
{% when '1.21', '1.21.1' %}
                        pHitResult,
                        FacadeSpreadLogic.fromParts(Screen.hasControlDown(), Screen.hasAltDown()),
{% when '26.1.2' %}
                        pHitResult,
                        FacadeSpreadLogic.fromParts(SFMWidgetUtils.hasCtrlDown(), SFMWidgetUtils.hasAltDown()),
{% endcase %}
                        pPlayer.getMainHandItem(),
                        InteractionHand.MAIN_HAND
                );
                if (SFMKeyMappings.isKeyDown(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY)) {
                    // we don't want to toggle the overlay if we're using alt-click behaviour
                    NetworkToolKeyMappingHandler.setExternalDebounce();
                }
                ClientFacadeWarningHelper.sendFacadePacketFromClientWithConfirmationIfNecessary(msg);
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
                return InteractionResult.CONSUME;
{% when '1.21', '1.21.1' %}
                return ItemInteractionResult.CONSUME;
{% endcase %}
            }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
            return InteractionResult.SUCCESS;
{% when '1.21', '1.21.1' %}
            return ItemInteractionResult.SUCCESS;
{% endcase %}
        }
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '26.1.2' %}
        return InteractionResult.PASS;
{% when '1.21', '1.21.1' %}
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
{% endcase %}
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
