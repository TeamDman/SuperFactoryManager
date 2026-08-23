package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.action.SFMReleaseReviewAction;
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
        for (SFMExplorerContextActionProvider provider : providers) {
            answer.addAll(Objects.requireNonNull(provider.choices(request), "provider choices"));
        }
        return List.copyOf(answer);
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
