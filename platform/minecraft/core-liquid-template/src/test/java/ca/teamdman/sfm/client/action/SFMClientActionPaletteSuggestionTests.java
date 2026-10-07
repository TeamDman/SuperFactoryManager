package ca.teamdman.sfm.client.action;

{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import com.mojang.brigadier.Command;
{% if features.typed_command_palette %}
import com.mojang.brigadier.arguments.StringArgumentType;
{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
{% endcase %}
{% if features.typed_command_palette %}
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
{% endif %}
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
{% if features.typed_command_palette or features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
import com.mojang.brigadier.suggestion.Suggestion;
{% endif %}
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
import java.util.Set;
{% endif %}
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
{% if features.command_history %}
import java.util.function.Supplier;
{% endif %}

import static org.junit.jupiter.api.Assertions.assertEquals;
{% if features.typed_command_palette %}
import static org.junit.jupiter.api.Assertions.assertFalse;
{% endif %}
import static org.junit.jupiter.api.Assertions.assertThrows;
{% if features.typed_command_palette or features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
import static org.junit.jupiter.api.Assertions.assertTrue;
{% endif %}

class SFMClientActionPaletteSuggestionTests {
    private static final String QUERY = "sfm action invoke open";

{% if features.typed_command_palette %}
    @Test
    void optInConstructionIsBoundedTypedAndNeverCallsUnrelatedValueProviders() {
        AtomicInteger completionCalls = new AtomicInteger();
        AtomicBoolean available = new AtomicBoolean(true);
        ResourceLocation id = new ResourceLocation("sfm:rule/add");
        class RuleAction implements SFMClientAction<Object>, SFMClientActionCompletion {
            public Component title() { return Component.literal("Add preview rule"); }
            public Component description() { return Component.literal("Construct a rule"); }
            public SFMClientActionRequirement<Object> requirement() { return context -> available.get()
                    ? SFMClientActionAvailability.available(context)
                    : SFMClientActionAvailability.unavailable(Component.literal("unavailable")); }
            public int execute(Object target, CommandContext<SFMClientActionSource> command) { throw new AssertionError("Completion cannot execute"); }
            public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
                node.then(RequiredArgumentBuilder.<SFMClientActionSource,String>argument("rule",StringArgumentType.greedyString())
                        .suggests((context,builder)->{throw new AssertionError("Arbitrary provider was invoked");}));
            }
            public List<Continuation> contextualContinuations(SFMClientActionContext context) {
                return List.of(new Continuation("typed_prefix", "Rule for abc.json"));
            }
            public java.util.Optional<List<SFMPaletteCandidate>> argumentCandidates(String command,int start,int cursor,SFMClientActionContext context) {
                completionCalls.incrementAndGet();
                return java.util.Optional.of(List.of(SFMPaletteCandidate.activatable(
                        new Suggestion(com.mojang.brigadier.context.StringRange.between(start,start+3),"new"),
                        SFMPaletteCandidate.Kind.ARGUMENT_VALUE,SFMPaletteCandidate.Origin.BRIGADIER,id,"typed",-1,null)));
            }
        }
        var tree=tree(Map.entry(id,new RuleAction()));
        var source=source();
        String command="sfm action invoke sfm:rule/add old minecraft:bell";
        var candidates=tree.getPaletteCandidates(command,tree.parse(command,source),command.indexOf("old")+1).join();
        assertEquals("sfm action invoke sfm:rule/add new minecraft:bell",candidates.get(0).apply(command).afterValue());
        assertEquals(1,completionCalls.get());
        String query="sfm action invoke rule";
        var top=tree.getPaletteCandidates(query,tree.parse(query,source)).join();
        assertTrue(top.stream().anyMatch(c->c.replacementText().equals("sfm:rule/add")));
        assertTrue(top.stream().anyMatch(c->c.replacementText().equals("sfm:rule/add typed_prefix")));
        available.set(false);
        assertTrue(tree.getPaletteCandidates(query,tree.parse(query,source)).join().isEmpty());
        assertEquals(1,completionCalls.get());
    }

{% endif %}
    @Test
    void fuzzyQueryRanksActionTitleAndTheResultStillExecutesThroughBrigadier()
            throws CommandSyntaxException {
        AtomicInteger terminalCount = new AtomicInteger();
        AtomicInteger workspaceCount = new AtomicInteger();
        SFMClientActionCommandTree tree = tree(
                Map.entry(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        new ResourceLocation("sfm", "panel/open"),
{% when "1.21", "1.21.1" %}
                        SFMResourceLocation.fromNamespaceAndPath("sfm", "terminal/open"),
{% when "26.1.2" %}
                        SFMResourceLocation.fromNamespaceAndPath("sfm", "terminal/open"),
{% else %}
                        new ResourceLocation("sfm", "terminal/open"),
{% endcase %}
                        new TestAction("Open terminal", terminalCount, true)
                ),
                Map.entry(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        new ResourceLocation("sfm", "panel/open/right"),
{% when "1.21", "1.21.1" %}
                        SFMResourceLocation.fromNamespaceAndPath("sfm", "workspace/open"),
{% when "26.1.2" %}
                        SFMResourceLocation.fromNamespaceAndPath("sfm", "workspace/open"),
{% else %}
                        new ResourceLocation("sfm", "workspace/open"),
{% endcase %}
                        new TestAction("Open workspace", workspaceCount, true)
                ),
                Map.entry(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                        new ResourceLocation("sfm", "terminal/close"),
{% when "1.21", "1.21.1" %}
                        SFMResourceLocation.fromNamespaceAndPath("sfm", "terminal/close"),
{% when "26.1.2" %}
                        SFMResourceLocation.fromNamespaceAndPath("sfm", "terminal/close"),
{% else %}
                        new ResourceLocation("sfm", "terminal/close"),
{% endcase %}
                        new TestAction("Close terminal", new AtomicInteger(), true)
                )
        );
        SFMClientActionSource source = source();

        var suggestions = tree.getPaletteSuggestions(QUERY, tree.parse(QUERY, source))
                .join()
                .getList();

        assertEquals(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                List.of("sfm:panel/open", "sfm:panel/open/right"),
{% else %}
                List.of("sfm:terminal/open", "sfm:workspace/open"),
{% endcase %}
                suggestions.stream().map(suggestion -> suggestion.getText()).toList()
        );
        assertEquals(1, tree.execute("sfm action invoke " + suggestions.get(0).getText(), source));
        assertEquals(1, terminalCount.get());
        assertEquals(0, workspaceCount.get());
    }

    @Test
    void fuzzyQueryExcludesUnavailableActions() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        ResourceLocation availableId = new ResourceLocation("sfm", "panel/open");
{% when "1.21", "1.21.1" %}
        ResourceLocation availableId = SFMResourceLocation.fromNamespaceAndPath("sfm", "terminal/open");
{% when "26.1.2" %}
        Identifier availableId = SFMResourceLocation.fromNamespaceAndPath("sfm", "terminal/open");
{% else %}
        ResourceLocation availableId = new ResourceLocation("sfm", "terminal/open");
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        ResourceLocation unavailableId = new ResourceLocation("sfm", "panel/open/right");
{% when "1.21", "1.21.1" %}
        ResourceLocation unavailableId = SFMResourceLocation.fromNamespaceAndPath("sfm", "workspace/open");
{% when "26.1.2" %}
        Identifier unavailableId = SFMResourceLocation.fromNamespaceAndPath("sfm", "workspace/open");
{% else %}
        ResourceLocation unavailableId = new ResourceLocation("sfm", "workspace/open");
{% endcase %}
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

{% if features.typed_command_palette %}
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

{% endif %}
{% if features.file_explorer or features.registry_explorer or features.explorer_search or features.explorer_compaction %}
    @Test
    void deepExplorerViewAndPathDisplayValuesAreDiscoverableAndExecutableGrammarLeaves() {
        ResourceLocation viewId = new ResourceLocation("sfm", "explorer/view/set");
        ResourceLocation pathDisplayId = new ResourceLocation("sfm", "explorer/path-display/set");
        SFMClientActionCommandTree tree = tree(
                Map.entry(viewId, new SFMExplorerAction(SFMExplorerAction.Operation.VIEW_SET)),
                Map.entry(pathDisplayId, new SFMExplorerAction(SFMExplorerAction.Operation.PATH_DISPLAY_SET))
        );
        SFMClientActionSource source = source();

        String viewPrefix = "sfm action invoke sfm:explorer/view/set focused ";
        List<Suggestion> views = tree.getPaletteSuggestions(viewPrefix, tree.parse(viewPrefix, source))
                .join().getList();
        assertEquals(List.of("sfm:list", "sfm:small_icons"),
                views.stream().map(Suggestion::getText).toList());
        assertTrue(views.stream().allMatch(suggestion -> suggestion.getTooltip() != null));
        assertTrue(views.stream().anyMatch(suggestion -> suggestion.getTooltip().getString().contains("ItemStack")));
        var parsedView = tree.parse(viewPrefix + "sfm:small_icons", source);
        assertTrue(SFMClientActionExecutor.isExecutable(parsedView), () ->
                "remaining=" + parsedView.getReader().getRemaining()
                        + ", exceptions=" + parsedView.getExceptions()
                        + ", command=" + parsedView.getContext().getCommand());

        String pathPrefix = "sfm action invoke sfm:explorer/path-display/set focused ";
        List<Suggestion> paths = tree.getPaletteSuggestions(pathPrefix, tree.parse(pathPrefix, source))
                .join().getList();
        assertEquals(Set.of("sfm:name", "sfm:relative_path", "sfm:absolute_path"),
                paths.stream().map(Suggestion::getText).collect(java.util.stream.Collectors.toSet()));
        assertTrue(paths.stream().allMatch(suggestion -> suggestion.getTooltip() != null));
        var parsedPathDisplay = tree.parse(pathPrefix + "sfm:absolute_path", source);
        assertTrue(SFMClientActionExecutor.isExecutable(parsedPathDisplay), () ->
                "remaining=" + parsedPathDisplay.getReader().getRemaining()
                        + ", exceptions=" + parsedPathDisplay.getExceptions()
                        + ", command=" + parsedPathDisplay.getContext().getCommand());
    }

{% endif %}
{% if features.command_history %}
    @Test
    void blankPalettePlacesNewestAvailableHistoryBeforeNormalActions() {
        SFMClientActionCommandTree tree = treeWithHistory(
                List.of(
                        "sfm action invoke sfm:echo old",
                        "sfm action invoke sfm:echo newest",
                        "sfm action invoke sfm:echo old"),
                Map.entry(
                        new ResourceLocation("sfm", "echo"),
                        new TestAction("Echo", new AtomicInteger(), true)),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new TestAction("Open", new AtomicInteger(), true)));
        String query = "sfm action invoke ";

        List<String> suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source()))
                .join().getList().stream().map(Suggestion::getText).toList();

        assertEquals(List.of("sfm:echo old", "sfm:echo newest", "sfm:echo", "sfm:panel/open"), suggestions);
    }

    @Test
    void typedHistoryUsesActionFuzzyRelevanceAndDoesNotPromoteUnrelatedCommands() {
        SFMClientActionCommandTree tree = treeWithHistory(
                List.of(
                        "sfm action invoke sfm:panel/open right",
                        "sfm action invoke sfm:echo newest"),
                Map.entry(
                        new ResourceLocation("sfm", "echo"),
                        new TestAction("Echo", new AtomicInteger(), true)),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new TestAction("Open", new AtomicInteger(), true)),
                Map.entry(
                        new ResourceLocation("sfm", "terminal/close"),
                        new TestAction("Close terminal", new AtomicInteger(), true)));
        String query = "sfm action invoke open";

        List<String> suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source()))
                .join().getList().stream().map(Suggestion::getText).toList();

        assertEquals("sfm:panel/open", suggestions.get(0));
        assertTrue(suggestions.contains("sfm:panel/open right"));
        assertTrue(suggestions.indexOf("sfm:panel/open")
                < suggestions.indexOf("sfm:panel/open right"));
        assertFalse(suggestions.contains("sfm:echo newest"));
    }

    @Test
    void compatiblePanelOpenHistoryBoostsTheSceneAcrossDirections() {
        SFMClientActionCommandTree tree = treeWithHistory(
                List.of(
                        "sfm action invoke sfm:panel/open sfm:terminal_properties",
                        "sfm action invoke sfm:echo unrelated"),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new SceneAction(new AtomicInteger(), true)),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open/right"),
                        new SceneAction(new AtomicInteger(), true)),
                Map.entry(
                        new ResourceLocation("sfm", "echo"),
                        new TestAction("Echo", new AtomicInteger(), true)));
        SFMClientActionSource source = source();
        String query = "sfm action invoke sfm:panel/open/right ";

        List<String> suggestions = tree.getPaletteSuggestions(query, tree.parse(query, source))
                .join().getList().stream().map(Suggestion::getText).toList();

        assertEquals("sfm:terminal_properties", suggestions.get(0));
        assertTrue(suggestions.contains("sfm:terminal"));
        assertFalse(suggestions.contains("unrelated"));
    }

    @Test
    void compatibleArgumentHistorySurvivesUnrelatedSiblingGrammarDifferences() {
        SFMClientActionCommandTree tree = treeWithHistory(
                List.of("sfm action invoke sfm:panel/open favourite"),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new AsymmetricSceneAction(false)),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open/right"),
                        new AsymmetricSceneAction(true)));
        String query = "sfm action invoke sfm:panel/open/right ";

        List<SFMPaletteCandidate> candidates = tree.getPaletteCandidates(
                query, tree.parse(query, source())).join();

        assertTrue(candidates.stream().anyMatch(candidate ->
                candidate.origin() == SFMPaletteCandidate.Origin.COMMAND_HISTORY
                        && candidate.replacementText().equals("favourite")));
    }

