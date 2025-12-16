package ca.teamdman.sfm;

import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.CommonProxy;
import ca.teamdman.sfm.common.command.SFMCommand;
import ca.teamdman.sfm.common.program.LimitedInputSlotObjectPool;
import ca.teamdman.sfm.common.program.LimitedOutputSlotObjectPool;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import vswe.superfactory.SuperFactoryManager;
import vswe.superfactory.components.internal.ModItemHelper;
import vswe.superfactory.registry.ModBlocks;

/// Welcome to SFM's source code!
/// I hope you enjoy your visit :D
@Mod(
     modid = SFM.MOD_ID,
     name = SFM.MOD_NAME,
     version = SFM.VERSION,
     dependencies = "required-after:forge@[14.23.5.2847,);")
public class SFM {

    public static final String MOD_ID = Tags.MOD_ID;
    public static final String MOD_NAME = "Super Factory Manager";
    public static final String VERSION = Tags.VERSION;

    public static final String LOCALIZATION_KEY = "sfm";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final String ISSUE_TRACKER_URL = "https://github.com/TeamDman/SuperFactoryManager/issues";

    public static final String RESOURCE_SHORT_ID = "sfm";

    @SuppressWarnings("NotNullFieldNotInitialized") // automatically set by Forge
    @Mod.Instance(SFM.MOD_ID)
    public static SFM instance;

    public final SuperFactoryManager oldMod = new SuperFactoryManager();

    @SuppressWarnings("NotNullFieldNotInitialized")
    @SidedProxy(clientSide = "ca.teamdman.sfm.client.ClientProxy", serverSide = "ca.teamdman.sfm.common.CommonProxy")
    public static CommonProxy proxy;
    private static boolean devEnvCache = false;

    public static boolean isRunningInDevEnvironment() {
        return devEnvCache;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        oldMod.preInit(event);
        proxy.preInit();


        devEnvCache = (Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");

    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
        ModBlocks.addRecipes();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit();
        ModItemHelper.init();
    }

    @Mod.EventHandler
    public void onServerStartup(FMLServerStartingEvent event) {
        proxy.loadComplete();
        event.registerServerCommand(new SFMCommand());
    }

    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        LimitedInputSlotObjectPool.onServerStopped(event);
        LimitedOutputSlotObjectPool.onServerStopped(event);
    }

}
