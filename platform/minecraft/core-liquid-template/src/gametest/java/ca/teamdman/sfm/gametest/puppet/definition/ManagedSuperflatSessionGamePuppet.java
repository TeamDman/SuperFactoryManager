package ca.teamdman.sfm.gametest.puppet.definition;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% if features.game_puppet_runtime %}
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.network.chat.Component;

@SFMGamePuppet(timeoutTicks = 20 * 8 * 60 * 60)
public final class ManagedSuperflatSessionGamePuppet {
    private ManagedSuperflatSessionGamePuppet() {}
    public static void run(SFMGamePuppetHelper puppet) {
        String test = SFMProperties.requiredGamePuppetGameTest().value();
        puppet.createFreshFlatWorld();
        puppet.runGameTest(test);
        puppet.captureGameTestOrbit("orbit",8,Component.literal("Managed superflat initial test: " + test));
        puppet.manageSession(test);
    }
}
{% endif %}
{% endcase %}
