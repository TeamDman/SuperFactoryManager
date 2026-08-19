package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSourceRootIdentity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Provider-neutral, fail-closed join from immutable editor context to the
 * worker-resolved definition request contract.
 */
public final class SFMDefinitionContextAdapter {
    public enum DiagnosticCode {
        HANDSHAKE_NOT_READY,
        WORKSPACE_METADATA_UNAVAILABLE,
        ORIGIN_IS_NOT_DOCUMENT,
        DOCUMENT_NOT_READY,
        DOCUMENT_PATH_ABSENT,
        AUTHORIZED_ROOT_ABSENT,
        DOCUMENT_PATH_NOT_FILE,
        AUTHORIZED_ROOT_NOT_FILE,
        PRIMARY_CURSOR_ABSENT,
        PRIMARY_CURSOR_INACTIVE,
        CANVAS_WHITESPACE,
        AUTHORIZED_ROOT_NOT_MAPPED,
        AUTHORIZED_ROOT_AMBIGUOUS,
        DOCUMENT_OUTSIDE_AUTHORIZED_ROOT,
        DOCUMENT_EQUALS_AUTHORIZED_ROOT,
        CURRENT_HASH_MISMATCH,
        BASELINE_HASH_ABSENT,
        BASELINE_HASH_MISMATCH,
        ROOT_METADATA_MISMATCH,
        INVALID_REQUEST_IDENTITY
    }

    public record Diagnostic(DiagnosticCode code, String message) {
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    public record Adaptation(
            SFMContextOriginId originId,
            Optional<SFMDefinitionRequest> request,
            List<Diagnostic> diagnostics
    ) {
        public Adaptation {
            Objects.requireNonNull(originId, "originId");
            request = Objects.requireNonNull(request, "request");
            diagnostics = List.copyOf(diagnostics);
            if (request.isPresent() == !diagnostics.isEmpty()) {
                throw new IllegalArgumentException("Adaptation must contain either one request or diagnostics");
            }
        }

        public boolean success() {
            return request.isPresent();
        }
    }

    public Adaptation adapt(
            SFMContextContribution contribution,
            Optional<SFMSymbolServerProtocol.ServerHello> handshake,
            long requestId,
            long requestGeneration
    ) {
        Objects.requireNonNull(contribution, "contribution");
        if (!(contribution.projection() instanceof SFMContextDocumentProjection document)) {
            return rejected(
                    contribution.originId(),
                    DiagnosticCode.ORIGIN_IS_NOT_DOCUMENT,
                    "The context origin does not project a text document"
            );
        }
        return adapt(contribution.originId(), document, handshake, requestId, requestGeneration);
    }

    public Adaptation adapt(
            SFMContextOriginId originId,
            SFMContextDocumentProjection document,
            Optional<SFMSymbolServerProtocol.ServerHello> handshake,
            long requestId,
            long requestGeneration
    ) {
        Objects.requireNonNull(originId, "originId");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(handshake, "handshake");
        if (requestId <= 0 || requestGeneration < 0) {
            return rejected(
                    originId,
                    DiagnosticCode.INVALID_REQUEST_IDENTITY,
                    "Definition request ids must be positive and generations must not be negative"
            );
        }
        if (handshake.isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.HANDSHAKE_NOT_READY,
                    "The symbol worker has not completed its handshake"
            );
        }
        SFMSymbolServerProtocol.WorkspaceMetadata metadata = handshake.orElseThrow().workspace();

