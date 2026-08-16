package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerPathRevealTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://minecraft/item/");
    private static final SFMPath DECOY_A = SFMPath.parse("registry://minecraft/item/a");
    private static final SFMPath DECOY_B = SFMPath.parse("registry://minecraft/item/b");
    private static final SFMPath PACKAGE = SFMPath.parse("registry://minecraft/item/zpackage");
    private static final SFMPath TARGET = SFMPath.parse("registry://minecraft/item/zpackage/target");

    @Test
    void revealPagesUntilEveryAncestorExistsThenPublishesExactSelection() {
        Fixture fixture = fixture();
        fixture.session.setFilterQuery("nothing-matches");
        AtomicInteger selected = new AtomicInteger();

        SFMExplorerPathReveal.Result result = SFMExplorerPathReveal.reveal(
                fixture.session,
                fixture.loader,
                ROOT,
                TARGET,
                1,
                selected::incrementAndGet
        ).toCompletableFuture().join();

        assertEquals(4, result.loadedPages(), "three root pages plus one package page");
        assertEquals(List.of(ROOT, PACKAGE), result.expanded());
        assertTrue(fixture.session.snapshot().expanded().containsAll(List.of(ROOT, PACKAGE)));
        assertEquals(Optional.of(TARGET), fixture.session.snapshot().navigationCursor());
        assertEquals("", fixture.session.snapshot().settings().filterQuery());
        assertEquals(1, selected.get());
        assertTrue(fixture.loader.relationSnapshot().relation().childrenOf(ROOT).contains(PACKAGE));
        assertTrue(fixture.loader.relationSnapshot().relation().childrenOf(PACKAGE).contains(TARGET));

        AtomicInteger repeatedSelection = new AtomicInteger();
        SFMExplorerPathReveal.Result repeated = SFMExplorerPathReveal.reveal(
                fixture.session,
                fixture.loader,
                ROOT,
                TARGET,
                1,
                repeatedSelection::incrementAndGet
        ).toCompletableFuture().join();
        assertEquals(0, repeated.loadedPages(), "already-materialized reveals perform no resolver work");
        assertEquals(1, repeatedSelection.get());
    }

    private static Fixture fixture() {
        ArrayList<SFMInMemoryRegistryExplorerResolver.Node> nodes = new ArrayList<>();
        nodes.add(node(ROOT, true, List.of(DECOY_A, DECOY_B, PACKAGE)));
        nodes.add(node(DECOY_A, false, List.of()));
        nodes.add(node(DECOY_B, false, List.of()));
        nodes.add(node(PACKAGE, true, List.of(TARGET)));
        nodes.add(node(TARGET, false, List.of()));
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(new SFMInMemoryRegistryExplorerResolver(nodes, Runnable::run, 1));
        SFMChildRelationRepository relations = new SFMChildRelationRepository();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(resolvers, relations, Runnable::run);
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("reveal-test"),
                ROOT,
                new SFMSelectionRepository()
        );
        loader.openRoot(ROOT).join();
        return new Fixture(session, loader);
    }

    private static SFMInMemoryRegistryExplorerResolver.Node node(
            SFMPath path,
            boolean expandable,
            List<SFMPath> children
    ) {
        return new SFMInMemoryRegistryExplorerResolver.Node(
                SFMExplorerEntry.simple(path, path.canonical(), expandable, Optional.empty()),
                children
        );
    }

    private record Fixture(SFMExplorerSession session, SFMLazyExplorerLoader loader) {
    }
}
