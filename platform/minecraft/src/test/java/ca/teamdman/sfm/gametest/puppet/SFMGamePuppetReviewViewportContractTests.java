package ca.teamdman.sfm.gametest.puppet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Compile the pure gametest viewport contract: JUnit does not load the gametest source set. */
class SFMGamePuppetReviewViewportContractTests {
    @TempDir Path classes;

    @Test
    void reviewSquareWitnessIsExplicitAndDoesNotBroadenOtherPuppets() throws Exception {
        Path source = gameTestPuppetSourceRoot();
        assertNotNull(source);
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The test runtime must use the configured JDK");
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-proc:none", "-d", classes.toString(),
                source.resolve("SFMGamePuppetViewportVariant.java").toString(),
                source.resolve("SFMGamePuppetViewportProfile.java").toString(),
                source.resolve("SFMGamePuppetViewportSelection.java").toString()));
        try (var loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()}, getClass().getClassLoader())) {
            String prefix = "ca.teamdman.sfm.gametest.puppet.";
            Class<?> profile = loader.loadClass(prefix + "SFMGamePuppetViewportProfile");
            Class<?> selection = loader.loadClass(prefix + "SFMGamePuppetViewportSelection");
            Object review = profile.getField("REVIEW_READINESS").get(null);
            Object common = profile.getField("COMMON_RESPONSIVE").get(null);
            var parse = selection.getMethod("parse", String.class);
            var resolve = selection.getMethod("resolve", profile, int.class, int.class);
            for (String witness : List.of("1920x1080@1", "1920x1080@2", "1920x1080@3",
                    "1920x1080@4", "1920x1080@auto", "2000x2000@4")) {
                List<?> variants = (List<?>) resolve.invoke(parse.invoke(null, witness), review, 1280, 720);
                assertEquals(1, variants.size());
                assertEquals(witness, variants.get(0).getClass().getMethod("id").invoke(variants.get(0)));
            }
            assertEquals(4, ((List<?>) resolve.invoke(parse.invoke(null, "declared"), review, 1280, 720)).size(),
                    "The square witness must not silently enlarge every declared run");
            var commonFailure = assertThrows(InvocationTargetException.class,
                    () -> resolve.invoke(parse.invoke(null, "2000x2000@4"), common, 1280, 720));
            assertInstanceOf(IllegalArgumentException.class, commonFailure.getCause());
            var unlistedFailure = assertThrows(InvocationTargetException.class,
                    () -> resolve.invoke(parse.invoke(null, "2048x2048@4"), review, 1280, 720));
            assertInstanceOf(IllegalArgumentException.class, unlistedFailure.getCause());
        }
        assertTrue(Files.readString(source.resolve("definition/TitleScreenExploratoryReviewGamePuppet.java"))
                .contains("viewportProfile = SFMGamePuppetViewportProfile.REVIEW_READINESS"));
    }

    private static Path gameTestPuppetSourceRoot() {
        Path cursor = Path.of("").toAbsolutePath();
        Path direct = Path.of("src/gametest/java/ca/teamdman/sfm/gametest/puppet");
        Path nested = Path.of("platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet");
        while (cursor != null) {
            if (Files.isDirectory(cursor.resolve(direct))) return cursor.resolve(direct);
            if (Files.isDirectory(cursor.resolve(nested))) return cursor.resolve(nested);
            cursor = cursor.getParent();
        }
        return null;
    }
}
