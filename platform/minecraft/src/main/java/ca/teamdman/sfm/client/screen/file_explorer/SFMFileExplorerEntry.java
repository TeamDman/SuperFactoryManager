package ca.teamdman.sfm.client.screen.file_explorer;

import java.util.List;
import java.util.Objects;

/**
 * A read-only entry exposed by an {@link SFMFileExplorerSource}.
 *
 * <p>The path is a source-owned logical path, never an ambient host filesystem
 * path. Source adapters are responsible for enforcing their own boundaries.</p>
 */
public record SFMFileExplorerEntry(
        String path,
        String name,
        boolean directory,
        List<SFMFileExplorerEntry> children
) {
    public SFMFileExplorerEntry {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(children, "children");
        if (path.isBlank()) throw new IllegalArgumentException("Entry path must not be blank");
        if (name.isBlank()) throw new IllegalArgumentException("Entry name must not be blank");
        if (!directory && !children.isEmpty()) {
            throw new IllegalArgumentException("Files cannot contain children: " + path);
        }
        children = List.copyOf(children);
    }

    public static SFMFileExplorerEntry file(
            String path,
            String name
    ) {
        return new SFMFileExplorerEntry(path, name, false, List.of());
    }

    public static SFMFileExplorerEntry directory(
            String path,
            String name,
            List<SFMFileExplorerEntry> children
    ) {
        return new SFMFileExplorerEntry(path, name, true, children);
    }
}
