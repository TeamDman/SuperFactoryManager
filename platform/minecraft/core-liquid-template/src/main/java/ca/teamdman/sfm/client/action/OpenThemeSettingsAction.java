package ca.teamdman.sfm.client.action;
import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.theme_settings.SFMThemeSettingsPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
public final class OpenThemeSettingsAction implements SFMClientAction<SFMClientActionContext>{
 public Component title(){return Component.literal("Open theme settings");}
 public Component description(){return Component.literal("Edit colours, syntax styles, and ItemStack icon schemes with live preview");}
 public SFMClientActionRequirement<SFMClientActionContext> requirement(){return SFMClientActionAvailability::available;}
 public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context){
  var mc=Minecraft.getInstance(); SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(mc.screen,new SFMThemeSettingsPanel())); return 1;
 }
}
