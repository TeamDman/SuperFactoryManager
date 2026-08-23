package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerPanel;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Ordinary panel surface over the currently open portable release-review document. */
public final class SFMReleaseReviewExplorerScreenType implements SFMClientScreenType {
    public enum Projection { CHANGES, COMMENTS, HASHTAGS, QUERY, STATUS, MIGRATIONS }

    private final Projection projection;

    public SFMReleaseReviewExplorerScreenType(Projection projection) {
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(ResourceLocation screenTypeId, Opener opener) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal(screenTypeId.toString());
        if (projection != Projection.QUERY) {
            return node.executes(context -> opener.open(context, new Recipe(screenTypeId, projection, Optional.empty())));
        }
        node.executes(context -> opener.open(context, new Recipe(screenTypeId, projection, Optional.empty())));
        return node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "query", StringArgumentType.greedyString())
                .executes(context -> opener.open(context, new Recipe(
                        screenTypeId,
                        projection,
                        Optional.of(StringArgumentType.getString(context, "query"))
                ))));
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            Projection projection,
            Optional<String> queryExpression
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
            Objects.requireNonNull(projection, "projection");
            Objects.requireNonNull(queryExpression, "queryExpression");
            queryExpression = queryExpression.map(String::trim).filter(value -> !value.isEmpty());
            if (projection != Projection.QUERY && queryExpression.isPresent()) {
                throw new IllegalArgumentException("Only a release-review query panel accepts a query expression");
            }
        }

        @Override
        public SFMScreenPanel reopen() {
            SFMReleaseReviewRuntime runtime = SFMReleaseReviewRuntime.get();
            return new SFMReviewExplorerPanel(
                    title(),
                    project(runtime.document()),
                    () -> runtime.document().orElse(null),
                    () -> project(runtime.document())
            );
        }

        private String title() {
            return switch (projection) {
                case CHANGES -> "Release review · Changes";
                case COMMENTS -> "Release review · Comments";
                case HASHTAGS -> "Release review · Hashtags";
                case QUERY -> "Release review · Work queue";
                case STATUS -> "Release review · Status witnesses";
                case MIGRATIONS -> "Release review · Migrations";
            };
        }

        private SFMReviewExplorerModel project(Optional<SFMReleaseReviewV1> open) {
            if (open.isEmpty()) {
                return SFMReviewExplorerModel.message(
                        "Release review unavailable",
                        "Open a .sfm-review.json file with sfm:review/session/open first"
                );
            }
            SFMReleaseReviewV1 review = open.orElseThrow();
            return switch (projection) {
                case CHANGES -> SFMReviewExplorerModel.releaseChanges(review);
                case COMMENTS -> SFMReviewExplorerModel.comments(review.reviewSession());
                case HASHTAGS -> SFMReviewExplorerModel.hashtags(review.reviewSession());
                case QUERY -> SFMReviewExplorerModel.releaseQuery(
                        review, queryExpression.orElseGet(() -> activeQuery(review)));
                case STATUS -> SFMReviewExplorerModel.releaseStatus(review);
                case MIGRATIONS -> SFMReviewExplorerModel.releaseMigrations(review);
            };
        }

        private static String activeQuery(SFMReleaseReviewV1 review) {
            return review.resumeState().activeQueryExpression().orElseGet(() ->
                    review.resumeState().activeQueryId()
                            .flatMap(id -> review.namedQueries().stream()
                                    .filter(query -> query.id().equals(id))
                                    .findFirst())
                            .map(SFMReleaseReviewV1.NamedQuery::expression)
                            .orElse("remaining"));
        }
    }
}
