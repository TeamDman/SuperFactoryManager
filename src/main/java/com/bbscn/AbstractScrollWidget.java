package com.bbscn;

import ca.teamdman.sfm.SFM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

public abstract class AbstractScrollWidget extends AbstractWidget{
    private static final WidgetSprites BACKGROUND_SPRITES = new WidgetSprites(
            new ResourceLocation(SFM.MOD_ID,"widget/text_field.png"), new ResourceLocation(SFM.MOD_ID,"widget/text_field_highlighted.png")
    );
    private static final ResourceLocation SCROLLER_SPRITE = new ResourceLocation(SFM.MOD_ID,"widget/scroller.png");
    protected double scrollAmount;
    protected boolean scrolling = false;

    private static final int SCROLL_BAR_WIDTH = 8;
    private static final int INNER_PADDING = 4;

    public AbstractScrollWidget(int x, int y, int width, int height, ITextComponent component) {
        super(x, y, width, height,component);
    }

    @Override
    public boolean mouseClicked(int pMouseX, int pMouseY, int pButton) {
        if (!this.visible) {
            return false;
        } else {
            boolean flag = this.withinContentAreaPoint(pMouseX, pMouseY);
            boolean flag1 = this.scrollbarVisible()
                    && pMouseX >= (double)(this.getX() + this.width)
                    && pMouseX <= (double)(this.getX() + this.width + 8)
                    && pMouseY >= (double)this.getY()
                    && pMouseY < (double)(this.getY() + this.height);
            if (flag1 && pButton == 0) {
                this.scrolling = true;
                return true;
            } else {
                return flag || flag1;
            }
        }
    }
    protected boolean withinContentAreaPoint(double pX, double pY) {
        return pX >= (double)this.getX()
                && pX < (double)(this.getX() + this.width)
                && pY >= (double)this.getY()
                && pY < (double)(this.getY() + this.height);
    }
    protected boolean scrollbarVisible() {
        return this.getInnerHeight() > this.getHeight();
    }
    protected abstract int getInnerHeight();

    @Override
    public boolean mouseReleased(int pMouseX, int pMouseY, int pButton) {
        if (pButton == 0) {
            this.scrolling = false;
        }
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }
    protected void setScrollAmount(double pScrollAmount) {
        this.scrollAmount = Tools.clamp(pScrollAmount, 0.0, (double)this.getMaxScrollAmount());
    }
    protected int getMaxScrollAmount() {
        return Math.max(0, this.getContentHeight() - (this.height - 4));
    }
    private int getContentHeight() {
        return this.getInnerHeight() + 4;
    }
    public int getScrollBarHeight() {
        return Tools.clamp((int)((float)(this.height * this.height) / (float)this.getContentHeight()), 32, this.height);
    }
    @Override
    public boolean mouseDragged(int pMouseX, int pMouseY, int pButton, int pDragX, int pDragY) {
        if (this.visible && this.isFocused() && this.scrolling) {
            if (pMouseY < (double)this.getY()) {
                this.setScrollAmount(0.0);
            } else if (pMouseY > (double)(this.getY() + this.height)) {
                this.setScrollAmount((double)this.getMaxScrollAmount());
            } else {
                int i = this.getScrollBarHeight();
                double d0 = (double)Math.max(1, this.getMaxScrollAmount() / (this.height - i));
                this.setScrollAmount(this.scrollAmount + pDragY * d0);
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean mouseScrolled(int pMouseX, int pMouseY, int pScrollX, int pScrollY) {
        if (!this.visible) {
            return false;
        } else {
            this.setScrollAmount(this.scrollAmount - pScrollY * this.scrollRate());
            return true;
        }
    }
    protected abstract double scrollRate();

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        boolean flag = pKeyCode == Keyboard.KEY_UP; // 使用 Keyboard.KEY_UP 替代硬编码的键值
        boolean flag1 = pKeyCode == Keyboard.KEY_DOWN; // 使用 Keyboard.KEY_DOWN 替代硬编码的键值
        if (flag || flag1) {
            double d0 = this.scrollAmount;
            this.setScrollAmount(this.scrollAmount + (double)(flag ? -1 : 1) * this.scrollRate());
            if (d0 != this.scrollAmount) {
                return true;
            }
        }

        return super.keyPressed(pKeyCode, pScanCode, pModifiers);
    }

    @Override
    public void renderWidget(int mouseX, int mouseY, float partialTicks) {
        if (this.visible) {
            this.renderBackground();
            
            // Enable scissor test
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            ScaledResolution res = new ScaledResolution(Minecraft.getMinecraft());
            float scale = res.getScaleFactor();
            GL11.glScissor(
                (int) ((this.getX() + 1) * scale),
                (int) (Minecraft.getMinecraft().displayHeight - (this.getY() + this.height - 1) * scale),
                (int) ((this.width - 2) * scale),
                (int) ((this.height - 2) * scale)
            );
            
            // Render contents
            GL11.glPushMatrix();
            GL11.glTranslated(0.0, -this.scrollAmount, 0.0);
            this.renderContents(mouseX, mouseY, partialTicks);
            GL11.glPopMatrix();
            
            // Disable scissor test
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            
            this.renderDecorations();
        }
    }
    private void renderScrollBar() {
        if (this.getMaxScrollAmount() + this.getY() == 0) {
            return;
        }
        int i = this.getScrollBarHeight();
        int j = this.getX() + this.width;
        int k = Math.max(this.getY(), (int)this.scrollAmount * (this.height - i) / this.getMaxScrollAmount() + this.getY());
        
        GL11.glEnable(GL11.GL_BLEND);
        Minecraft.getMinecraft().getTextureManager().bindTexture(SCROLLER_SPRITE);
        this.drawTexturedModalRect(j, k, 0, 0, 8, i);
        GL11.glDisable(GL11.GL_BLEND);
    }
    protected void renderDecorations() {
        if (this.scrollbarVisible()) {
            this.renderScrollBar();
        }
    }

    protected abstract void renderContents(int mouseX, int mouseY, float partialTicks);

    protected void renderBackground() {
        ResourceLocation resourcelocation = BACKGROUND_SPRITES.get(this.isActive(), this.isFocused());
        Tools.blitSprite(resourcelocation,this.getX(), this.getY(), this.getWidth(), this.getHeight());
    }

    protected void renderBorder(int x, int y, int width, int height) {
        ResourceLocation resourcelocation = BACKGROUND_SPRITES.get(this.isActive(), this.isFocused());
        Minecraft.getMinecraft().getTextureManager().bindTexture(resourcelocation);
        this.drawTexturedModalRect(x, y, 0, 0, width, height);
    }
    protected int innerPadding() {
        return 4;
    }

    protected int totalInnerPadding() {
        return this.innerPadding() * 2;
    }

    protected double scrollAmount() {
        return this.scrollAmount;
    }
}
