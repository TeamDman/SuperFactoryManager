package ca.teamdman.sfm;

import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.registry.RegistryBlocks;
import ca.teamdman.sfm.common.registry.RegistryItems;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = SFM.MOD_ID,
        name = SFM.MOD_NAME,
        version = SFM.VERSION,
        dependencies = "required-after:forge@[14.23.5.2860,);"
)
public class SFM {
    public static final String MOD_ID = "sfm";
    public static final String MOD_NAME = "Super Factory Manager";
    public static final String VERSION = "@VERSION@";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final String ISSUE_TRACKER_URL = "https://github.com/TeamDman/SuperFactoryManager/issues";

    @Mod.Instance(modid = SFM.MOD_ID)
	public static       SFM instance;


    @SidedProxy(clientSide = "ca.teamdman.sfm.client.ClientProxy", serverSide = "ca.teamdman.sfm.common.CommonProxy")
    public static CommonProxy proxy;
    private static boolean devEnvCache = false;

    public static boolean isRunningInDevEnvironment() {
        return devEnvCache;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
//        SFMConfig.register();
        proxy.preInit();

        devEnvCache = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
    }
}
