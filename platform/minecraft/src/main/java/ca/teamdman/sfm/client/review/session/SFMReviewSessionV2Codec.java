package ca.teamdman.sfm.client.review.session;

import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic codec and explicit v1-to-v2 migration for the canonical review session. */
public final class SFMReviewSessionV2Codec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SFMReviewSessionV2Codec() {
    }

    public static SFMReviewSessionV2 parseOrMigrate(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String schema = text(root, "schema");
        if (SFMReviewSessionV2.SCHEMA.equals(schema)) return parseV2(root);
        if (SFMReviewSessionV1.SCHEMA.equals(schema)) return migrate(SFMReviewSessionV1Codec.parse(json));
        throw new IllegalArgumentException("Unsupported review-session schema '" + schema + "'");
    }

    public static SFMReviewSessionV2 parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        requireEqual(text(root, "schema"), SFMReviewSessionV2.SCHEMA, "schema");
        return parseV2(root);
    }

    public static SFMReviewSessionV2 migrate(SFMReviewSessionV1 session) {
        List<SFMReviewSessionV2.Comment> comments = session.comments().stream()
                .map(comment -> new SFMReviewSessionV2.Comment(
                        comment.id(),
                        comment.text(),
                        comment.provenance(),
                        new SFMReviewSessionV2.CommittedReviewTarget(comment.selectionRule())
                ))
                .toList();
        return new SFMReviewSessionV2(
                SFMReviewSessionV2.SCHEMA,
                session.id(),
                session.title(),
                session.coordinateSystem(),
                session.revisionLanes(),
                comments,
                session.styleRules(),
                session.completionPolicy()
        );
    }

    public static String write(SFMReviewSessionV2 session) {
        return GSON.toJson(writeTree(session)) + "\n";
    }

    /** Fresh owned tree for nesting without serializing and parsing an intermediate string. */
    public static JsonObject writeTree(SFMReviewSessionV2 session) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", session.schema());
        root.addProperty("id", session.id());
        root.addProperty("title", session.title());
        root.addProperty("coordinate_system", session.coordinateSystem());
        JsonArray lanes = new JsonArray();
        session.revisionLanes().forEach(lane -> lanes.add(writeLane(lane)));
        root.add("revision_lanes", lanes);
        JsonArray comments = new JsonArray();
        session.comments().forEach(comment -> comments.add(writeComment(comment)));
        root.add("comments", comments);
        JsonArray styles = new JsonArray();
        session.styleRules().forEach(style -> styles.add(writeStyle(style)));
        root.add("style_rules", styles);
        JsonObject policy = new JsonObject();
        policy.addProperty("coverage_mode", session.completionPolicy().coverageMode());
        policy.addProperty("approval_hashtag", session.completionPolicy().approvalHashtag());
        policy.add("blocking_hashtags", writeStrings(session.completionPolicy().blockingHashtags()));
        root.add("completion_policy", policy);
        return root;
    }

    private static SFMReviewSessionV2 parseV2(JsonObject root) {
        requireEqual(text(root, "coordinate_system"), SFMReviewSessionV1.COORDINATE_SYSTEM, "coordinate_system");
        List<SFMReviewSessionV1.RevisionLane> lanes = new ArrayList<>();
        for (JsonElement value : array(root, "revision_lanes")) lanes.add(parseLane(value.getAsJsonObject()));
        List<SFMReviewSessionV2.Comment> comments = new ArrayList<>();
        for (JsonElement value : array(root, "comments")) comments.add(parseComment(value.getAsJsonObject()));
        List<SFMReviewSessionV1.StyleRule> styles = new ArrayList<>();
        for (JsonElement value : array(root, "style_rules")) styles.add(parseStyle(value.getAsJsonObject()));
        JsonObject policy = object(root, "completion_policy");
        return new SFMReviewSessionV2(
                text(root, "schema"),
                text(root, "id"),
                text(root, "title"),
                text(root, "coordinate_system"),
                lanes,
                comments,
                styles,
                new SFMReviewSessionV1.CompletionPolicy(
                        text(policy, "coverage_mode"),
                        text(policy, "approval_hashtag"),
                        strings(policy, "blocking_hashtags")
                )
        );
    }

    private static SFMReviewSessionV2.Comment parseComment(JsonObject value) {
        if (value.has("tags")) throw new IllegalArgumentException("Comment " + text(value, "id")
                + " contains forbidden authoritative tags field");
        JsonObject provenance = object(value, "provenance");
        return new SFMReviewSessionV2.Comment(
                text(value, "id"),
                text(value, "text"),
                new SFMReviewSessionV1.Provenance(
                        text(provenance, "kind"),
                        text(provenance, "producer"),
                        text(provenance, "version"),
                        strings(provenance, "parent_comment_ids")
                ),
                parseTarget(object(value, "target"))
        );
    }

    private static SFMReviewSessionV2.CommentTarget parseTarget(JsonObject value) {
        return switch (text(value, "kind")) {
            case "committed_selection" -> new SFMReviewSessionV2.CommittedReviewTarget(
                    parseRule(object(value, "selection_rule")),
                    value.has("candidate_promotion")
                            ? Optional.of(parsePromotion(object(value, "candidate_promotion")))
                            : Optional.empty()
            );
            case "candidate_trajectory" -> new SFMReviewSessionV2.CandidateTrajectoryTarget(
                    text(value, "machine_id"),
                    longInteger(value, "machine_revision"),
                    text(value, "trajectory_plan_revision_id"),
                    text(value, "route_id"),
                    integer(value, "route_step_position"),
                    optionalTextValue(value, "trajectory_step_id"),
                    text(value, "predicted_state_id"),
                    optionalTextValue(value, "predicted_state_hash"),
                    SFMHistoryGraphContract.ProjectionStatus.valueOf(text(value, "projection_status").toUpperCase(java.util.Locale.ROOT)),
                    SFMReviewSessionV2.CandidateTargetKind.valueOf(text(value, "target_kind").toUpperCase(java.util.Locale.ROOT)),
                    optionalTextValue(value, "action_intent_id"),
                    value.has("projected_document_selection")
                            ? Optional.of(parseProjectedSelection(object(value, "projected_document_selection")))
                            : Optional.empty(),
                    optionalTextValue(value, "evaluator_revision"),
                    parseEvaluatorEvidence(array(value, "evaluator_evidence"))
            );
            default -> throw new IllegalArgumentException("Unknown comment target kind '" + text(value, "kind") + "'");
        };
    }

    private static SFMReviewSessionV2.ProjectedDocumentSelection parseProjectedSelection(JsonObject value) {
        return new SFMReviewSessionV2.ProjectedDocumentSelection(
                text(value, "document_id"),
                text(value, "document_state_hash"),
                text(value, "document_text_sha256"),
                integer(value, "start_byte"),
                integer(value, "end_byte"),
                text(value, "selected_text_sha256")
        );
    }

    private static SFMReviewSessionV2.CandidatePromotionLink parsePromotion(JsonObject value) {
        return new SFMReviewSessionV2.CandidatePromotionLink(
                text(value, "source_candidate_comment_id"),
                text(value, "source_candidate_target_sha256"),
                text(value, "decision_id"),
                text(value, "executed_history_head_id"),
                text(value, "executed_state_id"),
                text(value, "executed_state_hash"),
                text(value, "correspondence"),
                strings(value, "correspondence_evidence")
        );
    }

    private static List<SFMCandidateHistoryContract.EvaluatorEvidence> parseEvaluatorEvidence(JsonArray values) {
        List<SFMCandidateHistoryContract.EvaluatorEvidence> answer = new ArrayList<>();
        for (JsonElement item : values) {
            JsonObject value = item.getAsJsonObject();
            answer.add(new SFMCandidateHistoryContract.EvaluatorEvidence(text(value, "key"), text(value, "value")));
        }
        return answer;
    }

    private static JsonObject writeComment(SFMReviewSessionV2.Comment comment) {
        JsonObject value = new JsonObject();
        value.addProperty("id", comment.id());
        value.addProperty("text", comment.text());
        JsonObject provenance = new JsonObject();
        provenance.addProperty("kind", comment.provenance().kind());
        provenance.addProperty("producer", comment.provenance().producer());
        provenance.addProperty("version", comment.provenance().version());
        provenance.add("parent_comment_ids", writeStrings(comment.provenance().parentCommentIds()));
        value.add("provenance", provenance);
        value.add("target", writeTarget(comment.target()));
        return value;
    }

    private static JsonObject writeTarget(SFMReviewSessionV2.CommentTarget target) {
        JsonObject value = new JsonObject();
        if (target instanceof SFMReviewSessionV2.CommittedReviewTarget committed) {
            value.addProperty("kind", "committed_selection");
            value.add("selection_rule", writeRule(committed.selectionRule()));
            committed.candidatePromotion().ifPresent(promotion ->
                    value.add("candidate_promotion", writePromotion(promotion)));
            return value;
        }
        if (target instanceof SFMReviewSessionV2.CandidateTrajectoryTarget candidate) {
            value.addProperty("kind", "candidate_trajectory");
            value.addProperty("machine_id", candidate.machineId());
            value.addProperty("machine_revision", candidate.machineRevision());
            value.addProperty("trajectory_plan_revision_id", candidate.trajectoryPlanRevisionId());
            value.addProperty("route_id", candidate.routeId());
            value.addProperty("route_step_position", candidate.routeStepPosition());
            addOptional(value, "trajectory_step_id", candidate.trajectoryStepId());
            value.addProperty("predicted_state_id", candidate.predictedStateId());
            addOptional(value, "predicted_state_hash", candidate.predictedStateHash());
            value.addProperty("projection_status", lower(candidate.projectionStatus()));
            value.addProperty("target_kind", lower(candidate.targetKind()));
            addOptional(value, "action_intent_id", candidate.actionIntentId());
            candidate.projectedDocumentSelection().ifPresent(selection ->
                    value.add("projected_document_selection", writeProjectedSelection(selection)));
            addOptional(value, "evaluator_revision", candidate.evaluatorRevision());
            JsonArray evidence = new JsonArray();
            candidate.evaluatorEvidence().forEach(item -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("key", item.key());
                entry.addProperty("value", item.value());
                evidence.add(entry);
            });
            value.add("evaluator_evidence", evidence);
            return value;
        }
        throw new IllegalArgumentException("Unsupported comment target " + target.getClass().getName());
    }

    private static JsonObject writeProjectedSelection(SFMReviewSessionV2.ProjectedDocumentSelection selection) {
        JsonObject value = new JsonObject();
        value.addProperty("document_id", selection.documentId());
        value.addProperty("document_state_hash", selection.documentStateHash());
        value.addProperty("document_text_sha256", selection.documentTextSha256());
        value.addProperty("start_byte", selection.startByte());
        value.addProperty("end_byte", selection.endByte());
        value.addProperty("selected_text_sha256", selection.selectedTextSha256());
        return value;
    }

    private static JsonObject writePromotion(SFMReviewSessionV2.CandidatePromotionLink promotion) {
        JsonObject value = new JsonObject();
        value.addProperty("source_candidate_comment_id", promotion.sourceCandidateCommentId());
        value.addProperty("source_candidate_target_sha256", promotion.sourceCandidateTargetSha256());
        value.addProperty("decision_id", promotion.decisionId());
        value.addProperty("executed_history_head_id", promotion.executedHistoryHeadId());
        value.addProperty("executed_state_id", promotion.executedStateId());
        value.addProperty("executed_state_hash", promotion.executedStateHash());
        value.addProperty("correspondence", promotion.correspondence());
        value.add("correspondence_evidence", writeStrings(promotion.correspondenceEvidence()));
        return value;
    }

    private static SFMReviewSessionV1.RevisionLane parseLane(JsonObject value) {
        JsonObject repository = object(value, "repository");
        return new SFMReviewSessionV1.RevisionLane(
                text(value, "id"),
                new SFMReviewSessionV1.Repository(text(repository, "id"), text(repository, "root_hint")),
                nullableText(value, "version_label"),
                parseSnapshot(object(value, "before")),
                parseSnapshot(object(value, "after"))
        );
    }

    private static SFMReviewSessionV1.Snapshot parseSnapshot(JsonObject value) {
        List<SFMReviewSessionV1.DocumentRevision> documents = new ArrayList<>();
        for (JsonElement element : array(value, "documents")) {
            JsonObject document = element.getAsJsonObject();
            documents.add(new SFMReviewSessionV1.DocumentRevision(
                    text(document, "id"), text(document, "path"), text(document, "encoding"),
                    text(document, "sha256"), text(document, "text")
            ));
        }
        return new SFMReviewSessionV1.Snapshot(text(value, "id"), documents);
    }

    private static SFMReviewSessionV1.StyleRule parseStyle(JsonObject value) {
        return new SFMReviewSessionV1.StyleRule(
                text(value, "id"), strings(value, "required_hashtags"), integer(value, "priority"),
                nullableText(value, "foreground"), nullableText(value, "background"),
                nullableText(value, "underline"), nullableText(value, "gutter_marker"), bool(value, "enabled")
        );
    }

    private static SFMReviewSessionV1.SelectionRule parseRule(JsonObject value) {
        return switch (text(value, "kind")) {
            case "literal_utf8_range" -> new SFMReviewSessionV1.LiteralUtf8Range(
                    text(value, "document_revision_id"), integer(value, "start_byte"), integer(value, "end_byte"),
                    text(value, "document_sha256"), text(value, "selected_text_sha256"));
            case "union" -> new SFMReviewSessionV1.Union(parseRules(array(value, "rules")));
            case "intersection" -> new SFMReviewSessionV1.Intersection(parseRules(array(value, "rules")));
            case "difference" -> new SFMReviewSessionV1.Difference(
                    parseRule(object(value, "include")), parseRules(array(value, "exclude")));
            default -> throw new IllegalArgumentException("Unknown selection rule kind '" + text(value, "kind") + "'");
        };
    }

    private static List<SFMReviewSessionV1.SelectionRule> parseRules(JsonArray values) {
        List<SFMReviewSessionV1.SelectionRule> answer = new ArrayList<>();
        for (JsonElement value : values) answer.add(parseRule(value.getAsJsonObject()));
        return answer;
    }

    private static JsonObject writeLane(SFMReviewSessionV1.RevisionLane lane) {
        JsonObject value = new JsonObject();
        value.addProperty("id", lane.id());
        JsonObject repository = new JsonObject();
        repository.addProperty("id", lane.repository().id());
        repository.addProperty("root_hint", lane.repository().rootHint());
        value.add("repository", repository);
        if (lane.versionLabel() != null) value.addProperty("version_label", lane.versionLabel());
        value.add("before", writeSnapshot(lane.before()));
        value.add("after", writeSnapshot(lane.after()));
        return value;
    }

    private static JsonObject writeSnapshot(SFMReviewSessionV1.Snapshot snapshot) {
        JsonObject value = new JsonObject();
        value.addProperty("id", snapshot.id());
        JsonArray documents = new JsonArray();
        snapshot.documents().forEach(document -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", document.id());
            entry.addProperty("path", document.path());
            entry.addProperty("encoding", document.encoding());
            entry.addProperty("sha256", document.sha256());
            entry.addProperty("text", document.text());
            documents.add(entry);
        });
        value.add("documents", documents);
        return value;
    }

    private static JsonObject writeStyle(SFMReviewSessionV1.StyleRule style) {
        JsonObject value = new JsonObject();
        value.addProperty("id", style.id());
        value.add("required_hashtags", writeStrings(style.requiredHashtags()));
        value.addProperty("priority", style.priority());
        addNullable(value, "foreground", style.foreground());
        addNullable(value, "background", style.background());
        addNullable(value, "underline", style.underline());
        addNullable(value, "gutter_marker", style.gutterMarker());
        value.addProperty("enabled", style.enabled());
        return value;
    }

    private static JsonObject writeRule(SFMReviewSessionV1.SelectionRule rule) {
        JsonObject value = new JsonObject();
        if (rule instanceof SFMReviewSessionV1.LiteralUtf8Range literal) {
            value.addProperty("kind", "literal_utf8_range");
            value.addProperty("document_revision_id", literal.documentRevisionId());
            value.addProperty("start_byte", literal.startByte());
            value.addProperty("end_byte", literal.endByte());
            value.addProperty("document_sha256", literal.documentSha256());
            value.addProperty("selected_text_sha256", literal.selectedTextSha256());
        } else if (rule instanceof SFMReviewSessionV1.Union union) {
            value.addProperty("kind", "union"); value.add("rules", writeRules(union.rules()));
        } else if (rule instanceof SFMReviewSessionV1.Intersection intersection) {
            value.addProperty("kind", "intersection"); value.add("rules", writeRules(intersection.rules()));
        } else if (rule instanceof SFMReviewSessionV1.Difference difference) {
            value.addProperty("kind", "difference"); value.add("include", writeRule(difference.include()));
            value.add("exclude", writeRules(difference.exclude()));
        } else throw new IllegalArgumentException("Unsupported selection rule " + rule.getClass().getName());
        return value;
    }

    private static JsonArray writeRules(List<SFMReviewSessionV1.SelectionRule> rules) {
        JsonArray values = new JsonArray(); rules.forEach(rule -> values.add(writeRule(rule))); return values;
    }

    private static JsonArray writeStrings(List<String> values) {
        JsonArray answer = new JsonArray(); values.forEach(answer::add); return answer;
    }

    private static void addOptional(JsonObject object, String name, Optional<String> value) {
        value.ifPresent(item -> object.addProperty(name, item));
    }

    private static void addNullable(JsonObject object, String name, String value) {
        if (value != null) object.addProperty(name, value);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String text(JsonObject value, String name) { return value.get(name).getAsString(); }
    private static String nullableText(JsonObject value, String name) { return value.has(name) ? value.get(name).getAsString() : null; }
    private static Optional<String> optionalTextValue(JsonObject value, String name) {
        return value.has(name) ? Optional.of(value.get(name).getAsString()) : Optional.empty();
    }
    private static int integer(JsonObject value, String name) { return value.get(name).getAsInt(); }
    private static long longInteger(JsonObject value, String name) { return value.get(name).getAsLong(); }
    private static boolean bool(JsonObject value, String name) { return value.get(name).getAsBoolean(); }
    private static JsonArray array(JsonObject value, String name) { return value.getAsJsonArray(name); }
    private static JsonObject object(JsonObject value, String name) { return value.getAsJsonObject(name); }
    private static List<String> strings(JsonObject value, String name) {
        List<String> answer = new ArrayList<>();
        for (JsonElement item : array(value, name)) answer.add(item.getAsString());
        return answer;
    }
    private static void requireEqual(String actual, String expected, String field) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Unsupported " + field + " '" + actual + "'");
    }
}
