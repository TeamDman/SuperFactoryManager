package ca.teamdman.sfm.client.context;

import ca.teamdman.sfm.client.screen.SFMActionChoice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Ordered, contributor-friendly registry for contextual action drafts. */
public final class SFMContextActionRegistry {
    public record Diagnostic(String providerId, String message) {
        public Diagnostic {
            providerId = requireId(providerId);
            message = Objects.requireNonNull(message, "message").strip();
            if (message.isEmpty()) throw new IllegalArgumentException("Diagnostic message must not be blank");
        }
    }

    public record Resolution(List<SFMActionChoice> choices, List<Diagnostic> diagnostics) {
        public Resolution {
            choices = List.copyOf(choices);
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private record Registration(String id, int order, SFMContextActionProvider provider) {
        private Registration {
            id = requireId(id);
            Objects.requireNonNull(provider, "provider");
        }
    }

    private record RankedChoice(int providerOrder, String providerId, int offerRank, SFMActionChoice choice) {
    }

    public static final class Builder {
        private final ArrayList<Registration> registrations = new ArrayList<>();
        private final HashSet<String> ids = new HashSet<>();

        public Builder register(String id, int order, SFMContextActionProvider provider) {
            String canonical = requireId(id);
            if (!ids.add(canonical)) {
                throw new IllegalArgumentException("Duplicate context action provider id: " + canonical);
            }
            registrations.add(new Registration(canonical, order, provider));
            return this;
        }

        public SFMContextActionRegistry build() {
            return new SFMContextActionRegistry(registrations);
        }
    }

    private static final Comparator<Registration> REGISTRATION_ORDER = Comparator
            .comparingInt(Registration::order)
            .thenComparing(Registration::id);
    private static final Comparator<RankedChoice> OFFER_ORDER = Comparator
            .comparingInt(RankedChoice::providerOrder)
            .thenComparing(RankedChoice::providerId)
            .thenComparingInt(RankedChoice::offerRank)
            .thenComparing(candidate -> candidate.choice().command());
    private static final SFMContextActionRegistry MINECRAFT_DEFAULTS = builder()
            .register(SFMReleaseReviewContextActionProvider.ID, 50,
                    new SFMReleaseReviewContextActionProvider())
            .register("sfm:review-evidence-export", 45,
                    ca.teamdman.sfm.client.action.SFMReviewEvidenceExportAction::contextOffers)
            .register(SFMJavaSymbolContextActionProvider.ID, 100, new SFMJavaSymbolContextActionProvider())
            .register("sfm:document-search", 110, new SFMTextEditorSearchContextActionProvider())
            .build();

    private final List<Registration> registrations;

    private SFMContextActionRegistry(List<Registration> registrations) {
        ArrayList<Registration> ordered = new ArrayList<>(registrations);
        ordered.sort(REGISTRATION_ORDER);
        this.registrations = List.copyOf(ordered);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SFMContextActionRegistry minecraftDefaults() {
        return MINECRAFT_DEFAULTS;
    }

    public Resolution resolve(SFMContextActionProvider.Request request) {
        Objects.requireNonNull(request, "request");
        ArrayList<RankedChoice> ranked = new ArrayList<>();
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();
        for (Registration registration : registrations) {
            try {
                List<SFMContextActionProvider.Offer> offers = Objects.requireNonNull(
                        registration.provider().offers(request), "provider offers");
                for (SFMContextActionProvider.Offer offer : offers) {
                    if (offer == null) throw new IllegalStateException("Provider returned a null offer");
                    ranked.add(new RankedChoice(
                            registration.order(), registration.id(), offer.rank(), offer.choice()));
                }
            } catch (RuntimeException failure) {
                String message = failure.getMessage();
                diagnostics.add(new Diagnostic(
                        registration.id(),
                        message == null || message.isBlank() ? failure.getClass().getSimpleName() : message
                ));
            }
        }
        ranked.sort(OFFER_ORDER);
        HashSet<String> commands = new HashSet<>();
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        for (RankedChoice candidate : ranked) {
            if (commands.add(candidate.choice().command())) choices.add(candidate.choice());
        }
        return new Resolution(choices, diagnostics);
    }

    private static String requireId(String id) {
        String answer = Objects.requireNonNull(id, "id").strip();
        if (answer.isEmpty()) throw new IllegalArgumentException("Provider id must not be blank");
        return answer;
    }
}
