package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Theme-contributed, case-insensitive extension presentation before the paper fallback. */
public final class SFMFileExtensionExplorerPresenter implements SFMExplorerPresenter {
    public static final String ID = "sfm:file_extension";
    public static final int ORDER = SFMFilePathExplorerPresenter.ORDER - 100;

    private final Supplier<SFMClientTheme> themeSupplier;

    public SFMFileExtensionExplorerPresenter() {
        this(SFMClientThemeService::active);
    }

    SFMFileExtensionExplorerPresenter(Supplier<SFMClientTheme> themeSupplier) {
        this.themeSupplier = Objects.requireNonNull(themeSupplier, "themeSupplier");
    }

    @Override
    public Optional<SFMExplorerPresentation> present(SFMExplorerProjection.Row row) {
        Objects.requireNonNull(row, "row");
        if (row.path().kind() != SFMPath.Kind.FILE || row.entry().expandable()) return Optional.empty();
        SFMClientTheme theme = Objects.requireNonNull(themeSupplier.get(), "active theme");
        Optional<SFMItemIcon> icon = theme.matchingFileIcon(fileName(row.path()));
        return icon.map(value -> new SFMExplorerPresentation(
                row.entry().label(),
                new SFMExplorerPresentation.ItemIcon(value)
        ));
    }

    private static String fileName(SFMPath path) {
        if (path.segments().isEmpty()) return "";
        return path.segments().get(path.segments().size() - 1);
    }

}
