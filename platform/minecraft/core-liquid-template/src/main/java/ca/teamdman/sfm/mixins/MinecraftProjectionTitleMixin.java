package ca.teamdman.sfm.mixins;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the catalog key to a demo client's normal, dynamically refreshed title. */
@Mixin(Minecraft.class)
public abstract class MinecraftProjectionTitleMixin {
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true, remap = false)
    private void sfm$appendProjectionTitle(CallbackInfoReturnable<String> result) {
        String projectionKey = System.getProperty("sfm.clientRun.projectionKey");
        if (projectionKey == null || projectionKey.isBlank() || projectionKey.length() > 160) {
            return;
        }
        for (int index = 0; index < projectionKey.length(); index++) {
            if (Character.isISOControl(projectionKey.charAt(index))) {
                return;
            }
        }
        result.setReturnValue(result.getReturnValue() + " | " + projectionKey);
    }
}
