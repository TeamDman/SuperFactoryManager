package ca.teamdman.sfm.client.screen;

import ca.teamdman.langs.ANTLRv4Lexer;
import ca.teamdman.sfm.client.text_styling.ProgramSyntaxHighlightingHelper;
import net.minecraft.ChatFormatting;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class SFMDrawCanvasSyntaxHighlightingHelper {
    public static Map<SFMDrawCanvasModel.CanvasGlyph, Integer> buildSyntaxHighlightColours(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth,
            int defaultColour
    ) {
        CanvasDocumentProjection projection = projectCanvasDocument(sourceGlyphs, spaceWidth);
        return buildSyntaxHighlightColours(projection, defaultColour);
    }

    public static Map<SFMDrawCanvasModel.CanvasGlyph, Integer> buildSyntaxHighlightColours(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth,
            int lineHeight,
            int defaultColour
    ) {
        CanvasDocumentProjection projection = projectCanvasDocument(sourceGlyphs, spaceWidth, lineHeight);
        return buildSyntaxHighlightColours(projection, defaultColour);
    }

    public static Map<SFMDrawCanvasModel.CanvasGlyph, Integer> buildAntlrGrammarHighlightColours(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth,
            int lineHeight,
            int defaultColour
    ) {
        CanvasDocumentProjection projection = projectCanvasDocument(sourceGlyphs, spaceWidth, lineHeight);
        Map<SFMDrawCanvasModel.CanvasGlyph, Integer> colours = new IdentityHashMap<>();
        if (projection.text().isEmpty()) {
            return colours;
        }

        ANTLRv4Lexer lexer = new ANTLRv4Lexer(CharStreams.fromString(projection.text()));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        for (Token token : tokens.getTokens()) {
            if (token.getType() == ANTLRv4Lexer.EOF) {
                break;
            }
            int colour = formattingToRgb(getAntlrGrammarColour(token), defaultColour);
            for (int index = token.getStartIndex(); index <= token.getStopIndex() && index < projection.glyphsByCharIndex().size(); index++) {
                SFMDrawCanvasModel.CanvasGlyph glyph = projection.glyphsByCharIndex().get(index);
                if (glyph != null) {
                    colours.put(glyph, colour);
                }
            }
        }
        return colours;
    }

    private static Map<SFMDrawCanvasModel.CanvasGlyph, Integer> buildSyntaxHighlightColours(
            CanvasDocumentProjection projection,
            int defaultColour
    ) {
        Map<SFMDrawCanvasModel.CanvasGlyph, Integer> colours = new IdentityHashMap<>();
        if (projection.text().isEmpty()) {
            return colours;
        }
        for (ProgramSyntaxHighlightingHelper.TokenHighlight highlight : ProgramSyntaxHighlightingHelper.getTokenHighlights(projection.text())) {
            int colour = highlight.colour();
            for (int index = highlight.startIndex(); index <= highlight.stopIndex() && index < projection.glyphsByCharIndex().size(); index++) {
                SFMDrawCanvasModel.CanvasGlyph glyph = projection.glyphsByCharIndex().get(index);
                if (glyph != null) {
                    colours.put(glyph, colour);
                }
            }
        }
        return colours;
    }

    public static CanvasDocumentProjection projectCanvasDocument(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth
    ) {
        return projectCanvasDocument(sourceGlyphs, spaceWidth, null);
    }

    public static CanvasDocumentProjection projectCanvasDocument(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth,
            int lineHeight
    ) {
        return projectCanvasDocument(sourceGlyphs, spaceWidth, Integer.valueOf(Math.max(1, lineHeight)));
    }

    private static CanvasDocumentProjection projectCanvasDocument(
            List<SFMDrawCanvasModel.CanvasGlyph> sourceGlyphs,
            int spaceWidth,
            Integer lineHeight
    ) {
        List<SFMDrawCanvasModel.CanvasGlyph> glyphs = new ArrayList<>(sourceGlyphs);
        glyphs.sort((left, right) -> {
            int yCompare = Double.compare(left.y(), right.y());
            if (yCompare != 0) {
                return yCompare;
            }
            return Double.compare(left.x(), right.x());
        });

        StringBuilder text = new StringBuilder();
        List<SFMDrawCanvasModel.CanvasGlyph> glyphsByCharIndex = new ArrayList<>();
        Double currentY = null;
        double lineEndX = 0.0D;
        int safeSpaceWidth = Math.max(1, spaceWidth);
        Integer safeLineHeight = lineHeight == null ? null : Math.max(1, lineHeight);
        for (SFMDrawCanvasModel.CanvasGlyph glyph : glyphs) {
            if (currentY == null || Double.compare(currentY, glyph.y()) != 0) {
                if (currentY != null) {
                    int newlineCount = countNewlinesBetweenRows(currentY, glyph.y(), safeLineHeight);
                    for (int i = 0; i < newlineCount; i++) {
                        text.append('\n');
                        glyphsByCharIndex.add(null);
                    }
                }
                currentY = glyph.y();
                lineEndX = 0.0D;
            }
            int inferredSpaces = Math.max(0, (int) Math.floor((glyph.x() - lineEndX) / safeSpaceWidth));
            for (int i = 0; i < inferredSpaces; i++) {
                text.append(' ');
                glyphsByCharIndex.add(null);
            }
            for (int i = 0; i < glyph.text().length(); i++) {
                text.append(glyph.text().charAt(i));
                glyphsByCharIndex.add(glyph);
            }
            lineEndX = Math.max(lineEndX, glyph.x() + glyph.width());
        }
        return new CanvasDocumentProjection(text.toString(), glyphsByCharIndex);
    }

    private static int countNewlinesBetweenRows(
            double currentY,
            double nextY,
            Integer lineHeight
    ) {
        if (lineHeight == null) {
            return 1;
        }
        return Math.max(1, (int) Math.round((nextY - currentY) / lineHeight));
    }

    private static int formattingToRgb(
            ChatFormatting formatting,
            int defaultColour
    ) {
        Integer colour = formatting.getColor();
        return colour == null ? defaultColour : 0xFF000000 | colour;
    }

    private static ChatFormatting getAntlrGrammarColour(Token token) {
        if (token.getChannel() == ANTLRv4Lexer.COMMENT) {
            return ChatFormatting.GRAY;
        }
        return switch (token.getType()) {
            case ANTLRv4Lexer.GRAMMAR,
                 ANTLRv4Lexer.LEXER,
                 ANTLRv4Lexer.PARSER,
                 ANTLRv4Lexer.IMPORT,
                 ANTLRv4Lexer.OPTIONS,
                 ANTLRv4Lexer.TOKENS,
                 ANTLRv4Lexer.CHANNELS,
                 ANTLRv4Lexer.FRAGMENT,
                 ANTLRv4Lexer.MODE,
                 ANTLRv4Lexer.RETURNS,
                 ANTLRv4Lexer.LOCALS,
                 ANTLRv4Lexer.THROWS,
                 ANTLRv4Lexer.CATCH,
                 ANTLRv4Lexer.FINALLY -> ChatFormatting.BLUE;
            case ANTLRv4Lexer.TOKEN_REF -> ChatFormatting.GOLD;
            case ANTLRv4Lexer.RULE_REF -> ChatFormatting.GREEN;
            case ANTLRv4Lexer.STRING_LITERAL,
                 ANTLRv4Lexer.UNTERMINATED_STRING_LITERAL -> ChatFormatting.LIGHT_PURPLE;
            case ANTLRv4Lexer.ACTION,
                 ANTLRv4Lexer.ARG_ACTION,
                 ANTLRv4Lexer.ARGUMENT_CONTENT,
                 ANTLRv4Lexer.LEXER_CHAR_SET -> ChatFormatting.AQUA;
            case ANTLRv4Lexer.INT -> ChatFormatting.YELLOW;
            default -> ChatFormatting.WHITE;
        };
    }

    public record CanvasDocumentProjection(
            String text,
            List<SFMDrawCanvasModel.CanvasGlyph> glyphsByCharIndex
    ) {
    }
}
