package ca.teamdman.sfm.client.screen.review.comment;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.color.SFMArgbColor;
import ca.teamdman.sfm.client.screen.color.SFMColorInputPanel;
import ca.teamdman.sfm.client.screen.workspace.*;
import ca.teamdman.sfm.client.theme.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Source-first review surface over the general comment data-source boundary. */
public final class SFMReviewCommentPanel implements SFMScreenPanel {
    public enum View { REVIEW, STYLE_RULES, MIGRATION, LEGACY }
    private final SFMReviewCommentDataSource source;
    private SFMReviewCommentDataSource.SessionView session;
    private View view=View.REVIEW;
    private int selectedComment;
    private int selectedMigration;
    private String status="Before and after are first-class review documents";
    private @Nullable SFMWorkspacePanelContext context;
    private String createdId;
    private @Nullable SFMReviewCommentDataSource.RangeView literalSelection;
    private @Nullable EditMode editMode;
    private String editText="";
    private int documentTop, documentBottom, documentColumnWidth, documentGap, documentLeft;

    public SFMReviewCommentPanel(SFMReviewCommentDataSource source){this.source=source;this.session=source.refresh();}
    public SFMReviewCommentDataSource.SessionView session(){return session;}
    public View view(){return view;}
    @Override public Component title(){return Component.literal("Review Comments");}
    @Override public Component narration(){return Component.literal(status);}
    @Override public void opened(Minecraft mc,SFMScreenPanelBounds b,SFMWorkspacePanelContext context){this.context=context;}
    @Override public void closed(){context=null;}

