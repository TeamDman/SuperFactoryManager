package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;

import java.util.List;

/** Asserts paste effects without writing terminal or clipboard text into evidence. */
public record AssertTerminalPrivateContentPuppetAction(
        String artifactName,
        List<String> requiredExactLines,
        List<String> forbiddenExactLines
) implements SFMPuppetAction {
    @Override
    public String description() {
        return "assert privacy-safe terminal content predicates";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        boolean required = requiredExactLines.stream().allMatch(runtime::terminalContentHasExactLine);
        boolean forbidden = forbiddenExactLines.stream().noneMatch(runtime::terminalContentHasExactLine);
        if (!required) return false;
        if (!forbidden) {
            throw new IllegalStateException("Terminal contained a forbidden exact line after guarded paste");
        }
        String evidence = "schema=sfm-terminal-private-content-puppet-v1\n"
                + "required_exact_line_count=" + requiredExactLines.size() + "\n"
                + "forbidden_exact_line_count=" + forbiddenExactLines.size() + "\n"
                + "required_exact_lines_present=true\n"
                + "forbidden_exact_lines_absent=true\n"
                + "terminal_content_retained_in_artifact=false\n"
                + "clipboard_body_retained_in_artifact=false\n";
        runtime.writeTerminalInteractionEvidence(artifactName, evidence);
        return true;
    }
}
