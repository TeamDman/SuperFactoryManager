package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.explorer.lazy.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SFMReviewOfflineExplorerTreeTests {
    @Test void standardResolverCanBrowseAndReadRetainedEvidenceWithoutRepository() throws Exception {
        var ledger = SFMReviewOfflineEvidenceTests.fixture();
        String original = SFMReleaseReviewLedgerV3Codec.write(ledger);
        var tree = SFMReviewOfflineExplorerTree.prepare("disposable", "absent/review.sfm-review.json", ledger);
        var resolver = new SFMInMemoryTextExplorerResolver(SFMReviewOfflineExplorerTree.SCHEME, Runnable::run, 128);
        var token = new SFMExplorerCancellationToken();
        try (var lease = resolver.mount(tree.root().authority(), tree.nodes())) {
            var children = resolver.resolveChildren(new SFMExplorerResolver.ChildRequest(tree.root(), Optional.empty(),
                    128, resolver.generation(), token)).join();
            assertEquals(4, children.entries().size());
            assertTrue(children.complete());
            for (var node : tree.nodes()) {
                assertEquals(node.entry(), resolver.describe(node.entry().path(), token).join());
                if (node.text().isEmpty()) continue;
                var read = resolver.readText(new SFMResolverTextRequest(node.entry().path(), tree.root(),
                        Optional.empty(), 4 * 1024 * 1024, resolver.generation(), token)).join();
                assertEquals(SFMResolverTextResult.Status.READY, read.status());
                assertEquals(node.text(), read.text());
            }
            for (var comment : ledger.state().reviewSession().comments())
                assertTrue(tree.nodes().stream().anyMatch(node -> node.text().filter(comment.text()::equals).isPresent()));
            assertTrue(tree.nodes().stream().anyMatch(node -> node.text().filter(text ->
                    text.contains("No current release completion claim")).isPresent()));
        }
        assertTrue(resolver.root("disposable").isEmpty());
        assertEquals(original, SFMReleaseReviewLedgerV3Codec.write(ledger));
    }

    @Test void preparationIsDeterministicAndSourceLeavesKeepTheirLanguageExtension() throws Exception {
        var ledger = SFMReviewOfflineEvidenceTests.fixture();
        var first = SFMReviewOfflineExplorerTree.prepare("same", "offline.json", ledger);
        assertEquals(first, SFMReviewOfflineExplorerTree.prepare("same", "offline.json", ledger));
        var sourceNodes = first.nodes().stream().filter(node ->
                node.entry().sortKey("sfm:evidence/source-path").value().filter(path -> path.endsWith(".java")).isPresent()).toList();
        assertFalse(sourceNodes.isEmpty());
        assertTrue(sourceNodes.stream().allMatch(node -> node.entry().path().extension().equals("java")));
    }
    @Test void missingReportExplainsPathAndStorageWithoutClaimingGitIsUnavailable() throws Exception {
        var original = SFMReviewOfflineEvidenceTests.fixture();
        var storage = original.evidence().documents().stream().map(document -> new SFMReviewEvidenceTable.GitStorage(
                document.sha256(), "renamed/Example.java", new SFMReviewEvidenceTable.GitReference("sfm", "1".repeat(40), "2".repeat(40))))
                .distinct().toList();
        var ledger = new SFMReleaseReviewLedgerV3(original.targets(), original.state(),
                new SFMReviewEvidenceTable(java.util.List.of(), original.evidence().documents(), storage));
        var tree = SFMReviewOfflineExplorerTree.prepare("missing", "review.json", ledger);
        var report = tree.nodes().stream().filter(node -> node.entry().label().startsWith("Missing embedded evidence"))
                .findFirst().orElseThrow().text().orElseThrow();
        assertTrue(report.contains("This offline view deliberately does not read Git"));
        assertTrue(report.contains("Git availability has not been checked"));
        assertTrue(report.contains("original target unchanged"));
        assertTrue(report.contains("Storage path: renamed/Example.java"));
        for (var document : original.evidence().documents()) assertTrue(report.contains("Original path: " + document.path()));
    }
}
