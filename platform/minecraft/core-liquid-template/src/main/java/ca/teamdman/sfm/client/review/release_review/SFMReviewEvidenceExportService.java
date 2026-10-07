package ca.teamdman.sfm.client.review.release_review;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;

/** Background-only export acquisition. Exact authority, bounded Git reads, no writes. */
public final class SFMReviewEvidenceExportService {
    private SFMReviewEvidenceExportService() { }
    public record Preview(Path source, Path destination, SFMReviewEvidenceExport.Prepared prepared,
                          boolean repositoryHintsRebased) { }

    public static Preview preview(Path source, Path destination, String expectedHash,
                                  SFMReviewEvidenceExport.Policy policy) throws Exception {
        source = source.toRealPath();
        destination = destination.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent == null) throw new java.io.IOException("Export destination requires a parent directory");
        destination = parent.toRealPath().resolve(destination.getFileName());
        if (source.equals(destination) || Files.exists(destination, java.nio.file.LinkOption.NOFOLLOW_LINKS))
            throw new java.io.IOException("Export requires a new file, never the original or an existing destination");
        byte[] bytes;
        try (var stream = Files.newInputStream(source)) { bytes = stream.readNBytes(64 * 1024 * 1024 + 1); }
        if (bytes.length > 64 * 1024 * 1024) throw new java.io.IOException("Export authority exceeds the 64 MiB read limit");
        if (!SFMReleaseReviewKernel.sha256(bytes).equals(expectedHash))
            throw new java.io.IOException("Review changed since it was opened; reopen before exporting");
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        var ledger = SFMReleaseReviewLedgerV3Codec.parse(text);
        var bodies = new HashSet<String>();
        ledger.evidence().contents().forEach(body -> bodies.add(body.sha256()));
        var witnesses = new HashMap<String, SFMReviewEvidenceTable.Observed>();
        var recognized = new HashMap<String, SFMReviewGitEvidenceVerifier.Recognized>();
        var storage = new HashMap<String, SFMReviewEvidenceTable.GitStorage>();
        ledger.evidence().gitStorage().forEach(entry -> storage.put(entry.sha256(), entry));
        var pinnedHeads = new HashMap<Path, String>();
        long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
        long acquired = 0;
        int attempts = 0;
        for (var document : ledger.evidence().documents()) {
            boolean needed = policy == SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE
                    ? !bodies.contains(document.sha256()) : true;
            if (!needed) continue;
            if (document.git().isEmpty() && recognized.containsKey(document.sha256())) continue;
            if (++attempts > 4096) throw new java.io.IOException("Export Git acquisition exceeded 4096 attempts");
            var location = storage.get(document.sha256());
            var git = document.git().orElse(location == null ? null : location.git());
            // For previously embedded working-tree captures, only infer a storage
            // repository when the authority has exactly one repository identity/root.
            // Ambiguous captures keep their body rather than guessing provenance.
            if (git == null) {
                var candidates = new HashSet<String>();
                for (var lane : ledger.targets()) candidates.add(lane.repositoryId() + "\0" + lane.rootHint());
                if (candidates.size() != 1) continue;
                var lane = ledger.targets().get(0);
                Path root = source.getParent().resolve(lane.rootHint()).toAbsolutePath().normalize();
                try {
                    String commit = pinnedHeads.get(root);
                    if (commit == null) {
                        commit = SFMReviewGitEvidenceVerifier.pinHead(root, remaining(deadline));
                        pinnedHeads.put(root, commit);
                    }
                    var proof = SFMReviewGitEvidenceVerifier.recognize(document, lane.repositoryId(), commit,
                            document.path(), root, remaining(deadline));
                    recognized.put(document.sha256(), proof);
                    acquired += proof.original().text().getBytes(StandardCharsets.UTF_8).length;
                } catch (Exception unavailable) {
                    // Recognition is opportunistic only while original exact bytes
                    // remain embedded. Mandatory stored references never use this path.
                    if (!bodies.contains(document.sha256())) throw unavailable;
                    remaining(deadline); // Do not silently extend the total deadline.
                }
                if (acquired > 64 * 1024 * 1024) throw new java.io.IOException("Export Git evidence exceeds 64 MiB");
                continue;
            }
            var roots = new HashSet<Path>();
            for (var lane : ledger.targets()) if (lane.repositoryId().equals(git.repositoryId()))
                roots.add(source.getParent().resolve(lane.rootHint()).toAbsolutePath().normalize());
            if (roots.size() != 1) throw new java.io.IOException("Missing or ambiguous repository hint for " + git.repositoryId());
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || witnesses.size() >= 4096)
                throw new java.io.IOException("Export Git evidence acquisition exceeded its bounded budget");
            var timeout = Duration.ofNanos(Math.min(remaining, Duration.ofSeconds(10).toNanos()));
            SFMReviewEvidenceTable.Observed observed;
            if (document.git().isPresent()) {
                observed = SFMReviewGitEvidenceVerifier.verify(document, roots.iterator().next(), timeout);
            } else {
                var proof = SFMReviewGitEvidenceVerifier.recognize(document, git.repositoryId(), git.commit(),
                        location.path(), roots.iterator().next(), timeout);
                if (!proof.storage().equals(git)) throw new java.io.IOException("Stored Git blob identity mismatch");
                recognized.put(document.sha256(), proof);
                observed = proof.original();
            }
            acquired += observed.text().getBytes(StandardCharsets.UTF_8).length;
            if (acquired > 64 * 1024 * 1024) throw new java.io.IOException("Export Git evidence exceeds 64 MiB");
            witnesses.put(document.revisionId(), observed);
            if (policy == SFMReviewEvidenceExport.Policy.EMBED_COMMENT_EVIDENCE) bodies.add(document.sha256());
        }
        var prepared = SFMReviewEvidenceExport.prepare(text, policy, witnesses, recognized);
        boolean rebased = !source.getParent().equals(destination.getParent());
        if (rebased) {
            Path origin = source.getParent();
            var targets = ledger.targets().stream().map(lane -> new SFMReleaseReviewLedgerV3.TargetLane(
                    lane.id(), lane.repositoryId(), origin.resolve(lane.rootHint()).normalize().toString(),
                    lane.beforeCommit(), lane.afterCommit(), lane.scopePaths(), lane.excludedPaths(), lane.includeUntracked())).toList();
            var relocated = new SFMReleaseReviewLedgerV3(targets, prepared.ledger().state(), prepared.ledger().evidence());
            String output = SFMReleaseReviewLedgerV3Codec.write(relocated);
            prepared = new SFMReviewEvidenceExport.Prepared(prepared.originalSha256(), prepared.policy(), prepared.originalBytes(),
                    output.getBytes(StandardCharsets.UTF_8).length, prepared.embeddedUtf8Bytes(), prepared.embeddedBodies(),
                    prepared.gitDependentDocuments(), output, relocated);
        }
        if (prepared.outputBytes() > 64 * 1024 * 1024)
            throw new java.io.IOException("Export exceeds the 64 MiB portable evidence reader limit");
        return new Preview(source, destination, prepared, rebased);
    }
    private static Duration remaining(long deadline) throws java.io.IOException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new java.io.IOException("Export Git evidence acquisition exceeded its time limit");
        return Duration.ofNanos(Math.min(nanos, Duration.ofSeconds(10).toNanos()));
    }
}
