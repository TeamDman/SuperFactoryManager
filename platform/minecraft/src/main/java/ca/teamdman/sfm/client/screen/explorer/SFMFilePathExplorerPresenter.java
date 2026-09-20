package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Minecraft-default ItemStack presentation for filesystem-backed explorer rows. */
public final class SFMFilePathExplorerPresenter implements SFMExplorerPresenter {
    public static final String ID = "sfm:file_path";
    public static final int ORDER = 1_000;

    private final Supplier<SFMClientTheme> themeSupplier;

    public SFMFilePathExplorerPresenter() {
        this(SFMClientThemeService::active);
    }

    SFMFilePathExplorerPresenter(Supplier<SFMClientTheme> themeSupplier) {
        this.themeSupplier = Objects.requireNonNull(themeSupplier, "themeSupplier");
    }

    @Override
    public Optional<SFMExplorerPresentation> present(SFMExplorerProjection.Row row) {
        Objects.requireNonNull(row, "row");
        if (row.path().kind() != SFMPath.Kind.FILE) return Optional.empty();

        SFMClientTheme theme = Objects.requireNonNull(themeSupplier.get(), "active theme");
        String iconKey = row.entry().expandable() ? "directory" : "unknown";
        return Optional.of(new SFMExplorerPresentation(
                row.entry().label(),
                new SFMExplorerPresentation.ItemIcon(theme.fileIcon(iconKey))
        ));
    }
}
