package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Read-only explanation over already acquired authority and observation; never resolves source paths. */
public final class SFMReviewStorageInspection {
    private SFMReviewStorageInspection() { }

    /** Explicit user-requested authority read only; no source resolution and no writes. */
    public static String read(java.nio.file.Path path, String expectedHash,
                              SFMReleaseReviewV1 observation, Optional<String> documentId) throws java.io.IOException {
        int maximum = 256 * 1024 * 1024;
        byte[] bytes;
        try (var input = java.nio.file.Files.newInputStream(path)) {
            bytes = input.readNBytes(maximum + 1);
        }
        if (bytes.length > maximum) throw new java.io.IOException("Review exceeds bounded inspection size");
        if (!SFMReleaseReviewKernel.sha256(bytes).equals(expectedHash))
            throw new java.io.IOException("Review file changed since it was opened; reopen it before inspecting storage");
        String authority = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        return describe(authority, observation, documentId);
    }

    public static String describe(String authority, SFMReleaseReviewV1 observation, Optional<String> documentId) {
        var root = JsonParser.parseString(authority).getAsJsonObject();
        String schema = root.get("schema").getAsString();
        StringBuilder text = new StringBuilder("schema: sfm.review-storage-inspection/1\n");
        text.append("storage-schema: ").append(schema).append('\n');
        text.append("authority-utf8-bytes: ").append(bytes(authority)).append('\n');
        text.append("authority-sha256: ").append(SFMReleaseReviewKernel.sha256(authority.getBytes(StandardCharsets.UTF_8))).append('\n');
        text.append("inspection: read-only; source files and Git objects are not read by this report\n");
        if (!SFMReleaseReviewLedgerV3.SCHEMA.equals(schema)) {
            SFMReleaseReviewV1Codec.parse(authority);
            text.append("storage-model: legacy frozen review; source corpus is part of its serialized snapshot\n")
                    .append("This report does not convert, retarget, or compact the original review.\n");
            documentId.ifPresent(id -> {
                if (!appendObserved(text, observation, id))
                    throw new IllegalArgumentException("Document identity is absent from this review: " + id);
            });
            return text.toString();
        }
        var ledger = SFMReleaseReviewLedgerV3Codec.parse(authority);
        text.append("storage-model: single-file target ledger; browsing is transient\n")
                .append("durable-human-comments: ").append(ledger.state().reviewSession().comments().size()).append('\n')
                .append("retained-document-identities: ").append(ledger.evidence().documents().size()).append('\n')
                .append("deduplicated-content-bodies: ").append(ledger.evidence().contents().size()).append('\n')
                .append("separate-git-storage-references: ").append(ledger.evidence().gitStorage().size()).append('\n');
        long raw = 0, encoded = 0;
        for (var content : ledger.evidence().contents()) {
            raw += bytes(content.text());
            encoded += bytes(new JsonPrimitive(content.text()).toString());
        }
        text.append("embedded-source-utf8-bytes: ").append(raw).append('\n')
                .append("embedded-text-json-string-bytes: ").append(encoded).append('\n')
                .append("The JSON-string count includes quotes and escapes, not hashes, object syntax, or indentation.\n")
                .append("Equal source bytes are stored once even when several document identities or comments use them.\n");
        for (var target : ledger.targets()) {
            text.append("target: ").append(target.id()).append(" · ").append(target.beforeCommit())
                    .append(" -> ").append(target.afterCommit().orElse("live working tree")).append('\n');
        }
        if (documentId.isPresent()) {
            String id = documentId.orElseThrow();
            var retained = ledger.evidence().documents().stream().filter(document -> document.revisionId().equals(id)).findFirst();
            boolean observed = appendObserved(text, observation, id);
            if (retained.isPresent()) {
                var document = retained.orElseThrow();
                text.append("retained-path: ").append(document.path()).append('\n')
                        .append("retained-sha256: ").append(document.sha256()).append('\n');
                if (document.git().isPresent()) {
                    var git = document.git().orElseThrow();
                    text.append("storage: Git reference; repository objects required\n")
                            .append("repository: ").append(git.repositoryId()).append('\n')
                            .append("commit: ").append(git.commit()).append('\n')
                            .append("blob: ").append(git.blob()).append('\n');
                } else {
                    var content = ledger.evidence().contents().stream().filter(value -> value.sha256().equals(document.sha256())).findFirst();
                    if (content.isPresent()) {
                        text.append("storage: embedded exact non-Git source; retained when a human comment targeted it\n")
                                .append("document-source-utf8-bytes: ").append(bytes(content.orElseThrow().text())).append('\n');
                    } else {
                        var storage = ledger.evidence().gitStorage().stream().filter(value -> value.sha256().equals(document.sha256())).findFirst().orElseThrow();
                        text.append("storage: separate Git storage reference; original non-Git target identity unchanged\n")
                                .append("storage-path: ").append(storage.path()).append('\n')
                                .append("repository: ").append(storage.git().repositoryId()).append('\n')
                                .append("commit: ").append(storage.git().commit()).append('\n')
                                .append("blob: ").append(storage.git().blob()).append('\n')
                                .append("availability: not checked by this read-only report\n");
                    }
                }
                text.append("original-evidence-capture-time: not recorded in this ledger schema; current observation time is not a substitute\n");
            } else if (observed) {
                text.append("storage: not retained as comment evidence; this source belongs to the current transient observation\n");
            } else {
                throw new IllegalArgumentException("Document identity is absent from this review: " + id);
            }
        }
        return text.toString();
    }

    private static boolean appendObserved(StringBuilder text, SFMReleaseReviewV1 observation, String id) {
        text.append("selected-document-revision: ").append(id).append('\n');
        var source = observation.corpusDocuments().stream().filter(document -> document.documentRevisionId().equals(id)).findFirst();
        if (source.isEmpty()) return false;
        var document = source.orElseThrow();
        text.append("observed-path: ").append(document.path()).append('\n')
                .append("observed-side: ").append(document.snapshotSide()).append('\n')
                .append("observed-materialization: ").append(document.materialization()).append('\n')
                .append("observed-sha256: ").append(document.sha256()).append('\n');
        observation.repositoryBindings().stream().filter(binding -> binding.laneId().equals(document.laneId())).findFirst().ifPresent(binding -> {
            boolean liveAfter = document.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.AFTER && binding.workingTreeCapture().isPresent();
            text.append("observation-source: ").append(liveAfter ? "live disk observation" : "pinned Git source").append('\n');
            text.append("observation-repository: ").append(binding.repositoryId()).append('\n');
            if (!liveAfter) text.append("observation-commit: ").append(
                    document.snapshotSide() == SFMReleaseReviewV1.SnapshotSide.BEFORE
                            ? binding.beforeCommit() : binding.candidateCommit()).append('\n');
            if (liveAfter) text.append("observation-captured-at-unix-ms: ").append(binding.workingTreeCapture().orElseThrow().capturedAtUnixMs()).append('\n');
        });
        return true;
    }

    private static int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }
}
