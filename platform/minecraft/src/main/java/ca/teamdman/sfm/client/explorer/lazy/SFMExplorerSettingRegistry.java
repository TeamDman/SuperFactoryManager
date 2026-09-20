package ca.teamdman.sfm.client.explorer.lazy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Discoverable ids and descriptions for contributed explorer projection axes. */
public final class SFMExplorerSettingRegistry {
    public record Option<T>(String id, String label, String description, T value) {
        public Option {
            id = requireText(id, "id");
            label = requireText(label, "label");
            description = requireText(description, "description");
            Objects.requireNonNull(value, "value");
        }
    }

    private static final List<Option<SFMExplorerProjection.View>> VIEWS = List.of(
            new Option<>("sfm:list", "List", "Rows with ItemStack icons and labels",
                    SFMExplorerProjection.View.LIST),
            new Option<>("sfm:small_icons", "Small icons", "Compact ItemStack icon grid",
                    SFMExplorerProjection.View.SMALL_ICONS)
    );
    private static final List<Option<SFMExplorerProjection.PathDisplay>> PATH_DISPLAYS = List.of(
            new Option<>("sfm:name", "Name", "Display each entry's contributed name",
                    SFMExplorerProjection.PathDisplay.NAME),
            new Option<>("sfm:relative_path", "Relative path", "Display paths relative to an explorer root",
                    SFMExplorerProjection.PathDisplay.RELATIVE_PATH),
            new Option<>("sfm:absolute_path", "Absolute path", "Display the canonical absolute resolver path",
                    SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH)
    );

    private SFMExplorerSettingRegistry() {
    }

    public static List<Option<SFMExplorerProjection.View>> views() {
        return VIEWS;
    }

    public static List<Option<SFMExplorerProjection.PathDisplay>> pathDisplays() {
        return PATH_DISPLAYS;
    }

    public static SFMExplorerProjection.View requireView(String id) {
        return require(id, VIEWS, "view");
    }

    public static SFMExplorerProjection.PathDisplay requirePathDisplay(String id) {
        return require(id, PATH_DISPLAYS, "path display");
    }

    public static String id(SFMExplorerProjection.View value) {
        return id(value, VIEWS);
    }

    public static String id(SFMExplorerProjection.PathDisplay value) {
        return id(value, PATH_DISPLAYS);
    }

    private static <T> T require(String id, List<Option<T>> options, String kind) {
        String canonical = requireText(id, "id");
        return options.stream()
                .filter(option -> option.id().equals(canonical))
                .map(Option::value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown explorer " + kind + ": " + canonical));
    }

    private static <T> String id(T value, List<Option<T>> options) {
        Objects.requireNonNull(value, "value");
        return options.stream()
                .filter(option -> option.value().equals(value))
                .map(Option::id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unregistered explorer setting value: " + value));
    }

    /** Useful to tests and future contributors that need deterministic duplicate detection. */
    static <T> Map<String, Option<T>> indexed(List<Option<T>> options) {
        LinkedHashMap<String, Option<T>> result = new LinkedHashMap<>();
        for (Option<T> option : options) {
            if (result.putIfAbsent(option.id(), option) != null) {
                throw new IllegalArgumentException("Duplicate explorer setting id: " + option.id());
            }
        }
        return Map.copyOf(result);
    }

    private static String requireText(String value, String field) {
        String answer = Objects.requireNonNull(value, field).strip();
        if (answer.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return answer;
    }
}
