package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerPanel;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

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
            return node.executes(context -> opener.open(context, reviewRecipe(screenTypeId)));
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
                                    "fixture-v1",
                                    Optional.empty()
                            ));
                        })));
    }

    private Recipe reviewRecipe(ResourceLocation screenTypeId) {
        Optional<String> machineId = SFMHistoryGraphRuntime.get().snapshotEvent().activeMachineId();
        String sourceDescriptor = machineId
                .map(id -> "review-session-v2:" + id)
                .orElse("fixture-v1");
        return new Recipe(screenTypeId, projection, "", "", sourceDescriptor, machineId);
    }

    public record Recipe(
            ResourceLocation sceneTypeId,
            Projection projection,
            String beforeSelector,
            String afterSelector,
            String sourceDescriptor,
            Optional<String> reviewMachineId
    ) implements SFMPanelReopenRecipe {
        /** Compatibility constructor retained for fixture-backed recipes and existing tests. */
        public Recipe(
                ResourceLocation sceneTypeId,
                Projection projection,
                String beforeSelector,
                String afterSelector,
                String sourceDescriptor
        ) {
            this(sceneTypeId, projection, beforeSelector, afterSelector, sourceDescriptor, Optional.empty());
        }

        public Recipe {
            Objects.requireNonNull(sceneTypeId);
            Objects.requireNonNull(projection);
            Objects.requireNonNull(beforeSelector);
            Objects.requireNonNull(afterSelector);
            Objects.requireNonNull(reviewMachineId, "reviewMachineId");
            reviewMachineId = reviewMachineId.map(machineId -> {
                if (machineId.isBlank()) throw new IllegalArgumentException("Review machine id must not be blank");
                return machineId;
            });
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
                        reviewModel(false));
                case HASHTAGS -> new SFMReviewExplorerPanel(
                        "Comments · Hashtags",
                        reviewModel(true));
            };
        }

        private SFMReviewExplorerModel reviewModel(boolean hashtags) {
            if (reviewMachineId.isEmpty()) {
                return hashtags ? SFMReviewExplorerModel.hashtags() : SFMReviewExplorerModel.comments();
            }
            SFMReviewSessionV2 session = SFMReviewSessionRuntime.get().session(reviewMachineId.orElseThrow());
            return hashtags
                    ? SFMReviewExplorerModel.hashtags(session)
                    : SFMReviewExplorerModel.comments(session);
        }
    }
}
