package ca.teamdman.sfm.client.action;

{% if features.workspace_panels and features.workspace_panel_reopening and features.file_explorer %}
import ca.teamdman.sfm.client.explorer.SFMPath;
{% endif %}
{% if features.workspace_panels and features.workspace_panel_reopening and features.file_explorer %}
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
{% endif %}
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
{% if features.workspace_panels and features.workspace_panel_reopening and features.file_explorer %}
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
{% endif %}
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMReleaseReviewActionTests {
{% if features.workspace_panels and features.workspace_panel_reopening and features.file_explorer %}
    @Test
    void reviewViewRecipeUsesTheReviewFileAsAnOrdinaryExpandableExplorerRoot() {
        Path path = Path.of("D:\\review\\current.sfm-review.json").toAbsolutePath().normalize();
        var recipe = SFMReleaseReviewAction.mountedExplorerRecipe(path);

        assertEquals("sfm:explorer", recipe.sceneTypeId().toString());
        var literal = assertInstanceOf(SFMPathExpression.Literal.class, recipe.initialLocation());
        assertEquals(SFMPath.fromNative(path), literal.path());
    }

{% endif %}
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
{% if features.workspace_panels %}
                new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null))
{% else %}
                new SFMClientActionSource(new SFMClientActionContext(null, () -> true))
{% endif %}
        );
        return SFMClientActionExecutor.isExecutable(parsed);
    }
}
