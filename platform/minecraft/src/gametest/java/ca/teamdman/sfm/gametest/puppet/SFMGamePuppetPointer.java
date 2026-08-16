package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

/** Keeps native GLFW pointer movement and logical workspace injection explicit and independently observable. */
public final class SFMGamePuppetPointer {
    public record Position(
            double nativeX,
            double nativeY,
            double cachedNativeX,
            double cachedNativeY,
            double logicalX,
            double logicalY,
            double cachedLogicalX,
            double cachedLogicalY
    ) {
        public boolean isWithin(double expectedLogicalX, double expectedLogicalY, double tolerance) {
            return Math.abs(logicalX - expectedLogicalX) <= tolerance
                    && Math.abs(logicalY - expectedLogicalY) <= tolerance
                    && Math.abs(cachedLogicalX - expectedLogicalX) <= tolerance
                    && Math.abs(cachedLogicalY - expectedLogicalY) <= tolerance;
        }
    }

    private SFMGamePuppetPointer() {
    }

    /** Moves only the real GLFW pointer. Minecraft's callback updates its own cache asynchronously. */
    public static void moveNative(SFMScreenMultiplexer workspace, double logicalX, double logicalY) {
        requireTarget(workspace, logicalX, logicalY);
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double nativeX = logicalX
                * Math.max(1, window.getScreenWidth())
                / Math.max(1, window.getGuiScaledWidth());
        double nativeY = logicalY
                * Math.max(1, window.getScreenHeight())
                / Math.max(1, window.getGuiScaledHeight());
        GLFW.glfwSetCursorPos(window.getWindow(), nativeX, nativeY);
    }

    /** Injects only the logical workspace event; it never claims to move the OS pointer. */
    public static void moveWorkspace(SFMScreenMultiplexer workspace, double logicalX, double logicalY) {
        requireTarget(workspace, logicalX, logicalY);
        workspace.mouseMoved(logicalX, logicalY);
    }

    private static void requireTarget(SFMScreenMultiplexer workspace, double logicalX, double logicalY) {
        if (!Double.isFinite(logicalX) || !Double.isFinite(logicalY)) {
            throw new IllegalArgumentException("Puppet pointer coordinates must be finite");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != workspace) {
            throw new IllegalStateException("The puppet pointer target is not the active screen");
        }
    }

    public static Position current() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double nativeX;
        double nativeY;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(window.getWindow(), x, y);
            nativeX = x.get(0);
            nativeY = y.get(0);
        }
        double cachedNativeX = minecraft.mouseHandler.xpos();
        double cachedNativeY = minecraft.mouseHandler.ypos();
        double logicalX = nativeX
                * Math.max(1, window.getGuiScaledWidth())
                / Math.max(1, window.getScreenWidth());
        double logicalY = nativeY
                * Math.max(1, window.getGuiScaledHeight())
                / Math.max(1, window.getScreenHeight());
        double cachedLogicalX = cachedNativeX
                * Math.max(1, window.getGuiScaledWidth())
                / Math.max(1, window.getScreenWidth());
        double cachedLogicalY = cachedNativeY
                * Math.max(1, window.getGuiScaledHeight())
                / Math.max(1, window.getScreenHeight());
        return new Position(
                nativeX,
                nativeY,
                cachedNativeX,
                cachedNativeY,
                logicalX,
                logicalY,
                cachedLogicalX,
                cachedLogicalY
        );
    }
}
