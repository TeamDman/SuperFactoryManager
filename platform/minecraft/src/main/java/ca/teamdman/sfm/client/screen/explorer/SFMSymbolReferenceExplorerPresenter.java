package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.symbol.SFMSymbolReferenceResultRepository;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Theme-backed ItemStack presentation for repository-owned symbol-reference rows. */
public final class SFMSymbolReferenceExplorerPresenter implements SFMExplorerPresenter {
    public static final String ID = "sfm:symbol_reference";
    public static final int ORDER = SFMMinecraftItemExplorerPresenter.ORDER + 100;

    private final Supplier<SFMClientTheme> themeSupplier;

    public SFMSymbolReferenceExplorerPresenter() {
        this(SFMClientThemeService::active);
    }

    SFMSymbolReferenceExplorerPresenter(Supplier<SFMClientTheme> themeSupplier) {
        this.themeSupplier = Objects.requireNonNull(themeSupplier, "themeSupplier");
    }

    @Override
    public Optional<SFMExplorerPresentation> present(SFMExplorerProjection.Row row) {
        Objects.requireNonNull(row, "row");
        Optional<SFMSymbolReferenceResultRepository.PresentationMetadata> metadata =
                SFMSymbolReferenceResultRepository.presentationMetadata(row.entry());
        if (metadata.isEmpty()) return Optional.empty();

        SFMClientTheme theme = Objects.requireNonNull(themeSupplier.get(), "active theme");
        SFMItemIcon icon = switch (metadata.orElseThrow().kind()) {
            case HIERARCHY -> theme.fileIcon("directory");
            case JAVA_SOURCE -> theme.fileIcon(".java");
            case FILE_SOURCE, INFORMATION -> theme.fileIcon("unknown");
        };
        return Optional.of(new SFMExplorerPresentation(
                row.entry().label(),
                new SFMExplorerPresentation.ItemIcon(icon)
        ));
    }
}