        SFMTextDocumentSnapshot baseline = document.baseline();
        if (!baseline.ready()) {
            return rejected(
                    originId,
                    DiagnosticCode.DOCUMENT_NOT_READY,
                    "The document baseline is not READY"
            );
        }
        if (baseline.path().isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.DOCUMENT_PATH_ABSENT,
                    "The document has no resolver-issued path"
            );
        }
        if (baseline.authorizedRoot().isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.AUTHORIZED_ROOT_ABSENT,
                    "The document has no resolver-authorized root"
            );
        }
        SFMPath path = baseline.path().orElseThrow();
        SFMPath authorizedRoot = baseline.authorizedRoot().orElseThrow();
        if (path.kind() != SFMPath.Kind.FILE || !path.scheme().equals("file")) {
            return rejected(
                    originId,
                    DiagnosticCode.DOCUMENT_PATH_NOT_FILE,
                    "Definition analysis requires a path-addressed file document"
            );
        }
        if (authorizedRoot.kind() != SFMPath.Kind.FILE || !authorizedRoot.scheme().equals("file")) {
            return rejected(
                    originId,
                    DiagnosticCode.AUTHORIZED_ROOT_NOT_FILE,
                    "Definition analysis requires a file resolver authorization root"
            );
        }

        Optional<SFMContextCursorProjection> primary = document.cursors().stream()
                .filter(SFMContextCursorProjection::primary)
                .findFirst();
        if (primary.isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.PRIMARY_CURSOR_ABSENT,
                    "The document has no primary cursor"
            );
        }
        if (!primary.orElseThrow().active()) {
            return rejected(
                    originId,
                    DiagnosticCode.PRIMARY_CURSOR_INACTIVE,
                    "The primary cursor is not active"
            );
        }
        Optional<SFMTextDocumentPosition> textHit = textHit(primary.orElseThrow().position());
        if (textHit.isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.CANVAS_WHITESPACE,
                    "The primary canvas cursor does not hit document text"
            );
        }

        if (relativeSegments(authorizedRoot, path).isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.DOCUMENT_OUTSIDE_AUTHORIZED_ROOT,
                    "The document is outside its resolver-authorized root"
            );
        }
        SFMSymbolServerProtocol.WorkspaceMetadata workspaceMetadata = metadata;
        List<ResolvedSourceRoot> mappings;
        try {
            mappings = baseline.sourceRootIdentity().isPresent()
                    ? exactSourceRootMapping(
                            workspaceMetadata,
                            authorizedRoot,
                            path,
                            baseline.sourceRootIdentity().orElseThrow()
                    )
                    : deepestContainingMappings(workspaceMetadata, authorizedRoot, path);
        } catch (IllegalArgumentException invalidMetadata) {
            return rejected(
                    originId,
                    DiagnosticCode.ROOT_METADATA_MISMATCH,
                    "The negotiated worker source-root metadata is internally inconsistent"
            );
        }
        if (mappings.isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.AUTHORIZED_ROOT_NOT_MAPPED,
                    "No worker source root contains the document within its resolver authorization"
            );
        }
        if (mappings.size() != 1) {
            return rejected(
                    originId,
                    DiagnosticCode.AUTHORIZED_ROOT_AMBIGUOUS,
                    "More than one equally specific worker source root contains the document"
            );
        }
        ResolvedSourceRoot mapping = mappings.get(0);
        SFMPath analysisRoot = mapping.analysisRoot();
        Optional<List<String>> relativeSegments = relativeSegments(analysisRoot, path);
        if (relativeSegments.isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.DOCUMENT_OUTSIDE_AUTHORIZED_ROOT,
                    "The document is outside its resolver-authorized root"
            );
        }
        if (relativeSegments.orElseThrow().isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.DOCUMENT_EQUALS_AUTHORIZED_ROOT,
                    "The authorized source root itself is not a document"
            );
        }
        List<String> documentSegments = relativeSegments.orElseThrow();
        String relativePath = String.join("/", documentSegments);
        String reportPath = mapping.reportPrefix().isEmpty()
                ? relativePath
                : mapping.reportPrefix() + "/" + relativePath;

        String currentHash = SFMDefinitionRequest.sha256(document.currentText());
        if (!currentHash.equals(prefixedSha256(document.currentSha256()))) {
            return rejected(
                    originId,
                    DiagnosticCode.CURRENT_HASH_MISMATCH,
                    "The current document SHA-256 does not match its immutable text"
            );
        }
        if (baseline.sha256().isEmpty()) {
            return rejected(
                    originId,
                    DiagnosticCode.BASELINE_HASH_ABSENT,
                    "The READY baseline has no disk SHA-256 witness"
            );
        }
        String diskHash = prefixedSha256(baseline.sha256().orElseThrow());
        if (!diskHash.equals(SFMDefinitionRequest.sha256(baseline.text()))) {
            return rejected(
                    originId,
                    DiagnosticCode.BASELINE_HASH_MISMATCH,
                    "The baseline disk SHA-256 does not match its immutable text"
            );
        }

        SFMTextDocumentPosition cursor = textHit.orElseThrow();
        SFMDefinitionRequest request = new SFMDefinitionRequest(
                requestId,
                requestGeneration,
                workspaceMetadata.workspace(),
                new SFMDefinitionRequest.Document(
                        mapping.documentAddress(path, documentSegments),
                        mapping.rootId(),
                        relativePath,
                        reportPath,
                        mapping.sourceSet(),
                        document.currentText(),
                        currentHash,
                        Optional.of(diskHash)
                ),
                new SFMDefinitionRequest.Position(
                        (long) cursor.line() + 1,
                        (long) cursor.column() + 1,
                        cursor.byteOffset()
                )
        );
        return new Adaptation(originId, Optional.of(request), List.of());
    }

    private static Optional<SFMTextDocumentPosition> textHit(SFMContextPosition position) {
        if (position instanceof SFMContextPosition.Text text) return Optional.of(text.position());
        if (position instanceof SFMContextPosition.Canvas canvas) return canvas.textHit();
        return Optional.empty();
    }

    private static Optional<List<String>> relativeSegments(SFMPath root, SFMPath path) {
        if (root.kind() != SFMPath.Kind.FILE || path.kind() != SFMPath.Kind.FILE) {
            return Optional.empty();
        }
        Path nativeRoot = root.toNativePath().toAbsolutePath().normalize();
        Path nativePath = path.toNativePath().toAbsolutePath().normalize();
        if (!nativePath.startsWith(nativeRoot)) return Optional.empty();
        Path relative = nativeRoot.relativize(nativePath);
        ArrayList<String> segments = new ArrayList<>(relative.getNameCount());
        for (Path segment : relative) {
            segments.add(segment.toString());
        }
        return Optional.of(List.copyOf(segments));
    }

    private static List<ResolvedSourceRoot> deepestContainingMappings(
            SFMSymbolServerProtocol.WorkspaceMetadata workspace,
            SFMPath authorizedRoot,
            SFMPath documentPath
    ) {
        List<ResolvedSourceRoot> candidates = resolvedSourceRoots(workspace);
        int deepest = -1;
        ArrayList<ResolvedSourceRoot> matches = new ArrayList<>();
        for (ResolvedSourceRoot mapping : candidates) {
            // The resolver grant and worker root may be nested in either direction.
            // Their safe composition is the concrete document, which must be
            // contained by both. Requiring the grant to contain the complete
            // worker root rejected legitimate package/subtree grants.
            if (relativeSegments(authorizedRoot, documentPath).isEmpty()
                    || relativeSegments(mapping.analysisRoot(), documentPath).isEmpty()) {
                continue;
            }
            int depth = mapping.analysisRoot().segments().size();
            if (depth > deepest) {
                deepest = depth;
                matches.clear();
            }
            if (depth == deepest) matches.add(mapping);
        }
        return List.copyOf(matches);
    }

    private static List<ResolvedSourceRoot> exactSourceRootMapping(
            SFMSymbolServerProtocol.WorkspaceMetadata workspace,
            SFMPath authorizedRoot,
            SFMPath documentPath,
            SFMTextDocumentSourceRootIdentity identity
    ) {
        List<ResolvedSourceRoot> matches = resolvedSourceRoots(workspace).stream()
                .filter(mapping -> mapping.resolverId().equals(identity.resolverId()))
                .filter(mapping -> mapping.addressScheme().equals(identity.addressScheme()))
                .filter(mapping -> mapping.rootId().equals(identity.rootId()))
                .filter(mapping -> mapping.sourceSet().equals(identity.sourceSet()))
                .filter(mapping -> mapping.reportPrefix().equals(identity.reportPrefix()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Retained document source-root identity is stale or ambiguous");
        }
        ResolvedSourceRoot match = matches.get(0);
        if (relativeSegments(authorizedRoot, documentPath).isEmpty()
                || relativeSegments(match.analysisRoot(), documentPath).isEmpty()) {
            throw new IllegalArgumentException("Retained document source-root identity no longer contains document");
        }
        return List.of(match);
    }

    private static List<ResolvedSourceRoot> resolvedSourceRoots(
            SFMSymbolServerProtocol.WorkspaceMetadata workspace
    ) {
        ArrayList<ResolvedSourceRoot> candidates = new ArrayList<>();
        HashSet<String> managedRootKeys = new HashSet<>();
        for (SFMSymbolServerProtocol.ManagedSourceRootMapping mapping
                : workspace.managedSourceRootMappings()) {
            ResolvedSourceRoot resolved = managedSourceRoot(workspace, mapping);
            managedRootKeys.add(rootKey(resolved.rootId(), resolved.sourceSet()));
            candidates.add(resolved);
        }
        for (SFMSymbolServerProtocol.DependencySourceRootMapping mapping
                : workspace.dependencySourceRootMappings()) {
            if (!mapping.sourceSet().startsWith("dependency:")) {
                throw new IllegalArgumentException("Dependency source root has a non-dependency source set");
            }
            candidates.add(new ResolvedSourceRoot(
                    RootKind.DEPENDENCY_SOURCE,
                    "dependency-source",
                    "dependency-source",
                    mapping.rootId(),
                    mapping.sourceSet(),
                    mapping.reportPrefix(),
                    nativeRoot(mapping.canonicalAbsolutePath())
            ));
        }
        for (SFMSymbolServerProtocol.SourceRootMapping mapping : workspace.rootMappings()) {
            if (managedRootKeys.contains(rootKey(mapping.rootId(), mapping.sourceSet()))) continue;
            candidates.add(workspaceSourceRoot(workspace, mapping));
        }
        return List.copyOf(candidates);
    }

    private static ResolvedSourceRoot workspaceSourceRoot(
            SFMSymbolServerProtocol.WorkspaceMetadata workspace,
            SFMSymbolServerProtocol.SourceRootMapping mapping
    ) {
        SFMDefinitionRequest.SourceRoot sourceRoot = requireRequestRoot(
                workspace, mapping.rootId(), mapping.sourceSet());
        if (sourceRoot.kind().equals("jdk")) {
            throw new IllegalArgumentException("JDK source root has no managed resolver mapping");
        }
        if (!sourceRoot.exists() || !sourceRoot.path().equals(mapping.reportRootPath())) {
            throw new IllegalArgumentException("Workspace source-root projection mismatch");
        }
        return new ResolvedSourceRoot(
                RootKind.WORKSPACE,
                "workspace",
                "file",
                mapping.rootId(),
                mapping.sourceSet(),
                mapping.reportRootPath(),
                nativeRoot(mapping.canonicalAbsolutePath())
        );
    }

    private static ResolvedSourceRoot managedSourceRoot(
            SFMSymbolServerProtocol.WorkspaceMetadata workspace,
            SFMSymbolServerProtocol.ManagedSourceRootMapping mapping
    ) {
        SFMDefinitionRequest.SourceRoot sourceRoot = requireRequestRoot(
                workspace, mapping.rootId(), mapping.sourceSet());
        if (!sourceRoot.exists()) {
            throw new IllegalArgumentException("Managed source root is unavailable");
        }
        List<SFMSymbolServerProtocol.SourceRootMapping> orderedRoots = workspace.rootMappings().stream()
                .filter(candidate -> candidate.rootId().equals(mapping.rootId()))
                .filter(candidate -> candidate.sourceSet().equals(mapping.sourceSet()))
                .toList();
        if (orderedRoots.size() != 1) {
            throw new IllegalArgumentException("Managed source root has no unique ordered root");
        }
        SFMSymbolServerProtocol.SourceRootMapping orderedRoot = orderedRoots.get(0);
        if (!orderedRoot.reportRootPath().equals(sourceRoot.path())
                || !sameNativeRoot(orderedRoot.canonicalAbsolutePath(), mapping.canonicalAbsolutePath())) {
            throw new IllegalArgumentException("Managed and ordered roots disagree");
        }
        if (mapping.portableRootPath().isPresent()
                && !mapping.portableRootPath().orElseThrow().equals(sourceRoot.path())) {
            throw new IllegalArgumentException("Managed portable root disagrees with request root");
        }
        if (sourceRoot.kind().equals("jdk")
                && (!mapping.resolverId().equals("jdk-source")
                || !mapping.addressScheme().equals("jdk-source")
                || !mapping.resolverIdentity().equals(sourceRoot.path())
                || mapping.reportPrefix().isEmpty())) {
            throw new IllegalArgumentException("Managed JDK resolver identity mismatch");
        }
        String reportPrefix = mapping.reportPrefix()
                .or(() -> mapping.portableRootPath())
                .orElse(sourceRoot.path());
        return new ResolvedSourceRoot(
                RootKind.MANAGED,
                mapping.resolverId(),
                mapping.addressScheme(),
                mapping.rootId(),
                mapping.sourceSet(),
                reportPrefix,
                nativeRoot(mapping.canonicalAbsolutePath())
        );
    }

    private static SFMDefinitionRequest.SourceRoot requireRequestRoot(
            SFMSymbolServerProtocol.WorkspaceMetadata workspace,
            String rootId,
            String sourceSet
    ) {
        SFMDefinitionRequest.SourceRoot sourceRoot = workspace.workspace().sourceRoots().stream()
                .filter(candidate -> candidate.id().equals(rootId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Negotiated root has no request root"));
        if (!sourceRoot.sourceSet().equals(sourceSet)) {
            throw new IllegalArgumentException("Negotiated root source set mismatch");
        }
        return sourceRoot;
    }

    private static SFMPath nativeRoot(String canonicalAbsolutePath) {
        return SFMPath.fromNative(Path.of(canonicalAbsolutePath));
    }

    private static boolean sameNativeRoot(String left, String right) {
        Path leftPath = Path.of(left).toAbsolutePath().normalize();
        Path rightPath = Path.of(right).toAbsolutePath().normalize();
        return leftPath.equals(rightPath);
    }

    private static String rootKey(String rootId, String sourceSet) {
        return rootId + "\u0000" + sourceSet;
    }

    private enum RootKind {
        WORKSPACE,
        DEPENDENCY_SOURCE,
        MANAGED
    }

    private record ResolvedSourceRoot(
            RootKind kind,
            String resolverId,
            String addressScheme,
            String rootId,
            String sourceSet,
            String reportPrefix,
            SFMPath analysisRoot
    ) {
        private ResolvedSourceRoot {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(resolverId, "resolverId");
            Objects.requireNonNull(addressScheme, "addressScheme");
            Objects.requireNonNull(rootId, "rootId");
            Objects.requireNonNull(sourceSet, "sourceSet");
            Objects.requireNonNull(reportPrefix, "reportPrefix");
            Objects.requireNonNull(analysisRoot, "analysisRoot");
        }

        private String documentAddress(SFMPath nativePath, List<String> relativeSegments) {
            if (kind == RootKind.WORKSPACE) return nativePath.canonical();
            return new SFMPath(
                    SFMPath.Kind.CONTRIBUTED,
                    addressScheme,
                    rootId,
                    relativeSegments,
                    Optional.empty(),
                    false
            ).canonical();
        }
    }

    private static String prefixedSha256(String hash) {
        Objects.requireNonNull(hash, "hash");
        return hash.startsWith("sha256:") ? hash : "sha256:" + hash;
    }

    private static Adaptation rejected(
            SFMContextOriginId originId,
            DiagnosticCode code,
            String message
    ) {
        ArrayList<Diagnostic> diagnostics = new ArrayList<>(1);
        diagnostics.add(new Diagnostic(code, message));
        return new Adaptation(originId, Optional.empty(), diagnostics);
    }
}
