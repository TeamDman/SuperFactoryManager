package vswe.superfactory;

import static ca.teamdman.sfm.common.registry.SFMBlocks.MANAGER;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.event.FMLInterModComms;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.FMLEventChannel;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import ca.teamdman.sfm.Tags;
import vswe.superfactory.network.messages.MessageHandler;
import vswe.superfactory.network.packets.FileHelper;
import vswe.superfactory.network.packets.PacketEventHandler;

public class SuperFactoryManager {

    public static final String CHANNEL = "factorymanager";
    public static final String MODID = Tags.MOD_ID;
    public static final byte NBT_CURRENT_PROTOCOL_VERSION = 15;
    public static final String NBT_PROTOCOL_VERSION = "ProtocolVersion";
    public static final String RESOURCE_LOCATION = "superfactorymanager";
    public static final String UNLOCALIZED_START = "sfm.";
    public static final CreativeTabs creativeTab = new CreativeTabs("sfm") {

        @Override
        public ItemStack createIcon() {
            return new ItemStack(MANAGER);
        }
    };
    public static SuperFactoryManager instance;
    public static FMLEventChannel packetHandler;

    public void preInit(FMLPreInitializationEvent event) {
        packetHandler = NetworkRegistry.INSTANCE.newEventDrivenChannel(CHANNEL);

        FileHelper.setConfigDir(event.getModConfigurationDirectory());

        // SFM.oldProxy.preInit();

        packetHandler.register(new PacketEventHandler());

        MessageHandler.init();

        FMLInterModComms.sendMessage("Waila", "register", "Provider.callbackRegister");
    }
}
