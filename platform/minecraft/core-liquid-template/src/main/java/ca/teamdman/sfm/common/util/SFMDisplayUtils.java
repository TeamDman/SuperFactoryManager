package ca.teamdman.sfm.common.util;

import ca.teamdman.sfm.client.text_editor.SFMTextEditorIntellisenseLevel;
import ca.teamdman.sfm.common.config.SFMConfig;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import ca.teamdman.sfml.ast.ASTNode;
import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.intellisense.IntellisenseAction;
import ca.teamdman.sfml.intellisense.IntellisenseContext;
import ca.teamdman.sfml.intellisense.SFMLIntellisense;
import ca.teamdman.sfml.program_builder.ProgramBuildResult;
import com.mojang.datafixers.util.Pair;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.util.List;

public class SFMDisplayUtils {
    public static String getCursorPositionDisplay(
            String programString,
            int cursorPos
    ) {
        StringBuilder rtn = new StringBuilder();
        rtn.append(" [");
        // print the 10-closest characters before the cursor
        int start = Math.max(0, cursorPos - 10);
        int end = cursorPos;
        rtn.append(String.format("%20s", programString.substring(start, end).replaceAll("\n", "\\\\n")));

        // print |
        rtn.append("|");

        // print the 10 characters after the cursor
        start = cursorPos;
        end = Math.min(programString.length(), cursorPos + 10);
        rtn.append(String.format("%-20s", programString.substring(start, end).replaceAll("\n", "\\\\n")));
        rtn.append(" ] ");
        return rtn.toString();
    }

    public static String getCursorTokenDisplay(
            ProgramBuildResult buildResult,
            int cursorPos
    ) {
        var tokens = buildResult.metadata().tokens().getTokens();
        var displayTokens = tokens
                .stream()
                .filter(token -> token.getStartIndex() - 10 <= cursorPos && token.getStopIndex() + 10 >= cursorPos)
                .toList();
        var activeToken = buildResult.getTokenAtCursorPosition(cursorPos);
        if (activeToken == null) {
            return "[ COULDN'T FIND CURSOR TOKEN ]";
        }
        StringBuilder inner = new StringBuilder();
        for (Token displayToken : displayTokens) {
            if (displayToken == activeToken) {
                inner.append(">");
            }
            inner.append(displayToken.getText().replaceAll("\n", "\\\\n"));
            if (displayToken == activeToken) {
                inner.append("<");
            }
            inner.append(" ");
        }
        return String.format("[%20s]", inner);
    }

    public static String getTokenHierarchyDisplay(
            Program program,
            int cursorPos
    ) {
        StringBuilder rtn = new StringBuilder();
        List<Pair<ASTNode, ParserRuleContext>> nodesUnderCursor = program.astBuilder().getNodesUnderCursor(cursorPos);

        var iter = nodesUnderCursor.listIterator(nodesUnderCursor.size());
        while (iter.hasPrevious()) {
            Pair<ASTNode, ParserRuleContext> pair = iter.previous();
            ASTNode node = pair.getFirst();
            rtn.append(node.getClass().getSimpleName());
            if (iter.hasPrevious()) {
                rtn.append(" -> ");
            }
        }
        return rtn.toString();
    }

    public static String getSuggestionsDisplay(
            ProgramBuildResult programBuildResult,
            int cursorPos
    ) {
        StringBuilder rtn = new StringBuilder();
        List<IntellisenseAction> suggestions = SFMLIntellisense.getSuggestions(new IntellisenseContext(
                programBuildResult,
                cursorPos,
                0,
                LabelPositionHolder.empty(),
                SFMConfig.getOrFallback(
                        SFMConfig.CLIENT_TEXT_EDITOR_CONFIG.intellisenseLevel,
                        SFMTextEditorIntellisenseLevel.BASIC
                )
        ));
        rtn.append('[');
        for (int i = 0; i < suggestions.size(); i++) {
            rtn.append(suggestions.get(i).getComponent().getString().replaceAll("\n", "\\\\n"));
            if (i != suggestions.size() - 1) {
                rtn.append(", ");
            }
        }
        rtn.append(']');
        return rtn.toString();
    }
}
