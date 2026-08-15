package ca.teamdman.sfm.client.screen;

import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SFMDrawCanvasRemoteSyntaxStylesTests {
    @Test
    void projectsUtf8SpansOntoExactUnicodeGlyphs() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.pasteText("class Café {\r\n  String emoji = \"😀\";\r\n}", String::length, 9);
        String source = model.projectedText(1, 9);
        int keywordEnd = "class".getBytes(StandardCharsets.UTF_8).length;
        int stringStart = source.indexOf("\"😀\"");
        int stringStartByte = source.substring(0, stringStart).getBytes(StandardCharsets.UTF_8).length;
        int stringEndByte = stringStartByte + "\"😀\"".getBytes(StandardCharsets.UTF_8).length;

        var styles = SFMDrawCanvasRemoteSyntaxStyles.project(
                model.glyphs(),
                1,
                9,
                source,
                List.of(
                        new SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan(
                                0,
                                keywordEnd,
                                List.of(ChatFormatting.LIGHT_PURPLE)
                        ),
                        new SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan(
                                stringStartByte,
                                stringEndByte,
                                List.of(ChatFormatting.GREEN)
                        )
                )
        );

        assertEquals(List.of(ChatFormatting.LIGHT_PURPLE), styles.get(model.glyphs().get(0)));
        SFMDrawCanvasModel.CanvasGlyph emoji = model.glyphs().stream()
                .filter(glyph -> glyph.text().equals("😀"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of(ChatFormatting.GREEN), styles.get(emoji));
        assertEquals(
                ChatFormatting.GREEN.getColor(),
                SFMDrawCanvasRemoteSyntaxStyles.styledGlyph(emoji, styles)
                        .getStyle()
                        .getColor()
                        .getValue()
        );
    }

    @Test
    void rejectsStaleOverlappingAndSplitScalarSpans() {
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.pasteText("a😀b", String::length, 9);
        String source = model.projectedText(1, 9);

        assertThrows(IllegalArgumentException.class, () -> SFMDrawCanvasRemoteSyntaxStyles.project(
                model.glyphs(), 1, 9, source + "!", List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMDrawCanvasRemoteSyntaxStyles.project(
                model.glyphs(), 1, 9, source,
                List.of(new SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan(
                        2, 5, List.of(ChatFormatting.GREEN)
                ))
        ));
        assertThrows(IllegalArgumentException.class, () -> SFMDrawCanvasRemoteSyntaxStyles.project(
                model.glyphs(), 1, 9, source,
                List.of(
                        new SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan(0, 5, List.of(ChatFormatting.WHITE)),
                        new SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan(4, 6, List.of(ChatFormatting.GREEN))
                )
        ));
    }

    @Test
    void parsesCanonicalFormattingNamesAndRejectsUnknownOnes() {
        assertEquals(
                List.of(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC),
                SFMDrawCanvasRemoteSyntaxStyles.parseFormattingNames(List.of("dark_gray", "italic"))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> SFMDrawCanvasRemoteSyntaxStyles.parseFormattingNames(List.of("ultraviolet"))
        );
    }
}
