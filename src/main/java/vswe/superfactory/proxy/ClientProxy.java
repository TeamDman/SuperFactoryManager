package vswe.superfactory.proxy;

import ca.teamdman.sfm.common.registry.SFMBlocks;
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
import vswe.superfactory.registry.ModItems;
import vswe.superfactory.settings.Settings;

@Mod.EventBusSubscriber(modid = SuperFactoryManager.MODID, value = Side.CLIENT)
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

	@SideOnly(Side.CLIENT)
	@SubscribeEvent
	public static void registerRenderers(ModelRegistryEvent event) {

		addVariantNames(SFMBlocks.CABLE_RELAY, "cable_relay", "cable_relay_advanced");
		addVariantNames(SFMBlocks.CABLE_INTAKE, "cable_valve", "cable_valve_instant");
		addVariantNames(SFMBlocks.CABLE_CLUSTER, "cable_cluster", "cable_cluster_advanced");
		addVariantNames(SFMBlocks.CABLE_CAMOUFLAGE, "cable_camouflage", "cable_camouflage_inside", "cable_camouflage_shape");

		registerBlockModel(SFMBlocks.MANAGER);
		registerBlockModel(SFMBlocks.CABLE);
		registerBlockModel(SFMBlocks.CABLE_OUTPUT);
		registerBlockModel(SFMBlocks.CABLE_INPUT);
		//		        registerBlockModel(ModBlocks.CABLE_CREATIVE);
		registerBlockModel(SFMBlocks.CABLE_BUD);
		registerBlockModel(SFMBlocks.CABLE_BREAKER);
		registerBlockModel(SFMBlocks.CABLE_SIGN);
		registerBlockModelForVariant(SFMBlocks.CABLE_RELAY, 0, "cable_relay");
		registerBlockModelForVariant(SFMBlocks.CABLE_RELAY, 8, "cable_relay_advanced");
		registerBlockModelForVariant(SFMBlocks.CABLE_INTAKE, 0, "cable_valve");
		registerBlockModelForVariant(SFMBlocks.CABLE_INTAKE, 8, "cable_valve_instant");
		registerBlockModelForVariant(SFMBlocks.CABLE_CLUSTER, 0, "cable_cluster");
		registerBlockModelForVariant(SFMBlocks.CABLE_CLUSTER, 8, "cable_cluster_advanced");
		registerBlockModelForVariant(SFMBlocks.CABLE_CAMOUFLAGE, 0, "cable_camouflage");
		registerBlockModelForVariant(SFMBlocks.CABLE_CAMOUFLAGE, 1, "cable_camouflage_inside");
		registerBlockModelForVariant(SFMBlocks.CABLE_CAMOUFLAGE, 2, "cable_camouflage_shape");

		registerItemModel(ModItems.DISK, 0, ModItems.DISK.getRegistryName().toString());
	}

	@Override
	public void preInit() {
		Settings.load();
		ModelLoaderRegistry.registerLoader(new ModelLoader());
	}
}
