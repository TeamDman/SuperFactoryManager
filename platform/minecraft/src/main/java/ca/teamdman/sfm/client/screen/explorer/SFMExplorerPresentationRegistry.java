package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewCache;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRegistry;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewSubject;
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRules;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordered registry for explorer presentation contributors.
 *
 * <p>Lower order values win. Contributor ids break order ties, so resolution
 * does not depend on incidental registration order. A generic marker is always
 * available when no contributor accepts the row.</p>
 */
public final class SFMExplorerPresentationRegistry {
    public static final String GENERIC_FALLBACK_ID = "sfm:generic";

    public record Resolution(String contributorId, SFMExplorerPresentation presentation) {
        public Resolution {
            contributorId = requireId(contributorId);
            Objects.requireNonNull(presentation, "presentation");
        }
    }

    private record Registration(String id, int order, SFMExplorerPresenter presenter) {
        private Registration {
            id = requireId(id);
            Objects.requireNonNull(presenter, "presenter");
        }
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final HashSet<String> ids = new HashSet<>();

        public Builder register(String id, int order, SFMExplorerPresenter presenter) {
            String canonicalId = requireId(id);
            if (canonicalId.equals(GENERIC_FALLBACK_ID)) {
                throw new IllegalArgumentException("The generic fallback id is reserved");
            }
            if (!ids.add(canonicalId)) {
                throw new IllegalArgumentException("Duplicate explorer presenter id: " + canonicalId);
            }
            registrations.add(new Registration(canonicalId, order, presenter));
            return this;
        }

        public SFMExplorerPresentationRegistry build() {
            return new SFMExplorerPresentationRegistry(registrations);
        }
    }

    private static final Comparator<Registration> REGISTRATION_ORDER = Comparator
            .comparingInt(Registration::order)
            .thenComparing(Registration::id);
    private static final SFMExplorerPresentationRegistry MINECRAFT_DEFAULTS = builder()
            .register(
                    SFMMinecraftItemExplorerPresenter.ID,
                    SFMMinecraftItemExplorerPresenter.ORDER,
                    new SFMMinecraftItemExplorerPresenter()
            )
            .register(
                    SFMSymbolReferenceExplorerPresenter.ID,
                    SFMSymbolReferenceExplorerPresenter.ORDER,
                    new SFMSymbolReferenceExplorerPresenter()
            )
            .register(
                    SFMReleaseReviewExplorerPresenter.ID,
                    SFMReleaseReviewExplorerPresenter.ORDER,
                    new SFMReleaseReviewExplorerPresenter()
            )
            .register(
                    SFMFileExtensionExplorerPresenter.ID,
                    SFMFileExtensionExplorerPresenter.ORDER,
                    new SFMFileExtensionExplorerPresenter()
            )
            .register(
                    SFMFilePathExplorerPresenter.ID,
                    SFMFilePathExplorerPresenter.ORDER,
                    new SFMFilePathExplorerPresenter()
            )
            .build();

    private final List<Registration> registrations;
    private final SFMItemstackPreviewCache previewCache = new SFMItemstackPreviewCache();

    private SFMExplorerPresentationRegistry(List<Registration> registrations) {
        ArrayList<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort(REGISTRATION_ORDER);
        this.registrations = List.copyOf(ordered);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SFMExplorerPresentationRegistry minecraftDefaults() {
        return MINECRAFT_DEFAULTS;
    }

    public Resolution resolve(SFMExplorerProjection.Row row) {
        Resolution baseline=resolveBaseline(row);
        if (this!=MINECRAFT_DEFAULTS) return baseline;
        var decision=previewDecision(row);
        if (decision.winner().isPresent()) {
            var winner=decision.winner().orElseThrow();
            // Resolver-specific item/reference/review decorations remain their own defaults.
            if (winner.layer()!=SFMItemstackPreviewRules.Layer.DEFAULT || row.path().kind()==SFMPath.Kind.FILE)
                return new Resolution(baseline.contributorId(),new SFMExplorerPresentation(baseline.presentation().label(),
                        new SFMExplorerPresentation.ItemIcon(winner.icon())));
        }
        if (decision.status()==SFMItemstackPreviewRules.Status.AMBIGUOUS || decision.status()==SFMItemstackPreviewRules.Status.UNAVAILABLE)
            return new Resolution(baseline.contributorId(),new SFMExplorerPresentation(baseline.presentation().label(),
                    new SFMExplorerPresentation.DegradedIcon(baseline.presentation().icon())));
        return baseline;
    }

    public SFMItemstackPreviewRules.Decision previewDecision(SFMExplorerProjection.Row row) {
        return previewCache.resolve(SFMClientThemeService.active(),SFMItemstackPreviewRegistry.snapshot(),SFMItemstackPreviewSubject.from(row.entry()));
    }

    private Resolution resolveBaseline(SFMExplorerProjection.Row row) {
        Objects.requireNonNull(row, "row");
        for (Registration registration : registrations) {
            Optional<SFMExplorerPresentation> candidate = Objects.requireNonNull(
                    registration.presenter().present(row),
                    "presenter result"
            );
            if (candidate.isPresent()) {
                return new Resolution(registration.id(), candidate.orElseThrow());
            }
        }
        return new Resolution(
                GENERIC_FALLBACK_ID,
                new SFMExplorerPresentation(
                        row.entry().label(),
                        new SFMExplorerPresentation.MarkerIcon(row.entry().expandable() ? "[D]" : "[F]")
                )
        );
    }

    private static String requireId(String id) {
        String answer = Objects.requireNonNull(id, "id").strip();
        if (answer.isEmpty()) throw new IllegalArgumentException("Explorer presenter ids must not be blank");
        return answer;
    }
}
