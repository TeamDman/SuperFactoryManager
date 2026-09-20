package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Minecraft's raw GLFW key callback to client-side GameTest puppets. */
@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
    @Invoker("keyPress")
    @MCVersionDependentBehaviour
    void sfm$invokeKeyPress(
            long window,
            int keyCode,
            int scanCode,
            int action,
            int modifiers
    );
}