{% endif %}
{% if features.typed_command_palette %}
    @Test
    void sharedPaletteFrontierPublishesConcreteSuggestionsAndNamedUsage() {
        SFMClientActionCommandTree tree = tree(Map.entry(
                new ResourceLocation("sfm", "panel/open"),
                new SceneAction(new AtomicInteger(), true)
        ));
        String query = "sfm action invoke sfm:panel/open ";

        SFMCommandFrontierAnalysis frontier = tree.analyzePaletteFrontier(
                query, tree.parse(query, source())).join();

        assertEquals(List.of("sfm:terminal", "sfm:terminal_properties"),
                frontier.suggestions().stream().map(Suggestion::getText).toList());
        assertTrue(frontier.usageDisplayRows().isEmpty(),
                "literal continuations are concrete suggestions, not fake required arguments");
    }

{% endif %}
{% if features.command_history %}
    @Test
    void quotedAndGreedyArgumentHistoryUsesBrigadierRangesWithoutWhitespaceSplitting() {
        assertHistoricalValueRoundTrips(
                StringArgumentType.string(),
                "\"scene with spaces\""
        );
        assertHistoricalValueRoundTrips(
                StringArgumentType.greedyString(),
                "scene with spaces"
        );
    }

    @Test
    void explicitHistoryFamilyStillRejectsIncompatibleArgumentGrammars() {
        SFMClientActionCommandTree tree = treeWithHistory(
                List.of("sfm action invoke sfm:panel/open scene with spaces"),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new StringSceneAction(StringArgumentType.greedyString())),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open/right"),
                        new StringSceneAction(StringArgumentType.word())));
        String query = "sfm action invoke sfm:panel/open/right ";

        List<SFMPaletteCandidate> candidates = tree.getPaletteCandidates(query, tree.parse(query, source()))
                .join();

        assertFalse(candidates.stream().anyMatch(candidate ->
                candidate.origin() == SFMPaletteCandidate.Origin.COMMAND_HISTORY));
    }

