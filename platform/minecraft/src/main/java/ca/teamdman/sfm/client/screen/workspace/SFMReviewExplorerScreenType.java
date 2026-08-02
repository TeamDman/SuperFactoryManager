package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerPanel;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
            return node.executes(context -> opener.open(context, panel()));
        }
        return node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "before", StringArgumentType.string())
                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                "after", StringArgumentType.string())
                        .executes(context -> opener.open(context, panel(context)))));
    }

    private SFMScreenPanel panel() {
        return switch (projection) {
            case COMMENTS -> new SFMReviewExplorerPanel("Comments", SFMReviewExplorerModel.comments());
            case HASHTAGS -> new SFMReviewExplorerPanel("Comments · Hashtags", SFMReviewExplorerModel.hashtags());
            case CHANGES -> throw new AssertionError("Change selectors are required");
        };
    }

    private SFMScreenPanel panel(CommandContext<SFMClientActionSource> context) {
        return new SFMReviewExplorerPanel("Changes",
                SFMReviewExplorerModel.changes(
                        StringArgumentType.getString(context, "before"),
                        StringArgumentType.getString(context, "after")));
    }
}
