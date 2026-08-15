package ca.teamdman.sfm.client.symbol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable provider-neutral output from definition lookup at one editor location. */
public record SFMDefinitionResult(
        String schema,
        long requestId,
        long requestGeneration,
        long workspaceGeneration,
        Outcome outcome,
        AnalysisContext context,
        DocumentIdentity document,
        SFMDefinitionRequest.Position position,
        List<SymbolIdentity> symbols,
        List<Definition> definitions,
        Completeness completeness,
        List<Diagnostic> diagnostics,
        List<RecoveryAction> recoveryActions,
        Optional<DependencyIndex> dependencyIndex
) {
    public static final String SCHEMA = "sfm.definition-at-position-result/3";
    private static final Set<String> SYMBOL_KINDS = Set.of(
            "class", "interface", "enum", "record", "annotation", "field", "method", "constructor"
    );
    private static final Set<String> RESOLUTION_CONFIDENCES = Set.of(
            "resolved", "partially-resolved", "unresolved"
    );
    private static final Set<String> DIAGNOSTIC_SEVERITIES = Set.of("info", "warning", "error");
    private static final Set<String> DEPENDENCY_INDEX_STATUSES = Set.of("ready", "missing", "stale", "partial");

    public enum Outcome {
        SUCCESS("success"),
        NO_SYMBOL("no-symbol"),
        NO_DEFINITION("no-definition"),
        AMBIGUOUS("ambiguous"),
        STALE_DOCUMENT("stale-document"),
        INVALID_REQUEST("invalid-request"),
        UNAVAILABLE("unavailable");

        private final String wireName;
        Outcome(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static Outcome fromWireName(String value) {
            for (Outcome outcome : values()) if (outcome.wireName.equals(value)) return outcome;
            throw new IllegalArgumentException("Unknown definition outcome: " + value);
        }
    }

    public enum Completeness {
        COMPLETE("complete"), INCOMPLETE("incomplete");
        private final String wireName;
        Completeness(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static Completeness fromWireName(String value) {
            for (Completeness candidate : values()) if (candidate.wireName.equals(value)) return candidate;
            throw new IllegalArgumentException("Unknown definition completeness: " + value);
        }
    }

    public record AnalysisContext(
            String branch,
            String minecraftVersion,
            String javaRelease,
            String jdk,
            List<SFMDefinitionRequest.SourceRoot> sourceRoots,
            List<SourceSet> sourceSets,
            List<SourceExclusion> sourceExclusions,
            SFMDefinitionRequest.ClasspathMode classpathMode,
            String classpathFingerprint,
            String parserFingerprint,
            String indexFingerprint
    ) {
        public AnalysisContext {
            requireNonBlank(branch, "analysis branch");
            requireNonBlank(minecraftVersion, "Minecraft version");
            requireNonBlank(javaRelease, "Java release");
            requireNonBlank(jdk, "JDK");
            sourceRoots = List.copyOf(sourceRoots);
            sourceSets = List.copyOf(sourceSets);
            sourceExclusions = List.copyOf(sourceExclusions);
            Objects.requireNonNull(classpathMode, "classpathMode");
            requireNonBlank(classpathFingerprint, "classpath fingerprint");
            requireNonBlank(parserFingerprint, "parser fingerprint");
            requireNonBlank(indexFingerprint, "index fingerprint");
        }
    }

    public record SourceSet(String id, List<String> visibleSourceSets) {
        public SourceSet {
            requireNonBlank(id, "source-set id");
            visibleSourceSets = List.copyOf(visibleSourceSets);
        }
    }

    public record SourceExclusion(String sourceSet, String path, String origin) {
        public SourceExclusion {
            requireNonBlank(sourceSet, "excluded source set");
            requireNonBlank(path, "excluded path");
            requireNonBlank(origin, "exclusion origin");
        }
    }

    public record DocumentIdentity(
            String address,
            String rootId,
            String rootRelativePath,
            String reportPath,
            String sourceSet,
            String contentHash,
            Optional<String> diskContentHash
    ) {
        public DocumentIdentity {
            requireNonBlank(address, "document address");
            requireNonBlank(rootId, "document root id");
            requireNonBlank(rootRelativePath, "document root-relative path");
            requireNonBlank(reportPath, "document report path");
            requireNonBlank(sourceSet, "document source set");
            requireNonBlank(contentHash, "document content hash");
            diskContentHash = Objects.requireNonNull(diskContentHash, "diskContentHash");
        }
    }

    public record SymbolIdentity(
            String kind,
            String owner,
            String name,
            Optional<String> descriptor,
            String qualifiedName
    ) {
        public SymbolIdentity {
            requireWireName(kind, "symbol kind", SYMBOL_KINDS);
            requireNonBlank(owner, "symbol owner");
            requireNonBlank(name, "symbol name");
            descriptor = Objects.requireNonNull(descriptor, "descriptor");
            requireNonBlank(qualifiedName, "qualified symbol name");
        }
    }

    public record SourceSpan(
            String path,
            String sourceSet,
            String sourceHash,
            long startByte,
            long endByte,
            long startLine,
            long startColumn,
            long endLine,
            long endColumn
    ) {
        public SourceSpan {
            requireNonBlank(path, "source span path");
            requireNonBlank(sourceSet, "source span source set");
            requireNonBlank(sourceHash, "source span hash");
            if (startByte < 0 || endByte < startByte) throw new IllegalArgumentException("Invalid byte span");
            if (startLine <= 0 || startColumn <= 0 || endLine <= 0 || endColumn <= 0) {
                throw new IllegalArgumentException("Source span line and column values are one-based");
            }
        }
    }

    /** Resolver-addressable span used by /2 definition locations. */
    public record DefinitionSourceSpan(
            String address,
            String resolverId,
            String rootId,
            String rootRelativePath,
            String reportPath,
            String sourceSet,
            String sourceHash,
            Optional<String> sourceSha256,
            long startByte,
            long endByte,
            long startLine,
            long startColumn,
            long endLine,
            long endColumn
    ) {
        public DefinitionSourceSpan {
            requireNonBlank(address, "definition span address");
            requireNonBlank(resolverId, "definition span resolver id");
            requireNonBlank(rootId, "definition span root id");
            requireNonBlank(rootRelativePath, "definition span root-relative path");
            requireNonBlank(reportPath, "definition span report path");
            requireNonBlank(sourceSet, "definition span source set");
            requireTaggedHash(sourceHash, "definition span hash", Set.of("blake3", "sha256"));
            sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
            sourceSha256.ifPresent(value ->
                    requireTaggedHash(value, "definition span SHA-256 witness", Set.of("sha256")));
            if (startByte < 0 || endByte < startByte) throw new IllegalArgumentException("Invalid byte span");
            if (startLine <= 0 || startColumn <= 0 || endLine <= 0 || endColumn <= 0) {
                throw new IllegalArgumentException("Definition span line and column values are one-based");
            }
        }
    }

    public record Definition(
            SymbolIdentity symbol,
            DefinitionSourceSpan identifierSpan,
            DefinitionSourceSpan declarationSpan,
            String confidence
    ) {
        public Definition {
            Objects.requireNonNull(symbol, "symbol");
            Objects.requireNonNull(identifierSpan, "identifierSpan");
            Objects.requireNonNull(declarationSpan, "declarationSpan");
            requireWireName(confidence, "definition confidence", RESOLUTION_CONFIDENCES);
        }
    }

    public record Diagnostic(
            String code,
            String severity,
            String message,
            Optional<SourceSpan> span
    ) {
        public Diagnostic {
            requireNonBlank(code, "diagnostic code");
            requireWireName(severity, "diagnostic severity", DIAGNOSTIC_SEVERITIES);
            Objects.requireNonNull(message, "message");
            span = Objects.requireNonNull(span, "span");
        }
    }

    public enum RecoveryActionKind {
        RETRY("retry"),
        ACQUIRE_DEPENDENCY_SOURCES("acquire-dependency-sources"),
        REFRESH_DEPENDENCY_INDEX("refresh-dependency-index"),
        RESTART_WORKER("restart-worker");

        private final String wireName;
        RecoveryActionKind(String wireName) { this.wireName = wireName; }
        public String wireName() { return wireName; }
        public static RecoveryActionKind fromWireName(String value) {
            for (RecoveryActionKind kind : values()) if (kind.wireName.equals(value)) return kind;
            throw new IllegalArgumentException("Unknown definition recovery action: " + value);
        }
    }

    public record RecoveryAction(RecoveryActionKind kind, String label, Optional<String> command) {
        public RecoveryAction {
            Objects.requireNonNull(kind, "kind");
            requireNonBlank(label, "recovery action label");
            command = Objects.requireNonNull(command, "command");
            command.ifPresent(value -> requireNonBlank(value, "recovery action command"));
        }
    }

    public record DependencyIndex(
            String status,
            Completeness completeness,
            String expectedIdentity,
            String portablePath,
            String path,
            String reason,
            String refreshCommand,
            List<String> acquisitionCommands
    ) {
        public DependencyIndex {
            requireWireName(status, "dependency-index status", DEPENDENCY_INDEX_STATUSES);
            Objects.requireNonNull(completeness, "completeness");
            requireNonBlank(expectedIdentity, "expected dependency-index identity");
            Objects.requireNonNull(portablePath, "portablePath");
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(refreshCommand, "refreshCommand");
            acquisitionCommands = List.copyOf(acquisitionCommands);
        }
    }

    public SFMDefinitionResult {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported definition result schema");
        if (requestId < 0 || requestGeneration < 0 || workspaceGeneration < 0) {
            throw new IllegalArgumentException("Definition result identities must not be negative");
        }
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(position, "position");
        symbols = List.copyOf(symbols);
        definitions = List.copyOf(definitions);
        Objects.requireNonNull(completeness, "completeness");
        diagnostics = List.copyOf(diagnostics);
        recoveryActions = List.copyOf(recoveryActions);
        dependencyIndex = Objects.requireNonNull(dependencyIndex, "dependencyIndex");
    }

    public boolean matches(SFMDefinitionRequest request) {
        Objects.requireNonNull(request, "request");
        return requestId == request.requestId()
                && requestGeneration == request.requestGeneration()
                && workspaceGeneration == request.workspace().workspaceGeneration()
                && context.branch().equals(request.workspace().branch())
                && context.classpathMode() == request.workspace().classpathMode()
                && context.sourceRoots().equals(request.workspace().sourceRoots())
                && context.classpathFingerprint().equals(request.workspace().classpathFingerprint())
                && document.equals(new DocumentIdentity(
                        request.document().address(),
                        request.document().rootId(),
                        request.document().rootRelativePath(),
                        request.document().reportPath(),
                        request.document().sourceSet(),
                        request.document().contentHash(),
                        request.document().diskContentHash()
                ))
                && position.equals(request.position());
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
    }

    private static void requireTaggedHash(String value, String label, Set<String> algorithms) {
        requireNonBlank(value, label);
        int separator = value.indexOf(':');
        String algorithm = separator < 0 ? "" : value.substring(0, separator);
        String digest = separator < 0 ? "" : value.substring(separator + 1);
        if (!algorithms.contains(algorithm) || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be an algorithm-tagged lowercase 256-bit digest");
        }
    }

    private static void requireWireName(String value, String label, Set<String> allowed) {
        requireNonBlank(value, label);
        if (!allowed.contains(value)) throw new IllegalArgumentException("Unknown " + label + ": " + value);
    }
}
