package ca.teamdman.sfm.client.explorer;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMChildRelationRepositoryTests {
    private static final SFMPath A = SFMPath.parse("file:///C:/a");
    private static final SFMPath B = SFMPath.parse("file:///C:/b");
    private static final SFMPath A_OLD = SFMPath.parse("file:///C:/a/old.txt");
    private static final SFMPath A_NEW = SFMPath.parse("file:///C:/a/new.txt");
    private static final SFMPath A_NEXT = SFMPath.parse("file:///C:/a/next.txt");
    private static final SFMPath B_OLD = SFMPath.parse("file:///C:/b/old.txt");
    private static final SFMPath B_NEW = SFMPath.parse("file:///C:/b/new.txt");

    @Test
    public void multiParentRefreshReplacesOnlyCapturedRowsInOneRevision() {
        SFMChildRelationRepository repository = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket seed = repository.beginRefresh(Set.of(A, B), 1);
        repository.publish(seed, List.of(complete(A, A_OLD, 1), complete(B, B_OLD, 1)));
        long before = repository.snapshot().relation().id();

        SFMChildRelationRepository.RefreshTicket refresh = repository.beginRefresh(Set.of(A, B), 2);
        assertEquals(before, repository.snapshot().relation().id(), "staging must not clear old rows");
        SFMChildRelationRepository.PublishResult result = repository.publish(
                refresh,
                List.of(complete(A, A_NEW, 2), complete(B, B_NEW, 2))
        );

        assertEquals(SFMChildRelationRepository.PublishDisposition.PUBLISHED, result.disposition());
        assertEquals(before + 1, result.snapshot().relation().id());
        assertEquals(Set.of(A_NEW), result.snapshot().relation().childrenOf(A));
        assertEquals(Set.of(B_NEW), result.snapshot().relation().childrenOf(B));
        assertFalse(result.snapshot().relation().edges().contains(new SFMChildEdge(A, A_OLD)));
    }

    @Test
    public void failedRefreshKeepsRelationAndAddsDiagnostic() {
        SFMChildRelationRepository repository = seeded();
        SFMChildRelationRepository.Snapshot before = repository.snapshot();
        SFMChildRelationRepository.RefreshTicket refresh = repository.beginRefresh(Set.of(A), 2);
        SFMChildRelationRepository.PublishResult failure = repository.fail(refresh, "permission denied");

        assertEquals(SFMChildRelationRepository.PublishDisposition.FAILED, failure.disposition());
        assertEquals(before.relation(), failure.snapshot().relation());
        assertEquals(Set.of(A_OLD), failure.snapshot().relation().childrenOf(A));
        assertEquals(List.of("permission denied"), failure.snapshot().pageStates().get(A).diagnostics());
        assertTrue(failure.snapshot().statusGeneration() > before.statusGeneration());
    }

    @Test
    public void newerRequestMakesLatePublicationStale() {
        SFMChildRelationRepository repository = seeded();
        SFMChildRelationRepository.RefreshTicket old = repository.beginRefresh(Set.of(A), 2);
        SFMChildRelationRepository.RefreshTicket newer = repository.beginRefresh(Set.of(A), 3);
        repository.publish(newer, List.of(complete(A, A_NEW, 3)));
        long currentRevision = repository.snapshot().relation().id();

        SFMChildRelationRepository.PublishResult late = repository.publish(
                old,
                List.of(complete(A, A_NEXT, 2))
        );
        assertEquals(SFMChildRelationRepository.PublishDisposition.STALE, late.disposition());
        assertEquals(currentRevision, late.snapshot().relation().id());
        assertEquals(Set.of(A_NEW), late.snapshot().relation().childrenOf(A));
    }

    @Test
    public void partiallyOverlappingRequestRetiresTheWholeSupersededParentSet() {
        SFMChildRelationRepository repository = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket seed = repository.beginRefresh(Set.of(A, B), 1);
        repository.publish(seed, List.of(complete(A, A_OLD, 1), complete(B, B_OLD, 1)));

        SFMChildRelationRepository.RefreshTicket both = repository.beginRefresh(Set.of(A, B), 2);
        SFMChildRelationRepository.RefreshTicket onlyA = repository.beginRefresh(Set.of(A), 3);

        assertEquals(
                SFMChildRelationRepository.PublishDisposition.STALE,
                repository.publish(
                        both,
                        List.of(complete(A, A_NEW, 2), complete(B, B_NEW, 2))
                ).disposition()
        );
        // B was part of the superseded atomic ticket. It must no longer retain
        // that unpublished generation-2 reservation.
        SFMChildRelationRepository.RefreshTicket retryB = repository.beginRefresh(Set.of(B), 1);
        repository.publish(retryB, List.of(complete(B, B_NEW, 1)));
        repository.publish(onlyA, List.of(complete(A, A_NEW, 3)));

        assertEquals(Set.of(A_NEW), repository.snapshot().relation().childrenOf(A));
        assertEquals(Set.of(B_NEW), repository.snapshot().relation().childrenOf(B));
    }

    @Test
    public void olderResolverGenerationCannotSupersedePublishedOrActiveWork() {
        SFMChildRelationRepository repository = seeded();
        SFMChildRelationRepository.RefreshTicket active = repository.beginRefresh(Set.of(A), 3);

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.beginRefresh(Set.of(A), 2)
        );
        repository.publish(active, List.of(complete(A, A_NEW, 3)));
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.beginRefresh(Set.of(A), 2)
        );
        assertEquals(Set.of(A_NEW), repository.snapshot().relation().childrenOf(A));
    }

    @Test
    public void failedInitialLoadIsExplicitlyPartialAndUnmaterialized() {
        SFMChildRelationRepository repository = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket refresh = repository.beginRefresh(Set.of(A), 1);
        SFMChildRelationRepository.PublishResult failed = repository.fail(refresh, "offline");

        SFMChildRelationRepository.PageState state = failed.snapshot().pageStates().get(A);
        assertEquals(SFMChildPage.Completeness.PARTIAL, state.completeness());
        assertEquals(
                SFMChildRelationRepository.PageState.Materialization.REFRESH_FAILED,
                state.materialization()
        );
        SFMPathExpressionResolution resolution = SFMPathExpressionResolver.resolve(
                new SFMPathExpression.Children(new SFMPathExpression.Literal(A)),
                new SFMSelectionRepository(),
                repository
        );
        assertEquals(SFMPathExpressionResolution.Completeness.PARTIAL, resolution.completeness());
        assertTrue(resolution.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().equals("offline")
        ));
    }

    @Test
    public void continuationAppendsWithoutDiscardingPublishedPrefix() {
        SFMChildRelationRepository repository = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket first = repository.beginRefresh(Set.of(A), 4);
        repository.publish(first, List.of(new SFMChildPage(
                A,
                List.of(new SFMChildEdge(A, A_NEW)),
                Optional.of("page-2"),
                SFMChildPage.Completeness.PARTIAL,
                4,
                List.of()
        )));
        SFMChildRelationRepository.RefreshTicket next = repository
                .beginNextPage(A, "page-2")
                .orElseThrow();
        repository.publish(next, List.of(complete(A, A_NEXT, 4)));

        assertEquals(Set.of(A_NEW, A_NEXT), repository.snapshot().relation().childrenOf(A));
        assertEquals(SFMChildPage.Completeness.COMPLETE, repository.snapshot().pageStates().get(A).completeness());
        assertTrue(repository.snapshot().pageStates().get(A).continuation().isEmpty());
    }

    @Test
    public void cancellationRejectsLaterPage() {
        SFMChildRelationRepository repository = seeded();
        SFMChildRelationRepository.RefreshTicket refresh = repository.beginRefresh(Set.of(A), 2);
        assertTrue(repository.cancel(refresh));
        assertFalse(repository.cancel(refresh));
        assertEquals(
                SFMChildRelationRepository.PublishDisposition.CANCELLED,
                repository.publish(refresh, List.of(complete(A, A_NEW, 2))).disposition()
        );
        assertEquals(Set.of(A_OLD), repository.snapshot().relation().childrenOf(A));
    }

    private static SFMChildRelationRepository seeded() {
        SFMChildRelationRepository repository = new SFMChildRelationRepository();
        SFMChildRelationRepository.RefreshTicket seed = repository.beginRefresh(Set.of(A), 1);
        repository.publish(seed, List.of(complete(A, A_OLD, 1)));
        return repository;
    }

    private static SFMChildPage complete(SFMPath parent, SFMPath child, long generation) {
        return new SFMChildPage(
                parent,
                List.of(new SFMChildEdge(parent, child)),
                Optional.empty(),
                SFMChildPage.Completeness.COMPLETE,
                generation,
                List.of()
        );
    }
}
