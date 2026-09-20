package ca.teamdman.sfm.gametest.puppet;

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

/** Compile the pure test-only gates independently; normal JUnit does not load the gametest source set. */
class TouchDisplayTerminalVisualControlTests {
    @TempDir static Path classes;
    private static Path sourceRoot;
    private static URLClassLoader loader;
    private static Class<?> control;

    @BeforeAll static void loadPureControl() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path relative = Path.of("platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest");
        while (root != null && !Files.isDirectory(root.resolve(relative))) root = root.getParent();
        assertNotNull(root);
        sourceRoot = root.resolve(relative);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", classes.toString(),
                sourceRoot.resolve("tests/general/TouchDisplayTerminalVisualControl.java").toString()));
        loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, TouchDisplayTerminalVisualControlTests.class.getClassLoader());
        control = loader.loadClass("ca.teamdman.sfm.gametest.tests.general.TouchDisplayTerminalVisualControl");
    }

    @AfterAll static void closeLoader() throws Exception { if (loader != null) loader.close(); }

    @Test void fileRequestsCannotSkipReadyPressOrAcknowledgement() throws Exception {
        Object instance = fresh();
        rejected(instance, "requestPress");
        rejected(instance, "requestFinish");
        rejected(instance, "cleaned");
        ready(instance, "before");
        rejected(instance, "requestFinish");
        assertEquals("READY", stage(instance));
        var error = assertThrows(InvocationTargetException.class, () -> acknowledged(instance, "after", 1, 1, 0));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }

    @Test void exactlyOneChangedAcknowledgedTouchUnlocksCleanup() throws Exception {
        Object instance = fresh();
        ready(instance, "before");
        call(instance, "requestPress");
        rejected(instance, "requestPress");
        assertThrows(InvocationTargetException.class, () -> acknowledged(instance, "before", 1, 1, 0));
        assertThrows(InvocationTargetException.class, () -> acknowledged(instance, "after", 2, 1, 0));
        assertThrows(InvocationTargetException.class, () -> acknowledged(instance, "after", 1, 1, 1));
        acknowledged(instance, "after", 1, 1, 0);
        assertEquals("ACKNOWLEDGED", stage(instance));
        rejected(instance, "cleaned");
        call(instance, "requestFinish");
        rejected(instance, "requestFinish");
        call(instance, "cleaned");
        assertEquals("CLEANED", stage(instance));
    }

    @Test void abortIsStickyAndDiagnosticIsBounded() throws Exception {
        Object instance = fresh();
        control.getMethod("fail", String.class).invoke(instance, "x".repeat(1000));
        ready(instance, "cannot-resume");
        assertEquals("FAILED", stage(instance));
        Object snapshot = call(instance, "snapshot");
        assertEquals(512, ((String) snapshot.getClass().getMethod("failure").invoke(snapshot)).length());
        rejected(instance, "requestPress");
        rejected(instance, "requestFinish");
    }

    @Test void acknowledgementWaitsForReadyRasterEvenWhenEarlierBaselineDiffers() throws Exception {
        Object instance = fresh();
        String earlierAmbientBaseline = "stage-one-before-cursor-change";
        String visualReadyBaseline = "ready-after-cursor-change";
        ready(instance, visualReadyBaseline);
        call(instance, "requestPress");
        var changed = control.getMethod("hasChangedReadyRaster", String.class);
        assertNotEquals(earlierAmbientBaseline, visualReadyBaseline,
                "The old stage-1 guard would incorrectly treat this current READY frame as changed");
        assertEquals(false, changed.invoke(instance, visualReadyBaseline),
                "A worker ACK arriving before its raster must keep waiting, not call acknowledged and fail");
        assertEquals("READY", stage(instance));
        assertEquals(false, changed.invoke(instance, ""));
        assertEquals(true, changed.invoke(instance, "worker-output-after-ack"));
        acknowledged(instance, "worker-output-after-ack", 1, 1, 0);
        assertEquals("ACKNOWLEDGED", stage(instance));
    }

    @Test void ambientAndVisualRenderPathsRemainDistinct() throws Exception {
        String fixture = Files.readString(sourceRoot.resolve("tests/general/TouchDisplayTerminalIntegrationGameTest.java"));
        assertTrue(fixture.contains("public TouchDisplayTerminalIntegrationGameTest() { visual = null; }"));
        assertTrue(fixture.contains("visual == null ? \"11x3x3\" : \"11x5x7\""));
        assertTrue(fixture.contains("if (visual == null) touch.set(circuit.press());"));
        assertTrue(fixture.contains("visual == null ? digest.equals(initialDigest.get()) : !visual.hasChangedReadyRaster(digest)"),
                "Visual ACK publication must use READY's raster while ambient mode retains its initial baseline");
        assertEquals(3, fixture.split("if \\(visual == null\\) selectFrame", -1).length - 1,
                "Every synthetic render/upload call must remain exclusive to the ambient fixture");
        String action = Files.readString(sourceRoot.resolve("puppet/action/ExploreTouchDisplayTerminalPuppetAction.java"));
        assertTrue(action.contains("runtime.pressTouchDisplayFace"));
        assertTrue(action.contains("runtime.captureWithHud"));
        assertFalse(action.contains("textureForSelectedFrame("));
        assertFalse(action.contains("selectFrame("));
        assertTrue(action.contains("gameDirectory.toPath().toAbsolutePath().normalize()"),
                "The emitted control directory must not depend on the external driver's working directory");
        assertTrue(action.contains(".toAbsolutePath().normalize().resolve(\"screenshots\")"),
                "Screenshot evidence must likewise emit an absolute normalized path");
    }

    private static Object fresh() throws Exception { return control.getConstructor().newInstance(); }
    private static Object call(Object instance, String method) throws Exception { return control.getMethod(method).invoke(instance); }
    private static void rejected(Object instance, String method) {
        var error = assertThrows(InvocationTargetException.class, () -> call(instance, method));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }
    private static String stage(Object instance) throws Exception {
        Object snapshot = call(instance, "snapshot");
        return snapshot.getClass().getMethod("stage").invoke(snapshot).toString();
    }
    private static void ready(Object instance, String digest) throws Exception {
        control.getMethod("ready", String.class, int.class, int.class).invoke(instance, digest, 256, 256);
    }
    private static void acknowledged(Object instance, String digest, long attempted, long acknowledged, long rejected) throws Exception {
        control.getMethod("acknowledged", String.class, int.class, int.class, long.class, long.class, long.class)
                .invoke(instance, digest, 256, 256, attempted, acknowledged, rejected);
    }
}
