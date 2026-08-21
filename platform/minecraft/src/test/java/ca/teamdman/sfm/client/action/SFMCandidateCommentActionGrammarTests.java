package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCandidateCommentActionGrammarTests {
    @Test
    void candidateCreationGrammarRequiresFocusedFrameTextAndExactGlyphByteRange(@TempDir Path directory) {
        assertExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_ROUTE,
                "action focused comment on the route");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_STEP,
                "action focused comment on the step");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_ACTION,
                "action focused comment on the action");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_STATE,
                "action focused comment on the state");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_GLYPH,
                "action focused 1 7 comment on UTF-8 glyphs");

        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_ROUTE, "action");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_ROUTE, "action focused");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_GLYPH, "action focused");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_GLYPH, "action focused 1");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_GLYPH, "action focused 1 7");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.CREATE_GLYPH,
                "action focused -1 7 invalid negative start");
    }

    @Test
    void mutationNavigationAndPromotionGrammarRemainExplicitAndSelfContained(@TempDir Path directory) {
        String machine = "id(sfm:test/candidate-comment-action)";
        assertExecutable(directory, SFMCandidateCommentAction.Kind.EDIT,
                "action " + machine + " candidate-human-1 replacement text");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.ARCHIVE,
                "action " + machine + " candidate-human-1");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.NAVIGATE,
                "action " + machine + " candidate-human-1");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.PROMOTE_EXACT,
                "action " + machine + " candidate-human-1 decision-1");
        assertExecutable(directory, SFMCandidateCommentAction.Kind.MIGRATE_WITNESSED,
                "action " + machine
                        + " candidate-human-1 7 14 decision-2 human-confirmed correspondence evidence");

        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.EDIT,
                "action " + machine + " candidate-human-1");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.ARCHIVE,
                "action " + machine);
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.NAVIGATE,
                "action " + machine);
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.PROMOTE_EXACT,
                "action " + machine + " candidate-human-1");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.MIGRATE_WITNESSED,
                "action " + machine + " candidate-human-1");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.MIGRATE_WITNESSED,
                "action " + machine + " candidate-human-1 7");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.MIGRATE_WITNESSED,
                "action " + machine + " candidate-human-1 7 14");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.MIGRATE_WITNESSED,
                "action " + machine + " candidate-human-1 7 14 decision-2");
        assertNotExecutable(directory, SFMCandidateCommentAction.Kind.MIGRATE_WITNESSED,
                "action " + machine + " candidate-human-1 -1 14 decision-2 invalid negative start");
    }

    @Test
    void everyActionUsesTheHierarchicalReviewCommentNamespace() {
        for (SFMCandidateCommentAction.Kind kind : SFMCandidateCommentAction.Kind.values()) {
            assertTrue(kind.path().startsWith("review/comment/"), kind.name());
            assertFalse(kind.path().contains("_"), kind.name());
        }
    }

    private static void assertExecutable(
            Path directory,
            SFMCandidateCommentAction.Kind kind,
            String command
    ) {
        assertTrue(executable(parse(directory, kind, command)), kind + ": " + command);
    }

    private static void assertNotExecutable(
            Path directory,
            SFMCandidateCommentAction.Kind kind,
            String command
    ) {
        assertFalse(executable(parse(directory, kind, command)), kind + ": " + command);
    }

    private static ParseResults<SFMClientActionSource> parse(
            Path directory,
            SFMCandidateCommentAction.Kind kind,
            String command
    ) {
        SFMHistoryGraphRuntime historyRuntime = new SFMHistoryGraphRuntime();
        SFMReviewSessionRuntime reviewRuntime = new SFMReviewSessionRuntime(
                historyRuntime,
                machineId -> new SFMReviewSessionStore(directory.resolve(machineId.hashCode() + ".json"))
        );
        SFMCandidateCommentAction action = new SFMCandidateCommentAction(kind, historyRuntime, reviewRuntime);
        LiteralArgumentBuilder<SFMClientActionSource> root = LiteralArgumentBuilder.literal("action");
        action.configureCommandNode(root);
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(root);
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true)
        );
        return dispatcher.parse(command, source);
    }

    private static boolean executable(ParseResults<SFMClientActionSource> parsed) {
        assertNotNull(parsed);
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
