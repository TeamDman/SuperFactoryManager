package ca.teamdman.sfm.client.explorer;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Constructors for the currently supported typed selector domains. */
public final class SFMSelectorDomains {
    private SFMSelectorDomains() {
    }

    public static SFMSelectorDomain<SFMGameInstanceId> games(
            SFMSelectorRepository<SFMGameInstanceId> repository
    ) {
        return supported(
                SFMEntitySelector.Domain.GAME,
                repository,
                SFMGameInstanceId::value,
                true,
                false
        );
    }

    public static SFMSelectorDomain<SFMPanelEntryId> panelEntries(
            SFMSelectorRepository<SFMPanelEntryId> repository
    ) {
        return supported(
                SFMEntitySelector.Domain.PANEL_ENTRY,
                repository,
                SFMPanelEntryId::value,
                true,
                false
        );
    }

    public static SFMSelectorDomain<SFMExplorerId> explorers(
            SFMSelectorRepository<SFMExplorerId> repository
    ) {
        return supported(
                SFMEntitySelector.Domain.EXPLORER,
                repository,
                SFMExplorerId::value,
                true,
                false
        );
    }

    public static SFMSelectorDomain<SFMSelectionId> selections(
            SFMSelectorRepository<SFMSelectionId> repository
    ) {
        return supported(
                SFMEntitySelector.Domain.SELECTION,
                repository,
                SFMSelectionId::value,
                false,
                true
        );
    }

    /**
     * Explicit seam retained until pane identity is introduced in the later
     * pane/panel-entry separation slice. It captures a generation but cannot
     * fabricate or first-match a pane id.
     */
    public static SFMSelectorDomain<SFMPaneId> unsupportedPanes(long repositoryGeneration) {
        SFMSelectorRepositorySnapshot<SFMPaneId> snapshot = new SFMSelectorRepositorySnapshot<>(
                repositoryGeneration,
                java.util.List.of()
        );
        return new ImmutableDomain<>(
                SFMEntitySelector.Domain.PANE,
                SFMSelectorRepository.immutable(snapshot),
                ignored -> "",
                false,
                false,
                false,
                Optional.of(new SFMSelectorResolution.Diagnostic(
                        "selector.pane-resolution-unsupported",
                        SFMSelectorResolution.Severity.ERROR,
                        "Pane selectors are reserved until stable pane identities are available"
                ))
        );
    }

    private static <I> SFMSelectorDomain<I> supported(
            SFMEntitySelector.Domain domain,
            SFMSelectorRepository<I> repository,
            Function<I, String> stableText,
            boolean supportsFocus,
            boolean supportsNames
    ) {
        return new ImmutableDomain<>(
                domain,
                repository,
                stableText,
                supportsFocus,
                supportsNames,
                true,
                Optional.empty()
        );
    }

    private record ImmutableDomain<I>(
            SFMEntitySelector.Domain domain,
            SFMSelectorRepository<I> repository,
            Function<I, String> stableTextFunction,
            boolean supportsFocus,
            boolean supportsNames,
            boolean supportsResolution,
            Optional<SFMSelectorResolution.Diagnostic> unsupportedDiagnostic
    ) implements SFMSelectorDomain<I> {
        private ImmutableDomain {
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(repository, "repository");
            Objects.requireNonNull(stableTextFunction, "stableTextFunction");
            Objects.requireNonNull(unsupportedDiagnostic, "unsupportedDiagnostic");
            if (supportsResolution == unsupportedDiagnostic.isPresent()) {
                throw new IllegalArgumentException(
                        "Supported domains must not have an unsupported diagnostic and vice versa"
                );
            }
        }

        @Override
        public String stableText(I id) {
            String value = Objects.requireNonNull(stableTextFunction.apply(id), "stable identity text");
            SFMCanonicalText.requireValidUnicode(value, "selector.invalid-repository-id");
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Stable identity text must not be empty");
            }
            return value;
        }
    }
}
