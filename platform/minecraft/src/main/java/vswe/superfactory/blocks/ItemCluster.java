package vswe.superfactory.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import vswe.superfactory.Localization;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.registry.ClusterRegistry;

import javax.annotation.Nullable;
import java.util.List;

public class ItemCluster extends ItemBlock {
	public static final String NBT_CABLE = "Cable";
	public static final String NBT_TYPES = "Types";
	public ItemCluster(Block block) {
		super(block);
		setHasSubtypes(true);
		setMaxDamage(0);
	}

	@Override
	public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
		ItemStack      stack    = player.getHeldItem(hand);
		NBTTagCompound compound = stack.getTagCompound();
		if (compound != null && compound.hasKey(NBT_CABLE)) {
			NBTTagCompound cable = compound.getCompoundTag(NBT_CABLE);
			if (cable.hasKey(NBT_TYPES)) {
				return super.onItemUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
			}
		}

		return EnumActionResult.PASS;
	}

	@Override
	public String getTranslationKey(ItemStack item) {
		return "tile." + SuperFactoryManager.UNLOCALIZED_START + (BlockCableCluster.isAdvanced(item.getItemDamage()) ? "cable_cluster_advanced" : "cable_cluster");
	}

	@Override
	public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
		NBTTagCompound compound = stack.getTagCompound();
		if (compound != null && compound.hasKey(NBT_CABLE)) {
			NBTTagCompound cable = compound.getCompoundTag(NBT_CABLE);
			byte[]         types = cable.getByteArray(ItemCluster.NBT_TYPES);
			for (byte type : types) {
				tooltip.add(ClusterRegistry.getRegistryList().get(type).getItemStack().getDisplayName());
			}
		} else {
			tooltip.add(Localization.EMPTY_CLUSTER.toString());
		}
	}
}
