package ca.teamdman.sfm.client.screen.file_explorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class SFMFileExplorerDropPolicy {
    private SFMFileExplorerDropPolicy() {
    }

    public static SFMFileExplorerDropResult evaluate(@Nullable List<Path> paths) {
        if (paths == null || paths.size() != 1) {
            return SFMFileExplorerDropResult.rejected(
                    paths == null || paths.isEmpty()
                            ? "Drop rejected: no path supplied" : "Drop rejected: choose exactly one directory"
            );
        }
        if (paths.get(0) == null) return SFMFileExplorerDropResult.rejected("Drop rejected: invalid path");
        try {
            Path path = paths.get(0).toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS
            );
            if (attributes.isSymbolicLink()) return SFMFileExplorerDropResult.rejected("Drop rejected: links are not followed");
            if (!attributes.isDirectory()) return SFMFileExplorerDropResult.rejected("Drop rejected: path is not a directory");
            if (!Files.isReadable(path)) return SFMFileExplorerDropResult.rejected("Drop rejected: directory is not readable");
            return SFMFileExplorerDropResult.accepted(new SFMPathFileExplorerSource(path));
        } catch (IOException | RuntimeException exception) {
            return SFMFileExplorerDropResult.rejected("Drop rejected: directory is missing or inaccessible");
        }
    }
}
