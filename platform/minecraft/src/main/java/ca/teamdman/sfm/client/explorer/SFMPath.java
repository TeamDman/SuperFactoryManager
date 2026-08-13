package ca.teamdman.sfm.client.explorer;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical UTF-8 content identity used by generic explorers.
 *
 * <p>This is deliberately separate from both {@link Path} and Minecraft
 * resource locations. Native paths exist only at resolver boundaries.</p>
 */
public record SFMPath(
        Kind kind,
        String scheme,
        String authority,
        List<String> segments,
        Optional<String> revision,
        boolean trailingSlash
) implements Comparable<SFMPath> {
    public enum Kind {
        FILE,
        REGISTRY,
        SELECTION,
        CONTRIBUTED
    }

    public SFMPath {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(segments, "segments");
        Objects.requireNonNull(revision, "revision");
        SFMCanonicalText.requireValidUnicode(scheme, "path.invalid-scheme");
        SFMCanonicalText.requireValidUnicode(authority, "path.invalid-authority");
        ArrayList<String> normalizedSegments = new ArrayList<>(segments);
        for (String segment : normalizedSegments) {
            Objects.requireNonNull(segment, "segment");
            SFMCanonicalText.requireValidUnicode(segment, "path.invalid-segment");
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new SFMParseException(
                        "path.invalid-segment",
                        "Path segments must be non-empty and normalized",
                        0
                );
            }
        }
        if (kind == Kind.FILE) {
            authority = authority.toLowerCase(Locale.ROOT);
            if (!normalizedSegments.isEmpty() && isDriveSegment(normalizedSegments.get(0))) {
                String drive = normalizedSegments.get(0);
                normalizedSegments.set(0, Character.toUpperCase(drive.charAt(0)) + ":");
            }
        }
        segments = List.copyOf(normalizedSegments);
        revision.ifPresent(value -> SFMCanonicalText.requireValidUnicode(value, "path.invalid-revision"));
        if (!scheme.matches("[a-z][a-z0-9+.-]*")) {
            throw new SFMParseException("path.invalid-scheme", "Path scheme is not canonical", 0);
        }
        boolean schemeMatchesKind = switch (kind) {
            case FILE -> scheme.equals("file");
            case REGISTRY -> scheme.equals("registry");
            case SELECTION -> scheme.equals("selection");
            case CONTRIBUTED -> !scheme.equals("file")
                    && !scheme.equals("registry")
                    && !scheme.equals("selection");
        };
        if (!schemeMatchesKind) {
            throw new SFMParseException(
                    "path.kind-scheme-mismatch",
                    "Path kind and resolver scheme do not agree",
                    0
            );
        }
        if (kind == Kind.FILE && revision.isPresent()) {
            throw new SFMParseException("path.invalid-revision", "File paths cannot carry revisions", 0);
        }
        if (kind == Kind.REGISTRY && (authority.isEmpty() || segments.isEmpty())) {
            throw new SFMParseException(
                    "path.invalid-registry",
                    "Registry paths require a namespace and registry segment",
                    0
            );
        }
        if (kind == Kind.REGISTRY && (!authority.matches("[a-z0-9_.-]+")
                || segments.stream().anyMatch(segment -> !segment.matches("[a-z0-9_.-]+")))) {
            throw new SFMParseException(
                    "path.invalid-registry",
                    "Registry paths use ResourceLocation-compatible lowercase segments",
                    0
            );
        }
        if (kind == Kind.SELECTION && (authority.isEmpty() || !segments.isEmpty() || trailingSlash)) {
            throw new SFMParseException(
                    "path.invalid-selection",
                    "Selection paths contain one id/name and an optional revision",
                    0
            );
        }
        if (kind != Kind.SELECTION && revision.isPresent()) {
            throw new SFMParseException(
                    "path.invalid-revision",
                    "Only selection paths may carry a revision",
                    0
            );
        }
        trailingSlash = switch (kind) {
            case FILE -> segments.isEmpty()
                    || segments.size() == 1 && (isDriveSegment(segments.get(0)) || !authority.isEmpty());
            case REGISTRY -> segments.size() == 1;
            case SELECTION -> false;
            case CONTRIBUTED -> trailingSlash;
        };
    }

    public static SFMPath parse(String text) {
        Objects.requireNonNull(text, "text");
        SFMCanonicalText.requireValidUnicode(text, "path.invalid-unicode");
        if (text.indexOf('|') >= 0) {
            throw new SFMParseException(
                    "path.pipe-aggregate-forbidden",
                    "Pipe-concatenated paths are not a path expression",
                    text.indexOf('|')
            );
        }
        if (text.indexOf('?') >= 0 || text.indexOf('#') >= 0) {
            int offset = text.indexOf('?') >= 0 ? text.indexOf('?') : text.indexOf('#');
            throw new SFMParseException(
                    "path.query-fragment-forbidden",
                    "Content paths do not contain query strings or fragments",
                    offset
            );
        }
        int schemeEnd = text.indexOf("://");
        if (schemeEnd <= 0) {
            throw new SFMParseException("path.missing-scheme", "Expected a resolver path scheme", 0);
        }
        String scheme = text.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        if (!text.substring(0, schemeEnd).equals(scheme)) {
            throw new SFMParseException("path.noncanonical-scheme", "Path scheme must be lowercase", 0);
        }
        if (!scheme.matches("[a-z][a-z0-9+.-]*")) {
            throw new SFMParseException("path.invalid-scheme", "Unsupported path scheme syntax", 0);
        }
        String remainder = text.substring(schemeEnd + 3);
        return switch (scheme) {
            case "file" -> parseFile(remainder, schemeEnd + 3);
            case "registry" -> parseRegistry(remainder, schemeEnd + 3);
            case "selection" -> parseSelection(remainder, schemeEnd + 3);
            default -> parseContributed(scheme, remainder, schemeEnd + 3);
        };
    }

    public static SFMPath fromNative(Path path) {
        Objects.requireNonNull(path, "path");
        Path normalized = path.toAbsolutePath().normalize();
        String nativeText = normalized.toString();
        SFMCanonicalText.requireValidUnicode(nativeText, "path.non-utf8-native-path");
        String slashPath = nativeText.replace('\\', '/');
        if (slashPath.startsWith("//")) {
            String withoutPrefix = slashPath.substring(2);
            int separator = withoutPrefix.indexOf('/');
            if (separator <= 0) {
                throw new SFMParseException("path.invalid-unc", "UNC path requires a share", 0);
            }
            String host = withoutPrefix.substring(0, separator).toLowerCase(Locale.ROOT);
            List<String> segments = splitDecodedSegments(withoutPrefix.substring(separator + 1));
            return new SFMPath(Kind.FILE, "file", host, segments, Optional.empty(), false);
        }
        if (slashPath.length() >= 2 && slashPath.charAt(1) == ':') {
            slashPath = Character.toUpperCase(slashPath.charAt(0)) + slashPath.substring(1);
        }
        while (slashPath.startsWith("/")) slashPath = slashPath.substring(1);
        List<String> segments = slashPath.isEmpty() ? List.of() : splitDecodedSegments(slashPath);
        boolean root = segments.size() == 1 && isDriveSegment(segments.get(0));
        return new SFMPath(Kind.FILE, "file", "", segments, Optional.empty(), root);
    }

    public String canonical() {
        if (kind == Kind.SELECTION) {
            return "selection://" + SFMCanonicalText.encodeComponent(authority)
                    + revision.map(value -> "@" + SFMCanonicalText.encodeComponent(value)).orElse("");
        }
        StringBuilder answer = new StringBuilder(scheme).append("://");
        if (!authority.isEmpty()) answer.append(SFMCanonicalText.encodeComponent(authority));
        if (kind != Kind.CONTRIBUTED || !segments.isEmpty() || trailingSlash) {
            answer.append('/');
        }
        for (int index = 0; index < segments.size(); index++) {
            if (index > 0) answer.append('/');
            String segment = segments.get(index);
            if (kind == Kind.FILE && index == 0 && isDriveSegment(segment)) {
                answer.append(Character.toUpperCase(segment.charAt(0))).append(':');
            } else {
                answer.append(SFMCanonicalText.encodeComponent(segment));
            }
        }
        if (trailingSlash && !answer.toString().endsWith("/")) answer.append('/');
        return answer.toString();
    }

    public Path toNativePath() {
        if (kind != Kind.FILE) {
            throw new IllegalStateException("Only file paths have a native representation");
        }
        if (!authority.isEmpty()) {
            validateNativeComponent(authority, -1);
            if (segments.isEmpty()) {
                throw new SFMParseException(
                        "path.invalid-unc",
                        "UNC file paths require a share component",
                        0
                );
            }
            validateNativeComponent(segments.get(0), 1);
            Path root = nativePath("\\\\" + authority + "\\" + segments.get(0) + "\\");
            return resolveWithinRoot(root, segments.subList(1, segments.size()), 1);
        }
        if (!segments.isEmpty() && isDriveSegment(segments.get(0))) {
            Path root = nativePath(segments.get(0) + "\\");
            return resolveWithinRoot(root, segments.subList(1, segments.size()), 1);
        }
        Path root = nativePath("/");
        return resolveWithinRoot(root, segments, 0);
    }

    public String extension() {
        if (segments.isEmpty()) return "";
        String name = segments.get(segments.size() - 1);
        int dot = name.lastIndexOf('.');
        return dot <= 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1);
    }

    @Override
    public int compareTo(SFMPath other) {
        return canonical().compareTo(other.canonical());
    }

    @Override
    public String toString() {
        return canonical();
    }

    private static SFMPath parseFile(String remainder, int sourceOffset) {
        ParsedAuthorityPath parsed = parseAuthorityPath(remainder, sourceOffset, true);
        ArrayList<String> segments = new ArrayList<>(parsed.segments());
        if (parsed.authority().isEmpty() && !segments.isEmpty() && isDriveSegment(segments.get(0))) {
            String drive = segments.get(0);
            segments.set(0, Character.toUpperCase(drive.charAt(0)) + ":");
        }
        return new SFMPath(
                Kind.FILE,
                "file",
                parsed.authority().toLowerCase(Locale.ROOT),
                segments,
                Optional.empty(),
                parsed.trailingSlash()
        );
    }

    private static SFMPath parseRegistry(String remainder, int sourceOffset) {
        ParsedAuthorityPath parsed = parseAuthorityPath(remainder, sourceOffset, false);
        if (!parsed.authority().matches("[a-z0-9_.-]+")) {
            throw new SFMParseException(
                    "path.invalid-registry-namespace",
                    "Registry namespace must use ResourceLocation-compatible lowercase characters",
                    sourceOffset
            );
        }
        return new SFMPath(
                Kind.REGISTRY,
                "registry",
                parsed.authority(),
                parsed.segments(),
                Optional.empty(),
                parsed.trailingSlash()
        );
    }

    private static SFMPath parseSelection(String remainder, int sourceOffset) {
        if (remainder.isEmpty() || remainder.indexOf('/') >= 0) {
            throw new SFMParseException(
                    "path.invalid-selection",
                    "Selection path requires one encoded id/name",
                    sourceOffset
            );
        }
        int revisionSeparator = remainder.indexOf('@');
        if (revisionSeparator >= 0 && remainder.indexOf('@', revisionSeparator + 1) >= 0) {
            throw new SFMParseException(
                    "path.invalid-selection-revision",
                    "Selection path contains more than one revision separator",
                    sourceOffset + revisionSeparator
            );
        }
        String encodedName = revisionSeparator < 0 ? remainder : remainder.substring(0, revisionSeparator);
        String encodedRevision = revisionSeparator < 0 ? null : remainder.substring(revisionSeparator + 1);
        if (encodedName.isEmpty() || encodedRevision != null && encodedRevision.isEmpty()) {
            throw new SFMParseException(
                    "path.invalid-selection",
                    "Selection id/name and revision must not be empty",
                    sourceOffset
            );
        }
        String name = SFMCanonicalText.decodeComponent(encodedName, sourceOffset);
        Optional<String> revision = encodedRevision == null
                ? Optional.empty()
                : Optional.of(SFMCanonicalText.decodeComponent(
                        encodedRevision,
                        sourceOffset + revisionSeparator + 1
                ));
        return new SFMPath(Kind.SELECTION, "selection", name, List.of(), revision, false);
    }

    private static SFMPath parseContributed(String scheme, String remainder, int sourceOffset) {
        ParsedAuthorityPath parsed = parseAuthorityPath(remainder, sourceOffset, false);
        return new SFMPath(
                Kind.CONTRIBUTED,
                scheme,
                parsed.authority(),
                parsed.segments(),
                Optional.empty(),
                parsed.trailingSlash()
        );
    }

    private static ParsedAuthorityPath parseAuthorityPath(
            String remainder,
            int sourceOffset,
            boolean allowEmptyAuthority
    ) {
        int slash = remainder.indexOf('/');
        String encodedAuthority;
        String encodedPath;
        if (slash < 0) {
            encodedAuthority = remainder;
            encodedPath = "";
        } else {
            encodedAuthority = remainder.substring(0, slash);
            encodedPath = remainder.substring(slash + 1);
        }
        if (!allowEmptyAuthority && encodedAuthority.isEmpty()) {
            throw new SFMParseException("path.missing-authority", "Path authority is required", sourceOffset);
        }
        String authority = SFMCanonicalText.decodeComponent(encodedAuthority, sourceOffset);
        boolean trailingSlash = !encodedPath.isEmpty() && encodedPath.endsWith("/")
                || encodedPath.isEmpty() && slash >= 0;
        if (trailingSlash && !encodedPath.isEmpty()) {
            encodedPath = encodedPath.substring(0, encodedPath.length() - 1);
        }
        if (encodedPath.contains("//")) {
            throw new SFMParseException(
                    "path.empty-segment",
                    "Canonical paths do not contain empty segments",
                    sourceOffset + slash + 1 + encodedPath.indexOf("//")
            );
        }
        ArrayList<String> segments = new ArrayList<>();
        if (!encodedPath.isEmpty()) {
            int segmentOffset = sourceOffset + slash + 1;
            for (String encodedSegment : encodedPath.split("/", -1)) {
                String segment;
                if (segments.isEmpty() && allowEmptyAuthority && encodedSegment.matches("[A-Za-z]:")) {
                    segment = Character.toUpperCase(encodedSegment.charAt(0)) + ":";
                } else {
                    segment = SFMCanonicalText.decodeComponent(encodedSegment, segmentOffset);
                }
                if (segment.equals(".") || segment.equals("..")) {
                    throw new SFMParseException(
                            "path.noncanonical-segment",
                            "Canonical paths must normalize dot segments",
                            segmentOffset
                    );
                }
                segments.add(segment);
                segmentOffset += encodedSegment.length() + 1;
            }
        }
        return new ParsedAuthorityPath(authority, List.copyOf(segments), trailingSlash);
    }

    private static List<String> splitDecodedSegments(String slashPath) {
        ArrayList<String> answer = new ArrayList<>();
        for (String segment : slashPath.split("/")) {
            if (!segment.isEmpty()) answer.add(segment);
        }
        return List.copyOf(answer);
    }

    private static boolean isDriveSegment(String segment) {
        return segment.length() == 2
                && isAsciiLetter(segment.charAt(0))
                && segment.charAt(1) == ':';
    }

    private static Path resolveWithinRoot(Path root, List<String> components, int sourceIndex) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path answer = normalizedRoot;
        for (int index = 0; index < components.size(); index++) {
            String component = components.get(index);
            validateNativeComponent(component, sourceIndex + index);
            answer = answer.resolve(component);
        }
        answer = answer.toAbsolutePath().normalize();
        if (!answer.startsWith(normalizedRoot)) {
            throw new SFMParseException(
                    "path.native-component-escape",
                    "Native path components escape the canonical file root",
                    0
            );
        }
        return answer;
    }

    private static void validateNativeComponent(String component, int index) {
        if (component.indexOf('/') >= 0 || component.indexOf('\\') >= 0) {
            throw new SFMParseException(
                    "path.native-embedded-separator",
                    "A canonical file component decodes to a native path separator",
                    0
            );
        }
        if (index != 0 && looksLikeDrivePrefix(component)) {
            throw new SFMParseException(
                    "path.native-drive-prefix",
                    "A drive prefix is only valid as the first local file component",
                    0
            );
        }
        try {
            Path parsed = Path.of(component);
            if (parsed.isAbsolute() || parsed.getRoot() != null || parsed.getNameCount() != 1) {
                throw new SFMParseException(
                        "path.native-component-escape",
                        "A canonical file component is not one relative native component",
                        0
                );
            }
        } catch (InvalidPathException exception) {
            throw new SFMParseException(
                    "path.invalid-native-component",
                    "A canonical file component is invalid on this platform: " + exception.getMessage(),
                    0
            );
        }
    }

    private static boolean looksLikeDrivePrefix(String component) {
        return component.length() >= 2
                && isAsciiLetter(component.charAt(0))
                && component.charAt(1) == ':';
    }

    private static boolean isAsciiLetter(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static Path nativePath(String value) {
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new SFMParseException(
                    "path.invalid-native-root",
                    "Canonical file root is invalid on this platform: " + exception.getMessage(),
                    0
            );
        }
    }

    private record ParsedAuthorityPath(String authority, List<String> segments, boolean trailingSlash) {
    }
}
