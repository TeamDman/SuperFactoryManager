package vswe.superfactory.proxy;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMItems;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.blocks.client.ModelLoader;
import vswe.superfactory.settings.Settings;

public class ClientProxy extends CommonProxy {
	private static void addVariantNames(Block block, String... names) {
		ResourceLocation[] locations = new ResourceLocation[names.length];
		for (int i = 0; i < names.length; i++) {
			locations[i] = new ResourceLocation(getResource(names[i]));
		}

		if (block != null)
			ModelBakery.registerItemVariants(Item.getItemFromBlock(block), locations);
	}

	private static void registerBlockModelForVariant(Block base, int meta, String variant) {
		registerBlockModel(base, meta, getResource(variant));
	}

	private static void registerBlockModel(Block block) {
		ResourceLocation resourceLocation = Block.REGISTRY.getNameForObject(block);
		registerBlockModel(block, 0, resourceLocation.toString());
	}

	private static void registerBlockModel(Block block, int meta, String modelName) {
		registerItemModel(Item.getItemFromBlock(block), meta, modelName);
	}

	private static void registerItemModel(Item item, int meta, String resourcePath) {
		ModelResourceLocation modelResourceLocation = new ModelResourceLocation(resourcePath, "inventory");
		net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(item, meta, modelResourceLocation);
	}

	public static String getResource(String resource) {
		return (SuperFactoryManager.MODID.toLowerCase() + ":") + resource;
	}



	@Override
	public void preInit() {
		Settings.load();
		ModelLoaderRegistry.registerLoader(new ModelLoader());
	}
}
