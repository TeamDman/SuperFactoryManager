package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;

import java.util.Optional;

/** One ordered opportunity to provide a typed presentation for an explorer row. */
@FunctionalInterface
public interface SFMExplorerPresenter {
    Optional<SFMExplorerPresentation> present(SFMExplorerProjection.Row row);
}
