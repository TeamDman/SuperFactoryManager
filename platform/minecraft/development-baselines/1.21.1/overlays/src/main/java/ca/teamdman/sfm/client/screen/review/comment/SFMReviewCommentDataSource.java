package ca.teamdman.sfm.client.screen.review.comment;

import java.util.List;

/** UI boundary implemented by the frozen fixture now and the canonical kernel after integration. */
public interface SFMReviewCommentDataSource {
    enum Side { BEFORE, AFTER }
    enum EvaluationStatus { RESOLVED_EXACTLY, RESOLVED_WITH_RELOCATION, AMBIGUOUS, NO_MATCH, INVALID_RULE, SCOPE_MISSING, CONTENT_CHANGED }
    enum StyleChannel { FOREGROUND, BACKGROUND, UNDERLINE, GUTTER }
    record DocumentView(String id, Side side, String path, String text) {}
    record RangeView(String documentRevisionId, int startByte, int endByte) {}
    record CommentView(String id, String text, String provenance, boolean archived,
                       List<RangeView> ranges, EvaluationStatus evaluationStatus) {}
    record StyleRuleView(String id, List<String> requiredHashtags, int priority,
                         Integer foreground, Integer background, Integer underline, Integer gutter, boolean enabled) {}
    record MigrationView(String commentId, EvaluationStatus status, String diagnostic) {}
    record LegacyRow(String operationId, String reviewed, String decision, String audit) {}
    record SessionView(String title, List<DocumentView> documents, List<CommentView> comments,
                       List<StyleRuleView> styleRules, List<MigrationView> migrations, List<LegacyRow> legacyRows) {}

    SessionView refresh();
    String createLiteralComment(String text, List<RangeView> ranges);
    void editComment(String id, String text);
    void archiveComment(String id);
    void updateStyleColour(String ruleId, StyleChannel channel, int argb);
}
