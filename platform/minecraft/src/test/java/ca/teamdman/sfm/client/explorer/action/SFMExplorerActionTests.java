package ca.teamdman.sfm.client.explorer.action;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMEntitySelectorResolver;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.SFMSelectorDomains;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMExplorerActionTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/root");
    private static final SFMPath CHILD = SFMPath.parse("registry://test/root/child");
    private static final SFMPath SECOND_ROOT = SFMPath.parse("registry://test/second");
    private static final SFMPath SECOND_CHILD = SFMPath.parse("registry://test/second/child");

    @Test
    public void repositoryGenerationAndFocusedSelectorFollowFocusRecency() {
        SFMExplorerRepository repository = new SFMExplorerRepository();
        SFMExplorerRepository.Explorer first = explorer("first", ROOT, SFMExplorerPathPolicy.schemes(Set.of("registry")));
        SFMExplorerRepository.Explorer second = explorer("second", ROOT, SFMExplorerPathPolicy.schemes(Set.of("registry")));

        long initial = repository.generation();
        repository.register(first, false);
        long afterFirst = repository.generation();
        repository.register(second, false);
        long afterSecond = repository.generation();
        assertTrue(initial < afterFirst && afterFirst < afterSecond);
        assertTrue(resolveFocused(repository).isEmpty());

        repository.focus(first.id());
        long afterFirstFocus = repository.generation();
        assertEquals(List.of(first.id()), resolveFocused(repository));
        repository.focus(second.id());
        assertTrue(repository.generation() > afterFirstFocus);
        assertEquals(List.of(second.id()), resolveFocused(repository));
        assertEquals(
                List.of(first.id(), second.id()),
                SFMEntitySelectorResolver.resolve(
                        SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "all"),
                        SFMSelectorDomains.explorers(repository)
                ).identities()
        );
    }

    @Test
    public void explicitOperationsReturnRevisionEvidenceAndSeparateExpansionFromRefresh() {
        SFMExplorerRepository repository = new SFMExplorerRepository();
        SFMExplorerRepository.Explorer explorer = explorer(
                "primary", ROOT, SFMExplorerPathPolicy.schemes(Set.of("registry"))
        );
        repository.register(explorer, true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> {
            throw new AssertionError("factory must not be used");
        });
        SFMEntitySelector exact = exact(explorer.id());

        SFMExplorerActionResult listed = engine.execute(request(exact, new SFMExplorerActionRequest.ListExplorers()));
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, listed.status());
        assertEquals(SFMExplorerActionResult.TargetOutcome.DESCRIBED, listed.targets().get(0).outcome());
        assertEquals(0, listed.targets().get(0).snapshot().revision());

        SFMExplorerActionResult expanded = engine.execute(request(
                exact, new SFMExplorerActionRequest.NodeExpand(ROOT, 8)
        ));
        assertEquals(SFMExplorerActionResult.TargetOutcome.REFRESH_REQUESTED, expanded.targets().get(0).outcome());
        assertTrue(expanded.targets().get(0).snapshot().expanded().contains(ROOT));
        assertTrue(expanded.targets().get(0).loadRequest().isPresent());
        assertEquals(Set.of(CHILD), explorer.loader().relationSnapshot().relation().childrenOf(ROOT));
        assertTrue(expanded.targets().get(0).after().relationRevision() > 0);

        long relationBeforeSecondExpand = explorer.loader().relationSnapshot().relation().id();
        SFMExplorerActionResult alreadyExpanded = engine.execute(request(
                exact, new SFMExplorerActionRequest.NodeExpand(ROOT, 8)
        ));
        assertEquals(SFMExplorerActionResult.TargetOutcome.UNCHANGED, alreadyExpanded.targets().get(0).outcome());
        assertTrue(alreadyExpanded.targets().get(0).loadRequest().isEmpty());
        assertEquals(relationBeforeSecondExpand, explorer.loader().relationSnapshot().relation().id());

        SFMExplorerActionResult refreshed = engine.execute(request(
                exact, new SFMExplorerActionRequest.NodeRefresh(ROOT, 8)
        ));
        assertEquals(SFMExplorerActionResult.TargetOutcome.REFRESH_REQUESTED, refreshed.targets().get(0).outcome());
        assertTrue(explorer.loader().relationSnapshot().relation().id() > relationBeforeSecondExpand);

        SFMExplorerActionResult collapsed = engine.execute(request(
                exact, new SFMExplorerActionRequest.NodeCollapse(ROOT)
        ));
        assertEquals(SFMExplorerActionResult.TargetOutcome.APPLIED, collapsed.targets().get(0).outcome());
        assertFalse(collapsed.targets().get(0).snapshot().expanded().contains(ROOT));

        SFMExplorerActionResult toggled = engine.execute(request(
                exact, new SFMExplorerActionRequest.NodeToggle(ROOT, 8)
        ));
        assertEquals(SFMExplorerActionResult.TargetOutcome.APPLIED, toggled.targets().get(0).outcome());
        assertTrue(toggled.targets().get(0).snapshot().expanded().contains(ROOT));

        engine.execute(request(exact, new SFMExplorerActionRequest.ViewSet(SFMExplorerProjection.View.SMALL_ICONS)));
        engine.execute(request(exact, new SFMExplorerActionRequest.SortSet(SFMExplorerProjection.Sort.ICON)));
        engine.execute(request(exact, new SFMExplorerActionRequest.GroupSet(SFMExplorerProjection.Group.NONE)));
        SFMExplorerActionResult settings = engine.execute(request(
                exact, new SFMExplorerActionRequest.HoistSet(SFMExplorerProjection.Hoist.SHOW_ROOTS)
        ));
        assertEquals(
                new SFMExplorerProjection.Settings(
                        SFMExplorerProjection.View.SMALL_ICONS,
                        SFMExplorerProjection.Sort.ICON,
                        SFMExplorerProjection.Group.NONE,
                        SFMExplorerProjection.Hoist.SHOW_ROOTS
                ),
                settings.targets().get(0).snapshot().settings()
        );

        SFMExplorerActionResult add = engine.execute(request(
                exact, new SFMExplorerActionRequest.RootAdd(SECOND_ROOT)
        ));
        assertEquals(SFMExplorerActionResult.TargetOutcome.APPLIED, add.targets().get(0).outcome());
        assertEquals(Set.of(ROOT, SECOND_ROOT), add.targets().get(0).snapshot().roots());
        assertTrue(add.targets().get(0).after().locationSelectionRevision().isPresent());
        assertTrue(add.targets().get(0).after().selectionRepositoryGeneration()
                > add.targets().get(0).before().orElseThrow().selectionRepositoryGeneration());

        SFMExplorerActionResult roots = engine.execute(request(exact, new SFMExplorerActionRequest.RootList()));
        assertEquals(Set.of(ROOT, SECOND_ROOT), roots.targets().get(0).snapshot().roots());
        SFMExplorerActionResult remove = engine.execute(request(
                exact, new SFMExplorerActionRequest.RootRemove(SECOND_ROOT)
        ));
        assertEquals(Set.of(ROOT), remove.targets().get(0).snapshot().roots());
    }

    @Test
    public void openNewIsExplicitDeferredAndIdempotentWhileExactMissNeverCreates() {
        SFMExplorerRepository repository = new SFMExplorerRepository();
        AtomicInteger factoryCalls = new AtomicInteger();
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, root -> {
            int ordinal = factoryCalls.incrementAndGet();
            return explorer("created-" + ordinal, root, SFMExplorerPathPolicy.schemes(Set.of("registry")));
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> new SFMExplorerActionRequest(
                        exact(new SFMExplorerId("missing")),
                        new SFMExplorerActionRequest.RootAdd(ROOT),
                        SFMExplorerActionRequest.IfNoMatch.OPEN_NEW
                )
        );

        SFMExplorerActionResult exactMiss = engine.execute(request(
                exact(new SFMExplorerId("missing")),
                new SFMExplorerActionRequest.RootAdd(ROOT)
        ));
        assertEquals(SFMExplorerActionResult.Status.NO_TARGETS, exactMiss.status());
        assertEquals(0, factoryCalls.get());

        SFMExplorerActionRequest open = new SFMExplorerActionRequest(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "focused"),
                new SFMExplorerActionRequest.RootAdd(ROOT),
                SFMExplorerActionRequest.IfNoMatch.OPEN_NEW
        );
        SFMExplorerActionEngine.PreparedAction prepared = engine.prepare(open);
        assertTrue(prepared.createsExplorerOnPublish());
        assertEquals(0, factoryCalls.get(), "selector preparation must not call the factory");

        SFMExplorerActionResult created = engine.publish(prepared);
        SFMExplorerActionResult replay = engine.publish(prepared);
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, created.status());
        assertEquals(SFMExplorerActionResult.TargetOutcome.CREATED, created.targets().get(0).outcome());
        assertTrue(created.targets().get(0).before().isEmpty());
        assertEquals(created, replay);
        assertEquals(1, factoryCalls.get());
        assertEquals(1, repository.explorersInStableOrder().size());
        assertEquals(created.targets().get(0).explorerId(), repository.stateSnapshot().focused().orElseThrow());
    }

    @Test
    public void expansionPublishesViewStateBeforeItsUnmaterializedLoadCompletes() {
        ManualExecutor executor = new ManualExecutor();
        SFMExplorerRepository repository = new SFMExplorerRepository();
        SFMExplorerRepository.Explorer explorer = explorer(
                "delayed", ROOT, SFMExplorerPathPolicy.schemes(Set.of("registry")), executor
        );
        repository.register(explorer, true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> explorer);

        SFMExplorerActionResult result = engine.execute(request(
                exact(explorer.id()), new SFMExplorerActionRequest.NodeExpand(ROOT, 8)
        ));

        assertTrue(result.targets().get(0).snapshot().expanded().contains(ROOT));
        assertEquals(1, executor.size());
        assertTrue(explorer.loader().relationSnapshot().relation().childrenOf(ROOT).isEmpty());
        executor.runNext();
        assertEquals(Set.of(CHILD), explorer.loader().relationSnapshot().relation().childrenOf(ROOT));
    }

    @Test
    public void exactNodeActionRejectsPathReachableOnlyFromAnotherExplorersRoot() {
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                List.of(
                        node(ROOT, true, List.of(CHILD)),
                        node(CHILD, false, List.of()),
                        node(SECOND_ROOT, true, List.of(SECOND_CHILD)),
                        node(SECOND_CHILD, false, List.of())
                ),
                Runnable::run,
                16
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader sharedLoader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        SFMExplorerPathPolicy pathPolicy = SFMExplorerPathPolicy.schemes(Set.of("registry"));
        SFMExplorerRepository.Explorer first = new SFMExplorerRepository.Explorer(
                new SFMExplorerSession(new SFMExplorerId("first"), ROOT, new SFMSelectionRepository()),
                sharedLoader,
                pathPolicy,
                8
        );
        SFMExplorerRepository.Explorer second = new SFMExplorerRepository.Explorer(
                new SFMExplorerSession(new SFMExplorerId("second"), SECOND_ROOT, new SFMSelectionRepository()),
                sharedLoader,
                pathPolicy,
                8
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        repository.register(first, true);
        repository.register(second, false);
        sharedLoader.refresh(SECOND_ROOT, 8).completion().join();
        assertEquals(
                Set.of(SECOND_CHILD),
                sharedLoader.relationSnapshot().relation().childrenOf(SECOND_ROOT),
                "the shared relation must contain explorer B's child before exercising explorer A"
        );

        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> first);
        SFMExplorerActionResult result = engine.execute(request(
                exact(first.id()),
                new SFMExplorerActionRequest.NodeExpand(SECOND_CHILD, 8)
        ));

        assertEquals(SFMExplorerActionResult.Status.REJECTED, result.status());
        assertFalse(first.session().snapshot().expanded().contains(SECOND_CHILD));
        assertTrue(result.diagnostics().stream().anyMatch(message ->
                message.contains("Node path is not materialized in this explorer")
        ));
    }

    @Test
    public void locationSetPreservesCanonicalExpressionsAndRejectsStaleOrUnauthorizedSaves() {
        SFMExplorerRepository repository = new SFMExplorerRepository();
        SFMExplorerRepository.Explorer explorer = explorer(
                "location", ROOT, SFMExplorerPathPolicy.schemes(Set.of("registry"))
        );
        repository.register(explorer, true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(
                repository,
                ignored -> explorer,
                ignored -> { },
                path -> path.equals(SECOND_ROOT)
                        ? Optional.of("typed location lacks authority")
                        : Optional.empty()
        );
        long initialRevision = explorer.session().snapshot().revision();
        SFMPathExpression composed = SFMPathExpression.parse(
                "union(" + ROOT.canonical() + "," + CHILD.canonical() + ")"
        );

        SFMExplorerActionResult applied = engine.execute(request(
                exact(explorer.id()),
                new SFMExplorerActionRequest.LocationSet(composed, initialRevision)
        ));
        assertEquals(SFMExplorerActionResult.Status.SUCCEEDED, applied.status());
        assertEquals(composed.canonical(), applied.targets().get(0).snapshot().location().canonical());
        assertEquals(Set.of(ROOT, CHILD), applied.targets().get(0).snapshot().roots());

        long appliedRevision = applied.targets().get(0).after().sessionRevision();
        SFMExplorerActionResult stale = engine.execute(request(
                exact(explorer.id()),
                new SFMExplorerActionRequest.LocationSet(new SFMPathExpression.Literal(ROOT), initialRevision)
        ));
        assertEquals(SFMExplorerActionResult.Status.REJECTED, stale.status());
        assertEquals(composed, explorer.session().snapshot().location());
        assertTrue(stale.diagnostics().stream().anyMatch(message -> message.contains("expected")));

        SFMExplorerActionResult unauthorized = engine.execute(request(
                exact(explorer.id()),
                new SFMExplorerActionRequest.LocationSet(
                        new SFMPathExpression.Literal(SECOND_ROOT),
                        appliedRevision
                )
        ));
        assertEquals(SFMExplorerActionResult.Status.REJECTED, unauthorized.status());
        assertEquals(composed, explorer.session().snapshot().location());
        assertTrue(unauthorized.diagnostics().stream().anyMatch(message -> message.contains("authority")));
    }

    private static List<SFMExplorerId> resolveFocused(SFMExplorerRepository repository) {
        return SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "focused"),
                SFMSelectorDomains.explorers(repository)
        ).identities();
    }

    private static SFMEntitySelector exact(SFMExplorerId id) {
        return SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, id.value());
    }

    private static SFMExplorerActionRequest request(
            SFMEntitySelector selector,
            SFMExplorerActionRequest.Operation operation
    ) {
        return new SFMExplorerActionRequest(selector, operation, SFMExplorerActionRequest.IfNoMatch.FAIL);
    }

    private static SFMExplorerRepository.Explorer explorer(
            String id,
            SFMPath root,
            SFMExplorerPathPolicy pathPolicy
    ) {
        return explorer(id, root, pathPolicy, Runnable::run);
    }

    private static SFMExplorerRepository.Explorer explorer(
            String id,
            SFMPath root,
            SFMExplorerPathPolicy pathPolicy,
            Executor executor
    ) {
        ArrayList<SFMInMemoryRegistryExplorerResolver.Node> nodes = new ArrayList<>();
        List<SFMPath> children = root.equals(ROOT) ? List.of(CHILD) : List.of();
        nodes.add(node(root, true, children));
        if (root.equals(ROOT)) nodes.add(node(CHILD, false, List.of()));
        if (!root.equals(SECOND_ROOT)) nodes.add(node(SECOND_ROOT, true, List.of()));
        SFMInMemoryRegistryExplorerResolver resolver = new SFMInMemoryRegistryExplorerResolver(
                nodes, executor, 16
        );
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(resolver);
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers, new SFMChildRelationRepository()
        );
        return new SFMExplorerRepository.Explorer(
                new SFMExplorerSession(new SFMExplorerId(id), root, new SFMSelectionRepository()),
                loader,
                pathPolicy,
                8
        );
    }

    private static SFMInMemoryRegistryExplorerResolver.Node node(
            SFMPath path,
            boolean expandable,
            List<SFMPath> children
    ) {
        String label = path.segments().isEmpty()
                ? path.authority()
                : path.segments().get(path.segments().size() - 1);
        return new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(path, label, expandable, Optional.of("test")),
                children
        );
    }

    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> work = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            work.add(Objects.requireNonNull(command, "command"));
        }

        private int size() {
            return work.size();
        }

        private void runNext() {
            work.removeFirst().run();
        }
    }
}
