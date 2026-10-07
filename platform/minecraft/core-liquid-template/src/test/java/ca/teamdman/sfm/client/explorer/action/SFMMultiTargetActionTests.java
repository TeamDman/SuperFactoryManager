package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMMultiTargetActionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/root");
    private static final SFMPath EXTRA = SFMPath.parse("registry://test/extra");

    @Test
    public void allSelectorPublishesEveryCompatibleMutationWithTypedEvidence() {
        Fixture fixture = fixture(SFMExplorerPathPolicy.schemes(Set.of("registry")));
        SFMExplorerActionResult result = fixture.engine().execute(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.ViewSet(SFMExplorerProjection.View.SMALL_ICONS),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, result.status());
        assertEquals(List.of(new SFMExplorerId("one"), new SFMExplorerId("two")),
                     result.targets().stream().map(SFMExplorerActionResult.TargetResult::explorerId).toList());
        assertTrue(result.targets().stream().allMatch(target ->
                target.outcome() == SFMExplorerActionResult.TargetOutcome.APPLIED
                        && target.snapshot().settings().view() == SFMExplorerProjection.View.SMALL_ICONS
                        && target.after().sessionRevision()
                        > target.before().orElseThrow().sessionRevision()
        ));
    }

    @Test
    public void oneIncompatibleTargetRejectsTheWholeCapturedSet() {
        SFMExplorerPathPolicy rejecting = path -> path.equals(EXTRA)
                ? Optional.of("extra root is intentionally incompatible")
                : Optional.empty();
        Fixture fixture = fixture(rejecting);
        Set<SFMPath> oneBefore = fixture.one().session().snapshot().roots();
        Set<SFMPath> twoBefore = fixture.two().session().snapshot().roots();

        SFMExplorerActionResult result = fixture.engine().execute(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.RootAdd(EXTRA),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertEquals(SFMExplorerActionResult.Status.REJECTED, result.status());
        assertEquals(2, result.targets().size(), "every captured target receives an outcome");
        assertTrue(result.targets().stream().allMatch(target ->
                target.outcome() == SFMExplorerActionResult.TargetOutcome.REJECTED
        ));
        assertEquals(oneBefore, fixture.one().session().snapshot().roots());
        assertEquals(twoBefore, fixture.two().session().snapshot().roots());
        assertEquals(0, fixture.one().session().selectionRepository().stateSnapshot().generation());
        assertEquals(0, fixture.two().session().selectionRepository().stateSnapshot().generation());
        assertTrue(result.diagnostics().stream().anyMatch(message -> message.contains("incompatible")));
    }

    @Test
    public void locationReplacementRollsBackEveryTargetWhenOneTargetRejectsTheExpression() {
        SFMExplorerPathPolicy rejecting = path -> path.equals(EXTRA)
                ? Optional.of("extra location is intentionally incompatible")
                : Optional.empty();
        Fixture fixture = fixture(rejecting);

        SFMExplorerActionResult result = fixture.engine().execute(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.LocationSet(
                        new SFMPathExpression.Literal(EXTRA),
                        0
                ),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertEquals(SFMExplorerActionResult.Status.REJECTED, result.status());
        assertEquals(Set.of(ROOT), fixture.one().session().snapshot().roots());
        assertEquals(Set.of(ROOT), fixture.two().session().snapshot().roots());
        assertEquals(new SFMPathExpression.Literal(ROOT), fixture.one().session().snapshot().location());
        assertEquals(new SFMPathExpression.Literal(ROOT), fixture.two().session().snapshot().location());
    }

    @Test
    public void oneStaleSessionPreventsEveryPreparedMutation() {
        Fixture fixture = fixture(SFMExplorerPathPolicy.schemes(Set.of("registry")));
        SFMExplorerActionEngine.PreparedAction prepared = fixture.engine().prepare(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.SortSet(SFMExplorerProjection.Sort.ICON),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        fixture.two().session().setGroup(SFMExplorerProjection.Group.NONE);
        SFMExplorerActionResult result = fixture.engine().publish(prepared);

        assertEquals(SFMExplorerActionResult.Status.STALE, result.status());
        assertEquals(SFMExplorerProjection.Sort.NAME, fixture.one().session().snapshot().settings().sort());
        assertEquals(SFMExplorerProjection.Sort.NAME, fixture.two().session().snapshot().settings().sort());
        assertEquals(SFMExplorerProjection.Group.HIERARCHY, fixture.one().session().snapshot().settings().group());
        assertEquals(SFMExplorerProjection.Group.NONE, fixture.two().session().snapshot().settings().group());
        assertTrue(result.targets().stream().allMatch(target ->
                target.outcome() == SFMExplorerActionResult.TargetOutcome.STALE
        ));
    }

    @Test
    public void successfulMultiRootMutationUpdatesBothSelectionLedgersOrNeither() {
        Fixture fixture = fixture(SFMExplorerPathPolicy.schemes(Set.of("registry")));
        SFMExplorerActionResult result = fixture.engine().execute(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.RootAdd(EXTRA),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, result.status());
        assertEquals(Set.of(ROOT, EXTRA), fixture.one().session().snapshot().roots());
        assertEquals(Set.of(ROOT, EXTRA), fixture.two().session().snapshot().roots());
        assertTrue(result.targets().stream().allMatch(target ->
                target.after().selectionRepositoryGeneration()
                        > target.before().orElseThrow().selectionRepositoryGeneration()
                        && target.after().locationSelectionRevision().isPresent()
        ));
    }

    @Test
    public void applyTimeFailureRestoresEverySessionAndSelectionLedger() {
        SFMExplorerRepository repository = new SFMExplorerRepository();
        SFMSelectionRepository firstSelections = new SFMSelectionRepository();
        SFMSelectionRepository failingSelections = new SFMSelectionRepository(new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.of("UTC");
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                throw new IllegalStateException("injected revision clock failure");
            }
        });
        SFMExplorerRepository.Explorer one = explorer("one", firstSelections);
        SFMExplorerRepository.Explorer two = explorer("two", failingSelections);
        repository.register(one, true);
        repository.register(two, false);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> {
            throw new AssertionError("factory must not be used");
        });

        SFMExplorerActionResult result = engine.execute(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.RootAdd(EXTRA),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertEquals(SFMExplorerActionResult.Status.REJECTED, result.status());
        assertEquals(Set.of(ROOT), one.session().snapshot().roots());
        assertEquals(Set.of(ROOT), two.session().snapshot().roots());
        assertEquals(0, firstSelections.stateSnapshot().generation());
        assertEquals(0, failingSelections.stateSnapshot().generation());
        assertTrue(result.diagnostics().stream().anyMatch(message ->
                message.contains("explorer.transaction-aborted")
                        && message.contains("injected revision clock failure")
        ));
    }

    @Test
    public void committedHookDoesNotGrantAuthorityForRejectedOrMissingTargets() {
        SFMExplorerPathPolicy rejecting = path -> path.equals(EXTRA)
                ? Optional.of("extra root is intentionally incompatible")
                : Optional.empty();
        Fixture fixture = fixture(rejecting);
        AtomicInteger committedHooks = new AtomicInteger();
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(
                fixture.repository(),
                ignored -> {
                    throw new AssertionError("factory must not be used");
                },
                ignored -> committedHooks.incrementAndGet()
        );

        SFMExplorerActionResult rejected = engine.execute(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.RootAdd(EXTRA),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));
        SFMExplorerActionResult missing = engine.execute(new SFMExplorerActionRequest(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, "missing"),
                new SFMExplorerActionRequest.RootAdd(EXTRA),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertEquals(SFMExplorerActionResult.Status.REJECTED, rejected.status());
        assertEquals(SFMExplorerActionResult.Status.NO_TARGETS, missing.status());
        assertEquals(0, committedHooks.get());
    }

    @Test
    public void missingResolverOnLaterTargetRejectsBeforeEarlierResolverStarts() {
        AtomicInteger resolverStarts = new AtomicInteger();
        SFMExplorerResolver countingResolver = new SFMExplorerResolver() {
            @Override
            public String scheme() {
                return "registry";
            }

            @Override
            public long generation() {
                return 0;
            }

            @Override
            public CompletableFuture<SFMExplorerEntry> describe(
                    SFMPath path,
                    ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken cancellation
            ) {
                return CompletableFuture.completedFuture(node(path).entry());
            }

            @Override
            public CompletableFuture<ChildPage> resolveChildren(ChildRequest request) {
                resolverStarts.incrementAndGet();
                return CompletableFuture.completedFuture(new ChildPage(
                        request.parent(), List.of(), Optional.empty(), 0, List.of(), 0
                ));
            }
        };
        SFMExplorerResolverRegistry firstResolvers = new SFMExplorerResolverRegistry();
        firstResolvers.register(countingResolver);
        SFMExplorerRepository.Explorer first = new SFMExplorerRepository.Explorer(
                new SFMExplorerSession(new SFMExplorerId("one"), ROOT, new SFMSelectionRepository()),
                new SFMLazyExplorerLoader(firstResolvers, new SFMChildRelationRepository()),
                SFMExplorerPathPolicy.schemes(Set.of("registry")),
                8
        );
        SFMExplorerRepository.Explorer second = new SFMExplorerRepository.Explorer(
                new SFMExplorerSession(new SFMExplorerId("two"), ROOT, new SFMSelectionRepository()),
                new SFMLazyExplorerLoader(
                        new SFMExplorerResolverRegistry(),
                        new SFMChildRelationRepository()
                ),
                SFMExplorerPathPolicy.schemes(Set.of("registry")),
                8
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        repository.register(first, true);
        repository.register(second, false);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> first);

        SFMExplorerActionResult result = engine.execute(new SFMExplorerActionRequest(
                all(),
                new SFMExplorerActionRequest.NodeRefresh(ROOT, 8),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertEquals(SFMExplorerActionResult.Status.REJECTED, result.status());
        assertEquals(0, resolverStarts.get(), "pure preflight must finish for every target before work starts");
        assertEquals(0, first.session().activeRequestCount());
        assertEquals(0, second.session().activeRequestCount());
        assertTrue(result.diagnostics().stream().anyMatch(message ->
                message.contains("explorer.side-effect-preflight-failed")
                        && message.contains("No lazy explorer resolver")
        ));
    }

    private static SFMEntitySelector all() {
        return SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "all");
    }

    private static Fixture fixture(SFMExplorerPathPolicy secondPolicy) {
        SFMExplorerRepository repository = new SFMExplorerRepository();
        SFMExplorerRepository.Explorer one = explorer("one", SFMExplorerPathPolicy.schemes(Set.of("registry")));
        SFMExplorerRepository.Explorer two = explorer("two", secondPolicy);
        repository.register(one, true);
        repository.register(two, false);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> {
            throw new AssertionError("factory must not be used");
        });
        return new Fixture(repository, one, two, engine);
    }

    private static SFMExplorerRepository.Explorer explorer(String id, SFMExplorerPathPolicy policy) {
        return explorer(id, policy, new SFMSelectionRepository());
    }

    private static SFMExplorerRepository.Explorer explorer(String id, SFMSelectionRepository selections) {
        return explorer(id, SFMExplorerPathPolicy.schemes(Set.of("registry")), selections);
    }

    private static SFMExplorerRepository.Explorer explorer(
            String id,
            SFMExplorerPathPolicy policy,
            SFMSelectionRepository selections
    ) {
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                List.of(node(ROOT), node(EXTRA)), Runnable::run, 8
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        return new SFMExplorerRepository.Explorer(
                new SFMExplorerSession(new SFMExplorerId(id), ROOT, selections),
                new SFMLazyExplorerLoader(resolvers, new SFMChildRelationRepository()),
                policy,
                8
        );
    }

    private static SFMInMemoryRegistryExplorerResolver.Node node(SFMPath path) {
        String label = path.segments().get(path.segments().size() - 1);
        return new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(path, label, true, Optional.of("test")),
                List.of()
        );
    }

    private record Fixture(
            SFMExplorerRepository repository,
            SFMExplorerRepository.Explorer one,
            SFMExplorerRepository.Explorer two,
            SFMExplorerActionEngine engine
    ) {
    }
}
