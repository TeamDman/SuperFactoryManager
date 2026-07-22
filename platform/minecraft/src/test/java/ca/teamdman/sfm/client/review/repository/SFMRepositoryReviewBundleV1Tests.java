package ca.teamdman.sfm.client.review.repository;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Store;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMRepositoryReviewBundleV1Tests {
    @Test
    void canonicalFixtureConformsAndSnapshotBytesAreImmutable() throws Exception {
        SFMRepositoryReviewBundleV1 bundle = fixture();

        assertEquals("sha256:53fcdc3aed1b2cd4dbf9bac7ba409db9ad67df9ef6be1326ce8a14abc80c2af4",
                bundle.id());
        assertEquals(bundle.id(), SFMRepositoryReviewBundleV1Codec.bundleId(
                bundle.repository().name(), bundle.before().id(), bundle.after().id()));
        assertEquals(2, bundle.before().files().size());
        assertEquals(3, bundle.after().files().size());
        assertEquals(List.of("src/A.java", "src/B.java"), bundle.comparison().fileChanges().stream()
                .map(change -> change.afterPath() == null ? change.beforePath() : change.afterPath()).toList());
        byte[] first = bundle.before().files().get(0).content();
        first[0] = 99;
        assertNotEquals(99, bundle.before().files().get(0).content()[0]);
    }

    @Test
    void duplicateKeysInvalidIdentityPathsHashesRangesAndBase64FailClosed() throws Exception {
        String fixture = Files.readString(fixturePath(), StandardCharsets.UTF_8);
        assertInvalid(fixture.replaceFirst("\\{", "{\n  \"schema\": \"duplicate\","), "Duplicate object key");
        assertInvalid(fixture.replace(bundleId(fixture), "sha256:" + "0".repeat(64)), "bundle id");
        assertInvalid(fixture.replaceFirst("assets/data\\.bin", "../data.bin"), "Path");
        assertInvalid(fixture.replace("\"end_byte\": 36", "\"end_byte\": 999"), "range");
        assertInvalid(fixture.replace("\"data\": \"AP9B\"", "\"data\": \"AP9\""), "base64");
        assertInvalid(fixture.replaceFirst("6b86b273", "0b86b273"), "selected bytes sha256");
    }

    @Test
    void decodedBoundsRejectWithoutAllocatingBoundarySizedFixtures() {
        SFMRepositoryReviewBundleV1Codec.validateBounds(100_000, 16L * 1024 * 1024,
                256L * 1024 * 1024, "limit");
        assertThrows(IllegalArgumentException.class, () ->
                SFMRepositoryReviewBundleV1Codec.validateBounds(100_001, 0, 0, "files"));
        assertThrows(IllegalArgumentException.class, () ->
                SFMRepositoryReviewBundleV1Codec.validateBounds(1, 16L * 1024 * 1024 + 1, 0, "file"));
        assertThrows(IllegalArgumentException.class, () ->
                SFMRepositoryReviewBundleV1Codec.validateBounds(1, 0, 256L * 1024 * 1024 + 1, "snapshot"));
    }

    @Test
    void managedInboxProjectsGeneratedCommentsAndRestoresHumanComments(@TempDir Path directory) throws Exception {
        Path inbox = directory.resolve("review-bundles");
        Path stores = directory.resolve("review-sessions");
        Files.createDirectories(inbox);
        Files.copy(fixturePath(), inbox.resolve("fixture.json"));
        var storeFactory = (java.util.function.Function<String, SFMReviewSessionV1Store>) id ->
                new SFMReviewSessionV1Store(stores.resolve(SFMReviewSessionV1Kernel.sha256(
                        id.getBytes(StandardCharsets.UTF_8)) + ".json"));
        SFMManagedReviewBundleRepository repository = new SFMManagedReviewBundleRepository(inbox, storeFactory);

        assertEquals(1, repository.listBundles().size());
        SFMRepositoryReviewRepository.OpenBundle opened = repository.open(
                "Repository review bundle v1 conformance fixture");
        assertFalse(opened.restored());
        assertEquals("sfm.repository-review-session/1:" + opened.summary().id(), opened.sessionId());
        assertEquals(List.of("#modified", "#added"), opened.dataSource().session().comments().stream()
                .map(comment -> SFMReviewSessionV1Kernel.derivedHashtags(comment.text()).get(0)).toList());
        assertEquals(3, opened.dataSource().refresh().documents().size());
        assertTrue(opened.dataSource().session().comments().get(0).provenance().parentCommentIds().stream()
                .anyMatch(value -> value.equals("comparison-operation:replace-src-a-value")));

        SFMReviewCommentDataSource.DocumentView target = opened.dataSource().refresh().documents().stream()
                .filter(document -> document.side() == SFMReviewCommentDataSource.Side.AFTER)
                .filter(document -> document.path().equals("src/B.java")).findFirst().orElseThrow();
        opened.dataSource().createLiteralComment("#approved Persist across reopen.", List.of(
                new SFMReviewCommentDataSource.RangeView(target.id(), 0,
                        target.text().getBytes(StandardCharsets.UTF_8).length)));

        SFMRepositoryReviewRepository.OpenBundle reopened =
                new SFMManagedReviewBundleRepository(inbox, storeFactory).open(opened.summary().id());
        assertTrue(reopened.restored());
        assertTrue(reopened.dataSource().session().comments().stream()
                .anyMatch(comment -> comment.text().equals("#approved Persist across reopen.")));
    }

    @Test
    void managedInboxReportsNoBundlesInvalidBundlesAndAmbiguousNames(@TempDir Path directory) throws Exception {
        var stores = (java.util.function.Function<String, SFMReviewSessionV1Store>) id ->
                new SFMReviewSessionV1Store(directory.resolve("sessions").resolve(id.hashCode() + ".json"));
        SFMManagedReviewBundleRepository empty = new SFMManagedReviewBundleRepository(directory.resolve("empty"), stores);
        assertEquals(SFMRepositoryReviewException.Code.NO_BUNDLES,
                assertThrows(SFMRepositoryReviewException.class, () -> empty.open("missing")).code());

        Path invalidInbox = directory.resolve("invalid");
        Files.createDirectories(invalidInbox);
        Files.writeString(invalidInbox.resolve("bad.json"), "{}", StandardCharsets.UTF_8);
        assertEquals(SFMRepositoryReviewException.Code.INVALID_BUNDLE,
                assertThrows(SFMRepositoryReviewException.class,
                        () -> new SFMManagedReviewBundleRepository(invalidInbox, stores).listBundles()).code());

        Path duplicateInbox = directory.resolve("duplicate");
        Files.createDirectories(duplicateInbox);
        Files.copy(fixturePath(), duplicateInbox.resolve("a.json"));
        Files.copy(fixturePath(), duplicateInbox.resolve("b.json"));
        assertEquals(SFMRepositoryReviewException.Code.INVALID_BUNDLE,
                assertThrows(SFMRepositoryReviewException.class,
                        () -> new SFMManagedReviewBundleRepository(duplicateInbox, stores).listBundles()).code());

        Path ambiguousInbox = directory.resolve("ambiguous");
        Files.createDirectories(ambiguousInbox);
        String first = Files.readString(fixturePath());
        Files.writeString(ambiguousInbox.resolve("a.json"), first);
        SFMRepositoryReviewBundleV1 original = fixture();
        String secondRepository = "fixture-two";
        String secondId = SFMRepositoryReviewBundleV1Codec.bundleId(
                secondRepository, original.before().id(), original.after().id());
        String second = first.replace("\"name\": \"fixture\",", "\"name\": \"" + secondRepository + "\",")
                .replace(original.id(), secondId);
        Files.writeString(ambiguousInbox.resolve("b.json"), second);
        SFMManagedReviewBundleRepository ambiguous = new SFMManagedReviewBundleRepository(ambiguousInbox, stores);
        assertEquals(2, ambiguous.listBundles().size());
        assertEquals(SFMRepositoryReviewException.Code.AMBIGUOUS_NAME,
                assertThrows(SFMRepositoryReviewException.class,
                        () -> ambiguous.open(original.name())).code());
        assertEquals(original.id(), ambiguous.open(original.id()).summary().id());
    }

    private static void assertInvalid(String json, String message) {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> SFMRepositoryReviewBundleV1Codec.parse(json.getBytes(StandardCharsets.UTF_8)))
                .getMessage().contains(message));
    }

    private static String bundleId(String json) {
        int start = json.indexOf("\"id\": \"") + "\"id\": \"".length();
        return json.substring(start, json.indexOf('"', start));
    }

    private static SFMRepositoryReviewBundleV1 fixture() throws Exception {
        return SFMRepositoryReviewBundleV1Codec.parse(fixturePath());
    }

    private static Path fixturePath() {
        Path cursor = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 8 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/repository-review-bundle-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate canonical repository-review bundle fixture");
    }
}
