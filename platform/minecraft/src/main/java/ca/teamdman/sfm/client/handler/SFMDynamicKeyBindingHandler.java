package ca.teamdman.sfm.client.handler;

import ca.teamdman.sfm.client.keybinding.SFMKeyBindingEngine;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeyInputEvent;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
import ca.teamdman.sfm.common.event_bus.SFMSubscribeEvent;
import ca.teamdman.sfm.common.util.SFMDist;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.Set;

/**
 * Adapts Forge's ordered raw keyboard callback to the replayable SFM matcher.
 * Unlike polling a {@code KeyMapping} once per tick, this seam preserves
 * press/release order and multiple transitions occurring during one tick.
 */
public final class SFMDynamicKeyBindingHandler {
    private static long eventSequence;
    private static long clientTick;
    private static boolean windowFocused = true;

    private SFMDynamicKeyBindingHandler() {
    }

    @SFMSubscribeEvent(value = SFMDist.CLIENT)
    public static void onKey(InputEvent.Key event) {
        SFMKeyInputEvent.Type type = switch (event.getAction()) {
            case GLFW.GLFW_PRESS -> SFMKeyInputEvent.Type.PRESS;
            case GLFW.GLFW_RELEASE -> SFMKeyInputEvent.Type.RELEASE;
            case GLFW.GLFW_REPEAT -> SFMKeyInputEvent.Type.REPEAT;
            default -> null;
        };
        if (type == null) return;
        SFMKeyBindingService.INSTANCE.accept(new SFMKeyInputEvent(
                ++eventSequence,
                clientTick,
                event.getKey(),
                type,
                modifiers(event.getModifiers())
        ));
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
        SFMKeyBindingService.INSTANCE.reset(SFMKeyBindingEngine.ResetReason.FOCUS_LOST);
    }

    private static Set<SFMKeyModifier> modifiers(int mask) {
        EnumSet<SFMKeyModifier> result = EnumSet.noneOf(SFMKeyModifier.class);
        if ((mask & GLFW.GLFW_MOD_CONTROL) != 0) result.add(SFMKeyModifier.CONTROL);
        if ((mask & GLFW.GLFW_MOD_ALT) != 0) result.add(SFMKeyModifier.ALT);
        if ((mask & GLFW.GLFW_MOD_SHIFT) != 0) result.add(SFMKeyModifier.SHIFT);
        if ((mask & GLFW.GLFW_MOD_SUPER) != 0) result.add(SFMKeyModifier.SUPER);
        return result;
    }
}
