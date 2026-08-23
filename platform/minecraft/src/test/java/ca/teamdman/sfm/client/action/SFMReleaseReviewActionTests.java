package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewActionTests {
    @Test
    void greedyPathArgumentsRemainRawEvenWhenTheyContainSpaces() {
        Path path = Path.of("D:\\Repo With Space\\review.sfm-review.json");
        String argument = SFMReleaseReviewAction.greedyPathArgument(path);

        assertEquals(path.toString(), argument);
        assertFalse(argument.startsWith("\""));
        assertEquals(path, Path.of(argument));
        assertTrue(executable(SFMReleaseReviewAction.Kind.OPEN, "action " + argument));
    }

    @Test
    void colonBearingProposalIdsAreQuotedIntoExecutableCommands() {
        String proposal = "selector-proposal:semantic:abc123";
        String command = "action " + StringArgumentType.escapeIfRequired(proposal)
                + " #approved Reviewed through the in-game release-review surface.";

        assertTrue(executable(SFMReleaseReviewAction.Kind.COMMENT_CREATE, command));
        assertFalse(executable(SFMReleaseReviewAction.Kind.COMMENT_CREATE,
                "action " + proposal + " #approved Unquoted identifiers must not parse accidentally."));
    }

    @Test
    void colonBearingMigrationIdsAreQuotedIntoExecutableCommands() {
        String migration = "migration:1-relocated";
        String expectedState = "a".repeat(64);
        String command = "action " + StringArgumentType.escapeIfRequired(migration)
                + " " + expectedState
                + " relocation-confirmed none Explicitly confirmed the witnessed relocation.";

        assertTrue(executable(SFMReleaseReviewAction.Kind.MIGRATION_DECIDE, command));
        assertFalse(executable(SFMReleaseReviewAction.Kind.MIGRATION_DECIDE,
                "action " + migration + " " + expectedState
                        + " deferred none Unquoted identifiers must not parse accidentally."));
    }

    @Test
    void colonBearingReviewUnitIdsAreQuotedIntoExecutableCommands() {
        String unit = "unit:1.19.2:src/SFM.java:hunk:1";
        assertTrue(executable(SFMReleaseReviewAction.Kind.SELECT,
                "action " + StringArgumentType.escapeIfRequired(unit)));
        assertFalse(executable(SFMReleaseReviewAction.Kind.SELECT, "action " + unit));
    }

    private static boolean executable(SFMReleaseReviewAction.Kind kind, String command) {
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal("action");
        new SFMReleaseReviewAction(kind).configureCommandNode(node);
        dispatcher.register(node);
        ParseResults<SFMClientActionSource> parsed = dispatcher.parse(
                command,
                new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null))
        );
        return SFMClientActionExecutor.isExecutable(parsed);
    }
}
