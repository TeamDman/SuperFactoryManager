package ca.teamdman.sfm.client.screen.review;

import java.util.List;
import java.util.Objects;

/** Immutable source-oriented interchange model; semantic producers can replace the fallback comparator later. */
public record SFMSourceComparison(
        String beforeSnapshot,
        String afterSnapshot,
        List<FileChange> files,
        List<SourceOperation> operations,
        List<String> diagnostics,
        List<String> assumptions
) {
    public SFMSourceComparison {
        beforeSnapshot = requireText(beforeSnapshot, "beforeSnapshot");
        afterSnapshot = requireText(afterSnapshot, "afterSnapshot");
        files = List.copyOf(files);
        operations = List.copyOf(operations);
        diagnostics = List.copyOf(diagnostics);
        assumptions = List.copyOf(assumptions);
    }

    public enum FileChangeKind { ADDED, DELETED, RENAMED, MODIFIED, UNCHANGED }
    public enum OperationKind { INSERT, DELETE, REPLACE, RENAME, FORMATTING_ONLY, CHANGED_BODY, UNKNOWN }
    public enum Equivalence { BYTE, FORMATTING_INSENSITIVE, ALPHA, UNKNOWN }
    public enum AuditStatus { PERMITTED, WARNED, FORBIDDEN }

    public record SourceSpan(int startLine, int endLine) {
        public SourceSpan {
            if (startLine < 1 || endLine < startLine) throw new IllegalArgumentException("Invalid source span");
        }
    }

    public record FileChange(String beforePath, String afterPath, FileChangeKind kind) {
        public FileChange {
            beforePath = beforePath == null ? "" : beforePath;
            afterPath = afterPath == null ? "" : afterPath;
            Objects.requireNonNull(kind, "kind");
        }
    }

    public record SourceOperation(
            String id,
            OperationKind kind,
            String label,
            String beforePath,
            String afterPath,
            SourceSpan beforeSpan,
            SourceSpan afterSpan,
            String beforeHash,
            String afterHash,
            List<String> beforeLines,
            List<String> afterLines,
            Equivalence equivalence,
            AuditStatus auditStatus,
            String diagnostic
    ) {
        public SourceOperation {
            id = requireText(id, "id");
            label = requireText(label, "label");
            beforePath = beforePath == null ? "" : beforePath;
            afterPath = afterPath == null ? "" : afterPath;
            beforeHash = requireText(beforeHash, "beforeHash");
            afterHash = requireText(afterHash, "afterHash");
            beforeLines = List.copyOf(beforeLines);
            afterLines = List.copyOf(afterLines);
            diagnostic = diagnostic == null ? "" : diagnostic;
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(equivalence, "equivalence");
            Objects.requireNonNull(auditStatus, "auditStatus");
        }

        public String displayPath() {
            if (beforePath.isBlank()) return afterPath;
            if (afterPath.isBlank() || beforePath.equals(afterPath)) return beforePath;
            return beforePath + " -> " + afterPath;
        }
    }

    public SFMSourceComparison replaceOperation(SourceOperation replacement) {
        List<SourceOperation> replaced = operations.stream()
                .map(operation -> operation.id().equals(replacement.id()) ? replacement : operation)
                .toList();
        return new SFMSourceComparison(beforeSnapshot, afterSnapshot, files, replaced, diagnostics, assumptions);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
