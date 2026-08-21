package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMDecimalNumberingChamberScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMHistoryGraphScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMReviewExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMTestScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMTextEditorScreenType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    void everyDirectionalPanelActionAcceptsTheGenericExplorerPathExpressionGrammar() {
        ResourceLocation explorer = new ResourceLocation("sfm", "explorer");
        String location = "union(file:///D:/Repos/Minecraft/SFM,members(id(selection-1)))";
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        for (OpenPanelAction.Direction direction : OpenPanelAction.Direction.values()) {
            ResourceLocation actionId = switch (direction) {
                case FOCUSED -> new ResourceLocation("sfm", "panel/open");
                case LEFT -> new ResourceLocation("sfm", "panel/open/left");
                case RIGHT -> new ResourceLocation("sfm", "panel/open/right");
                case ABOVE -> new ResourceLocation("sfm", "panel/open/above");
                case BELOW -> new ResourceLocation("sfm", "panel/open/below");
            };
            OpenPanelAction action = new OpenPanelAction(
                    direction,
                    () -> List.of(Map.entry(explorer, new SFMExplorerScreenType()))
            );
            SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                    Map.entry(actionId, action)
            ));

            assertTrue(isExecutable(tree.parse(
                    "sfm action invoke " + actionId + " " + explorer,
                    source
            )), direction + " should accept the omitted default location");
            assertTrue(isExecutable(tree.parse(
                    "sfm action invoke " + actionId + " " + explorer + " " + location,
                    source
            )), direction + " should accept the full path expression");
        }
    }

    @Test
    void paletteFuzzyFindsNestedSceneIdsWithoutChangingBrigadierExecution() throws Exception {
        ResourceLocation terminal = new ResourceLocation("sfm", "terminal");
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.FOCUSED,
                () -> List.of(
                        Map.entry(terminal, new ca.teamdman.sfm.client.screen.workspace.SFMTerminalScreenType()),
                        Map.entry(SCREEN_ID, new SFMTestScreenType())
                )
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));
        String query = "sfm action invoke sfm:panel/open term";

        var suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source)).get();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals(terminal.toString())));
        assertFalse(isExecutable(tree.parse(query, source)));
        assertTrue(isExecutable(tree.parse(
                "sfm action invoke sfm:panel/open " + terminal, source)));
    }

    @Test
    void historyGraphSceneIsFuzzyDiscoverableAndExecutableWithoutArguments() throws Exception {
        ResourceLocation history = new ResourceLocation("sfm", "episode/history");
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.RIGHT,
                () -> List.of(Map.entry(history, new SFMHistoryGraphScreenType()))
        );
        ResourceLocation actionId = new ResourceLocation("sfm", "panel/open/right");
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(actionId, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));
        String query = "sfm action invoke sfm:panel/open/right hist";

        var suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source)).get();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals(history.toString())));
        assertTrue(isExecutable(tree.parse(
                "sfm action invoke sfm:panel/open/right " + history,
                source
        )));
    }

    @Test
    void temporalNumberingChamberIsFuzzyDiscoverableAndExecutableWithoutArguments() throws Exception {
        ResourceLocation chamber = new ResourceLocation("sfm", "chamber/temporal-decimal-numbering");
        OpenPanelAction action = new OpenPanelAction(
                OpenPanelAction.Direction.FOCUSED,
                () -> List.of(Map.entry(chamber, new SFMDecimalNumberingChamberScreenType()))
        );
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(
                SFMClientActionContext.create(null, () -> true));
        String query = "sfm action invoke sfm:panel/open decimal number";

        var suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source)).get();

        assertTrue(suggestions.getList().stream()
                .anyMatch(suggestion -> suggestion.getText().equals(chamber.toString())));
        assertTrue(isExecutable(tree.parse(
                "sfm action invoke sfm:panel/open " + chamber,
                source
        )));
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

    @Test
    void typedTestSceneSuppliesAReusableFreshPanelRecipe() throws Exception {
        AtomicReference<SFMPanelReopenRecipe> captured = new AtomicReference<>();
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(new SFMTestScreenType().createCommandNode(
                SCREEN_ID,
                (context, recipe) -> {
                    captured.set(recipe);
                    return 1;
                }));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        assertEquals(1, dispatcher.execute("sfm:test_screen duplicated text", source));
        SFMPanelReopenRecipe recipe = captured.get();
        var first = (ca.teamdman.sfm.client.screen.workspace.SFMTestScreenPanel) recipe.reopen();
        var second = (ca.teamdman.sfm.client.screen.workspace.SFMTestScreenPanel) recipe.reopen();

        assertEquals(SCREEN_ID, recipe.sceneTypeId());
        assertEquals("duplicated text", first.displayText());
        assertEquals(first.displayText(), second.displayText());
        assertNotSame(first, second);
    }

    @Test
    void reviewRecipeSharesSelectorsButCreatesIndependentNavigationModels() throws Exception {
        ResourceLocation sceneId = new ResourceLocation("sfm", "explorer/changes");
        AtomicReference<SFMPanelReopenRecipe> captured = new AtomicReference<>();
        CommandDispatcher<SFMClientActionSource> dispatcher = new CommandDispatcher<>();
        dispatcher.register(new SFMReviewExplorerScreenType(
                SFMReviewExplorerScreenType.Projection.CHANGES).createCommandNode(
                sceneId,
                (context, recipe) -> {
                    captured.set(recipe);
                    return 1;
                }));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        assertEquals(1, dispatcher.execute("sfm:explorer/changes \"mod 4.34.0\" \"HEAD\"", source));
        SFMReviewExplorerPanel first = (SFMReviewExplorerPanel) captured.get().reopen();
        SFMReviewExplorerPanel second = (SFMReviewExplorerPanel) captured.get().reopen();
        first.model().selectNext();

        assertNotSame(first, second);
        assertNotSame(first.model(), second.model());
        assertNotEquals(first.model().selectionIndex(), second.model().selectionIndex());
        assertNotEquals(first.model().selected().label(), second.model().selected().label());
        assertTrue(second.model().selected().label().contains("Changes"));
    }

    private static boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        assertNotNull(parsed);
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
