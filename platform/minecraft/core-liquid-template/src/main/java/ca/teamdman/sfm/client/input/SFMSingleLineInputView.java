package ca.teamdman.sfm.client.input;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% if targets.mc_1_21_1 %}
import net.minecraft.client.gui.GuiGraphics;
{% else %}
import com.mojang.blaze3d.vertex.PoseStack;
{% endif %}
import net.minecraft.client.gui.Font;
{% if targets.mc_1_21_1 %}
{% else %}
import net.minecraft.client.gui.GuiComponent;
{% endif %}

/** Bounded text/caret/selection rendering and hit-testing for custom single-line fields. */
public final class SFMSingleLineInputView {
    private String cachedText = "";
    private int cachedCursor = -1;
    private int cachedWidth = -1;
    private Font cachedFont;
    private int start;
    private String visible = "";

    private void layout(SFMSingleLineInput input, Font font, int width) {
        width = Math.max(0, width);
        if (cachedText.equals(input.text()) && cachedCursor == input.cursor()
                && cachedWidth == width && cachedFont == font) return;
        cachedText = input.text();
        cachedCursor = input.cursor();
        cachedWidth = width;
        cachedFont = font;
        String prefix = cachedText.substring(0, cachedCursor);
        String tail = font.plainSubstrByWidth(prefix, Math.max(0, width - 1), true);
        start = cachedCursor - tail.length();
        visible = font.plainSubstrByWidth(cachedText.substring(start), Math.max(0, width - 1));
    }

{% if targets.mc_1_21_1 %}
    public void render(GuiGraphics pose, Font font, SFMSingleLineInput input,
{% else %}
    public void render(PoseStack pose, Font font, SFMSingleLineInput input,
{% endif %}
                       int x, int y, int width, boolean focused, String placeholder,
                       int textColour, int mutedColour) {
        layout(input, font, width);
        if (width <= 0) return;
        if (input.text().isEmpty() && !focused) {
            SFMFontUtils.draw(pose, font, font.plainSubstrByWidth(placeholder, width), x, y, mutedColour, true);
            return;
        }
        int first = Math.max(start, input.selectionStart());
        int last = Math.min(start + visible.length(), input.selectionEnd());
        if (focused && last > first) {
            int left = font.width(visible.substring(0, first - start));
            int right = font.width(visible.substring(0, last - start));
            fillGraphics(pose, x + left, y - 1, x + right, y + font.lineHeight + 1, 0xFF285D92);
        }
        SFMFontUtils.draw(pose, font, visible, x, y, textColour, true);
        if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
            int caret = font.width(input.text().substring(start, input.cursor()));
            fillGraphics(pose, x + caret, y - 1, x + caret + 1, y + font.lineHeight, textColour);
        }
    }

    public int indexAt(SFMSingleLineInput input, Font font, int width, double pixelX) {
        layout(input, font, width);
        int previousWidth = 0;
        for (int index = 0; index < visible.length();) {
            int next = visible.offsetByCodePoints(index, 1);
            int nextWidth = font.width(visible.substring(0, next));
            if (pixelX < (previousWidth + nextWidth) / 2D) return start + index;
            previousWidth = nextWidth;
            index = next;
        }
        return start + visible.length();
    }
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
{% if targets.mc_1_21_1 %}
    private static void fillGraphics(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }
{% else %}
    private static void fillGraphics(PoseStack graphics, int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(graphics, x1, y1, x2, y2, color);
    }
{% endif %}

}
