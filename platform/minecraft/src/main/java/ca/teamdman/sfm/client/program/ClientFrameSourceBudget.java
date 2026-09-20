package ca.teamdman.sfm.client.program;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.sfm.common.blockentity.ClientManagerProgramProjection;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.Program;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.nio.charset.StandardCharsets;

/** Iterative lexical guard runs before recursive parser/AST work on client programs. */
public final class ClientFrameSourceBudget {
    public static final int MAX_TOKENS = 4096;

    private ClientFrameSourceBudget() {}

    public static boolean permits(String source) {
        if (source.length() > Program.MAX_PROGRAM_LENGTH
            || source.getBytes(StandardCharsets.UTF_8).length > ClientManagerProgramProjection.MAX_SOURCE_BYTES) return false;
        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        int count = 0;
        int scopes = 0;
        int parentheses = 0;
        int booleanOperators = 0;
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            if (token.getChannel() != Token.DEFAULT_CHANNEL) continue;
            if (++count > MAX_TOKENS) return false;
            switch (token.getType()) {
                // Count ELSE IF conservatively as well: its AST is a nested branch chain.
                case SFMLLexer.IF, SFMLLexer.EVERY -> scopes++;
                case SFMLLexer.END -> { scopes = Math.max(0, scopes - 1); booleanOperators = 0; }
                case SFMLLexer.LPAREN -> parentheses++;
                case SFMLLexer.RPAREN -> parentheses = Math.max(0, parentheses - 1);
                case SFMLLexer.NOT, SFMLLexer.AND, SFMLLexer.OR -> booleanOperators++;
                case SFMLLexer.THEN, SFMLLexer.DO -> booleanOperators = 0;
                default -> { }
            }
            if (scopes > FrameTrigger.MAX_NESTING || parentheses > FrameTrigger.MAX_NESTING
                || booleanOperators > FrameTrigger.MAX_NESTING) return false;
        }
        return true;
    }
}
