package ca.teamdman.sfm.common.util;

import net.minecraft.launchwrapper.Launch;

/// Convenience helpers, also reduces {@link MCVersionDependentBehaviour} in import statements.
public class SFMEnvironmentUtils {

    public static boolean isGameLoaded() {

        return true;
    }

    public static boolean isInIDE() {

        return Launch.blackboard == null || (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    }

    public static boolean isClient() {

        return SFMDist.current().isClient();
    }

}
