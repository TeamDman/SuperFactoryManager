package vswe.superfactory.registry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import vswe.superfactory.tiles.TileEntityCluster;
import vswe.superfactory.tiles.TileEntityClusterElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ClusterRegistry {
	private static HashMap<Class<? extends TileEntityClusterElement>, ClusterRegistryElement> registry     = new HashMap<>();
	private static List<ClusterRegistryElement>                                               registryList = new ArrayList<>();


	public static void register(Class<? extends TileEntityClusterElement> clazz, BlockContainer block) {
		register(new ClusterRegistryElement(clazz, block, new ItemStack(block)));
	}

	public static void register(ClusterRegistryElement registryElement) {
		registryList.add(registryElement);
		ClusterRegistryElement parent = registry.get(registryElement.clazz);
		if (parent == null) {
			registry.put(registryElement.clazz, registryElement);
			registryElement.headSubRegistry = registryElement;
		} else {
			registryElement.headSubRegistry = parent;
			ClusterRegistryElement elem = parent;
			while (elem.nextSubRegistry != null) {
				elem = elem.nextSubRegistry;
			}
			elem.nextSubRegistry = registryElement;
		}
	}

	public static ClusterRegistryElement get(TileEntityClusterElement tileEntityClusterElement) {
		return registry.get(tileEntityClusterElement.getClass());
	}

	public static List<ClusterRegistryElement> getRegistryList() {
		return registryList;
	}

	public static class ClusterRegistryAdvancedSensitive extends ClusterRegistryElement {

		public ClusterRegistryAdvancedSensitive(Class<? extends TileEntityClusterElement> clazz, BlockContainer block, ItemStack itemStack) {
			super(clazz, block, itemStack);
		}

		@Override
		public boolean isValidMeta(int meta) {
			return (itemStack.getItemDamage() & 8) == (meta & 8);
		}
	}

	public static class ClusterRegistryElement {
		protected final BlockContainer                            block;
		protected final Class<? extends TileEntityClusterElement> clazz;
		protected final int                                       id;
		protected final ItemStack                                 itemStack;
		protected       ClusterRegistryElement                    headSubRegistry;
		protected       ClusterRegistryElement                    nextSubRegistry;

		private ClusterRegistryElement(Class<? extends TileEntityClusterElement> clazz, BlockContainer block, ItemStack itemStack) {
			this.clazz = clazz;
			this.block = block;
			this.itemStack = itemStack;
			this.id = registryList.size();
		}

		public int getId() {
			return id;
		}

		public BlockContainer getBlock() {
			return block;
		}

		public ItemStack getItemStack(int meta) {

			ClusterRegistryElement element = this.headSubRegistry;
			while (element != null) {
				if (element.isValidMeta(meta)) {
					return element.getItemStack();
				}
				element = element.nextSubRegistry;
			}
			return getItemStack();
		}

		public ItemStack getItemStack() {
			return itemStack;
		}

		public boolean isValidMeta(int meta) {
			return true;
		}

		public boolean isChainPresentIn(List<Integer> types) {
			ClusterRegistryElement element = this.headSubRegistry;
			while (element != null) {
				if (types.contains(element.id)) {
					return true;
				}
				element = element.nextSubRegistry;
			}

			return false;
		}
	}

	public static class ClusterRegistryMetaSensitive extends ClusterRegistryElement {
		public ClusterRegistryMetaSensitive(Class<? extends TileEntityClusterElement> clazz, BlockContainer block, ItemStack itemStack) {
			super(clazz, block, itemStack);
		}

		@Override
		public boolean isValidMeta(int meta) {
			return itemStack.getItemDamage() == meta;
		}
	}
}
