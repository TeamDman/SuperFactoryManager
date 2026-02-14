package com.bbscn;

import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class Button extends AbstractButton {
    public static final int SMALL_WIDTH = 120;
    public static final int DEFAULT_WIDTH = 150;
    public static final int BIG_WIDTH = 200;
    public static final int DEFAULT_HEIGHT = 20;
    public static final int DEFAULT_SPACING = 8;
    protected final OnPress onPress;


    protected Button(
            int pX, int pY, int pWidth, int pHeight, ITextComponent pMessage, OnPress pOnPress
    ) {
        super(pX, pY, pWidth, pHeight, pMessage);
        this.onPress = pOnPress;
    }


    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }


    @SideOnly(Side.CLIENT)
    public interface OnPress {
        void onPress(Button pButton);
    }
}
