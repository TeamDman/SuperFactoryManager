package ca.teamdman.sfm.gametest.puppet.definition;
import ca.teamdman.sfm.gametest.puppet.*;
import ca.teamdman.sfm.gametest.puppet.action.ShowThemeSettingsPuppetAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.network.chat.Component;
@SFMGamePuppet
public final class TitleScreenThemeSettingsGamePuppet {
 private TitleScreenThemeSettingsGamePuppet(){}
 public static void run(SFMGamePuppetHelper p){
  p.waitForOverlayToNotBePresent(LoadingOverlay.class); p.waitTicks(20);
  p.showThemeSettings(ShowThemeSettingsPuppetAction.View.OPEN);
  p.capture("theme-settings-overview",cap("All semantic colours, syntax roles, file icons, and action icons are visible."));
  p.showThemeSettings(ShowThemeSettingsPuppetAction.View.EDIT_COLOUR);
  p.setColorInputHex("#FF345678",false);
  p.capture("theme-settings-colour-picker",cap("The reusable typed ARGB picker edits panel.background beside its live settings panel."));
  p.confirmColorInput(); p.waitTicks(6);
  p.capture("theme-settings-colour-preview",cap("The draft panel background changes immediately before saving."));
  p.showThemeSettings(ShowThemeSettingsPuppetAction.View.EDIT_ICON);
  p.configureItemPicker(ca.teamdman.sfm.gametest.puppet.action.ConfigureItemPickerPuppetAction.View.MULTIPLEXED_SEARCH);
  p.capture("theme-settings-icon-picker",cap("The reusable ItemStack picker selects minecraft:chest for .sfml."));
  p.showThemeSettings(ShowThemeSettingsPuppetAction.View.CONFIRM_ICON);
  p.capture("theme-settings-icon-preview",cap("The .sfml preview now uses the selected chest ItemStack."));
  p.showThemeSettings(ShowThemeSettingsPuppetAction.View.SAVE);
  p.waitTicks(20);
  p.capture("theme-settings-saved",cap("Ctrl+S semantics atomically persist and reload the exact validated snapshot."));
  p.showThemeSettings(ShowThemeSettingsPuppetAction.View.INVALID);
  p.capture("theme-settings-invalid",cap("Invalid TOML is rejected while the last valid preview remains active."));
 }
 private static Component cap(String s){return Component.literal("SFM Theme Settings: ").withStyle(ChatFormatting.GOLD).append(s);}
}
