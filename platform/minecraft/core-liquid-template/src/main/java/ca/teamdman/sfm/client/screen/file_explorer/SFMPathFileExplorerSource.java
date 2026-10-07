package ca.teamdman.sfm.client.screen.file_explorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded read-only adapter for one explicitly supplied directory.
 *
 * <p>It never follows links, only reads explicitly requested bounded text, and excludes volatile
 * capture/world/log directories plus files that commonly contain account or
 * server history when encountered below a broader root. An explicitly dropped
 * root is user-authorized even when its own name is normally excluded from a
 * parent listing. Component and real-path checks make link traversal a best-effort
 * rejection on providers without {@code SecureDirectoryStream}; a hostile process
 * could still race path replacement between validation and opening. This is not an
 * ambient host filesystem browser.</p>
 */
public final class SFMPathFileExplorerSource implements SFMFileExplorerSource {
    public static final int MAX_DEPTH = 3;
    public static final int MAX_ENTRIES = 256;
    public static final int MAX_CHILDREN_PER_DIRECTORY = 64;
    public static final int MAX_TEXT_BYTES = 1024 * 1024;

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
            return SFMFileExplorerSnapshot.error("Unable to read this source root");
        }
    }

    public Path root() {
        return root;
    }

    @Override
    public SFMFileReadResult readText(String logicalPath) {
        try {
            Path logical = Path.of(logicalPath);
            if (logical.isAbsolute()) return SFMFileReadResult.error("Absolute paths are not allowed");
            Path resolved = root.resolve(logical).normalize();
            if (!resolved.startsWith(root)) return SFMFileReadResult.error("Path escapes the source root");
            Path relative = root.relativize(resolved);
            Path cursor = root;
            if (readAttributes(cursor).isSymbolicLink()) return SFMFileReadResult.error("Links are not followed");
            for (Path segment : relative) {
                if (isExcluded(segment.toString())) return SFMFileReadResult.error("Path is excluded");
                cursor = cursor.resolve(segment);
                if (readAttributes(cursor).isSymbolicLink()) return SFMFileReadResult.error("Links are not followed");
            }
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path realResolved = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realResolved.startsWith(realRoot)) return SFMFileReadResult.error("Resolved path escapes the source root");
            BasicFileAttributes attributes = readAttributes(resolved);
            if (attributes.isSymbolicLink()) return SFMFileReadResult.error("Links are not followed");
            if (!attributes.isRegularFile()) return SFMFileReadResult.error("Path is not a regular file");
            if (attributes.size() > MAX_TEXT_BYTES) {
                return SFMFileReadResult.error("File exceeds the read-only preview limit");
            }
            byte[] bytes;
            try (var input = Files.newInputStream(resolved, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_TEXT_BYTES + 1);
            }
            if (bytes.length > MAX_TEXT_BYTES) return SFMFileReadResult.error("File exceeds the read-only preview limit");
            try {
                String text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
                return SFMFileReadResult.ready(text);
            } catch (CharacterCodingException exception) {
                return SFMFileReadResult.error("File is not valid UTF-8 text");
            }
        } catch (IOException | RuntimeException exception) {
            return SFMFileReadResult.error("Unable to read this file safely");
        }
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
        for (Path child : boundedChildren(path)) {
                if (counter.value >= MAX_ENTRIES) break;
                String childName = fileName(child);
                counter.value++;
                String childLogicalPath = logicalPath.equals(".") ? childName : logicalPath + "/" + childName;
                children.add(readEntry(child, childLogicalPath, childName, depth + 1, counter));
        }
        return SFMFileExplorerEntry.directory(logicalPath, name, children);
    }

    private static List<Path> boundedChildren(Path directory) throws IOException {
        PriorityQueue<Path> retained = new PriorityQueue<>(MAX_CHILDREN_PER_DIRECTORY, PATH_ORDER.reversed());
        try (var paths = Files.newDirectoryStream(directory)) {
            for (Path child : paths) {
                if (isExcluded(fileName(child))) continue;
                BasicFileAttributes attributes;
                try {
                    attributes = readAttributes(child);
                } catch (IOException ignored) {
                    continue;
                }
                if (!attributes.isDirectory() && !attributes.isRegularFile()) continue;
                if (retained.size() < MAX_CHILDREN_PER_DIRECTORY) retained.add(child);
                else if (PATH_ORDER.compare(child, retained.peek()) < 0) {
                    retained.remove();
                    retained.add(child);
                }
            }
        }
        ArrayList<Path> answer = new ArrayList<>(retained);
        answer.sort(PATH_ORDER);
        return answer;
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
