package ca.teamdman.sfm.client.screen.review.repository;
import ca.teamdman.sfm.client.review.repository.SFMRepositoryReviewRepository;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.gui.screens.Screen;
public final class SFMRepositoryReviewWorkspace {
    private SFMRepositoryReviewWorkspace() {}
    public static Screen create(Screen previous, SFMRepositoryReviewRepository repository,
                                SFMRepositoryReviewRepository.OpenBundle opened) {
        return SFMScreenMultiplexer.create(previous, new SFMRepositoryReviewPanel(repository, opened));
    }
}
