package ca.teamdman.sfm.client.screen.review.repository;

import ca.teamdman.sfm.client.review.repository.SFMManagedReviewBundleRepository;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewBundleV1;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewRepository;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SFMRepositoryReviewPanelTests {
    @Test
    void opensAndNavigatesManagedChangedFiles(@TempDir Path directory) throws Exception {
        SFMManagedReviewBundleRepository repository = repository(directory);
        var opened = repository.open("SFM d07bef66c to 8e9946d9f review bundle");
        SFMRepositoryReviewPanel panel = new SFMRepositoryReviewPanel(repository, opened);

        assertEquals("d07bef66c", panel.bundle().summary().beforeLabel());
        assertEquals("8e9946d9f", panel.bundle().summary().afterLabel());
        panel.selectFile(1);
        assertEquals(1, panel.selectedIndex());
        panel.setSearch("B.java");
        assertEquals(0, panel.selectedIndex());
        assertTrue(panel.status().contains("1 matches"));
    }

    @Test
    void literalCommentUsesKernelAndRestoresFromPersistentStore(@TempDir Path directory) throws Exception {
        SFMManagedReviewBundleRepository repository = repository(directory);
        var opened = repository.open("SFM d07bef66c to 8e9946d9f review bundle");
        SFMRepositoryReviewPanel panel = new SFMRepositoryReviewPanel(repository, opened);
        assertTrue(panel.selectSourceLine(ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource.Side.AFTER, 1));
        panel.beginComment();
        panel.setDraft("#question Confirm repository review behavior");
        panel.submitComment();
        assertEquals(1, panel.humanCommentCount());

        SFMManagedReviewBundleRepository freshRepository = repository(directory);
        var reopened = freshRepository.open(opened.summary().id());
        assertTrue(reopened.restored());
        SFMRepositoryReviewPanel restored = new SFMRepositoryReviewPanel(freshRepository, reopened);
        assertEquals(1, restored.humanCommentCount());
    }

    @Test
    void zeroMatchSearchIsSafeAndDisablesCommentCreation(@TempDir Path directory) throws Exception {
        SFMManagedReviewBundleRepository repository = repository(directory);
        SFMRepositoryReviewPanel panel = new SFMRepositoryReviewPanel(repository,
                repository.open("SFM d07bef66c to 8e9946d9f review bundle"));

        panel.setSearch("does-not-exist");
        panel.selectFile(0);
        assertFalse(panel.selectSourceLine(
                ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource.Side.AFTER, 0));
        panel.beginComment();

        assertEquals(0, panel.humanCommentCount());
        assertTrue(panel.status().startsWith("Select a before/after source line"));
    }

    @Test
    void validZeroChangeBundleRendersAsSafeEmptyState(@TempDir Path directory) throws Exception {
        SFMManagedReviewBundleRepository base = repository(directory);
        var opened = base.open("SFM d07bef66c to 8e9946d9f review bundle");
        var summary = new SFMRepositoryReviewRepository.BundleSummary(opened.summary().id(),
                "Empty comparison", opened.summary().repositoryName(), opened.summary().beforeLabel(),
                opened.summary().afterLabel(), 0);
        var emptyOpen = new SFMRepositoryReviewRepository.OpenBundle(summary, opened.sessionId(), opened.bundle(),
                java.util.List.of(), opened.dataSource(), false, java.util.List.of());
        SFMRepositoryReviewRepository emptyRepository = fixedRepository(emptyOpen);
        SFMRepositoryReviewPanel panel = new SFMRepositoryReviewPanel(emptyRepository, emptyOpen);

        assertDoesNotThrow(() -> panel.selectFile(0));
        assertFalse(panel.selectSourceLine(SFMReviewCommentDataSource.Side.AFTER, 0));
        panel.beginComment();
        assertEquals(0, panel.humanCommentCount());
    }

    @Test
    void removedFileCanSelectBeforeSourceAndCreateComment(@TempDir Path directory) throws Exception {
        SFMManagedReviewBundleRepository base = repository(directory);
        var opened = base.open("SFM d07bef66c to 8e9946d9f review bundle");
        var removed = new SFMRepositoryReviewRepository.ChangedFile(
                SFMRepositoryReviewBundleV1.ChangeKind.REMOVED, "src/A.java", null, false, java.util.List.of());
        var removedOpen = new SFMRepositoryReviewRepository.OpenBundle(opened.summary(), opened.sessionId(),
                opened.bundle(), java.util.List.of(removed), opened.dataSource(), false, java.util.List.of());
        SFMRepositoryReviewPanel panel = new SFMRepositoryReviewPanel(fixedRepository(removedOpen), removedOpen);

        assertTrue(panel.selectSourceLine(SFMReviewCommentDataSource.Side.BEFORE, 1));
        panel.beginComment();
        panel.setDraft("#question Confirm removed source");
        panel.submitComment();
        assertEquals(1, panel.humanCommentCount());
    }

    private static SFMRepositoryReviewRepository fixedRepository(
            SFMRepositoryReviewRepository.OpenBundle opened
    ) {
        return new SFMRepositoryReviewRepository() {
            @Override public java.util.List<BundleSummary> listBundles() { return java.util.List.of(opened.summary()); }
            @Override public OpenBundle open(String bundleIdOrName) { return opened; }
        };
    }

    private static SFMManagedReviewBundleRepository repository(Path directory) throws Exception {
        Path inbox = Files.createDirectories(directory.resolve("review-bundles"));
        Path stores = Files.createDirectories(directory.resolve("review-sessions"));
        Path target = inbox.resolve("fixture.json");
        if (!Files.exists(target)) {
            String json = Files.readString(fixturePath(), StandardCharsets.UTF_8)
                    .replace("Repository review bundle v1 conformance fixture",
                            "SFM d07bef66c to 8e9946d9f review bundle")
                    .replace("\"label\": \"Before\"", "\"label\": \"d07bef66c\"")
                    .replace("\"label\": \"After\"", "\"label\": \"8e9946d9f\"");
            Files.writeString(target, json, StandardCharsets.UTF_8);
        }
        return new SFMManagedReviewBundleRepository(inbox, id -> new SFMReviewSessionV1Store(
                stores.resolve(SFMReviewSessionV1Kernel.sha256(id.getBytes(StandardCharsets.UTF_8)) + ".json")));
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/repository-review-bundle-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Canonical repository-review fixture not found");
    }
}
