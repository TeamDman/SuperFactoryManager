package ca.teamdman.sfm.common.block;

import ca.teamdman.sfm.client.ClientFacadeWarningHelper;
import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.cablenetwork.CableNetworkManager;
import ca.teamdman.sfm.common.cablenetwork.ICableBlock;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.net.ServerboundFacadePacket;
import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import ca.teamdman.sfm.common.util.NotStored;
import ca.teamdman.sfm.common.util.Stored;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.World;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class CableBlock extends Block implements ICableBlock, IFacadableBlock {
    public CableBlock() {
        super(Material.PISTON);
    }

    @Override
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state) {
        super.onBlockAdded(worldIn, pos, state);
        CableNetworkManager.onCablePlaced(worldIn, pos);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPlace(
            BlockState state,
            Level world,
            @Stored BlockPos pos,
            BlockState oldState,
            boolean isMoving
    ) {
        // does nothing but keeping for symmetry
        super.onPlace(state, world, pos, oldState, isMoving);

        if (!(oldState.getBlock() instanceof ICableBlock)) {
            CableNetworkManager.onCablePlaced(world, pos);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(
            BlockState state,
            Level level,
            @Stored BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        // purges block entity
        super.onRemove(state, level, pos, newState, isMoving);

        if (!(newState.getBlock() instanceof ICableBlock)) {
            CableNetworkManager.onCableRemoved(level, pos);
        }
    }

    @Override
    public IFacadableBlock getNonFacadeBlock() {
        return SFMBlocks.CABLE_BLOCK.get();
    }

    @Override
    public IFacadableBlock getFacadeBlock() {
        return SFMBlocks.CABLE_FACADE_BLOCK.get();
    }

    @Override
    public BlockState getStateForPlacementByFacadePlan(
            LevelAccessor level,
            @NotStored BlockPos pos
    ) {
        return defaultBlockState();
    }
}
