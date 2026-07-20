package ca.teamdman.sfm.client.screen.file_explorer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Central extension-to-presentation mapping.
 *
 * <p>Matching is case-insensitive using {@link Locale#ROOT}. Registered suffixes
 * are checked longest-first, so compound extensions deterministically win over
 * their shorter tails.</p>
 */
public final class SFMFilePresentationRegistry {
    private static final SFMFilePresentation DIRECTORY = new SFMFilePresentation(
            "[DIR]", "directory", 0xFFFFC857, SFMFilePresentation.Emphasis.BOLD
    );
    private static final SFMFilePresentation UNKNOWN_EXTENSION = new SFMFilePresentation(
            "[?]", "unknown file", 0xFFB8B8B8, SFMFilePresentation.Emphasis.NORMAL
    );
    private static final SFMFilePresentation NO_EXTENSION = new SFMFilePresentation(
            "[TXT]", "file without extension", 0xFFD0D0D0, SFMFilePresentation.Emphasis.NORMAL
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

    public static SFMFilePresentationRegistry createDefault() {
        return new SFMFilePresentationRegistry()
                .register(".sfml", presentation("[SFM]", "SFM program", 0xFF72D572, SFMFilePresentation.Emphasis.BOLD))
                .register(".sfmp", presentation("[SFM]", "SFM program archive", 0xFF72D572, SFMFilePresentation.Emphasis.NORMAL))
                .register(".g4", presentation("[G4]", "ANTLR grammar", 0xFFE7A95B, SFMFilePresentation.Emphasis.BOLD))
                .register(".java", presentation("[J]", "Java source", 0xFFED8B3A, SFMFilePresentation.Emphasis.NORMAL))
                .register(".json", presentation("[{}]", "JSON document", 0xFFE6D85C, SFMFilePresentation.Emphasis.NORMAL))
                .register(".toml", presentation("[CFG]", "TOML configuration", 0xFF9CCFD8, SFMFilePresentation.Emphasis.NORMAL))
                .register(".properties", presentation("[CFG]", "properties configuration", 0xFF9CCFD8, SFMFilePresentation.Emphasis.NORMAL))
                .register(".md", presentation("[MD]", "Markdown document", 0xFF7EB6FF, SFMFilePresentation.Emphasis.NORMAL))
                .register(".txt", presentation("[TXT]", "text document", 0xFFD0D0D0, SFMFilePresentation.Emphasis.NORMAL))
                .register(".tar.gz", presentation("[ARC]", "compressed archive", 0xFFC792EA, SFMFilePresentation.Emphasis.NORMAL))
                .register(".gz", presentation("[GZ]", "gzip archive", 0xFFC792EA, SFMFilePresentation.Emphasis.NORMAL));
    }

    private static SFMFilePresentation presentation(
            String icon,
            String label,
            int colour,
            SFMFilePresentation.Emphasis emphasis
    ) {
        return new SFMFilePresentation(icon, label, colour, emphasis);
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
