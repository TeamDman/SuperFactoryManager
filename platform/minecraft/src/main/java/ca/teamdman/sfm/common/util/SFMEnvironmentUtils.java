package ca.teamdman.sfm.common.util;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;

/// Convenience helpers, also reduces {@link MCVersionDependentBehaviour} in import statements.
public class SFMEnvironmentUtils {

    public static boolean isGameLoaded() {
        return FMLLoader.getCurrentOrNull() != null;
    }

    public static boolean isInIDE() {

        return !FMLEnvironment.isProduction() || !isGameLoaded();
    }

    public static boolean isClient() {

        return SFMDist.current().isClient();
    }

}
