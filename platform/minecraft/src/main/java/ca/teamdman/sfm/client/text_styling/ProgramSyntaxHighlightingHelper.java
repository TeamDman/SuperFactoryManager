package ca.teamdman.sfm.client.text_styling;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.langs.TomlLexer;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
import ca.teamdman.sfm.client.text_editor.TextEditScreenContentLanguage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class ProgramSyntaxHighlightingHelper {

    public static List<MutableComponent> withSyntaxHighlighting(
            String programString,
            boolean showContextActionHints,
            TextEditScreenContentLanguage language
    ) {
        if (language == TextEditScreenContentLanguage.PLAINTEXT) {
            return getPlaintextComponents(programString);
        }

        Lexer lexer = createLexer(programString, language);
        CommonTokenStream tokens = new CommonTokenStream(lexer) {
            // This is a hack to make hidden tokens show up in the token stream
            @Override
            public List<Token> getHiddenTokensToRight(int tokenIndex, int channel) {
                if (channel == Token.DEFAULT_CHANNEL) {
                    return getHiddenTokensToRight(tokenIndex, Token.HIDDEN_CHANNEL);
                } else {
                    return super.getHiddenTokensToRight(tokenIndex, channel);
                }
            }

            @Override
            public List<Token> getHiddenTokensToLeft(int tokenIndex, int channel) {
                if (channel == Token.DEFAULT_CHANNEL) {
                    return getHiddenTokensToLeft(tokenIndex, Token.HIDDEN_CHANNEL);
                } else {
                    return super.getHiddenTokensToLeft(tokenIndex, channel);
                }
            }
        };
        List<MutableComponent> textComponents = new ArrayList<>();
        MutableComponent lineComponent = Component.empty();
        tokens.fill();
        for (Token token : tokens.getTokens()) {
            if (token.getType() == Token.EOF) break;
            // the token may contain newlines in it, so we need to split it up
            String[] lines = token.getText().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i != 0) {
                    textComponents.add(lineComponent);
                    lineComponent = Component.empty();
                }
                String line = lines[i];
                if (!line.isEmpty()) {
                    var text = Component.literal(line).withStyle(getStyle(token, showContextActionHints, language));
                    lineComponent = lineComponent.append(text);
                }
            }
        }
        textComponents.add(lineComponent);

        return textComponents;
    }

    private static Lexer createLexer(String programString, TextEditScreenContentLanguage language) {
        return switch (language) {
            case SFML -> {
                SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(programString));
                lexer.INCLUDE_UNUSED = true;
                yield lexer;
            }
            case TOML -> {
                TomlLexer lexer = new TomlLexer(CharStreams.fromString(programString));
                lexer.INCLUDE_UNUSED = true;
                yield lexer;
            }
            case PLAINTEXT -> throw new IllegalArgumentException("PLAINTEXT should be handled separately");
        };
    }

    private static List<MutableComponent> getPlaintextComponents(String programString) {
        List<MutableComponent> textComponents = new ArrayList<>();
        String[] lines = programString.split("\n", -1);
        for (String line : lines) {
            textComponents.add(Component.literal(line));
        }
        return textComponents;
    }

    private static Style getStyle(Token token, boolean showContextActionHints, TextEditScreenContentLanguage language) {
        Style style = Style.EMPTY;
        style = style.withColor(getColour(token, language));
        if (showContextActionHints && language == TextEditScreenContentLanguage.SFML && ProgramTokenContextActions.hasContextAction(token)) {
            style = style.withUnderlined(true);
        }
        return style;
    }

    private static ChatFormatting getColour(Token token, TextEditScreenContentLanguage language) {
        return switch (language) {
            case SFML -> getSfmlColour(token);
            case TOML -> getTomlColour(token);
            case PLAINTEXT -> ChatFormatting.WHITE;
        };
    }

    private static ChatFormatting getSfmlColour(Token token) {
        //noinspection EnhancedSwitchMigration
        switch (token.getType()) {
            case SFMLLexer.SIDE:
            case SFMLLexer.TOP:
            case SFMLLexer.BOTTOM:
            case SFMLLexer.NORTH:
            case SFMLLexer.SOUTH:
            case SFMLLexer.EAST:
            case SFMLLexer.WEST:
            case SFMLLexer.EACH:
            case SFMLLexer.LEFT:
            case SFMLLexer.RIGHT:
            case SFMLLexer.FRONT:
            case SFMLLexer.BACK:
                return ChatFormatting.DARK_PURPLE;
            case SFMLLexer.LINE_COMMENT:
                return ChatFormatting.GRAY;
            case SFMLLexer.INPUT:
            case SFMLLexer.FROM:
            case SFMLLexer.TO:
            case SFMLLexer.OUTPUT:
                return ChatFormatting.LIGHT_PURPLE;
            case SFMLLexer.NAME:
            case SFMLLexer.EVERY:
            case SFMLLexer.END:
            case SFMLLexer.DO:
            case SFMLLexer.IF:
            case SFMLLexer.ELSE:
            case SFMLLexer.THEN:
            case SFMLLexer.HAS:
            case SFMLLexer.TRUE:
            case SFMLLexer.FALSE:
            case SFMLLexer.FORGET:
                return ChatFormatting.BLUE;
            case SFMLLexer.IDENTIFIER:
            case SFMLLexer.STRING:
                return ChatFormatting.GREEN;
            case SFMLLexer.TICKS:
            case SFMLLexer.TICK:
            case SFMLLexer.GLOBAL:
            case SFMLLexer.SECONDS:
            case SFMLLexer.SECOND:
            case SFMLLexer.SLOTS:
            case SFMLLexer.SLOT:
            case SFMLLexer.EXCEPT:
            case SFMLLexer.RETAIN:
            case SFMLLexer.LONE:
            case SFMLLexer.ONE:
            case SFMLLexer.OVERALL:
            case SFMLLexer.SOME:
            case SFMLLexer.AND:
            case SFMLLexer.NOT:
            case SFMLLexer.OR:
            case SFMLLexer.IN:
            case SFMLLexer.EMPTY:
                return ChatFormatting.GOLD;
            case SFMLLexer.NUMBER:
            case SFMLLexer.PLUS:
            case SFMLLexer.GT:
            case SFMLLexer.LT:
            case SFMLLexer.EQ:
            case SFMLLexer.GE:
            case SFMLLexer.LE:
            case SFMLLexer.GT_SYMBOL:
            case SFMLLexer.LT_SYMBOL:
            case SFMLLexer.EQ_SYMBOL:
            case SFMLLexer.GE_SYMBOL:
            case SFMLLexer.LE_SYMBOL:
            case SFMLLexer.WITH:
            case SFMLLexer.WITHOUT:
            case SFMLLexer.HASHTAG:
            case SFMLLexer.TAG:
                return ChatFormatting.AQUA;
            case SFMLLexer.UNUSED:
            case SFMLLexer.REDSTONE:
            case SFMLLexer.PULSE:
                return ChatFormatting.RED;
            case SFMLLexer.ROUND:
            case SFMLLexer.ROBIN:
            case SFMLLexer.BY:
            case SFMLLexer.BLOCK:
            case SFMLLexer.LABEL:
                return ChatFormatting.YELLOW;
            default:
                return ChatFormatting.WHITE;
        }
    }

    private static ChatFormatting getTomlColour(Token token) {
        //noinspection EnhancedSwitchMigration
        switch (token.getType()) {
            case TomlLexer.COMMENT:
                return ChatFormatting.GRAY;
            case TomlLexer.BASIC_STRING:
            case TomlLexer.ML_BASIC_STRING:
            case TomlLexer.LITERAL_STRING:
            case TomlLexer.ML_LITERAL_STRING:
                return ChatFormatting.GREEN;
            case TomlLexer.UNQUOTED_KEY:
                return ChatFormatting.AQUA;
            case TomlLexer.DEC_INT:
            case TomlLexer.HEX_INT:
            case TomlLexer.OCT_INT:
            case TomlLexer.BIN_INT:
            case TomlLexer.FLOAT:
            case TomlLexer.INF:
            case TomlLexer.NAN:
                return ChatFormatting.GOLD;
            case TomlLexer.BOOLEAN:
                return ChatFormatting.BLUE;
            case TomlLexer.OFFSET_DATE_TIME:
            case TomlLexer.LOCAL_DATE_TIME:
            case TomlLexer.LOCAL_DATE:
            case TomlLexer.LOCAL_TIME:
                return ChatFormatting.LIGHT_PURPLE;
            case TomlLexer.L_BRACKET:
            case TomlLexer.DOUBLE_L_BRACKET:
            case TomlLexer.R_BRACKET:
            case TomlLexer.DOUBLE_R_BRACKET:
            case TomlLexer.L_BRACE:
            case TomlLexer.R_BRACE:
                return ChatFormatting.YELLOW;
            case TomlLexer.EQUALS:
            case TomlLexer.DOT:
            case TomlLexer.COMMA:
                return ChatFormatting.WHITE;
            default:
                return ChatFormatting.WHITE;
        }
    }
}
