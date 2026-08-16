package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextCursorProjection;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPosition;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;

import java.nio.file.Path;
import java.util.ArrayList;
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
        List<SFMSymbolServerProtocol.SourceRootMapping> mappings = deepestContainingMappings(
                workspaceMetadata,
                authorizedRoot,
                path
        );
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
        SFMSymbolServerProtocol.SourceRootMapping mapping = mappings.get(0);
        SFMDefinitionRequest.SourceRoot sourceRoot = workspaceMetadata.workspace().sourceRoots().stream()
                .filter(candidate -> candidate.id().equals(mapping.rootId()))
                .findFirst()
                .orElse(null);
        if (sourceRoot == null
                || !sourceRoot.exists()
                || !sourceRoot.sourceSet().equals(mapping.sourceSet())
                || !sourceRoot.path().equals(mapping.reportRootPath())) {
            return rejected(
                    originId,
                    DiagnosticCode.ROOT_METADATA_MISMATCH,
                    "The mapped source root disagrees with the resolved workspace identity"
            );
        }

        SFMPath analysisRoot = SFMPath.fromNative(java.nio.file.Path.of(mapping.canonicalAbsolutePath()));
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
        String relativePath = String.join("/", relativeSegments.orElseThrow());
        String reportPath = mapping.reportRootPath().isEmpty()
                ? relativePath
                : mapping.reportRootPath() + "/" + relativePath;

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
                        path.canonical(),
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

    private static List<SFMSymbolServerProtocol.SourceRootMapping> deepestContainingMappings(
            SFMSymbolServerProtocol.WorkspaceMetadata workspace,
            SFMPath authorizedRoot,
            SFMPath documentPath
    ) {
        int deepest = -1;
        ArrayList<SFMSymbolServerProtocol.SourceRootMapping> matches = new ArrayList<>();
        for (SFMSymbolServerProtocol.SourceRootMapping mapping : workspace.rootMappings()) {
            SFMPath analysisRoot = SFMPath.fromNative(java.nio.file.Path.of(mapping.canonicalAbsolutePath()));
            // The resolver grant and worker root may be nested in either direction.
            // Their safe composition is the concrete document, which must be
            // contained by both. Requiring the grant to contain the complete
            // worker root rejected legitimate package/subtree grants.
            if (relativeSegments(authorizedRoot, documentPath).isEmpty()
                    || relativeSegments(analysisRoot, documentPath).isEmpty()) {
                continue;
            }
            int depth = analysisRoot.segments().size();
            if (depth > deepest) {
                deepest = depth;
                matches.clear();
            }
            if (depth == deepest) matches.add(mapping);
        }
        return List.copyOf(matches);
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
