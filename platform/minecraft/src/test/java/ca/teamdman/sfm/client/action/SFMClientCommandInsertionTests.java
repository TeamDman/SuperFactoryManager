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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SFMClientCommandInsertionTests {
    private static final ResourceLocation ECHO = new ResourceLocation("sfm", "echo");
    private static final ResourceLocation TERMINAL = new ResourceLocation("sfm", "terminal");
    private static final ResourceLocation OPTIONAL = new ResourceLocation("sfm", "optional");
    private final SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(
            List.<Map.Entry<ResourceLocation, SFMClientAction<?>>>of(
                    Map.entry(ECHO, new EchoAction()),
                    Map.entry(TERMINAL, new TerminalAction()),
                    Map.entry(OPTIONAL, new OptionalAction())
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
}
