package ca.teamdman.sfm.client.screen.explorer;

{% if features.release_review %}
import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
{% endif %}
{% if features.clipboard_action_commands %}
import ca.teamdman.sfm.client.action.SFMExplorerRowCopyDetailsAction;
{% endif %}
{% if features.clipboard_action_commands and features.theme_preview_rules and features.client_theme %}
import ca.teamdman.sfm.client.action.SFMExplorerRowCopySummaryAction;
{% endif %}
{% if features.release_review %}
import ca.teamdman.sfm.client.explorer.SFMPath;
{% endif %}
{% if features.release_review %}
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
{% endif %}
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import net.minecraft.resources.ResourceLocation;

{% if features.release_review %}
import java.nio.file.Path;
{% endif %}
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Deterministic one-to-many context-action registry for generic explorer rows. */
public final class SFMExplorerContextActionRegistry {
{% if features.release_review %}
    private static final ResourceLocation PATH_OPEN = new ResourceLocation("sfm", "path/open");
    private static final ResourceLocation REVIEW_OPEN = new ResourceLocation(
            "sfm", SFMReleaseReviewAction.Kind.OPEN.path());
    private static final ResourceLocation REVIEW_OPEN_READ_ONLY = new ResourceLocation(
            "sfm", SFMReleaseReviewAction.Kind.OPEN_READ_ONLY.path());
{% endif %}
    private static final SFMExplorerContextActionRegistry MINECRAFT_DEFAULTS =
            new SFMExplorerContextActionRegistry(List.of(
{% if features.clipboard_action_commands %}
{% if features.release_review %}
                    new ExplorerRowDetailsProvider(),
{% else %}
                    new ExplorerRowDetailsProvider()
{% endif %}
{% endif %}
{% if features.release_review %}
                    new ReleaseReviewProjectionProvider(),
                    new ReviewFileProvider()
{% endif %}
            ));

    private final List<SFMExplorerContextActionProvider> providers;

    public SFMExplorerContextActionRegistry(List<SFMExplorerContextActionProvider> providers) {
        ArrayList<SFMExplorerContextActionProvider> ordered = new ArrayList<>(
                Objects.requireNonNull(providers, "providers"));
        ordered.sort(Comparator.comparing(SFMExplorerContextActionProvider::id));
        HashSet<String> ids = new HashSet<>();
        for (SFMExplorerContextActionProvider provider : ordered) {
            Objects.requireNonNull(provider, "provider");
            if (!ids.add(provider.id())) {
                throw new IllegalArgumentException("Duplicate explorer context provider " + provider.id());
            }
        }
        this.providers = List.copyOf(ordered);
    }

    public static SFMExplorerContextActionRegistry minecraftDefaults() {
        return MINECRAFT_DEFAULTS;
    }

    public List<SFMActionChoice> resolve(SFMExplorerContextActionProvider.Request request) {
        Objects.requireNonNull(request, "request");
        ArrayList<SFMActionChoice> answer = new ArrayList<>();
{% if features.theme_preview_rules and features.client_theme %}
        if(request.iconInspection().isPresent()) {
            var inspection=request.iconInspection().orElseThrow();
            var capture=ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewCaptures.retain(request.actionContext(),inspection);
{% if features.clipboard_action_commands %}
            answer.add(SFMExplorerRowCopySummaryAction.captureChoice(inspection));
            answer.add(SFMExplorerRowCopyDetailsAction.captureChoice(inspection));
{% endif %}
            answer.add(ca.teamdman.sfm.client.action.SFMItemstackPreviewRulePromptAction.choice(capture.id()));
            if(request.target()!=SFMExplorerContextActionProvider.Target.ROW) {
                answer.addAll(ca.teamdman.sfm.client.action.SFMItemstackPreviewInspectionAction.choices(capture.id()));
                answer.addAll(ca.teamdman.sfm.client.action.SFMItemstackPreviewRuleToolsAction.choices(inspection));
                var action=new ca.teamdman.sfm.client.action.SFMItemstackPreviewRuleAction();
                for(var continuation:action.contextualContinuations(request.actionContext())) answer.add(SFMActionChoice.continuation(
                        ca.teamdman.sfm.client.action.SFMItemstackPreviewRuleAction.ID,continuation.arguments(),continuation.displayText()));
            }
            if(request.target()==SFMExplorerContextActionProvider.Target.ICON) return List.copyOf(answer);
        }
{% endif %}
        for (SFMExplorerContextActionProvider provider : providers) {
{% if features.theme_preview_rules and features.client_theme and features.clipboard_action_commands %}
            if(request.iconInspection().isPresent() && provider instanceof ExplorerRowDetailsProvider) continue;
{% endif %}
            answer.addAll(Objects.requireNonNull(provider.choices(request), "provider choices"));
        }
        return List.copyOf(answer);
    }

{% if features.clipboard_action_commands %}
    private static final class ExplorerRowDetailsProvider implements SFMExplorerContextActionProvider {
        @Override
        public String id() {
            return "sfm:explorer_row_details";
        }

