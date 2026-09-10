package ca.teamdman.sfm.gametest.puppet;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** JUnit does not include the gametest source set; runtime wiring is proved by the puppet. */
class SFMGamePuppetRenderCaptureSourceTests {
    @Test
    void finalAssertionsAreReportedOnceBeforeEitherHoldWithoutPretendingTheViewportWasRestored() throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        Path relative = Path.of("platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet/SFMGamePuppetHarness.java");
        while (cursor != null && !Files.isRegularFile(cursor.resolve(relative))) cursor = cursor.getParent();
        assertNotNull(cursor);
        String source = Files.readString(cursor.resolve(relative));
        int lastExecution = source.indexOf("if (nextPuppetIndex < selectedExecutions.size())");
        int report = source.indexOf("reportCompletion();", lastExecution);
        int retained = source.indexOf("SFM_GAME_PUPPET_VIEWPORT_RETAINED", report);
        int forever = source.indexOf("if (keepOpenSeconds < 0)", retained);
        int countdown = source.indexOf("if (keepOpenSeconds > 0)", forever);
        assertTrue(lastExecution >= 0 && report > lastExecution && retained > report && forever > retained && countdown > forever);
        assertTrue(source.contains("if (completionReported) return;"));
        assertEquals(1, source.split("SFM_GAME_PUPPET_COMPLETE failed=", -1).length - 1);
        assertTrue(source.contains("finalWorldHoldTicksRemaining = keepOpenSeconds * 20L"));
    }

    @Test
    void captureMustWaitForACompletedRenderFrameNotJustAClientTick() throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        Path relative = Path.of("platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet");
        while (cursor != null && !Files.isDirectory(cursor.resolve(relative))) cursor = cursor.getParent();
        assertNotNull(cursor);
        Path root = cursor.resolve(relative);
        String harness = Files.readString(root.resolve("SFMGamePuppetRenderHarness.java"));
        String runtime = Files.readString(root.resolve("SFMGamePuppetMinecraftRuntime.java"));
        assertTrue(harness.contains("event.phase == TickEvent.Phase.END) completedFrames++"));
        int prepare = runtime.indexOf("state.frameBeforePreparation = SFMGamePuppetRenderHarness.completedFrames()");
        int gate = runtime.indexOf("SFMGamePuppetRenderHarness.completedFrames() <= state.frameBeforePreparation");
        int copy = runtime.indexOf("queueCaptionedScreenshot(state)", gate);
        assertTrue(prepare >= 0 && gate > prepare && copy > gate);
    }
}
