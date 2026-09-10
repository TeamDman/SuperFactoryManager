package ca.teamdman.sfm.client.screen.theme_settings;

import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.color.SFMArgbColor;
import ca.teamdman.sfm.client.screen.color.SFMColorInputPanel;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerPanel;
import ca.teamdman.sfm.client.screen.workspace.*;
import ca.teamdman.sfm.client.theme.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Responsive structured editor over the runtime theme snapshot. */
public final class SFMThemeSettingsPanel implements SFMScreenPanel {
    private final SFMThemeSettingsModel model;
    private @Nullable SFMWorkspacePanelContext context;
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private int firstRow;

    public SFMThemeSettingsPanel() { this(new SFMThemeSettingsModel(SFMClientThemeService.active())); }
    public SFMThemeSettingsPanel(SFMThemeSettingsModel model) { this.model = model; }
    public SFMThemeSettingsModel model() { return model; }
    @Override public Component title() { return Component.literal("Theme settings"); }
    @Override public SFMPanelCloseState closeState() { return new SFMPanelCloseState(model.dirty(), false); }
    @Override public Component narration() { return Component.literal("Theme settings. " + model.status()); }
    @Override public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) { this.bounds=bounds; this.context=context; }
    @Override public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) { this.bounds=bounds; }
    @Override public void closed() { context=null; }

    @Override public void render(PoseStack ps, Minecraft mc, SFMScreenPanelBounds b, int mx, int my, float pt, boolean focused) {
        this.bounds=b;
        SFMClientTheme theme=model.draft();
        int right=b.x()+b.width(), bottom=b.y()+b.height();
        GuiComponent.fill(ps,b.x(),b.y(),right,bottom,theme.colour(SFMColourRole.PANEL_BACKGROUND));
        SFMFontUtils.draw(ps,mc.font,"Theme settings"+(model.dirty()?" *":""),b.x()+8,b.y()+7,theme.colour(SFMColourRole.TEXT_ACCENT),false);
        SFMFontUtils.draw(ps,mc.font,"Colours · syntax · file icons · action icons",b.x()+8,b.y()+20,theme.colour(SFMColourRole.TEXT_MUTED),false);
        int previewH=Math.max(72,Math.min(112,b.height()/3));
        int listTop=b.y()+36, listBottom=bottom-previewH-30;
        int visible=Math.max(1,(listBottom-listTop)/15);
        if(model.selectedIndex()<firstRow) firstRow=model.selectedIndex();
        if(model.selectedIndex()>=firstRow+visible) firstRow=model.selectedIndex()-visible+1;
        for(int i=firstRow;i<Math.min(model.properties().size(),firstRow+visible);i++){
            int y=listTop+(i-firstRow)*15;
            if(i==model.selectedIndex()) GuiComponent.fill(ps,b.x()+5,y-2,right-5,y+12,theme.colour(SFMColourRole.PANEL_SELECTION));
            String text=model.properties().get(i).label();
            if(mc.font.width(text)>b.width()-18) text=mc.font.plainSubstrByWidth(text,b.width()-28)+"…";
            SFMFontUtils.draw(ps,mc.font,text,b.x()+9,y,theme.colour(SFMColourRole.TEXT_PRIMARY),false);
        }
        int py=listBottom+5;
        GuiComponent.fill(ps,b.x()+6,py,right-6,bottom-25,theme.colour(SFMColourRole.PANEL_SELECTION));
        SFMFontUtils.draw(ps,mc.font,"Live preview",b.x()+11,py+6,theme.colour(SFMColourRole.TEXT_ACCENT),false);
        SFMFontUtils.draw(ps,mc.font,"EVERY INPUT example \"hello\" 42",b.x()+11,py+20,theme.syntax("keyword").colour(),false);
        SFMFontUtils.draw(ps,mc.font,"program.sfml",b.x()+31,py+38,theme.colour(SFMColourRole.TEXT_PRIMARY),false);
        SFMFontUtils.draw(ps,mc.font,"palette/open",b.x()+132,py+38,theme.colour(SFMColourRole.TEXT_PRIMARY),false);
        SFMItemIconRenderer.render(ps,mc,theme.fileIcon(".sfml"),b.x()+11,py+34);
        SFMItemIconRenderer.render(ps,mc,theme.actionIcon(SFMThemeSettingsModel.PALETTE_ACTION,theme.fileIcon("unknown")),b.x()+112,py+34);
        SFMFontUtils.draw(ps,mc.font,"Enter edit · Ctrl+S save · R reset · D defaults · T TOML",b.x()+8,bottom-21,theme.colour(SFMColourRole.TEXT_MUTED),false);
        SFMFontUtils.draw(ps,mc.font,model.status(),b.x()+8,bottom-10,model.status().startsWith("Invalid")?theme.colour(SFMColourRole.TEXT_ERROR):theme.colour(SFMColourRole.TEXT_PRIMARY),false);
    }

    @Override public boolean keyPressed(int key,int scan,int mods){
        if(key==GLFW.GLFW_KEY_UP){model.move(-1);return true;} if(key==GLFW.GLFW_KEY_DOWN){model.move(1);return true;}
        if(key==GLFW.GLFW_KEY_HOME){model.select(0);return true;} if(key==GLFW.GLFW_KEY_END){model.select(model.properties().size()-1);return true;}
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){openEditor();return true;}
        if(key==GLFW.GLFW_KEY_S&&(mods&GLFW.GLFW_MOD_CONTROL)!=0){save();return true;}
        if(key==GLFW.GLFW_KEY_R){model.resetSelected();return true;} if(key==GLFW.GLFW_KEY_D){model.restoreDefaults();return true;}
        if(key==GLFW.GLFW_KEY_T){
            if(save()) {
                Util.getPlatform().openFile(SFMClientThemeService.activeThemePath().toFile());
                model.setStatus("Opened raw TOML after saving the validated draft");
            }
            return true;
        }
        return false;
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){model.move(delta>0?-1:1);return bounds.contains(x,y);}
    public void openEditor(){
        if(context==null){model.setStatus("Editor host is unavailable");return;}
        SFMThemeProperty p=model.selected(); SFMScreenPanel editor;
        if(p.kind()==SFMThemeProperty.Kind.COLOUR||p.kind()==SFMThemeProperty.Kind.SYNTAX)
            editor=new SFMColorInputPanel(new SFMArgbColor(model.selectedColour()),List.of(),c->model.setSelectedColour(c.argb()),()->model.setStatus("Edit cancelled"));
        else editor=SFMItemPickerPanel.fromRegistry(model.selectedIcon(),model::setSelectedIcon,()->model.setStatus("Edit cancelled"));
        context.submit(new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT,editor));
    }
    public boolean save(){
        SFMThemeLoadResult result=SFMClientThemeService.save(model.draft());
        if(result.valid()){ model.markSaved(result.theme().orElseThrow()); return true; }
        model.setStatus("Invalid theme: "+String.join("; ",result.diagnostics())); return false;
    }
}
