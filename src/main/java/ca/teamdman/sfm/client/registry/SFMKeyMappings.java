package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.common.localization.LocalizationKeys;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@SideOnly(Side.CLIENT)
public class SFMKeyMappings {

    public static final KeyBinding MORE_INFO_TOOLTIP_KEY = new KeyBinding(
            LocalizationKeys.MORE_HOVER_INFO_KEY.key().get(),
            KeyConflictContext.UNIVERSAL,
            KeyModifier.NONE, Keyboard.KEY_LSHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding CYCLE_LABEL_VIEW_KEY = new KeyBinding(
            LocalizationKeys.CYCLE_LABEL_VIEW_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN,
            KeyModifier.NONE, Keyboard.KEY_LMENU,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding TOGGLE_NETWORK_TOOL_OVERLAY_KEY = new KeyBinding(
            LocalizationKeys.TOGGLE_NETWORK_TOOL_OVERLAY.key().get(),
            SFMKeyConflictContext.NETWORK_TOOL,
            KeyModifier.NONE, Keyboard.KEY_LMENU,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding CONTAINER_INSPECTOR_KEY = new KeyBinding(
            LocalizationKeys.CONTAINER_INSPECTOR_TOGGLE_KEY.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.CONTROL, Keyboard.KEY_I,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding ITEM_INSPECTOR_KEY = new KeyBinding(
            LocalizationKeys.ITEM_INSPECTOR_TOGGLE_KEY.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.NONE, Keyboard.KEY_NONE,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_PICK_BLOCK_MODIFIER_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN_MODIFIER,
            KeyModifier.NONE, Keyboard.KEY_LMENU,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_CONTIGUOUS_MODIFIER_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN_MODIFIER,
            KeyModifier.NONE, Keyboard.KEY_LCONTROL,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_CLEAR_MODIFIER_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_CLEAR_MODIFIER_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN_MODIFIER,
            KeyModifier.NONE, Keyboard.KEY_LSHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_SCROLL_MODIFIER_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_SCROLL_MODIFIER_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN_SCROLL_MODIFIER,
            KeyModifier.NONE, Keyboard.KEY_LSHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_NEXT_LABEL_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_NEXT_LABEL_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN,
            KeyModifier.NONE, Keyboard.KEY_NONE,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_PREVIOUS_LABEL_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_PREVIOUS_LABEL_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN,
            KeyModifier.NONE, Keyboard.KEY_NONE,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_PULL_MODIFIER_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_PULL_MODIFIER_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN_ON_MANAGER,
            Keyboard.KEY_LSHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY = new KeyBinding(
            LocalizationKeys.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY.key().get(),
            SFMKeyConflictContext.LABEL_GUN,
            KeyModifier.NONE, Keyboard.KEY_RMENU, LocalizationKeys.SFM_KEY_CATEGORY.key().get());
    public static final KeyBinding MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY = new KeyBinding(LocalizationKeys.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY.key().get(), SFMKeyConflictContext.MANAGER,
            KeyModifier.CONTROL, Keyboard.KEY_E, LocalizationKeys.SFM_KEY_CATEGORY.key().get());

    public static void register() {
        ClientRegistry.registerKeyBinding(MORE_INFO_TOOLTIP_KEY);
        ClientRegistry.registerKeyBinding(CYCLE_LABEL_VIEW_KEY);
        ClientRegistry.registerKeyBinding(TOGGLE_NETWORK_TOOL_OVERLAY_KEY);
        ClientRegistry.registerKeyBinding(CONTAINER_INSPECTOR_KEY);
        ClientRegistry.registerKeyBinding(ITEM_INSPECTOR_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_PICK_BLOCK_MODIFIER_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_CONTIGUOUS_MODIFIER_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_CLEAR_MODIFIER_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_SCROLL_MODIFIER_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_NEXT_LABEL_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_PREVIOUS_LABEL_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_PULL_MODIFIER_KEY);
        ClientRegistry.registerKeyBinding(LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY);
        ClientRegistry.registerKeyBinding(MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY);
    }

    public static boolean isKeyDown(KeyBinding key) {
        if (key.getKeyCode() == 0) {
            return false;
        }
        return Keyboard.isKeyDown(key.getKeyCode());
    }

    public static ITextComponent getKeyDisplay(KeyBinding binding) {
        return new TextComponentString(binding.getDisplayName()).setStyle(new Style().setColor(TextFormatting.AQUA));
    }
}