package ca.teamdman.sfm.test.draw;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasModel;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasSyntaxHighlightingHelper;
import ca.teamdman.sfm.SFMProperties;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SFMDrawCanvasSyntaxHighlightingTests {
    private static final int DEFAULT_COLOUR = 0xFFE6EDF3;
    private static final int SPACE_WIDTH = 1;
    private static final int LINE_HEIGHT = 9;

    @Test
    public void projectionMapsCanvasGlyphsToSourceText() {
        List<SFMDrawCanvasModel.CanvasGlyph> glyphs = glyphsFromLines("EVERY 20 TICKS DO", "END");

        var projection = SFMDrawCanvasSyntaxHighlightingHelper.projectCanvasDocument(glyphs, SPACE_WIDTH);

        assertEquals("EVERY 20 TICKS DO\nEND", projection.text());
        assertEquals(glyphs.get(0), projection.glyphsByCharIndex().get(0));
        assertEquals(glyphs.get(5), projection.glyphsByCharIndex().get(5));
        assertEquals(glyphs.get(17), projection.glyphsByCharIndex().get(18));
    }

    @Test
    public void syntaxHighlightingColoursCanvasGlyphs() {
        List<SFMDrawCanvasModel.CanvasGlyph> glyphs = glyphsFromLines("EVERY 20 TICKS DO", "END");

        var colours = SFMDrawCanvasSyntaxHighlightingHelper.buildSyntaxHighlightColours(glyphs, SPACE_WIDTH, DEFAULT_COLOUR);

        assertEquals(formattingToRgb(ChatFormatting.BLUE), colours.get(glyphs.get(0))); // E in EVERY
        assertEquals(formattingToRgb(ChatFormatting.AQUA), colours.get(glyphs.get(6))); // 2 in 20
        assertEquals(formattingToRgb(ChatFormatting.GOLD), colours.get(glyphs.get(9))); // T in TICKS
        assertEquals(formattingToRgb(ChatFormatting.BLUE), colours.get(glyphs.get(17))); // E in END
    }

    @Test
    public void antlrGrammarSyntaxHighlightingColoursCanvasGlyphs() {
        List<SFMDrawCanvasModel.CanvasGlyph> glyphs = glyphsFromLines(
                "grammar Example;",
                "ruleName: TOKEN_REF 'literal';",
                "TOKEN_REF: 'A';",
                "// comment"
        );

        var colours = SFMDrawCanvasSyntaxHighlightingHelper.buildAntlrGrammarHighlightColours(
                glyphs,
                SPACE_WIDTH,
                LINE_HEIGHT,
                DEFAULT_COLOUR
        );

        assertEquals(formattingToRgb(ChatFormatting.BLUE), colours.get(glyphAt(glyphs, 0, 0))); // grammar keyword
        assertEquals(formattingToRgb(ChatFormatting.GREEN), colours.get(glyphAt(glyphs, 1, 0))); // parser rule ref
        assertEquals(formattingToRgb(ChatFormatting.GOLD), colours.get(glyphAt(glyphs, 1, 10))); // lexer token ref
        assertEquals(formattingToRgb(ChatFormatting.LIGHT_PURPLE), colours.get(glyphAt(glyphs, 1, 20))); // string literal
        assertEquals(formattingToRgb(ChatFormatting.GRAY), colours.get(glyphAt(glyphs, 3, 0))); // comment
    }

    @Test
    public void typedSingleLineRoundTripsThroughProjection() {
        assertTypedRoundTrip("EVERY 20 TICKS DO");
    }

    @Test
    public void typedMultipleLinesRoundTripThroughProjection() {
        assertTypedRoundTrip("EVERY 20 TICKS DO\nINPUT FROM a\nEND");
    }

    @Test
    public void typedProgramWithBlankLinesRoundTripsThroughProjection() {
        assertTypedRoundTrip("NAME \"blank lines\"\n\nEVERY 20 TICKS DO\n\nEND");
    }

    @Test
    public void templateProgramsRoundTripThroughProjection() throws IOException {
        Path templatesPath = findDirectoryUpwards("src/main/resources/assets/sfm/template_programs");
        assertNotNull(templatesPath, "Could not locate template programs directory starting from " + SFMProperties.userDirectory());

        int found = 0;
        try (var ds = Files.newDirectoryStream(templatesPath)) {
            for (Path templatePath : ds) {
                String input = Files.readString(templatePath)
                                    .replace("\r\n", "\n")
                                    .replace("$REPLACE_RESOURCE_TYPES_HERE$", "")
                                    .replaceAll("\\n+$", "");
                assertTypedRoundTrip(input, templatePath.toString());
                found++;
            }
        }
        assertNotEquals(0, found);
    }

    @Test
    public void fuzzedTypedProgramsRoundTripThroughProjection() {
        Random random = new Random(0x5FCD);
        for (int i = 0; i < 200; i++) {
            String input = randomProgramText(random);
            assertTypedRoundTrip(input, "fuzz case " + i + ": " + input);
        }
    }

    private static void assertTypedRoundTrip(String input) {
        assertTypedRoundTrip(input, input);
    }

    private static void assertTypedRoundTrip(
            String input,
            String message
    ) {
        SFMDrawCanvasModel canvas = typeInput(input);

        var projection = SFMDrawCanvasSyntaxHighlightingHelper.projectCanvasDocument(canvas.glyphs(), SPACE_WIDTH, LINE_HEIGHT);

        assertEquals(normalizeProjectedText(input), normalizeProjectedText(projection.text()), message);
    }

    private static SFMDrawCanvasModel typeInput(String input) {
        SFMDrawCanvasModel canvas = new SFMDrawCanvasModel();
        canvas.typeText(input, ignored -> 1, LINE_HEIGHT);
        return canvas;
    }

    private static String normalizeProjectedText(String input) {
        return input
                .replaceAll("[ \\t]+(?=\\n|$)", "")
                .strip();
    }

    private static String randomProgramText(Random random) {
        int lines = 1 + random.nextInt(8);
        StringBuilder builder = new StringBuilder();
        for (int line = 0; line < lines; line++) {
            if (line != 0) {
                builder.append('\n');
            }
            if (line != 0 && line != lines - 1 && random.nextInt(5) == 0) {
                continue;
            }
            int length = 1 + random.nextInt(40);
            for (int i = 0; i < length; i++) {
                builder.append(randomProgramChar(random));
            }
        }
        return builder.toString();
    }

    private static char randomProgramChar(Random random) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 _-:/\"'().,*";
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }

    private static Path findDirectoryUpwards(String relativePath) {
        Path cwd = SFMProperties.userDirectory();
        for (int i = 0; i < 5; i++) {
            Path candidate = cwd.resolve(relativePath);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cwd = cwd.getParent();
            if (cwd == null) {
                break;
            }
        }
        return null;
    }

    private static List<SFMDrawCanvasModel.CanvasGlyph> glyphsFromLines(String... lines) {
        List<SFMDrawCanvasModel.CanvasGlyph> glyphs = new ArrayList<>();
        for (int y = 0; y < lines.length; y++) {
            String line = lines[y];
            for (int x = 0; x < line.length(); x++) {
                glyphs.add(new SFMDrawCanvasModel.CanvasGlyph(Character.toString(line.charAt(x)), x, y, 1));
            }
        }
        return glyphs;
    }

    private static SFMDrawCanvasModel.CanvasGlyph glyphAt(
            List<SFMDrawCanvasModel.CanvasGlyph> glyphs,
            int line,
            int column
    ) {
        return glyphs.stream()
                .filter(glyph -> glyph.y() == line && glyph.x() == column)
                .findFirst()
                .orElseThrow();
    }

    private static int formattingToRgb(ChatFormatting formatting) {
        Integer colour = formatting.getColor();
        return colour == null ? DEFAULT_COLOUR : 0xFF000000 | colour;
    }
}
