package vswe.superfactory.items;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.tiles.TileEntityManager;

import javax.annotation.Nullable;
import java.util.List;

public class ItemMemoryDisk extends Item {
	public ItemMemoryDisk() {
		setCreativeTab(SuperFactoryManager.creativeTab);
		setTranslationKey(SuperFactoryManager.UNLOCALIZED_START + "memorydisk");
		setMaxStackSize(1);
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		tooltip.add(TextFormatting.RED + "WIP");
		if (stack.hasTagCompound() && validateNBT(stack)) {
			if (stack.getTagCompound().hasKey("Author")) {
				tooltip.add("Manager setup authored by:");
				tooltip.add(stack.getTagCompound().getString("Author"));
			} else {
				int x = stack.getTagCompound().getInteger("x");
				int y = stack.getTagCompound().getInteger("y");
				int z = stack.getTagCompound().getInteger("z");
				tooltip.add("Data stored from Manager at:");
				tooltip.add("x: " + x + " y: " + y + " z: " + z);
			}
		}
	}

	@Override
	public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand) {
		ItemStack stack = player.getHeldItem(hand);
		if (!world.isRemote && player.isSneaking()) {
			TileEntity te = world.getTileEntity(pos);
			if (te instanceof TileEntityManager) {
				if (stack.hasTagCompound() && validateNBT(stack)) {
					te.readFromNBT(correctNBT((TileEntityManager) te, stack.getTagCompound()));
					stack.setTagCompound(null);
					return EnumActionResult.PASS;

				} else {
					NBTTagCompound tagCompound = new NBTTagCompound();
					te.writeToNBT(tagCompound);
					tagCompound.setTag("ench", new NBTTagList());
					stack.setTagCompound(tagCompound);
					return EnumActionResult.PASS;
				}
			}
		}
		validateNBT(stack);
		return EnumActionResult.PASS;
	}

	public static boolean validateNBT(ItemStack stack) {
		return stack.hasTagCompound() && (stack.getTagCompound().getString("id").equals("superfactorymanager:manager") || stack.getTagCompound().getString("id").equals("rfmanager"));
		//        stack.setTagCompound(null);
	}

	private static NBTTagCompound correctNBT(TileEntityManager manager, NBTTagCompound tagCompound) {
		tagCompound.setInteger("x", manager.getPos().getX());
		tagCompound.setInteger("y", manager.getPos().getY());
		tagCompound.setInteger("z", manager.getPos().getZ());
		int currentFlow = manager.getFlowItems().size();
		if (currentFlow > 0) {
			byte       version       = tagCompound.getByte("ProtocolVersion");
			NBTTagList components    = tagCompound.getTagList("Components", 10);
			NBTTagList newComponents = new NBTTagList();
			for (int variablesTag = 0; variablesTag < components.tagCount(); ++variablesTag) {
				NBTTagCompound flowComponent  = components.getCompoundTagAt(variablesTag);
				NBTTagList     connections    = flowComponent.getTagList("Connection", 10);
				NBTTagList     newConnections = new NBTTagList();
				for (int i = 0; i < connections.tagCount(); ++i) {
					NBTTagCompound connection = connections.getCompoundTagAt(i);
					if (connection.hasKey("ConnectionComponent")) {
						if (version < 9) {
							connection.setByte("ConnectionComponent", (byte) (connection.getByte("ConnectionComponent") + currentFlow));
						} else {
							connection.setShort("ConnectionComponent", (short) (connection.getShort("ConnectionComponent") + currentFlow));
						}
					}
					newConnections.appendTag(connection);
				}
				flowComponent.setTag("Connection", newConnections);
				newComponents.appendTag(flowComponent);
			}
			tagCompound.setTag("Components", newComponents);
		}
		return tagCompound;
	}
}
