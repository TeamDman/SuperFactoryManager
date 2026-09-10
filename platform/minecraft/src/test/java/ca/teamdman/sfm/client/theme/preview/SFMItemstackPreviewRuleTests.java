package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression.*;
import static org.junit.jupiter.api.Assertions.*;

class SFMItemstackPreviewRuleTests {
    private static final SFMItemstackPreviewOperators OPS=SFMItemstackPreviewOperators.builtins();
    private static final SFMItemstackPreviewExpression FILE=call("sfm:entry/is_file");
    private static final String JSON="sfm:bool/and sfm:entry/is_file sfm:string/ends_with sfm:entry/name \".json\"";
    private static SFMItemstackPreviewSubject subject(String name, SFMItemstackPreviewSubject.Kind kind) {
        return SFMItemstackPreviewSubject.of(SFMPath.fromNative(java.nio.file.Path.of("C:/tmp/example")),"file",kind,name,Map.of());
    }
    private static SFMItemstackPreviewExpression eq(String name) { return call("sfm:string/equals",call("sfm:entry/name"),literal(name)); }
    private static SFMItemstackPreviewRules.Rule rule(String id, SFMItemstackPreviewRules.Layer layer, SFMItemstackPreviewExpression expr,String item) {
        return new SFMItemstackPreviewRules.Rule(id,layer,expr,SFMItemIcon.vanilla(item,item));
    }
    @Test void roundTripNestedQuotedUnicodeAndStopBeforeItemArgument() {
        var expr=call("sfm:bool/and",FILE,call("sfm:bool/not",eq("a\"\\\n雪😀.json")));
        assertEquals(expr,parse(expr.print(),OPS));
        String all=JSON+" minecraft:bell";
        var parsed=parse(all,0,SFMItemstackPreviewOperators.Type.BOOLEAN,OPS);
        assertEquals(JSON,parsed.expression().print());
        assertEquals(" minecraft:bell",all.substring(parsed.end()));
    }
    @Test void malformedPartialAndWrongTypeStayExplicit() {
        for(String text:List.of("", "sfm:bool/and", "sfm:bool/and sfm:entry/name sfm:entry/is_file",
                "sfm:string/equals sfm:entry/name unquoted", "sfm:missing", "sfm:entry/is_file garbage",
                "sfm:string/equals sfm:entry/name \"unterminated"))
            assertThrows(IllegalArgumentException.class,()->parse(text,OPS),text);
    }
    @Test void quotedLiteralsRejectLenientNonJsonControlsAndEscapes() {
        String prefix="sfm:string/equals sfm:entry/name ";
        for(String value:List.of("\"line\nfeed\"", "\"tab\there\"", "\"bad\\'escape\"", "\"bad\\vescape\""))
            assertThrows(IllegalArgumentException.class,()->parse(prefix+value,OPS));
        assertEquals("line\nfeed",parse(prefix+"\"line\\nfeed\"",OPS).operands().get(1).literal());
    }
    @Test void depthNodeLengthAndUnicodeBoundariesAreBounded() {
        assertThrows(IllegalArgumentException.class,()->parse("sfm:bool/not ".repeat(18)+"sfm:entry/is_file",OPS));
        assertThrows(IllegalArgumentException.class,()->literal("a".repeat(1025)));
        assertThrows(IllegalArgumentException.class,()->literal("\uD800"));
        assertThrows(IllegalArgumentException.class,()->parse(" ".repeat(8193),OPS));
        assertEquals(List.of("😀","😀a","😀ab"),subject("😀ab",SFMItemstackPreviewSubject.Kind.FILE).prefixes());
    }
    @Test void structuredKindCaseBasenameAndCompoundSuffixAreNotGuessed() {
        var expr=parse(JSON,OPS);
        assertEquals(Optional.of(true),expr.evaluate(OPS,subject("a.JSON",SFMItemstackPreviewSubject.Kind.FILE)));
        assertEquals(Optional.of(false),expr.evaluate(OPS,subject("a.json",SFMItemstackPreviewSubject.Kind.CONTAINER)));
        assertEquals("a.b",subject("a.b.json",SFMItemstackPreviewSubject.Kind.FILE).basename());
        assertEquals(List.of(".b.json",".json"),subject("a.b.json",SFMItemstackPreviewSubject.Kind.FILE).suffixes());
        for(String name:List.of(".gitignore","file","a.")) assertEquals(name,subject(name,SFMItemstackPreviewSubject.Kind.FILE).basename());
        assertEquals(Optional.of(false),eq("é").evaluate(OPS,subject("e\u0301",SFMItemstackPreviewSubject.Kind.FILE)));
    }
    @Test void conservativeDominanceAndAuthorityArePermutationIndependent() {
        var suffix=parse(JSON,OPS);
        var exact=call("sfm:bool/and",FILE,eq("abc.json"));
        var broad=call("sfm:bool/or",suffix,FILE);
        assertTrue(SFMItemstackPreviewRules.implies(exact,suffix));
        assertFalse(SFMItemstackPreviewRules.implies(suffix,exact));
        assertFalse(SFMItemstackPreviewRules.implies(broad,suffix));
        var a=rule("exact",SFMItemstackPreviewRules.Layer.USER,exact,"bell");
        var b=rule("suffix",SFMItemstackPreviewRules.Layer.USER,suffix,"paper");
        var c=rule("broad",SFMItemstackPreviewRules.Layer.USER,broad,"barrel");
        var s=subject("abc.json",SFMItemstackPreviewSubject.Kind.FILE);
        for(var list:List.of(List.of(a,b,c),List.of(c,b,a),List.of(b,a,c)))
            assertEquals("exact",SFMItemstackPreviewRules.resolve(list,OPS,s).winner().orElseThrow().id());
        var higher=rule("higher",SFMItemstackPreviewRules.Layer.USER,FILE,"bell");
        var lower=rule("lower",SFMItemstackPreviewRules.Layer.MOD,exact,"paper");
        assertEquals("higher",SFMItemstackPreviewRules.resolve(List.of(lower,higher),OPS,s).winner().orElseThrow().id());
    }
    @Test void incomparablePredicatesAreAmbiguousAndUnavailableFactsNotFalse() {
        var a=rule("a",SFMItemstackPreviewRules.Layer.USER,call("sfm:string/starts_with",call("sfm:entry/name"),literal("a")),"bell");
        var b=rule("b",SFMItemstackPreviewRules.Layer.USER,parse(JSON,OPS),"paper");
        assertEquals(SFMItemstackPreviewRules.Status.AMBIGUOUS,SFMItemstackPreviewRules.resolve(List.of(a,b),OPS,
                subject("abc.json",SFMItemstackPreviewSubject.Kind.FILE)).status());
        var custom=OPS.with(new SFMItemstackPreviewOperators.Operator("test:missing","Unavailable metadata",SFMItemstackPreviewOperators.Type.BOOLEAN,
                List.of(),(s,args)->Optional.empty()));
        var missing=rule("missing",SFMItemstackPreviewRules.Layer.USER,call("sfm:bool/not",call("test:missing")),"bell");
        assertEquals(SFMItemstackPreviewRules.Status.UNAVAILABLE,SFMItemstackPreviewRules.resolve(List.of(missing),custom,
                subject("abc.json",SFMItemstackPreviewSubject.Kind.FILE)).status());
    }
    @Test void registrySignaturesAreValidatedAndContributionsTyped() {
        assertThrows(IllegalArgumentException.class,()->new SFMItemstackPreviewOperators(List.of(OPS.descriptors().get(0),OPS.descriptors().get(0))));
        var custom=OPS.with(new SFMItemstackPreviewOperators.Operator("test:prefix","New pure projection",SFMItemstackPreviewOperators.Type.STRING,
                List.of(),(s,args)->Optional.of("a")));
        var expr=parse("sfm:string/starts_with sfm:entry/name test:prefix",custom);
        assertEquals(Optional.of(true),expr.evaluate(custom,subject("abc",SFMItemstackPreviewSubject.Kind.FILE)));
    }
    @Test void versionedTypedStorageRoundTripsAndRejectsMalformedTrees() {
        var expr=call("sfm:bool/and",FILE,eq("café 雪😀.json"));
        var value=SFMItemstackPreviewRules.Rule.user(expr,SFMItemIcon.vanilla("bell","review icon"));
        var parser=com.electronwill.nightconfig.toml.TomlFormat.instance().createParser();
        var root=parser.parse("schema_version=1\n[unrelated]\nvalue=\"preserve\"\n"+SFMItemstackPreviewRuleCodec.write(List.of(value)));
        assertEquals(List.of(value),SFMItemstackPreviewRuleCodec.read(root,OPS));
        assertEquals("preserve",root.get("unrelated.value"));
        assertEquals(List.of(),SFMItemstackPreviewRuleCodec.read(parser.parse("schema_version=1"),OPS));
        assertThrows(IllegalArgumentException.class,()->SFMItemstackPreviewRuleCodec.read(parser.parse(
                SFMItemstackPreviewRuleCodec.write(List.of(value)).replace("schema_version = 1","schema_version = 2")),OPS));
        assertThrows(IllegalArgumentException.class,()->SFMItemstackPreviewRuleCodec.read(parser.parse(
                SFMItemstackPreviewRuleCodec.write(List.of(value,value))),OPS));
    }
}
