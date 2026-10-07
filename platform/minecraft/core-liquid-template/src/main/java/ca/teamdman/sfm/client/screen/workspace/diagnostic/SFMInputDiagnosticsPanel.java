package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Embeddable live input-event inspector for the workspace multiplexer. */
public final class SFMInputDiagnosticsPanel implements SFMScreenPanel {
    private static final int BACKGROUND = 0xFF101010;
    private static final int PANEL = 0xFF202020;
    private static final int BORDER = 0xFF606060;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFB0B0B0;
    private static final int EVENT_LIMIT = 500;

    private final List<String> events = new ArrayList<>();
    private int nextEventId;
    private int scrollOffset;
    private SFMRawInputDiagnosticTap.Subscription rawInputSubscription;

    @Override
    public Component title() {
        return Component.literal("Input diagnostics");
    }

    @Override
    public Component narration() {
        return Component.literal("Input diagnostics, " + events.size() + " events");
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        closeSubscription();
        rawInputSubscription = SFMRawInputDiagnosticTap.subscribe(this::log);
        log("panel.open id=%s", context.panelId());
    }

    @Override
    public void closed() {
        log("panel.close");
        closeSubscription();
    }

    @Override
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        int boundsRight = bounds.x() + bounds.width();
        int boundsBottom = bounds.y() + bounds.height();
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), boundsRight, boundsBottom, BACKGROUND);
        int left = bounds.x() + 7;
        int top = bounds.y() + 7;
        int right = Math.max(left + 1, boundsRight - 7);
        int bottom = Math.max(top + 1, boundsBottom - 7);
        GuiComponent.fill(poseStack, left, top, right, bottom, PANEL);
        GuiComponent.fill(poseStack, left, top, right, top + 1, BORDER);
        GuiComponent.fill(poseStack, left, bottom - 1, right, bottom, BORDER);
        GuiComponent.fill(poseStack, left, top, left + 1, bottom, BORDER);
        GuiComponent.fill(poseStack, right - 1, top, right, bottom, BORDER);

        SFMFontUtils.draw(poseStack, minecraft.font, "SFM Input Diagnostics", left + 8, top + 8, TEXT, true);
        SFMFontUtils.draw(
                poseStack,
                minecraft.font,
                "Ctrl+C copies · Ctrl+L clears · Active: " + SFMRawInputDiagnosticTap.activeModifiers(),
                left + 8,
                top + 21,
                MUTED,
                true
        );

        int eventTop = top + 37;
        int eventBottom = bottom - 8;
        int lineHeight = minecraft.font.lineHeight + 2;
        int maxLines = Math.max(1, (eventBottom - eventTop) / lineHeight);
        int endExclusive = Math.max(0, events.size() - scrollOffset);
        int startInclusive = Math.max(0, endExclusive - maxLines);
        int y = eventTop;
        for (int index = startInclusive; index < endExclusive; index++) {
            String event = trimToWidth(minecraft, events.get(index), right - left - 16);
            SFMFontUtils.draw(poseStack, minecraft.font, event, left + 8, y, TEXT, true);
            y += lineHeight;
        }
        if (events.isEmpty()) {
            SFMFontUtils.draw(poseStack, minecraft.font, "No input events yet.", left + 8, eventTop, MUTED, true);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        log("keyPressed key=%d scan=%d name=%s modifiers=%s active=%s",
                keyCode,
                scanCode,
                SFMRawInputDiagnosticTap.keyName(keyCode, scanCode),
                SFMRawInputDiagnosticTap.modifierMask(modifiers),
                SFMRawInputDiagnosticTap.activeModifiers());
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && keyCode == GLFW.GLFW_KEY_C) {
            Minecraft.getInstance().keyboardHandler.setClipboard(String.join("\n", events));
            log("panel.copy count=%d", events.size());
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_L) {
            events.clear();
            scrollOffset = 0;
            log("panel.clear");
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        log("keyReleased key=%d scan=%d name=%s modifiers=%s active=%s",
                keyCode,
                scanCode,
                SFMRawInputDiagnosticTap.keyName(keyCode, scanCode),
                SFMRawInputDiagnosticTap.modifierMask(modifiers),
                SFMRawInputDiagnosticTap.activeModifiers());
        return false;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        log("charTyped char=%s codepoint=U+%04X modifiers=%s active=%s",
                characterDisplay(character),
                (int) character,
                SFMRawInputDiagnosticTap.modifierMask(modifiers),
                SFMRawInputDiagnosticTap.activeModifiers());
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        log("mouseClicked x=%.1f y=%.1f button=%d active=%s",
                mouseX, mouseY, button, SFMRawInputDiagnosticTap.activeModifiers());
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        log("mouseReleased x=%.1f y=%.1f button=%d active=%s",
                mouseX, mouseY, button, SFMRawInputDiagnosticTap.activeModifiers());
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        log("mouseDragged x=%.1f y=%.1f button=%d dx=%.1f dy=%.1f active=%s",
                mouseX, mouseY, button, dragX, dragY, SFMRawInputDiagnosticTap.activeModifiers());
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        log("mouseScrolled x=%.1f y=%.1f delta=%.1f active=%s",
                mouseX, mouseY, delta, SFMRawInputDiagnosticTap.activeModifiers());
        scrollOffset = Math.max(0, Math.min(events.size(), scrollOffset + (delta > 0 ? 1 : -1)));
        return true;
    }

    private void closeSubscription() {
        if (rawInputSubscription == null) return;
        rawInputSubscription.close();
        rawInputSubscription = null;
    }

    private void log(String format, Object... arguments) {
        log(format.formatted(arguments));
    }

    private void log(String event) {
        events.add("%04d  %s".formatted(++nextEventId, event));
        while (events.size() > EVENT_LIMIT) events.remove(0);
        scrollOffset = 0;
    }

    private static String trimToWidth(Minecraft minecraft, String value, int width) {
        if (minecraft.font.width(value) <= width) return value;
        int ellipsisWidth = minecraft.font.width("...");
        return minecraft.font.plainSubstrByWidth(value, Math.max(0, width - ellipsisWidth)) + "...";
    }

    private static String characterDisplay(char character) {
        return switch (character) {
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            default -> "'" + character + "'";
        };
    }
}
