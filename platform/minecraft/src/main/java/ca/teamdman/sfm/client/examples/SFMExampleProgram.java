package ca.teamdman.sfm.client.examples;

import ca.teamdman.sfm.common.template.SFMDrawTemplate;
import ca.teamdman.sfm.common.template.SFMDrawTemplateRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SFMExampleProgram(
        String displayName,

        String programString
) implements Comparable<SFMExampleProgram> {

    public static List<SFMExampleProgram> gatherAll() {
        return SFMDrawTemplateRegistry.gatherAll().stream()
                .map(template -> new SFMExampleProgram(template.displayName(), template.programString()))
                .sorted()
                .toList();
    }

    public static SFMExampleProgram getChangelog() {
        SFMDrawTemplate changelog = SFMDrawTemplateRegistry.findByName("changelog");
        if (changelog != null) {
            return new SFMExampleProgram(changelog.displayName(), changelog.programString());
        }
        return new SFMExampleProgram(
                "Failed to load changelog",
                "Failed to load changelog"
        );
    }

    @Override
    public int compareTo(@NotNull SFMExampleProgram o) {

        return this.displayName().compareTo(o.displayName());
    }
}
