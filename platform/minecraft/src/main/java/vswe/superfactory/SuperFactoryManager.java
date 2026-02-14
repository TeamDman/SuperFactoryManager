package vswe.superfactory;

import ca.teamdman.sfm.common.registry.SFMBlocks;
import ca.teamdman.sfm.common.registry.SFMPackets;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import ca.teamdman.sfm.Tags;
import vswe.superfactory.network.packets.FileHelper;
import vswe.superfactory.network.packets.PacketEventHandler;

public class SuperFactoryManager {
	public static final String              CHANNEL                      = "factorymanager";
	public static final String              MODID                        = Tags.MOD_ID;
	public static final byte                NBT_CURRENT_PROTOCOL_VERSION = 15;
	public static final String              NBT_PROTOCOL_VERSION         = "ProtocolVersion";
	public static final String              RESOURCE_LOCATION            = "superfactorymanager";
	public static final String              UNLOCALIZED_START            = "sfm.";
	public static final CreativeTabs        creativeTab                  = new CreativeTabs("sfm") {
		@Override
		public ItemStack createIcon() {
			return new ItemStack(Item.getItemFromBlock(SFMBlocks.MANAGER_BLOCK));
		}
	};
	public static       SuperFactoryManager instance;

	public void preInit(FMLPreInitializationEvent event) {

		FileHelper.setConfigDir(event.getModConfigurationDirectory());


		FMLInterModComms.sendMessage("Waila", "register", "Provider.callbackRegister");
	}



}
