package ca.teamdman.sfm.gametest.puppet;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.mixins.MouseHandlerInvoker;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Drives Minecraft's mouse callback with a harness-owned virtual pointer.
 *
 * <p>The logical target is converted to the native-window coordinates expected
 * by {@code MouseHandler#onMove}; the callback then performs the ordinary
 * vanilla GUI scaling and screen dispatch. No GLFW cursor setter or operating-
 * system input API is used, so a puppet cannot seize the user's desktop
 * pointer.</p>
 */
public final class SFMGamePuppetPointer {
    public record Position(
            double cachedNativeX,
            double cachedNativeY,
            double cachedLogicalX,
            double cachedLogicalY
    ) {
        public boolean callbackIsWithin(double expectedLogicalX, double expectedLogicalY, double tolerance) {
            return Math.abs(cachedLogicalX - expectedLogicalX) <= tolerance
                    && Math.abs(cachedLogicalY - expectedLogicalY) <= tolerance;
        }
    }

    private SFMGamePuppetPointer() {
    }

    /** Routes one virtual move through Minecraft's real mouse callback. */
    public static void moveVirtual(SFMScreenMultiplexer workspace, double logicalX, double logicalY) {
        requireTarget(workspace, logicalX, logicalY);
        Minecraft minecraft = Minecraft.getInstance();
        var window = minecraft.getWindow();
        double nativeX = logicalX
                * Math.max(1, window.getScreenWidth())
                / Math.max(1, window.getGuiScaledWidth());
        double nativeY = logicalY
                * Math.max(1, window.getScreenHeight())
                / Math.max(1, window.getGuiScaledHeight());
        ((MouseHandlerInvoker) minecraft.mouseHandler)
                .sfm$invokeOnMove(window.getWindow(), nativeX, nativeY);
    }

    /**
     * Injects only the isolated logical workspace event. Prefer
     * {@link #moveVirtual(SFMScreenMultiplexer, double, double)} unless a test
     * explicitly targets the workspace contract below Minecraft's callback.
     */
    public static void moveWorkspace(SFMScreenMultiplexer workspace, double logicalX, double logicalY) {
        requireTarget(workspace, logicalX, logicalY);
        workspace.mouseMoved(logicalX, logicalY);
    }

    /** Routes one virtual button transition through Minecraft's real mouse callback. */
    public static void buttonVirtual(int button, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) {
            throw new IllegalArgumentException("Puppet mouse action must be GLFW_PRESS or GLFW_RELEASE");
        }
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        ((MouseHandlerInvoker) minecraft.mouseHandler)
                .sfm$invokeOnPress(window, button, action, modifiers);
    }

    /** Moves, presses, and releases through Minecraft without moving the OS cursor. */
    public static void clickVirtual(
            SFMScreenMultiplexer workspace,
            double logicalX,
            double logicalY,
            int button,
            int modifiers
    ) {
        moveVirtual(workspace, logicalX, logicalY);
        buttonVirtual(button, GLFW.GLFW_PRESS, modifiers);
        buttonVirtual(button, GLFW.GLFW_RELEASE, modifiers);
    }

    /** Routes one virtual wheel transition through Minecraft's real mouse callback. */
    public static void scrollVirtual(double horizontal, double vertical) {
        scrollVirtual(horizontal, vertical, 0);
    }

    public static void scrollVirtual(double horizontal, double vertical, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        long window = minecraft.getWindow().getWindow();
        ca.teamdman.sfm.client.input.SFMPointerInputModifiers.during(modifiers, () -> {
            ((MouseHandlerInvoker) minecraft.mouseHandler)
                    .sfm$invokeOnScroll(window, horizontal, vertical);
            return null;
        });
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
        double cachedNativeX = minecraft.mouseHandler.xpos();
        double cachedNativeY = minecraft.mouseHandler.ypos();
        double cachedLogicalX = cachedNativeX
                * Math.max(1, window.getGuiScaledWidth())
                / Math.max(1, window.getScreenWidth());
        double cachedLogicalY = cachedNativeY
                * Math.max(1, window.getGuiScaledHeight())
                / Math.max(1, window.getScreenHeight());
        return new Position(
                cachedNativeX,
                cachedNativeY,
                cachedLogicalX,
                cachedLogicalY
        );
    }
}
