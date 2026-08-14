package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.SFMTitleScreenDevScreen;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMDeveloperActionRemovalTests {
    private static final List<String> REMOVED_ACTION_PATHS = List.of(
            "developer/open_file_explorer",
            "developer/open_instance_file_explorer",
            "developer/open_item_icon_picker"
    );
    private static final List<String> REMOVED_ENUM_CASES = List.of(
            "FILE_EXPLORER",
            "INSTANCE_FILE_EXPLORER",
            "ITEM_ICON_PICKER"
    );

    @Test
    void developerScreenAndActionDeclarationsContainOnlyRetainedSurfaces() {
        List<String> screenCases = Arrays.stream(SFMTitleScreenDevScreen.values()).map(Enum::name).toList();
        List<String> actionFields = Arrays.stream(SFMDeveloperActions.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        List<String> presentationFields = Arrays.stream(OpenTitleScreenDevScreenAction.class.getDeclaredFields())
                .map(Field::getName)
                .toList();

        for (String removedCase : REMOVED_ENUM_CASES) {
            assertFalse(screenCases.contains(removedCase), () -> "Legacy developer screen remains: " + removedCase);
            assertFalse(actionFields.contains(removedCase), () -> "Legacy action field remains: " + removedCase);
            assertTrue(
                    presentationFields.stream().noneMatch(field -> field.startsWith(removedCase)),
                    () -> "Legacy presentation metadata remains: " + removedCase
            );
        }
    }

    @Test
    void productionAndPuppetSourcesDoNotReferenceRemovedActionIds() throws IOException {
        Path projectRoot = locateMinecraftProjectRoot();
        List<Path> sourceRoots = List.of(
                projectRoot.resolve("src/main/java"),
                projectRoot.resolve("src/gametest/java")
        );
        ArrayList<String> violations = new ArrayList<>();

        for (Path sourceRoot : sourceRoots) {
            try (var paths = Files.walk(sourceRoot)) {
                for (Path path : paths.filter(candidate -> candidate.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    for (String removedPath : REMOVED_ACTION_PATHS) {
                        if (source.contains(removedPath)) {
                            violations.add(projectRoot.relativize(path) + " contains " + removedPath);
                        }
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    private static Path locateMinecraftProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("src/main/java"))) return current;
            Path nested = current.resolve("platform/minecraft");
            if (Files.isDirectory(nested.resolve("src/main/java"))) return nested;
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate the Minecraft project root");
    }
}
