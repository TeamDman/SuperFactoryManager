package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
{% if features.theme_preview_rules and features.client_theme %}
import ca.teamdman.sfm.client.explorer.SFMPath;
{% endif %}
{% if features.theme_preview_rules and features.client_theme %}
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
{% endif %}
{% if features.theme_preview_rules and features.client_theme %}
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewCache;
{% endif %}
{% if features.theme_preview_rules and features.client_theme %}
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRegistry;
{% endif %}
{% if features.theme_preview_rules and features.client_theme %}
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewSubject;
{% endif %}
{% if features.theme_preview_rules and features.client_theme %}
import ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewRules;
{% endif %}

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
{% if features.client_theme or features.command_palette %}
            .register(
                    SFMMinecraftItemExplorerPresenter.ID,
                    SFMMinecraftItemExplorerPresenter.ORDER,
                    new SFMMinecraftItemExplorerPresenter()
            )
{% endif %}
{% if features.java_symbols and features.client_theme %}
            .register(
                    SFMSymbolReferenceExplorerPresenter.ID,
                    SFMSymbolReferenceExplorerPresenter.ORDER,
                    new SFMSymbolReferenceExplorerPresenter()
            )
{% endif %}
{% if features.release_review and features.client_theme or features.release_review and features.command_palette %}
            .register(
                    SFMReleaseReviewExplorerPresenter.ID,
                    SFMReleaseReviewExplorerPresenter.ORDER,
                    new SFMReleaseReviewExplorerPresenter()
            )
{% endif %}
{% if features.client_theme and features.theme_file_icon_matching %}
            .register(
                    SFMFileExtensionExplorerPresenter.ID,
                    SFMFileExtensionExplorerPresenter.ORDER,
                    new SFMFileExtensionExplorerPresenter()
            )
{% endif %}
{% if features.client_theme %}
            .register(
                    SFMFilePathExplorerPresenter.ID,
                    SFMFilePathExplorerPresenter.ORDER,
                    new SFMFilePathExplorerPresenter()
            )
{% endif %}
            .build();

    private final List<Registration> registrations;
{% if features.theme_preview_rules and features.client_theme %}
    private final SFMItemstackPreviewCache previewCache = new SFMItemstackPreviewCache();
{% endif %}

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
{% if features.theme_preview_rules and features.client_theme %}
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
{% endif %}
        return baseline;
    }

{% if features.theme_preview_rules and features.client_theme %}
    public SFMItemstackPreviewRules.Decision previewDecision(SFMExplorerProjection.Row row) {
        return previewCache.resolve(SFMClientThemeService.active(),SFMItemstackPreviewRegistry.snapshot(),SFMItemstackPreviewSubject.from(row.entry()));
    }

{% endif %}
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