{% endif %}
{% if features.typed_command_palette %}
    @Test
    void unsuggestedRequiredArgumentPublishesNamedNonActivatableUsage() {
        SFMClientActionCommandTree tree = tree(Map.entry(
                new ResourceLocation("sfm", "required"),
                new RequiredStringAction()
        ));
        String query = "sfm action invoke sfm:required ";

        List<SFMPaletteCandidate> candidates = tree.getPaletteCandidates(query, tree.parse(query, source()))
                .join();

        SFMPaletteCandidate usage = candidates.stream()
                .filter(candidate -> candidate.kind() == SFMPaletteCandidate.Kind.USAGE_HINT)
                .findFirst()
                .orElseThrow();
        assertFalse(usage.activatable());
        assertTrue(usage.displayText().contains("message"), usage::displayText);
        assertTrue(usage.displayText().contains("string"), usage::displayText);
        assertThrows(IllegalStateException.class, () -> usage.apply(query));
    }

{% endif %}
{% if features.command_history %}
    private static void assertHistoricalValueRoundTrips(
            StringArgumentType argumentType,
            String historicalValue
    ) {
        SFMClientActionCommandTree tree = treeWithHistory(
                List.of("sfm action invoke sfm:panel/open " + historicalValue),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open"),
                        new StringSceneAction(argumentType)),
                Map.entry(
                        new ResourceLocation("sfm", "panel/open/right"),
                        new StringSceneAction(argumentType)));
        String query = "sfm action invoke sfm:panel/open/right ";

        SFMPaletteCandidate candidate = tree.getPaletteCandidates(query, tree.parse(query, source()))
                .join().stream()
                .filter(value -> value.origin() == SFMPaletteCandidate.Origin.COMMAND_HISTORY)
                .findFirst()
                .orElseThrow();

        assertEquals(historicalValue, candidate.replacementText());
        assertEquals(SFMClientActionArgumentHistory.PANEL_OPEN_SCENE_FAMILY, candidate.historyFamily());
        assertEquals(query + historicalValue, candidate.apply(query).afterValue());
    }

