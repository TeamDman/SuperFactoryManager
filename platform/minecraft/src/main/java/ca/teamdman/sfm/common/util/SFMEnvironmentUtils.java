package ca.teamdman.sfm.common.util;

import cpw.mods.modlauncher.Launcher;
import net.minecraftforge.fml.loading.FMLEnvironment;

/// Convenience helpers, also reduces {@link MCVersionDependentBehaviour} in import statements.
public class SFMEnvironmentUtils {

    public static boolean isGameLoaded() {

        return Launcher.INSTANCE != null;
    }

    public static boolean isInIDE() {

        return !FMLEnvironment.production || !isGameLoaded();
    }

    public static boolean isClient() {

        return SFMDist.current().isClient();
    }

}
