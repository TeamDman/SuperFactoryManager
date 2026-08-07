package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDisplay;
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
import ca.teamdman.sfm.client.keybinding.SFMKeySequence;
import ca.teamdman.sfm.client.keybinding.SFMKeySequenceCapture;
import ca.teamdman.sfm.client.keybinding.SFMKeyStroke;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Focusable, mouse-editable key sequence capture surface. */
public final class SFMKeySequenceCaptureWidget extends AbstractWidget {
    private static final int HORIZONTAL_PADDING = 3;
    private static final int TOKEN_GAP = 3;
    private static final int STROKE_GAP = 8;
    private static final int TOKEN_HEIGHT = 15;
    private static final int BACKGROUND = 0xFFE365B5;
    private static final int BORDER = 0xFFFF9AD5;
    private static final int TEXT = 0xFF190F16;

    private final Font font;
    private final SFMKeySequenceCapture capture;
    private final Runnable changed;
    private final Runnable cancelled;

    public SFMKeySequenceCaptureWidget(
            Font font,
            int x,
            int y,
            int width,
            int height,
            SFMKeySequenceCapture capture,
            Runnable changed,
            Runnable cancelled
    ) {
        super(x, y, width, height, Component.literal("Key sequence capture"));
        this.font = font;
        this.capture = capture;
        this.changed = changed;
        this.cancelled = cancelled;
    }

    public SFMKeySequenceCapture capture() {
        return capture;
    }

    @Override
    public void updateNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
        narration.add(NarratedElementType.USAGE,
                Component.literal("Capture a physical key sequence. Click a pink keycap to remove it."));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        capture.flushExpired(System.currentTimeMillis());
        fill(poseStack, x, y, x + width, y + height, 0xEE303030);
        int outline = isFocused() ? 0xFFFFFF55 : 0xFF777777;
        fill(poseStack, x, y, x + width, y + 1, outline);
        fill(poseStack, x, y + height - 1, x + width, y + height, outline);
        fill(poseStack, x, y, x + 1, y + height, outline);
        fill(poseStack, x + width - 1, y, x + width, y + height, outline);

        int cursor = x + 5;
        boolean firstStroke = true;
        List<List<String>> tokens = capture.isEmpty()
                ? List.of()
                : SFMKeyBindingDisplay.tokens(new SFMKeySequence(capture.strokes()));
        for (List<String> stroke : tokens) {
            if (!firstStroke) cursor += STROKE_GAP;
            firstStroke = false;
            boolean firstToken = true;
            for (String token : stroke) {
                if (!firstToken) cursor += TOKEN_GAP;
                firstToken = false;
                int tokenWidth = font.width(token) + HORIZONTAL_PADDING * 2;
                if (cursor + tokenWidth > x + width - 5) break;
                fill(poseStack, cursor, y + 3, cursor + tokenWidth, y + 3 + TOKEN_HEIGHT, BORDER);
                fill(poseStack, cursor + 1, y + 4, cursor + tokenWidth - 1,
                        y + 2 + TOKEN_HEIGHT, BACKGROUND);
                SFMFontUtils.draw(poseStack, font, token, cursor + HORIZONTAL_PADDING,
                        y + 5, TEXT, false);
                cursor += tokenWidth;
            }
        }
        if (capture.isEmpty()) {
            SFMFontUtils.draw(poseStack, font, "Press a key sequence; Enter saves; Esc x3 cancels",
                    x + 6, y + 5, 0xFFFFFF55, false);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        long now = System.currentTimeMillis();
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (capture.escape(now) == SFMKeySequenceCapture.EscapeResult.CANCELLED) cancelled.run();
            changed.run();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) return false;
        if (isModifierKey(keyCode)) return true;
        capture.capture(keyCode, modifiers(modifiers), now);
        changed.run();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !isMouseOver(mouseX, mouseY)) return false;
        int cursor = x + 5;
        List<List<String>> tokens = capture.isEmpty()
                ? List.of()
                : SFMKeyBindingDisplay.tokens(new SFMKeySequence(capture.strokes()));
        for (int strokeIndex = 0; strokeIndex < tokens.size(); strokeIndex++) {
            if (strokeIndex > 0) cursor += STROKE_GAP;
            List<String> stroke = tokens.get(strokeIndex);
            for (int tokenIndex = 0; tokenIndex < stroke.size(); tokenIndex++) {
                if (tokenIndex > 0) cursor += TOKEN_GAP;
                int tokenWidth = font.width(stroke.get(tokenIndex)) + HORIZONTAL_PADDING * 2;
                if (mouseX >= cursor && mouseX < cursor + tokenWidth
                        && mouseY >= y + 3 && mouseY < y + 3 + TOKEN_HEIGHT) {
                    capture.removeToken(strokeIndex, tokenIndex);
                    changed.run();
                    return true;
                }
                cursor += tokenWidth;
            }
        }
        setFocused(true);
        return true;
    }

    private static boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_SUPER || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private static EnumSet<SFMKeyModifier> modifiers(int mask) {
        EnumSet<SFMKeyModifier> result = EnumSet.noneOf(SFMKeyModifier.class);
        if ((mask & GLFW.GLFW_MOD_CONTROL) != 0) result.add(SFMKeyModifier.CONTROL);
        if ((mask & GLFW.GLFW_MOD_ALT) != 0) result.add(SFMKeyModifier.ALT);
        if ((mask & GLFW.GLFW_MOD_SHIFT) != 0) result.add(SFMKeyModifier.SHIFT);
        if ((mask & GLFW.GLFW_MOD_SUPER) != 0) result.add(SFMKeyModifier.SUPER);
        return result;
    }
}
