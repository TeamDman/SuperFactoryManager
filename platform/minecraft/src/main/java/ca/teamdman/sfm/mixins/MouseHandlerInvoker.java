package ca.teamdman.sfm.mixins;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes Minecraft's raw GLFW mouse callbacks to client-side GameTest puppets. */
@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onMove")
    @MCVersionDependentBehaviour
    void sfm$invokeOnMove(long window, double nativeX, double nativeY);

    @Invoker("onPress")
    @MCVersionDependentBehaviour
    void sfm$invokeOnPress(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    @MCVersionDependentBehaviour
    void sfm$invokeOnScroll(long window, double horizontal, double vertical);
}
