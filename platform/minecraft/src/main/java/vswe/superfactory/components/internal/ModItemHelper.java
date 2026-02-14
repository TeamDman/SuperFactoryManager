package vswe.superfactory.components.internal;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.RegistryNamespaced;

import java.util.HashMap;
import java.util.Map;

public final class ModItemHelper {
	private static Map<ResourceLocation, String> items;

	private ModItemHelper() {
	}

	public static void init() {
		RegistryNamespaced<ResourceLocation, Item> itemRegistry = Item.REGISTRY;

		items = new HashMap<ResourceLocation, String>();
		Object[] keys = itemRegistry.getKeys().toArray();
		for (Object key : keys) {
			Item             item             = itemRegistry.getObject((ResourceLocation) key);
			ResourceLocation resourceLocation = item.getRegistryName();
			String           modId            = resourceLocation == null ? null : resourceLocation.getNamespace();

			items.put(resourceLocation, modId);
		}
	}

	public static boolean areItemsFromSameMod(Item item1, Item item2) {
		if (item1 == null || item2 == null) {
			return false;
		} else {
			String mod1 = items.get(Item.REGISTRY.getNameForObject(item1));
			String mod2 = items.get(Item.REGISTRY.getNameForObject(item2));

			return mod1 != null && mod1.equals(mod2);
		}
	}
}
