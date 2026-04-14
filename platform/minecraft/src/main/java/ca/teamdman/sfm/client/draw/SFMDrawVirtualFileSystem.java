package ca.teamdman.sfm.client.draw;

import ca.teamdman.sfm.client.screen.SfmDrawScreen;
import ca.teamdman.sfm.common.template.SFMDrawTemplate;
import ca.teamdman.sfm.common.template.SFMDrawTemplateRegistry;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SFMDrawVirtualFileSystem {
    public static final String USER_HOME_VIRTUAL_ROOT = "/user/home";
    public static final String USER_HOME_DIRECTORY_NAME = "sfm-home";
    public static final String EXAMPLES_DIRECTORY_NAME = "examples";
    public static final String CANVAS_FILE_EXTENSION = ".sfm-draw.json";
    public static final String TEMPLATE_FILE_EXTENSION = ".sfml";
    public static final String DEFAULT_CANVAS_NAME = "canvas";

    private SFMDrawVirtualFileSystem() {
    }

    public static SFMDrawVirtualPath defaultCanvasPath() {
        return defaultCanvasPath(Minecraft.getInstance().gameDirectory.toPath());
    }

    public static SFMDrawVirtualPath defaultCanvasPath(Path gameDirectory) {
        return resolveCanvasPath(DEFAULT_CANVAS_NAME, gameDirectory);
    }

    public static SFMDrawVirtualPath resolveVirtualPath(String requestedPath) {
        return resolveVirtualPath(requestedPath, Minecraft.getInstance().gameDirectory.toPath());
    }

    public static SFMDrawVirtualPath resolveVirtualPath(
            String requestedPath,
            Path gameDirectory
    ) {
        String normalizedInput = requestedPath == null ? "" : requestedPath.strip().replace('\\', '/');
        String virtualPath = normalizedInput.isEmpty()
                ? USER_HOME_VIRTUAL_ROOT
                : normalizedInput.startsWith("/")
                        ? normalizedInput
                        : USER_HOME_VIRTUAL_ROOT + "/" + normalizedInput;

        if (!virtualPath.equals(USER_HOME_VIRTUAL_ROOT) && !virtualPath.startsWith(USER_HOME_VIRTUAL_ROOT + "/")) {
            throw new IllegalArgumentException("only /user/home paths are currently writable");
        }

        List<String> segments = new ArrayList<>();
        for (String rawSegment : virtualPath.split("/")) {
            if (rawSegment.isBlank()) {
                continue;
            }
            if (rawSegment.equals(".") || rawSegment.equals("..")) {
                throw new IllegalArgumentException("draw paths cannot contain '.' or '..'");
            }
            segments.add(rawSegment);
        }

        if (segments.size() < 2) {
            throw new IllegalArgumentException("draw paths must resolve within /user/home");
        }

        Path mountedPath = userHomeRoot(gameDirectory);
        for (int index = 2; index < segments.size(); index++) {
            mountedPath = mountedPath.resolve(segments.get(index));
        }
        mountedPath = mountedPath.normalize();

        Path normalizedUserHomeRoot = userHomeRoot(gameDirectory).normalize();
        if (!mountedPath.startsWith(normalizedUserHomeRoot)) {
            throw new IllegalArgumentException("draw path escaped the mounted /user/home root");
        }

        String normalizedVirtualPath = "/" + String.join("/", segments);
        return new SFMDrawVirtualPath(normalizedVirtualPath, mountedPath);
    }

    public static SFMDrawVirtualPath resolveCanvasPath(String requestedPath) {
        return resolveCanvasPath(requestedPath, Minecraft.getInstance().gameDirectory.toPath());
    }

    public static SFMDrawVirtualPath resolveCanvasPath(
            String requestedPath,
            Path gameDirectory
    ) {
        String normalizedInput = requestedPath == null ? "" : requestedPath.strip();
        if (normalizedInput.isEmpty()) {
            throw new IllegalArgumentException("draw path must not be empty");
        }

        SFMDrawVirtualPath virtualPath = resolveVirtualPath(normalizedInput, gameDirectory);
        List<String> segments = new ArrayList<>(List.of(virtualPath.virtualPath().substring(1).split("/")));

        if (segments.size() < 3) {
            throw new IllegalArgumentException("draw paths must point inside /user/home");
        }

        String lastSegment = segments.get(segments.size() - 1);
        if (!lastSegment.contains(".")) {
            segments.set(segments.size() - 1, lastSegment + CANVAS_FILE_EXTENSION);
        }

        return resolveVirtualPath("/" + String.join("/", segments), gameDirectory);
    }

    public static Path userHomeRoot() {
        return userHomeRoot(Minecraft.getInstance().gameDirectory.toPath());
    }

    public static Path userHomeRoot(Path gameDirectory) {
        return gameDirectory.resolve(USER_HOME_DIRECTORY_NAME);
    }

    public static void ensureUserHomeReady() throws IOException {
        ensureUserHomeReady(Minecraft.getInstance().gameDirectory.toPath());
    }

    public static void ensureUserHomeReady(Path gameDirectory) throws IOException {
        Path userHomeRoot = userHomeRoot(gameDirectory);
        Files.createDirectories(userHomeRoot);
        mirrorBundledTemplates(userHomeRoot);
    }

    public static SFMDrawVirtualDirectoryListing listDirectory(String requestedPath) throws IOException {
        return listDirectory(requestedPath, Minecraft.getInstance().gameDirectory.toPath());
    }

    public static SFMDrawVirtualDirectoryListing listDirectory(
            String requestedPath,
            Path gameDirectory
    ) throws IOException {
        SFMDrawVirtualPath target = resolveVirtualPath(requestedPath, gameDirectory);
        if (!Files.exists(target.mountedPath())) {
            throw new IOException("path does not exist: " + target.virtualPath());
        }

        if (!Files.isDirectory(target.mountedPath())) {
            return new SFMDrawVirtualDirectoryListing(target, List.of(createDirectoryEntry(target.mountedPath(), gameDirectory)));
        }

        List<SFMDrawVirtualDirectoryEntry> entries = new ArrayList<>();
        try (var children = Files.list(target.mountedPath())) {
            children
                    .sorted(Comparator
                            .comparing((Path path) -> !Files.isDirectory(path))
                            .thenComparing(path -> displayNameForPath(path).toLowerCase(Locale.ROOT)))
                    .forEach(path -> entries.add(createDirectoryEntry(path, gameDirectory)));
        }
        return new SFMDrawVirtualDirectoryListing(target, entries);
    }

    public static void move(
            SFMDrawVirtualPath from,
            SFMDrawVirtualPath to
    ) throws IOException {
        if (from.virtualPath().equals(to.virtualPath())) {
            return;
        }
        if (!Files.exists(from.mountedPath())) {
            throw new IOException("source canvas does not exist: " + from.virtualPath());
        }
        if (Files.exists(to.mountedPath())) {
            throw new IOException("destination already exists: " + to.virtualPath());
        }

        Files.createDirectories(to.mountedPath().getParent());
        Files.move(from.mountedPath(), to.mountedPath());
    }

    private static void mirrorBundledTemplates(Path userHomeRoot) throws IOException {
        Path examplesRoot = userHomeRoot.resolve(EXAMPLES_DIRECTORY_NAME);
        Files.createDirectories(examplesRoot);

        for (SFMDrawTemplate template : SFMDrawTemplateRegistry.gatherAll()) {
            Path target = resolveRelativePath(examplesRoot, template.key() + TEMPLATE_FILE_EXTENSION);
            if (Files.exists(target)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.writeString(target, template.programString(), StandardCharsets.UTF_8);
        }
    }

    private static Path resolveRelativePath(
            Path root,
            String relativePath
    ) {
        Path current = root;
        for (String segment : relativePath.replace('\\', '/').split("/")) {
            if (segment.isBlank()) {
                continue;
            }
            current = current.resolve(segment);
        }
        return current.normalize();
    }

    private static SFMDrawVirtualDirectoryEntry createDirectoryEntry(
            Path mountedPath,
            Path gameDirectory
    ) {
        String virtualPath = virtualPathForMountedPath(mountedPath, gameDirectory);
        return new SFMDrawVirtualDirectoryEntry(
                displayNameForPath(mountedPath),
                virtualPath,
                Files.isDirectory(mountedPath)
        );
    }

    private static String virtualPathForMountedPath(
            Path mountedPath,
            Path gameDirectory
    ) {
        Path relativePath = userHomeRoot(gameDirectory).normalize().relativize(mountedPath.normalize());
        if (relativePath.getNameCount() == 0) {
            return USER_HOME_VIRTUAL_ROOT;
        }
        return USER_HOME_VIRTUAL_ROOT + "/" + relativePath.toString().replace('\\', '/');
    }

    private static String displayNameForPath(Path path) {
        String fileName = path.getFileName() == null ? USER_HOME_DIRECTORY_NAME : path.getFileName().toString();
        if (fileName.endsWith(CANVAS_FILE_EXTENSION)) {
            return fileName.substring(0, fileName.length() - CANVAS_FILE_EXTENSION.length());
        }
        return fileName;
    }
}