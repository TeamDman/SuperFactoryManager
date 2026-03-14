package ca.teamdman.sfm.common.util;

import cpw.mods.modlauncher.Launcher;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/// Convenience helpers, also reduces {@link MCVersionDependentBehaviour} in import statements.
public class SFMEnvironmentUtils {

    public static final Dist SERVER_DIST = Dist.DEDICATED_SERVER;

    public static final Dist CLIENT_DIST = Dist.CLIENT;

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
