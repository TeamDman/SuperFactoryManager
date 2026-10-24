import ca.teamdman.sfm.client.search.*;
import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Production matcher, field mapping and projection; no UI or rendering substitutes. */
public class NucleoProjectionProbe {
    static int checks;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    static List<SFMPath> paths(SFMExplorerProjection.Result result) {
        return result.rows().stream().map(SFMExplorerProjection.Row::path).toList();
    }
    public static void main(String[] args) {
        var options = SFMTextMatchOptions.defaults().toggleNucleo();
        var matcher = SFMTextMatcher.compile("😀e", options);
        check(matcher.match("a😀_é").fragments().equals(List.of(new SFMTextMatcher.Fragment(1,3),
                new SFMTextMatcher.Fragment(4,5))), "native adapter translated to common UTF16 fragments");
        check(matcher.match("😀e").score() == 0, "no native ranking exposed");
        check(!SFMTextMatcher.compile("papar", options).match("paper").matches(), "Nucleo explicit behavior");
        check(SFMTextMatcher.compile("papar", SFMTextMatchOptions.legacyFuzzy()).match("paper").matches(), "legacy fuzzy remains unchanged");
        check(!SFMTextMatcher.compile("😀e", SFMTextMatchOptions.defaults()).match("a😀_é").matches(), "literal remains unchanged");
        check(SFMTextMatcher.compile("a.+é", SFMTextMatchOptions.defaults().toggleRegex()).match("a😀_é").matches(), "regex remains unchanged");
        try { SFMTextMatcher.compile("e", options.withWholeWord(true)); throw new AssertionError("unsupported treated as supported"); }
        catch (SFMTextMatcher.LimitExceeded expected) { checks++; }
        try { matcher.match("\ud800"); throw new AssertionError("invalid accepted"); }
        catch (SFMTextMatcher.LimitExceeded expected) { checks++; }
        try { matcher.match("😀e", new SFMMatchBudget(100, () -> true)); throw new AssertionError("cancel ignored"); }
        catch (CancellationException expected) { checks++; }
        var root = SFMPath.parse("inspection://order/");
        var alpha = SFMPath.parse("inspection://order/alpha");
        var zeta = SFMPath.parse("inspection://order/zeta");
        var relations = new SFMChildRelationRepository();
        var ticket = relations.beginRefresh(Set.of(root), 1);
        relations.publish(ticket, List.of(new SFMChildPage(root, List.of(new SFMChildEdge(root,alpha),new SFMChildEdge(root,zeta)),
                Optional.empty(),SFMChildPage.Completeness.COMPLETE,1,List.of())));
        var a = SFMExplorerEntry.simple(alpha,"z_open_open",false,Optional.empty());
        var z = SFMExplorerEntry.simple(zeta,"a_open",false,Optional.empty());
        var entries = Map.of(alpha,a,zeta,z);
        var session = new SFMExplorerSession(new SFMExplorerId("nucleo-probe"),root,new SFMSelectionRepository());
        session.setFilterOptions(options); session.navigateTo(alpha); session.setScrollOffset(17);
        for (var group : SFMExplorerProjection.Group.values()) {
            session.setGroup(group); session.setFilterQuery("");
            var before = paths(SFMExplorerProjection.project(session.snapshot(), relations.snapshot(), entries));
            session.setFilterQuery("open");
            var after = SFMExplorerProjection.project(session.snapshot(), relations.snapshot(), entries);
            check(before.equals(List.of(zeta,alpha)) && paths(after).equals(before), "normal ordering retained for " + group);
            check(session.snapshot().navigationCursor().equals(Optional.of(alpha)) && session.snapshot().scrollOffset()==17, "projection preserves cursor and scroll");
        }
        var meta = new SFMExplorerEntry(alpha,"unrelated",false,a.sortKeys(),List.of("a😀_é"),List.of());
        var evidence = SFMExplorerEntryMatch.evaluate(meta, matcher);
        check(evidence.matches() && evidence.labelFragments().isEmpty(), "metadata match does not invent label highlight");
        var visible = new SFMExplorerEntry(alpha,"prefix a😀_é",false,a.sortKeys(),List.of("a😀_é"),List.of());
        check(SFMExplorerEntryMatch.evaluate(visible, matcher).labelFragments().equals(List.of(
                new SFMTextMatcher.Fragment(8,10),new SFMTextMatcher.Fragment(11,12))), "exact visible-field offset translation");
        try { SFMExplorerProjection.project(session.snapshot(),relations.snapshot(),entries,Map.of(),()->true);
            throw new AssertionError("cancelled projection completed"); }
        catch (CancellationException expected) { checks++; }
        var stressRoot = SFMPath.parse("inspection://budget/");
        var knownPath = SFMPath.parse("inspection://budget/known");
        var costlyPath = SFMPath.parse("inspection://budget/costly");
        var laterPath = SFMPath.parse("inspection://budget/later");
        var stressRelations = new SFMChildRelationRepository();
        var stressTicket = stressRelations.beginRefresh(Set.of(stressRoot), 1);
        stressRelations.publish(stressTicket, List.of(new SFMChildPage(stressRoot,
                List.of(new SFMChildEdge(stressRoot,knownPath), new SFMChildEdge(stressRoot,costlyPath), new SFMChildEdge(stressRoot,laterPath)),
                Optional.empty(),SFMChildPage.Completeness.COMPLETE,1,List.of())));
        var costly = SFMExplorerEntry.simple(costlyPath,"B costly",false,Optional.empty());
        var stressEntries = Map.of(stressRoot,SFMExplorerEntry.simple(stressRoot,"root",true,Optional.empty()),
                knownPath,SFMExplorerEntry.simple(knownPath,"A needle",false,Optional.empty()),
                costlyPath,new SFMExplorerEntry(costlyPath,costly.label(),false,costly.sortKeys(),Collections.nCopies(64,"x".repeat(4096)),List.of()),
                laterPath,SFMExplorerEntry.simple(laterPath,"C later",false,Optional.empty()));
        var stress = new SFMExplorerSession(new SFMExplorerId("budget-probe"),stressRoot,new SFMSelectionRepository());
        stress.setFilterOptions(options); stress.setFilterQuery("needle"); stress.setHoist(SFMExplorerProjection.Hoist.SHOW_ROOTS);
        var limited = SFMExplorerProjection.project(stress.snapshot(),stressRelations.snapshot(),stressEntries);
        check(paths(limited).contains(knownPath) && limited.filter().incompleteMaterialization(),
                "exhaustion retains known matches and ancestor aggregation while reporting incomplete");
        System.out.println("PASS " + checks + " production matcher/projection/field checks; no Minecraft UI claim");
    }
}
