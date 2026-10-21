package ca.teamdman.sfm.gametest.tests.compat.justdirethings;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Exercise the pure gametest scheduler without loading Minecraft or a game world. */
class Issue610VisualControlsTests {
    @TempDir static Path classes;
    private static URLClassLoader loader;
    private static Class<?> control;

    @BeforeAll static void loadPureControl() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path relative = Path.of("src/gametest/java/ca/teamdman/sfm/gametest/tests/compat/justdirethings/Issue610VisualControls.java");
        while (root != null && !Files.isRegularFile(root.resolve(relative))) root = root.getParent();
        assertNotNull(root, "Run from the rendered Minecraft project");
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", classes.toString(),
                root.resolve(relative).toString()));
        loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, Issue610VisualControlsTests.class.getClassLoader());
        control = loader.loadClass("ca.teamdman.sfm.gametest.tests.compat.justdirethings.Issue610VisualControls");
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }

    @Test void selectionBoundaryPreventsEarlyPressAndHonorsBoundaryLever() throws Exception {
        Object automatic = fresh(0);
        assertEquals("NONE", tick(automatic, 59, false, false, false, 0, false));
        assertEquals("SELECTING", value(automatic, "mode"));
        assertEquals("PRESS_MANAGER", tick(automatic, 60, false, false, false, 0, false));
        assertEquals("AUTOMATIC", value(automatic, "mode"));
        Object manual = fresh(0);
        assertEquals("NONE", tick(manual, 60, true, false, false, 0, false));
        assertEquals("MANUAL", value(manual, "mode"));
        Object late = fresh(0);
        assertEquals("PRESS_MANAGER", tick(late, 61, true, false, false, 0, false));
        assertEquals("AUTOMATIC", value(late, "mode"));
    }

    @Test void anyEarlyLeverSampleLatchesManualForLongRecordingWaits() throws Exception {
        Object instance = fresh(1000);
        assertEquals("NONE", tick(instance, 1001, true, false, false, 0, false));
        assertEquals("NONE", tick(instance, 1002, false, false, false, 0, false));
        assertEquals("NONE", tick(instance, 1_000_000, false, false, false, 0, false));
        assertEquals("MANUAL", value(instance, "mode"));
        assertEquals("NONE", tick(instance, 1_000_001, false, false, false, 1, false));
        assertEquals("DRAIN", value(instance, "phase"));
        assertEquals("NONE", tick(instance, 2_000_000, false, false, false, 1, true));
        assertEquals("SECOND_RUN", value(instance, "phase"));
        assertEquals("NONE", tick(instance, 3_000_000, false, false, false, 2, true));
        assertEquals("DONE", value(instance, "phase"));
    }

    @Test void automaticSequenceWaitsForActualAcknowledgementsGapsAndButtonRelease() throws Exception {
        Object instance = fresh(0);
        assertEquals("PRESS_MANAGER", tick(instance, 60, false, false, false, 0, false));
        assertEquals("NONE", tick(instance, 61, false, true, false, 0, false));
        assertEquals("FIRST_RUN", value(instance, "phase"));
        assertEquals("NONE", tick(instance, 62, false, true, false, 1, false));
        assertEquals("DRAIN", value(instance, "phase"));
        assertEquals("NONE", tick(instance, 101, false, false, false, 1, false));
        assertEquals("NONE", tick(instance, 102, false, true, false, 1, false));
        assertEquals("NONE", tick(instance, 103, false, false, true, 1, false));
        assertEquals("PRESS_DRAIN", tick(instance, 104, false, false, false, 1, false));
        assertEquals("NONE", tick(instance, 105, false, false, true, 1, false));
        assertEquals("NONE", tick(instance, 106, false, false, true, 1, true));
        assertEquals("SECOND_RUN", value(instance, "phase"));
        assertEquals("NONE", tick(instance, 145, false, false, false, 1, true));
        assertEquals("NONE", tick(instance, 146, false, false, true, 1, true));
        assertEquals("PRESS_MANAGER", tick(instance, 147, false, false, false, 1, true));
        assertEquals("NONE", tick(instance, 148, false, true, false, 1, true));
        assertEquals("NONE", tick(instance, 149, false, true, false, 2, true));
        assertEquals("DONE", value(instance, "phase"));
        assertEquals("NONE", tick(instance, 10_000, false, false, false, 2, true));
    }

    @Test void heldButtonsDelayFirstSyntheticPressWithoutStartingAnActionTimeout() throws Exception {
        Object instance = fresh(0);
        assertEquals("NONE", tick(instance, 60, false, true, false, 0, false));
        assertEquals("NONE", tick(instance, 5000, false, false, true, 0, false));
        assertEquals("PRESS_MANAGER", tick(instance, 5001, false, false, false, 0, false));
    }

    @Test void missingAcknowledgementTimesOutOnceWithoutDuplicatePressOrRetry() throws Exception {
        Object instance = fresh(0);
        assertEquals("PRESS_MANAGER", tick(instance, 60, false, false, false, 0, false));
        assertEquals("NONE", tick(instance, 159, false, false, false, 0, false));
        var first = assertThrows(InvocationTargetException.class, () -> tick(instance, 160, false, false, false, 0, false));
        assertInstanceOf(IllegalStateException.class, first.getCause());
        assertTrue(first.getCause().getMessage().contains("no automatic retry"));
        var later = assertThrows(InvocationTargetException.class, () -> tick(instance, 161, false, false, false, 1, false));
        assertEquals(first.getCause().getMessage(), later.getCause().getMessage());
    }

    @Test void acknowledgementAtTimeoutBoundaryWinsAndNextActionStillWaitsFortyTicks() throws Exception {
        Object instance = fresh(10);
        assertEquals("PRESS_MANAGER", tick(instance, 70, false, false, false, 0, false));
        assertEquals("NONE", tick(instance, 170, false, false, false, 1, false));
        assertEquals("NONE", tick(instance, 209, false, false, false, 1, false));
        assertEquals("PRESS_DRAIN", tick(instance, 210, false, false, false, 1, false));
        assertEquals("NONE", tick(instance, 211, true, false, false, 1, false));
        assertEquals("AUTOMATIC", value(instance, "mode"), "Late lever changes do not switch an active automatic run");
    }

    @Test void acknowledgementBeyondTimeoutCannotSilentlyResumeTheSequence() throws Exception {
        Object instance = fresh(0);
        assertEquals("PRESS_MANAGER", tick(instance, 60, false, false, false, 0, false));
        var late = assertThrows(InvocationTargetException.class, () -> tick(instance, 161, false, false, false, 1, false));
        assertInstanceOf(IllegalStateException.class, late.getCause());
        assertTrue(late.getCause().getMessage().contains("no automatic retry"));
    }

    private static Object fresh(long tick) throws Exception { return control.getConstructor(long.class).newInstance(tick); }
    private static String value(Object instance, String method) throws Exception { return control.getMethod(method).invoke(instance).toString(); }
    private static String tick(Object instance, long tick, boolean lever, boolean run, boolean drain, int count, boolean drained) throws Exception {
        return control.getMethod("tick", long.class, boolean.class, boolean.class, boolean.class, int.class, boolean.class)
                .invoke(instance, tick, lever, run, drain, count, drained).toString();
    }
}
