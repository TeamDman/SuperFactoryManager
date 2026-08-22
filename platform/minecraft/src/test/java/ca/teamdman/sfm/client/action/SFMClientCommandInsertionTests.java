package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMClientCommandInsertionTests {
    private static final ResourceLocation ECHO = new ResourceLocation("sfm", "echo");
    private static final ResourceLocation TERMINAL = new ResourceLocation("sfm", "terminal");
    private static final ResourceLocation OPTIONAL = new ResourceLocation("sfm", "optional");
    private static final ResourceLocation PANEL_OPEN = new ResourceLocation("sfm", "panel/open");
    private static final ResourceLocation PANEL_OPEN_RIGHT = new ResourceLocation("sfm", "panel/open/right");
    private final SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(
            List.<Map.Entry<ResourceLocation, SFMClientAction<?>>>of(
                    Map.entry(ECHO, new EchoAction()),
                    Map.entry(TERMINAL, new TerminalAction()),
                    Map.entry(OPTIONAL, new OptionalAction()),
                    Map.entry(PANEL_OPEN, new PanelOpenAction()),
                    Map.entry(PANEL_OPEN_RIGHT, new PanelOpenAction())
            )
    );
    private final SFMClientActionSource source = new SFMClientActionSource(
            SFMClientActionContext.create(new Object(), () -> true)
    );

    @Test
    public void requiredArgumentActionGetsExactlyOneSeparator() {
        assertEquals("sfm action invoke sfm:echo ", prepare("sfm action invoke sfm:echo"));
        assertEquals("sfm action invoke sfm:echo ", prepare("sfm action invoke sfm:echo "));
        org.junit.jupiter.api.Assertions.assertTrue(SFMClientCommandInsertion.isAwaitingRequiredArgument(
                "sfm action invoke sfm:echo ", tree, source
        ));
    }

    @Test
    public void requiredArgumentActionDoesNotPretendItsFreeFormValueIsASubAction() {
        var parsed = tree.parse("sfm action invoke sfm:echo", source);
        org.junit.jupiter.api.Assertions.assertFalse(
                SFMClientCommandInsertion.hasAvailableLiteralChildren(parsed));
    }

    @Test
    public void terminalAndOptionalActionsRemainExecutableWithoutMutation() {
        assertEquals("sfm action invoke sfm:terminal", prepare("sfm action invoke sfm:terminal"));
        assertEquals("sfm action invoke sfm:optional", prepare("sfm action invoke sfm:optional"));
    }

    @Test
    public void enteredAndQuotedArgumentsRemainUnchanged() {
        assertEquals("sfm action invoke sfm:echo hello", prepare("sfm action invoke sfm:echo hello"));
        assertEquals("sfm action invoke sfm:echo \"hello world\"", prepare("sfm action invoke sfm:echo \"hello world\""));
    }

    @Test
    public void invalidAndResourceLocationInProgressInputsRemainUnchanged() {
        assertEquals("sfm action invoke sfm:missing", prepare("sfm action invoke sfm:missing"));
        assertEquals("sfm action invoke sfm:ec", prepare("sfm action invoke sfm:ec"));
        assertEquals("sfm action invoke", prepare("sfm action invoke"));
    }

    @Test
    public void paletteTabMustNotUseExecutionPreparationAtAnActionBoundary() {
        String query = "sfm action invoke open";
        SFMPaletteCandidate selected = tree.getPaletteCandidates(query, tree.parse(query, source))
                .join()
                .get(0);
        assertEquals("sfm:panel/open", selected.replacementText());

        SFMCompletionApplication application = selected.apply(query);

        assertEquals("sfm action invoke sfm:panel/open", application.afterValue());
        assertEquals("", application.deliberateSeparator());
        assertEquals(SFMPaletteCandidate.Kind.ACTION_BOUNDARY, application.candidateKind());
        assertEquals(SFMPaletteCandidate.Origin.ACTION_REGISTRY, application.candidateOrigin());
        assertEquals(selected.replacementRange(), application.replacementRange());
        assertEquals(selected.replacementText(), application.replacementText());
    }

    @Test
    public void repeatedTabAtAnExactBoundaryProgressesToAStrictActionDescendant() {
        String current = "sfm action invoke sfm:panel/open";
        List<SFMPaletteCandidate> candidates = tree.getPaletteCandidates(current, tree.parse(current, source))
                .join();
        assertEquals("sfm:panel/open", candidates.get(0).replacementText());
        assertEquals(current, candidates.get(0).apply(current).afterValue());

        OptionalInt progressing = SFMPaletteCandidate.progressingIndex(candidates, 0, current);

        assertTrue(progressing.isPresent());
        assertEquals("sfm:panel/open/right", candidates.get(progressing.getAsInt()).replacementText());
        assertEquals("sfm action invoke sfm:panel/open/right",
                candidates.get(progressing.getAsInt()).apply(current).afterValue());
    }

    @Test
    public void explicitSpaceEntersTheSceneFrontier() {
        String command = "sfm action invoke sfm:panel/open ";

        List<String> candidates = tree.getPaletteCandidates(command, tree.parse(command, source))
                .join().stream()
                .filter(SFMPaletteCandidate::activatable)
                .map(SFMPaletteCandidate::replacementText)
                .toList();

        assertEquals(List.of("sfm:text_editor"), candidates);
    }

    private String prepare(String command) {
        return SFMClientCommandInsertion.prepare(command, tree, source);
    }

    private static class TerminalAction implements SFMClientAction<Object> {
        @Override public Component title() { return Component.literal("Terminal"); }
        @Override public Component description() { return Component.literal("Terminal action"); }
        @Override public SFMClientActionRequirement<Object> requirement() { return context -> SFMClientActionAvailability.available(new Object()); }
        @Override public int execute(Object target, CommandContext<SFMClientActionSource> context) { return 1; }
    }

    private static final class OptionalAction extends TerminalAction {
        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.executes(this::invoke).then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("optional", StringArgumentType.word())
                    .executes(this::invoke));
        }
    }

    private static final class PanelOpenAction extends TerminalAction {
        @Override
        public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
            node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("sfm:text_editor")
                    .executes(this::invoke));
        }
    }
}
