package ca.teamdman.sfm.gametest.puppet;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.Platform;

/** Test-only foreground-window control and evidence for visible-latency puppets. */
public final class SFMGamePuppetForegroundWindow {
    public record Observation(
            boolean glfwFocused,
            boolean iconified,
            boolean platformProbeAvailable,
            boolean platformForeground,
            boolean foregroundRequestAccepted,
            boolean threadInputAttached,
            long platformWindow,
            long platformForegroundWindow
    ) {
        public boolean readyForVisibleLatency() {
            return glfwFocused && !iconified && (!platformProbeAvailable || platformForeground);
        }
    }

    private SFMGamePuppetForegroundWindow() {
    }

    public static Observation request(Minecraft minecraft) {
        long window = minecraft.getWindow().getWindow();
        if (GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE) {
            GLFW.glfwRestoreWindow(window);
        }
        GLFW.glfwShowWindow(window);
        GLFW.glfwFocusWindow(window);

        boolean accepted = false;
        boolean attached = false;
        long nativeWindow = 0L;
        long foregroundWindow = 0L;
        boolean platformProbeAvailable = Platform.get() == Platform.WINDOWS;
        if (platformProbeAvailable) {
            nativeWindow = GLFWNativeWin32.glfwGetWin32Window(window);
            WinDef.HWND target = new WinDef.HWND(new Pointer(nativeWindow));
            WinDef.HWND previousForeground = User32.INSTANCE.GetForegroundWindow();
            int currentThread = Kernel32.INSTANCE.GetCurrentThreadId();
            int foregroundThread = previousForeground == null
                    ? 0
                    : User32.INSTANCE.GetWindowThreadProcessId(previousForeground, null);
            if (foregroundThread != 0 && foregroundThread != currentThread) {
                attached = User32.INSTANCE.AttachThreadInput(
                        new WinDef.DWORD(currentThread),
                        new WinDef.DWORD(foregroundThread),
                        true
                );
            }
            try {
                User32.INSTANCE.ShowWindow(target, WinUser.SW_RESTORE);
                User32.INSTANCE.BringWindowToTop(target);
                accepted = User32.INSTANCE.SetForegroundWindow(target);
                User32.INSTANCE.SetFocus(target);
            } finally {
                if (attached) {
                    User32.INSTANCE.AttachThreadInput(
                            new WinDef.DWORD(currentThread),
                            new WinDef.DWORD(foregroundThread),
                            false
                    );
                }
            }
            foregroundWindow = handleValue(User32.INSTANCE.GetForegroundWindow());
        }

        boolean glfwFocused = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_FOCUSED) == GLFW.GLFW_TRUE;
        boolean iconified = GLFW.glfwGetWindowAttrib(window, GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
        return new Observation(
                glfwFocused,
                iconified,
                platformProbeAvailable,
                !platformProbeAvailable || nativeWindow == foregroundWindow,
                accepted,
                attached,
                nativeWindow,
                foregroundWindow
        );
    }

    private static long handleValue(WinDef.HWND handle) {
        return handle == null ? 0L : Pointer.nativeValue(handle.getPointer());
    }
}
