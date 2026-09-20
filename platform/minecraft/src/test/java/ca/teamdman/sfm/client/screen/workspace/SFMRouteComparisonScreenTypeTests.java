package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMRouteComparisonIntegrationTestFixture;
import ca.teamdman.sfm.client.history.SFMRouteComparisonRuntime;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonStore;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SFMRouteComparisonScreenTypeTests {
    @TempDir
    Path temporaryDirectory;

    @Test
    void latestPairAndExplicitPairGrammarProducePersistedRecipes() throws Exception {
        SFMHistoryGraphRuntime historyRuntime = new SFMHistoryGraphRuntime();
        historyRuntime.register(new SFMRouteComparisonIntegrationTestFixture.RecordingController("episode-a"));
        SFMRouteComparisonRuntime comparisonRuntime = new SFMRouteComparisonRuntime(id ->
                new SFMRouteComparisonStore(temporaryDirectory.resolve(id + ".route-comparison")));
        ResourceLocation sceneId = new ResourceLocation("sfm", "episode/route-comparison");
        AtomicReference<SFMPanelReopenRecipe> captured = new AtomicReference<>();
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(new SFMRouteComparisonScreenType(historyRuntime, comparisonRuntime)
                .createCommandNode(sceneId, (context, recipe) -> {
                    captured.set(recipe);
                    return 1;
                }));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));

        assertEquals(1, dispatcher.execute(
                "sfm:episode/route-comparison id(episode-a)",
                source
        ));
        SFMRouteComparisonScreenType.Recipe latest =
                (SFMRouteComparisonScreenType.Recipe) captured.get();
        assertEquals(sceneId, latest.sceneTypeId());

        assertEquals(1, dispatcher.execute(
                "sfm:episode/route-comparison id(episode-a) "
                        + "episode-a/plan episode-a/route "
                        + "episode-a/plan episode-a/route-z explicit-comparison",
                source
        ));
        SFMRouteComparisonScreenType.Recipe explicit =
                (SFMRouteComparisonScreenType.Recipe) captured.get();
        assertEquals("explicit-comparison", explicit.sessionId());
        assertNotEquals(latest.sessionId(), explicit.sessionId());
        assertEquals("explicit-comparison", comparisonRuntime.require("explicit-comparison").id());
    }
}
