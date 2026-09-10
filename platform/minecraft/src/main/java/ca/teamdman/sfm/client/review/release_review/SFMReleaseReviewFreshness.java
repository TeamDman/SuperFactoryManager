package ca.teamdman.sfm.client.review.release_review;

import static ca.teamdman.sfm.client.search.SFMExplorerSearchText.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Transient read-only evidence. Never changes a review's content identity or approvals. */
public final class SFMReleaseReviewFreshness {
    public enum State { CHECKING, CURRENT_AT_CHECK, OUTDATED, UNKNOWN }
    public record Repository(String lane, Path root, String candidate, String head) { }
    public record Evidence(State state, long checkedAtMillis, String details, List<Repository> repositories) {
        public Evidence {
            Objects.requireNonNull(state);
            Objects.requireNonNull(details);
            repositories = List.copyOf(repositories);
        }
        public String summary() {
            return switch (state) {
                case CHECKING -> value(FRESH_CHECKING);
                case CURRENT_AT_CHECK -> value(FRESH_CURRENT);
                case OUTDATED -> value(FRESH_OUTDATED);
                case UNKNOWN -> value(FRESH_UNKNOWN);
            };
        }
        public String age(long nowMillis) {
            long seconds = Math.max(0, (nowMillis - checkedAtMillis) / 1000);
            long displayedSeconds = seconds < 10 ? seconds : seconds / 10 * 10;
            return checkedAtMillis == 0 ? value(NOT_CHECKED) : value(CHECK_AGE,
                    displayedSeconds);
        }
    }
    public static Evidence unavailable(String reason) {
        return new Evidence(State.UNKNOWN, 0, reason, List.of());
    }
    public static Evidence checking() {
        return new Evidence(State.CHECKING, 0, "Read-only freshness check is running.", List.of());
    }

    public static Evidence parse(String json, List<SFMReleaseReviewV1.RepositoryBinding> expected) {
        JsonObject output = JsonParser.parseString(json).getAsJsonObject();
        if (!"sfm.release-review-freshness/1".equals(output.get("schema").getAsString())) {
            throw new IllegalArgumentException("Unsupported freshness schema; update the SFM companion");
        }
        boolean outdated = false;
        boolean unknown = false;
        long oldestCheck = Long.MAX_VALUE;
        var seen = new HashSet<String>();
        var repositories = new ArrayList<Repository>();
        for (var element : output.getAsJsonArray("repository_relationships")) {
            JsonObject lane = element.getAsJsonObject();
            String laneId = lane.get("lane_id").getAsString();
            String repositoryId = lane.get("repository_id").getAsString();
            var binding = expected.stream().filter(value -> value.laneId().equals(laneId)
                    && value.repositoryId().equals(repositoryId)).findFirst().orElseThrow(
                    () -> new IllegalArgumentException("Unexpected repository in freshness response"));
            if (!seen.add(laneId + "\0" + repositoryId)
                    || !binding.candidateIdentity().equals(lane.has("candidate_id")
                    ? lane.get("candidate_id").getAsString() : optionalString(lane, "candidate_commit"))) {
                throw new IllegalArgumentException("Freshness response does not describe this pinned review");
            }
            String head = optionalString(lane, "ambient_head");
            repositories.add(new Repository(laneId, Path.of(ca.teamdman.sfm.common.util.SFMNativePaths
                    .ordinaryWindowsPath(lane.get("repository_root").getAsString())),
                    binding.candidateIdentity(), head));
            String classification = lane.get("classification").getAsString();
            boolean capture = binding.workingTreeCapture().isPresent();
            if (capture) {
                if (!lane.has("capture_scope_matches") || lane.get("capture_scope_matches").isJsonNull()) unknown = true;
                else outdated |= !lane.get("capture_scope_matches").getAsBoolean();
            } else if (classification.equals("source_affecting_divergence")) outdated = true;
            else if (!classification.equals("exact") && !classification.equals("review_evidence_only")) unknown = true;
            // Failed ancestry checks use a fail-closed divergence classification plus diagnostics.
            for (var diagnostic : lane.getAsJsonArray("diagnostics")) {
                String code = diagnostic.getAsJsonObject().get("code").getAsString();
                if (!List.of("ambient.head-exact", "ambient.non-descendant", "ambient.review-evidence-only",
                        "ambient.source-affecting-paths").contains(code)) unknown = true;
            }
            if (!lane.has("working_tree")) { unknown = true; continue; }
            JsonObject tree = lane.getAsJsonObject("working_tree");
            if (!"sfm.release-review.working-tree-evidence/1".equals(tree.get("schema").getAsString())) {
                throw new IllegalArgumentException("Unsupported working-tree evidence schema");
            }
            long check = tree.get("checked_at_unix_millis").getAsLong();
            if (check <= 0) unknown = true;
            else oldestCheck = Math.min(oldestCheck, check);
            if (head.isEmpty() || !head.equals(optionalString(tree, "head"))
                    || !tree.has("source_dirty") || tree.get("source_dirty").isJsonNull()) unknown = true;
            else outdated |= tree.get("source_dirty").getAsBoolean();
            for (var diagnostic : tree.getAsJsonArray("diagnostics")) {
                String code = diagnostic.getAsJsonObject().get("code").getAsString();
                boolean scopeKnown = capture && lane.has("capture_scope_matches")
                        && !lane.get("capture_scope_matches").isJsonNull();
                boolean informative = scopeKnown && code.equals(lane.get("capture_scope_matches").getAsBoolean()
                        ? "capture.scope-exact" : "capture.scope-changed");
                if (!informative) unknown = true;
            }
        }
        if (seen.size() != expected.size() || seen.isEmpty()) unknown = true;
        return new Evidence(unknown ? State.UNKNOWN : outdated ? State.OUTDATED : State.CURRENT_AT_CHECK,
                oldestCheck == Long.MAX_VALUE ? 0 : oldestCheck, json, repositories);
    }

    private static String optionalString(JsonObject object, String name) {
        return object.has(name) && !object.get(name).isJsonNull() ? object.get(name).getAsString() : "";
    }
    private SFMReleaseReviewFreshness() { }
}
