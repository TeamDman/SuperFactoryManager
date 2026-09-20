package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMHistoryGraphTestFixture;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingChamber;
import ca.teamdman.sfm.client.history.chamber.SFMDecimalNumberingTrajectoryController;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import com.mojang.brigadier.ParseResults;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMTrajectoryMachineActionTests {
    @Test
    void paletteSurfacePreservesDynamicMachineSelectorSuggestions() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-a"));
        ResourceLocation actionId = new ResourceLocation("sfm", "episode/trajectory/plan");
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(actionId, new SFMTrajectoryMachineAction(
                        SFMTrajectoryMachineAction.Kind.PLAN,
                        runtime
                ))
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));
        String prefix = "sfm action invoke " + actionId + " ";

        List<String> suggestions = tree.getPaletteCandidates(prefix, tree.parse(prefix, source))
                .join().stream()
                .filter(SFMPaletteCandidate::activatable)
                .map(SFMPaletteCandidate::replacementText)
                .toList();

        assertTrue(suggestions.contains("focused"));
        assertTrue(suggestions.contains("all"));
        assertTrue(suggestions.contains(SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EPISODE,
                "episode-a"
        ).canonical()));
    }

    @Test
    void replayActionsSuggestExplicitRetainedBoundariesAndExecuteThroughTheRegistrySurface() throws Exception {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMDecimalNumberingTrajectoryController controller = new SFMDecimalNumberingTrajectoryController(
                "sfm:test/replay-action",
                "document-1",
                SFMDecimalNumberingTrajectoryController.INITIAL_TEXT,
                () -> "ambient-stable"
        );
        String boundary = controller.currentState().revisionId();
        controller.apply(new SFMHistoryGraphRuntime.InvokeSemanticAction(
                SFMDecimalNumberingChamber.SELECT_ALL_HYPHENS_ACTION_ID));
        controller.apply(new SFMHistoryGraphRuntime.InvokeSemanticAction(
                SFMDecimalNumberingChamber.REPLACE_DECIMAL_SEQUENCE_ACTION_ID));
        runtime.register(controller);
        ResourceLocation actionId = new ResourceLocation("sfm", "episode/replay/exact");
        SFMTrajectoryMachineAction action = new SFMTrajectoryMachineAction(
                SFMTrajectoryMachineAction.Kind.EXACT_REPLAY,
                runtime
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(actionId, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));
        String exactSelector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EPISODE,
                controller.machineId()
        ).canonical();
        String prefix = "sfm action invoke " + actionId + " " + exactSelector + " ";

        var sourceSuggestions = tree.getCompletionSuggestions(tree.parse(prefix, source)).get();
        assertTrue(sourceSuggestions.getList().stream().anyMatch(suggestion ->
                suggestion.getText().equals(boundary)));
        String targetPrefix = prefix + boundary + " ";
        var targetSuggestions = tree.getCompletionSuggestions(tree.parse(targetPrefix, source)).get();
        assertTrue(targetSuggestions.getList().stream().anyMatch(suggestion ->
                suggestion.getText().equals(boundary)));
        String command = targetPrefix + boundary;
        assertTrue(executable(tree.parse(command, source)));
        assertEquals(1, tree.execute(command, source));
        assertTrue(controller.replayArchive().replayReports().stream().anyMatch(report ->
                report.mode() == ca.teamdman.sfm.client.history.replay.SFMTemporalReplayArchive.ReplayMode.EXACT_REPLAY));

        assertEquals(1, SFMTrajectoryMachineAction.replayChoices(
                actionId,
                runtime.snapshotEvent().machines(),
                SFMTrajectoryMachineAction.Kind.EXACT_REPLAY
        ).size());
    }

    @Test
    void selectorIsRequiredAndBoundedRunInvokesTheExactMachine() throws Exception {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        SFMHistoryGraphTestFixture.MutableController controller =
                new SFMHistoryGraphTestFixture.MutableController("episode-a");
        runtime.register(controller);
        ResourceLocation actionId = new ResourceLocation("sfm", "episode/trajectory/run");
        SFMTrajectoryMachineAction action = new SFMTrajectoryMachineAction(
                SFMTrajectoryMachineAction.Kind.RUN,
                runtime
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(actionId, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));

        assertFalse(executable(tree.parse("sfm action invoke " + actionId, source)));
        String command = "sfm action invoke " + actionId + " id(episode-a) 3";
        assertTrue(executable(tree.parse(command, source)));
        assertEquals(1, tree.execute(command, source));
        assertEquals(new SFMHistoryGraphRuntime.Run(3), controller.operations().get(0));
    }

    @Test
    void routeSuggestionsAreDerivedFromMatchedImmutablePlans() throws Exception {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-a"));
        ResourceLocation actionId = new ResourceLocation("sfm", "episode/trajectory/route/select");
        SFMTrajectoryMachineAction action = new SFMTrajectoryMachineAction(
                SFMTrajectoryMachineAction.Kind.SELECT_ROUTE,
                runtime
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(actionId, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));
        String prefix = "sfm action invoke " + actionId + " id(episode-a) episode-a/plan ";

        var suggestions = tree.getCompletionSuggestions(tree.parse(prefix, source)).get();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals("episode-a/route")));
        assertTrue(executable(tree.parse(prefix + "episode-a/route", source)));
    }

    @Test
    void routeChoicesNarrowBroadDiscoveryToTheSupplyingMachine() {
        SFMHistoryGraphRuntime runtime = new SFMHistoryGraphRuntime();
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-a"));
        runtime.register(new SFMHistoryGraphTestFixture.MutableController("episode-b"));
        ResourceLocation actionId = new ResourceLocation("sfm", "episode/trajectory/route/select");

        var choices = SFMTrajectoryMachineAction.routeChoices(
                actionId,
                runtime.snapshotEvent().machines()
        );

        assertEquals(2, choices.size());
        assertTrue(choices.stream().anyMatch(choice -> choice.command().equals(
                "sfm action invoke " + actionId
                        + " id(episode-a) episode-a/plan episode-a/route")));
        assertTrue(choices.stream().anyMatch(choice -> choice.command().equals(
                "sfm action invoke " + actionId
                        + " id(episode-b) episode-b/plan episode-b/route")));
        assertTrue(choices.stream().noneMatch(choice -> choice.command().contains(" all ")));
    }

    private static boolean executable(ParseResults<SFMClientActionSource> parsed) {
        assertNotNull(parsed);
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
