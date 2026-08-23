package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** ItemStack-backed presentation for generic release-review resolver rows. */
public final class SFMReleaseReviewExplorerPresenter implements SFMExplorerPresenter {
    public static final String ID = "sfm:release_review";
    public static final int ORDER = 150;

    @Override
    public Optional<SFMExplorerPresentation> present(SFMExplorerProjection.Row row) {
        Objects.requireNonNull(row, "row");
        if (!row.path().scheme().equals(SFMReleaseReviewExplorerRuntime.PATH_SCHEME)) return Optional.empty();
        ResourceLocation item = row.entry().sortKey(SFMExplorerEntry.SORT_ICON)
                .value()
                .map(ResourceLocation::tryParse)
                .orElse(null);
        if (item == null) item = SFMItemIcon.PAPER;
        return Optional.of(new SFMExplorerPresentation(
                row.entry().label(),
                new SFMExplorerPresentation.ItemIcon(new SFMItemIcon(
                        item,
                        SFMItemIcon.PAPER,
                        row.entry().label()
                ))
        ));
    }
}
