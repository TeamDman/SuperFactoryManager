package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.theme.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression.*;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRules.*;

public class SFMItemstackPreviewThemeTests {
    @Test public void defaultInventoryIsCoveredAndNamedContainersRespectUserAuthority() {
        var defaults=SFMClientTheme.defaults();
        for (String suffix:List.of("toml", "dat", "mca", "txt", "gz", "png", "json", "dat_old",
                "lua", "log", "lock", "ini", "cfg", "properties", "bak", "v1", "json5", "marker",
                "zip", "gradle", "md")) {
            assertTrue(defaults.fileIcons().containsKey("."+suffix),suffix);
            assertEquals(Status.MATCHED,resolve(defaults,"sample."+suffix,false).status(),suffix);
        }
        assertEquals("minecraft:grass_block",resolve(defaults,"minecraft",true).winner().orElseThrow().icon().requestedItem().toString());
        assertEquals("minecraft:paper",resolve(defaults,"minecraft",false).winner().orElseThrow().icon().requestedItem().toString());
        var user=SFMClientThemeLoader.load("schema_version=1\n[icons.files]\ndirectory=\"minecraft:apple\"",defaults).theme().orElseThrow();
        assertEquals("minecraft:apple",resolve(user,"minecraft",true).winner().orElseThrow().icon().requestedItem().toString());
    }
    private final SFMItemstackPreviewRegistry.Snapshot registry=new SFMItemstackPreviewRegistry.Snapshot(0,SFMItemstackPreviewOperators.builtins(),List.of());
    private static SFMItemstackPreviewSubject subject(String name,boolean directory) {
        return SFMItemstackPreviewSubject.of(SFMPath.parse("file:///C:/fixture/"+name),"file",
                directory ? SFMItemstackPreviewSubject.Kind.CONTAINER : SFMItemstackPreviewSubject.Kind.FILE,name,Map.of());
    }
    private Decision resolve(SFMClientTheme theme,String name,boolean directory) {
        return SFMItemstackPreviewThemeResolver.resolve(theme,registry,subject(name,directory));
    }
    private static SFMItemIcon icon(String id) { return SFMItemIcon.vanilla(id,id); }
    @Test public void legacyMapsPreserveDotfilesCompoundSuffixAndContainerSemantics() {
        var theme=SFMClientTheme.defaults();
        for(String name:List.of("a.java","a.JSON","a.sfm-review.json",".json","foo","x.","x.y.","a.b.json","a.nope"))
            assertEquals(theme.fileIconForName(name),resolve(theme,name,false).winner().orElseThrow().icon(),name);
        assertEquals(theme.fileIcon("directory"),resolve(theme,"folder.json",true).winner().orElseThrow().icon());
    }
    @Test public void legacyUnknownUserFallbackDoesNotMaskKnownExtensionAndUserBeatsMod() {
        var loaded=SFMClientThemeLoader.load("schema_version=1\n[icons.files]\nunknown=\"minecraft:apple\"",SFMClientTheme.defaults()).theme().orElseThrow();
        assertEquals("minecraft:cocoa_beans",resolve(loaded,"a.java",false).winner().orElseThrow().icon().requestedItem().toString());
        assertEquals(Layer.USER,resolve(loaded,"a.xyz",false).winner().orElseThrow().layer());
        var predicate=call("sfm:string/equals",call("sfm:entry/name"),literal("a.java"));
        var mod=new Rule("test:mod",Layer.MOD,predicate,icon("stone"));
        var snapshot=new SFMItemstackPreviewRegistry.Snapshot(1,registry.operators(),List.of(mod));
        var user=loaded.withPreviewRules(List.of(Rule.user(predicate,icon("bell"))));
        assertEquals("minecraft:bell",SFMItemstackPreviewThemeResolver.resolve(user,snapshot,subject("a.java",false)).winner().orElseThrow().icon().requestedItem().toString());
    }
    @Test public void themeRoundTripRetainsProvenanceAndTypedRulesWithoutPromotingDefaults() {
        var defaults=SFMClientTheme.defaults();
        assertEquals(defaults,SFMClientThemeLoader.load(SFMClientThemeTomlWriter.write(defaults),defaults).theme().orElseThrow());
        var loaded=SFMClientThemeLoader.load("schema_version=1\n[icons.files]\n\".java\"=\"minecraft:apple\"",defaults).theme().orElseThrow();
        var withRules=loaded.withPreviewRules(List.of(Rule.user(call("sfm:entry/is_file"),icon("bell"))));
        assertEquals(Set.of(".java"),withRules.explicitFileIcons());
        assertEquals(withRules,SFMClientThemeLoader.load(SFMClientThemeTomlWriter.write(withRules),defaults).theme().orElseThrow());
    }
    @Test public void equivalentAuthoredRuleReplacesLoweredUserRuleAndCacheInvalidates() {
        var theme=SFMClientThemeLoader.load("schema_version=1\n[icons.files]\ndirectory=\"minecraft:apple\"",SFMClientTheme.defaults()).theme().orElseThrow();
        var changed=theme.withPreviewRules(List.of(Rule.user(call("sfm:entry/is_container"),icon("bell"))));
        var cache=new SFMItemstackPreviewCache(); var subject=subject("docs",true);
        var first=cache.resolve(theme,registry,subject);
        assertSame(first,cache.resolve(theme,registry,subject));
        assertEquals("minecraft:bell",cache.resolve(changed,registry,subject).winner().orElseThrow().icon().requestedItem().toString());
        assertEquals(1,cache.size());
    }
    @Test public void contextualSuffixReplacesPopulatedThemePreferenceAndResetRestoresIt() {
        var theme=SFMClientThemeLoader.load(SFMClientThemeService.DEFAULT_TOML,SFMClientTheme.defaults()).theme().orElseThrow();
        for (String name:List.of("abc.json","abc.JSON")) {
            var predicate=ca.teamdman.sfm.client.action.SFMItemstackPreviewRuleAction.contextualPredicates(subject(name,false)).get(0).predicate();
            var authored=theme.withPreviewRules(List.of(Rule.user(predicate,icon("bell"))));
            var decision=resolve(authored,name,false);
            assertEquals(Status.MATCHED,decision.status(),name);
            assertEquals("minecraft:bell",decision.winner().orElseThrow().icon().requestedItem().toString(),name);
            assertEquals(theme.fileIconForName(name),resolve(authored.withPreviewRules(List.of()),name,false).winner().orElseThrow().icon());
        }
    }
    @Test public void ambiguityAndMissingFactsDoNotSilentlyPickAnotherLayer() {
        var name=call("sfm:string/starts_with",call("sfm:entry/name"),literal("a"));
        var suffix=call("sfm:string/ends_with",call("sfm:entry/name"),literal(".java"));
        var theme=SFMClientTheme.defaults().withPreviewRules(List.of(Rule.user(name,icon("apple")),Rule.user(suffix,icon("bell"))));
        assertEquals(Status.AMBIGUOUS,resolve(theme,"a.java",false).status());
        assertEquals(Status.AMBIGUOUS,resolve(theme.withPreviewRules(List.of(theme.previewRules().get(1),theme.previewRules().get(0))),"a.java",false).status());
    }
    @Test public void virtualFileContainersUseDeclaredFactsNotSortPrefixesOrExpandability() {
        var entry=new ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry(
                SFMPath.parse("review://capture/file-1"),"platform/abc.json",true,Map.of(
                ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry.SORT_NAME,
                ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry.SortKey.available("01-file/platform/abc.json"),
                ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry.SUBJECT_NAME,
                ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry.SortKey.available("abc.json"),
                ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry.SUBJECT_KIND,
                ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry.SortKey.available("file")),List.of());
        var subject=SFMItemstackPreviewSubject.from(entry);
        assertEquals("abc.json",subject.name());
        assertEquals(SFMItemstackPreviewSubject.Kind.FILE,subject.kind());
        assertEquals("abc",subject.basename());
    }
    @Test public void oldDirectoryMapDoesNotTakeOverUnrelatedRegistryContainers() {
        var loaded=SFMClientThemeLoader.load("schema_version=1\n[icons.files]\ndirectory=\"minecraft:apple\"",SFMClientTheme.defaults()).theme().orElseThrow();
        var registryRoot=SFMItemstackPreviewSubject.of(SFMPath.parse("registry://minecraft/item/"),"registry",
                SFMItemstackPreviewSubject.Kind.CONTAINER,"Items",Map.of());
        assertEquals(Status.NO_MATCH,SFMItemstackPreviewThemeResolver.resolve(loaded,registry,registryRoot).status());
        var authored=loaded.withPreviewRules(List.of(Rule.user(call("sfm:entry/is_container"),icon("bell"))));
        assertEquals(Status.MATCHED,SFMItemstackPreviewThemeResolver.resolve(authored,registry,registryRoot).status());
    }
}
