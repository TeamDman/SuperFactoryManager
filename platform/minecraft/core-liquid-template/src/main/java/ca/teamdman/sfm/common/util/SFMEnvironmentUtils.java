package ca.teamdman.sfm.common.util;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1' %}
import cpw.mods.modlauncher.Launcher;
import net.minecraftforge.fml.loading.FMLEnvironment;
{% when '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
import cpw.mods.modlauncher.Launcher;
import net.neoforged.fml.loading.FMLEnvironment;
{% when '26.1.2' %}
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
{% endcase %}

/// Convenience helpers, also reduces {@link MCVersionDependentBehaviour} in import statements.
public class SFMEnvironmentUtils {

    public static boolean isGameLoaded() {

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        return Launcher.INSTANCE != null;
{% when '26.1.2' %}
        return FMLLoader.getCurrentOrNull() != null;
{% endcase %}
    }

    public static boolean isInIDE() {
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}

        return !FMLEnvironment.production || !isGameLoaded();
{% when '26.1.2' %}
        try {
            return !FMLEnvironment.isProduction() || !isGameLoaded();
        } catch (IllegalStateException e) {
            if (e.getMessage().equals("There is no current FML Loader")) {
                return false;
            }
        }
        throw new IllegalStateException("Unable to assess if we are in an IDE");
{% endcase %}
    }

    public static boolean isClient() {

        return SFMDist.current().isClient();
    }

}
