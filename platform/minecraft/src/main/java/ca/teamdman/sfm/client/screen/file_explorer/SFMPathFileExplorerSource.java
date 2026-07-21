package ca.teamdman.sfm.client.screen.file_explorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bounded read-only adapter for one explicitly supplied directory.
 *
 * <p>It never follows links, never reads file contents, and excludes volatile
 * capture/world/log directories plus files that commonly contain account or
 * server history. This is a narrow developer-instance source, not an ambient
 * host filesystem browser.</p>
 */
public final class SFMPathFileExplorerSource implements SFMFileExplorerSource {
    public static final int MAX_DEPTH = 3;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_CHILDREN_PER_DIRECTORY = 64;

    private static final Set<String> EXCLUDED_NAMES = Set.of(
            "screenshots",
            "logs",
            "saves",
            "crash-reports",
            "downloads",
            "launcher_accounts.json",
            "usercache.json",
            "servers.dat",
            "realms_persistence.json"
    );
    private static final Comparator<Path> PATH_ORDER = Comparator
            .comparing((Path path) -> fileName(path).toLowerCase(Locale.ROOT))
            .thenComparing(SFMPathFileExplorerSource::fileName);

    private final Path root;
    private final String rootName;

    public SFMPathFileExplorerSource(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.rootName = fileName(this.root);
    }

    @Override
    public String displayName() {
        return "Minecraft instance / " + rootName;
    }

    @Override
    public SFMFileExplorerSnapshot snapshot() {
        try {
            BasicFileAttributes attributes = readAttributes(root);
            if (!attributes.isDirectory()) {
                return SFMFileExplorerSnapshot.error("Instance root is not a directory: " + rootName);
            }
            Counter counter = new Counter();
            counter.value = 1;
            SFMFileExplorerEntry entry = readEntry(root, ".", rootName, 0, counter);
            return SFMFileExplorerSnapshot.ready(List.of(entry));
        } catch (IOException exception) {
            return SFMFileExplorerSnapshot.error("Unable to read instance root: " + exception.getMessage());
        }
    }

    public Path root() {
        return root;
    }

    private SFMFileExplorerEntry readEntry(
            Path path,
            String logicalPath,
            String name,
            int depth,
            Counter counter
    ) throws IOException {
        BasicFileAttributes attributes = readAttributes(path);
        if (!attributes.isDirectory()) return SFMFileExplorerEntry.file(logicalPath, name);
        if (depth >= MAX_DEPTH || counter.value >= MAX_ENTRIES) {
            return SFMFileExplorerEntry.directory(logicalPath, name, List.of());
        }

        ArrayList<SFMFileExplorerEntry> children = new ArrayList<>();
        try (var paths = Files.list(path)) {
            for (Path child : paths.sorted(PATH_ORDER).limit(MAX_CHILDREN_PER_DIRECTORY).toList()) {
                if (counter.value >= MAX_ENTRIES) break;
                String childName = fileName(child);
                if (isExcluded(childName)) continue;
                BasicFileAttributes childAttributes;
                try {
                    childAttributes = readAttributes(child);
                } catch (IOException ignored) {
                    continue;
                }
                if (!childAttributes.isDirectory() && !childAttributes.isRegularFile()) continue;
                counter.value++;
                String childLogicalPath = logicalPath.equals(".") ? childName : logicalPath + "/" + childName;
                children.add(readEntry(child, childLogicalPath, childName, depth + 1, counter));
            }
        }
        return SFMFileExplorerEntry.directory(logicalPath, name, children);
    }

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean isExcluded(String name) {
        return EXCLUDED_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    private static String fileName(Path path) {
        Path name = path.getFileName();
        return name == null ? path.toString() : name.toString();
    }

    private static final class Counter {
        private int value;
    }
}
