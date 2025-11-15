package com.bbscn;

import ca.teamdman.sfm.SFM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public abstract class AbstractButton extends AbstractWidget {
    protected static final int TEXT_MARGIN = 2;

    protected static final WidgetSprites SPRITES = new WidgetSprites(
            new ResourceLocation(SFM.MOD_ID, "widget/button.png"),
            new ResourceLocation(SFM.MOD_ID, "widget/button_disabled.png"),
            new ResourceLocation(SFM.MOD_ID, "widget/button_highlighted.png")
    );

    public AbstractButton(int pX, int pY, int pWidth, int pHeight, ITextComponent pMessage) {
        super(pX, pY, pWidth, pHeight, pMessage);
    }

    public abstract void onPress();

    @Override
    protected void renderWidget(int pMouseX, int pMouseY, float pPartialTick) {
        if (!this.visible) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        // 1. 设置颜色和透明度
        GlStateManager.color(1.0F, 1.0F, 1.0F, this.alpha);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO
        );
        GlStateManager.enableDepth();

        Tools.blitSprite(SPRITES.get(this.active, this.isHoveredOrFocused()), getX(), getY(), getWidth(), getHeight());

        // 3. 恢复颜色状态
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // 4. 渲染文本
        int textColor = this.getFGColor() | ((int) (this.alpha * 255.0F) << 24);
        this.renderString(
                mc.fontRenderer,
                textColor
        );
    }

    public void renderString(FontRenderer pFont, int pColor) {
        this.renderScrollingString(pFont, 2, pColor);
    }

    @Override
    public void onClick(int pMouseX, int pMouseY, int button) {
        this.onPress();
    }


    public static boolean isSelectedInput(int pKey) {
        return pKey == 257 || pKey == 32 || pKey == 335;
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     *
     * @param pKeyCode   the key code of the pressed key.
     * @param pScanCode  the scan code of the pressed key.
     * @param pModifiers the keyboard modifiers.
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     */
    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        if (!this.active || !this.visible) {
            return false;
        } else if (isSelectedInput(pKeyCode)) {
            this.playDownSound(Minecraft.getMinecraft());
            this.onPress();
            return true;
        } else {
            return false;
        }
    }
}
