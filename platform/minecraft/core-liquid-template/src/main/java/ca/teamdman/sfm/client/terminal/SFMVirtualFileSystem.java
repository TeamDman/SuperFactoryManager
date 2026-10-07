package ca.teamdman.sfm.client.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Small bounded in-memory filesystem used by the Java-local terminal.
 *
 * <p>Paths are always absolute, slash-separated, and normalized.  The
 * bounded store is deliberate: the fallback terminal must never become an
 * unbounded way for a command or a remote backend to allocate memory.</p>
 */
public final class SFMVirtualFileSystem {
    public static final int DEFAULT_MAX_FILES = 256;
    public static final int DEFAULT_MAX_FILE_BYTES = 16 * 1024;
    public static final int DEFAULT_MAX_TOTAL_BYTES = 256 * 1024;

    private final int maxFiles;
    private final int maxFileBytes;
    private final int maxTotalBytes;
    private final Map<String, String> files = new LinkedHashMap<>();
    private int totalBytes;

    public SFMVirtualFileSystem() {
        this(DEFAULT_MAX_FILES, DEFAULT_MAX_FILE_BYTES, DEFAULT_MAX_TOTAL_BYTES);
    }

    public SFMVirtualFileSystem(int maxFiles, int maxFileBytes, int maxTotalBytes) {
        if (maxFiles < 1 || maxFileBytes < 1 || maxTotalBytes < maxFileBytes) {
            throw new IllegalArgumentException("Invalid virtual filesystem bounds");
        }
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.maxTotalBytes = maxTotalBytes;
    }

    public synchronized void put(String path, String content) {
        String normalized = normalizeFile(path);
        Objects.requireNonNull(content, "content");
        int bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (bytes > maxFileBytes) throw new IllegalArgumentException("File exceeds terminal file limit");
        String previous = files.get(normalized);
        int previousBytes = previous == null ? 0 : utf8Bytes(previous);
        if (previous == null && files.size() >= maxFiles) throw new IllegalStateException("Virtual filesystem file limit reached");
        if (totalBytes - previousBytes + bytes > maxTotalBytes) throw new IllegalStateException("Virtual filesystem byte limit reached");
        files.put(normalized, content);
        totalBytes = totalBytes - previousBytes + bytes;
    }

    public synchronized String read(String path) {
        return files.get(normalizeFile(path));
    }

    public synchronized boolean contains(String path) {
        return files.containsKey(normalizeFile(path));
    }

    /** Returns immediate child names beneath a directory in stable order. */
    public synchronized List<String> list(String directory) {
        String normalized = normalizeDirectory(directory);
        String prefix = normalized.equals("/") ? "/" : normalized + "/";
        List<String> children = new ArrayList<>();
        for (String path : files.keySet()) {
            if (!path.startsWith(prefix)) continue;
            String remainder = path.substring(prefix.length());
            int slash = remainder.indexOf('/');
            String child = slash < 0 ? remainder : remainder.substring(0, slash) + "/";
            if (!children.contains(child)) children.add(child);
        }
        Collections.sort(children);
        return List.copyOf(children);
    }

    public synchronized int fileCount() {
        return files.size();
    }

    public synchronized int totalBytes() {
        return totalBytes;
    }

    public static String normalizeDirectory(String path) {
        String normalized = normalize(path);
        return normalized.endsWith("/") && normalized.length() > 1
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    public static String normalizeFile(String path) {
        String normalized = normalize(path);
        if (normalized.equals("/")) throw new IllegalArgumentException("A directory is not a file");
        return normalized;
    }

    private static String normalize(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank() || !path.startsWith("/")) throw new IllegalArgumentException("Terminal paths must be absolute");
        StringBuilder result = new StringBuilder("/");
        for (String part : path.split("/+")) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) {
                int slash = result.lastIndexOf("/");
                if (slash > 0) result.delete(slash, result.length());
            } else {
                if (result.length() > 1) result.append('/');
                result.append(part);
            }
        }
        return result.toString();
    }

    private static int utf8Bytes(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
