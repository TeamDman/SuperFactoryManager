package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import java.util.ArrayList;
import java.util.List;

public class SFMInputDiagnosticsScreen extends Screen {
    private static final int BACKGROUND = 0xE0101010;
    private static final int PANEL = 0xE0202020;
    private static final int BORDER = 0xFF606060;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFB0B0B0;
    private static final int EVENT_LIMIT = 500;

    private final Screen previousScreen;
    private final List<String> events = new ArrayList<>();
    private int nextEventId;
    private int scrollOffset;
    private long rawScrollCallbackWindow;
    private GLFWScrollCallback previousRawScrollCallback;
    private GLFWScrollCallbackI rawScrollCallback;
    private long rawKeyCallbackWindow;
    private GLFWKeyCallback previousRawKeyCallback;
    private GLFWKeyCallbackI rawKeyCallback;

    public SFMInputDiagnosticsScreen(Screen previousScreen) {
        super(Component.literal("SFM Input Diagnostics"));
        this.previousScreen = previousScreen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        restoreRawKeyCallback();
        restoreRawScrollCallback();
        Minecraft.getInstance().setScreen(previousScreen);
    }

    @Override
    public void removed() {
        restoreRawKeyCallback();
        restoreRawScrollCallback();
        super.removed();
    }

    @Override
    protected void init() {
        super.init();
        installRawKeyCallback();
        installRawScrollCallback();
        int y = this.height - 24;
        this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(8, y)
                .setSize(60, 20)
                .setText(Component.literal("Clear"))
                .setOnPress(this::clearEvents)
                .build());
        this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(74, y)
                .setSize(60, 20)
                .setText(Component.literal("Copy"))
                .setOnPress(this::copyEvents)
                .build());
        this.addRenderableWidget(new SFMButtonBuilder()
                .setPosition(this.width - 88, y)
                .setSize(80, 20)
                .setText(CommonComponents.GUI_DONE)
                .setOnPress(button -> this.onClose())
                .build());
    }

    @MCVersionDependentBehaviour
    private void installRawScrollCallback() {
        if (rawScrollCallback != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        rawScrollCallbackWindow = minecraft.getWindow().handle();
        rawScrollCallback = (window, xOffset, yOffset) -> {
            if (window == rawScrollCallbackWindow && minecraft.screen == this) {
                log(
                        "glfwScroll xOffset=%.3f yOffset=%.3f active=%s",
                        xOffset,
                        yOffset,
                        activeModifiers()
                );
            }
            if (previousRawScrollCallback != null) {
                previousRawScrollCallback.invoke(window, xOffset, yOffset);
            }
        };
        previousRawScrollCallback = GLFW.glfwSetScrollCallback(rawScrollCallbackWindow, rawScrollCallback);
        log("screen.raw_scroll_callback.install window=%d", rawScrollCallbackWindow);
    }

    @MCVersionDependentBehaviour
    private void installRawKeyCallback() {
        if (rawKeyCallback != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        rawKeyCallbackWindow = minecraft.getWindow().handle();
        rawKeyCallback = (window, key, scanCode, action, modifiers) -> {
            if (window == rawKeyCallbackWindow && minecraft.screen == this) {
                log(
                        "glfwKey key=%d scan=%d name=%s action=%s modifiers=%s active=%s",
                        key,
                        scanCode,
                        keyName(key, scanCode),
                        keyActionName(action),
                        modifierMask(modifiers),
                        activeModifiers()
                );
            }
            if (previousRawKeyCallback != null) {
                previousRawKeyCallback.invoke(window, key, scanCode, action, modifiers);
            }
        };
        previousRawKeyCallback = GLFW.glfwSetKeyCallback(rawKeyCallbackWindow, rawKeyCallback);
        log("screen.raw_key_callback.install window=%d", rawKeyCallbackWindow);
    }

    private void restoreRawScrollCallback() {
        if (rawScrollCallback == null) {
            return;
        }
        GLFW.glfwSetScrollCallback(rawScrollCallbackWindow, previousRawScrollCallback);
        rawScrollCallback = null;
        previousRawScrollCallback = null;
        rawScrollCallbackWindow = 0L;
    }

    private void restoreRawKeyCallback() {
        if (rawKeyCallback == null) {
            return;
        }
        GLFW.glfwSetKeyCallback(rawKeyCallbackWindow, previousRawKeyCallback);
        rawKeyCallback = null;
        previousRawKeyCallback = null;
        rawKeyCallbackWindow = 0L;
    }

    private void clearEvents(Button button) {
        events.clear();
        scrollOffset = 0;
        log("screen.clear");
    }

    private void copyEvents(Button button) {
        Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", events));
        log("screen.copy count=%d", events.size());
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean keyPressed(
            KeyEvent event
    ) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        log(
                "keyPressed key=%d scan=%d name=%s modifiers=%s active=%s",
                keyCode,
                scanCode,
                keyName(keyCode, scanCode),
                modifierMask(modifiers),
                activeModifiers()
        );
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }
        return true;
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean keyReleased(
            KeyEvent event
    ) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
        log(
                "keyReleased key=%d scan=%d name=%s modifiers=%s active=%s",
                keyCode,
                scanCode,
                keyName(keyCode, scanCode),
                modifierMask(modifiers),
                activeModifiers()
        );
        return true;
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean charTyped(
            CharacterEvent event
    ) {
        int codePoint = event.codepoint();
        log(
                "charTyped char=%s codepoint=U+%04X active=%s",
                charDisplay(codePoint),
                codePoint,
                activeModifiers()
        );
        return true;
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean mouseClicked(
            MouseButtonEvent event,
            boolean doubleClick
    ) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        log("mouseClicked x=%.1f y=%.1f button=%d active=%s", mouseX, mouseY, button, activeModifiers());
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean mouseReleased(
            MouseButtonEvent event
    ) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        log("mouseReleased x=%.1f y=%.1f button=%d active=%s", mouseX, mouseY, button, activeModifiers());
        return super.mouseReleased(event);
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean mouseDragged(
            MouseButtonEvent event,
            double dragX,
            double dragY
    ) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        log(
                "mouseDragged x=%.1f y=%.1f button=%d dx=%.1f dy=%.1f active=%s",
                mouseX,
                mouseY,
                button,
                dragX,
                dragY,
                activeModifiers()
        );
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    @MCVersionDependentBehaviour
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double deltaX,
            double deltaY
    ) {
        log("mouseScrolled x=%.1f y=%.1f dx=%.1f dy=%.1f active=%s", mouseX, mouseY, deltaX, deltaY, activeModifiers());
        scrollOffset = Math.max(0, scrollOffset + (deltaY > 0 ? 1 : -1));
        return true;
    }

    @Override
    @MCVersionDependentBehaviour
    public void extractRenderState(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        guiGraphics.fill( 0, 0, this.width, this.height, BACKGROUND);

        int left = 8;
        int top = 8;
        int right = this.width - 8;
        int bottom = this.height - 32;
        guiGraphics.fill( left, top, right, bottom, PANEL);
        guiGraphics.fill( left, top, right, top + 1, BORDER);
        guiGraphics.fill( left, bottom - 1, right, bottom, BORDER);
        guiGraphics.fill( left, top, left + 1, bottom, BORDER);
        guiGraphics.fill( right - 1, top, right, bottom, BORDER);

        drawString(guiGraphics, this.title.copy().withStyle(ChatFormatting.BOLD), left + 8, top + 8, TEXT);
        drawString(guiGraphics,
                "Events received by the Minecraft screen. Press keys or click inside this window.",
                left + 8,
                top + 22,
                MUTED
        );
        drawString(guiGraphics,
                "Active modifiers: " + activeModifiers(),
                left + 8,
                top + 34,
                MUTED
        );

        int eventTop = top + 52;
        int eventBottom = bottom - 8;
        int lineHeight = this.font.lineHeight + 2;
        int maxLines = Math.max(1, (eventBottom - eventTop) / lineHeight);
        int endExclusive = Math.max(0, events.size() - scrollOffset);
        int startInclusive = Math.max(0, endExclusive - maxLines);
        int y = eventTop;
        for (int i = startInclusive; i < endExclusive; i++) {
            drawString(guiGraphics, trimToWidth(events.get(i), right - left - 16), left + 8, y, TEXT);
            y += lineHeight;
        }
        if (events.isEmpty()) {
            drawString(guiGraphics, "No input events yet.", left + 8, eventTop, MUTED);
        }
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void log(
            String format,
            Object... args
    ) {
        events.add("%04d  %s".formatted(++nextEventId, format.formatted(args)));
        while (events.size() > EVENT_LIMIT) {
            events.remove(0);
        }
        scrollOffset = 0;
    }

    private String trimToWidth(
            String value,
            int width
    ) {
        if (this.font.width(value) <= width) {
            return value;
        }
        return this.font.plainSubstrByWidth(value, Math.max(0, width - this.font.width("..."))) + "...";
    }

    @MCVersionDependentBehaviour
    private static String keyName(
            int keyCode,
            int scanCode
    ) {
        try {
            return InputConstants.getKey(new KeyEvent(keyCode, scanCode, 0)).getDisplayName().getString();
        } catch (RuntimeException ignored) {
            return "<unknown>";
        }
    }

    @MCVersionDependentBehaviour
    private static String charDisplay(int codePoint) {
        return switch (codePoint) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            default -> "'" + Character.toString(codePoint) + "'";
        };
    }

    private static String modifierMask(int modifiers) {
        List<String> names = new ArrayList<>();
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) names.add("shift");
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) names.add("control");
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) names.add("alt");
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) names.add("super");
        if ((modifiers & GLFW.GLFW_MOD_CAPS_LOCK) != 0) names.add("caps");
        if ((modifiers & GLFW.GLFW_MOD_NUM_LOCK) != 0) names.add("num");
        if (names.isEmpty()) return "none";
        return String.join("+", names);
    }

    private static String keyActionName(int action) {
        return switch (action) {
            case GLFW.GLFW_PRESS -> "press";
            case GLFW.GLFW_RELEASE -> "release";
            case GLFW.GLFW_REPEAT -> "repeat";
            default -> Integer.toString(action);
        };
    }

    @MCVersionDependentBehaviour
    private static String activeModifiers() {
        List<String> names = new ArrayList<>();
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        if (isModifierDown(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) names.add("shift");
        if (isModifierDown(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) names.add("control");
        if (isModifierDown(windowHandle, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) names.add("alt");
        if (names.isEmpty()) return "none";
        return String.join("+", names);
    }

    @MCVersionDependentBehaviour
    private static boolean isModifierDown(long windowHandle, int leftKey, int rightKey) {
        return GLFW.glfwGetKey(windowHandle, leftKey) == GLFW.GLFW_PRESS
               || GLFW.glfwGetKey(windowHandle, rightKey) == GLFW.GLFW_PRESS;
    }

    @MCVersionDependentBehaviour
    private void drawString(
            GuiGraphicsExtractor guiGraphics,
            Component text,
            int x,
            int y,
            int color
    ) {
        guiGraphics.textRenderer().accept(x, y, text.copy().withStyle(style -> style.withColor(color)));
    }

    @MCVersionDependentBehaviour
    private void drawString(
            GuiGraphicsExtractor guiGraphics,
            String text,
            int x,
            int y,
            int color
    ) {
        drawString(guiGraphics, Component.literal(text), x, y, color);
    }
}
