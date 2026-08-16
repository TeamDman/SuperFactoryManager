package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
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
        Optional<SFMItemIcon> icon = matchingIcon(theme.fileIcons(), fileName(row.path()));
        return icon.map(value -> new SFMExplorerPresentation(
                row.entry().label(),
                new SFMExplorerPresentation.ItemIcon(value)
        ));
    }

    static Optional<SFMItemIcon> matchingIcon(Map<String, SFMItemIcon> icons, String fileName) {
        Objects.requireNonNull(icons, "icons");
        String lowerName = Objects.requireNonNull(fileName, "fileName").toLowerCase(Locale.ROOT);
        ArrayList<Map.Entry<String, SFMItemIcon>> extensions = new ArrayList<>();
        for (Map.Entry<String, SFMItemIcon> entry : icons.entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!key.startsWith(".") || key.length() == 1) continue;
            if (lowerName.endsWith(key) && lowerName.length() > key.length()) extensions.add(entry);
        }
        extensions.sort(Comparator
                .<Map.Entry<String, SFMItemIcon>>comparingInt(entry -> entry.getKey().length())
                .reversed()
                .thenComparing(Map.Entry::getKey));
        if (!extensions.isEmpty()) return Optional.of(extensions.get(0).getValue());
        if (isExtensionless(lowerName)) return Optional.ofNullable(icons.get("extensionless"));
        return Optional.empty();
    }

    private static String fileName(SFMPath path) {
        if (path.segments().isEmpty()) return "";
        return path.segments().get(path.segments().size() - 1);
    }

    private static boolean isExtensionless(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot <= 0 || dot == fileName.length() - 1;
    }
}
