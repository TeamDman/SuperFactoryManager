package ca.teamdman.sfm.client.theme.preview;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewExpression.*;

class SFMItemstackPreviewCoverageTests {
    static final SFMItemstackPreviewRegistry.Snapshot REGISTRY = new SFMItemstackPreviewRegistry.Snapshot(
            0, SFMItemstackPreviewOperators.builtins(), List.of());
    static SFMItemstackPreviewSubject subject(String name, boolean directory) {
        return SFMItemstackPreviewSubject.of(SFMPath.fromNative(Path.of("C:/fixture").resolve(name)), "file",
                directory ? SFMItemstackPreviewSubject.Kind.CONTAINER : SFMItemstackPreviewSubject.Kind.FILE, name, Map.of());
    }
    @Test void genericPaperSuffixAndChestDoNotCountButSpecificRulesDo() {
        var prepared=SFMItemstackPreviewThemeResolver.prepare(SFMClientTheme.defaults(),REGISTRY);
        for (var subject:List.of(subject("unknown.zzz",false),subject("file.txt",false),subject("ordinary-directory",true)))
            assertFalse(SFMItemstackPreviewCoverage.classify(prepared.resolve(subject)).covered());
        for (var subject:List.of(subject("x.java",false),subject("minecraft",true))) {
            assertTrue(SFMItemstackPreviewCoverage.classify(prepared.resolve(subject)).covered());
            assertEquals(SFMItemstackPreviewThemeResolver.resolve(SFMClientTheme.defaults(),REGISTRY,subject),prepared.resolve(subject));
        }
    }
    @Test void arbitraryCatchAllAndAmbiguousRulesCannotInflateCoverage() {
        var icon=SFMItemIcon.vanilla("apple","apple");
        var catchAll=SFMItemstackPreviewRules.Rule.user(call("sfm:entry/is_file"),icon);
        var decision=SFMItemstackPreviewRules.resolve(List.of(catchAll),REGISTRY.operators(),subject("x.java",false));
        assertEquals("GENERIC_RULE",SFMItemstackPreviewCoverage.classify(decision).reason());
        var same=SFMItemstackPreviewRules.Rule.user(call("sfm:entry/is_file"),SFMItemIcon.vanilla("bell","bell"));
        assertEquals("AMBIGUOUS",SFMItemstackPreviewCoverage.classify(
                SFMItemstackPreviewRules.resolve(List.of(catchAll,same),REGISTRY.operators(),subject("x.java",false))).reason());
    }
    @Test void repositoryDefaultsHaveMeaningfulSpecificityAndDoNotOverrideUsers() {
        var defaults=SFMClientTheme.defaults();
        var prepared=SFMItemstackPreviewThemeResolver.prepare(defaults,REGISTRY);
        for (String name:List.of("src","tests","review","explorer","config","recipes","build",".github")) {
            var decision=prepared.resolve(subject(name,true));
            assertTrue(SFMItemstackPreviewCoverage.classify(decision).covered(),name);
            assertTrue(decision.winner().orElseThrow().predicate().print().contains("sfm:entry/is_container"));
        }
        for (String name:List.of("README.md","AGENTS.md",".gitignore",".gitattributes","LICENSE","LICENSE.txt",
                "x.class","x.o","x.rlib","x.rmeta","x.timestamp","x.ts","x.g4","x.patch"))
            assertTrue(SFMItemstackPreviewCoverage.classify(prepared.resolve(subject(name,false))).covered(),name);
        var custom=defaults.withPreviewRules(List.of(SFMItemstackPreviewRules.Rule.user(
                call("sfm:entry/is_container"),SFMItemIcon.vanilla("apple","user choice"))));
        assertEquals("minecraft:apple",SFMItemstackPreviewThemeResolver.resolve(custom,REGISTRY,subject("src",true)).winner().orElseThrow().icon().requestedItem().toString());
        assertFalse(SFMItemstackPreviewCoverage.classify(prepared.resolve(subject("unrecognized-name",true))).covered());
    }

