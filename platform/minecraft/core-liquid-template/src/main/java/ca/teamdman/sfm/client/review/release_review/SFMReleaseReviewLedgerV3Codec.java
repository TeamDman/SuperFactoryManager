package ca.teamdman.sfm.client.review.release_review;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Strict v3 storage codec. Parsing does no source resolution or filesystem I/O. */
public final class SFMReleaseReviewLedgerV3Codec {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private SFMReleaseReviewLedgerV3Codec() {}

    public static String write(SFMReleaseReviewLedgerV3 ledger) {
        var root = new JsonObject();
        root.addProperty("schema", SFMReleaseReviewLedgerV3.SCHEMA);
        var targets = new JsonArray();
        for (var lane : ledger.targets()) {
            var value = new JsonObject();
            value.addProperty("id", lane.id());
            value.addProperty("repository_id", lane.repositoryId());
            value.addProperty("root_hint", lane.rootHint());
            value.addProperty("before_commit", lane.beforeCommit());
            lane.afterCommit().ifPresent(commit -> value.addProperty("after_commit", commit));
            value.addProperty("after_kind", lane.live() ? "working_tree" : "git");
            value.add("scope_paths", strings(lane.scopePaths()));
            value.add("excluded_paths", strings(lane.excludedPaths()));
            value.addProperty("include_untracked", lane.includeUntracked());
            targets.add(value);
        }
        root.add("targets", targets);
        root.add("state", SFMReleaseReviewV1Codec.writeTree(ledger.state()));
        var evidence = new JsonObject();
        var contents = new JsonArray();
        for (var content : ledger.evidence().contents()) {
            var value = new JsonObject();
            value.addProperty("sha256", content.sha256());
            value.addProperty("text", content.text());
            contents.add(value);
        }
        evidence.add("contents", contents);
        var documents = new JsonArray();
        for (var document : ledger.evidence().documents()) {
            var value = new JsonObject();
            value.addProperty("revision_id", document.revisionId());
            value.addProperty("path", document.path());
            value.addProperty("sha256", document.sha256());
            document.git().ifPresent(reference -> {
                var git = new JsonObject();
                git.addProperty("repository_id", reference.repositoryId());
                git.addProperty("commit", reference.commit());
                git.addProperty("blob", reference.blob());
                value.add("git", git);
            });
            documents.add(value);
        }
        evidence.add("documents", documents);
        if (!ledger.evidence().gitStorage().isEmpty()) {
            var storage = new JsonArray();
            for (var entry : ledger.evidence().gitStorage()) {
                var value = new JsonObject();
                value.addProperty("sha256", entry.sha256());
                value.addProperty("path", entry.path());
                value.addProperty("repository_id", entry.git().repositoryId());
                value.addProperty("commit", entry.git().commit());
                value.addProperty("blob", entry.git().blob());
                storage.add(value);
            }
            evidence.add("git_storage", storage);
        }
        root.add("evidence", evidence);
        return JSON.toJson(root) + "\n";
    }

    public static SFMReleaseReviewLedgerV3 parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        fields(root, "schema", "targets", "state", "evidence");
        if (!SFMReleaseReviewLedgerV3.SCHEMA.equals(text(root, "schema")))
            throw new IllegalArgumentException("Unsupported review ledger schema");
        var targets = new ArrayList<SFMReleaseReviewLedgerV3.TargetLane>();
        for (var item : root.getAsJsonArray("targets")) {
            var value = item.getAsJsonObject();
            fields(value, "id", "repository_id", "root_hint", "before_commit", "after_kind", "after_commit",
                    "scope_paths", "excluded_paths", "include_untracked");
            var after = value.has("after_commit") ? Optional.of(text(value, "after_commit")) : Optional.<String>empty();
            if (!(after.isPresent() ? "git" : "working_tree").equals(text(value, "after_kind")))
                throw new IllegalArgumentException("Review target kind/commit mismatch");
            if (!value.get("include_untracked").isJsonPrimitive()
                    || !value.getAsJsonPrimitive("include_untracked").isBoolean())
                throw new IllegalArgumentException("Expected include_untracked boolean");
            targets.add(new SFMReleaseReviewLedgerV3.TargetLane(text(value, "id"), text(value, "repository_id"),
                    text(value, "root_hint"), text(value, "before_commit"), after, readStrings(value, "scope_paths"),
                    readStrings(value, "excluded_paths"), value.get("include_untracked").getAsBoolean()));
        }
        var evidence = root.getAsJsonObject("evidence");
        fields(evidence, "contents", "documents", "git_storage");
        var contents = new ArrayList<SFMReviewEvidenceTable.Content>();
        for (var item : evidence.getAsJsonArray("contents")) {
            var value = item.getAsJsonObject(); fields(value, "sha256", "text");
            contents.add(new SFMReviewEvidenceTable.Content(text(value, "sha256"), text(value, "text")));
        }
        var documents = new ArrayList<SFMReviewEvidenceTable.Document>();
        for (var item : evidence.getAsJsonArray("documents")) {
            var value = item.getAsJsonObject(); fields(value, "revision_id", "path", "sha256", "git");
            Optional<SFMReviewEvidenceTable.GitReference> reference = Optional.empty();
            if (value.has("git")) {
                var git = value.getAsJsonObject("git"); fields(git, "repository_id", "commit", "blob");
                reference = Optional.of(new SFMReviewEvidenceTable.GitReference(text(git, "repository_id"),
                        text(git, "commit"), text(git, "blob")));
            }
            documents.add(new SFMReviewEvidenceTable.Document(text(value, "revision_id"), text(value, "path"),
                    text(value, "sha256"), reference));
        }
        var storage = new ArrayList<SFMReviewEvidenceTable.GitStorage>();
        if (evidence.has("git_storage")) for (var item : evidence.getAsJsonArray("git_storage")) {
            var value = item.getAsJsonObject(); fields(value, "sha256", "path", "repository_id", "commit", "blob");
            storage.add(new SFMReviewEvidenceTable.GitStorage(text(value, "sha256"), text(value, "path"),
                    new SFMReviewEvidenceTable.GitReference(text(value, "repository_id"), text(value, "commit"), text(value, "blob"))));
        }
        return new SFMReleaseReviewLedgerV3(targets, SFMReleaseReviewV1Codec.parse(JSON.toJson(root.get("state"))),
                new SFMReviewEvidenceTable(contents, documents, storage));
    }

    private static void fields(JsonObject value, String... allowed) {
        var names = Set.of(allowed);
        for (var key : value.keySet()) if (!names.contains(key))
            throw new IllegalArgumentException("Unknown review ledger field: " + key);
    }
    private static String text(JsonObject value, String key) {
        var item = value.get(key);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected review ledger string: " + key);
        return item.getAsString();
    }
    private static JsonArray strings(List<String> values) {
        var result = new JsonArray(); values.forEach(result::add); return result;
    }
    private static List<String> readStrings(JsonObject value, String key) {
        var result = new ArrayList<String>();
        for (var item : value.getAsJsonArray(key)) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
                throw new IllegalArgumentException("Expected review scope string");
            result.add(item.getAsString());
        }
        return result;
    }
}
