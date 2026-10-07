package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
{% case minecraft_version %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
{% endcase %}
import com.mojang.blaze3d.platform.InputConstants;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
{% endcase %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    private void installRawScrollCallback() {
        if (rawScrollCallback != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        rawScrollCallbackWindow = minecraft.getWindow().getWindow();
{% when "26.1.2" %}
        rawScrollCallbackWindow = minecraft.getWindow().handle();
{% endcase %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    private void installRawKeyCallback() {
        if (rawKeyCallback != null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        rawKeyCallbackWindow = minecraft.getWindow().getWindow();
{% when "26.1.2" %}
        rawKeyCallbackWindow = minecraft.getWindow().handle();
{% endcase %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean keyPressed(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            int keyCode,
            int scanCode,
            int modifiers
{% when "26.1.2" %}
            KeyEvent event
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
{% endcase %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean keyReleased(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            int keyCode,
            int scanCode,
            int modifiers
{% when "26.1.2" %}
            KeyEvent event
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
{% endcase %}
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
{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean charTyped(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            char codePoint,
            int modifiers
{% when "26.1.2" %}
            CharacterEvent event
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        int codePoint = event.codepoint();
{% endcase %}
        log(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                "charTyped char=%s codepoint=U+%04X modifiers=%s active=%s",
{% when "26.1.2" %}
                "charTyped char=%s codepoint=U+%04X active=%s",
{% endcase %}
                charDisplay(codePoint),
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                (int) codePoint,
                modifierMask(modifiers),
{% when "26.1.2" %}
                codePoint,
{% endcase %}
                activeModifiers()
        );
        return true;
    }

    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean mouseClicked(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            double mouseX,
            double mouseY,
            int button
{% when "26.1.2" %}
            MouseButtonEvent event,
            boolean doubleClick
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
        log("mouseClicked x=%.1f y=%.1f button=%d active=%s", mouseX, mouseY, button, activeModifiers());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.mouseClicked(mouseX, mouseY, button);
{% when "26.1.2" %}
        return super.mouseClicked(event, doubleClick);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean mouseReleased(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            double mouseX,
            double mouseY,
            int button
{% when "26.1.2" %}
            MouseButtonEvent event
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
        log("mouseReleased x=%.1f y=%.1f button=%d active=%s", mouseX, mouseY, button, activeModifiers());
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.mouseReleased(mouseX, mouseY, button);
{% when "26.1.2" %}
        return super.mouseReleased(event);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean mouseDragged(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            double mouseX,
            double mouseY,
            int button,
{% when "26.1.2" %}
            MouseButtonEvent event,
{% endcase %}
            double dragX,
            double dragY
    ) {
{% case minecraft_version %}
{% when "26.1.2" %}
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
{% endcase %}
        log(
                "mouseDragged x=%.1f y=%.1f button=%d dx=%.1f dy=%.1f active=%s",
                mouseX,
                mouseY,
                button,
                dragX,
                dragY,
                activeModifiers()
        );
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
{% when "26.1.2" %}
        return super.mouseDragged(event, dragX, dragY);
{% endcase %}
    }

    @Override
{% case minecraft_version %}
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
            double delta
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            double deltaX,
            double deltaY
{% endcase %}
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1" %}
        log("mouseScrolled x=%.1f y=%.1f delta=%.1f active=%s", mouseX, mouseY, delta, activeModifiers());
        scrollOffset = Math.max(0, scrollOffset + (delta > 0 ? 1 : -1));
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        log("mouseScrolled x=%.1f y=%.1f dx=%.1f dy=%.1f active=%s", mouseX, mouseY, deltaX, deltaY, activeModifiers());
        scrollOffset = Math.max(0, scrollOffset + (deltaY > 0 ? 1 : -1));
{% endcase %}
        return true;
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(
            PoseStack poseStack,
{% when "1.20", "1.20.1" %}
    public void render(
            GuiGraphics guiGraphics,
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @MCVersionDependentBehaviour
    public void render(
            GuiGraphics guiGraphics,
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
    public void extractRenderState(
            GuiGraphicsExtractor guiGraphics,
{% endcase %}
            int mouseX,
            int mouseY,
            float partialTick
    ) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        this.renderBackground(poseStack);
        fill(poseStack, 0, 0, this.width, this.height, BACKGROUND);
{% when "1.20", "1.20.1" %}
        this.renderBackground(guiGraphics);
        guiGraphics.fill( 0, 0, this.width, this.height, BACKGROUND);
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill( 0, 0, this.width, this.height, BACKGROUND);
{% when "26.1.2" %}
        guiGraphics.fill( 0, 0, this.width, this.height, BACKGROUND);
{% endcase %}

        int left = 8;
        int top = 8;
        int right = this.width - 8;
        int bottom = this.height - 32;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        fill(poseStack, left, top, right, bottom, PANEL);
        fill(poseStack, left, top, right, top + 1, BORDER);
        fill(poseStack, left, bottom - 1, right, bottom, BORDER);
        fill(poseStack, left, top, left + 1, bottom, BORDER);
        fill(poseStack, right - 1, top, right, bottom, BORDER);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        guiGraphics.fill( left, top, right, bottom, PANEL);
        guiGraphics.fill( left, top, right, top + 1, BORDER);
        guiGraphics.fill( left, bottom - 1, right, bottom, BORDER);
        guiGraphics.fill( left, top, left + 1, bottom, BORDER);
        guiGraphics.fill( right - 1, top, right, bottom, BORDER);
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, this.font, this.title.copy().withStyle(ChatFormatting.BOLD), left + 8, top + 8, TEXT, true);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMFontUtils.draw(guiGraphics, this.font, this.title.copy().withStyle(ChatFormatting.BOLD), left + 8, top + 8, TEXT, true);
{% endcase %}
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                guiGraphics,
{% endcase %}
                this.font,
                "Events received by the Minecraft screen. Press keys or click inside this window.",
                left + 8,
                top + 22,
                MUTED,
                true
        );
        SFMFontUtils.draw(
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                poseStack,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                guiGraphics,
{% endcase %}
                this.font,
                "Active modifiers: " + activeModifiers(),
                left + 8,
                top + 34,
                MUTED,
                true
        );

        int eventTop = top + 52;
        int eventBottom = bottom - 8;
        int lineHeight = this.font.lineHeight + 2;
        int maxLines = Math.max(1, (eventBottom - eventTop) / lineHeight);
        int endExclusive = Math.max(0, events.size() - scrollOffset);
        int startInclusive = Math.max(0, endExclusive - maxLines);
        int y = eventTop;
        for (int i = startInclusive; i < endExclusive; i++) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, this.font, trimToWidth(events.get(i), right - left - 16), left + 8, y, TEXT, true);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(guiGraphics, this.font, trimToWidth(events.get(i), right - left - 16), left + 8, y, TEXT, true);
{% endcase %}
            y += lineHeight;
        }
        if (events.isEmpty()) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            SFMFontUtils.draw(poseStack, this.font, "No input events yet.", left + 8, eventTop, MUTED, true);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            SFMFontUtils.draw(guiGraphics, this.font, "No input events yet.", left + 8, eventTop, MUTED, true);
{% endcase %}
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        super.render(poseStack, mouseX, mouseY, partialTick);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super.render(guiGraphics, mouseX, mouseY, partialTick);
{% when "26.1.2" %}
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
{% endcase %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    private static String keyName(
            int keyCode,
            int scanCode
    ) {
        try {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            return InputConstants.getKey(keyCode, scanCode).getDisplayName().getString();
{% when "26.1.2" %}
            return InputConstants.getKey(new KeyEvent(keyCode, scanCode, 0)).getDisplayName().getString();
{% endcase %}
        } catch (RuntimeException ignored) {
            return "<unknown>";
        }
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private static String charDisplay(char codePoint) {
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
    private static String charDisplay(int codePoint) {
{% endcase %}
        return switch (codePoint) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            default -> "'" + codePoint + "'";
{% when "26.1.2" %}
            default -> "'" + Character.toString(codePoint) + "'";
{% endcase %}
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

{% case minecraft_version %}
{% when "26.1.2" %}
    @MCVersionDependentBehaviour
{% endcase %}
    private static String activeModifiers() {
        List<String> names = new ArrayList<>();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        if (Screen.hasShiftDown()) names.add("shift");
        if (Screen.hasControlDown()) names.add("control");
        if (Screen.hasAltDown()) names.add("alt");
{% when "26.1.2" %}
        long windowHandle = Minecraft.getInstance().getWindow().handle();
        if (isModifierDown(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT)) names.add("shift");
        if (isModifierDown(windowHandle, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL)) names.add("control");
        if (isModifierDown(windowHandle, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT)) names.add("alt");
{% endcase %}
        if (names.isEmpty()) return "none";
        return String.join("+", names);
    }
{% case minecraft_version %}
{% when "26.1.2" %}

    @MCVersionDependentBehaviour
    private static boolean isModifierDown(long windowHandle, int leftKey, int rightKey) {
        return GLFW.glfwGetKey(windowHandle, leftKey) == GLFW.GLFW_PRESS
               || GLFW.glfwGetKey(windowHandle, rightKey) == GLFW.GLFW_PRESS;
    }

{% endcase %}
}
