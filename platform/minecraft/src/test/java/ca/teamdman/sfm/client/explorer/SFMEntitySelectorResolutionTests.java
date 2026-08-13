package ca.teamdman.sfm.client.explorer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SFMEntitySelectorResolutionTests {
    @Test
    public void explorerSetAlgebraIsDeterministicAndCapturesOneGeneration() {
        SFMSelectorRepositorySnapshot<SFMExplorerId> snapshot = new SFMSelectorRepositorySnapshot<>(
                41,
                List.of(
                        SFMSelectorRepositoryEntry.unnamed(new SFMExplorerId("explorer-c"), false),
                        SFMSelectorRepositoryEntry.unnamed(new SFMExplorerId("explorer-a"), true),
                        SFMSelectorRepositoryEntry.unnamed(new SFMExplorerId("explorer-b"), false)
                )
        );
        AtomicInteger captures = new AtomicInteger();
        SFMSelectorDomain<SFMExplorerId> domain = SFMSelectorDomains.explorers(() -> {
            captures.incrementAndGet();
            return snapshot;
        });

        SFMSelectorResolution<SFMExplorerId> resolution = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(
                        SFMEntitySelector.Domain.EXPLORER,
                        "difference(intersection(all,union(focused,id(explorer-c),id(explorer-b))),"
                                + "id(explorer-b))"
                ),
                domain
        );

        assertEquals(1, captures.get());
        assertEquals(41, resolution.repositoryGeneration());
        assertTrue(resolution.complete());
        assertEquals(
                List.of(new SFMExplorerId("explorer-a"), new SFMExplorerId("explorer-c")),
                resolution.identities()
        );
    }

    @Test
    public void exactMissIsACompleteEmptySetAndNeverFallsBackToFocus() {
        SFMSelectorDomain<SFMExplorerId> domain = SFMSelectorDomains.explorers(
                SFMSelectorRepository.immutable(new SFMSelectorRepositorySnapshot<>(
                        9,
                        List.of(SFMSelectorRepositoryEntry.unnamed(new SFMExplorerId("focused-one"), true))
                ))
        );

        SFMSelectorResolution<SFMExplorerId> resolution = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, "missing"),
                domain
        );

        assertTrue(resolution.complete());
        assertTrue(resolution.identities().isEmpty());
        assertTrue(resolution.hasDiagnostic("selector.exact-miss"));
    }

    @Test
    public void selectionNamesResolveZeroOrManyWithoutFirstMatch() {
        SFMSelectorDomain<SFMSelectionId> domain = SFMSelectorDomains.selections(
                SFMSelectorRepository.immutable(new SFMSelectorRepositorySnapshot<>(
                        12,
                        List.of(
                                SFMSelectorRepositoryEntry.named(new SFMSelectionId("selection-z"), "review"),
                                SFMSelectorRepositoryEntry.named(new SFMSelectionId("selection-a"), "review"),
                                SFMSelectorRepositoryEntry.named(new SFMSelectionId("selection-other"), "other")
                        )
                ))
        );

        SFMSelectorResolution<SFMSelectionId> many = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.SELECTION, "name(review)"),
                domain
        );
        assertEquals(
                List.of(new SFMSelectionId("selection-a"), new SFMSelectionId("selection-z")),
                many.identities()
        );
        assertTrue(many.hasDiagnostic("selector.name-multiple"));

        SFMSelectorResolution<SFMSelectionId> none = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.SELECTION, "name(missing)"),
                domain
        );
        assertTrue(none.identities().isEmpty());
        assertTrue(none.complete());
        assertTrue(none.hasDiagnostic("selector.name-miss"));
    }

    @Test
    public void snapshotsAndResultsAreImmutableCopies() {
        ArrayList<SFMSelectorRepositoryEntry<SFMExplorerId>> mutable = new ArrayList<>();
        mutable.add(SFMSelectorRepositoryEntry.unnamed(new SFMExplorerId("one"), false));
        SFMSelectorRepositorySnapshot<SFMExplorerId> snapshot = new SFMSelectorRepositorySnapshot<>(7, mutable);
        mutable.add(SFMSelectorRepositoryEntry.unnamed(new SFMExplorerId("two"), false));

        SFMSelectorResolution<SFMExplorerId> resolution = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "all"),
                SFMSelectorDomains.explorers(SFMSelectorRepository.immutable(snapshot))
        );

        assertEquals(List.of(new SFMExplorerId("one")), resolution.identities());
        assertThrows(
                UnsupportedOperationException.class,
                () -> resolution.identities().add(new SFMExplorerId("three"))
        );
    }

    @Test
    public void paneResolutionIsExplicitlyUnsupportedInsteadOfFabricatingIdentity() {
        SFMSelectorResolution<SFMPaneId> resolution = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.PANE, "all"),
                SFMSelectorDomains.unsupportedPanes(73)
        );

        assertEquals(73, resolution.repositoryGeneration());
        assertEquals(SFMSelectorResolution.Completeness.UNSUPPORTED, resolution.completeness());
        assertTrue(resolution.identities().isEmpty());
        assertTrue(resolution.hasDiagnostic("selector.pane-resolution-unsupported"));
    }

    @Test
    public void gameAndPanelEntryDomainsRetainTheirOwnTypedIdentities() {
        SFMGameInstanceId game = new SFMGameInstanceId("game-1");
        SFMSelectorResolution<SFMGameInstanceId> gameResolution = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.GAME, "focused"),
                SFMSelectorDomains.games(SFMSelectorRepository.immutable(
                        new SFMSelectorRepositorySnapshot<>(3, List.of(
                                SFMSelectorRepositoryEntry.unnamed(game, true)
                        ))
                ))
        );
        assertEquals(List.of(game), gameResolution.identities());

        SFMPanelEntryId panelEntry = new SFMPanelEntryId("panel-entry-4");
        SFMSelectorResolution<SFMPanelEntryId> panelResolution = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.PANEL_ENTRY, "all"),
                SFMSelectorDomains.panelEntries(SFMSelectorRepository.immutable(
                        new SFMSelectorRepositorySnapshot<>(7, List.of(
                                SFMSelectorRepositoryEntry.unnamed(panelEntry, false)
                        ))
                ))
        );
        assertEquals(List.of(panelEntry), panelResolution.identities());
    }

    @Test
    public void crossDomainResolutionFailsClosedWithCapturedGeneration() {
        SFMSelectorResolution<SFMSelectionId> resolution = SFMEntitySelectorResolver.resolve(
                SFMEntitySelector.parse(SFMEntitySelector.Domain.EXPLORER, "all"),
                SFMSelectorDomains.selections(SFMSelectorRepository.immutable(
                        new SFMSelectorRepositorySnapshot<>(22, List.of())
                ))
        );

        assertEquals(22, resolution.repositoryGeneration());
        assertEquals(SFMSelectorResolution.Completeness.INVALID, resolution.completeness());
        assertFalse(resolution.complete());
        assertTrue(resolution.identities().isEmpty());
        assertTrue(resolution.hasDiagnostic("selector.domain-mismatch"));
    }

    @Test
    public void duplicateTypedIdentityCannotEnterAnImmutableSnapshot() {
        SFMExplorerId id = new SFMExplorerId("same");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SFMSelectorRepositorySnapshot<>(
                        1,
                        List.of(
                                new SFMSelectorRepositoryEntry<>(id, Optional.empty(), false),
                                new SFMSelectorRepositoryEntry<>(id, Optional.empty(), true)
                        )
                )
        );
    }
}
