import ca.teamdman.sfm.client.inspection.SFMInspectionTree;
import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.lazy.*;
import ca.teamdman.sfm.client.search.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual contributed resolver + real matcher; no Minecraft rendering substitute. */
public class InspectionSearchProbe {
    static int checks;
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    static SFMExplorerFilterDomainResolver.FilterDomainRequest request(SFMPath root, String query,
            int maximum, long generation, SFMTextMatchOptions options, SFMExplorerCancellationToken cancellation) {
        return new SFMExplorerFilterDomainResolver.FilterDomainRequest(root,query,maximum,generation,cancellation,options);
    }
    static void reject(CompletableFuture<?> future, Class<?> expected) {
        try { future.join(); throw new AssertionError("Expected " + expected); }
        catch (CompletionException failure) { check(expected.isInstance(failure.getCause()), failure.toString()); }
        catch (CancellationException failure) { check(expected.isInstance(failure), failure.toString()); }
    }
    public static void main(String[] args) throws Exception {
        var tree = SFMInspectionTree.read(Path.of(args[0]), "search").get(0);
        var queue = new ArrayDeque<Runnable>();
        var resolver = new SFMInMemoryTextExplorerResolver("inspection", queue::add, 2);
        var lease = resolver.mount(tree.root().authority(), tree.nodes());
        var options = SFMTextMatchOptions.defaults().toggleNucleo();
        var future = resolver.resolveFilterDomain(request(tree.root(), "Instrbds",4096,resolver.generation(),options,new SFMExplorerCancellationToken()));
        check(!future.isDone(), "matching runs on supplied worker, not calling UI thread");
        queue.remove().run();
        var domain = future.join();
        check(domain.complete() && !domain.matchedPaths().isEmpty(), "complete real Nucleo domain");
        check(domain.entries().size()==tree.nodes().size(), "uncollapsed mount metadata is searchable");
        check(!domain.matchedPaths().contains(tree.root()), "containing root is not a direct match");
        check(domain.matchedPaths().stream().anyMatch(path -> !domain.matchEvidence().get(path).labelFragments().isEmpty()),
                "actual UTF16 label spans where the display label matches");
        var literal = resolver.resolveFilterDomain(request(tree.root(),"Instrbds",4096,resolver.generation(),SFMTextMatchOptions.defaults(),new SFMExplorerCancellationToken()));
        queue.remove().run();
        check(literal.join().complete() && literal.join().matchedPaths().isEmpty(), "literal mode is preserved");
        var limited = resolver.resolveFilterDomain(request(tree.root(),"source",1,resolver.generation(),options,new SFMExplorerCancellationToken()));
        queue.remove().run();
        check(!limited.join().complete() && limited.join().matchedPaths().size()==1 && limited.join().totalMatchCount()>1,
                "match truncation is explicit");
        var bounded = limited.join();
        var relations = new SFMChildRelationRepository();
        var ticket = relations.beginRefresh(bounded.childPages().stream().map(SFMChildPage::parent).collect(java.util.stream.Collectors.toSet()),resolver.generation());
        relations.publish(ticket,bounded.childPages());
        var session = new SFMExplorerSession(new SFMExplorerId("bounded-search"),tree.root(),new SFMSelectionRepository());
        session.setFilterOptions(options);session.setFilterQuery("source");
        var entries = bounded.entries().stream().collect(java.util.stream.Collectors.toMap(SFMExplorerEntry::path,e->e));
        var projected = SFMExplorerProjection.project(session.snapshot(),relations.snapshot(),entries,bounded.matchEvidence(),()->false);
        check(projected.rows().stream().filter(row -> bounded.matchEvidence().get(row.path()).matches()).count()==1,
                "projection must not rematch omitted result-limit entries");
        check(bounded.matchEvidence().keySet().containsAll(entries.keySet()), "every published entry carries evaluated or suppressed evidence");
        var child = tree.nodes().stream().filter(n -> n.entry().label().startsWith("Instruction changes only")).findFirst().orElseThrow();
        var subtree = resolver.resolveFilterDomain(request(child.entry().path(),"source",4096,resolver.generation(),options,new SFMExplorerCancellationToken()));
        queue.remove().run();
        check(subtree.join().entries().size()<domain.entries().size() && !subtree.join().matchedPaths().isEmpty(), "exact descendant domain");
        var cancellation = new SFMExplorerCancellationToken();
        var cancelled = resolver.resolveFilterDomain(request(tree.root(),"source",4096,resolver.generation(),options,cancellation));
        cancellation.cancel();queue.remove().run();reject(cancelled,CancellationException.class);
        var stale = resolver.resolveFilterDomain(request(tree.root(),"source",4096,resolver.generation(),options,new SFMExplorerCancellationToken()));
        lease.close();queue.remove().run();reject(stale,SFMExplorerResolver.StaleGenerationException.class);
        var removed = resolver.resolveFilterDomain(request(tree.root(),"source",4096,resolver.generation(),options,new SFMExplorerCancellationToken()));
        queue.remove().run();reject(removed,IllegalArgumentException.class);
        var leaf = SFMPath.parse("inspection://leaf/");
        try (var leafLease = resolver.mount("leaf",List.of(SFMInMemoryTextExplorerResolver.Node.text(
                SFMExplorerEntry.simple(leaf,"leaf",false,Optional.empty()),"unread body")))) {
            var leafFuture = resolver.resolveFilterDomain(request(leaf,"body",1,resolver.generation(),options,new SFMExplorerCancellationToken()));
            queue.remove().run();
            check(leafFuture.join().childPages().size()==1 && leafFuture.join().childPages().get(0).edges().isEmpty(), "leaf root has required empty root page");
            check(leafFuture.join().matchedPaths().isEmpty(), "document bodies are not searched");
        }
        check(domain.entries().stream().allMatch(entry -> entry.sortKey(SFMExplorerEntry.SUBJECT_COMPLETE_CHILD_COUNT).value().isPresent()),
                "complete child counts attest actual topology for compaction");
        var duplicateRoot = SFMPath.parse("inspection://duplicates/");
        var duplicateChild = SFMPath.parse("inspection://duplicates/child");
        try (var duplicates = resolver.mount("duplicates",List.of(
                SFMInMemoryTextExplorerResolver.Node.directory(SFMExplorerEntry.simple(duplicateRoot,"root",true,Optional.empty()),
                        Collections.nCopies(200001,duplicateChild)),
                SFMInMemoryTextExplorerResolver.Node.text(SFMExplorerEntry.simple(duplicateChild,"child",false,Optional.empty()),"body")))) {
            var large = resolver.resolveFilterDomain(request(duplicateRoot,"child",1,resolver.generation(),options,new SFMExplorerCancellationToken()));
            queue.remove().run();reject(large,IllegalArgumentException.class);
        }
        var mergeRoot = SFMPath.parse("inspection://merge/");
        var mergeParent = SFMPath.parse("inspection://merge/group/");
        var mergeChild = SFMPath.parse("inspection://merge/group/child");
        var immediate = new SFMInMemoryTextExplorerResolver("inspection",Runnable::run,8);
        try (var mergeLease = immediate.mount("merge",List.of(
                SFMInMemoryTextExplorerResolver.Node.directory(SFMExplorerEntry.simple(mergeRoot,"root",true,Optional.empty()),List.of(mergeParent)),
                SFMInMemoryTextExplorerResolver.Node.directory(SFMExplorerEntry.simple(mergeParent,"needle group",true,Optional.empty()),List.of(mergeChild)),
                SFMInMemoryTextExplorerResolver.Node.text(SFMExplorerEntry.simple(mergeChild,"needle child",false,Optional.empty()),"unread")))) {
            var capped = new SFMExplorerFilterDomainResolver() {
                public String scheme(){return immediate.scheme();}
                public long generation(){return immediate.generation();}
                public CompletableFuture<SFMExplorerEntry> describe(SFMPath path,SFMExplorerCancellationToken token){return immediate.describe(path,token);}
                public CompletableFuture<ChildPage> resolveChildren(ChildRequest input){return immediate.resolveChildren(input);}
                public CompletableFuture<FilterDomain> resolveFilterDomain(FilterDomainRequest input){
                    return immediate.resolveFilterDomain(request(input.root(),input.query(),1,input.expectedResolverGeneration(),input.options(),input.cancellation()));
                }
            };
            var registry = new SFMExplorerResolverRegistry();registry.register(capped);
            var loader = new SFMLazyExplorerLoader(registry,new SFMChildRelationRepository(),Runnable::run);
            loader.openRoot(mergeRoot).join();loader.refresh(mergeRoot,8).completion().join();loader.refresh(mergeParent,8).completion().join();
            check(loader.ensureFilterDomain(mergeRoot,"needle",options).orElseThrow().join()==SFMLazyExplorerLoader.LoadDisposition.PUBLISHED,
                    "actual loader accepts complete domain contract");
            var merged=loader.filterProjection(Set.of(mergeRoot),"needle",Set.of(mergeParent),options).orElseThrow();
            var mergeSession=new SFMExplorerSession(new SFMExplorerId("merged-search"),mergeRoot,new SFMSelectionRepository());
            mergeSession.setFilterOptions(options);mergeSession.setFilterQuery("needle");
            var mergeView=SFMExplorerProjection.project(mergeSession.snapshot(),merged.relations(),merged.entries(),merged.matchEvidence(),()->false);
            check(mergeView.filter().matchCount()==1,"ordinary expanded children cannot erase query truncation evidence");
            check(merged.entries().get(mergeParent).sortKey(SFMExplorerEntry.SUBJECT_COMPLETE_CHILD_COUNT).value().equals(Optional.of("1")),
                    "ordinary merge preserves complete topology attestation");
        }
        System.out.println("PASS " + checks + " mounted inspection search / real Nucleo / literal / bound / cancellation / generation checks");
    }
}
