package vswe.superfactory.blocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.interfaces.IItemBlockProvider;
import vswe.superfactory.tiles.TileEntityCluster;
import vswe.superfactory.tiles.TileEntityClusterElement;
import vswe.superfactory.tiles.TileEntityRelay;

public class BlockCableRelay extends BlockCableDirectionAdvanced implements IItemBlockProvider {
	@Override
	public ItemBlock getItem() {
		return new ItemRelay(this);
	}

	@Override
	public TileEntity createNewTileEntity(World world, int var2) {
		return new TileEntityRelay();
	}

	@Override
	public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase entity, ItemStack item) {
		super.onBlockPlacedBy(world, pos, state, entity, item);

		TileEntityRelay relay = TileEntityCluster.getTileEntity(TileEntityRelay.class, world, pos);
		if (relay != null && isAdvanced(relay.getBlockMetadata())){// && !world.isRemote) {
			relay.setOwner(entity);
			System.out.println(relay + " placed");
//			relay.getPermissions().add(new UserPermission(entity.getUniqueID(),entity.getName()));
		}
	}

	@Override
	protected Class<? extends TileEntityClusterElement> getTeClass() {
		return TileEntityRelay.class;
	}

	@Override
	public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		TileEntityRelay relay = TileEntityCluster.getTileEntity(TileEntityRelay.class, world, pos);
		if (relay != null && isAdvanced(relay.getBlockMetadata())) {
			if (!world.isRemote) {
				FMLNetworkHandler.openGui(player, SuperFactoryManager.instance, 0, world, pos.getX(), pos.getY(), pos.getZ());
			}
			return true;
		} else {
			return false;
		}
	}
}
