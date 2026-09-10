package ca.teamdman.sfm.client.screen.theme_settings;

import ca.teamdman.sfm.client.action.*;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelViewport.Rect;
import ca.teamdman.sfm.client.screen.workspace.*;
import ca.teamdman.sfm.client.theme.preview.*;
import ca.teamdman.sfm.common.localization.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Mouse/keyboard front end to canonical rule actions; the draft never replaces the active theme. */
public final class SFMItemstackPreviewDraftPanel implements SFMScreenPanel {
    @SFMLocalizationDatagen public static final LocalizationEntry TITLE=new LocalizationEntry("gui.sfm.preview_rule.draft.title","ItemStack rule preview");
    @SFMLocalizationDatagen public static final LocalizationEntry APPLY=new LocalizationEntry("gui.sfm.preview_rule.draft.apply","Save rule");
    @SFMLocalizationDatagen public static final LocalizationEntry PICK=new LocalizationEntry("gui.sfm.preview_rule.draft.pick","Choose icon");
    @SFMLocalizationDatagen public static final LocalizationEntry COPY=new LocalizationEntry("gui.sfm.preview_rule.draft.copy","Copy command");
    @SFMLocalizationDatagen public static final LocalizationEntry CANCEL=new LocalizationEntry("gui.sfm.preview_rule.draft.cancel","Cancel");
    public static final List<LocalizationEntry> CONTROLS=List.of(APPLY,PICK,COPY,CANCEL);
    private final SFMItemstackPreviewDraft draft;
    private final Optional<SFMItemstackPreviewRules.Decision> decision;
    private SFMWorkspacePanelContext context;
    private SFMScreenPanelBounds bounds=new SFMScreenPanelBounds(0,0,1,1);
    private int selectedControl,firstLine,lineCount;
    private String status="No theme file has changed. Save is an explicit command.";
    public SFMItemstackPreviewDraftPanel(SFMItemstackPreviewDraft draft) { this.draft=Objects.requireNonNull(draft);this.decision=draft.decision(); }
    public SFMItemstackPreviewDraft draft() { return draft; }
    public String status() { return status; }
    @Override public Component title() { return TITLE.getComponent(); }
    @Override public Component narration() { return Component.literal(title().getString()+". "+draft.scopeText()+". "+status); }
    @Override public void opened(Minecraft mc,SFMScreenPanelBounds bounds,SFMWorkspacePanelContext context) { this.bounds=bounds;this.context=context; }
    @Override public void resized(Minecraft mc,SFMScreenPanelBounds bounds) { this.bounds=bounds; }
    @Override public void closed() { context=null; }
    public static List<Rect> controls(SFMScreenPanelBounds b) {
        int columns=b.width()<260 ? 2 : 4;int rows=4/columns;int width=Math.max(1,(b.width()-12)/columns);
        var result=new ArrayList<Rect>();for(int i=0;i<4;i++) result.add(new Rect(b.x()+6+(i%columns)*width,b.y()+b.height()-6-rows*22+(i/columns)*22,width-2,20));
        return List.copyOf(result);
    }
    public String commandFor(int index) {
        String arguments=draft.request().command().substring(SFMItemstackPreviewRuleAction.PREFIX.length()+1);
        return switch(index) {
            case 0 -> draft.request().command();
            case 1 -> SFMItemstackPreviewRuleToolsAction.Kind.PICK.prefix()+" "+draft.request().theme().argument()+" "+draft.request().predicate().print();
            case 2 -> SFMItemstackPreviewRuleToolsAction.Kind.COPY.prefix()+" "+arguments;
            case 3 -> "sfm action invoke sfm:panel/close";
            default -> throw new IllegalArgumentException("Unknown draft control");
        };
    }
    public boolean activate(int index) {
        if(context==null)return false;
        var actionContext=new SFMClientActionContext(context.host(),()->context!=null,context.panelId());
        try { return SFMClientActionExecutor.execute(commandFor(index),actionContext,message->status=message.getString())>0; }
        catch(Exception failure) { status="Rule action failed: "+failure.getMessage();return false; }
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        var cells=controls(bounds);for(int i=0;i<cells.size();i++) if(cells.get(i).contains(x,y)) {
            selectedControl=i;
            if(button==GLFW.GLFW_MOUSE_BUTTON_LEFT) { activate(i);return true; }
            if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT && context!=null) {
                var actionContext=new SFMClientActionContext(context.host(),()->context!=null,context.panelId());
                ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen.open(actionContext,commandFor(i));return true;
            }
        }
        return false;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_TAB) { selectedControl=Math.floorMod(selectedControl+((mods&GLFW.GLFW_MOD_SHIFT)!=0 ? -1:1),4);return true; }
        if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_KP_ENTER) { activate(selectedControl);return true; }
        if(key==GLFW.GLFW_KEY_ESCAPE) { activate(3);return true; }
        if(key==GLFW.GLFW_KEY_UP || key==GLFW.GLFW_KEY_DOWN) { firstLine=Math.max(0,Math.min(Math.max(0,lineCount-1),firstLine+(key==GLFW.GLFW_KEY_UP?-1:1)));return true; }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double delta) {
        if(!bounds.contains(x,y))return false;firstLine=Math.max(0,Math.min(Math.max(0,lineCount-1),firstLine+(delta>0?-3:3)));return true;
    }
    @Override public Optional<SFMPanelTooltip> tooltipAt(double x,double y) {
        var cells=controls(bounds);for(int i=0;i<cells.size();i++) if(cells.get(i).contains(x,y))
            return Optional.of(new SFMPanelTooltip(List.of(CONTROLS.get(i).getComponent(),Component.literal(commandFor(i)),Component.literal("Right-click to inspect/edit the command"))));
        return Optional.empty();
    }
    @Override public void render(PoseStack ps,Minecraft mc,SFMScreenPanelBounds b,int mx,int my,float partial,boolean focused) {
        bounds=b;GuiComponent.fill(ps,b.x(),b.y(),b.x()+b.width(),b.y()+b.height(),0xFF202020);
        SFMFontUtils.draw(ps,mc.font,mc.font.plainSubstrByWidth(title().getString(),Math.max(1,b.width()-16)),b.x()+8,b.y()+8,0xFF55FFFF,false);
        SFMItemIconRenderer.render(ps,mc,draft.request().rule().icon(),b.x()+8,b.y()+25);
        SFMFontUtils.draw(ps,mc.font,mc.font.plainSubstrByWidth(draft.request().item().toString(),Math.max(1,b.width()-40)),b.x()+31,b.y()+29,0xFFFFFFFF,false);
        String text=draft.scopeText()+"\n\nDestination: "+draft.request().theme().path()+"\nRevision: "+draft.request().theme().sha256()
                +"\n\nCaptured entry: "+draft.subject().map(SFMItemstackPreviewSubject::name).orElse("unavailable; no scope guessed")
                +"\nPreview result: "+decision.map(value->value.status().name()).orElse("no captured subject")
                +"\nInheritance: user > mod > default; reset removes only an authored user rule.\n\n"+status+"\n\n"+draft.request().command();
        var lines=mc.font.split(Component.literal(text),Math.max(1,b.width()-16));lineCount=lines.size();
        var cells=controls(b);int bottom=cells.get(0).y()-5;int y=b.y()+49;
        for(int i=firstLine;i<lines.size() && y+mc.font.lineHeight<=bottom;i++,y+=mc.font.lineHeight+1)
            SFMFontUtils.draw(ps,mc.font,lines.get(i),b.x()+8,y,0xFFE8E8E8,false);
        for(int i=0;i<cells.size();i++) {
            var cell=cells.get(i);GuiComponent.fill(ps,cell.x(),cell.y(),cell.x()+cell.width(),cell.y()+cell.height(),focused&&selectedControl==i ? 0xFF35636A:cell.contains(mx,my)?0xFF454545:0xFF303030);
            String label=mc.font.plainSubstrByWidth(CONTROLS.get(i).getComponent().getString(),Math.max(1,cell.width()-6));
            SFMFontUtils.draw(ps,mc.font,label,cell.x()+3,cell.y()+6,0xFFFFFFFF,false);
        }
    }
}