        @Override
        public List<SFMActionChoice> choices(Request request) {
            return List.of(SFMExplorerRowCopyDetailsAction.captureChoice(request.inspection()));
        }
    }

{% endif %}
{% if features.release_review %}
    private static final class ReviewFileProvider implements SFMExplorerContextActionProvider {
        @Override
        public String id() {
            return "sfm:release_review_file";
        }

        @Override
        public List<SFMActionChoice> choices(Request request) {
            SFMPath path = request.path();
            if (path.kind() != SFMPath.Kind.FILE
                    || !fileName(path).endsWith(".sfm-review.json")) return List.of();
            Path nativePath = path.toNativePath();
            String greedyPath = SFMReleaseReviewAction.greedyPathArgument(nativePath);
            ArrayList<SFMActionChoice> choices = new ArrayList<>(List.of(
                    SFMActionChoice.invoke(PATH_OPEN, path.canonical() + " focus", "Open review JSON as text"),
                    SFMActionChoice.invoke(REVIEW_OPEN, greedyPath, "Enable commenting in this mounted review"),
                    SFMActionChoice.invoke(ca.teamdman.sfm.client.action.SFMReviewOfflineOpenAction.ID,
                            com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(nativePath.toString()),
                            "Open retained review evidence (offline, v3)"),
                    SFMActionChoice.invoke(
                            REVIEW_OPEN_READ_ONLY,
                            greedyPath,
                            "Reopen mounted review read-only"
                    )
            ));
            var review = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.get().snapshot();
            if (review.path().map(value -> value.toAbsolutePath().normalize().equals(
                    nativePath.toAbsolutePath().normalize())).orElse(false) && review.document().isPresent()) {
                choices.addAll(ca.teamdman.sfm.client.action.SFMReviewRemainingWorkAction.choices(
                        review.document().orElseThrow()));
                ca.teamdman.sfm.client.action.SFMReviewWorkQueueControls.CONTROLS.stream()
                        .map(ca.teamdman.sfm.client.action.SFMReviewWorkQueueControls.Control::choice)
                        .forEach(choices::add);
                choices.addAll(ca.teamdman.sfm.client.action.SFMReviewFreshnessAction.choices());
            }
            return List.copyOf(choices);
        }

        private static String fileName(SFMPath path) {
            if (path.segments().isEmpty()) return "";
            return path.segments().get(path.segments().size() - 1).toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final class ReleaseReviewProjectionProvider implements SFMExplorerContextActionProvider {
        @Override
        public String id() {
            return "sfm:release_review_projection";
        }

        @Override
        public List<SFMActionChoice> choices(Request request) {
            if (!request.path().scheme().equals(SFMReleaseReviewExplorerRuntime.PATH_SCHEME)) {
                return List.of();
            }
            return SFMReleaseReviewExplorerRuntime.get().contextChoices(request.path());
        }
    }
{% endif %}
}
