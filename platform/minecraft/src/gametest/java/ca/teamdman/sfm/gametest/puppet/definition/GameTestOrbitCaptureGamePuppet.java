package ca.teamdman.sfm.gametest.puppet.definition;

import ca.teamdman.sfm.gametest.puppet.SFMGamePuppet;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import ca.teamdman.sfm.properties.SFMProperties;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Captures a completed GameTest from eight stable overview angles.
 *
 * <p>Pass the GameTest through {@code --game-test sfm:<name>} on the puppet command.
 * Purpose-built puppets remain the right choice for interaction and GUI captures.</p>
 */
@SFMGamePuppet
public final class GameTestOrbitCaptureGamePuppet {
    private static final int ORBIT_CAPTURE_COUNT = 8;

    private GameTestOrbitCaptureGamePuppet() {
    }

    public static void run(SFMGamePuppetHelper puppet) {
        String testName = SFMProperties.requiredGamePuppetGameTest().value();
        puppet.createFreshFlatWorld();
        puppet.runGameTest(testName);
        puppet.captureGameTestOrbit(
                "orbit",
                ORBIT_CAPTURE_COUNT,
                Component.literal("Completed ")
                        .append(Component.literal(testName).withStyle(ChatFormatting.GOLD))
                        .append(" orbit overview.")
        );
    }
}
