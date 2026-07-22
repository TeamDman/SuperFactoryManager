package ca.teamdman.sfm.client.screen.review.repository;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewRepository;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelGroup;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import net.minecraft.client.gui.screens.Screen;
public final class SFMRepositoryReviewWorkspace {
    private SFMRepositoryReviewWorkspace() {}
    public static Screen create(Screen previous, SFMRepositoryReviewRepository repository,
                                SFMRepositoryReviewRepository.OpenBundle opened) {
        SFMRepositoryReviewWorkspaceModel model = new SFMRepositoryReviewWorkspaceModel(repository, opened);
        SFMRepositoryReviewChangedFilesPanel files = new SFMRepositoryReviewChangedFilesPanel(model);
        SFMRepositoryReviewSourcePanel before = new SFMRepositoryReviewSourcePanel(
                model, SFMReviewCommentDataSource.Side.BEFORE);
        SFMRepositoryReviewSourcePanel after = new SFMRepositoryReviewSourcePanel(
                model, SFMReviewCommentDataSource.Side.AFTER);
        SFMRepositoryReviewCommentDetailsPanel comments = new SFMRepositoryReviewCommentDetailsPanel(model);
        SFMWorkspacePanelGroup group = new SFMWorkspacePanelGroup((bounds, maximized) ->
                SFMRepositoryReviewResponsivePolicy.layout(
                        bounds, model.activeBlade(), files, before, after, comments));
        model.attachGroup(group);
        return SFMScreenMultiplexer.create(previous, group);
    }
}
