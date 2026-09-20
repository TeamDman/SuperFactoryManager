package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.client.handler.SFMDynamicKeyBindingHandler;
import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Supplies the pre-Forge key ingress skipped by Vanilla reserved mappings. */
@Mixin(KeyboardHandler.class)
public final class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    @MCVersionDependentBehaviour
    private void handleSfmReservedDynamicBinding(
            long window,
            int keyCode,
            int scanCode,
            int action,
            int modifiers,
            CallbackInfo callback
    ) {
        if (SFMClientOverlayRuntime.get().key(window, keyCode, scanCode, action, modifiers)) {
            callback.cancel();
            return;
        }
        if (action != GLFW.GLFW_PRESS) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()) return;
        if (!minecraft.options.keyFullscreen.matches(keyCode, scanCode)
                && !minecraft.options.keyScreenshot.matches(keyCode, scanCode)) return;
        if (SFMDynamicKeyBindingHandler.onPreVanillaReservedKeyPressed(
                minecraft.screen,
                keyCode,
                scanCode,
                modifiers)) {
            callback.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    @MCVersionDependentBehaviour
    private void handleSfmOverlayCharacter(
            long window,
            int codePoint,
            int modifiers,
            CallbackInfo callback
    ) {
        if (SFMClientOverlayRuntime.get().character(window, codePoint, modifiers)) callback.cancel();
    }
}
