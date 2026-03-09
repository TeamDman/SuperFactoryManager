package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import ca.teamdman.sfm.common.util.SFMDist;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.common.util.Lazy;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;


public class SFMKeyMappings {
    @SFMLocalizationDatagen
    public static final LocalizationEntry SFM_KEY_CATEGORY = new LocalizationEntry(
            "key.categories.sfm",
            "Super Factory Manager"
    );

    @SFMLocalizationDatagen
    public static final LocalizationEntry MORE_HOVER_INFO_KEY_NAME = new LocalizationEntry(
            "key.sfm.more_info",
            "Hold For More Info"
    );

    public static final Lazy<KeyMapping> MORE_INFO_TOOLTIP_KEY = Lazy.of(() -> new KeyMapping(
            MORE_HOVER_INFO_KEY_NAME.key().get(),
            KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry CYCLE_LABEL_VIEW_KEY_NAME = new LocalizationEntry(
            "key.sfm.toggle_label_view_key",
            "Cycle label gun view"
    );

    public static final Lazy<KeyMapping> CYCLE_LABEL_VIEW_KEY = Lazy.of(() -> new KeyMapping(
            CYCLE_LABEL_VIEW_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry TOGGLE_NETWORK_TOOL_OVERLAY_KEY_NAME = new LocalizationEntry(
            "key.sfm.toggle_network_tool_overlay",
            "Toggle network tool overlay"
    );

    public static final Lazy<KeyMapping> TOGGLE_NETWORK_TOOL_OVERLAY_KEY = Lazy.of(() -> new KeyMapping(
            TOGGLE_NETWORK_TOOL_OVERLAY_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry CONTAINER_INSPECTOR_TOGGLE_KEY_NAME = new LocalizationEntry(
            "key.sfm.container_inspector.activation_key",
            "Toggle Container Inspector"
    );

    public static final Lazy<KeyMapping> CONTAINER_INSPECTOR_KEY = Lazy.of(() -> new KeyMapping(
            CONTAINER_INSPECTOR_TOGGLE_KEY_NAME.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry ITEM_INSPECTOR_TOGGLE_KEY_NAME = new LocalizationEntry(
            "key.sfm.item_inspector.activation_key",
            "(WIP) Copy Hovered Item To Clipboard"
    );

    public static final Lazy<KeyMapping> ITEM_INSPECTOR_KEY = Lazy.of(() -> new KeyMapping(
            ITEM_INSPECTOR_TOGGLE_KEY_NAME.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
//            GLFW.GLFW_KEY_GRAVE_ACCENT,
            InputConstants.UNKNOWN.getValue(),
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_PICK_BLOCK_MODIFIER_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.pick_block_modifier",
            "Label Gun Pick Block Modifier"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_PICK_BLOCK_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_PICK_BLOCK_MODIFIER_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_CONTIGUOUS_MODIFIER_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.contiguous_modifier",
            "Label Gun Contiguous Modifier"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_CONTIGUOUS_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_CONTIGUOUS_MODIFIER_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_CLEAR_MODIFIER_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.clear_modifier",
            "Label Gun Clear Modifier"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_CLEAR_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_CLEAR_MODIFIER_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_SCROLL_MODIFIER_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.scroll_modifier",
            "Label Gun Scroll Modifier"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_SCROLL_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_SCROLL_MODIFIER_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_NEXT_LABEL_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.next_label",
            "Label Gun Next Label"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_NEXT_LABEL_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_NEXT_LABEL_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_PREVIOUS_LABEL_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.previous_label",
            "Label Gun Previous Label"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_PREVIOUS_LABEL_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_PREVIOUS_LABEL_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_PULL_MODIFIER_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.pull_modifier",
            "Label Gun Pull Modifier"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_PULL_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_PULL_MODIFIER_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY_NAME = new LocalizationEntry(
            "key.sfm.label_gun.target_manager_modifier",
            "Label Gun Target Manager Modifier"
    );

    public static final Lazy<KeyMapping> LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY_NAME.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_ALT,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY_NAME = new LocalizationEntry(
            "key.sfm.manager.text_editor",
            "Manager Screen - Open Text Editor"
    );

    public static final Lazy<KeyMapping> MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY = Lazy.of(() -> new KeyMapping(
            MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY_NAME.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY_NAME = new LocalizationEntry(
            "key.sfm.title_screen.text_editor",
            "Title Screen - Open Text Editor"
    );

    public static final Lazy<KeyMapping> TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY = Lazy.of(() -> new KeyMapping(
            TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY_NAME.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            SFM_KEY_CATEGORY.key().get()
    ));

    @SFMLocalizationDatagen
    public static final LocalizationEntry TEXT_EDITOR_ACCEPT_INTELLISENSE_KEY_NAME = new LocalizationEntry(
            "key.sfm.text_editor.accept_intellisense",
            "Text Editor - Accept Intellisense Suggestion"
    );

    public static final Lazy<KeyMapping> TEXT_EDITOR_ACCEPT_INTELLISENSE_KEY = Lazy.of(() -> new KeyMapping(
            TEXT_EDITOR_ACCEPT_INTELLISENSE_KEY_NAME.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            SFM_KEY_CATEGORY.key().get()
    ));

    public static KeyMapping[] getSFMKeyMappings() {

        return new KeyMapping[]{
                MORE_INFO_TOOLTIP_KEY.get(),
                CONTAINER_INSPECTOR_KEY.get(),
                ITEM_INSPECTOR_KEY.get(),
                CYCLE_LABEL_VIEW_KEY.get(),
                LABEL_GUN_PICK_BLOCK_MODIFIER_KEY.get(),
                LABEL_GUN_CONTIGUOUS_MODIFIER_KEY.get(),
                LABEL_GUN_CLEAR_MODIFIER_KEY.get(),
                LABEL_GUN_SCROLL_MODIFIER_KEY.get(),
                LABEL_GUN_NEXT_LABEL_KEY.get(),
                LABEL_GUN_PREVIOUS_LABEL_KEY.get(),
                LABEL_GUN_PULL_MODIFIER_KEY.get(),
                LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY.get(),
                TEXT_EDITOR_ACCEPT_INTELLISENSE_KEY.get(),
                MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY.get(),
                TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY.get(),
                TOGGLE_NETWORK_TOOL_OVERLAY_KEY.get()
        };
    }

    public static Component getKeyDisplay(KeyMapping key) {

        return key.getTranslatedKeyMessage().plainCopy().withStyle(ChatFormatting.AQUA);
    }

    public static Component getKeyDisplay(Supplier<KeyMapping> key) {

        return getKeyDisplay(key.get());
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void registerBindings(RegisterKeyMappingsEvent event) {

        for (KeyMapping key : getSFMKeyMappings()) {
            event.register(key);
        }
    }

    public static boolean isKeyDown(Supplier<KeyMapping> key) {

        KeyMapping keyMapping = key.get();
        if (keyMapping.getKey().equals(InputConstants.UNKNOWN)) {
            return false;
        }
        if (keyMapping.getKey().getType() == InputConstants.Type.MOUSE) {
            SFM.LOGGER.warn(
                    "Attempted to use a mouse key to check if InputConstants.isKeyDown, use .isDown directly on the KeyMapping instead: {}",
                    keyMapping.getKey()
            );
        }
        // We cannot use keyMapping.isDown because it fails when a screen is open
        // https://github.com/mekanism/Mekanism/blob/f92b48a49e0766cd3aa78e95c9c4a47ba90402f5/src/main/java/mekanism/client/key/MekKeyHandler.java
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        boolean keyDown = InputConstants.isKeyDown(
                windowHandle,
                keyMapping.getKey().getValue()
        );
        if (!keyDown) {
            return false;
        } else if (KeyModifier.isKeyCodeModifier(keyMapping.getKey())) {
            return true;
        } else {
            return keyMapping.getKeyModifier().isActive(KeyConflictContext.GUI);
        }
    }

}
