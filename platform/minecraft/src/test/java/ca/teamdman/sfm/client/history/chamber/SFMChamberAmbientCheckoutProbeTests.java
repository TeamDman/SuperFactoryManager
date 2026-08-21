package ca.teamdman.sfm.client.history.chamber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMChamberAmbientCheckoutProbeTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void discoversBranchRootAndMeasuresProtectedPathAndBytesDeterministically() throws IOException {
        Path branch = createBranch("branch");
        Path source = branch.resolve("platform/minecraft/src/main/java/example/A.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "class A {}\n");

        SFMChamberAmbientCheckoutProbe probe = SFMChamberAmbientCheckoutProbe.discover(
                source.getParent()
        );
        String first = probe.get();

        assertEquals(branch.toAbsolutePath().normalize(), probe.branchRoot());
        assertTrue(probe.scopeDescription().contains(branch.toUri().toString()));
        assertEquals(first, probe.get(), "an unchanged checkout must have one stable digest");

        Files.writeString(source, "class A { int value; }\n");
        String changedBytes = probe.get();
        assertNotEquals(first, changedBytes, "protected source bytes must affect the digest");

        Path renamed = source.resolveSibling("B.java");
        Files.move(source, renamed);
        assertNotEquals(changedBytes, probe.get(), "protected relative paths must affect the digest");
    }

    @Test
    void generatedAndRuntimeFilesDoNotContaminateTheProtectedCheckoutMeasurement() throws IOException {
        Path branch = createBranch("branch");
        Path source = branch.resolve("docs/plan.md");
        Files.writeString(source, "protected\n");
        SFMChamberAmbientCheckoutProbe probe = SFMChamberAmbientCheckoutProbe.discover(branch);
        String baseline = probe.get();

        write(branch.resolve("platform/minecraft/build/generated.txt"), "generated\n");
        write(branch.resolve("platform/minecraft/run/options.txt"), "runtime\n");
        write(branch.resolve("platform/cli/sfm-propagate-changes/target/debug/cache.bin"), "cache\n");
        write(branch.resolve("platform/cli/sfm/target/debug/sfm.exe"), "cache\n");
        write(branch.resolve("platform/visual-studio-code/node_modules/example/index.js"), "cache\n");
        write(branch.resolve("logs/latest.log"), "log\n");

        assertEquals(baseline, probe.get(),
                "runtime/build/cache materialization must not make a protected-checkout proof stale");
        assertTrue(SFMChamberAmbientCheckoutProbe.excluded(
                Path.of("platform/cli/sfm-propagate-changes/target")));
        assertTrue(SFMChamberAmbientCheckoutProbe.excluded(
                Path.of("platform/cli/sfm/target")));
        assertTrue(SFMChamberAmbientCheckoutProbe.excluded(
                Path.of("platform/visual-studio-code/node_modules")));
    }

    @Test
    void discoveryFailsClosedOutsideAnSfmBranch() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> SFMChamberAmbientCheckoutProbe.discover(temporaryDirectory)
        );
        assertTrue(failure.getMessage().contains("Could not discover the SFM branch root"));
    }

    private Path createBranch(String name) throws IOException {
        Path branch = temporaryDirectory.resolve(name);
        Files.createDirectories(branch.resolve("platform/minecraft/src"));
        Files.createDirectories(branch.resolve("docs"));
        Files.writeString(branch.resolve("platform/minecraft/sfm-toolchain.lock.json"), "{}\n");
        return branch;
    }

    private static void write(Path path, String contents) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
    }
}
