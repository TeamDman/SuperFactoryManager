package ca.teamdman.sfm.client.review.session;

import java.util.List;

/** Frozen {@code sfm.review-session/1} semantic model. Lists retain wire order. */
public record SFMReviewSessionV1(
        String schema,
        String id,
        String title,
        String coordinateSystem,
        List<RevisionLane> revisionLanes,
        List<Comment> comments,
        List<StyleRule> styleRules,
        CompletionPolicy completionPolicy
) {
    public static final String SCHEMA = "sfm.review-session/1";
    public static final String COORDINATE_SYSTEM = "utf8_byte_half_open";

    public SFMReviewSessionV1 {
        revisionLanes = List.copyOf(revisionLanes);
        comments = List.copyOf(comments);
        styleRules = List.copyOf(styleRules);
    }

    public record RevisionLane(String id, Repository repository, String versionLabel,
                               Snapshot before, Snapshot after) {}
    public record Repository(String id, String rootHint) {}
    public record Snapshot(String id, List<DocumentRevision> documents) {
        public Snapshot { documents = List.copyOf(documents); }
    }
    public record DocumentRevision(String id, String path, String encoding, String sha256, String text) {}
    public record Comment(String id, String text, Provenance provenance, SelectionRule selectionRule) {}
    public record Provenance(String kind, String producer, String version, List<String> parentCommentIds) {
        public Provenance { parentCommentIds = List.copyOf(parentCommentIds); }
    }

    public sealed interface SelectionRule permits LiteralUtf8Range, Union, Intersection, Difference {}
    public record LiteralUtf8Range(String documentRevisionId, int startByte, int endByte,
                                   String documentSha256, String selectedTextSha256) implements SelectionRule {}
    public record Union(List<SelectionRule> rules) implements SelectionRule {
        public Union { rules = List.copyOf(rules); }
    }
    public record Intersection(List<SelectionRule> rules) implements SelectionRule {
        public Intersection { rules = List.copyOf(rules); }
    }
    public record Difference(SelectionRule include, List<SelectionRule> exclude) implements SelectionRule {
        public Difference { exclude = List.copyOf(exclude); }
    }

    public record StyleRule(String id, List<String> requiredHashtags, int priority,
                            String foreground, String background, String underline,
                            String gutterMarker, boolean enabled) {
        public StyleRule { requiredHashtags = List.copyOf(requiredHashtags); }
    }
    public record CompletionPolicy(String coverageMode, String approvalHashtag, List<String> blockingHashtags) {
        public CompletionPolicy { blockingHashtags = List.copyOf(blockingHashtags); }
    }
}
