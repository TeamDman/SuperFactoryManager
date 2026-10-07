package ca.teamdman.sfm.client.control;

import ca.teamdman.sfm.client.explorer.SFMChildRelationRepository;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMSelectionRepository;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionEngine;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerPathPolicy;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMExplorerControlBoundsTests {
    private static final SFMPath ROOT = SFMPath.parse("registry://test/root");

    @Test
    void oversizedCapturedTargetSetIsRejectedBeforePublication() {
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        resolvers.register(new SFMInMemoryRegistryExplorerResolver(
                List.of(new SFMInMemoryRegistryExplorerResolver.Node(
                        SFMExplorerEntry.simple(ROOT, "root", true, Optional.of("test")),
                        List.of()
                )),
                Runnable::run,
                8
        ));
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        ArrayList<SFMExplorerSession> sessions = new ArrayList<>();
        for (int index = 0; index <= SFMClientControlServer.MAX_EXPLORER_TARGET_RESULTS; index++) {
            SFMExplorerSession session = new SFMExplorerSession(
                    new SFMExplorerId("explorer-" + index),
                    ROOT,
                    new SFMSelectionRepository()
            );
            sessions.add(session);
            repository.register(new SFMExplorerRepository.Explorer(
                    session,
                    loader,
                    SFMExplorerPathPolicy.schemes(Set.of("registry")),
                    8
            ), index == 0);
        }
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> {
            throw new AssertionError("factory must not run");
        });
        SFMExplorerActionEngine.PreparedAction prepared = engine.prepare(new SFMExplorerActionRequest(
                SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.EXPLORER, "all"),
                new SFMExplorerActionRequest.ViewSet(SFMExplorerProjection.View.SMALL_ICONS),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertThrows(IllegalArgumentException.class, () -> SFMExplorerControlBounds.validatePrepared(prepared));
        assertTrue(sessions.stream().allMatch(session ->
                session.snapshot().settings().view() == SFMExplorerProjection.View.LIST
        ));
    }

    @Test
    void prospectiveRootOverflowIsRejectedByThePureSnapshotCheck() {
        TreeSet<SFMPath> roots = new TreeSet<>();
        for (int index = 0; index < SFMClientControlServer.MAX_EXPLORER_ROOTS_PER_TARGET; index++) {
            roots.add(SFMPath.parse("registry://test/root/" + index));
        }
        SFMPath first = roots.first();
        SFMExplorerSession.Snapshot snapshot = new SFMExplorerSession.Snapshot(
                new SFMExplorerId("bounded"),
                0,
                new SFMPathExpression.Literal(first),
                roots,
                List.copyOf(roots),
                Set.of(),
                Optional.empty(),
                0,
                SFMExplorerProjection.Settings.defaults(),
                Set.of(),
                Optional.empty(),
                false
        );

        assertThrows(IllegalArgumentException.class, () -> SFMExplorerControlBounds.validateSnapshot(
                snapshot,
                new SFMExplorerActionRequest.RootAdd(SFMPath.parse("registry://test/root/new"))
        ));
    }

    @Test
    void individuallyValidRootsCannotComposeAnOversizedRequiredResponse() {
        TreeSet<SFMPath> roots = new TreeSet<>();
        String largeSegment = "x".repeat(10_000);
        for (int index = 0; index < 40; index++) {
            roots.add(SFMPath.parse("registry://test/root/" + index + largeSegment));
        }
        SFMPath first = roots.first();
        SFMExplorerSession session = new SFMExplorerSession(
                new SFMExplorerId("aggregate"),
                first,
                new SFMSelectionRepository()
        );
        for (SFMPath root : roots) session.addRoot(root);
        SFMExplorerResolverRegistry resolvers = new SFMExplorerResolverRegistry();
        SFMLazyExplorerLoader loader = new SFMLazyExplorerLoader(
                resolvers,
                new SFMChildRelationRepository()
        );
        SFMExplorerRepository repository = new SFMExplorerRepository();
        repository.register(new SFMExplorerRepository.Explorer(
                session,
                loader,
                SFMExplorerPathPolicy.schemes(Set.of("registry")),
                8
        ), true);
        SFMExplorerActionEngine engine = new SFMExplorerActionEngine(repository, ignored -> {
            throw new AssertionError("factory must not run");
        });
        SFMExplorerActionEngine.PreparedAction prepared = engine.prepare(new SFMExplorerActionRequest(
                SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.EXPLORER, "all"),
                new SFMExplorerActionRequest.Describe(),
                SFMExplorerActionRequest.IfNoMatch.FAIL
        ));

        assertThrows(IllegalArgumentException.class, () -> SFMExplorerControlBounds.validatePrepared(prepared));
    }

    @Test
    void visiblePathBudgetIsGlobalAndNeverAcceptsAnOversizedCanonicalPath() {
        SFMExplorerControlBounds.VisiblePathBudget budget = SFMExplorerControlBounds.visiblePathBudget();
        int accepted = 0;
        String segment = "x".repeat(1_000);
        while (budget.tryInclude("registry://test/root/" + accepted + segment)) accepted++;

        assertTrue(accepted > 0);
        assertTrue(accepted < 512);
        assertFalse(budget.tryInclude("not a canonical path"));
    }

    @Test
    void evidenceTextIsControlFreeAndNeverSplitsUtf8CodePoints() {
        String bounded = SFMExplorerControlBounds.boundedEvidenceText("🙂".repeat(5000));
        assertTrue(bounded.getBytes(StandardCharsets.UTF_8).length
                <= SFMClientControlServer.MAX_EXPLORER_EVIDENCE_TEXT_BYTES);
        assertTrue(bounded.codePoints().noneMatch(Character::isISOControl));
        assertEquals("line one line two", SFMExplorerControlBounds.boundedEvidenceText("line one\nline two"));
    }
}
