package ca.teamdman.sfm.common.util;

import cpw.mods.modlauncher.Launcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

public class SFMEnvironmentUtils {
    public static boolean isGameLoaded() {
        return Launcher.INSTANCE != null;
    }
    public static boolean isInIDE() {
        return !FMLEnvironment.production;
    }
    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
