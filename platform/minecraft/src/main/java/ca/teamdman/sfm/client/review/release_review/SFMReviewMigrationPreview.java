package ca.teamdman.sfm.client.review.release_review;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Pure bounded literal-witness matching. A preview is not an approval or a saved mutation. */
public final class SFMReviewMigrationPreview {
    private SFMReviewMigrationPreview() {}

    public enum Status { EXACT, RELOCATED, AMBIGUOUS, MISSING, INCOMPLETE }
    public record Limits(long comparedBytes, int candidates) {
        public static final Limits DEFAULT = new Limits(16_000_000, 256);
        public Limits {
            if (comparedBytes < 1 || candidates < 1) throw new IllegalArgumentException("Positive migration limits required");
        }
    }
    public record Candidate(String documentRevisionId, String path, String documentSha256, int startByte, int endByte) {}
    public record Result(Status status, List<Candidate> candidates, long comparedBytes, String diagnostic) {
        public Result { candidates = List.copyOf(candidates); }
        public boolean uniquelyApplicable() { return status == Status.EXACT || status == Status.RELOCATED; }
    }

    /**
     * Caller supplies an explicit destination domain and its completeness. Missing/truncated
     * candidates cannot establish uniqueness. Search all occurrences, including overlaps:
     * a same-offset match is not preferred over a second plausible occurrence.
     */
    public static Result match(SFMReviewEvidenceTable.Observed original, int startByte, int endByte,
                               List<SFMReviewEvidenceTable.Observed> destination, boolean domainComplete, Limits limits) {
        Objects.requireNonNull(original); Objects.requireNonNull(destination); Objects.requireNonNull(limits);
        ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange.positionAtByteOffset(original.text(), startByte);
        ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange.positionAtByteOffset(original.text(), endByte);
        if (endByte < startByte) throw new IllegalArgumentException("Reversed migration witness");
        byte[] source = Arrays.copyOfRange(original.text().getBytes(StandardCharsets.UTF_8), startByte, endByte);
        if (source.length == 0) return new Result(Status.INCOMPLETE, List.of(), 0,
                "An empty witness needs an explicit target; it cannot establish relocation");
        var ids = new HashSet<String>();
        var ordered = destination.stream().sorted(Comparator.comparing(value -> value.document().revisionId())).toList();
        for (var value : ordered) if (!ids.add(value.document().revisionId()))
            throw new IllegalArgumentException("Duplicate destination revision");
        var matches = new ArrayList<Candidate>();
        long work = 0;
        for (var value : ordered) {
            byte[] bytes = value.text().getBytes(StandardCharsets.UTF_8);
            for (int offset = 0; offset <= bytes.length - source.length; offset++) {
                int index = 0;
                while (index < source.length) {
                    if (work == limits.comparedBytes()) return new Result(Status.INCOMPLETE, matches, work,
                            "Comparison limit reached; matches are incomplete and cannot be accepted");
                    work++;
                    if (bytes[offset + index] != source[index]) break;
                    index++;
                }
                if (index != source.length) continue;
                // Exact UTF-8 sequence matching must also respect the editor's CRLF coordinate model.
                if (splitsCrLf(bytes, offset) || splitsCrLf(bytes, offset + source.length)) continue;
                if (matches.size() == limits.candidates()) return new Result(Status.INCOMPLETE, matches, work,
                        "Candidate limit reached; matches are incomplete and cannot be accepted");
                matches.add(new Candidate(value.document().revisionId(), value.document().path(),
                        value.document().sha256(), offset, offset + source.length));
            }
        }
        if (!domainComplete) return new Result(Status.INCOMPLETE, matches, work,
                "Destination domain has unavailable source evidence; uniqueness is unknown");
        if (matches.isEmpty()) return new Result(Status.MISSING, matches, work, "No exact literal witness found");
        if (matches.size() > 1) return new Result(Status.AMBIGUOUS, matches, work,
                "Multiple exact witnesses; no candidate was selected");
        Candidate found = matches.get(0);
        boolean exact = found.path().equals(original.document().path()) && found.startByte() == startByte;
        return new Result(exact ? Status.EXACT : Status.RELOCATED, matches, work,
                exact ? "Unique same-path, same-offset bytes; new source identity still requires explicit acceptance"
                        : "Unique exact bytes at a different path or offset; explicit acceptance required");
    }

    private static boolean splitsCrLf(byte[] bytes, int offset) {
        return offset > 0 && offset < bytes.length && bytes[offset - 1] == '\r' && bytes[offset] == '\n';
    }
}
