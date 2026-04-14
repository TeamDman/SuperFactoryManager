package ca.teamdman.sfm.common.command.draw;

import java.util.List;

public record DrawTemplateProgramCard(
        String templateKey,
        String displayName,
        String programString,
        List<String> detailLines,
        List<String> warningLines,
        List<String> errorLines,
        List<String> astLines
) {
}