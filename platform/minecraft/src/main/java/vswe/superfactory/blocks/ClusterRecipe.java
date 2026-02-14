package vswe.superfactory.blocks;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import vswe.superfactory.registry.ClusterRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ClusterRecipe implements IRecipe {
	private ItemStack output = ItemStack.EMPTY;
	private ResourceLocation registryName;

	public ClusterRecipe(ResourceLocation name) {
		this.registryName = name;
	}

	@Override
	public boolean matches(InventoryCrafting inventorycrafting, World world) {
		ItemStack cluster = ItemStack.EMPTY;
		for (int i = 0; i < inventorycrafting.getSizeInventory(); i++) {
			ItemStack item = inventorycrafting.getStackInSlot(i);

			if (!item.isEmpty() && item.getItem().equals(Item.getItemFromBlock(SFMBlocks.CABLE_CLUSTER))) {
				if (!cluster.isEmpty()) {
					return false; //multiple clusters
				} else {
					cluster = item;
				}
			}
		}

		if (!cluster.isEmpty()) {
			boolean        foundClusterComponent = false;
			List<Integer>  types                 = new ArrayList<>();
			NBTTagCompound compound              = cluster.getTagCompound();
			if (compound != null && compound.hasKey(ItemCluster.NBT_CABLE)) {
				byte[] typeIds = compound.getCompoundTag(ItemCluster.NBT_CABLE).getByteArray(ItemCluster.NBT_TYPES);
				for (byte typeId : typeIds) {
					types.add((int) typeId);
				}
			}

			for (int i = 0; i < inventorycrafting.getSizeInventory(); i++) {
				ItemStack item = inventorycrafting.getStackInSlot(i);

				if (!item.isEmpty() && !item.getItem().equals(Item.getItemFromBlock(SFMBlocks.CABLE_CLUSTER))) {
					boolean validItem = false;
					for (int j = 0; j < ClusterRegistry.getRegistryList().size(); j++) {
						if (item.isItemEqual(ClusterRegistry.getRegistryList().get(j).getItemStack())) {
							if (ClusterRegistry.getRegistryList().get(j).isChainPresentIn(types)) {
								return false; //duplicate item
							}
							types.add(j);
							validItem = true;
							foundClusterComponent = true;
							break;
						}
					}
					if (!validItem) {
						return false; //invalid item
					}
				}
			}

			byte[] typeIds = new byte[types.size()];
			for (int i = 0; i < types.size(); i++) {
				typeIds[i] = (byte) (int) types.get(i);
			}

			if (!foundClusterComponent) {
				return false;
			}

			output = new ItemStack(SFMBlocks.CABLE_CLUSTER, 1, cluster.getItemDamage());
			NBTTagCompound newCompound = new NBTTagCompound();
			output.setTagCompound(newCompound);
			NBTTagCompound subCompound = new NBTTagCompound();
			newCompound.setTag(ItemCluster.NBT_CABLE, subCompound);
			subCompound.setByteArray(ItemCluster.NBT_TYPES, typeIds);
			return true;
		}
		return false;
	}

	@Override
	public ItemStack getCraftingResult(InventoryCrafting inventorycrafting) {
		return output.copy();
	}

	@Override
	public boolean canFit(int width, int height) {
		return false;
	}

	@Override
	public ItemStack getRecipeOutput() {
		return output;
	}

	@Override
	public IRecipe setRegistryName(ResourceLocation name) {
		registryName = name;
		return this;
	}

	@Nullable
	@Override
	public ResourceLocation getRegistryName() {
		return registryName;
	}

	@Override
	public Class<IRecipe> getRegistryType() {
		return IRecipe.class;
	}
}