    @Override public boolean keyPressed(int key,int scan,int mods){
        if(editMode!=null){
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){submitText();return true;}
            if(key==GLFW.GLFW_KEY_ESCAPE){editMode=null;status="Comment edit cancelled";return true;}
            if(key==GLFW.GLFW_KEY_BACKSPACE&&!editText.isEmpty()){editText=editText.substring(0,editText.length()-1);return true;}
            return false;
        }
        if(key==GLFW.GLFW_KEY_F2){nextProblem();return true;}
        if(key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_RIGHT_BRACKET){selectAdjacentComment(1);return true;}
        if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_LEFT_BRACKET){selectAdjacentComment(-1);return true;}
        if(key==GLFW.GLFW_KEY_C && view==View.STYLE_RULES){openStyleColourPicker();return true;}
        if(key==GLFW.GLFW_KEY_DELETE && !session.comments().isEmpty()){archiveActive();return true;}
        if(key==GLFW.GLFW_KEY_N){beginCreate();return true;} if(key==GLFW.GLFW_KEY_E){beginEdit();return true;}
        if(key==GLFW.GLFW_KEY_1){view=View.REVIEW;return true;} if(key==GLFW.GLFW_KEY_2){view=View.STYLE_RULES;return true;}
        if(key==GLFW.GLFW_KEY_3){view=View.MIGRATION;return true;} if(key==GLFW.GLFW_KEY_4){view=View.LEGACY;return true;}
        return false;
    }
    @Override public boolean charTyped(char c,int modifiers){if(editMode==null||Character.isISOControl(c))return false;editText+=c;return true;}
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button!=GLFW.GLFW_MOUSE_BUTTON_LEFT||view!=View.REVIEW||mouseY<documentTop||mouseY>=documentBottom)return false;
        boolean after=mouseX>=documentLeft+documentColumnWidth+documentGap;
        int x=after?documentLeft+documentColumnWidth+documentGap:documentLeft;
        if(mouseX<x||mouseX>=x+documentColumnWidth)return false;
        var doc=session.documents().get(after?1:0);int line=Math.max(0,(int)(mouseY-documentTop-22)/14);selectLine(doc,line);return true;
    }

    public void selectLiteral(SFMReviewCommentDataSource.RangeView range){literalSelection=range;status="Literal selection "+shortId(range.documentRevisionId())+"["+range.startByte()+","+range.endByte()+") · N new comment";}
    public void beginCreate(){if(literalSelection==null){status="Select a before/after source line first";return;}editMode=EditMode.CREATE;editText="";status="Type comment text; Enter creates, Esc cancels";}
    public void beginEdit(){if(session.comments().isEmpty())return;editMode=EditMode.EDIT;editText=activeComment().text();status="Edit comment text; Enter applies and re-derives hashtags";}
    public void replaceDraftText(String text){if(editMode==null)throw new IllegalStateException("No comment editor active");editText=text;}
    public void submitText(){
        if(editMode==null)return;
        if(editText.isBlank()){status="Comment text must not be blank";return;}
        if(editMode==EditMode.CREATE){createdId=source.createLiteralComment(editText,List.of(literalSelection));refresh();selectedComment=indexOf(createdId);status="Created literal UTF-8 selection comment";}
        else {source.editComment(activeComment().id(),editText);refresh();status="Edited comment; hashtags re-derived from text";}
        editMode=null;
    }
    public void archiveActive(){String id=activeComment().id();source.archiveComment(id);refresh();status="Archived "+id+" without deleting history";}
    public void selectAdjacentComment(int direction){if(session.comments().isEmpty())return;selectedComment=Math.floorMod(selectedComment+direction,session.comments().size());view=View.REVIEW;status=(direction>0?"Next":"Previous")+" comment · "+activeComment().id();}
    private void selectLine(SFMReviewCommentDataSource.DocumentView doc,int line){String[] lines=doc.text().split("\\n",-1);line=Math.min(line,lines.length-1);int start=0;for(int i=0;i<line;i++)start+=lines[i].getBytes(StandardCharsets.UTF_8).length+1;int end=start+lines[line].getBytes(StandardCharsets.UTF_8).length;selectLiteral(new SFMReviewCommentDataSource.RangeView(doc.id(),start,end));}

    public void applyAutomation(String command){
        if(command.equals("overview")){view=View.REVIEW;selectedComment=indexOf("diff-rename");status="Diff-produced comment selects before and after source";}
        else if(command.equals("disjoint")){view=View.REVIEW;selectedComment=indexOf("cross-side-intent");status="One comment retains disjoint ranges across both revisions";}
        else if(command.equals("create")){selectLiteral(new SFMReviewCommentDataSource.RangeView(SFMFixtureReviewCommentDataSource.AFTER,20,42));beginCreate();replaceDraftText("#question Check this literal selection.");submitText();}
        else if(command.equals("edit")){selectedComment=indexOf(createdId);beginEdit();replaceDraftText("#needs-change Rename needs a clearer explanation.");submitText();}
        else if(command.equals("archive")){selectedComment=indexOf(createdId);archiveActive();}
        else if(command.equals("overlap")){view=View.REVIEW;selectedComment=indexOf("overlapping-problem");status="2 independently inspectable comments overlap audit();";}
        else if(command.equals("styles")){view=View.STYLE_RULES;status="Priority resolves each visual channel independently";}
        else if(command.equals("style-picker")){view=View.STYLE_RULES;openStyleColourPicker();}
        else if(command.equals("f2")) nextProblem();
        else if(command.startsWith("migration:")){view=View.MIGRATION;selectedMigration=Integer.parseInt(command.substring(10));status="Migration queue distinguishes exact, changed, and invalid";}
        else if(command.equals("legacy")){view=View.LEGACY;status="Compatibility projection only; comments remain authoritative";}
        else throw new IllegalArgumentException("Unknown comment-review command "+command);
    }
    public void setStyleColourForAutomation(int argb){source.updateStyleColour("problem-underline",SFMReviewCommentDataSource.StyleChannel.BACKGROUND,argb);refresh();status="Updated visible #problem source background through typed colour input";}
    private void nextProblem(){for(int i=1;i<=session.comments().size();i++){int n=(selectedComment+i)%session.comments().size();if(SFMCommentHashtags.derive(session.comments().get(n).text()).contains("#problem")){selectedComment=n;view=View.REVIEW;status="F2 → #problem at after byte 60..68";return;}}status="No #problem comments";}
    private void openStyleColourPicker(){
        if(context==null){status="Colour picker host unavailable";return;}
        int initial=session.styleRules().stream().filter(r->r.id().equals("problem-underline")).findFirst().orElseThrow().background();
        context.submit(new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT,new SFMColorInputPanel(new SFMArgbColor(initial),List.of(),c->setStyleColourForAutomation(c.argb()),()->status="Colour edit cancelled")));
    }
    private void refresh(){session=source.refresh();selectedComment=Math.min(selectedComment,session.comments().size()-1);}
    private int indexOf(String id){for(int i=0;i<session.comments().size();i++)if(session.comments().get(i).id().equals(id))return i;throw new IllegalStateException(id);}
    private SFMReviewCommentDataSource.CommentView activeComment(){return session.comments().get(Math.max(0,selectedComment));}

    @Override public void render(PoseStack ps,Minecraft mc,SFMScreenPanelBounds b,int mx,int my,float pt,boolean focused){
        SFMClientTheme theme=SFMClientThemeService.active();int right=b.x()+b.width(),bottom=b.y()+b.height();
        GuiComponent.fill(ps,b.x(),b.y(),right,bottom,theme.colour(SFMColourRole.PANEL_BACKGROUND));
        draw(ps,mc,"Review Comments · frozen v1 fixture adapter",b.x()+8,b.y()+7,b.width()-16,theme.colour(SFMColourRole.TEXT_ACCENT),true);
        draw(ps,mc,session.title(),b.x()+8,b.y()+20,b.width()-16,theme.colour(SFMColourRole.TEXT_PRIMARY),false);
        draw(ps,mc,"[1] Review  [2] Styles  [3] Migration  [4] Legacy  ↑/↓ comments  F2 #problem",b.x()+8,b.y()+33,b.width()-16,theme.colour(SFMColourRole.TEXT_MUTED),false);
        int top=b.y()+52;
        switch(view){case REVIEW->renderReview(ps,mc,theme,b.x()+8,top,b.width()-16,bottom-29);case STYLE_RULES->renderStyles(ps,mc,theme,b.x()+8,top,b.width()-16);case MIGRATION->renderMigration(ps,mc,theme,b.x()+8,top,b.width()-16);case LEGACY->renderLegacy(ps,mc,theme,b.x()+8,top,b.width()-16);}
        if(editMode!=null){GuiComponent.fill(ps,b.x()+18,bottom-52,right-18,bottom-25,0xFF101419);draw(ps,mc,(editMode==EditMode.CREATE?"New comment: ":"Edit comment: ")+editText+"_",b.x()+24,bottom-44,b.width()-48,theme.colour(SFMColourRole.TEXT_PRIMARY),false);}
        draw(ps,mc,status,b.x()+8,bottom-16,b.width()-16,status.contains("invalid")||status.contains("Changed")?theme.colour(SFMColourRole.TEXT_ERROR):theme.colour(SFMColourRole.TEXT_ACCENT),false);
    }
    private void renderReview(PoseStack ps,Minecraft mc,SFMClientTheme t,int x,int y,int w,int bottom){
        int gap=8,col=(w-gap)/2;documentLeft=x;documentTop=y;documentBottom=bottom-80;documentColumnWidth=col;documentGap=gap;renderDocument(ps,mc,t,session.documents().get(0),x,y,col,bottom);renderDocument(ps,mc,t,session.documents().get(1),x+col+gap,y,w-col-gap,bottom);
        var c=activeComment();int box=bottom-75;GuiComponent.fill(ps,x,box,x+w,bottom,t.colour(SFMColourRole.PANEL_SELECTION));
        draw(ps,mc,"Comment "+c.id()+" · "+c.provenance()+" · "+c.evaluationStatus(),x+6,box+5,w-12,t.colour(SFMColourRole.TEXT_MUTED),false);
        draw(ps,mc,c.text(),x+6,box+19,w-12,t.colour(SFMColourRole.TEXT_PRIMARY),true);
        draw(ps,mc,"Derived hashtags: "+String.join(" ",SFMCommentHashtags.derive(c.text())),x+6,box+33,w-12,t.colour(SFMColourRole.TEXT_ACCENT),false);
        draw(ps,mc,"Ranges: "+c.ranges().stream().map(r->shortId(r.documentRevisionId())+"["+r.startByte()+","+r.endByte()+")").toList(),x+6,box+47,w-12,t.colour(SFMColourRole.TEXT_MUTED),false);
        if(c.id().equals("overlapping-problem"))draw(ps,mc,"Overlaps: approved-method + audit problem · both details retained",x+6,box+61,w-12,0xFFFFAA00,false);
    }
    private void renderDocument(PoseStack ps,Minecraft mc,SFMClientTheme t,SFMReviewCommentDataSource.DocumentView d,int x,int y,int w,int bottom){
        GuiComponent.fill(ps,x,y,x+w,bottom-80,d.side()==SFMReviewCommentDataSource.Side.BEFORE?0x802A1818:0x80182A20);
        draw(ps,mc,d.side()+" · "+d.path(),x+5,y+5,w-10,d.side()==SFMReviewCommentDataSource.Side.BEFORE?t.colour(SFMColourRole.TEXT_ERROR):t.colour(SFMColourRole.TEXT_ACCENT),true);
        String[] lines=d.text().split("\\n",-1);int offset=0;
        for(int i=0;i<lines.length;i++){String line=lines[i];int bytes=line.getBytes(StandardCharsets.UTF_8).length+1;List<SFMReviewCommentDataSource.CommentView> hits=commentsAt(d.id(),offset,offset+bytes);int ly=y+22+i*14;
            if(!hits.isEmpty())GuiComponent.fill(ps,x+3,ly-2,x+w-3,ly+11,backgroundFor(hits));
            draw(ps,mc,String.format("%2d  %s",i+1,line),x+5,ly,w-10,t.colour(SFMColourRole.TEXT_PRIMARY),false);
            if(hits.size()>1)draw(ps,mc,"["+hits.size()+"]",x+w-22,ly,20,0xFFFFAA00,true);offset+=bytes;}
    }
    private List<SFMReviewCommentDataSource.CommentView> commentsAt(String doc,int start,int end){return session.comments().stream().filter(c->!c.archived()&&c.ranges().stream().anyMatch(r->r.documentRevisionId().equals(doc)&&r.startByte()<end&&r.endByte()>start)).toList();}
    private int backgroundFor(List<SFMReviewCommentDataSource.CommentView> comments){return comments.stream().flatMap(c->SFMCommentHashtags.derive(c.text()).stream()).map(tag->session.styleRules().stream().filter(r->r.enabled()&&r.requiredHashtags().contains(tag)&&r.background()!=null).max(Comparator.comparingInt(SFMReviewCommentDataSource.StyleRuleView::priority)).orElse(null)).filter(java.util.Objects::nonNull).max(Comparator.comparingInt(SFMReviewCommentDataSource.StyleRuleView::priority)).map(SFMReviewCommentDataSource.StyleRuleView::background).orElse(0x44404040);}
    private void renderStyles(PoseStack ps,Minecraft mc,SFMClientTheme t,int x,int y,int w){draw(ps,mc,"COMMENT STYLE RULES · C opens reusable colour picker",x,y,w,t.colour(SFMColourRole.TEXT_PRIMARY),true);int row=22;for(var r:session.styleRules()){GuiComponent.fill(ps,x,y+row-3,x+w,y+row+23,t.colour(SFMColourRole.PANEL_SELECTION));draw(ps,mc,r.id()+"  "+r.requiredHashtags()+"  priority "+r.priority(),x+6,y+row,w-12,t.colour(SFMColourRole.TEXT_PRIMARY),true);draw(ps,mc,"foreground="+hex(r.foreground())+" background="+hex(r.background())+" underline="+hex(r.underline())+" gutter="+hex(r.gutter()),x+6,y+row+12,w-12,t.colour(SFMColourRole.TEXT_MUTED),false);row+=31;}draw(ps,mc,"Per-channel precedence: #problem underline overlays #approved background",x,y+row+8,w,0xFFFFAA00,false);}
    private void renderMigration(PoseStack ps,Minecraft mc,SFMClientTheme t,int x,int y,int w){draw(ps,mc,"COMMENT MIGRATION QUEUE",x,y,w,t.colour(SFMColourRole.TEXT_PRIMARY),true);int row=22;for(int i=0;i<session.migrations().size();i++){var m=session.migrations().get(i);int colour=m.status()==SFMReviewCommentDataSource.EvaluationStatus.RESOLVED_EXACTLY?0xFF55FF55:m.status()==SFMReviewCommentDataSource.EvaluationStatus.CONTENT_CHANGED?0xFFFFAA00:0xFFFF5555;if(i==selectedMigration)GuiComponent.fill(ps,x,y+row-3,x+w,y+row+24,t.colour(SFMColourRole.PANEL_SELECTION));draw(ps,mc,m.status()+" · "+m.commentId(),x+6,y+row,w-12,colour,true);draw(ps,mc,m.diagnostic(),x+6,y+row+12,w-12,t.colour(SFMColourRole.TEXT_MUTED),false);row+=33;}draw(ps,mc,"Changed/invalid comments never silently carry effective approval.",x,y+row+8,w,t.colour(SFMColourRole.TEXT_ERROR),false);}
    private void renderLegacy(PoseStack ps,Minecraft mc,SFMClientTheme t,int x,int y,int w){draw(ps,mc,"LEGACY LEDGER PROJECTION · read-only compatibility",x,y,w,t.colour(SFMColourRole.TEXT_PRIMARY),true);int row=24;for(var l:session.legacyRows()){draw(ps,mc,l.operationId()+"  review="+l.reviewed()+"  decision="+l.decision()+"  audit="+l.audit(),x+6,y+row,w-12,l.audit().equals("FORBIDDEN")?t.colour(SFMColourRole.TEXT_ERROR):t.colour(SFMColourRole.TEXT_ACCENT),false);row+=18;}draw(ps,mc,"Projected as #reviewed/#approved/#audit-forbidden comments; not a second authority.",x,y+row+10,w,0xFFFFAA00,false);}
    private static String shortId(String id){return id.contains(":before:")?"before":"after";}
    private static String hex(Integer c){return c==null?"—":String.format("#%08X",c);}
    private static void draw(PoseStack p,Minecraft m,String s,int x,int y,int w,int c,boolean shadow){if(w>0)SFMFontUtils.draw(p,m.font,m.font.plainSubstrByWidth(s,w),x,y,c,shadow);}
    private enum EditMode { CREATE, EDIT }
}
