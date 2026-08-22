package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.keybinding.SFMKeyBindingEngine;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingMatchResult;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeyInputEvent;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextSnapshot;
import ca.teamdman.sfm.client.keybinding.SFMKeyboardUsageContextProvider;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Matches dynamic bindings at Forge's cancellable pre-screen boundary.
 * Raw input remains a compatibility path for explicit-global relationships
 * while no GUI is open.
 */
public final class SFMDynamicKeyBindingHandler {
    private static long clientTick;
    private static boolean windowFocused = true;
    private static final Set<Integer> PRESSED_KEYS = new HashSet<>();
    private static final Set<Integer> CONSUMED_KEYS = new HashSet<>();
    private static final Set<Integer> CHARACTER_SUPPRESSION_KEYS = new HashSet<>();
    private static final ThreadLocal<ScreenEventMarker> SCREEN_EVENT = new ThreadLocal<>();
    private static @Nullable Screen activeInputScreen;

    private SFMDynamicKeyBindingHandler() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    @MCVersionDependentBehaviour
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        markScreenEvent(event.getKeyCode(), event.getScanCode(), false, event.getModifiers());
        if (acceptScreenPress(event.getScreen(), event.getKeyCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    /**
     * Mixin ingress for Vanilla's screenshot/fullscreen press paths, which
     * return before Forge emits ScreenEvent/InputEvent on Minecraft 1.19.2.
     */
    @MCVersionDependentBehaviour
    public static boolean onPreVanillaReservedKeyPressed(
            @Nullable Screen screen,
            int keyCode,
            int modifiers
    ) {
        return acceptScreenPress(screen, keyCode, modifiers);
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT, receiveCanceled = true)
    @MCVersionDependentBehaviour
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        markScreenEvent(event.getKeyCode(), event.getScanCode(), true, event.getModifiers());
        synchronizeScreen(event.getScreen());
        PRESSED_KEYS.remove(event.getKeyCode());
        CHARACTER_SUPPRESSION_KEYS.remove(event.getKeyCode());
        if (CONSUMED_KEYS.remove(event.getKeyCode())) event.setCanceled(true);
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT, receiveCanceled = true)
    @MCVersionDependentBehaviour
    public static void onScreenCharacterTyped(ScreenEvent.CharacterTyped.Pre event) {
        synchronizeScreen(event.getScreen());
        if (!CHARACTER_SUPPRESSION_KEYS.isEmpty()) event.setCanceled(true);
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onKey(InputEvent.Key event) {
        if (consumeScreenEvent(
                event.getKey(),
                event.getScanCode(),
                event.getAction() == GLFW.GLFW_RELEASE,
                event.getModifiers())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        synchronizeScreen(null);
        SFMKeyInputEvent.Type type = switch (event.getAction()) {
            case GLFW.GLFW_PRESS -> SFMKeyInputEvent.Type.PRESS;
            case GLFW.GLFW_RELEASE -> SFMKeyInputEvent.Type.RELEASE;
            case GLFW.GLFW_REPEAT -> SFMKeyInputEvent.Type.REPEAT;
            default -> null;
        };
        if (type == null) return;
        if (type == SFMKeyInputEvent.Type.PRESS && !PRESSED_KEYS.add(event.getKey())) return;
        if (type == SFMKeyInputEvent.Type.RELEASE) {
            PRESSED_KEYS.remove(event.getKey());
            CONSUMED_KEYS.remove(event.getKey());
            CHARACTER_SUPPRESSION_KEYS.remove(event.getKey());
        }
        SFMKeyBindingMatchResult match = SFMKeyBindingService.INSTANCE.acceptKey(
                event.getKey(),
                type,
                modifiers(event.getModifiers()),
                SFMKeyboardUsageContextSnapshot.global(
                        null,
                        () -> Minecraft.getInstance().screen == null));
        if (type == SFMKeyInputEvent.Type.PRESS && match.consumed()) {
            CONSUMED_KEYS.add(event.getKey());
            CHARACTER_SUPPRESSION_KEYS.add(event.getKey());
        }
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean focused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        if (windowFocused && !focused) resetForFocusLoss();
        windowFocused = focused;
        SFMKeyBindingService.INSTANCE.advanceTime(++clientTick);
    }

    public static void resetForFocusLoss() {
        PRESSED_KEYS.clear();
        CONSUMED_KEYS.clear();
        CHARACTER_SUPPRESSION_KEYS.clear();
        SFMKeyBindingService.INSTANCE.reset(SFMKeyBindingEngine.ResetReason.FOCUS_LOST);
    }

    private static SFMKeyboardUsageContextSnapshot contextFor(@Nullable Screen screen) {
        if (screen instanceof SFMKeyboardUsageContextProvider provider) {
            return provider.keyboardUsageContextSnapshot();
        }
        return SFMKeyboardUsageContextSnapshot.global(
                screen,
                () -> Minecraft.getInstance().screen == screen);
    }

    private static boolean acceptScreenPress(
            @Nullable Screen screen,
            int keyCode,
            int modifierMask
    ) {
        synchronizeScreen(screen);
        boolean firstPress = PRESSED_KEYS.add(keyCode);
        if (CONSUMED_KEYS.contains(keyCode)) return true;
        if (!firstPress) return false;
        SFMKeyBindingMatchResult match = SFMKeyBindingService.INSTANCE.acceptKey(
                keyCode,
                SFMKeyInputEvent.Type.PRESS,
                modifiers(modifierMask),
                contextFor(screen));
        if (!match.consumed()) return false;
        CONSUMED_KEYS.add(keyCode);
        CHARACTER_SUPPRESSION_KEYS.add(keyCode);
        return true;
    }

    private static void synchronizeScreen(@Nullable Screen screen) {
        if (activeInputScreen == screen) return;
        activeInputScreen = screen;
        SFMKeyBindingService.INSTANCE.reset(SFMKeyBindingEngine.ResetReason.CONTEXT_CHANGED);
    }

    private static void markScreenEvent(int keyCode, int scanCode, boolean release, int modifiers) {
        SCREEN_EVENT.set(new ScreenEventMarker(keyCode, scanCode, release, modifiers));
    }

    private static boolean consumeScreenEvent(int keyCode, int scanCode, boolean release, int modifiers) {
        ScreenEventMarker marker = SCREEN_EVENT.get();
        SCREEN_EVENT.remove();
        return marker != null
                && marker.keyCode == keyCode
                && marker.scanCode == scanCode
                && marker.release == release
                && marker.modifiers == modifiers;
    }

    private static Set<SFMKeyModifier> modifiers(int mask) {
        EnumSet<SFMKeyModifier> result = EnumSet.noneOf(SFMKeyModifier.class);
        if ((mask & GLFW.GLFW_MOD_CONTROL) != 0) result.add(SFMKeyModifier.CONTROL);
        if ((mask & GLFW.GLFW_MOD_ALT) != 0) result.add(SFMKeyModifier.ALT);
        if ((mask & GLFW.GLFW_MOD_SHIFT) != 0) result.add(SFMKeyModifier.SHIFT);
        if ((mask & GLFW.GLFW_MOD_SUPER) != 0) result.add(SFMKeyModifier.SUPER);
        return result;
    }

    private record ScreenEventMarker(int keyCode, int scanCode, boolean release, int modifiers) {
    }
}
