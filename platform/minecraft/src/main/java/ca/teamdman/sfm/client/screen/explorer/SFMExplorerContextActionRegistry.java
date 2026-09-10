package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
import ca.teamdman.sfm.client.action.SFMExplorerRowCopyDetailsAction;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Deterministic one-to-many context-action registry for generic explorer rows. */
public final class SFMExplorerContextActionRegistry {
    private static final ResourceLocation PATH_OPEN = new ResourceLocation("sfm", "path/open");
    private static final ResourceLocation REVIEW_OPEN_VIEW = new ResourceLocation(
            "sfm", SFMReleaseReviewAction.Kind.OPEN_VIEW.path());
    private static final ResourceLocation REVIEW_OPEN_READ_ONLY_VIEW = new ResourceLocation(
            "sfm", SFMReleaseReviewAction.Kind.OPEN_READ_ONLY_VIEW.path());
    private static final SFMExplorerContextActionRegistry MINECRAFT_DEFAULTS =
            new SFMExplorerContextActionRegistry(List.of(
                    new ExplorerRowDetailsProvider(),
                    new ReleaseReviewProjectionProvider(),
                    new ReviewFileProvider()
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
        if(request.iconInspection().isPresent()) {
            var inspection=request.iconInspection().orElseThrow();
            var capture=ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewCaptures.retain(request.actionContext(),inspection);
            answer.add(SFMExplorerRowCopyDetailsAction.captureChoice(inspection));
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
        for (SFMExplorerContextActionProvider provider : providers) {
            if(request.iconInspection().isPresent() && provider instanceof ExplorerRowDetailsProvider) continue;
            answer.addAll(Objects.requireNonNull(provider.choices(request), "provider choices"));
        }
        return List.copyOf(answer);
    }

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

    private static final class ReviewFileProvider implements SFMExplorerContextActionProvider {
        @Override
        public String id() {
            return "sfm:release_review_file";
        }

        @Override
        public List<SFMActionChoice> choices(Request request) {
            SFMPath path = request.path();
            if (request.entry().expandable()
                    || path.kind() != SFMPath.Kind.FILE
                    || !fileName(path).endsWith(".sfm-review.json")) return List.of();
            Path nativePath = path.toNativePath();
            String greedyPath = SFMReleaseReviewAction.greedyPathArgument(nativePath);
            return List.of(
                    SFMActionChoice.invoke(PATH_OPEN, path.canonical() + " focus", "Open review JSON as text"),
                    SFMActionChoice.invoke(REVIEW_OPEN_VIEW, greedyPath, "Open release review writable"),
                    SFMActionChoice.invoke(ca.teamdman.sfm.client.action.SFMReviewOfflineOpenAction.ID,
                            com.mojang.brigadier.arguments.StringArgumentType.escapeIfRequired(nativePath.toString()),
                            "Open retained review evidence (offline, v3)"),
                    SFMActionChoice.invoke(
                            REVIEW_OPEN_READ_ONLY_VIEW,
                            greedyPath,
                            "Open release review read-only"
                    )
            );
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
}
