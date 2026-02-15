package vswe.superfactory.blocks;

import javax.annotation.Nullable;

import ca.teamdman.sfm.client.ClientFacadeWarningHelper;
import ca.teamdman.sfm.client.handler.NetworkToolKeyMappingHandler;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.block.IFacadableBlock;
import ca.teamdman.sfm.common.facade.FacadeSpreadLogic;
import ca.teamdman.sfm.common.net.ServerboundFacadePacket;
import ca.teamdman.sfm.common.registry.registration.SFMBlocks;
import ca.teamdman.sfm.common.registry.registration.SFMItems;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import ca.teamdman.sfm.common.block_network.CableNetworkManager;
import ca.teamdman.sfm.common.block_network.ICableBlock;
import org.jetbrains.annotations.NotNull;

public class BlockCable extends Block implements ICableBlock, IFacadableBlock {
    public BlockCable() {
        super(Material.IRON);
        setSoundType(SoundType.METAL);
        setHardness(0.4F);
    }

    @Override
    public boolean onBlockActivated(
            World world,
            BlockPos pos,
            IBlockState state,
            EntityPlayer player,
            EnumHand hand,
            EnumFacing facing,
            float hitX,
            float hitY,
            float hitZ
    ) {
        if (player.getHeldItemOffhand().getItem() == SFMItems.NETWORK_TOOL) {
            if (world.isRemote) {
                ServerboundFacadePacket msg = new ServerboundFacadePacket(
                        pos,
                        facing,
                        FacadeSpreadLogic.fromParts(GuiScreen.isCtrlKeyDown(), GuiScreen.isAltKeyDown()),
                        player.getHeldItemMainhand(),
                        EnumHand.MAIN_HAND
                );
                if (SFMKeyMappings.isKeyDown(SFMKeyMappings.TOGGLE_NETWORK_TOOL_OVERLAY_KEY)) {
                    // we don't want to toggle the overlay if we're using alt-click behaviour
                    NetworkToolKeyMappingHandler.setExternalDebounce();
                }
                ClientFacadeWarningHelper.sendFacadePacketFromClientWithConfirmationIfNecessary(msg);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state) {
        super.onBlockAdded(worldIn, pos, state);
        CableNetworkManager.onCablePlaced(worldIn, pos);
    }


    @Override
    public void onNeighborChange(IBlockAccess world, BlockPos pos, BlockPos neighbor) {
        super.onNeighborChange(world, pos, neighbor);

        bustCaches(world, pos, neighbor);
    }

    public static void onNeighborChange(IBlockAccess world, BlockPos pos) {
        bustCaches(world, pos, null);
    }

    public static void bustCaches(IBlockAccess world, BlockPos pos, @Nullable BlockPos neighborPos) {
        if (world instanceof World w) {
            var network = CableNetworkManager.getOrRegisterNetworkFromCablePosition(w, pos);
            network.ifPresent(nw -> {
                if (neighborPos != null && world.getTileEntity(neighborPos) == null) {
                    nw.bustCapabilityCacheForBlock(neighborPos);
                    nw.updateVisualManagers();
                }
            });
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        super.breakBlock(world, pos, state);
        CableNetworkManager.onCableRemoved(world, pos);
    }


    @Override
    public @NotNull IFacadableBlock getNonFacadeBlock() {
        return SFMBlocks.CABLE;
    }

    @Override
    public @NotNull IFacadableBlock getFacadeBlock() {
        return SFMBlocks.CABLE_CAMOUFLAGE;
    }

    @Override
    public IBlockState getStateForPlacementByFacadePlan(World level, BlockPos pos) {
        return this.getDefaultState();
    }
}
