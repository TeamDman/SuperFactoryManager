package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** One ref-counted observer of Minecraft's GLFW callbacks. */
final class SFMRawInputDiagnosticTap {
    private static final SFMRawInputDiagnosticTap INSTANCE = new SFMRawInputDiagnosticTap();

    private final List<Consumer<String>> subscribers = new ArrayList<>();
    private long window;
    private GLFWKeyCallback previousKeyCallback;
    private GLFWKeyCallbackI keyCallback;
    private GLFWScrollCallback previousScrollCallback;
    private GLFWScrollCallbackI scrollCallback;

    private SFMRawInputDiagnosticTap() {
    }

    static Subscription subscribe(Consumer<String> subscriber) {
        return INSTANCE.add(Objects.requireNonNull(subscriber));
    }

    private synchronized Subscription add(Consumer<String> subscriber) {
        subscribers.add(subscriber);
        installIfNecessary();
        subscriber.accept("raw_input.subscribe subscribers=" + subscribers.size());
        return new Subscription(this, subscriber);
    }

    private void installIfNecessary() {
        Minecraft minecraft = Minecraft.getInstance();
        long requestedWindow = minecraft.getWindow().getWindow();
        if (keyCallback != null && requestedWindow == window) return;
        restore();
        window = requestedWindow;
        keyCallback = (callbackWindow, key, scanCode, action, modifiers) -> {
            if (callbackWindow == window) {
                publish("glfwKey key=%d scan=%d name=%s action=%s modifiers=%s active=%s".formatted(
                        key,
                        scanCode,
                        keyName(key, scanCode),
                        keyActionName(action),
                        modifierMask(modifiers),
                        activeModifiers()
                ));
            }
            if (previousKeyCallback != null) {
                previousKeyCallback.invoke(callbackWindow, key, scanCode, action, modifiers);
            }
        };
        scrollCallback = (callbackWindow, xOffset, yOffset) -> {
            if (callbackWindow == window) {
                publish("glfwScroll xOffset=%.3f yOffset=%.3f active=%s".formatted(
                        xOffset,
                        yOffset,
                        activeModifiers()
                ));
            }
            if (previousScrollCallback != null) {
                previousScrollCallback.invoke(callbackWindow, xOffset, yOffset);
            }
        };
        previousKeyCallback = GLFW.glfwSetKeyCallback(window, keyCallback);
        previousScrollCallback = GLFW.glfwSetScrollCallback(window, scrollCallback);
        publish("raw_input.callbacks.install window=" + window);
    }

    private synchronized void remove(Consumer<String> subscriber) {
        subscribers.remove(subscriber);
        if (subscribers.isEmpty()) restore();
    }

    private synchronized void publish(String event) {
        for (Consumer<String> subscriber : List.copyOf(subscribers)) {
            subscriber.accept(event);
        }
    }

    private void restore() {
        if (keyCallback != null) GLFW.glfwSetKeyCallback(window, previousKeyCallback);
        if (scrollCallback != null) GLFW.glfwSetScrollCallback(window, previousScrollCallback);
        keyCallback = null;
        previousKeyCallback = null;
        scrollCallback = null;
        previousScrollCallback = null;
        window = 0L;
    }

    static String keyName(int keyCode, int scanCode) {
        try {
            return InputConstants.getKey(keyCode, scanCode).getDisplayName().getString();
        } catch (RuntimeException ignored) {
            return "<unknown>";
        }
    }

    static String modifierMask(int modifiers) {
        List<String> names = new ArrayList<>();
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) names.add("shift");
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) names.add("control");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) names.add("alt");
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) names.add("super");
        if ((modifiers & GLFW.GLFW_MOD_CAPS_LOCK) != 0) names.add("caps");
        if ((modifiers & GLFW.GLFW_MOD_NUM_LOCK) != 0) names.add("num");
        return names.isEmpty() ? "none" : String.join("+", names);
    }

    static String activeModifiers() {
        List<String> names = new ArrayList<>();
        if (Screen.hasShiftDown()) names.add("shift");
        if (Screen.hasControlDown()) names.add("control");
        if (Screen.hasAltDown()) names.add("alt");
        return names.isEmpty() ? "none" : String.join("+", names);
    }

    private static String keyActionName(int action) {
        return switch (action) {
            case GLFW.GLFW_PRESS -> "press";
            case GLFW.GLFW_RELEASE -> "release";
            case GLFW.GLFW_REPEAT -> "repeat";
            default -> Integer.toString(action);
        };
    }

    static final class Subscription implements AutoCloseable {
        private SFMRawInputDiagnosticTap owner;
        private Consumer<String> subscriber;

        private Subscription(SFMRawInputDiagnosticTap owner, Consumer<String> subscriber) {
            this.owner = owner;
            this.subscriber = subscriber;
        }

        @Override
        public void close() {
            if (owner == null) return;
            owner.remove(subscriber);
            owner = null;
            subscriber = null;
        }
    }
}
