package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SFMReleaseReviewFreshnessTests {
    @Test void ageBecomesQuietAfterTenSecondsWithoutChangingEvidence() {
        long checked = 100_000;
        var evidence = new SFMReleaseReviewFreshness.Evidence(
                SFMReleaseReviewFreshness.State.OUTDATED, checked, "exact evidence", List.of());
        for (long[] sample : new long[][]{{0, 0}, {9999, 9}, {10000, 10},
                {19999, 10}, {20000, 20}, {59999, 50}, {60000, 60}, {-1000, 0}}) {
            assertEquals("checked " + sample[1] + "s ago", evidence.age(checked + sample[0]));
        }
        assertEquals(checked, evidence.checkedAtMillis());
        assertEquals("exact evidence", evidence.details());
        var unavailable = SFMReleaseReviewFreshness.unavailable("not checked");
        assertEquals(unavailable.age(0), unavailable.age(1_000_000));
    }

    @Test void rustCanonicalWindowsRootsRemainUsableForCurrentFilesActions() throws Exception {
        var bindings = List.of(review().repositoryBindings().get(0));
        var json = JsonParser.parseString(fixture("release-review-freshness-v1.json")).getAsJsonObject();
        var lane = json.getAsJsonArray("repository_relationships").get(0).getAsJsonObject();
        lane.getAsJsonObject("working_tree").addProperty("source_dirty", false);
        for (String[] spelling : List.of(
                new String[]{"\\\\?\\D:\\Review Repo", "D:\\Review Repo"},
                new String[]{"\\\\?\\UNC\\server\\share\\Review Repo", "\\\\server\\share\\Review Repo"},
                new String[]{"D:\\Review Repo", "D:\\Review Repo"})) {
            lane.addProperty("repository_root", spelling[0]);
            var evidence = SFMReleaseReviewFreshness.parse(json.toString(), bindings);
            assertEquals(SFMReleaseReviewFreshness.State.CURRENT_AT_CHECK, evidence.state());
            assertEquals(Path.of(spelling[1]), evidence.repositories().get(0).root());
            assertDoesNotThrow(() -> evidence.repositories().get(0).root().toUri());
            assertEquals(json.toString(), evidence.details(), "Keep original native diagnostic evidence");
        }
    }

    @Test void captureComparesItsScopeInsteadOfDeclaringEveryDirtyCheckoutStale() throws Exception {
        var review = SFMReleaseReviewV1Codec.parse(fixture("release-review-working-tree-v2.json"));
        var binding = review.repositoryBindings().get(0);
        var json = JsonParser.parseString(fixture("release-review-freshness-v1.json")).getAsJsonObject();
        var lane = json.getAsJsonArray("repository_relationships").get(0).getAsJsonObject();
        lane.addProperty("lane_id", binding.laneId());
        lane.addProperty("repository_id", binding.repositoryId());
        lane.remove("candidate_commit");
        lane.addProperty("candidate_id", binding.candidateIdentity());
        lane.addProperty("classification", "source_affecting_divergence");
        lane.getAsJsonArray("diagnostics").get(0).getAsJsonObject().addProperty("code", "ambient.source-affecting-paths");
        lane.addProperty("capture_scope_matches", true);
        lane.getAsJsonObject("working_tree").addProperty("source_dirty", false);
        var scopeDiagnostic = new com.google.gson.JsonObject();
        scopeDiagnostic.addProperty("code", "capture.scope-exact");
        scopeDiagnostic.addProperty("message", "Scoped disk content still matches");
        lane.getAsJsonObject("working_tree").getAsJsonArray("diagnostics").add(scopeDiagnostic);
        assertEquals(SFMReleaseReviewFreshness.State.CURRENT_AT_CHECK,
                SFMReleaseReviewFreshness.parse(json.toString(), List.of(binding)).state());
        lane.addProperty("capture_scope_matches", false);
        scopeDiagnostic.addProperty("code", "capture.scope-changed");
        assertEquals(SFMReleaseReviewFreshness.State.OUTDATED,
                SFMReleaseReviewFreshness.parse(json.toString(), List.of(binding)).state());
        scopeDiagnostic.addProperty("code", "capture.read-failed");
        assertEquals(SFMReleaseReviewFreshness.State.UNKNOWN,
                SFMReleaseReviewFreshness.parse(json.toString(), List.of(binding)).state());
        lane.remove("capture_scope_matches");
        assertEquals(SFMReleaseReviewFreshness.State.UNKNOWN,
                SFMReleaseReviewFreshness.parse(json.toString(), List.of(binding)).state());
    }
    @Test void exactHeadDoesNotHideUntrackedSourceAndOriginalReviewIsUnchanged() throws Exception {
        var review = review();
        var bindings = List.of(review.repositoryBindings().get(0));
        String before = SFMReleaseReviewV1Codec.write(review);
        var evidence = SFMReleaseReviewFreshness.parse(fixture("release-review-freshness-v1.json"), bindings);
        assertEquals(SFMReleaseReviewFreshness.State.OUTDATED, evidence.state());
        assertEquals(bindings.get(0).candidateCommit(), evidence.repositories().get(0).head());
        assertEquals(before, SFMReleaseReviewV1Codec.write(review));
        assertEquals("checked 3s ago", evidence.age(evidence.checkedAtMillis() + 3500));
        assertEquals("checked 0s ago", evidence.age(evidence.checkedAtMillis() - 100));
    }

    @Test void cleanDirtyMissingFailedAndRacingEvidenceStayDistinct() throws Exception {
        var bindings = List.of(review().repositoryBindings().get(0));
        var json = JsonParser.parseString(fixture("release-review-freshness-v1.json")).getAsJsonObject();
        var lane = json.getAsJsonArray("repository_relationships").get(0).getAsJsonObject();
        var tree = lane.getAsJsonObject("working_tree");
        tree.addProperty("source_dirty", false);
        assertEquals(SFMReleaseReviewFreshness.State.CURRENT_AT_CHECK, SFMReleaseReviewFreshness.parse(json.toString(), bindings).state());
        tree.remove("source_dirty");
        assertEquals(SFMReleaseReviewFreshness.State.UNKNOWN, SFMReleaseReviewFreshness.parse(json.toString(), bindings).state());
        tree.addProperty("source_dirty", false);
        tree.addProperty("head", "3".repeat(40));
        assertEquals(SFMReleaseReviewFreshness.State.UNKNOWN, SFMReleaseReviewFreshness.parse(json.toString(), bindings).state());
        tree.addProperty("head", "2".repeat(40));
        lane.addProperty("classification", "source_affecting_divergence");
        lane.getAsJsonArray("diagnostics").get(0).getAsJsonObject().addProperty("code", "ambient.source-affecting-paths");
        assertEquals(SFMReleaseReviewFreshness.State.OUTDATED, SFMReleaseReviewFreshness.parse(json.toString(), bindings).state());
        lane.getAsJsonArray("diagnostics").get(0).getAsJsonObject().addProperty("code", "ambient.repository-unavailable");
        assertEquals(SFMReleaseReviewFreshness.State.UNKNOWN, SFMReleaseReviewFreshness.parse(json.toString(), bindings).state());
        lane.addProperty("candidate_commit", "4".repeat(40));
        assertThrows(IllegalArgumentException.class, () -> SFMReleaseReviewFreshness.parse(json.toString(), bindings));
        json.getAsJsonArray("repository_relationships").remove(0);
        assertEquals(SFMReleaseReviewFreshness.State.UNKNOWN, SFMReleaseReviewFreshness.parse(json.toString(), bindings).state());
    }

    @Test void repeatedTicksShareOneProbeAndReopenRejectsLateCompletion() throws Exception {
        var document = review();
        var first = snapshot(document, 1);
        var reopened = snapshot(document, 2);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var oldFinished = new CountDownLatch(1);
        var calls = new AtomicInteger();
        try (var runtime = new SFMReleaseReviewFreshnessRuntime(Executors.newFixedThreadPool(2), (file, bindings) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                started.countDown();
                boolean done = false;
                while (!done) {
                    try { done = release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { /* Deliberately misbehaving old worker. */ }
                }
                oldFinished.countDown();
            }
            return SFMReleaseReviewFreshness.unavailable("result " + call);
        })) {
            runtime.ensure(first, false);
            assertTrue(started.await(3, TimeUnit.SECONDS));
            for (int i = 0; i < 100; i++) runtime.ensure(first, false);
            assertEquals(1, calls.get());
            runtime.ensure(reopened, false);
            awaitResult(runtime, reopened, "result 2");
            release.countDown();
            assertTrue(oldFinished.await(3, TimeUnit.SECONDS));
            assertEquals("result 2", runtime.evidence(reopened).details());
            assertEquals(SFMReleaseReviewFreshness.State.CHECKING, runtime.evidence(first).state());
            runtime.ensure(reopened, true);
            awaitResult(runtime, reopened, "result 3");
        } finally { release.countDown(); }
    }

    private static void awaitResult(SFMReleaseReviewFreshnessRuntime runtime,
                                    SFMReleaseReviewRuntime.Snapshot snapshot, String expected) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!runtime.evidence(snapshot).details().equals(expected) && System.nanoTime() < until) Thread.sleep(5);
        assertEquals(expected, runtime.evidence(snapshot).details());
    }
    private static SFMReleaseReviewRuntime.Snapshot snapshot(SFMReleaseReviewV1 document, long epoch) {
        return new SFMReleaseReviewRuntime.Snapshot(Optional.of(Path.of("disposable.sfm-review.json").toAbsolutePath()),
                Optional.of(document), Optional.of(SFMReleaseReviewStore.Access.READ_ONLY), 1, epoch, false);
    }
    private static SFMReleaseReviewV1 review() throws Exception {
        return SFMReleaseReviewV1Codec.parse(fixture("release-review-v1.json"));
    }
    private static String fixture(String name) throws Exception {
        for (Path root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            Path file = root.resolve("docs/architecture/fixtures").resolve(name);
            if (Files.isRegularFile(file)) return Files.readString(file).replace("\r\n", "\n");
        }
        throw new AssertionError("Missing fixture " + name);
    }
}
