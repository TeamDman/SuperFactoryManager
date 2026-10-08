package ca.teamdman.sfm.common.util;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1', '26.1.2' %}
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
{% endcase %}

/// This exists because the import for {@link Dist} is {@link MCVersionDependentBehaviour}
public enum SFMDist {
    CLIENT(Dist.CLIENT),
    DEDICATED_SERVER(Dist.DEDICATED_SERVER);

    public final Dist inner;

    SFMDist(Dist inner) {
        this.inner = inner;
    }

    public static SFMDist current() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return SFMDist.from(FMLEnvironment.dist);
{% when '26.1.2' %}
        return SFMDist.from(FMLEnvironment.getDist());
{% endcase %}
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
