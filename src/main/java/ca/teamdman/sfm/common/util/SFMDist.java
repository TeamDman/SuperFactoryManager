package ca.teamdman.sfm.common.util;


import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/// This exists because the import for {@link Dist} is {@link MCVersionDependentBehaviour}
public enum SFMDist {
    CLIENT(Side.CLIENT),
    DEDICATED_SERVER(Side.SERVER);

    public final Side inner;

    SFMDist(Side inner) {
        this.inner = inner;
    }

    public static SFMDist current() {
        return SFMDist.from(FMLCommonHandler.instance().getEffectiveSide());
    }

    public static SFMDist from(Side dist) {
        return switch(dist) {
            case CLIENT -> CLIENT;
            case SERVER -> DEDICATED_SERVER;
        };
    }

    public boolean isClient() {
        return inner == Side.CLIENT;
    }
}
