package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
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
            return SFMReleaseReviewExplorerRuntime.get().openScene(projection, queryExpression);
        }
    }
}
