package ca.teamdman.sfm.client.action;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SFMTextEditorSearchActionTests {
    @Test void onlyExactQueryAndOptionPrefixesOptIntoContextualConstruction() {
        for (var kind : SFMTextEditorSearchAction.Kind.values()) {
            SFMClientActionCompletion action = new SFMTextEditorSearchAction(kind);
            String prefix = "sfm action invoke sfm:document/search/" + kind.name().toLowerCase(java.util.Locale.ROOT);
            assertEquals(kind != SFMTextEditorSearchAction.Kind.SELECT, action.acceptsContinuation(prefix, null));
            assertFalse(action.acceptsContinuation(prefix + " invalid", null));
            assertFalse(action.acceptsContinuation("sfm action invoke sfm:panel/close", null));
            assertTrue(action.argumentCandidates(prefix, prefix.length(), prefix.length(), null).isEmpty());
        }
    }
    @Test void grammarDiscoversEveryOptionAndRequiresAnExplicitSelectMode() throws Exception {
        var dispatcher = new CommandDispatcher<SFMClientActionSource>();
        for (var kind : SFMTextEditorSearchAction.Kind.values()) {
            var node = LiteralArgumentBuilder.<SFMClientActionSource>literal(kind.name().toLowerCase(java.util.Locale.ROOT));
            new SFMTextEditorSearchAction(kind).configureCommandNode(node);
            dispatcher.register(node);
        }
        var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("toggle ", null)).get();
        assertEquals(List.of("case", "dot-all", "fuzzy", "regex", "whole-word"),
                suggestions.getList().stream().map(s -> s.getText()).toList());
        for (String command : List.of("select add-next", "select all", "query \"😀 hi\"", "query \"\"")) {
            var parsed = dispatcher.parse(command, null);
            assertFalse(parsed.getReader().canRead());
            assertNotNull(parsed.getContext().getCommand());
        }
        assertNull(dispatcher.parse("select", null).getContext().getCommand());
        assertTrue(dispatcher.parse("select unknown", null).getReader().canRead());
    }
}