    /** Opt-in real-tree test: no file contents are read, and traversal never follows links. */
    @Test void repositoryCoverage() throws Exception {
        String configured=System.getenv("SFM_ICON_COVERAGE_ROOT");
        Assumptions.assumeTrue(configured!=null,"Run scripts/analyze-itemstack-icon-coverage.ps1 for a real repository audit");
        Path root=Path.of(configured).toRealPath();
        assertTrue(Files.exists(root.resolve(".git")),"Coverage root must be the Git worktree root");
        Path reports=root.resolve("platform/minecraft/build/itemstack-icon-coverage").normalize();
        String run=Objects.requireNonNullElse(System.getenv("SFM_ICON_COVERAGE_RUN"),"latest");
        assertTrue(run.matches("[a-zA-Z0-9_-]+"),"Run label must be a simple directory name");
        Path output=reports.resolve(run);
        Files.createDirectories(output);
        var prepared=SFMItemstackPreviewThemeResolver.prepare(SFMClientTheme.defaults(),REGISTRY);
        var gson=new GsonBuilder().disableHtmlEscaping().create();
        var groups=new TreeMap<String,Group>();
        var items=new TreeMap<String,Long>();
        var exclusions=new TreeMap<String,Long>();
        var errors=new ArrayList<String>();
        long[] counts=new long[4]; // files, covered files, directories, covered directories
        boolean[] limited={false};
        try (var writer=Files.newBufferedWriter(output.resolve("entries.ndjson"),StandardCharsets.UTF_8)) {
            Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
                void excluded(String reason) { exclusions.merge(reason,1L,Long::sum); }
                @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                    if (dir.equals(reports) || dir.getFileName().toString().equals(".git")) {
                        excluded(dir.equals(reports) ? "report-directory" : "git-internals"); return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (attrs.isOther() || attrs.isSymbolicLink()) { excluded("link-or-special"); return FileVisitResult.SKIP_SUBTREE; }
                    return record(dir,true);
                }
                @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals(".git")) { excluded("git-internals"); return FileVisitResult.CONTINUE; }
                    if (!attrs.isRegularFile() || attrs.isSymbolicLink()) { excluded("link-or-special"); return FileVisitResult.CONTINUE; }
                    return record(file,false);
                }
                @Override public FileVisitResult visitFileFailed(Path path,IOException error) {
                    errors.add(root.relativize(path)+": "+error); return FileVisitResult.CONTINUE;
                }
                FileVisitResult record(Path path,boolean directory) throws IOException {
                    if (counts[0]+counts[2]>=250000) { limited[0]=true; return FileVisitResult.TERMINATE; }
                    String name=path.getFileName().toString();
                    var subject=SFMItemstackPreviewSubject.of(SFMPath.fromNative(path),"file",
                            directory ? SFMItemstackPreviewSubject.Kind.CONTAINER : SFMItemstackPreviewSubject.Kind.FILE,name,Map.of());
                    var result=SFMItemstackPreviewCoverage.classify(prepared.resolve(subject));
                    int index=directory ? 2 : 0; counts[index]++; if(result.covered()) counts[index+1]++;
                    items.merge(result.item(),1L,Long::sum);
                    String relative=path.equals(root) ? "." : root.relativize(path).toString().replace('\\','/');
                    var row=new JsonObject(); row.addProperty("path",relative); row.addProperty("kind",directory ? "directory" : "file");
                    row.add("coverage",gson.toJsonTree(result)); writer.write(gson.toJson(row)); writer.newLine();
                    if (!result.covered()) {
                        String key=directory ? "directory-name:"+name : "file-suffix:"+(subject.suffixes().isEmpty() ? "<none>" : subject.suffixes().get(subject.suffixes().size()-1));
                        Group group=groups.computeIfAbsent(key,ignored->new Group()); group.count++;
                        if(group.examples.size()<5) group.examples.add(relative);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        var summary=new JsonObject(); summary.addProperty("schema","sfm.itemstack-icon-coverage/1");
        summary.addProperty("root",root.toString()); summary.addProperty("complete",!limited[0] && errors.isEmpty());
        summary.addProperty("entry_limit",250000); summary.addProperty("limit_reached",limited[0]);
        summary.addProperty("files",counts[0]); summary.addProperty("covered_files",counts[1]);
        summary.addProperty("directories",counts[2]); summary.addProperty("covered_directories",counts[3]);
        summary.add("requested_items",gson.toJsonTree(items)); summary.add("exclusions",gson.toJsonTree(exclusions));
        summary.add("errors",gson.toJsonTree(errors));
        var ordered=new LinkedHashMap<String,Group>(); groups.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String,Group>>comparingLong(e->e.getValue().count).reversed().thenComparing(Map.Entry::getKey))
                .forEach(e->ordered.put(e.getKey(),e.getValue()));
        summary.add("uncovered_groups",gson.toJsonTree(ordered));
        Files.writeString(output.resolve("summary.json"),new GsonBuilder().setPrettyPrinting().create().toJson(summary),StandardCharsets.UTF_8);
        System.out.println("Icon coverage: files "+counts[1]+"/"+counts[0]+", directories "+counts[3]+"/"+counts[2]+"; report="+output);
        assertFalse(limited[0],"Scan hit its cap; report is incomplete"); assertTrue(errors.isEmpty(),errors.toString());
    }
    static class Group { long count; List<String> examples=new ArrayList<>(); }
}
