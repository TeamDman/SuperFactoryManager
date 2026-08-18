package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.Platform;

import java.nio.DoubleBuffer;

/** Keeps native GLFW pointer movement and logical workspace injection explicit and independently observable. */
public final class SFMGamePuppetPointer {
    private static final int WINDOWS_MESSAGE_MOUSE_MOVE = 0x0200;

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
        public boolean glfwIsWithin(double expectedLogicalX, double expectedLogicalY, double tolerance) {
            return Math.abs(logicalX - expectedLogicalX) <= tolerance
                    && Math.abs(logicalY - expectedLogicalY) <= tolerance;
        }

        public boolean callbackIsWithin(double expectedLogicalX, double expectedLogicalY, double tolerance) {
            return Math.abs(cachedLogicalX - expectedLogicalX) <= tolerance
                    && Math.abs(cachedLogicalY - expectedLogicalY) <= tolerance;
        }
    }

    private SFMGamePuppetPointer() {
    }

    /**
     * Moves only the native pointer route. Minecraft's callback updates its
     * own cache asynchronously. Windows receives an explicit client-area
     * mouse-move message as well, because isolated desktops can reject the OS
     * foreground transfer required by GLFW's cursor setter while still
     * dispatching messages to the target game window.
     */
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
        if (Platform.get() == Platform.WINDOWS) {
            long nativeWindow = GLFWNativeWin32.glfwGetWin32Window(window.getWindow());
            if (nativeWindow != 0L) {
                int x = (int) Math.round(nativeX);
                int y = (int) Math.round(nativeY);
                long packedCoordinates = (x & 0xffffL) | ((y & 0xffffL) << 16);
                User32.INSTANCE.PostMessage(
                        new WinDef.HWND(new Pointer(nativeWindow)),
                        WINDOWS_MESSAGE_MOUSE_MOVE,
                        new WinDef.WPARAM(0L),
                        new WinDef.LPARAM(packedCoordinates)
                );
            }
        }
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
