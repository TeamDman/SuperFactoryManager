package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewCommentChoiceActionTests {
    @Test
    void choiceActionsHaveUniqueHierarchicalPathsAndAreRegistered() throws Exception {
        assertEquals(SFMReleaseReviewCommentChoiceAction.Kind.values().length,
                new HashSet<>(Arrays.stream(SFMReleaseReviewCommentChoiceAction.Kind.values())
                        .map(SFMReleaseReviewCommentChoiceAction.Kind::path).toList()).size());
        for (String field : new String[]{
                "RELEASE_COMMENT_CHOICE_OPEN",
                "RELEASE_COMMENT_CHOICE_APPLY",
                "RELEASE_COMMENT_CHOICE_OTHER",
                "RELEASE_COMMENT_CHOICE_CANCEL",
                "RELEASE_COMMENT_CHOICE_REOPEN_WRITABLE"
        }) {
            assertNotNull(SFMReviewActions.class.getDeclaredField(field));
        }
    }

    @Test
    void exactCommentTextIsOneQuotedArgumentInsteadOfAmbientRecapture() {
        String draft = "review-comment-draft-17";
        String text = "#approved Unicode café and spaces";
        assertTrue(executable(
                SFMReleaseReviewCommentChoiceAction.Kind.APPLY,
                "action " + draft + " " + StringArgumentType.escapeIfRequired(text)
        ));
        assertFalse(executable(SFMReleaseReviewCommentChoiceAction.Kind.APPLY, "action " + draft));
        assertTrue(executable(SFMReleaseReviewCommentChoiceAction.Kind.OPEN, "action " + draft));
        assertTrue(executable(SFMReleaseReviewCommentChoiceAction.Kind.OTHER, "action " + draft));
        assertTrue(executable(SFMReleaseReviewCommentChoiceAction.Kind.CANCEL, "action " + draft));
        assertTrue(executable(SFMReleaseReviewCommentChoiceAction.Kind.REOPEN_WRITABLE, "action " + draft));
    }

    @Test
    void writableChoiceSurfaceContainsEveryBoundedCommentRouteWithoutDuplicatingBuiltIns() {
        List<SFMActionChoice> choices = SFMReleaseReviewCommentChoiceAction.choices(
                "review-comment-draft-17",
                true,
                List.of(
                        SFMReleaseReviewCommentChoiceAction.APPROVED,
                        "#reviewed Earlier custom note",
                        SFMReleaseReviewCommentChoiceAction.NEEDS_CHANGE
                )
        );

        assertEquals(List.of(
                "#approved",
                "#needs-change",
                "Recent · #reviewed Earlier custom note",
                "Other…",
                "Cancel"
        ), choices.stream().map(SFMActionChoice::displayText).toList());
        assertEquals(1, choices.stream().filter(choice -> choice.displayText().equals("#approved")).count());
        assertEquals(1, choices.stream().filter(choice -> choice.displayText().equals("#needs-change")).count());
        assertTrue(choices.stream().allMatch(choice ->
                choice.command().contains("review-comment-draft-17")));
    }

    @Test
    void readOnlyChoiceSurfaceOffersOnlyExplicitWritableTransitionAndCancel() {
        List<SFMActionChoice> choices = SFMReleaseReviewCommentChoiceAction.choices(
                "review-comment-draft-4", false, List.of("#recent"));

        assertEquals(List.of("Reopen this review writable", "Cancel"),
                choices.stream().map(SFMActionChoice::displayText).toList());
        assertTrue(choices.get(0).command().contains("reopen_writable"));
        assertTrue(choices.get(1).command().contains("cancel"));
    }

    private static boolean executable(SFMReleaseReviewCommentChoiceAction.Kind kind, String command) {
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal("action");
        new SFMReleaseReviewCommentChoiceAction(kind).configureCommandNode(node);
        dispatcher.register(node);
        ParseResults<SFMClientActionSource> parsed = dispatcher.parse(
                command,
                new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null))
        );
        return SFMClientActionExecutor.isExecutable(parsed);
    }
}
