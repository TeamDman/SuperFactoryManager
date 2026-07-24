package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.item_picker.SFMItemPickerPanel;
import ca.teamdman.sfm.client.screen.theme_settings.*;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.theme.*;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public record ShowThemeSettingsPuppetAction(View view) implements SFMPuppetAction {
    public enum View { OPEN, EDIT_COLOUR, EDIT_ICON, CONFIRM_ICON, SAVE, INVALID }
    @Override public String description(){ return "show theme settings " + view; }
    @Override public boolean tick(ISFMGamePuppetRuntime runtime){
        Minecraft mc=Minecraft.getInstance();
        if(view==View.OPEN){ SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(mc.screen,new SFMThemeSettingsPanel())); return true; }
        SFMThemeSettingsPanel panel=findSettings();
        switch(view){
            case EDIT_COLOUR -> { panel.model().select(SFMThemeProperty.Kind.COLOUR,"panel.background"); panel.openEditor(); }
            case EDIT_ICON -> { panel.model().select(SFMThemeProperty.Kind.FILE_ICON,".sfml"); panel.openEditor(); }
            case CONFIRM_ICON -> {
                SFMItemPickerPanel picker=findPicker(); picker.setQueryForAutomation("minecraft:chest");
                picker.pressForAutomation(GLFW.GLFW_KEY_ENTER,0);
                if(!panel.model().draft().fileIcon(".sfml").requestedItem().toString().equals("minecraft:chest"))
                    throw new IllegalStateException("Theme icon picker did not update .sfml draft");
            }
            case SAVE -> {
                if(!panel.save()) throw new IllegalStateException(panel.model().status());
                if(SFMClientThemeService.active().colour(SFMColourRole.PANEL_BACKGROUND)!=0xFF345678)
                    throw new IllegalStateException("Saved colour did not reload");
            }
            case INVALID -> {
                SFMClientTheme before=SFMClientThemeService.active();
                var result=SFMClientThemeService.saveText(SFMClientThemeService.activeThemePath(),"schema_version = [");
                if(result.valid()||SFMClientThemeService.active()!=before) throw new IllegalStateException("Invalid theme replaced active snapshot");
                panel.model().setStatus("Invalid theme rejected; saved preview retained");
            }
            default -> {}
        }
        return true;
    }
    private static SFMThemeSettingsPanel findSettings(){
        if(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer mux)
            return mux.panels().stream().filter(SFMThemeSettingsPanel.class::isInstance).map(SFMThemeSettingsPanel.class::cast).findFirst().orElseThrow();
        throw new IllegalStateException("Expected theme settings workspace");
    }
    private static SFMItemPickerPanel findPicker(){
        if(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer mux)
            return mux.panels().stream().filter(SFMItemPickerPanel.class::isInstance).map(SFMItemPickerPanel.class::cast).findFirst().orElseThrow();
        throw new IllegalStateException("Expected item picker beside theme settings");
    }
}
