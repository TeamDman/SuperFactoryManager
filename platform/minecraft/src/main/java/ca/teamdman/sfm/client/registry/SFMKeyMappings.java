package ca.teamdman.sfm.client.registry;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.common.localization.LocalizationKeys;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.util.Lazy;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;


@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = SFM.MOD_ID, value = Dist.CLIENT)

public class SFMKeyMappings {
    public static final Lazy<KeyMapping> MORE_INFO_TOOLTIP_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.MORE_HOVER_INFO_KEY.key().get(),
            KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> CYCLE_LABEL_VIEW_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.CYCLE_LABEL_VIEW_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));
    public static final Lazy<KeyMapping> TOGGLE_NETWORK_TOOL_OVERLAY_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.TOGGLE_NETWORK_TOOL_OVERLAY.key().get(),
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> CONTAINER_INSPECTOR_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.CONTAINER_INSPECTOR_TOGGLE_KEY.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> ITEM_INSPECTOR_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.ITEM_INSPECTOR_TOGGLE_KEY.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
//            GLFW.GLFW_KEY_GRAVE_ACCENT,
            InputConstants.UNKNOWN.getValue(),
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_PICK_BLOCK_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_PICK_BLOCK_MODIFIER_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_CONTIGUOUS_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_CONTIGUOUS_MODIFIER_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_CLEAR_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_CLEAR_MODIFIER_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_SCROLL_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_SCROLL_MODIFIER_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_NEXT_LABEL_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_NEXT_LABEL_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_PREVIOUS_LABEL_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_PREVIOUS_LABEL_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_PULL_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_PULL_MODIFIER_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.LABEL_GUN_TARGET_MANAGER_MODIFIER_KEY.key().get(),
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_ALT,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.MANAGER_SCREEN_OPEN_TEXT_EDITOR_KEY.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
    ));

    public static final Lazy<KeyMapping> TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY = Lazy.of(() -> new KeyMapping(
            LocalizationKeys.TITLE_SCREEN_OPEN_TEXT_EDITOR_KEY.key().get(),
            KeyConflictContext.GUI,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            LocalizationKeys.SFM_KEY_CATEGORY.key().get()
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

    @SubscribeEvent
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
            SFM.LOGGER.warn("Attempted to use a mouse key to check if InputConstants.isKeyDown, use .isDown directly on the KeyMapping instead: {}", keyMapping.getKey());
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
