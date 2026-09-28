package ca.teamdman.sfm.client.syntax;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class SFMLocalLexicalStylesTests {
    @Test void gradleTokensUseExactUtf8AndDoNotParseInsideStringsOrComments() {
        String text="plugins { id '雪😀' } // def 123\n def count = 42";
        var spans=SFMLocalLexicalStyles.highlight("gradle",text);
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        var tokens=spans.stream().map(s->new String(bytes,s.startByte(),s.endByte()-s.startByte(),StandardCharsets.UTF_8)).toList();
        assertEquals(java.util.List.of("plugins", "'雪😀'", "// def 123", "def", "42"),tokens);
        for (int i=1;i<spans.size();i++) assertTrue(spans.get(i-1).endByte()<=spans.get(i).startByte());
    }
    @Test void markdownAndLimitsAreExplicit() {
        assertEquals(2,SFMLocalLexicalStyles.highlight("markdown","# Title\nSome `code`.").size());
        assertTrue(SFMLocalLexicalStyles.highlight("gradle","x".repeat(SFMLocalLexicalStyles.MAX_CHARACTERS+1)).isEmpty());
        assertTrue(SFMLocalLexicalStyles.highlight("unknown","def x = 1").isEmpty());
    }
}
