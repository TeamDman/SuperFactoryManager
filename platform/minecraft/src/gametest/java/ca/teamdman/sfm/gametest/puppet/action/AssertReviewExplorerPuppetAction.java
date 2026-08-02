package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerModel;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;

/** Live structural witness for the review explorer tree and tombstone policy. */
public record AssertReviewExplorerPuppetAction(Projection projection) implements SFMPuppetAction {
    public enum Projection { CHANGES, COMMENTS, HASHTAGS }

    @Override
    public String description() { return "assert review explorer " + projection; }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer multiplexer)) {
            throw new IllegalStateException("Expected review explorer multiplexer");
        }
        SFMReviewExplorerPanel panel = multiplexer.panels().stream()
                .filter(SFMReviewExplorerPanel.class::isInstance)
                .map(SFMReviewExplorerPanel.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Review explorer panel is not open"));
        SFMReviewExplorerModel model = panel.model();
        switch (projection) {
            case CHANGES -> assertChanges(model);
            case COMMENTS -> assertComments(model);
            case HASHTAGS -> assertHashtags(model);
        }
        return true;
    }

    private static void assertChanges(SFMReviewExplorerModel model) {
        if (model.root().children().size() != 3) {
            throw new IllegalStateException("Expected three stable file nodes, got " + model.root().children().size());
        }
        long leaves = model.visibleNodes().stream().filter(row -> row.node().leaf() != null).count();
        long tombstones = model.visibleNodes().stream().filter(row -> row.node().leaf() != null
                && row.node().leaf().missing()).count();
        if (leaves != 12 || tombstones != 4) {
            throw new IllegalStateException("Expected 12 revision leaves and 4 tombstones, got "
                    + leaves + " and " + tombstones);
        }
        if (model.root().children().stream().anyMatch(file -> file.children().stream()
                .anyMatch(lane -> lane.children().size() != 2))) {
            throw new IllegalStateException("Every file/lane node must retain before and after leaves");
        }
    }

    private static void assertComments(SFMReviewExplorerModel model) {
        if (model.root().children().isEmpty() || model.root().children().stream()
                .anyMatch(comment -> comment.kind() != SFMReviewExplorerModel.Kind.COMMENT
                        || comment.children().isEmpty())) {
            throw new IllegalStateException("Comments projection must contain comment-to-region children");
        }
    }

    private static void assertHashtags(SFMReviewExplorerModel model) {
        if (model.root().children().isEmpty() || model.root().children().stream()
                .anyMatch(tag -> tag.kind() != SFMReviewExplorerModel.Kind.HASHTAG
                        || tag.children().isEmpty())) {
            throw new IllegalStateException("Hashtag projection must contain hashtag-to-file children");
        }
    }
}
