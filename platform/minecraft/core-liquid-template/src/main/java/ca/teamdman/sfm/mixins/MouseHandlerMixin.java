package ca.teamdman.sfm.mixins;

{% if features.client_overlay_input and features.client_overlay_scenes and features.workspace_panels %}
import ca.teamdman.sfm.client.overlay.scene.SFMClientOverlayRuntime;
{% endif %}
{% if features.game_puppet_runtime %}
import ca.teamdman.sfm.properties.SFMProperties;
{% endif %}
{% if features.pointer_modifier_ingress or features.client_overlay_input and features.client_overlay_scenes and features.workspace_panels %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endif %}
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

{% if features.game_puppet_runtime %}
/**
 * Keeps the operating-system cursor available while an SFM client puppet is
 * running. Vanilla {@link MouseHandler#grabMouse()} recentres and captures the
 * cursor, which makes a puppet run disruptive to use alongside other tools.
 */
{% endif %}
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
{% if features.pointer_modifier_ingress or features.client_overlay_input and features.client_overlay_scenes and features.workspace_panels %}
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    @MCVersionDependentBehaviour
    private void routeSfmOverlayButton(
            long window,
            int button,
            int action,
            int modifiers,
            CallbackInfo callback
    ) {
{% if features.pointer_modifier_ingress %}
        ca.teamdman.sfm.client.input.SFMPointerInputModifiers.begin(modifiers);
{% endif %}
{% if features.client_overlay_input and features.client_overlay_scenes and features.workspace_panels %}
{% if features.pointer_modifier_ingress %}
        try {
            if (SFMClientOverlayRuntime.get().mouseButton(window, button, action, modifiers)) {
                ca.teamdman.sfm.client.input.SFMPointerInputModifiers.end();
                callback.cancel();
            }
        } catch (RuntimeException | Error failure) {
            ca.teamdman.sfm.client.input.SFMPointerInputModifiers.end();
            throw failure;
        }
{% else %}
        if (SFMClientOverlayRuntime.get().mouseButton(window, button, action, modifiers)) {
            callback.cancel();
        }
{% endif %}
{% endif %}
    }
{% endif %}
{% if features.pointer_modifier_ingress %}

    @Inject(method = "onPress", at = @At("RETURN"))
    @MCVersionDependentBehaviour
    private void finishSfmPointerModifiers(long window, int button, int action, int modifiers, CallbackInfo callback) {
        ca.teamdman.sfm.client.input.SFMPointerInputModifiers.end();
    }
{% endif %}
{% if features.client_overlay_input and features.client_overlay_scenes and features.workspace_panels %}

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
{% endif %}
{% if features.game_puppet_runtime %}
{% if features.pointer_modifier_ingress or features.client_overlay_input and features.client_overlay_scenes and features.workspace_panels %}

{% endif %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
{% else %}
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true, remap = false)
{% endcase %}
    private void preventPuppetMouseGrab(CallbackInfo ci) {
        if (SFMProperties.clientRunMode().isPuppet()) {
            ci.cancel();
        }
    }
{% endif %}
}
