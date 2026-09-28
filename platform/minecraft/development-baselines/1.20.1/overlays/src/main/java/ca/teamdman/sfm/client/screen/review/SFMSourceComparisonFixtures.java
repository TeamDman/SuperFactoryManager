package ca.teamdman.sfm.client.screen.review;

import java.util.List;

/** Deterministic source-review lifecycle with file and operation families visible together. */
public final class SFMSourceComparisonFixtures {
    private SFMSourceComparisonFixtures() {}

    public static SFMSourceComparison reviewWalkthrough() {
        return new SFMSourceComparison(
                "baseline:43ffed6f", "candidate:review-workspace",
                List.of(
                        file("", "Added.java", SFMSourceComparison.FileChangeKind.ADDED),
                        file("Deleted.java", "", SFMSourceComparison.FileChangeKind.DELETED),
                        file("Frob.java", "Frog.java", SFMSourceComparison.FileChangeKind.RENAMED),
                        file("Worker.java", "Worker.java", SFMSourceComparison.FileChangeKind.MODIFIED)
                ),
                List.of(
                        operation("add", SFMSourceComparison.OperationKind.INSERT, "Inserted Added.java", "", "Added.java",
                                List.of(), List.of("final class Added {}"), SFMSourceComparison.Equivalence.UNKNOWN,
                                SFMSourceComparison.AuditStatus.PERMITTED, "New source file"),
                        operation("delete", SFMSourceComparison.OperationKind.DELETE, "Deleted obsolete helper", "Deleted.java", "",
                                List.of("void obsolete() {}"), List.of(), SFMSourceComparison.Equivalence.UNKNOWN,
                                SFMSourceComparison.AuditStatus.WARNED, "Deletion needs human review"),
                        operation("rename", SFMSourceComparison.OperationKind.RENAME, "Frob renamed to Frog", "Frob.java", "Frog.java",
                                List.of("final class Frob {}"), List.of("final class Frog {}"), SFMSourceComparison.Equivalence.ALPHA,
                                SFMSourceComparison.AuditStatus.PERMITTED, "Body unchanged under symbol rename"),
                        operation("body", SFMSourceComparison.OperationKind.CHANGED_BODY, "Changed transfer body", "Worker.java", "Worker.java",
                                List.of("return raw;"), List.of("return check(raw);"), SFMSourceComparison.Equivalence.UNKNOWN,
                                SFMSourceComparison.AuditStatus.FORBIDDEN, "Audit policy: unapproved transfer path")
                ),
                List.of("Body change violates the current SFM audit policy"),
                List.of("Fixture hashes are SHA-256 over shown source", "No type resolver is claimed")
        );
    }

    /** Same operation identity and snapshots, but changed source content invalidates its old witness. */
    public static SFMSourceComparison changeBodySource(SFMSourceComparison comparison) {
        var old = comparison.operations().stream().filter(operation -> operation.id().equals("body")).findFirst().orElseThrow();
        List<String> changedAfter = List.of("return log(check(raw));");
        var replacement = new SFMSourceComparison.SourceOperation(
                old.id(), old.kind(), old.label(), old.beforePath(), old.afterPath(), old.beforeSpan(), old.afterSpan(),
                old.beforeHash(), hash(changedAfter), old.beforeLines(), changedAfter,
                old.equivalence(), old.auditStatus(), "Changed after review; old SHA-256 witness does not match"
        );
        return comparison.replaceOperation(replacement);
    }

    private static SFMSourceComparison.FileChange file(String before, String after, SFMSourceComparison.FileChangeKind kind) {
        return new SFMSourceComparison.FileChange(before, after, kind);
    }

    private static SFMSourceComparison.SourceOperation operation(
            String id, SFMSourceComparison.OperationKind kind, String label, String beforePath, String afterPath,
            List<String> before, List<String> after, SFMSourceComparison.Equivalence equivalence,
            SFMSourceComparison.AuditStatus audit, String diagnostic
    ) {
        return new SFMSourceComparison.SourceOperation(
                id, kind, label, beforePath, afterPath,
                before.isEmpty() ? null : new SFMSourceComparison.SourceSpan(1, before.size()),
                after.isEmpty() ? null : new SFMSourceComparison.SourceSpan(1, after.size()),
                hash(before), hash(after), before, after, equivalence, audit, diagnostic
        );
    }

    private static String hash(List<String> lines) { return SFMLineRangeComparator.sha256(String.join("\n", lines)); }
}