{% endif %}
    private static SFMClientActionCommandTree tree(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>... actions
{% when "26.1.2" %}
            Map.Entry<Identifier, TestAction>... actions
{% else %}
            Map.Entry<ResourceLocation, TestAction>... actions
{% endcase %}
    ) {
        return SFMClientActionDispatcherCompiler.compileCommandTree(List.of(actions));
    }

{% if features.command_history %}
    private static SFMClientActionCommandTree treeWithHistory(
            List<String> history,
            Map.Entry<ResourceLocation, ? extends SFMClientAction<?>>... actions
    ) {
        Supplier<List<String>> supplier = () -> history;
        return SFMClientActionDispatcherCompiler.compileCommandTree(List.of(actions), supplier);
    }

{% endif %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.executes(this::invoke);
            node.then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument(
                            "text", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                    .executes(this::invoke));
        }

{% endcase %}
        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            count.incrementAndGet();
            return Command.SINGLE_SUCCESS;
        }
    }
{% if features.typed_command_palette %}

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

{% endif %}
{% if features.command_history %}
    private static final class AsymmetricSceneAction implements SFMClientAction<Object> {
        private final boolean extraSibling;

        private AsymmetricSceneAction(boolean extraSibling) {
            this.extraSibling = extraSibling;
        }

        @Override
        public Component title() {
            return Component.literal("Open scene");
        }

        @Override
        public Component description() {
            return Component.literal("Open one test scene");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return ignored -> SFMClientActionAvailability.available(new Object());
        }

        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("scene", StringArgumentType.word())
                    .executes(context -> Command.SINGLE_SUCCESS));
            if (extraSibling) {
                node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("preview")
                        .then(RequiredArgumentBuilder
                                .<SFMClientActionSource, String>argument(
                                        "preview_mode", StringArgumentType.word())
                                .executes(context -> Command.SINGLE_SUCCESS)));
            }
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            return Command.SINGLE_SUCCESS;
        }
    }

    private static final class StringSceneAction implements SFMClientAction<Object> {
        private final StringArgumentType argumentType;

        private StringSceneAction(StringArgumentType argumentType) {
            this.argumentType = argumentType;
        }

        @Override
        public Component title() {
            return Component.literal("Open string scene");
        }

        @Override
        public Component description() {
            return Component.literal("Test history parsing");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return ignored -> SFMClientActionAvailability.available(new Object());
        }

        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("scene", argumentType)
                    .executes(this::invoke));
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            return Command.SINGLE_SUCCESS;
        }
    }

{% endif %}
{% if features.typed_command_palette %}
    private static final class RequiredStringAction implements SFMClientAction<Object> {
        @Override
        public Component title() {
            return Component.literal("Required string");
        }

        @Override
        public Component description() {
            return Component.literal("Requires one unsuggested message");
        }

        @Override
        public SFMClientActionRequirement<Object> requirement() {
            return ignored -> SFMClientActionAvailability.available(new Object());
        }

        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("message", StringArgumentType.greedyString())
                    .executes(this::invoke));
        }

        @Override
        public int execute(Object target, CommandContext<SFMClientActionSource> context) {
            return Command.SINGLE_SUCCESS;
        }
    }
{% endif %}
}
