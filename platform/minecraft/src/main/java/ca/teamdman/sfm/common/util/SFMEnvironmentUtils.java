package ca.teamdman.sfm.common.util;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;

/// Convenience helpers, also reduces {@link MCVersionDependentBehaviour} in import statements.
public class SFMEnvironmentUtils {

    public static boolean isGameLoaded() {
        return FMLLoader.getCurrentOrNull() != null;
    }

    public static boolean isInIDE() {
        try {
            return !FMLEnvironment.isProduction() || !isGameLoaded();
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("There is no current FML Loader")) {
                return false;
            }
        }
        throw new IllegalStateException("Unable to assess if we are in an IDE");
    }

    public static boolean isClient() {

        return SFMDist.current().isClient();
    }

}
