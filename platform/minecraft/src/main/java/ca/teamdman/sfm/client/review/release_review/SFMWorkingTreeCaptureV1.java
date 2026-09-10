package ca.teamdman.sfm.client.review.release_review;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Immutable source manifest. Validation and identity computation perform no filesystem IO. */
public record SFMWorkingTreeCaptureV1(
        String schema, String id, String observedHeadCommit, String observedHeadTree,
        long capturedAtUnixMs, String consistency, List<String> scopePaths,
        List<String> excludedPaths, boolean includeUntracked, List<Entry> entries
) {
    public static final String SCHEMA = "sfm.release-review.working-tree-capture/1";
    public static final String ID_PREFIX = "working-tree:sha256:";
    private static final Comparator<String> UTF8_ORDER = (left, right) ->
            Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));

    public SFMWorkingTreeCaptureV1 {
        if (!SCHEMA.equals(schema) || !"verified_two_pass".equals(consistency) || capturedAtUnixMs < 0)
            throw new IllegalArgumentException("Unsupported working-tree capture schema/consistency/time");
        hex(observedHeadCommit, 40);
        hex(observedHeadTree, 40);
        scopePaths = List.copyOf(scopePaths);
        excludedPaths = List.copyOf(excludedPaths);
        entries = List.copyOf(entries);
        if (scopePaths.isEmpty() || entries.size() > 100_000)
            throw new IllegalArgumentException("Working-tree capture scope/count is invalid");
        prefixes(scopePaths);
        prefixes(excludedPaths);
        var scopes = Set.copyOf(scopePaths);
        var exclusions = Set.copyOf(excludedPaths);
        String previous = null;
        long total = 0;
        for (var entry : entries) {
            if (previous != null && UTF8_ORDER.compare(previous, entry.path()) >= 0)
                throw new IllegalArgumentException("Capture entries must be sorted and unique");
            previous = entry.path();
            if (!inPrefixes(scopes, entry.path())
                    || inPrefixes(exclusions, entry.path())
                    || (!entry.tracked() && !includeUntracked))
                throw new IllegalArgumentException("Capture entry is outside its source policy: " + entry.path());
            total += entry.byteLength().orElse(0L);
        }
        if (total > 256L * 1024 * 1024) throw new IllegalArgumentException("Capture exceeds aggregate read limit");
        if (!Objects.equals(id, computedId(observedHeadCommit, observedHeadTree, scopePaths, excludedPaths, includeUntracked, entries)))
            throw new IllegalArgumentException("Working-tree capture identity mismatch");
    }

    public record Entry(String path, String kind, boolean tracked, Optional<Long> byteLength,
                        Optional<String> sha256, boolean executable, String materialization,
                        Optional<String> documentRevisionId, Optional<String> diagnostic) {
        public Entry {
            validatePath(path, false);
            Objects.requireNonNull(byteLength);
            Objects.requireNonNull(sha256);
            Objects.requireNonNull(documentRevisionId);
            Objects.requireNonNull(diagnostic);
            if (documentRevisionId.filter(String::isBlank).isPresent() || diagnostic.filter(String::isBlank).isPresent())
                throw new IllegalArgumentException("Empty capture entry document/diagnostic");
            boolean document = documentRevisionId.isPresent();
            switch (kind) {
                case "regular_file" -> {
                    hex(sha256.orElseThrow(() -> new IllegalArgumentException("Missing file hash")), 64);
                    long size = byteLength.orElseThrow(() -> new IllegalArgumentException("Missing file size"));
                    if (size < 0 || size > 64L * 1024 * 1024)
                        throw new IllegalArgumentException("Capture file exceeds read limit");
                    boolean valid = switch (materialization) {
                        case "utf8" -> document && size <= 4L * 1024 * 1024;
                        case "unchanged" -> !document;
                        case "binary" -> !document && diagnostic.isPresent();
                        case "oversized" -> !document && diagnostic.isPresent() && size > 4L * 1024 * 1024;
                        default -> false;
                    };
                    if (!valid) throw new IllegalArgumentException("Invalid regular-file materialization");
                }
                case "deleted", "symlink", "gitlink" -> {
                    if (sha256.isPresent() || byteLength.isPresent() || document || executable
                            || !"unavailable".equals(materialization) || diagnostic.isEmpty()
                            || (kind.equals("deleted") && !tracked))
                        throw new IllegalArgumentException("Invalid absent/unsupported capture entry");
                }
                default -> throw new IllegalArgumentException("Unknown capture entry kind");
            }
        }
    }

    public void validateAgainst(ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.RevisionLane lane,
                                SFMReleaseReviewV1 review) {
        if (!id.equals(lane.after().id())) throw new IllegalArgumentException("Capture snapshot identity mismatch");
        var byPath = new HashMap<String, Entry>();
        entries.forEach(entry -> byPath.put(entry.path(), entry));
        var documents = new HashMap<String, ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.DocumentRevision>();
        lane.after().documents().forEach(document -> documents.put(document.path(), document));
        for (var document : lane.after().documents()) {
            var entry = byPath.get(document.path());
            if (entry == null || !entry.materialization().equals("utf8")
                    || !entry.documentRevisionId().equals(Optional.of(document.id()))
                    || !entry.sha256().equals(Optional.of(document.sha256()))
                    || entry.byteLength().orElse(-1L) != document.text().getBytes(StandardCharsets.UTF_8).length)
                throw new IllegalArgumentException("Capture manifest disagrees with embedded bytes: " + document.path());
        }
        var represented = new HashSet<String>();
        var beforePaths = new HashSet<String>();
        lane.before().documents().forEach(document -> beforePaths.add(document.path()));
        for (var unit : review.reviewUnits()) if (unit.laneId().equals(lane.id())) {
            unit.pathBefore().ifPresent(represented::add);
            unit.pathAfter().ifPresent(represented::add);
        }
        for (var entry : entries) {
            if (entry.materialization().equals("utf8") && !documents.containsKey(entry.path()))
                throw new IllegalArgumentException("Captured UTF-8 document is missing: " + entry.path());
            boolean changed = !entry.materialization().equals("unchanged")
                    && (!entry.kind().equals("deleted") || beforePaths.contains(entry.path()));
            if (changed && !represented.contains(entry.path()))
                throw new IllegalArgumentException("Capture change has no review unit: " + entry.path());
        }
        for (var path : represented) if (!byPath.containsKey(path))
            throw new IllegalArgumentException("Review unit is outside captured scope: " + path);
        var corpusPaths = new HashSet<String>();
        for (var corpus : review.corpusDocuments()) {
            if (!corpus.laneId().equals(lane.id()) || corpus.snapshotSide() != SFMReleaseReviewV1.SnapshotSide.AFTER) continue;
            var entry = byPath.get(corpus.path());
            corpusPaths.add(corpus.path());
            if (entry == null || entry.kind().equals("deleted") || entry.materialization().equals("unchanged")
                    || !corpus.sourceOwner().equals(id)
                    || !corpus.sourceLocator().equals("working-tree-capture://" + id.substring(ID_PREFIX.length()) + "/" + corpus.path())
                    || (entry.sha256().isPresent() && !entry.sha256().get().equals(corpus.sha256()))
                    || (entry.materialization().equals("utf8")
                    != (corpus.materialization() == SFMReleaseReviewV1.Materialization.COMPLETE)))
                throw new IllegalArgumentException("Capture corpus source witness mismatch: " + corpus.path());
        }
        for (var entry : entries) if (!entry.kind().equals("deleted") && !entry.materialization().equals("unchanged")
                && !corpusPaths.contains(entry.path()))
            throw new IllegalArgumentException("Captured after corpus is missing: " + entry.path());
    }

    public static String computedId(String head, String tree, List<String> scopes, List<String> excludes,
                                    boolean untracked, List<Entry> entries) {
        var bytes = new ByteArrayOutputStream();
        bytes.writeBytes((SCHEMA + "\n").getBytes(StandardCharsets.UTF_8));
        frame(bytes, head);
        frame(bytes, tree);
        frame(bytes, Integer.toString(scopes.size()));
        scopes.forEach(value -> frame(bytes, value));
        frame(bytes, Integer.toString(excludes.size()));
        excludes.forEach(value -> frame(bytes, value));
        frame(bytes, Boolean.toString(untracked));
        frame(bytes, Integer.toString(entries.size()));
        for (var entry : entries) {
            frame(bytes, entry.path());
            frame(bytes, entry.kind());
            frame(bytes, Boolean.toString(entry.tracked()));
            frame(bytes, entry.byteLength().map(Object::toString).orElse("-"));
            frame(bytes, entry.sha256().orElse("-"));
            frame(bytes, Boolean.toString(entry.executable()));
            frame(bytes, entry.materialization());
        }
        try {
            return ID_PREFIX + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void frame(ByteArrayOutputStream bytes, String text) {
        var value = text.getBytes(StandardCharsets.UTF_8);
        bytes.writeBytes((value.length + ":").getBytes(StandardCharsets.UTF_8));
        bytes.writeBytes(value);
        bytes.write('\n');
    }

    public static boolean contains(String prefix, String path) {
        return prefix.equals(".") || path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean inPrefixes(Set<String> prefixes, String path) {
        if (prefixes.contains(".") || prefixes.contains(path)) return true;
        for (int index = path.indexOf('/'); index >= 0; index = path.indexOf('/', index + 1))
            if (prefixes.contains(path.substring(0, index))) return true;
        return false;
    }

    private static void validatePath(String path, boolean prefix) {
        Objects.requireNonNull(path);
        if (prefix && path.equals(".")) return;
        if (path.getBytes(StandardCharsets.UTF_8).length > 4096
                || path.indexOf('\\') >= 0 || path.indexOf(':') >= 0 || path.indexOf('\0') >= 0)
            throw new IllegalArgumentException("Unsafe capture path: " + path);
        for (String part : path.split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.equalsIgnoreCase(".git"))
                throw new IllegalArgumentException("Unsafe capture path: " + path);
        for (int i = 0; i < path.length(); i++) {
            char ch = path.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < path.length() && Character.isLowSurrogate(path.charAt(i + 1))) i++;
            else if (Character.isSurrogate(ch)) throw new IllegalArgumentException("Invalid Unicode capture path");
        }
    }

    private static void prefixes(List<String> paths) {
        if (paths.size() > 100_000) throw new IllegalArgumentException("Too many capture prefixes");
        var seen = new HashSet<String>();
        for (int i = 0; i < paths.size(); i++) {
            var path = paths.get(i);
            validatePath(path, true);
            if (i > 0 && UTF8_ORDER.compare(paths.get(i - 1), path) >= 0)
                throw new IllegalArgumentException("Capture prefixes must be sorted and unique");
            if (inPrefixes(seen, path))
                throw new IllegalArgumentException("Redundant capture prefix");
            seen.add(path);
        }
    }

    private static void hex(String value, int length) {
        if (value == null || value.length() != length || !value.matches("[0-9a-f]+"))
            throw new IllegalArgumentException("Invalid lowercase capture hash");
    }
}
