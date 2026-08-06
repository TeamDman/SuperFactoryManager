package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerPanel;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Typed command-palette scenes for the composable review explorer projections. */
public final class SFMReviewExplorerScreenType implements SFMClientScreenType {
    public enum Projection { CHANGES, COMMENTS, HASHTAGS }

    private final Projection projection;

    public SFMReviewExplorerScreenType(Projection projection) {
        this.projection = projection;
    }

    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(ResourceLocation screenTypeId, Opener opener) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder.literal(screenTypeId.toString());
        if (projection != Projection.CHANGES) {
            return node.executes(context -> opener.open(context, new Recipe(
                    screenTypeId,
                    projection,
                    "",
                    "",
                    "fixture-v1"
            )));
        }
        return node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "before", StringArgumentType.string())
                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                "after", StringArgumentType.string())
                        .executes(context -> {
                            String before = StringArgumentType.getString(context, "before");
                            String after = StringArgumentType.getString(context, "after");
                            return opener.open(context, new Recipe(
                                    screenTypeId,
                                    projection,
                                    before,
                                    after,
                                    "fixture-v1"
                            ));
                        })));
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            Projection projection,
            String beforeSelector,
            String afterSelector,
            String sourceDescriptor
    ) implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId);
            Objects.requireNonNull(projection);
            Objects.requireNonNull(beforeSelector);
            Objects.requireNonNull(afterSelector);
            if (sourceDescriptor == null || sourceDescriptor.isBlank()) {
                throw new IllegalArgumentException("Review source descriptor must not be blank");
            }
            if (projection == Projection.CHANGES
                    && (beforeSelector.isBlank() || afterSelector.isBlank())) {
                throw new IllegalArgumentException("Change recipes require before and after selectors");
            }
        }

        @Override
        public SFMScreenPanel reopen() {
            return switch (projection) {
                case CHANGES -> new SFMReviewExplorerPanel(
                        "Changes",
                        SFMReviewExplorerModel.changes(beforeSelector, afterSelector));
                case COMMENTS -> new SFMReviewExplorerPanel(
                        "Comments",
                        SFMReviewExplorerModel.comments());
                case HASHTAGS -> new SFMReviewExplorerPanel(
                        "Comments · Hashtags",
                        SFMReviewExplorerModel.hashtags());
            };
        }
    }
}
