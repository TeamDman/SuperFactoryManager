package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMTestScreenType;
import com.mojang.brigadier.ParseResults;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScreenToSideActionTests {
    private static final ResourceLocation ACTION_ID = new ResourceLocation("sfm", "workspace/open_to_side");
    private static final ResourceLocation SCREEN_ID = new ResourceLocation("sfm", "test_screen");

    @Test
    void registeredScreenTypeContributesItsTypedArgumentsToTheActionTree() {
        OpenScreenToSideAction action = new OpenScreenToSideAction(() -> List.of(
                Map.entry(SCREEN_ID, new SFMTestScreenType())
        ));
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        ParseResults<SFMClientActionSource> incomplete = tree.parse(
                "sfm action invoke sfm:workspace/open_to_side sfm:test_screen ",
                source
        );
        ParseResults<SFMClientActionSource> complete = tree.parse(
                "sfm action invoke sfm:workspace/open_to_side sfm:test_screen test screen 1",
                source
        );

        assertFalse(isExecutable(incomplete));
        assertTrue(isExecutable(complete));
    }

    @Test
    void registeredScreenTypeIsSuggestedByBrigadier() throws Exception {
        OpenScreenToSideAction action = new OpenScreenToSideAction(() -> List.of(
                Map.entry(SCREEN_ID, new SFMTestScreenType())
        ));
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        var suggestions = tree.getCompletionSuggestions(tree.parse(
                "sfm action invoke sfm:workspace/open_to_side ",
                source
        )).get();

        assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals(SCREEN_ID.toString())));
    }

    private static boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        assertNotNull(parsed);
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
