package ca.teamdman.sfm.client.text_styling;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

public class ProgramSyntaxHighlightingHelper {

    public static List<MutableComponent> withSyntaxHighlighting(String programString, boolean showContextActionHints) {
        SFMLLexer lexer = new SFMLLexer(CharStreams.fromString(programString));
        lexer.INCLUDE_UNUSED = true;
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
            if (token.getType() == SFMLLexer.EOF) break;
            // the token may contain newlines in it, so we need to split it up
            String[] lines = token.getText().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i != 0) {
                    textComponents.add(lineComponent);
                    lineComponent = Component.empty();
                }
                String line = lines[i];
                if (!line.isEmpty()) {
                    var text = Component.literal(line).withStyle(getStyle(token, showContextActionHints));
                    lineComponent = lineComponent.append(text);
                }
            }
        }
        textComponents.add(lineComponent);

        return textComponents;
    }

    private static Style getStyle(Token token, boolean showContextActionHints) {
        Style style = Style.EMPTY;
        style = style.withColor(getColour(token));
        if (showContextActionHints && ProgramTokenContextActions.hasContextAction(token)) {
            style = style.withUnderlined(true);
        }
        return style;
    }

    private static ChatFormatting getColour(Token token) {
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
            case SFMLLexer.NUMBER_WITH_G_SUFFIX:
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
}
