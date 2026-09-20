package ca.teamdman.sfm.client.semantic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMOutlinkRepositoryTests {
    @Test
    void orderingTieEvidenceAndFailureIsolationAreDeterministic() {
        List<String> calls = new ArrayList<>();
        try (var repository = new SFMOutlinkRepository()) {
            repository.register(provider("other:z", 20, calls, false));
            repository.register(provider("sfm:broken", 100, calls, true));
            repository.register(provider("sfm:b", 20, calls, false));
            repository.register(provider("sfm:a", 20, calls, false));

            var result = repository.probe(request(), new SFMSpatialSemanticProvider.Cancellation());
            assertEquals(List.of("sfm:broken", "other:z", "sfm:a", "sfm:b"), calls);
            assertEquals(List.of("other:z", "sfm:a", "sfm:b"),
                    result.matches().stream().map(SFMOutlinkRepository.Match::providerId).toList());
            assertTrue(result.hasPriorityTie());
            assertTrue(result.evidence().stream().anyMatch(evidence ->
                    evidence.providerId().equals("sfm:broken") && evidence.outcome().equals("failed")));
            assertTrue(result.evidence().stream().anyMatch(evidence ->
                    evidence.providerId().equals("sfm:provider_resolution")
                            && evidence.outcome().equals("priority-tie")
                            && evidence.diagnostic().equals("other:z,sfm:a,sfm:b")));
        }
    }

    @Test
    void duplicateIdentityInvalidCertificationAndCancellationFailClosed() {
        try (var repository = new SFMOutlinkRepository()) {
            var one = provider("sfm:one", 1, new ArrayList<>(), false);
            repository.register(one);
            assertThrows(IllegalArgumentException.class, () -> repository.register(one));
        }

        var invalid = new SFMOutlinkRepository();
        invalid.register(new TestProvider("sfm:invalid", 1, false, true) {
            @Override
            public Contribution probe(Request request, Cancellation cancellation) {
                return contribution(region("outside", 20, 30), id());
            }
        });
        var invalidResult = invalid.probe(request(), new SFMSpatialSemanticProvider.Cancellation());
        assertTrue(invalidResult.matches().isEmpty());
        assertEquals("failed", invalidResult.evidence().get(0).outcome());
        invalid.close();

        var cancellation = new SFMSpatialSemanticProvider.Cancellation();
        assertTrue(cancellation.cancel());
        assertFalse(cancellation.cancel());
        try (var repository = new SFMOutlinkRepository()) {
            repository.register(provider("sfm:never", 1, new ArrayList<>(), false));
            assertTrue(repository.probe(request(), cancellation).cancelled());
        }
    }

    @Test
    void closeOwnsProviderLifecycleAndRepositoryCannotBeReused() {
        AtomicBoolean closed = new AtomicBoolean();
        var repository = new SFMOutlinkRepository();
        repository.register(new TestProvider("sfm:lifecycle", 1, false, false) {
            @Override
            public void close() {
                closed.set(true);
            }
        });
        repository.close();
        assertTrue(closed.get());
        assertThrows(IllegalStateException.class, repository::snapshot);
    }

    private static SFMSpatialSemanticProvider provider(
            String id, int priority, List<String> calls, boolean fails
    ) {
        return new TestProvider(id, priority, fails, true) {
            @Override
            public Contribution probe(Request request, Cancellation cancellation) {
                calls.add(id());
                if (fails) throw new IllegalStateException("contributor exploded");
                return super.probe(request, cancellation);
            }
        };
    }

    private static class TestProvider implements SFMSpatialSemanticProvider {
        private final String id;
        private final int priority;
        private final boolean fails;
        private final boolean available;

        private TestProvider(String id, int priority, boolean fails, boolean available) {
            this.id = id;
            this.priority = priority;
            this.fails = fails;
            this.available = available;
        }

        @Override public String id() { return id; }
        @Override public int priority() { return priority; }
        @Override public long generation() { return 7; }
        @Override public boolean available() { return available; }

        @Override
        public Contribution probe(Request request, Cancellation cancellation) {
            if (fails) throw new IllegalStateException("contributor exploded");
            cancellation.throwIfCancelled();
            return contribution(region(id, 0, 10), id);
        }
    }

    private static SFMSpatialSemanticProvider.Contribution contribution(
            SFMSpatialSemanticContract.Region region, String providerId
    ) {
        var action = new SFMSpatialSemanticContract.ActionDraft("sfm:symbol/definition/open", List.of("target"));
        var outlink = new SFMSpatialSemanticContract.Outlink(
                SFMSpatialSemanticContract.OUTLINK_SCHEMA, "outlink:" + providerId, region.id(), null,
                "symbol:target", "definition", SFMSpatialSemanticContract.Intent.NAVIGATE, providerId, 7,
                "test", SFMSpatialSemanticContract.Confidence.RESOLVED,
                SFMSpatialSemanticContract.Completeness.COMPLETE, "start", List.of(action), providerId);
        return new SFMSpatialSemanticProvider.Contribution(
                region,
                new SFMSpatialSemanticContract.Classification(
                        SFMSpatialSemanticContract.ClassificationStatus.ACTIONABLE, null),
                List.of(outlink), List.of(), providerId);
    }

    private static SFMSpatialSemanticProvider.Request request() {
        return new SFMSpatialSemanticProvider.Request(
                new SFMSpatialSemanticContract.SnapshotIdentity(
                        "blake3:w", 1, "file:///A.java", "sha256:a", 2,
                        "blake3:s", 3, "blake3:l", 4),
                "canvas:test", List.of(5.0, 2.0), SFMSpatialSemanticContract.Intent.NAVIGATE);
    }

    private static SFMSpatialSemanticContract.Region region(String id, double left, double right) {
        return new SFMSpatialSemanticContract.Region(
                SFMSpatialSemanticContract.REGION_SCHEMA, "region:" + id, "canvas:test",
                SFMSpatialSemanticContract.Representation.RECTANGLE,
                List.of(new SFMSpatialSemanticContract.AxisBound(left, right),
                        new SFMSpatialSemanticContract.AxisBound(0, 5)),
                "half-open", "test", "sfm:test", List.of());
    }
}
