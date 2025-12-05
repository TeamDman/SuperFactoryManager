package ca.teamdman.sfm.common.util;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/// Convenience helpers, also reduces {@link MCVersionDependentBehaviour} in import statements.
public class SFMEnvironmentUtils {

    public static final Side SERVER_DIST = Side.SERVER;

    public static final Side CLIENT_DIST = Side.CLIENT;

    public static boolean isGameLoaded() {

        return true;
    }

    public static boolean isInIDE() {

        return (boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment");
    }

    public static boolean isClient() {

        return FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT;
    }

    public static Side getCurrentSide() {
        return FMLCommonHandler.instance().getEffectiveSide();
    }
}
