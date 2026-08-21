package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
import ca.teamdman.sfm.properties.SFMProperties;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the operating-system cursor available while an SFM client puppet is
 * running. Vanilla {@link MouseHandler#grabMouse()} recentres and captures the
 * cursor, which makes a puppet run disruptive to use alongside other tools.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    @MCVersionDependentBehaviour
    private void routeSfmOverlayButton(
            long window,
            int button,
            int action,
            int modifiers,
            CallbackInfo callback
    ) {
        if (SFMClientOverlayRuntime.get().mouseButton(window, button, action, modifiers)) callback.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    @MCVersionDependentBehaviour
    private void routeSfmOverlayScroll(
            long window,
            double horizontal,
            double vertical,
            CallbackInfo callback
    ) {
        if (SFMClientOverlayRuntime.get().mouseScroll(window, horizontal, vertical)) callback.cancel();
    }

    @Inject(method = "onMove", at = @At("HEAD"))
    @MCVersionDependentBehaviour
    private void observeSfmOverlayPointer(
            long window,
            double x,
            double y,
            CallbackInfo callback
    ) {
        SFMClientOverlayRuntime.get().mouseMoved(window, x, y);
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void preventPuppetMouseGrab(CallbackInfo ci) {
        if (SFMProperties.clientRunMode().isPuppet()) {
            ci.cancel();
        }
    }
}
