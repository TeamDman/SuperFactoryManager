package ca.teamdman.sfm.client.text_styling;

import ca.teamdman.langs.SFMLLexer;
import ca.teamdman.sfm.client.ProgramTokenContextActions;
{% if features.client_theme %}
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMSyntaxStyle;
{% else %}
import net.minecraft.ChatFormatting;
{% endif %}
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
{% if features.client_theme %}
        List<TokenHighlight> highlights = getTokenHighlights(programString);
        List<MutableComponent> textComponents = new ArrayList<>();
        MutableComponent lineComponent = Component.empty();
        for (TokenHighlight highlight : highlights) {
            // the token may contain newlines in it, so we need to split it up
            String[] lines = highlight.text().split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (i != 0) {
                    textComponents.add(lineComponent);
                    lineComponent = Component.empty();
                }
                String line = lines[i];
                if (!line.isEmpty()) {
                    var text = Component.literal(line).withStyle(getStyle(highlight.token(), showContextActionHints));
                    lineComponent = lineComponent.append(text);
                }
            }
        }
        textComponents.add(lineComponent);

        return textComponents;
    }

    public static List<TokenHighlight> getTokenHighlights(String programString) {
{% endif %}
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
{% if features.client_theme %}
        List<TokenHighlight> highlights = new ArrayList<>();
{% else %}
        List<MutableComponent> textComponents = new ArrayList<>();
        MutableComponent lineComponent = Component.empty();
{% endif %}
        tokens.fill();
        for (Token token : tokens.getTokens()) {
            if (token.getType() == SFMLLexer.EOF) break;
{% if features.client_theme %}
            SFMSyntaxStyle style = SFMClientThemeService.active().syntax(syntaxTokenId(token));
            highlights.add(new TokenHighlight(token.getStartIndex(), token.getStopIndex(), token.getText(), style.colour(), token));
{% else %}
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
{% endif %}
        }
{% if features.client_theme %}
        return highlights;
{% else %}
        textComponents.add(lineComponent);

        return textComponents;
{% endif %}
    }

    private static Style getStyle(Token token, boolean showContextActionHints) {
{% if features.client_theme %}
        Style style = SFMClientThemeService.active().syntax(syntaxTokenId(token)).apply(Style.EMPTY);
{% else %}
        Style style = Style.EMPTY;
        style = style.withColor(getColour(token));
{% endif %}
        if (showContextActionHints && ProgramTokenContextActions.hasContextAction(token)) {
            style = style.withUnderlined(true);
        }
        return style;
    }

{% if features.client_theme %}
    public static String syntaxTokenId(Token token) {
{% else %}
    private static ChatFormatting getColour(Token token) {
{% endif %}
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
{% if features.client_theme %}
                return "direction";
{% else %}
                return ChatFormatting.DARK_PURPLE;
{% endif %}
            case SFMLLexer.LINE_COMMENT:
{% if features.client_theme %}
                return "comment";
{% else %}
                return ChatFormatting.GRAY;
{% endif %}
            case SFMLLexer.INPUT:
            case SFMLLexer.FROM:
            case SFMLLexer.TO:
            case SFMLLexer.OUTPUT:
{% if features.client_theme %}
                return "io";
{% else %}
                return ChatFormatting.LIGHT_PURPLE;
{% endif %}
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
{% if features.client_theme %}
{% if features.packet_computation %}
            case SFMLLexer.LET:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.BE:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.PLAYER:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.LIKE:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.OBJECT:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.INVOKE:
{% endif %}
{% if features.client_program_actions %}
            case SFMLLexer.JSON:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.CREATE:
{% endif %}
{% if features.packet_transport_private %}
            case SFMLLexer.BROADCAST:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.NEW:
{% endif %}
                return "keyword";
{% else %}
                return ChatFormatting.BLUE;
{% endif %}
            case SFMLLexer.IDENTIFIER:
            case SFMLLexer.STRING:
{% if features.client_theme %}
                return "string";
{% else %}
                return ChatFormatting.GREEN;
{% endif %}
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
{% if features.client_theme %}
{% if features.packet_computation %}
            case SFMLLexer.OF:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.FIELD:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.GUID:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.STRING_TYPE:
{% endif %}
{% if features.packet_computation %}
            case SFMLLexer.CAPABILITY:
{% endif %}
{% if features.packet_computation or features.client_frame_language %}
            case SFMLLexer.AS:
{% endif %}
                return "modifier";
{% else %}
                return ChatFormatting.GOLD;
{% endif %}
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
{% if features.client_theme %}
                return "number";
{% else %}
                return ChatFormatting.AQUA;
{% endif %}
            case SFMLLexer.UNUSED:
            case SFMLLexer.REDSTONE:
            case SFMLLexer.PULSE:
{% if features.client_theme %}
                return "redstone";
{% else %}
                return ChatFormatting.RED;
{% endif %}
            case SFMLLexer.ROUND:
            case SFMLLexer.ROBIN:
            case SFMLLexer.BY:
            case SFMLLexer.BLOCK:
            case SFMLLexer.LABEL:
{% if features.client_theme %}
                return "round_robin";
{% else %}
                return ChatFormatting.YELLOW;
{% endif %}
            default:
{% if features.client_theme %}
                return "default";
{% else %}
                return ChatFormatting.WHITE;
{% endif %}
        }
    }
{% if features.client_theme %}

    public record TokenHighlight(
            int startIndex,
            int stopIndex,
            String text,
            int colour,
            Token token
    ) {
    }
{% endif %}
}
