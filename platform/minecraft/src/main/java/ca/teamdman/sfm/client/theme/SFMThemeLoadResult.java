package ca.teamdman.sfm.client.theme;

import java.util.List;
import java.util.Optional;

public record SFMThemeLoadResult(Optional<SFMClientTheme> theme, List<String> diagnostics) {
    public SFMThemeLoadResult {
        theme = theme == null ? Optional.empty() : theme;
        diagnostics = List.copyOf(diagnostics);
    }

    public boolean valid() { return theme.isPresent() && diagnostics.isEmpty(); }
}
