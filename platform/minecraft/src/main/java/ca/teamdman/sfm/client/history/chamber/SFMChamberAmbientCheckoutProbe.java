package ca.teamdman.sfm.client.history.chamber;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Byte-level measurement of the development checkout protected by a chamber.
 *
 * <p>The probe never shells out and never writes. It discovers the branch root
 * from Minecraft's working directory, hashes every non-generated regular file
 * in deterministic relative-path order, and is sampled before planning and
 * immediately before supervision publication.</p>
 */
public final class SFMChamberAmbientCheckoutProbe implements Supplier<String> {
    private static final int BUFFER_SIZE = 64 * 1024;
    private final Path branchRoot;

    private SFMChamberAmbientCheckoutProbe(Path branchRoot) {
        this.branchRoot = Objects.requireNonNull(branchRoot, "branchRoot")
                .toAbsolutePath()
                .normalize();
    }

    public static SFMChamberAmbientCheckoutProbe discover(Path startingPath) {
        Path start = Objects.requireNonNull(startingPath, "startingPath")
                .toAbsolutePath()
                .normalize();
        Optional<Path> root = Stream.iterate(start, Objects::nonNull, Path::getParent)
                .filter(SFMChamberAmbientCheckoutProbe::isBranchRoot)
                .findFirst();
        return new SFMChamberAmbientCheckoutProbe(root.orElseThrow(() ->
                new IllegalStateException("Could not discover the SFM branch root from " + start)));
    }

    public Path branchRoot() {
        return branchRoot;
    }

    public String scopeDescription() {
        return "non-generated-files-under:" + branchRoot.toUri();
    }

    @Override
    public String get() {
        MessageDigest digest = sha256();
        List<Path> files = protectedFiles();
        byte[] buffer = new byte[BUFFER_SIZE];
        for (Path file : files) {
            Path relative = branchRoot.relativize(file);
            update(digest, canonicalRelativePath(relative).getBytes(StandardCharsets.UTF_8));
            try (InputStream input = Files.newInputStream(file)) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            } catch (IOException failure) {
                throw new IllegalStateException("Could not hash protected checkout file " + file, failure);
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private List<Path> protectedFiles() {
        ArrayList<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(branchRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(branchRoot) && excluded(branchRoot.relativize(directory))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && !excluded(branchRoot.relativize(file))) files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            throw new IllegalStateException("Could not enumerate the protected checkout", failure);
        }
        files.sort(Comparator.comparing(path -> canonicalRelativePath(branchRoot.relativize(path))));
        return List.copyOf(files);
    }

    static boolean excluded(Path relative) {
        String value = canonicalRelativePath(relative).toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return false;
        for (String segment : value.split("/")) {
            if (segment.equals("target") || segment.equals("node_modules")) return true;
        }
        String first = firstSegment(value);
        if (List.of(".git", ".gradle", ".idea", ".vscode", "build", "config",
                "defaultconfigs", "logs", "mods").contains(first)) {
            return true;
        }
        if (value.endsWith(".log")) return true;
        if (value.startsWith("platform/minecraft/")) {
            String rest = value.substring("platform/minecraft/".length());
            String segment = firstSegment(rest);
            return segment.equals(".gradle")
                    || segment.equals(".idea")
                    || segment.equals(".vscode")
                    || segment.equals("build")
                    || segment.equals("logs")
                    || segment.startsWith("run");
        }
        return false;
    }

    private static boolean isBranchRoot(Path candidate) {
        return Files.isDirectory(candidate.resolve("platform/minecraft/src"))
                && Files.isRegularFile(candidate.resolve("platform/minecraft/sfm-toolchain.lock.json"))
                && Files.isDirectory(candidate.resolve("docs"));
    }

    private static String firstSegment(String value) {
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    private static String canonicalRelativePath(Path value) {
        return value.toString().replace('\\', '/');
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void update(MessageDigest digest, byte[] bytes) {
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
