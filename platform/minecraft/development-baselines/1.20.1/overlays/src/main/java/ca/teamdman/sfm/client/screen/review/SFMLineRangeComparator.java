package ca.teamdman.sfm.client.screen.review;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Deterministic text fallback that makes no semantic-correspondence claim. */
public final class SFMLineRangeComparator {
    private SFMLineRangeComparator() {}

    public static SFMSourceComparison compare(
            String beforeSnapshot, String afterSnapshot, String path, String beforeText, String afterText
    ) {
        if (beforeText.equals(afterText)) {
            return new SFMSourceComparison(beforeSnapshot, afterSnapshot,
                    List.of(new SFMSourceComparison.FileChange(path, path, SFMSourceComparison.FileChangeKind.UNCHANGED)),
                    List.of(), List.of(), List.of("Byte-identical UTF-8 text"));
        }
        List<String> before = List.of(beforeText.split("\\R", -1));
        List<String> after = List.of(afterText.split("\\R", -1));
        int prefix = 0;
        while (prefix < before.size() && prefix < after.size() && before.get(prefix).equals(after.get(prefix))) prefix++;
        int suffix = 0;
        while (suffix < before.size() - prefix && suffix < after.size() - prefix
                && before.get(before.size() - 1 - suffix).equals(after.get(after.size() - 1 - suffix))) suffix++;
        List<String> removed = before.subList(prefix, before.size() - suffix);
        List<String> added = after.subList(prefix, after.size() - suffix);
        var kind = removed.isEmpty() ? SFMSourceComparison.OperationKind.INSERT
                : added.isEmpty() ? SFMSourceComparison.OperationKind.DELETE : SFMSourceComparison.OperationKind.REPLACE;
        var operation = new SFMSourceComparison.SourceOperation(
                "line-range:" + prefix + ":" + removed.size() + ":" + added.size(), kind, "Line-range fallback",
                path, path,
                removed.isEmpty() ? null : new SFMSourceComparison.SourceSpan(prefix + 1, prefix + removed.size()),
                added.isEmpty() ? null : new SFMSourceComparison.SourceSpan(prefix + 1, prefix + added.size()),
                sha256(beforeText), sha256(afterText), removed, added,
                SFMSourceComparison.Equivalence.UNKNOWN, SFMSourceComparison.AuditStatus.WARNED,
                "Fallback comparison; no semantic correspondence claimed"
        );
        return new SFMSourceComparison(beforeSnapshot, afterSnapshot,
                List.of(new SFMSourceComparison.FileChange(path, path, SFMSourceComparison.FileChangeKind.MODIFIED)),
                List.of(operation), List.of(), List.of("Java \\R line splitting; unchanged edges are anchors"));
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
