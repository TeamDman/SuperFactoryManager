package ca.teamdman.sfm.client.review.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Strict semantic parser and deterministic two-space/trailing-newline writer for the frozen v1 wire contract. */
public final class SFMReviewSessionV1Codec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private SFMReviewSessionV1Codec() {}

    public static SFMReviewSessionV1 parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        requireEqual(text(root, "schema"), SFMReviewSessionV1.SCHEMA, "schema");
        requireEqual(text(root, "coordinate_system"), SFMReviewSessionV1.COORDINATE_SYSTEM, "coordinate_system");
        List<SFMReviewSessionV1.RevisionLane> lanes = new ArrayList<>();
        for (JsonElement element : array(root, "revision_lanes")) lanes.add(parseLane(element.getAsJsonObject()));
        List<SFMReviewSessionV1.Comment> comments = new ArrayList<>();
        for (JsonElement element : array(root, "comments")) comments.add(parseComment(element.getAsJsonObject()));
        List<SFMReviewSessionV1.StyleRule> styles = new ArrayList<>();
        for (JsonElement element : array(root, "style_rules")) styles.add(parseStyle(element.getAsJsonObject()));
        JsonObject policy = object(root, "completion_policy");
        return new SFMReviewSessionV1(text(root, "schema"), text(root, "id"), text(root, "title"),
                text(root, "coordinate_system"), lanes, comments, styles,
                new SFMReviewSessionV1.CompletionPolicy(text(policy, "coverage_mode"),
                        text(policy, "approval_hashtag"), strings(policy, "blocking_hashtags")));
    }

    public static String write(SFMReviewSessionV1 session) {
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
        return GSON.toJson(root) + "\n";
    }

    private static SFMReviewSessionV1.RevisionLane parseLane(JsonObject value) {
        JsonObject repository = object(value, "repository");
        return new SFMReviewSessionV1.RevisionLane(text(value, "id"),
                new SFMReviewSessionV1.Repository(text(repository, "id"), text(repository, "root_hint")),
                optionalText(value, "version_label"), parseSnapshot(object(value, "before")),
                parseSnapshot(object(value, "after")));
    }

    private static SFMReviewSessionV1.Snapshot parseSnapshot(JsonObject value) {
        List<SFMReviewSessionV1.DocumentRevision> documents = new ArrayList<>();
        for (JsonElement element : array(value, "documents")) {
            JsonObject document = element.getAsJsonObject();
            documents.add(new SFMReviewSessionV1.DocumentRevision(text(document, "id"), text(document, "path"),
                    text(document, "encoding"), text(document, "sha256"), text(document, "text")));
        }
        return new SFMReviewSessionV1.Snapshot(text(value, "id"), documents);
    }

    private static SFMReviewSessionV1.Comment parseComment(JsonObject value) {
        if (value.has("tags")) throw new IllegalArgumentException("Comment " + text(value, "id")
                + " contains forbidden authoritative tags field");
        JsonObject provenance = object(value, "provenance");
        return new SFMReviewSessionV1.Comment(text(value, "id"), text(value, "text"),
                new SFMReviewSessionV1.Provenance(text(provenance, "kind"), text(provenance, "producer"),
                        text(provenance, "version"), strings(provenance, "parent_comment_ids")),
                parseRule(object(value, "selection_rule")));
    }

    private static SFMReviewSessionV1.SelectionRule parseRule(JsonObject value) {
        return switch (text(value, "kind")) {
            case "literal_utf8_range" -> new SFMReviewSessionV1.LiteralUtf8Range(
                    text(value, "document_revision_id"), integer(value, "start_byte"), integer(value, "end_byte"),
                    text(value, "document_sha256"), text(value, "selected_text_sha256"));
            case "union" -> new SFMReviewSessionV1.Union(parseRules(array(value, "rules")));
            case "intersection" -> new SFMReviewSessionV1.Intersection(parseRules(array(value, "rules")));
            case "difference" -> new SFMReviewSessionV1.Difference(parseRule(object(value, "include")),
                    parseRules(array(value, "exclude")));
            default -> throw new IllegalArgumentException("Unknown selection rule kind '" + text(value, "kind") + "'");
        };
    }

    private static List<SFMReviewSessionV1.SelectionRule> parseRules(JsonArray values) {
        List<SFMReviewSessionV1.SelectionRule> result = new ArrayList<>();
        for (JsonElement value : values) result.add(parseRule(value.getAsJsonObject()));
        return result;
    }

    private static SFMReviewSessionV1.StyleRule parseStyle(JsonObject value) {
        return new SFMReviewSessionV1.StyleRule(text(value, "id"), strings(value, "required_hashtags"),
                integer(value, "priority"), optionalText(value, "foreground"), optionalText(value, "background"),
                optionalText(value, "underline"), optionalText(value, "gutter_marker"), bool(value, "enabled"));
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
            JsonObject item = new JsonObject();
            item.addProperty("id", document.id());
            item.addProperty("path", document.path());
            item.addProperty("encoding", document.encoding());
            item.addProperty("sha256", document.sha256());
            item.addProperty("text", document.text());
            documents.add(item);
        });
        value.add("documents", documents);
        return value;
    }

    private static JsonObject writeComment(SFMReviewSessionV1.Comment comment) {
        JsonObject value = new JsonObject();
        value.addProperty("id", comment.id());
        value.addProperty("text", comment.text());
        JsonObject provenance = new JsonObject();
        provenance.addProperty("kind", comment.provenance().kind());
        provenance.addProperty("producer", comment.provenance().producer());
        provenance.addProperty("version", comment.provenance().version());
        provenance.add("parent_comment_ids", writeStrings(comment.provenance().parentCommentIds()));
        value.add("provenance", provenance);
        value.add("selection_rule", writeRule(comment.selectionRule()));
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

    private static JsonObject writeStyle(SFMReviewSessionV1.StyleRule style) {
        JsonObject value = new JsonObject();
        value.addProperty("id", style.id()); value.add("required_hashtags", writeStrings(style.requiredHashtags()));
        value.addProperty("priority", style.priority());
        addOptional(value, "foreground", style.foreground()); addOptional(value, "background", style.background());
        addOptional(value, "underline", style.underline()); addOptional(value, "gutter_marker", style.gutterMarker());
        value.addProperty("enabled", style.enabled()); return value;
    }

    private static JsonArray writeStrings(List<String> strings) {
        JsonArray result = new JsonArray(); strings.forEach(result::add); return result;
    }
    private static void addOptional(JsonObject object, String name, String value) { if (value != null) object.addProperty(name, value); }
    private static String text(JsonObject value, String name) { return value.get(name).getAsString(); }
    private static String optionalText(JsonObject value, String name) { return value.has(name) ? value.get(name).getAsString() : null; }
    private static int integer(JsonObject value, String name) { return value.get(name).getAsInt(); }
    private static boolean bool(JsonObject value, String name) { return value.get(name).getAsBoolean(); }
    private static JsonArray array(JsonObject value, String name) { return value.getAsJsonArray(name); }
    private static JsonObject object(JsonObject value, String name) { return value.getAsJsonObject(name); }
    private static List<String> strings(JsonObject value, String name) {
        List<String> result = new ArrayList<>(); for (JsonElement item : array(value, name)) result.add(item.getAsString()); return result;
    }
    private static void requireEqual(String actual, String expected, String field) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Unsupported " + field + " '" + actual + "'");
    }
}
