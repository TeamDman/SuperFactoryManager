package ca.teamdman.sfm.gametest.puppet;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMGamePuppetVirtualPointerSourceTests {
    @Test
    void puppetSourcesCannotWarpOrInjectTheOperatingSystemCursor() throws Exception {
        Path root = projectRoot().resolve("src/gametest/java");
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path path : files.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                reject(path, source, "glfwSetCursorPos", violations);
                reject(path, source, "SetCursorPos", violations);
                reject(path, source, "WINDOWS_MESSAGE_MOUSE_MOVE", violations);
                reject(path, source, "PostMessage(", violations);
            }
        }
        assertTrue(violations.isEmpty(),
                "puppet input must use the virtual Minecraft callback seam: " + violations);
    }

    @Test
    void virtualPointerUsesMinecraftMouseCallbackRatherThanDirectCacheMutation() throws Exception {
        Path project = projectRoot();
        String pointer = Files.readString(project.resolve(
                "src/gametest/java/ca/teamdman/sfm/gametest/puppet/SFMGamePuppetPointer.java"));
        String invoker = Files.readString(project.resolve(
                "src/main/java/ca/teamdman/sfm/mixins/MouseHandlerInvoker.java"));
        String mixins = Files.readString(project.resolve("src/main/resources/sfm.mixins.json"));

        assertTrue(pointer.contains("sfm$invokeOnMove"));
        assertTrue(pointer.contains("sfm$invokeOnPress"));
        assertTrue(pointer.contains("sfm$invokeOnScroll"));
        assertTrue(pointer.contains("moveVirtual"));
        assertTrue(pointer.contains("clickVirtual"));
        assertTrue(invoker.contains("@Invoker(\"onMove\")"));
        assertTrue(invoker.contains("@Invoker(\"onPress\")"));
        assertTrue(invoker.contains("@Invoker(\"onScroll\")"));
        assertTrue(mixins.contains("\"MouseHandlerInvoker\""));
    }

    private static void reject(
            Path path,
            String source,
            String forbidden,
            List<String> violations
    ) {
        if (source.contains(forbidden)) {
            violations.add(projectRoot().relativize(path).toString() + " contains " + forbidden);
        }
    }

    private static Path projectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/gametest/java"))) return current;
            Path nested = current.resolve("platform/minecraft");
            if (Files.isDirectory(nested.resolve("src/gametest/java"))) return nested;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the Minecraft project root");
    }
}
