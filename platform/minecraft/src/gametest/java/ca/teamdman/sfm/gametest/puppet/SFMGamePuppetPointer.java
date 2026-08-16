package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Keeps the native GLFW pointer and logical workspace callbacks in agreement. */
public final class SFMGamePuppetPointer {
    private static final Method MOUSE_ON_MOVE = mouseOnMove();

    public record Position(double nativeX, double nativeY, double logicalX, double logicalY) {
        public boolean isWithin(double expectedLogicalX, double expectedLogicalY, double tolerance) {
            return Math.abs(logicalX - expectedLogicalX) <= tolerance
                    && Math.abs(logicalY - expectedLogicalY) <= tolerance;
        }
    }

    private SFMGamePuppetPointer() {
    }

    public static void move(SFMScreenMultiplexer workspace, double logicalX, double logicalY) {
        if (!Double.isFinite(logicalX) || !Double.isFinite(logicalY)) {
            throw new IllegalArgumentException("Puppet pointer coordinates must be finite");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != workspace) {
            throw new IllegalStateException("The puppet pointer target is not the active screen");
        }
        var window = minecraft.getWindow();
        double nativeX = logicalX
                * Math.max(1, window.getScreenWidth())
                / Math.max(1, window.getGuiScaledWidth());
        double nativeY = logicalY
                * Math.max(1, window.getScreenHeight())
                / Math.max(1, window.getGuiScaledHeight());
        GLFW.glfwSetCursorPos(window.getWindow(), nativeX, nativeY);
        try {
            MOUSE_ON_MOVE.invoke(minecraft.mouseHandler, window.getWindow(), nativeX, nativeY);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not invoke Minecraft's mouse-move callback", failure);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Minecraft's mouse-move callback failed", cause);
        }
    }

    public static Position current() {
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double nativeX = minecraft.mouseHandler.xpos();
        double nativeY = minecraft.mouseHandler.ypos();
        double logicalX = nativeX
                * Math.max(1, window.getGuiScaledWidth())
                / Math.max(1, window.getScreenWidth());
        double logicalY = nativeY
                * Math.max(1, window.getGuiScaledHeight())
                / Math.max(1, window.getScreenHeight());
        return new Position(nativeX, nativeY, logicalX, logicalY);
    }

    private static Method mouseOnMove() {
        try {
            Method method = MouseHandler.class.getDeclaredMethod(
                    "onMove", long.class, double.class, double.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
