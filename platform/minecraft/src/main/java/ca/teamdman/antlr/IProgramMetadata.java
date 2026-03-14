package ca.teamdman.antlr;

import net.minecraft.network.chat.contents.TranslatableContents;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.Token;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Context from the build process of transforming a {@link String} into an {@link IAstNode}.
/// This helps with operations like getting the source-code position of a given {@link IAstNode}.
public interface IProgramMetadata<ASTNODE extends IAstNode<?>, LEXER extends Lexer, PARSER extends Parser, BUILDER extends IAstBuilder<ASTNODE>> {
    String programString();

    LEXER lexer();

    CommonTokenStream tokens();

    PARSER parser();

    BUILDER astBuilder();

    List<TranslatableContents> errors();

    default @Nullable Token getTokenAtCursorPosition(int cursorPos) {

        for (Token token : tokens().getTokens()) {
            if (token.getStartIndex() <= cursorPos && token.getStopIndex() + 1 >= cursorPos) {
                return token;
            }
        }
        return null;
    }

    /**
     * @param cursorPos The cursor position
     * @return The sequence of non-whitespace characters to the left of the cursor
     */
    default String getWordAtCursorPosition(int cursorPos) {

        StringBuilder word = new StringBuilder();
        for (int i = cursorPos - 1; i >= 0; i--) {
            char c = this.programString().charAt(i);
            if (Character.isWhitespace(c)) {
                break;
            }
            word.insert(0, c);
        }
        return word.toString();
    }
}
