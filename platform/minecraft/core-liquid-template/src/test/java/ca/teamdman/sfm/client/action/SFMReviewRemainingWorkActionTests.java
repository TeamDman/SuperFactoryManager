package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
{% if features.workspace_panels and features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.explorer.SFMPath;
{% endif %}
{% if features.workspace_panels and features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
{% endif %}
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec;
{% if features.workspace_panels and features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
{% endif %}
{% if features.workspace_panels and features.workspace_panel_reopening %}
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
{% endif %}
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
{% if features.workspace_panels and features.workspace_panel_reopening %}
import java.util.Optional;
{% endif %}

import static org.junit.jupiter.api.Assertions.*;

class SFMReviewRemainingWorkActionTests {
    @Test
    void mouseChoicesAreExecutableAndUseTheSameExactWitnessesAsTheCanonicalLaneQuery() throws Exception {
        var review = fixture();
        var dispatcher = new CommandDispatcher<SFMClientActionSource>();
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal("remaining");
        new SFMReviewRemainingWorkAction().configureCommandNode(node);
        dispatcher.register(node);
{% if features.workspace_panels %}
{% if features.workspace_panels %}
        var source = new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null));
{% else %}
        var source = new SFMClientActionSource(new SFMClientActionContext(null, () -> true));
{% endif %}
{% else %}
        var source = new SFMClientActionSource(new SFMClientActionContext(null, () -> true));
{% endif %}
        for (var choice : SFMReviewRemainingWorkAction.choices(review)) {
            String input = "remaining" + choice.command().substring(("sfm action invoke " + choice.actionId()).length());
            var parsed = dispatcher.parse(input, source);
            assertTrue(SFMClientActionExecutor.isExecutable(parsed), input);
            String lane = StringArgumentType.getString(parsed.getContext().build(input), "lane");
            var actual = SFMReleaseReviewKernel.query(review, SFMReviewRemainingWorkAction.expression(review, lane));
            var expected = SFMReleaseReviewKernel.query(review,
                    lane.equals("all") ? "remaining intersect HEAD" : "remaining intersect " + lane + " HEAD");
            assertEquals(expected.reviewUnitIds(), actual.reviewUnitIds());
            assertEquals(expected.surfaceCoverage(), actual.surfaceCoverage());
            if (lane.equals("all") || lane.equals("1.19.2")) {
                assertTrue(actual.reviewUnitIds().contains("unit:src/Cafe.java:value"),
                        "the fixture's after-side approval does not approve its before-side bytes");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> SFMReviewRemainingWorkAction.expression(review, "unknown"));
        assertThrows(IllegalArgumentException.class, () -> SFMReviewRemainingWorkAction.expression(review, "1.19.2 union #approved"));
    }

{% if features.workspace_panels and features.workspace_panel_reopening %}
    @Test
    void lensMenuExposesRemainingCoverageAndAnExactRetainedFilterControl() throws Exception {
        var lens = new SFMReleaseReviewExplorerRuntime.LensDescriptor(
                new SFMPath(SFMPath.Kind.CONTRIBUTED, SFMReleaseReviewExplorerRuntime.PATH_SCHEME,
                        "review-test", List.of("changes"), Optional.empty(), true),
                Path.of("review.sfm-review.json"), 1L,
                SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                SFMReviewExplorerModel.PathLayout.HIERARCHY, Optional.empty(), "Changes");
        var choices = SFMReviewLensSetAction.controlChoices(lens, fixture(), "explorer-雪", ".java");
        assertTrue(choices.stream().anyMatch(choice -> choice.command().equals(
                "sfm action invoke sfm:review/work/remaining 1.19.2")));
        assertTrue(choices.stream().anyMatch(choice -> choice.command().equals(
                "sfm action invoke sfm:review/lens/set status")
                && choice.displayText().contains("Exact coverage and blockers")));
        String exactClear = "sfm action invoke sfm:explorer/filter/clear "
                + SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, "explorer-雪").canonical();
        assertTrue(choices.stream().anyMatch(choice -> choice.command().equals(exactClear)
                && choice.displayText().contains(".java")));
        assertFalse(SFMReviewLensSetAction.controlChoices(lens, fixture(), "explorer-雪", "").stream()
                .anyMatch(choice -> choice.command().equals(exactClear)));
        assertEquals("Work queue", SFMReviewLensSetAction.shortTitle(
                new SFMReleaseReviewExplorerRuntime.LensDescriptor(lens.root(), lens.reviewPath(),
                        lens.reviewOpenEpoch(), SFMReleaseReviewExplorerScreenType.Projection.QUERY,
                        lens.changesPathLayout(), Optional.empty(), "Query")));
    }

{% endif %}
{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
    @Test
    void explicitClearFilterCommandRetainsAnExactExplorerSelector() {
        var dispatcher = new CommandDispatcher<SFMClientActionSource>();
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal("clear");
        new SFMExplorerAction(SFMExplorerAction.Operation.FILTER_CLEAR).configureCommandNode(node);
        dispatcher.register(node);
        String selector = SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, "explorer-雪").canonical();
        var source = new SFMClientActionSource(new SFMClientActionContext(null, () -> true, null));
        assertTrue(SFMClientActionExecutor.isExecutable(dispatcher.parse("clear " + selector, source)));
        assertEquals("no text filter", SFMReviewLensSetAction.filterDescription(""));
        assertTrue(SFMReviewLensSetAction.filterDescription(".java").contains("filter retained: .java"));
    }

{% endif %}
    private static SFMReleaseReviewV1 fixture() throws Exception {
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path file = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(file)) return SFMReleaseReviewV1Codec.parse(Files.readString(file));
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Canonical release-review fixture unavailable");
    }
}
