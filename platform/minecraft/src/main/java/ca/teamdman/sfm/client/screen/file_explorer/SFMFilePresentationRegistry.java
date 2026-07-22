package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Central extension-to-presentation mapping.
 *
 * <p>Matching is case-insensitive using {@link Locale#ROOT}. Registered suffixes
 * are checked longest-first, so compound extensions deterministically win over
 * their shorter tails.</p>
 */
public final class SFMFilePresentationRegistry {
    private static final Set<String> TEXT_SUFFIXES = Set.of(
            ".sfml", ".sfmp", ".g4", ".java", ".json", ".toml", ".properties", ".md", ".txt"
    );
    private static final SFMFilePresentation DIRECTORY = new SFMFilePresentation(
            icon("minecraft:chest", "directory"), "directory", 0xFFFFC857, SFMFilePresentation.Emphasis.BOLD
    );
    private static final SFMFilePresentation UNKNOWN_EXTENSION = new SFMFilePresentation(
            icon("minecraft:paper", "unknown file"), "unknown file", 0xFFB8B8B8, SFMFilePresentation.Emphasis.NORMAL
    );
    private static final SFMFilePresentation NO_EXTENSION = new SFMFilePresentation(
            icon("minecraft:name_tag", "file without extension"), "file without extension", 0xFFD0D0D0, SFMFilePresentation.Emphasis.NORMAL
    );

    private final Map<String, SFMFilePresentation> bySuffix = new LinkedHashMap<>();
    private List<String> orderedSuffixes = List.of();

    public SFMFilePresentationRegistry register(
            String suffix,
            SFMFilePresentation presentation
    ) {
        String normalized = normalizeSuffix(suffix);
        bySuffix.put(normalized, Objects.requireNonNull(presentation, "presentation"));
        ArrayList<String> suffixes = new ArrayList<>(bySuffix.keySet());
        suffixes.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        orderedSuffixes = List.copyOf(suffixes);
        return this;
    }

    public SFMFilePresentation presentationFor(SFMFileExplorerEntry entry) {
        if (entry.directory()) return DIRECTORY;
        String normalizedName = entry.name().toLowerCase(Locale.ROOT);
        for (String suffix : orderedSuffixes) {
            if (normalizedName.endsWith(suffix)) return bySuffix.get(suffix);
        }
        return normalizedName.contains(".") ? UNKNOWN_EXTENSION : NO_EXTENSION;
    }

    public SFMFilePresentation directoryPresentation() {
        return DIRECTORY;
    }

    public boolean isTextLike(SFMFileExplorerEntry entry) {
        if (entry.directory()) return false;
        String normalizedName = entry.name().toLowerCase(Locale.ROOT);
        if (!normalizedName.contains(".")) return true;
        return TEXT_SUFFIXES.stream().anyMatch(normalizedName::endsWith);
    }

    public static SFMFilePresentationRegistry createDefault() {
        return new SFMFilePresentationRegistry()
                .register(".sfml", presentation("sfm:disk", "SFM program", 0xFF72D572, SFMFilePresentation.Emphasis.BOLD))
                .register(".sfmp", presentation("minecraft:bundle", "SFM program archive", 0xFF72D572, SFMFilePresentation.Emphasis.NORMAL))
                .register(".g4", presentation("minecraft:knowledge_book", "ANTLR grammar", 0xFFE7A95B, SFMFilePresentation.Emphasis.BOLD))
                .register(".java", presentation("minecraft:book", "Java source", 0xFFED8B3A, SFMFilePresentation.Emphasis.NORMAL))
                .register(".json", presentation("minecraft:map", "JSON document", 0xFFE6D85C, SFMFilePresentation.Emphasis.NORMAL))
                .register(".toml", presentation("minecraft:comparator", "TOML configuration", 0xFF9CCFD8, SFMFilePresentation.Emphasis.NORMAL))
                .register(".properties", presentation("minecraft:repeater", "properties configuration", 0xFF9CCFD8, SFMFilePresentation.Emphasis.NORMAL))
                .register(".md", presentation("minecraft:writable_book", "Markdown document", 0xFF7EB6FF, SFMFilePresentation.Emphasis.NORMAL))
                .register(".txt", presentation("minecraft:paper", "text document", 0xFFD0D0D0, SFMFilePresentation.Emphasis.NORMAL))
                .register(".tar.gz", presentation("minecraft:ender_chest", "compressed archive", 0xFFC792EA, SFMFilePresentation.Emphasis.NORMAL))
                .register(".gz", presentation("minecraft:barrel", "gzip archive", 0xFFC792EA, SFMFilePresentation.Emphasis.NORMAL));
    }

    private static SFMFilePresentation presentation(
            String itemId,
            String label,
            int colour,
            SFMFilePresentation.Emphasis emphasis
    ) {
        return new SFMFilePresentation(icon(itemId, label), label, colour, emphasis);
    }

    private static SFMItemIcon icon(String itemId, String accessibleLabel) {
        return new SFMItemIcon(new ResourceLocation(itemId), SFMItemIcon.PAPER, accessibleLabel);
    }

    private static String normalizeSuffix(String suffix) {
        Objects.requireNonNull(suffix, "suffix");
        String normalized = suffix.strip().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".") || normalized.length() == 1) {
            throw new IllegalArgumentException("File suffix must begin with a dot: " + suffix);
        }
        return normalized;
    }
}
