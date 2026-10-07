package ca.teamdman.sfm.client.screen.widget;

import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDisplay;
import ca.teamdman.sfm.client.keybinding.SFMKeySequence;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;

import java.util.List;

/** Shared non-interactive rendering for unambiguous physical-key tokens. */
public final class SFMKeycapRenderer {
    private static final int HORIZONTAL_PADDING = 2;
    private static final int TOKEN_GAP = 2;
    private static final int STROKE_GAP = 5;
    private static final int HEIGHT = 11;
    private static final int BACKGROUND = 0xFFE365B5;
    private static final int BORDER = 0xFFFF9AD5;
    private static final int TEXT = 0xFF190F16;
    private static final int DISABLED_BACKGROUND = 0xFF76566A;
    private static final int DISABLED_BORDER = 0xFF9A758D;
    private static final int DISABLED_TEXT = 0xFFDDD1D9;

    private SFMKeycapRenderer() {
    }

    /**
     * Draws complete keycaps which fit in {@code maxWidth}; a keycap is never
     * clipped into an ambiguous partial token. Returns the consumed width.
     */
    public static int draw(
            PoseStack poseStack,
            Font font,
            SFMKeySequence sequence,
            int x,
            int y,
            int maxWidth,
            boolean enabled
    ) {
        int cursor = x;
        boolean firstStroke = true;
        for (List<String> stroke : SFMKeyBindingDisplay.tokens(sequence)) {
            if (!firstStroke) cursor += STROKE_GAP;
            firstStroke = false;
            boolean firstToken = true;
            for (String token : stroke) {
                int gap = firstToken ? 0 : TOKEN_GAP;
                int width = font.width(token) + HORIZONTAL_PADDING * 2;
                if (cursor + gap + width > x + Math.max(0, maxWidth)) return cursor - x;
                cursor += gap;
                int background = enabled ? BACKGROUND : DISABLED_BACKGROUND;
                int border = enabled ? BORDER : DISABLED_BORDER;
                int text = enabled ? TEXT : DISABLED_TEXT;
                GuiComponent.fill(poseStack, cursor, y - 1, cursor + width, y + HEIGHT - 1, border);
                GuiComponent.fill(poseStack, cursor + 1, y, cursor + width - 1, y + HEIGHT - 2, background);
                SFMFontUtils.draw(poseStack, font, token, cursor + HORIZONTAL_PADDING, y, text, false);
                cursor += width;
                firstToken = false;
            }
        }
        return cursor - x;
    }
}
