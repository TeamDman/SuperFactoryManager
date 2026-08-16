package ca.teamdman.sfm.client.symbol;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable provider-neutral input for definition lookup at one editor location. */
public record SFMDefinitionRequest(
        String schema,
        long requestId,
        /** Provider-origin-scoped correlation generation; supersession is an explicit cancellation. */
        long requestGeneration,
        Workspace workspace,
        Document document,
        Position position
) {
    public static final String SCHEMA = "sfm.definition-at-position-request/2";
    private static final Set<String> SOURCE_ROOT_KINDS = Set.of("declared", "generated", "custom", "jdk");

    public enum ClasspathMode {
        BRANCH("branch"),
        ISOLATED("isolated");

        private final String wireName;

        ClasspathMode(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static ClasspathMode fromWireName(String value) {
            for (ClasspathMode mode : values()) {
                if (mode.wireName.equals(value)) return mode;
            }
            throw new IllegalArgumentException("Unknown Java classpath mode: " + value);
        }
    }

    public record SourceRoot(
            String id,
            String sourceSet,
            String path,
            String kind,
            boolean exists
    ) {
        public SourceRoot {
            requireNonBlank(id, "source-root id");
            requireNonBlank(sourceSet, "source-root source set");
            requireNonBlank(path, "source-root path");
            requireWireName(kind, "source-root kind", SOURCE_ROOT_KINDS);
        }
    }

    public record Workspace(
            String branch,
            ClasspathMode classpathMode,
            List<SourceRoot> sourceRoots,
            String classpathFingerprint,
            Optional<String> dependencyIndexIdentity,
            String workspaceFingerprint,
            long workspaceGeneration
    ) {
        public Workspace {
            requireNonBlank(branch, "workspace branch");
            Objects.requireNonNull(classpathMode, "classpathMode");
            sourceRoots = List.copyOf(sourceRoots);
            requireNonBlank(classpathFingerprint, "classpath fingerprint");
            dependencyIndexIdentity = Objects.requireNonNull(
                    dependencyIndexIdentity,
                    "dependencyIndexIdentity"
            );
            dependencyIndexIdentity.ifPresent(value -> requireNonBlank(value, "dependency-index identity"));
            requireHashShape(workspaceFingerprint, "workspace fingerprint");
            requireNonNegative(workspaceGeneration, "workspaceGeneration");
            HashSet<String> ids = new HashSet<>();
            for (SourceRoot root : sourceRoots) {
                if (!ids.add(root.id())) {
                    throw new IllegalArgumentException("Duplicate source-root id: " + root.id());
                }
            }
        }
    }

    public record Document(
            String address,
            String rootId,
            String rootRelativePath,
            String reportPath,
            String sourceSet,
            String text,
            String contentHash,
            Optional<String> diskContentHash
    ) {
        public Document {
            requireNonBlank(address, "document address");
            requireNonBlank(rootId, "document root id");
            requireCanonicalRelativePath(rootRelativePath, "document root-relative path");
            requireCanonicalRelativePath(reportPath, "document report path");
            requireNonBlank(sourceSet, "document source set");
            Objects.requireNonNull(text, "text");
            requireWellFormedUnicode(text, "document text");
            requireHashShape(contentHash, "contentHash");
            diskContentHash = Objects.requireNonNull(diskContentHash, "diskContentHash");
            diskContentHash.ifPresent(value -> requireHashShape(value, "diskContentHash"));
            if (contentHash.startsWith("sha256:")
                    && !contentHash.equals(SFMDefinitionRequest.sha256(text))) {
                throw new IllegalArgumentException("Document content hash does not match its UTF-8 text");
            }
        }

        public static Document sha256(
                String address,
                String rootId,
                String rootRelativePath,
                String reportPath,
                String sourceSet,
                String text,
                Optional<String> diskContentHash
        ) {
            return new Document(
                    address,
                    rootId,
                    rootRelativePath,
                    reportPath,
                    sourceSet,
                    text,
                    SFMDefinitionRequest.sha256(text),
                    diskContentHash
            );
        }
    }

    /** One-based Unicode-scalar line/column and the corresponding UTF-8 byte offset. */
    public record Position(long line, long column, long byteOffset) {
        public Position {
            requirePositive(line, "line");
            requirePositive(column, "column");
            requireNonNegative(byteOffset, "byteOffset");
        }

        public static Position fromText(String text, long line, long column) {
            Objects.requireNonNull(text, "text");
            return new Position(line, column, utf8ByteOffset(text, line, column));
        }

        public void validateAgainst(String text) {
            long expected = utf8ByteOffset(text, line, column);
            if (expected != byteOffset) {
                throw new IllegalArgumentException(
                        "Definition position resolves to UTF-8 byte " + expected
                                + ", not supplied byte " + byteOffset
                );
            }
        }
    }

    public SFMDefinitionRequest {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported definition request schema");
        requireNonNegative(requestId, "requestId");
        requireNonNegative(requestGeneration, "requestGeneration");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(position, "position");
        SourceRoot root = workspace.sourceRoots().stream()
                .filter(candidate -> candidate.id().equals(document.rootId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document references unknown source-root id: " + document.rootId()
                ));
        if (!root.exists()) throw new IllegalArgumentException("Document source root is unavailable: " + root.id());
        if (!root.sourceSet().equals(document.sourceSet())) {
            throw new IllegalArgumentException("Document source set disagrees with its source root");
        }
        position.validateAgainst(document.text());
    }

    public SFMDefinitionRequest(
            long requestId,
            long requestGeneration,
            Workspace workspace,
            Document document,
            Position position
    ) {
        this(SCHEMA, requestId, requestGeneration, workspace, document, position);
    }

    public SFMDefinitionRequest withIdentity(long requestId, long requestGeneration) {
        return new SFMDefinitionRequest(requestId, requestGeneration, workspace, document, position);
    }

    public static String sha256(String text) {
        Objects.requireNonNull(text, "text");
        requireWellFormedUnicode(text, "text");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static long utf8ByteOffset(String text, long requestedLine, long requestedColumn) {
        requirePositive(requestedLine, "line");
        requirePositive(requestedColumn, "column");
        long currentLine = 1;
        int lineStart = 0;
        for (int index = 0; currentLine < requestedLine && index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                currentLine++;
                lineStart = index + 1;
            }
        }
        if (currentLine != requestedLine) {
            throw new IllegalArgumentException("Definition line lies outside the document: " + requestedLine);
        }
        int physicalEnd = text.indexOf('\n', lineStart);
        if (physicalEnd < 0) physicalEnd = text.length();
        int logicalEnd = physicalEnd > lineStart && text.charAt(physicalEnd - 1) == '\r'
                ? physicalEnd - 1
                : physicalEnd;
        String lineText = text.substring(lineStart, logicalEnd);
        long scalarIndex = requestedColumn - 1;
        int scalarCount = lineText.codePointCount(0, lineText.length());
        if (scalarIndex > scalarCount) {
            throw new IllegalArgumentException(
                    "Definition column lies outside line " + requestedLine + ": " + requestedColumn
            );
        }
        int utf16Offset = scalarIndex == scalarCount
                ? lineText.length()
                : lineText.offsetByCodePoints(0, Math.toIntExact(scalarIndex));
        return text.substring(0, lineStart + utf16Offset).getBytes(StandardCharsets.UTF_8).length;
    }

    private static void requireCanonicalRelativePath(String value, String label) {
        requireNonBlank(value, label);
        if (value.indexOf('\\') >= 0 || value.startsWith("/")) {
            throw new IllegalArgumentException(label + " must use relative `/` separators");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException(label + " contains a non-canonical segment");
            }
        }
    }

    private static void requireHashShape(String value, String label) {
        requireNonBlank(value, label);
        int separator = value.indexOf(':');
        if (separator < 0) throw new IllegalArgumentException(label + " has no algorithm prefix");
        String algorithm = value.substring(0, separator);
        String digest = value.substring(separator + 1);
        if (!(algorithm.equals("sha256") || algorithm.equals("blake3")) || digest.length() != 64) {
            throw new IllegalArgumentException(label + " has an unsupported shape");
        }
        for (int index = 0; index < digest.length(); index++) {
            char valueAt = digest.charAt(index);
            if (!((valueAt >= '0' && valueAt <= '9') || (valueAt >= 'a' && valueAt <= 'f'))) {
                throw new IllegalArgumentException(label + " must use lowercase hexadecimal");
            }
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }

    private static void requireWireName(String value, String label, Set<String> allowed) {
        requireNonBlank(value, label);
        if (!allowed.contains(value)) throw new IllegalArgumentException("Unknown " + label + ": " + value);
    }

    private static void requireWellFormedUnicode(String value, String label) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException(label + " contains an unpaired UTF-16 surrogate");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains an unpaired UTF-16 surrogate");
            }
        }
    }

    private static void requirePositive(long value, String label) {
        if (value <= 0) throw new IllegalArgumentException(label + " must be positive");
    }

    private static void requireNonNegative(long value, String label) {
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }
}
