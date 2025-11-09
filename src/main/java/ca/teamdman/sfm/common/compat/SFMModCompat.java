package ca.teamdman.sfm.common.compat;

import net.minecraftforge.fml.common.Loader;

public class SFMModCompat {
    public static boolean isMekanismLoaded() {
        return isModLoaded("mekanism");
    }

    public static boolean isAE2Loaded() {
        return isModLoaded("ae2");
    }

    public static boolean isModLoaded(String modid) {
        return Loader.instance().getIndexedModList().containsKey(modid);
    }

}
