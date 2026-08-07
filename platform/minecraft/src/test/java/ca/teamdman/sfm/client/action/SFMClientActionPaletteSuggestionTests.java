package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMClientActionPaletteSuggestionTests {
    private static final String QUERY = "sfm action invoke open";

    @Test
    void fuzzyQueryRanksActionTitleAndTheResultStillExecutesThroughBrigadier()
            throws CommandSyntaxException {
        AtomicInteger terminalCount = new AtomicInteger();
        AtomicInteger workspaceCount = new AtomicInteger();
        SFMClientActionCommandTree tree = tree(
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new TestAction("Open terminal", terminalCount, true)
                ),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open/right"),
                        new TestAction("Open workspace", workspaceCount, true)
                ),
                Map.entry(
                        new ResourceLocation("sfm", "terminal/close"),
                        new TestAction("Close terminal", new AtomicInteger(), true)
                )
        );
        SFMClientActionSource source = source();

        var suggestions = tree.getPaletteSuggestions(QUERY, tree.parse(QUERY, source))
                .join()
                .getList();

        assertEquals(
                List.of("sfm:panel/open", "sfm:panel/open/right"),
                suggestions.stream().map(suggestion -> suggestion.getText()).toList()
        );
        assertEquals(1, tree.execute("sfm action invoke " + suggestions.get(0).getText(), source));
        assertEquals(1, terminalCount.get());
        assertEquals(0, workspaceCount.get());
    }

    @Test
    void fuzzyQueryExcludesUnavailableActions() {
        ResourceLocation availableId = new ResourceLocation("sfm", "panel/open");
        ResourceLocation unavailableId = new ResourceLocation("sfm", "panel/open/right");
        SFMClientActionCommandTree tree = tree(
                Map.entry(availableId, new TestAction("Open terminal", new AtomicInteger(), true)),
                Map.entry(unavailableId, new TestAction("Open workspace", new AtomicInteger(), false))
        );
        SFMClientActionSource source = source();

        var suggestions = tree.getPaletteSuggestions(QUERY, tree.parse(QUERY, source))
                .join()
                .getList();

        assertEquals(List.of(availableId.toString()), suggestions.stream()
                .map(suggestion -> suggestion.getText())
                .toList());
        assertThrows(
                CommandSyntaxException.class,
                () -> tree.execute("sfm action invoke " + unavailableId, source)
        );
    }

    @Test
    void fuzzyActionSlotQueryFindsCompleteLiteralContinuationPaths()
            throws CommandSyntaxException {
        AtomicInteger openedTerminal = new AtomicInteger();
        SFMClientActionCommandTree tree = tree(
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new SceneAction(openedTerminal, true)
                ),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open/left"),
                        new SceneAction(new AtomicInteger(), true)
                ),
                Map.entry(
                        new ResourceLocation("sfm", "terminal/server/start"),
                        new TestAction("Start terminal", new AtomicInteger(), true)
                )
        );
        SFMClientActionSource source = source();
        String query = "sfm action invoke term";

        var suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source))
                .join()
                .getList();
        List<String> texts = suggestions.stream().map(suggestion -> suggestion.getText()).toList();

        assertTrue(texts.contains("sfm:panel/open sfm:terminal"));
        assertTrue(texts.contains("sfm:panel/open sfm:terminal_properties"));
        assertTrue(texts.contains("sfm:panel/open/left sfm:terminal"));
        assertTrue(texts.contains("sfm:panel/open/left sfm:terminal_properties"));
        var terminal = suggestions.stream()
                .filter(suggestion -> suggestion.getText().equals("sfm:panel/open sfm:terminal"))
                .findFirst()
                .orElseThrow();
        String completeCommand = terminal.apply(query);
        assertEquals("sfm action invoke sfm:panel/open sfm:terminal", completeCommand);
        assertEquals(1, tree.execute(completeCommand, source));
        assertEquals(1, openedTerminal.get());
    }

    @Test
    void literalContinuationSearchDoesNotLeakUnavailableActionTrees() {
        SFMClientActionCommandTree tree = tree(Map.entry(
                new ResourceLocation("sfm", "panel/open"),
                new SceneAction(new AtomicInteger(), false)
        ));
        SFMClientActionSource source = source();
        String query = "sfm action invoke term";

        List<String> suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source))
                .join()
                .getList()
                .stream()
                .map(suggestion -> suggestion.getText())
                .toList();

        assertFalse(suggestions.stream().anyMatch(suggestion -> suggestion.contains("sfm:terminal")));
    }

    private static SFMClientActionCommandTree tree(
            Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>... actions
    ) {
        return SFMClientActionDispatcherCompiler.compileCommandTree(List.of(actions));
    }

    private static SFMClientActionSource source() {
        return new SFMClientActionSource(
                SFMClientActionContext.create(new Object(), new AtomicBoolean(true)::get)
        );
    }

    private static final class TestAction implements SFMClientAction<Object> {
        private final String title;
        private final AtomicInteger count;
        private final boolean available;

        private TestAction(String title, AtomicInteger count, boolean available) {
            this.title = title;
            this.count = count;
            this.available = available;
        }

        @Override
        public Component title() {
            return Component.literal(title);
        }

        @Override
        public Component description() {
            return Component.literal("Test palette action");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return ignored -> available
                    ? SFMClientActionAvailability.available(new Object())
                    : SFMClientActionAvailability.unavailable(Component.literal("test unavailable"));
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            count.incrementAndGet();
            return Command.SINGLE_SUCCESS;
        }
    }

    private static final class SceneAction implements SFMClientAction<Object> {
        private final AtomicInteger openedTerminal;
        private final boolean available;

        private SceneAction(AtomicInteger openedTerminal, boolean available) {
            this.openedTerminal = openedTerminal;
            this.available = available;
        }

        @Override
        public Component title() {
            return Component.literal("Open panel");
        }

        @Override
        public Component description() {
            return Component.literal("Open a registered scene");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return ignored -> available
                    ? SFMClientActionAvailability.available(new Object())
                    : SFMClientActionAvailability.unavailable(Component.literal("test unavailable"));
        }

        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("sfm:terminal")
                    .executes(context -> {
                        openedTerminal.incrementAndGet();
                        return Command.SINGLE_SUCCESS;
                    }));
            node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("sfm:terminal_properties")
                    .executes(context -> Command.SINGLE_SUCCESS));
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            throw new AssertionError("The scene argument is required");
        }
    }
}
