package ca.teamdman.sfm.common.util;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/// This exists because the import for {@link Dist} is {@link MCVersionDependentBehaviour}
public enum SFMDist {
    CLIENT(Dist.CLIENT),
    DEDICATED_SERVER(Dist.DEDICATED_SERVER);

    public final Dist inner;

    SFMDist(Dist inner) {
        this.inner = inner;
    }

    public static SFMDist current() {
        return SFMDist.from(FMLEnvironment.dist);
    }

    public static SFMDist from(Dist dist) {
        return switch(dist) {
            case CLIENT -> CLIENT;
            case DEDICATED_SERVER -> DEDICATED_SERVER;
        };
    }

    public boolean isClient() {
        return inner == Dist.CLIENT;
    }
}
