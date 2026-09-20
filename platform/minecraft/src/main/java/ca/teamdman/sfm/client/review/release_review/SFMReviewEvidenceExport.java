package ca.teamdman.sfm.client.review.release_review;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure export preparation; neither browsing nor this dry run changes durable authority. */
public final class SFMReviewEvidenceExport {
    private SFMReviewEvidenceExport() { }

    public enum Policy {
        /** Retained comment evidence is self-contained; current targets still need their sources. */
        EMBED_COMMENT_EVIDENCE,
        /** Explicit repository-dependent alternative, never advertised as offline portable. */
        VERIFIED_GIT_REFERENCES
    }

    public record Prepared(String originalSha256, Policy policy, long originalBytes, long outputBytes,
                           long embeddedUtf8Bytes, int embeddedBodies, int gitDependentDocuments,
                           String output, SFMReleaseReviewLedgerV3 ledger) {
        public boolean commentEvidencePortable() { return gitDependentDocuments == 0; }
    }

    /**
     * verifiedGit must come from an exact commit/path/blob resolver, not a working-tree read.
     * An embedded body needs no external read when making a portable copy. Removing one
     * requires fresh exact Git evidence; non-Git documents keep their body unless
     * separately verified storage provenance is supplied explicitly.
     */
    public static Prepared prepare(String exactAuthority, Policy policy,
                                   Map<String, SFMReviewEvidenceTable.Observed> verifiedGit) {
        return prepare(exactAuthority, policy, verifiedGit, Map.of());
    }

    public static Prepared prepare(String exactAuthority, Policy policy,
                                   Map<String, SFMReviewEvidenceTable.Observed> verifiedGit,
                                   Map<String, SFMReviewGitEvidenceVerifier.Recognized> recognized) {
        java.util.Objects.requireNonNull(policy);
        java.util.Objects.requireNonNull(verifiedGit);
        var original = SFMReleaseReviewLedgerV3Codec.parse(exactAuthority);
        var storage = new TreeMap<String, SFMReviewEvidenceTable.GitStorage>();
        original.evidence().gitStorage().forEach(entry -> storage.put(entry.sha256(), entry));
        recognized.forEach((hash, proof) -> {
            if (!hash.equals(proof.original().document().sha256()) || original.evidence().documents().stream()
                    .noneMatch(document -> document.sha256().equals(hash)))
                throw new IllegalArgumentException("Unreferenced recognized Git evidence");
            verifyBlob(proof.original().text(), proof.storage().blob());
            storage.put(hash, new SFMReviewEvidenceTable.GitStorage(hash, proof.storagePath(), proof.storage()));
        });
        var bodies = new TreeMap<String, SFMReviewEvidenceTable.Content>();
        original.evidence().contents().forEach(content -> bodies.put(content.sha256(), content));
        if (policy == Policy.EMBED_COMMENT_EVIDENCE) {
            for (var document : original.evidence().documents()) {
                if (bodies.containsKey(document.sha256())) continue;
                var observed = witness(document, verifiedGit, recognized, storage);
                bodies.put(document.sha256(), new SFMReviewEvidenceTable.Content(document.sha256(), observed.text()));
            }
        } else {
            // A content hash may be shared by Git and non-Git documents. Never discard
            // a shared body without verified storage for every dependent identity.
            var requiredBodies = new java.util.HashSet<String>();
            for (var document : original.evidence().documents()) {
                if (document.git().isEmpty() && !recognized.containsKey(document.sha256())) {
                    if (!bodies.containsKey(document.sha256()))
                        throw new IllegalArgumentException("Stored Git evidence must be reverified before export");
                    requiredBodies.add(document.sha256());
                } else witness(document, verifiedGit, recognized, storage);
            }
            bodies.keySet().retainAll(requiredBodies);
        }
        var evidence = new SFMReviewEvidenceTable(List.copyOf(bodies.values()), original.evidence().documents(),
                List.copyOf(storage.values()));
        var exported = new SFMReleaseReviewLedgerV3(original.targets(), original.state(), evidence);
        String output = SFMReleaseReviewLedgerV3Codec.write(exported);
        int dependent = (int) evidence.documents().stream().filter(document -> !bodies.containsKey(document.sha256())).count();
        return new Prepared(SFMReleaseReviewKernel.sha256(exactAuthority.getBytes(StandardCharsets.UTF_8)), policy,
                exactAuthority.getBytes(StandardCharsets.UTF_8).length, output.getBytes(StandardCharsets.UTF_8).length,
                bodies.values().stream().mapToLong(content -> content.text().getBytes(StandardCharsets.UTF_8).length).sum(),
                bodies.size(), dependent, output, exported);
    }

    private static SFMReviewEvidenceTable.Observed witness(SFMReviewEvidenceTable.Document document,
            Map<String, SFMReviewEvidenceTable.Observed> git,
            Map<String, SFMReviewGitEvidenceVerifier.Recognized> recognized,
            Map<String, SFMReviewEvidenceTable.GitStorage> storage) {
        if (document.git().isPresent()) return requireGitWitness(document, git);
        var proof = recognized.get(document.sha256());
        var location = storage.get(document.sha256());
        if (proof == null || location == null || !proof.storage().equals(location.git())
                || !proof.storagePath().equals(location.path()))
            throw new IllegalArgumentException("Exact Git storage evidence unavailable for export");
        verifyBlob(proof.original().text(), location.git().blob());
        return new SFMReviewEvidenceTable.Observed(document, proof.original().text());
    }

    private static SFMReviewEvidenceTable.Observed requireGitWitness(SFMReviewEvidenceTable.Document document,
                                                                     Map<String, SFMReviewEvidenceTable.Observed> witnesses) {
        var observed = witnesses.get(document.revisionId());
        if (document.git().isEmpty() || observed == null || !observed.document().equals(document))
            throw new IllegalArgumentException("Exact Git evidence unavailable for export: " + document.revisionId());
        // Observed validates exact UTF-8 and SHA256. Independently verify Git's blob
        // identity as well; the I/O resolver is responsible for the commit/path link.
        verifyBlob(observed.text(), document.git().orElseThrow().blob());
        return observed;
    }

    private static void verifyBlob(String text, String expectedBlob) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII));
            String blob = java.util.HexFormat.of().formatHex(digest.digest(bytes));
            if (!blob.equals(expectedBlob))
                throw new IllegalArgumentException("Export evidence does not match the recorded Git blob");
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-1 unavailable", impossible);
        }
    }
}
