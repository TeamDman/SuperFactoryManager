package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Bounded read-only companion resolution, invoked on the review persistence worker. */
@FunctionalInterface
public interface SFMReleaseReviewLedgerResolver {
    record Resolved(SFMReleaseReviewV1 document, Map<String, SFMReviewEvidenceTable.Observed> sources,
                    java.util.List<String> diagnostics) {
        public Resolved { sources = Map.copyOf(sources); diagnostics = java.util.List.copyOf(diagnostics); }
        public Resolved(SFMReleaseReviewV1 document, Map<String, SFMReviewEvidenceTable.Observed> sources) {
            this(document, sources, java.util.List.of());
        }
    }

    Resolved resolve(Path reviewPath, String exactAuthority) throws Exception;

    static Resolved companion(Path reviewPath, String exactAuthority) throws Exception {
        Path request = Files.createTempFile("sfm-review-ledger-", ".json");
        Process process = null;
        try {
            Files.writeString(request, exactAuthority, StandardCharsets.UTF_8);
            String executable = System.getProperty("sfm.releaseReviewToolchainExecutable", "sfm-propagate-changes.exe");
            long companionStarted = System.nanoTime();
            process = new ProcessBuilder(executable, "--output-format", "json", "review", "session", "resolve",
                    "--file", reviewPath.toString(), "--request-file", request.toString())
                    .directory(reviewPath.getParent().toFile()).start();
            process.getOutputStream().close();
            Process owned = process;
            var stdout = CompletableFuture.supplyAsync(() -> read(owned.getInputStream(), 512 * 1024 * 1024));
            var stderr = CompletableFuture.supplyAsync(() -> read(owned.getErrorStream(), 128 * 1024));
            try {
                if (!process.waitFor(180, TimeUnit.SECONDS)) throw new IOException("Review resolution exceeded 180 seconds");
                String output = stdout.get(5, TimeUnit.SECONDS);
                String errors = stderr.get(5, TimeUnit.SECONDS);
                if (process.exitValue() != 0) throw new IOException("Review resolution failed: "
                        + errors.substring(0, Math.min(errors.length(), 4096)));
                long decodeStarted = System.nanoTime();
                ca.teamdman.sfm.SFM.LOGGER.info(
                        "SFM_RELEASE_REVIEW_RESOLUTION_PHASE companion_pid={} phase=companion_and_ipc elapsed_micros={} output_chars={}",
                        process.pid(), (decodeStarted - companionStarted) / 1_000L, output.length());
                return decode(output, exactAuthority, process.pid());
            } finally {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                }
                process.getInputStream().close(); process.getErrorStream().close();
                stdout.cancel(true); stderr.cancel(true);
            }
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            Files.deleteIfExists(request);
        }
    }

    private static Resolved decode(String output, String exactAuthority, long companionPid) throws IOException {
        long phaseStarted = System.nanoTime();
        var root = JsonParser.parseString(output).getAsJsonObject();
        if (!"sfm.release-review-resolve/3".equals(root.get("schema").getAsString())
                || !SFMReleaseReviewKernel.sha256(exactAuthority.getBytes(StandardCharsets.UTF_8))
                .equals(root.get("authority_sha256").getAsString()))
            throw new IOException("Companion resolution does not describe the exact opened ledger");
        var resolution = root.getAsJsonObject("resolution");
        var document = SFMReleaseReviewV1Codec.parse(resolution.get("document").toString());
        if (!SFMReleaseReviewV1.OBSERVATION_SCHEMA.equals(document.schema()))
            throw new IOException("Expected a transient ledger observation");
        long decoded = System.nanoTime();
        ca.teamdman.sfm.SFM.LOGGER.info(
                "SFM_RELEASE_REVIEW_RESOLUTION_PHASE companion_pid={} phase=json_decode elapsed_micros={}",
                companionPid, (decoded - phaseStarted) / 1_000L);
        SFMReleaseReviewKernel.validate(document);
        long validated = System.nanoTime();
        ca.teamdman.sfm.SFM.LOGGER.info(
                "SFM_RELEASE_REVIEW_RESOLUTION_PHASE companion_pid={} phase=validation elapsed_micros={}",
                companionPid, (validated - decoded) / 1_000L);
        var bodies = new HashMap<String, ca.teamdman.sfm.client.review.session.SFMReviewSessionV1.DocumentRevision>();
        for (var lane : document.reviewSession().revisionLanes()) {
            lane.before().documents().forEach(body -> bodies.put(body.id(), body));
            lane.after().documents().forEach(body -> bodies.put(body.id(), body));
        }
        var sources = new HashMap<String, SFMReviewEvidenceTable.Observed>();
        for (var item : resolution.getAsJsonArray("source_evidence")) {
            var value = item.getAsJsonObject();
            String id = value.get("revision_id").getAsString();
            var body = bodies.get(id);
            if (body == null) continue; // Explicit missing historical Git evidence remains in the corpus.
            Optional<SFMReviewEvidenceTable.GitReference> git = Optional.empty();
            if (value.has("git")) {
                var ref = value.getAsJsonObject("git");
                git = Optional.of(new SFMReviewEvidenceTable.GitReference(ref.get("repository_id").getAsString(),
                        ref.get("commit").getAsString(), ref.get("blob").getAsString()));
            }
            var source = new SFMReviewEvidenceTable.Observed(new SFMReviewEvidenceTable.Document(id,
                    value.get("path").getAsString(), value.get("sha256").getAsString(), git), body.text());
            if (!body.path().equals(source.document().path()) || sources.put(id, source) != null)
                throw new IOException("Conflicting resolution source identity");
        }
        var diagnostics = new java.util.ArrayList<String>();
        resolution.getAsJsonArray("diagnostics").forEach(item -> diagnostics.add(item.getAsString()));
        var resolved = new Resolved(document, sources, diagnostics);
        ca.teamdman.sfm.SFM.LOGGER.info(
                "SFM_RELEASE_REVIEW_RESOLUTION_PHASE companion_pid={} phase=evidence_index elapsed_micros={} sources={}",
                companionPid, (System.nanoTime() - validated) / 1_000L, sources.size());
        return resolved;
    }

    private static String read(InputStream stream, int limit) {
        try (stream; var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = stream.read(buffer)) >= 0;) {
                if (output.size() > limit - count) throw new IOException("Review resolution output exceeds byte limit");
                output.write(buffer, 0, count);
            }
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(output.toByteArray())).toString();
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }
}
