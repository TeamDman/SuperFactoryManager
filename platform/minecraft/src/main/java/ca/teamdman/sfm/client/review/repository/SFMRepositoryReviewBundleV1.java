package ca.teamdman.sfm.client.review.repository;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Immutable semantic model for {@code sfm.repository-review-bundle/1}. */
public record SFMRepositoryReviewBundleV1(
        String schema,
        String id,
        String name,
        Repository repository,
        Producer producer,
        Snapshot before,
        Snapshot after,
        Comparison comparison
) {
    public static final String SCHEMA = "sfm.repository-review-bundle/1";
    public static final String SNAPSHOT_SCHEMA = "sfm.repository-snapshot/1";
    public static final String COMPARISON_SCHEMA = "sfm.repository-comparison/1";

    public record Repository(String name, String displayPath) {}
    public record Producer(String id, String contractVersion) {}
    public record Snapshot(String schema, String id, Source source, List<FileEntry> files) {
        public Snapshot { files = List.copyOf(files); }
    }
    public record Source(String kind, String revision, String label) {}
    public enum Encoding { UTF8, BASE64 }

    public static final class FileEntry {
        private final String path;
        private final Encoding encoding;
        private final @Nullable String text;
        private final @Nullable String data;
        private final String sha256;
        private final byte[] content;

        public FileEntry(String path, Encoding encoding, @Nullable String text, @Nullable String data,
                         String sha256, byte[] content) {
            this.path = path;
            this.encoding = encoding;
            this.text = text;
            this.data = data;
            this.sha256 = sha256;
            this.content = content.clone();
        }

        public String path() { return path; }
        public Encoding encoding() { return encoding; }
        public @Nullable String text() { return text; }
        public @Nullable String data() { return data; }
        public String sha256() { return sha256; }
        public byte[] content() { return content.clone(); }
        public int contentLength() { return content.length; }
    }

    public record Comparison(String schema, String beforeSnapshotId, String afterSnapshotId,
                             List<FileChange> fileChanges) {
        public Comparison { fileChanges = List.copyOf(fileChanges); }
    }
    public enum ChangeKind { ADDED, REMOVED, MODIFIED, RENAMED, UNCHANGED }
    public record FileChange(ChangeKind kind, @Nullable String beforePath, @Nullable String afterPath,
                             boolean binary, List<Operation> operations, List<String> diagnostics) {
        public FileChange { operations = List.copyOf(operations); diagnostics = List.copyOf(diagnostics); }
    }
    public enum OperationKind { INSERT, DELETE, REPLACE }
    public record Operation(String id, OperationKind kind, @Nullable Selection before,
                            @Nullable Selection after) {}
    public record Selection(String path, int startByte, int endByte, String sha256) {}
}
