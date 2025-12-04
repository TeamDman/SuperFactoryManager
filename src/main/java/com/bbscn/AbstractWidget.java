package com.bbscn;

import ca.teamdman.sfm.client.widget.Tooltip;
import ca.teamdman.sfm.client.widget.WidgetTooltipHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;

public abstract class AbstractWidget extends Gui implements GuiEventListener, Renderable {
    protected int width;
    protected int height;
    protected int x;
    protected int y;
    protected ITextComponent message;
    protected boolean isHovered;
    public boolean active = true;
    public boolean visible = true;
    protected float alpha = 1.0F;
    protected boolean focused;
    protected int packedFGColor = UNSET_FG_COLOR;
    public static final int UNSET_FG_COLOR = -1;

    protected WidgetTooltipHolder tooltip = new WidgetTooltipHolder();

    public AbstractWidget(int x, int y, int width, int height, ITextComponent message) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.message = message;
    }

    public void setTooltip(@Nullable Tooltip pTooltip) {
        this.tooltip.set(pTooltip);
    }

    @Nullable
    public Tooltip getTooltip() {
        return this.tooltip.get();
    }

    public boolean isHoveredOrFocused() {
        return this.isHovered() || this.isFocused();
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;
        this.isHovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        this.renderWidget(mouseX, mouseY, partialTicks);
        if (tooltip.get() != null) {
            this.tooltip.refreshTooltipForNextRenderPass(this.isHovered(), this.isFocused(), new ScreenRectangle(x, y, width, height));
        }
    }

    protected abstract void renderWidget(int mouseX, int mouseY, float partialTicks);

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (active && visible && isValidClickButton(button) && clicked(mouseX, mouseY)) {
            this.playDownSound(Minecraft.getMinecraft());
            this.onClick(mouseX, mouseY, button);
            return true;
        }
        return false;
    }

    protected void onClick(int mouseX, int mouseY, int button) {
    }

    public void onRelease(int pMouseX, int pMouseY) {
    }

    protected void onDrag(int pMouseX, int pMouseY, int pDragX, int pDragY) {
    }

    protected boolean isValidClickButton(int button) {
        return button == 0;
    }

    public boolean clicked(int mouseX, int mouseY) {
        return this.active && this.visible &&
                mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    @Override
    public boolean mouseReleased(int pMouseX, int pMouseY, int pButton) {
        if (this.isValidClickButton(pButton)) {
            this.onRelease(pMouseX, pMouseY);
            return true;
        } else {
            return false;
        }
    }

    public void playDownSound(Minecraft mc) {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public boolean isMouseOver(int mouseX, int mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
        if (!focused) {
            this.isHovered = false;
        }
    }

    public boolean isFocused() {
        return focused;
    }

    public boolean isHovered() {
        return isHovered;
    }

    public boolean isActive() {
        return visible && active;
    }

    public int getFGColor() {
        if (packedFGColor != UNSET_FG_COLOR) return packedFGColor;
        return this.active ? 16777215 : 10526880; // White : Light Grey
    }

    public void setFGColor(int color) {
        this.packedFGColor = color;
    }

    public ITextComponent getMessage() {
        return message;
    }

    public void setMessage(ITextComponent message) {
        this.message = message;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    protected void renderScrollingString(FontRenderer pFont, int pWidth, int pColor) {
        int i = this.getX() + pWidth;
        int j = this.getX() + this.getWidth() - pWidth;
        renderScrollingString(pFont, this.getMessage(), i, this.getY(), j, this.getY() + this.getHeight(), pColor);
    }

    private static void renderScrollingString(FontRenderer pFont, ITextComponent pText, int pCenterX, int pMinX, int pMinY, int pMaxX, int pMaxY, int pColor) {
        int i = pFont.getStringWidth(pText.getUnformattedText());
        int j = (pMinY + pMaxY - 9) / 2 + 1;
        int k = pMaxX - pMinX;
        if (i > k) {
            int l = i - k;
            double d0 = (double) Minecraft.getSystemTime() / 1000.0;
            double d1 = Math.max((double) l * 0.5, 3.0);
            double d2 = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * d0 / d1)) / 2.0 + 0.5;
            double d3 = Tools.lerp(d2, 0.0, (double) l);
            Gui.drawRect(pMinX, pMinY, pMaxX, pMaxY, 0x80000000); // Scissor effect using a semi-transparent rectangle
            pFont.drawStringWithShadow(pText.getUnformattedText(), pMinX - (int) d3, j, pColor);
        } else {
            int i1 = Tools.clamp(pCenterX, pMinX + i / 2, pMaxX - i / 2);
            pFont.drawStringWithShadow(pText.getUnformattedText(), i1 - i / 2, j, pColor);
        }
    }

    public static void renderScrollingString(FontRenderer pFont, ITextComponent pText, int pMinX, int pMinY, int pMaxX, int pMaxY, int pColor
    ) {
        renderScrollingString(pFont, pText, (pMinX + pMaxX) / 2, pMinX, pMinY, pMaxX, pMaxY, pColor);
    }
}
