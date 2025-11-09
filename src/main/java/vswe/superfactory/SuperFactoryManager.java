package vswe.superfactory;

import ca.teamdman.sfm.SFM;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import vswe.superfactory.components.internal.ModItemHelper;
import vswe.superfactory.network.messages.MessageHandler;
import vswe.superfactory.network.packets.FileHelper;
import vswe.superfactory.network.packets.PacketEventHandler;
import vswe.superfactory.registry.ModBlocks;

import static ca.teamdman.sfm.common.registry.SFMBlocks.MANAGER;

public class SuperFactoryManager {
	public static final String              CHANNEL                      = "factorymanager";
	public static final String              MODID                        = "superfactorymanager";
	public static final byte                NBT_CURRENT_PROTOCOL_VERSION = 14;
	public static final String              NBT_PROTOCOL_VERSION         = "ProtocolVersion";
	public static final String              RESOURCE_LOCATION            = "superfactorymanager";
	public static final String              UNLOCALIZED_START            = "sfm.";
	public static final CreativeTabs        creativeTab                  = new CreativeTabs("sfm") {
		@Override
		public ItemStack createIcon() {
			return new ItemStack(MANAGER);
		}
	};
	@Mod.Instance(MODID)
	public static       SuperFactoryManager instance;
	public static       FMLEventChannel     packetHandler;

    @Mod.EventHandler
	public void preInit(FMLPreInitializationEvent event) {
		packetHandler = NetworkRegistry.INSTANCE.newEventDrivenChannel(CHANNEL);

		FileHelper.setConfigDir(event.getModConfigurationDirectory());

		SFM.oldProxy.preInit();

		packetHandler.register(new PacketEventHandler());

		MessageHandler.init();
		NetworkRegistry.INSTANCE.registerGuiHandler(this, new GuiHandler());

		FMLInterModComms.sendMessage("Waila", "register", "Provider.callbackRegister");
	}

	@Mod.EventHandler
	public void init(FMLInitializationEvent event) {
		ModBlocks.addRecipes();
	}

	@Mod.EventHandler
	public void postInit(FMLPostInitializationEvent event) {
		ModItemHelper.init();
	}


}
