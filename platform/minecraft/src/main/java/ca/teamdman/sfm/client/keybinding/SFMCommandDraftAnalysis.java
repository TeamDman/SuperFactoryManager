package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.action.SFMClientCommandInsertion;
import ca.teamdman.sfm.client.action.SFMCommandFrontierAnalysis;
import com.mojang.brigadier.ParseResults;
import org.jetbrains.annotations.Nullable;

/** Pure Brigadier-backed classification for a bound command draft. */
public record SFMCommandDraftAnalysis(
        State state,
        String preparedCommand,
        @Nullable MissingParameter missingParameter,
        String diagnostic
) {
    public enum State { COMPLETE, INCOMPLETE, INVALID }

    public static SFMCommandDraftAnalysis analyze(
            String commandDraft,
            SFMClientActionCommandTree tree,
            SFMClientActionSource source
    ) {
        String command = stripSlash(commandDraft).stripLeading();
        String prepared = SFMClientCommandInsertion.prepare(command, tree, source);
        ParseResults<SFMClientActionSource> parsed = tree.parse(prepared, source);
        if (SFMClientActionExecutor.isExecutable(parsed)) {
            return new SFMCommandDraftAnalysis(State.COMPLETE, prepared, null, "Ready to confirm");
        }
        SFMCommandFrontierAnalysis frontier = tree.analyzeFrontier(prepared, parsed);
        MissingParameter missing = frontier.missingParameters().stream()
                .map(parameter -> new MissingParameter(parameter.name(), parameter.displayType()))
                .findFirst()
                .orElse(null);
        // Brigadier records the child argument's expected-input exception at EOF.
        // The parsed parent plus a concrete argument child is still a typed incomplete draft.
        boolean onlyTrailingWhitespaceRemains = !parsed.getReader().canRead()
                || parsed.getReader().getRemaining().isBlank();
        if (missing != null && onlyTrailingWhitespaceRemains) {
            return new SFMCommandDraftAnalysis(State.INCOMPLETE, prepared, missing,
                    "Provide " + missing.name() + " (" + missing.displayType() + ")");
        }
        String diagnostic = frontier.diagnostics().stream()
                .findFirst()
                .orElseGet(() -> parsed.getReader().canRead()
                        ? "Unexpected input at character " + parsed.getReader().getCursor()
                        : "The command is incomplete, but its next input has no typed editor");
        return new SFMCommandDraftAnalysis(State.INVALID, prepared, null, diagnostic);
    }

    private static String stripSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    public record MissingParameter(String name, String displayType) {
    }
}
