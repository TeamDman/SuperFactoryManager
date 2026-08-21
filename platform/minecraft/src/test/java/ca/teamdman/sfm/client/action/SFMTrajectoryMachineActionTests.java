package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMHistoryGraphTestFixture;
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
