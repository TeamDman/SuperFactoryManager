package ca.teamdman.sfm.client.review.release_review;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable durable evidence closure, separate from transient source observations. */
public record SFMReviewEvidenceTable(List<Content> contents, List<Document> documents, List<GitStorage> gitStorage) {
    public SFMReviewEvidenceTable(List<Content> contents, List<Document> documents) {
        this(contents, documents, List.of());
    }
    public static final SFMReviewEvidenceTable EMPTY = new SFMReviewEvidenceTable(List.of(), List.of());

    public record Content(String sha256, String text) {
        public Content {
            Objects.requireNonNull(text);
            requireHash(sha256, 64);
            if (!hash(text.getBytes(StandardCharsets.UTF_8)).equals(sha256))
                throw new IllegalArgumentException("Evidence content hash mismatch");
            // Java's encoder otherwise replaces unpaired surrogates silently.
            if (!new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).equals(text))
                throw new IllegalArgumentException("Evidence text is not representable as exact UTF-8");
        }
    }

    public record GitReference(String repositoryId, String commit, String blob) {
        public GitReference {
            requireText(repositoryId);
            requireHash(commit, 40);
            requireHash(blob, 40);
        }
    }

    public record Document(String revisionId, String path, String sha256, Optional<GitReference> git) {
        public Document {
            requireText(revisionId);
            requireText(path);
            requireHash(sha256, 64);
            Objects.requireNonNull(git);
        }
    }

    /** Storage location for exact bytes; never part of a comment's document identity. */
    public record GitStorage(String sha256, String path, GitReference git) {
        public GitStorage {
            requireHash(sha256, 64);
            Objects.requireNonNull(git);
            requireText(path);
            if (path.startsWith("/") || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0
                    || Arrays.stream(path.split("/", -1)).anyMatch(p -> p.isEmpty() || p.equals(".") || p.equals("..")))
                throw new IllegalArgumentException("Git storage path must be repository-relative");
        }
    }

    /** Exact bytes already shown to the user; capture never reads a filesystem. */
    public record Observed(Document document, String text) {
        public Observed {
            Objects.requireNonNull(document);
            new Content(document.sha256(), text);
        }
    }

    public SFMReviewEvidenceTable {
        contents = List.copyOf(contents);
        documents = List.copyOf(documents);
        gitStorage = List.copyOf(gitStorage);
        var storageHashes = new java.util.HashSet<String>();
        for (var storage : gitStorage) if (!storageHashes.add(storage.sha256()))
            throw new IllegalArgumentException("Duplicate Git storage hash");
        var byHash = new TreeMap<String, Content>();
        for (var content : contents) {
            if (byHash.put(content.sha256(), content) != null)
                throw new IllegalArgumentException("Duplicate evidence content");
        }
        var byId = new TreeMap<String, Document>();
        var referencedHashes = new java.util.HashSet<String>();
        for (var document : documents) {
            referencedHashes.add(document.sha256());
            if (byId.put(document.revisionId(), document) != null)
                throw new IllegalArgumentException("Duplicate evidence document identity");
            if (document.git().isEmpty() && !byHash.containsKey(document.sha256()) && !storageHashes.contains(document.sha256()))
                throw new IllegalArgumentException("Non-Git evidence requires embedded content");
        }
        for (var content : contents) {
            if (!referencedHashes.contains(content.sha256()))
                throw new IllegalArgumentException("Unreferenced embedded evidence");
        }
        if (!referencedHashes.containsAll(storageHashes))
            throw new IllegalArgumentException("Unreferenced Git storage");
    }

    /** Retain only a comment's full selector closure and its literal selection witness. */
    public SFMReviewEvidenceTable capture(
            SFMReleaseReviewV1.SelectorProposal proposal, Map<String, Observed> observation
    ) {
        Objects.requireNonNull(proposal);
        var requested = new ArrayList<SFMReviewSessionV1.LiteralUtf8Range>();
        collect(proposal.selectionRule(), requested, 0);
        var capturedContents = new TreeMap<String, Content>();
        contents.forEach(content -> capturedContents.put(content.sha256(), content));
        var capturedDocuments = new TreeMap<String, Document>();
        documents.forEach(document -> capturedDocuments.put(document.revisionId(), document));
        for (var range : proposal.literalWitness().ranges()) {
            var source = requireObserved(observation, range.documentRevisionId(), range.documentSha256());
            validateRange(source.text(), range.startByte(), range.endByte());
            retain(source, capturedContents, capturedDocuments);
        }
        for (var range : requested) {
            var source = requireObserved(observation, range.documentRevisionId(), range.documentSha256());
            byte[] slice = validateRange(source.text(), range.startByte(), range.endByte());
            if (!hash(slice).equals(range.selectedTextSha256()))
                throw new IllegalArgumentException("Selected evidence hash mismatch");
            retain(source, capturedContents, capturedDocuments);
        }
        // Retention is append-only here; unchanged sizes mean all requested identities
        // were already present and checked by retain. Keep the immutable table itself.
        if (capturedContents.size() == contents.size() && capturedDocuments.size() == documents.size())
            return this;
        return new SFMReviewEvidenceTable(List.copyOf(capturedContents.values()),
                List.copyOf(capturedDocuments.values()), gitStorage);
    }

    /** Verify saved selector closure without resolving Git or reading the working tree. */
    public void validateRetained(SFMReleaseReviewV1.SelectorProposal proposal) {
        var required = new java.util.ArrayList<SFMReviewSessionV1.LiteralUtf8Range>();
        collect(proposal.selectionRule(), required, 0);
        var byId = new java.util.HashMap<String, Document>();
        documents.forEach(document -> byId.put(document.revisionId(), document));
        var bodies = new java.util.HashMap<String, String>();
        contents.forEach(content -> bodies.put(content.sha256(), content.text()));
        for (var range : proposal.literalWitness().ranges())
            requireRetained(byId, bodies, range.documentRevisionId(), range.documentSha256(), range.startByte(), range.endByte());
        for (var range : required) {
            byte[] slice = requireRetained(byId, bodies, range.documentRevisionId(), range.documentSha256(),
                    range.startByte(), range.endByte());
            if (slice != null && !hash(slice).equals(range.selectedTextSha256()))
                throw new IllegalArgumentException("Retained selected evidence hash mismatch");
        }
    }

    private static byte[] requireRetained(Map<String, Document> documents, Map<String, String> bodies,
                                          String id, String sha256, int start, int end) {
        var document = documents.get(id);
        if (document == null || !document.sha256().equals(sha256) || start < 0 || end < start)
            throw new IllegalArgumentException("Saved comment is missing exact retained evidence: " + id);
        String body = bodies.get(sha256);
        return body == null ? null : validateRange(body, start, end);
    }

    private static Observed requireObserved(Map<String, Observed> observation, String id, String sha256) {
        var source = observation.get(id);
        if (source == null || !source.document().revisionId().equals(id)
                || !source.document().sha256().equals(sha256))
            throw new IllegalArgumentException("Exact displayed evidence is unavailable: " + id);
        return source;
    }

    private static void retain(Observed source, Map<String, Content> contents, Map<String, Document> documents) {
        var existing = documents.putIfAbsent(source.document().revisionId(), source.document());
        if (existing != null && !existing.equals(source.document()))
            throw new IllegalArgumentException("Conflicting immutable evidence identity");
        if (source.document().git().isEmpty()) {
            var prior = contents.get(source.document().sha256());
            // Observed and Content have already validated their hashes. Compare the
            // exact text as well; a reused hash must never stand in for byte equality.
            if (prior != null && !prior.text().equals(source.text()))
                throw new IllegalArgumentException("Conflicting evidence bytes for hash");
            if (prior == null)
                contents.put(source.document().sha256(), new Content(source.document().sha256(), source.text()));
        }
    }

    private static byte[] validateRange(String text, int start, int end) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (start < 0 || end < start || end > bytes.length || !boundary(bytes, start) || !boundary(bytes, end))
            throw new IllegalArgumentException("Evidence range is not an exact UTF-8 range");
        return Arrays.copyOfRange(bytes, start, end);
    }

    private static boolean boundary(byte[] bytes, int offset) {
        return offset == bytes.length || (bytes[offset] & 0xC0) != 0x80;
    }

    private static void collect(SFMReviewSessionV1.SelectionRule rule,
                                List<SFMReviewSessionV1.LiteralUtf8Range> ranges, int depth) {
        if (depth > 64 || ranges.size() >= 100_000)
            throw new IllegalArgumentException("Evidence selector exceeds capture bounds");
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) ranges.add(literal);
        else if (rule instanceof SFMReviewSessionV1.Union union)
            union.rules().forEach(child -> collect(child, ranges, depth + 1));
        else if (rule instanceof SFMReviewSessionV1.Intersection intersection)
            intersection.rules().forEach(child -> collect(child, ranges, depth + 1));
        else if (rule instanceof SFMReviewSessionV1.Difference difference) {
            collect(difference.include(), ranges, depth + 1);
            difference.exclude().forEach(child -> collect(child, ranges, depth + 1));
        } else throw new IllegalArgumentException("Unsupported evidence selector");
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing evidence identity");
    }

    private static void requireHash(String value, int length) {
        if (value == null || !value.matches("[0-9a-f]{" + length + "}"))
            throw new IllegalArgumentException("Invalid evidence hash");
    }

    private static String hash(byte[] bytes) { return SFMReleaseReviewKernel.sha256(bytes); }
}
