package ca.teamdman.sfm.common.util;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

public class SFMEnvironmentUtils {

    public static boolean isGameLoaded() {
        return true;
    }

    public static boolean isInIDE() {
        return (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    }

    public static boolean isClient() {
        return FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT;
    }
}
