package ca.teamdman.sfm.client.screen.review.comment;

import java.util.ArrayList;
import java.util.List;

/** Isolated adapter over the frozen v1 fixture semantics; not a competing wire model. */
public final class SFMFixtureReviewCommentDataSource implements SFMReviewCommentDataSource {
    public static final String BEFORE="sfm-1.19.2:before:Example.java", AFTER="sfm-1.19.2:after:Example.java";
    private final List<CommentView> comments=new ArrayList<>();
    private final List<StyleRuleView> styles=new ArrayList<>();
    private int nextId=1;
    public SFMFixtureReviewCommentDataSource(){
        comments.add(comment("approved-method","#approved Replacement method reviewed.","human · fixture-reviewer",
                List.of(new RangeView(AFTER,20,74)),EvaluationStatus.RESOLVED_EXACTLY));
        comments.add(comment("overlapping-problem","#problem #audit-forbidden Explain the audit call.","sfm_audit · fixture:audit-call",
                List.of(new RangeView(AFTER,60,68)),EvaluationStatus.RESOLVED_EXACTLY));
        comments.add(comment("cross-side-intent","#needs-change Confirm the rename and added audit behavior together.","human · fixture-reviewer",
                List.of(new RangeView(BEFORE,20,57),new RangeView(AFTER,20,74)),EvaluationStatus.CONTENT_CHANGED));
        comments.add(comment("diff-rename","#modified #renamed oldName → newName; audit() was added.","diff_engine · fixture:line-range",
                List.of(new RangeView(BEFORE,20,57),new RangeView(AFTER,20,74)),EvaluationStatus.RESOLVED_EXACTLY));
        styles.add(new StyleRuleView("approved-background",List.of("#approved"),10,null,0x5522AA44,null,null,true));
        styles.add(new StyleRuleView("needs-change-underline",List.of("#needs-change"),50,null,null,0xFFFFAA00,"!",true));
        styles.add(new StyleRuleView("problem-underline",List.of("#problem"),100,0xFFFF7777,0x55441111,0xFFFF5555,"!",true));
        styles.add(new StyleRuleView("modified-background",List.of("#modified"),5,null,0x553366AA,null,null,true));
    }
    private static CommentView comment(String id,String text,String provenance,List<RangeView> ranges,EvaluationStatus status){return new CommentView(id,text,provenance,false,List.copyOf(ranges),status);}
    @Override public SessionView refresh(){
        return new SessionView("Comment overlap and migration fixture",
                List.of(new DocumentView(BEFORE,Side.BEFORE,"src/Example.java","class Example {\n    void oldName() {\n        run();\n    }\n}\n"),
                        new DocumentView(AFTER,Side.AFTER,"src/Example.java","class Example {\n    void newName() {\n        run();\n        audit();\n    }\n}\n")),
                List.copyOf(comments),List.copyOf(styles),
                List.of(new MigrationView("approved-method",EvaluationStatus.RESOLVED_EXACTLY,"Exact UTF-8 witness retained"),
                        new MigrationView("cross-side-intent",EvaluationStatus.CONTENT_CHANGED,"Selection text changed; approval suspended"),
                        new MigrationView("legacy-out-of-bounds",EvaluationStatus.INVALID_RULE,"Literal range exceeds new document length")),
                List.of(new LegacyRow("rename","REVIEWED","APPROVED","PERMITTED"),
                        new LegacyRow("body","REVIEWED","APPROVED","FORBIDDEN")));
    }
    @Override public String createLiteralComment(String text,List<RangeView> ranges){String id="human-"+nextId++;comments.add(comment(id,text,"human · in-game reviewer",List.copyOf(ranges),EvaluationStatus.RESOLVED_EXACTLY));return id;}
    @Override public void editComment(String id,String text){replace(id,c->new CommentView(c.id(),text,c.provenance(),c.archived(),c.candidate(),c.targetLabel(),c.ranges(),c.evaluationStatus()));}
    @Override public void archiveComment(String id){replace(id,c->new CommentView(c.id(),c.text(),c.provenance(),true,c.candidate(),c.targetLabel(),c.ranges(),c.evaluationStatus()));}
    @Override public void updateStyleColour(String id,StyleChannel channel,int argb){
        for(int i=0;i<styles.size();i++) if(styles.get(i).id().equals(id)){StyleRuleView s=styles.get(i);styles.set(i,new StyleRuleView(s.id(),s.requiredHashtags(),s.priority(),replace(channel,StyleChannel.FOREGROUND,s.foreground(),argb),replace(channel,StyleChannel.BACKGROUND,s.background(),argb),replace(channel,StyleChannel.UNDERLINE,s.underline(),argb),s.gutterMarker(),s.enabled()));return;}
        throw new IllegalArgumentException("Unknown style rule "+id);
    }
    private void replace(String id,java.util.function.Function<CommentView,CommentView> f){for(int i=0;i<comments.size();i++)if(comments.get(i).id().equals(id)){comments.set(i,f.apply(comments.get(i)));return;}throw new IllegalArgumentException("Unknown comment "+id);}
    private static Integer replace(StyleChannel actual,StyleChannel expected,Integer old,int value){return actual==expected?Integer.valueOf(value):old;}
}
