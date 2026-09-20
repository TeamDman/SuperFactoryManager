package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerSessionRequestObservationTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath STONE = SFMPath.parse("registry://minecraft/item/minecraft/stone");

    @Test
    public void activeEvidenceBecomesRecentPublishedEvidenceAfterGatedPublication() {
        ManualExecutor executor = new ManualExecutor();
        SFMGatedExplorerResolver gated = new SFMGatedExplorerResolver(resolver(executor));
        Fixture fixture = fixture(gated);
        SFMGatedExplorerResolver.Gate gate = gated.armNext(ROOT);

        SFMLazyExplorerLoader.LoadHandle handle = fixture.session().requestChildren(
                ROOT,
                fixture.loader(),
                7
        );
        assertEquals(1, fixture.session().activeRequestCount());
        assertTrue(fixture.session().recentRequestEvidence().isEmpty());
        assertActiveEvidence(fixture.session().activeRequestEvidence().get(0), handle, 7);

        executor.runNext();
        assertTrue(gate.captured().isDone());
        assertFalse(handle.completion().isDone());
        assertEquals(1, fixture.session().activeRequestCount());

        gate.release();
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, handle.completion().join().disposition());
        assertTrue(fixture.session().activeRequestEvidence().isEmpty());

        List<SFMExplorerSession.RequestObservation> recent = fixture.session().recentRequestEvidence();
        assertEquals(1, recent.size());
        assertCompletedEvidence(
                recent.get(0),
                handle,
                SFMLazyExplorerLoader.LoadDisposition.PUBLISHED,
                Optional.empty(),
                7
        );
    }

    @Test
    public void cancellationMovesActiveEvidenceToRecentCancelledEvidenceExactlyOnce() {
        ManualExecutor executor = new ManualExecutor();
        Fixture fixture = fixture(resolver(executor));
        fixture.session().expand(ROOT);

        SFMLazyExplorerLoader.LoadHandle handle = fixture.session().requestChildren(
                ROOT,
                fixture.loader(),
                5
        );
        assertEquals(1, fixture.session().activeRequestCount());

        fixture.session().collapse(ROOT);
        assertEquals(SFMLazyExplorerLoader.LoadDisposition.CANCELLED, handle.completion().join().disposition());
        assertTrue(fixture.session().activeRequestEvidence().isEmpty());
        assertEquals(1, fixture.session().recentRequestEvidence().size());
        assertCompletedEvidence(
                fixture.session().recentRequestEvidence().get(0),
                handle,
                SFMLazyExplorerLoader.LoadDisposition.CANCELLED,
                Optional.of("request cancelled"),
                5
        );

        executor.runNext();
        assertEquals(1, fixture.session().recentRequestEvidence().size(),
                     "late delegate completion must not duplicate cancelled evidence");
        assertTrue(fixture.relations().snapshot().relation().edges().isEmpty());
    }

    @Test
    public void recentRequestEvidenceRetainsOnlyTheNewestBoundedWindow() {
        Fixture fixture = fixture(resolver(Runnable::run));
        long firstRetainedRequestId = -1;
        long lastRequestId = -1;

        for (int i = 0; i < 70; i++) {
            SFMLazyExplorerLoader.LoadHandle handle = fixture.session().requestChildren(
                    ROOT,
                    fixture.loader(),
                    3
            );
            assertEquals(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED, handle.completion().join().disposition());
            if (i == 6) firstRetainedRequestId = handle.evidence().relationRequestId();
            lastRequestId = handle.evidence().relationRequestId();
        }

        List<SFMExplorerSession.RequestObservation> recent = fixture.session().recentRequestEvidence();
        assertEquals(64, recent.size());
        assertEquals(firstRetainedRequestId, recent.get(0).evidence().relationRequestId());
        assertEquals(lastRequestId, recent.get(recent.size() - 1).evidence().relationRequestId());
        assertTrue(recent.stream().allMatch(observation ->
                observation.disposition().equals(Optional.of(SFMLazyExplorerLoader.LoadDisposition.PUBLISHED))
                        && observation.completedAtEpochMillis().isPresent()
                        && observation.diagnostic().isEmpty()
        ));
    }

    private static void assertActiveEvidence(
            SFMExplorerSession.RequestObservation observation,
            SFMLazyExplorerLoader.LoadHandle handle,
            int expectedPageSize
    ) {
        assertEquals(handle.evidence(), observation.evidence());
        assertTrue(observation.startedAtEpochMillis() > 0);
        assertTrue(observation.completedAtEpochMillis().isEmpty());
        assertTrue(observation.disposition().isEmpty());
        assertTrue(observation.diagnostic().isEmpty());
        assertBoundedRequestFields(observation.evidence(), expectedPageSize);
    }

    private static void assertCompletedEvidence(
            SFMExplorerSession.RequestObservation observation,
            SFMLazyExplorerLoader.LoadHandle handle,
            SFMLazyExplorerLoader.LoadDisposition expectedDisposition,
            Optional<String> expectedDiagnostic,
            int expectedPageSize
    ) {
        assertEquals(handle.evidence(), observation.evidence());
        assertEquals(Optional.of(expectedDisposition), observation.disposition());
        assertEquals(expectedDiagnostic, observation.diagnostic());
        long completedAt = observation.completedAtEpochMillis().orElseThrow();
        assertTrue(completedAt >= observation.startedAtEpochMillis());
        assertBoundedRequestFields(observation.evidence(), expectedPageSize);
    }

    private static void assertBoundedRequestFields(
            SFMLazyExplorerLoader.RequestEvidence evidence,
            int expectedPageSize
    ) {
        assertTrue(evidence.relationRequestId() > 0);
        assertEquals(SFMChildRelationRepository.RequestMode.REPLACE, evidence.mode());
        assertEquals(ROOT, evidence.parent());
        assertEquals("registry", evidence.resolverScheme());
        assertTrue(evidence.resolverGeneration() >= 0);
        assertTrue(evidence.continuation().isEmpty());
        assertEquals(expectedPageSize, evidence.pageSize());
    }

    private static Fixture fixture(SFMExplorerResolver resolver) {
        SFMExplorerResolverRegistry registry = new SFMExplorerResolverRegistry();
        registry.register(resolver);
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        return new Fixture(
                relations,
                new SFMLazyExplorerLoader(registry, relations),
                new SFMExplorerSession(
                        new SFMExplorerId("request-observation"),
                        ROOT,
                        new SFMSelectionRepository()
                )
        );
    }

    private static SFMInMemoryRegistryExplorerResolver resolver(Executor executor) {
        SFMExplorerEntry root = SFMExplorerEntry.simple(
                ROOT,
                "Items",
                true,
                Optional.of("registry")
        );
        SFMExplorerEntry stone = SFMExplorerEntry.simple(
                STONE,
                "Stone",
                false,
                Optional.of("minecraft:stone")
        );
        return new SFMInMemoryRegistryExplorerResolver(
                List.of(
                        new SFMInMemoryRegistryExplorerResolver.Node(root, List.of(STONE)),
                        new SFMInMemoryRegistryExplorerResolver.Node(stone, List.of())
                ),
                executor,
                8
        );
    }

    private record Fixture(
            SFMChildRelationRepository relations,
            SFMLazyExplorerLoader loader,
            SFMExplorerSession session
    ) {
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            work.addLast(Objects.requireNonNull(command, "command"));
        }

        private void runNext() {
            work.removeFirst().run();
        }
    }
}
