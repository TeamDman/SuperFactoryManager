package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.history.SFMHistoryGraphPanel;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMHistoryGraphScreenTypeTests {
    @Test
    void defaultAndPinnedSceneFormsProduceFreshReopenablePanels() throws Exception {
        ResourceLocation sceneId = new ResourceLocation("sfm", "episode/history");
        AtomicReference<SFMPanelReopenRecipe> captured = new AtomicReference<>();
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(new SFMHistoryGraphScreenType().createCommandNode(
                sceneId,
                (context, recipe) -> {
                    captured.set(recipe);
                    return 1;
                }
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));

        assertEquals(1, dispatcher.execute("sfm:episode/history", source));
        SFMHistoryGraphScreenType.Recipe activeRecipe = (SFMHistoryGraphScreenType.Recipe) captured.get();
        assertEquals(sceneId, activeRecipe.sceneTypeId());
        assertEquals("focused", activeRecipe.episodeSelector());
        assertTrue(activeRecipe.reopen() instanceof SFMHistoryGraphPanel);
        assertNotSame(activeRecipe.reopen(), activeRecipe.reopen());

        assertEquals(1, dispatcher.execute("sfm:episode/history id(episode-a)", source));
        SFMHistoryGraphScreenType.Recipe pinned = (SFMHistoryGraphScreenType.Recipe) captured.get();
        assertEquals("id(episode-a)", pinned.episodeSelector());
        assertNotSame(pinned.reopen(), pinned.reopen());
    }

    @Test
    void defaultScenePinsAnOriginatingEpisodeWhenOneIsAvailable() {
        assertEquals(
                "id(episode-b)",
                SFMHistoryGraphScreenType.canonicalDefaultSelector(Optional.of("episode-b"))
        );
        assertEquals(
                "focused",
                SFMHistoryGraphScreenType.canonicalDefaultSelector(Optional.empty())
        );
    }
}
