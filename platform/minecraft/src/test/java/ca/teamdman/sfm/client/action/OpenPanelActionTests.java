package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMTestScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMReviewExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMTextEditorScreenType;
import com.mojang.brigadier.ParseResults;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenPanelActionTests {
    private static final ResourceLocation ACTION_ID = new ResourceLocation("sfm", "panel/open");
    private static final ResourceLocation SCREEN_ID = new ResourceLocation("sfm", "test_screen");

    @Test
    void registeredScreenTypeContributesItsTypedArgumentsToTheHierarchicalPanelAction() {
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.FOCUSED,
                () -> List.of(Map.entry(SCREEN_ID, new SFMTestScreenType()))
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        ParseResults<SFMClientActionSource> incomplete = tree.parse(
                "sfm action invoke sfm:panel/open sfm:test_screen ", source);
        ParseResults<SFMClientActionSource> complete = tree.parse(
                "sfm action invoke sfm:panel/open sfm:test_screen test screen 1", source);
        ParseResults<SFMClientActionSource> parent = tree.parse(
                "sfm action invoke sfm:panel/open", source);

        assertFalse(isExecutable(incomplete));
        assertTrue(isExecutable(complete));
        assertTrue(SFMClientCommandInsertion.hasAvailableLiteralChildren(parent));
    }

    @Test
    void sceneIsSuggestedBeforeItsRequiredArgument() throws Exception {
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.RIGHT,
                () -> List.of(Map.entry(SCREEN_ID, new SFMTestScreenType()))
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(new ResourceLocation("sfm", "panel/open/right"), action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        var suggestions = tree.getCompletionSuggestions(tree.parse(
                "sfm action invoke sfm:panel/open/right ", source)).get();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals(SCREEN_ID.toString())));
    }

    @Test
    void completionDoesNotReinvokeACompletedSceneCatalog() {
        AtomicInteger catalogCalls = new AtomicInteger();
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.FOCUSED,
                () -> {
                    catalogCalls.incrementAndGet();
                    return List.of(Map.entry(SCREEN_ID, new SFMTestScreenType()));
                }
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));
        int callsAfterCompile = catalogCalls.get();

        tree.getCompletionSuggestions(tree.parse("sfm action invoke sfm:panel/open ", source)).join();

        org.junit.jupiter.api.Assertions.assertEquals(callsAfterCompile, catalogCalls.get());
    }

    @Test
    void reviewChangesSceneRequiresBothSelectors() {
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.FOCUSED,
                () -> List.of(Map.entry(
                        new ResourceLocation("sfm", "explorer/changes"),
                        new SFMReviewExplorerScreenType(SFMReviewExplorerScreenType.Projection.CHANGES)
                ))
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        assertFalse(isExecutable(tree.parse(
                "sfm action invoke sfm:panel/open sfm:explorer/changes \"mod 4.34.0\"", source)));
        assertTrue(isExecutable(tree.parse(
                "sfm action invoke sfm:panel/open sfm:explorer/changes \"mod 4.34.0\" \"HEAD\"", source)));
    }

    @Test
    void textEditorSceneAcceptsNamespacedEditorId() {
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.FOCUSED,
                () -> List.of(Map.entry(
                        new ResourceLocation("sfm", "text_editor"),
                        new SFMTextEditorScreenType()
                ))
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        assertTrue(isExecutable(tree.parse(
                "sfm action invoke sfm:panel/open sfm:text_editor sfm:text_editor_v3", source)));
    }

    private static boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        assertNotNull(parsed);
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
