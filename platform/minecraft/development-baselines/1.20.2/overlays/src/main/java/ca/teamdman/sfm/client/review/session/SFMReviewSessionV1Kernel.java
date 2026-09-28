package ca.teamdman.sfm.client.review.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure v1 hashtag, UTF-8 coordinate, selection algebra, and approval-policy kernel. */
public final class SFMReviewSessionV1Kernel {
    public static final String EVALUATOR_VERSION = "sfm-review-v1/1";

    public enum Status {
        RESOLVED_EXACTLY,
        RESOLVED_WITH_RELOCATION,
        AMBIGUOUS,
        NO_MATCH,
        INVALID_RULE,
        SCOPE_MISSING,
        CONTENT_CHANGED
    }

    public record Range(String documentRevisionId, int startByte, int endByte) {}
    public record Evaluation(String commentId, String evaluatorVersion, Status status,
                             List<Range> ranges, List<String> diagnostics) {
        public Evaluation { ranges = List.copyOf(ranges); diagnostics = List.copyOf(diagnostics); }
    }

    private record RuleResult(Status status, List<Range> ranges, List<String> diagnostics) {}

    private SFMReviewSessionV1Kernel() {}

    public static List<String> derivedHashtags(String text) {
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        Set<String> tags = new LinkedHashSet<>();
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset);
            int cpLength = Character.charCount(cp);
            if (cp != '#') { offset += cpLength; continue; }
            if (offset > 0) {
                int previous = text.codePointBefore(offset);
                if (Character.isLetterOrDigit(previous) || previous == '_') { offset += cpLength; continue; }
            }
            int cursor = offset + 1;
            if (cursor < text.length() && text.charAt(cursor) == '"') {
                cursor++;
                StringBuilder value = new StringBuilder();
                boolean closed = false;
                while (cursor < text.length()) {
                    char character = text.charAt(cursor++);
                    if (character == '"') { closed = true; break; }
                    if (character == '\\' && cursor < text.length()) {
                        char escaped = text.charAt(cursor);
                        if (escaped == '\\' || escaped == '"') { value.append(escaped); cursor++; continue; }
                    }
                    value.append(character);
                }
                if (closed && !value.toString().isBlank()) tags.add("#\"" + normalizeTag(value.toString()) + "\"");
                offset = cursor;
                continue;
            }
            int start = cursor;
            while (cursor < text.length()) {
                int current = text.codePointAt(cursor);
                if (!(Character.isLetterOrDigit(current) || current == '_' || current == '-')) break;
                cursor += Character.charCount(current);
            }
            if (cursor > start) tags.add("#" + normalizeTag(text.substring(start, cursor)));
            offset = Math.max(cursor, offset + 1);
        }
        return List.copyOf(tags);
    }

    public static List<Evaluation> evaluateAll(SFMReviewSessionV1 session) {
        validateSession(session);
        Map<String, SFMReviewSessionV1.DocumentRevision> documents = documents(session);
        return session.comments().stream().map(comment -> evaluateComment(comment, documents)).toList();
    }

    public static Evaluation evaluateComment(SFMReviewSessionV1 session, SFMReviewSessionV1.Comment comment) {
        validateSession(session);
        return evaluateComment(comment, documents(session));
    }

    public static boolean isApprovalEffective(SFMReviewSessionV1 session, SFMReviewSessionV1.Comment comment,
                                              Evaluation evaluation) {
        List<String> hashtags = derivedHashtags(comment.text());
        String approval = normalizeSerializedHashtag(session.completionPolicy().approvalHashtag());
        boolean resolved = evaluation.status() == Status.RESOLVED_EXACTLY
                || evaluation.status() == Status.RESOLVED_WITH_RELOCATION;
        if (!resolved || !hashtags.contains(approval)) return false;
        return session.completionPolicy().blockingHashtags().stream()
                .map(SFMReviewSessionV1Kernel::normalizeSerializedHashtag)
                .noneMatch(hashtags::contains);
    }

    public static int overlapPairCount(List<Evaluation> evaluations) {
        int overlaps = 0;
        for (int left = 0; left < evaluations.size(); left++) {
            for (int right = left + 1; right < evaluations.size(); right++) {
                if (overlaps(evaluations.get(left).ranges(), evaluations.get(right).ranges())) overlaps++;
            }
        }
        return overlaps;
    }

    public static int utf8ByteToUtf16Index(String text, int byteOffset) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (!isBoundary(bytes, byteOffset)) throw new IllegalArgumentException("Offset is not a UTF-8 boundary: " + byteOffset);
        return new String(bytes, 0, byteOffset, StandardCharsets.UTF_8).length();
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Evaluation evaluateComment(SFMReviewSessionV1.Comment comment,
                                                Map<String, SFMReviewSessionV1.DocumentRevision> documents) {
        RuleResult result = evaluateRule(comment.selectionRule(), documents);
        return new Evaluation(comment.id(), EVALUATOR_VERSION, result.status(), result.ranges(), result.diagnostics());
    }

    private static RuleResult evaluateRule(SFMReviewSessionV1.SelectionRule rule,
                                           Map<String, SFMReviewSessionV1.DocumentRevision> documents) {
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) return evaluateLiteral(literal, documents);
        if (rule instanceof SFMReviewSessionV1.Union union) return evaluateSet(union.rules(), documents, SetOperation.UNION);
        if (rule instanceof SFMReviewSessionV1.Intersection intersection)
            return evaluateSet(intersection.rules(), documents, SetOperation.INTERSECTION);
        if (rule instanceof SFMReviewSessionV1.Difference difference) {
            RuleResult include = evaluateRule(difference.include(), documents);
            if (!resolved(include.status())) return include;
            List<Range> ranges = include.ranges();
            Status status = include.status();
            List<String> diagnostics = new ArrayList<>(include.diagnostics());
            for (SFMReviewSessionV1.SelectionRule excluded : difference.exclude()) {
                RuleResult child = evaluateRule(excluded, documents);
                if (!resolved(child.status())) return child;
                if (child.status() == Status.RESOLVED_WITH_RELOCATION) status = Status.RESOLVED_WITH_RELOCATION;
                ranges = difference(ranges, child.ranges());
                diagnostics.addAll(child.diagnostics());
            }
            if (ranges.isEmpty()) status = Status.NO_MATCH;
            return new RuleResult(status, ranges, diagnostics);
        }
        return new RuleResult(Status.INVALID_RULE, List.of(), List.of("Unsupported selection rule type"));
    }

    private enum SetOperation { UNION, INTERSECTION }

    private static RuleResult evaluateSet(List<SFMReviewSessionV1.SelectionRule> rules,
                                          Map<String, SFMReviewSessionV1.DocumentRevision> documents,
                                          SetOperation operation) {
        if (rules.isEmpty()) return new RuleResult(Status.INVALID_RULE, List.of(), List.of(operation + " requires rules"));
        List<Range> ranges = null;
        Status status = Status.RESOLVED_EXACTLY;
        List<String> diagnostics = new ArrayList<>();
        for (SFMReviewSessionV1.SelectionRule rule : rules) {
            RuleResult child = evaluateRule(rule, documents);
            if (!resolved(child.status())) return child;
            if (child.status() == Status.RESOLVED_WITH_RELOCATION) status = Status.RESOLVED_WITH_RELOCATION;
            ranges = ranges == null ? child.ranges()
                    : operation == SetOperation.UNION ? union(ranges, child.ranges()) : intersection(ranges, child.ranges());
            diagnostics.addAll(child.diagnostics());
        }
        ranges = normalize(ranges == null ? List.of() : ranges);
        if (ranges.isEmpty()) status = Status.NO_MATCH;
        return new RuleResult(status, ranges, diagnostics);
    }

    private static RuleResult evaluateLiteral(SFMReviewSessionV1.LiteralUtf8Range literal,
                                               Map<String, SFMReviewSessionV1.DocumentRevision> documents) {
        SFMReviewSessionV1.DocumentRevision document = documents.get(literal.documentRevisionId());
        if (document == null) return new RuleResult(Status.SCOPE_MISSING, List.of(),
                List.of("Document revision not found: " + literal.documentRevisionId()));
        byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
        if (literal.startByte() < 0 || literal.endByte() < literal.startByte() || literal.endByte() > bytes.length
                || !isBoundary(bytes, literal.startByte()) || !isBoundary(bytes, literal.endByte())) {
            return new RuleResult(Status.INVALID_RULE, List.of(), List.of("Range is outside the document or splits UTF-8"));
        }
        if (!validHash(literal.documentSha256()) || !validHash(literal.selectedTextSha256())
                || !validHash(document.sha256())) {
            return new RuleResult(Status.INVALID_RULE, List.of(), List.of("Hashes must be lowercase SHA-256 hex"));
        }
        String actualDocumentHash = sha256(bytes);
        String actualSelectedHash = sha256(java.util.Arrays.copyOfRange(bytes, literal.startByte(), literal.endByte()));
        Range original = new Range(document.id(), literal.startByte(), literal.endByte());
        if (actualDocumentHash.equals(document.sha256()) && actualDocumentHash.equals(literal.documentSha256())
                && actualSelectedHash.equals(literal.selectedTextSha256())) {
            return new RuleResult(Status.RESOLVED_EXACTLY, List.of(original), List.of());
        }
        int length = literal.endByte() - literal.startByte();
        List<Range> candidates = new ArrayList<>();
        for (int start = 0; start + length <= bytes.length; start++) {
            int end = start + length;
            if (!isBoundary(bytes, start) || !isBoundary(bytes, end)) continue;
            if (sha256(java.util.Arrays.copyOfRange(bytes, start, end)).equals(literal.selectedTextSha256()))
                candidates.add(new Range(document.id(), start, end));
        }
        if (candidates.size() == 1) return new RuleResult(Status.RESOLVED_WITH_RELOCATION, candidates,
                List.of("Document changed; selected-text witness relocated uniquely"));
        if (candidates.size() > 1) return new RuleResult(Status.AMBIGUOUS, candidates,
                List.of("Selected-text witness has " + candidates.size() + " matches"));
        return new RuleResult(Status.CONTENT_CHANGED, List.of(original),
                List.of("Document or selected-text SHA-256 witness changed"));
    }

    private static List<Range> union(List<Range> left, List<Range> right) {
        List<Range> result = new ArrayList<>(left); result.addAll(right); return normalize(result);
    }

    private static List<Range> intersection(List<Range> left, List<Range> right) {
        List<Range> result = new ArrayList<>();
        for (Range a : left) for (Range b : right) if (a.documentRevisionId().equals(b.documentRevisionId())) {
            int start = Math.max(a.startByte(), b.startByte()); int end = Math.min(a.endByte(), b.endByte());
            if (start < end) result.add(new Range(a.documentRevisionId(), start, end));
        }
        return normalize(result);
    }

    private static List<Range> difference(List<Range> include, List<Range> exclude) {
        List<Range> current = normalize(include);
        for (Range cut : normalize(exclude)) {
            List<Range> next = new ArrayList<>();
            for (Range range : current) {
                if (!range.documentRevisionId().equals(cut.documentRevisionId())
                        || cut.endByte() <= range.startByte() || cut.startByte() >= range.endByte()) next.add(range);
                else {
                    if (range.startByte() < cut.startByte()) next.add(new Range(range.documentRevisionId(), range.startByte(), cut.startByte()));
                    if (cut.endByte() < range.endByte()) next.add(new Range(range.documentRevisionId(), cut.endByte(), range.endByte()));
                }
            }
            current = next;
        }
        return normalize(current);
    }

    private static List<Range> normalize(List<Range> input) {
        List<Range> sorted = input.stream().filter(range -> range.startByte() < range.endByte())
                .sorted(Comparator.comparing(Range::documentRevisionId).thenComparingInt(Range::startByte)
                        .thenComparingInt(Range::endByte)).toList();
        List<Range> result = new ArrayList<>();
        for (Range range : sorted) {
            if (!result.isEmpty()) {
                Range previous = result.get(result.size() - 1);
                if (previous.documentRevisionId().equals(range.documentRevisionId())
                        && range.startByte() <= previous.endByte()) {
                    result.set(result.size() - 1, new Range(previous.documentRevisionId(), previous.startByte(),
                            Math.max(previous.endByte(), range.endByte())));
                    continue;
                }
            }
            result.add(range);
        }
        return List.copyOf(result);
    }

    private static boolean overlaps(List<Range> left, List<Range> right) {
        return left.stream().anyMatch(a -> right.stream().anyMatch(b -> a.documentRevisionId().equals(b.documentRevisionId())
                && Math.max(a.startByte(), b.startByte()) < Math.min(a.endByte(), b.endByte())));
    }

    private static boolean resolved(Status status) {
        return status == Status.RESOLVED_EXACTLY || status == Status.RESOLVED_WITH_RELOCATION;
    }

    private static boolean isBoundary(byte[] bytes, int offset) {
        return offset >= 0 && offset <= bytes.length && (offset == bytes.length || (bytes[offset] & 0xC0) != 0x80);
    }

    private static boolean validHash(String hash) { return hash != null && hash.matches("[0-9a-f]{64}"); }

    private static Map<String, SFMReviewSessionV1.DocumentRevision> documents(SFMReviewSessionV1 session) {
        Map<String, SFMReviewSessionV1.DocumentRevision> documents = new HashMap<>();
        for (SFMReviewSessionV1.RevisionLane lane : session.revisionLanes()) {
            for (SFMReviewSessionV1.DocumentRevision document : lane.before().documents()) putUnique(documents, document);
            for (SFMReviewSessionV1.DocumentRevision document : lane.after().documents()) putUnique(documents, document);
        }
        return documents;
    }

    private static void putUnique(Map<String, SFMReviewSessionV1.DocumentRevision> documents,
                                  SFMReviewSessionV1.DocumentRevision document) {
        if (documents.putIfAbsent(document.id(), document) != null)
            throw new IllegalArgumentException("Duplicate document revision id " + document.id());
    }

    private static void validateSession(SFMReviewSessionV1 session) {
        if (!SFMReviewSessionV1.SCHEMA.equals(session.schema())) throw new IllegalArgumentException("Unsupported schema");
        if (!SFMReviewSessionV1.COORDINATE_SYSTEM.equals(session.coordinateSystem()))
            throw new IllegalArgumentException("Unsupported coordinate system");
        for (SFMReviewSessionV1.DocumentRevision document : documents(session).values()) {
            if (!"utf-8".equals(document.encoding())) throw new IllegalArgumentException("Unsupported encoding " + document.encoding());
            String path = document.path();
            if (path.isBlank() || path.startsWith("/") || path.contains("\\")
                    || java.util.Arrays.asList(path.split("/", -1)).contains(".."))
                throw new IllegalArgumentException("Document path is not normalized repository-relative: " + path);
        }
    }

    private static String normalizeTag(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static String normalizeSerializedHashtag(String value) {
        if (value.startsWith("#\"") && value.endsWith("\"")) return "#\"" + normalizeTag(value.substring(2, value.length() - 1)) + "\"";
        return value.startsWith("#") ? "#" + normalizeTag(value.substring(1)) : "#" + normalizeTag(value);
    }
}
