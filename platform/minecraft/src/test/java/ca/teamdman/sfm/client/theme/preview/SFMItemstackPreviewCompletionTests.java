package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.explorer.SFMPath;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMItemstackPreviewCompletionTests {
    private static final SFMItemstackPreviewOperators OPS=SFMItemstackPreviewOperators.builtins();
    private static final SFMItemstackPreviewSubject SUBJECT=SFMItemstackPreviewSubject.of(
            SFMPath.fromNative(java.nio.file.Path.of("C:/tmp/abc.json")),"file",
            SFMItemstackPreviewSubject.Kind.FILE,"abc.json",Map.of());
    @Test void middleLiteralReplacementPreservesTrailingItemAndOtherOperands() {
        String before="sfm:bool/and sfm:entry/is_file sfm:string/ends_with sfm:entry/name \".j\" minecraft:bell";
        int cursor=before.indexOf(".j")+2;
        var frontier=SFMItemstackPreviewCompletion.complete(before,0,cursor,OPS,Optional.of(SUBJECT)).orElseThrow();
        var option=frontier.options().stream().filter(o->o.replacement().equals("\".json\"")).findFirst().orElseThrow();
        String after=before.substring(0,frontier.start())+option.replacement()+before.substring(frontier.end());
        assertTrue(after.endsWith("\".json\" minecraft:bell"));
        assertEquals("\".j\"",before.substring(frontier.start(),frontier.end()));
    }
    @Test void nestedMissingFrontiersNameTheirExpectedOperandAndType() {
        String before="sfm:bool/and sfm:entry/is_file sfm:string/ends_with sfm:entry/name ";
        var frontier=SFMItemstackPreviewCompletion.complete(before,0,before.length(),OPS,Optional.of(SUBJECT)).orElseThrow();
        assertEquals("sfm:string/ends_with.pattern : STRING",frontier.expected());
        assertTrue(frontier.options().stream().anyMatch(o->o.replacement().equals("\"a\"")));
        assertTrue(frontier.options().stream().anyMatch(o->o.replacement().equals("\"ab\"")));
        assertFalse(frontier.options().stream().anyMatch(o->o.replacement().equals("sfm:bool/and")));
    }
    @Test void exactExpressionEndDoesNotConsumeTheItemParameter() {
        String before="sfm:entry/is_file minecraft:bell";
        assertTrue(SFMItemstackPreviewCompletion.complete(before,0,before.length(),OPS,Optional.of(SUBJECT)).isEmpty());
        var frontier=SFMItemstackPreviewCompletion.complete(before,0,4,OPS,Optional.of(SUBJECT)).orElseThrow();
        assertTrue(frontier.options().stream().anyMatch(o->o.replacement().equals("sfm:entry/is_file")));
        assertEquals("sfm:entry/is_file".length(),frontier.end());
    }
    @Test void finiteLongPrefixesRemainReachableByNarrowingWithoutCartesianEnumeration() {
        var subject=SFMItemstackPreviewSubject.of(SUBJECT.path(),"file",SUBJECT.kind(),"a".repeat(500),Map.of());
        String before="sfm:string/starts_with sfm:entry/name \""+"a".repeat(499);
        var frontier=SFMItemstackPreviewCompletion.complete(before,0,before.length(),OPS,Optional.of(subject)).orElseThrow();
        assertTrue(frontier.options().size()<=256);
        assertTrue(frontier.options().stream().anyMatch(o->o.replacement().equals("\""+"a".repeat(500)+"\"")));
    }
}
