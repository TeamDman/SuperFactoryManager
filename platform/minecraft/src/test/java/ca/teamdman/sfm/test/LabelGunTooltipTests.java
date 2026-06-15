package ca.teamdman.sfm.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LabelGunTooltipTests {
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path LABEL_GUN_ITEM_PATH = PROJECT_ROOT.resolve(
            "src/main/java/ca/teamdman/sfm/common/item/LabelGunItem.java"
    );

    @Test
    public void clearTooltipUsesClearModifierKeyBinding() throws IOException {
        String source = Files.readString(LABEL_GUN_ITEM_PATH);
        Pattern pattern = Pattern.compile(
                "LABEL_GUN_ITEM_TOOLTIP_CLEAR_REMINDER\\.getComponent\\(\\s*"
                + "SFMKeyMappings\\.getKeyDisplay\\(SFMKeyMappings\\.LABEL_GUN_CLEAR_MODIFIER_KEY\\)",
                Pattern.MULTILINE
        );
        assertTrue(
                pattern.matcher(source).find(),
                "Expected clear reminder tooltip to use LABEL_GUN_CLEAR_MODIFIER_KEY"
        );
    }

    private static Path findProjectRoot() {
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();

        for (Path candidate = workingDirectory; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }

            Path nestedMinecraftProjectRoot = candidate.resolve("platform/minecraft");
            if (Files.isDirectory(nestedMinecraftProjectRoot.resolve("src/main/java"))) {
                return nestedMinecraftProjectRoot;
            }
        }

        throw new IllegalStateException("Could not locate project root containing src/main/java from " + workingDirectory);
    }
}
