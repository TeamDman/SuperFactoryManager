package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.properties.SFMProperties;
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
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void preventPuppetMouseGrab(CallbackInfo ci) {
        if (SFMProperties.clientRunMode().isPuppet()) {
            ci.cancel();
        }
    }
}
