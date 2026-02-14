package com.bbscn;

import ca.teamdman.sfm.SFM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

public class MultiLineEditBox extends AbstractScrollWidget implements GuiEventListener {
    private static final ResourceLocation SCROLLER_SPRITE = new ResourceLocation(SFM.MOD_ID,"widget/scroller.png");
    private static final int CURSOR_INSERT_WIDTH = 1;
    private static final int CURSOR_INSERT_COLOR = -3092272;
    private static final String CURSOR_APPEND_CHARACTER = "_";
    private static final int TEXT_COLOR = -2039584;
    private static final int PLACEHOLDER_TEXT_COLOR = -857677600;
    private static final int CURSOR_BLINK_INTERVAL_MS = 300;
    protected FontRenderer font;
    private final ITextComponent placeholder;
    private long focusedTime = Minecraft.getSystemTime();
    protected MultilineTextField textField;

    public MultiLineEditBox(FontRenderer font, int pX, int pY, int pWidth, int pHeight, ITextComponent placeholder, ITextComponent pMessage) {
        super(pX, pY, pWidth, pHeight, pMessage);
        this.placeholder = placeholder;
        this.font = font;
        this.textField = new MultilineTextField(font, width - this.totalInnerPadding());
        this.textField.setCursorListener(this::scrollToCursor);
    }

    //    @Override
//    public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
//        pNarrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", this.getMessage(), this.getValue()));
//    }
    public String getValue() {
        return this.textField.value();
    }
    public void setValue(String pFullText) {
        this.textField.setValue(pFullText);
    }


    @Override
    public boolean mouseClicked(int pMouseX, int pMouseY, int pButton) {
        if (this.withinContentAreaPoint(pMouseX, pMouseY) && pButton == 0) {
            this.textField.setSelecting(GuiScreen.isShiftKeyDown());
            this.seekCursorScreen(pMouseX, pMouseY);
            return true;
        } else {
            return super.mouseClicked(pMouseX, pMouseY, pButton);
        }
    }

    @Override
    public boolean mouseDragged(int pMouseX, int pMouseY, int pButton, int pDragX, int pDragY) {
        if (super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY)) {
            return true;
        } else if (this.withinContentAreaPoint(pMouseX, pMouseY) && pButton == 0) {
            this.textField.setSelecting(true);
            this.seekCursorScreen(pMouseX, pMouseY);
            this.textField.setSelecting(GuiScreen.isShiftKeyDown());
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean keyPressed(int pKeyCode, int pScanCode, int pModifiers) {
        return this.textField.keyPressed(pKeyCode);
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     *
     * @param pCodePoint the code point of the typed character.
     * @param pModifiers the keyboard modifiers.
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     */
    @Override
    public boolean charTyped(char pCodePoint, int pModifiers) {
        if (this.visible && this.isFocused() && Tools.isAllowedChatCharacter(pCodePoint)) {
            this.textField.insertText(Character.toString(pCodePoint));
            return true;
        } else {
            return false;
        }
    }


    private static final WidgetSprites BACKGROUND_SPRITES = new WidgetSprites(
            //todo 纹理不知道是否存在
            new ResourceLocation(SFM.MOD_ID,"widget/text_field"), new ResourceLocation(SFM.MOD_ID,"widget/text_field_highlighted")
    );


    public boolean isActive() {
        return this.visible && this.active;
    }


    protected int totalInnerPadding() {
        return this.innerPadding() * 2;
    }

    protected boolean withinContentAreaTopBottom(int pTop, int pBottom) {
        return (double) pBottom - this.scrollAmount >= (double) this.getY() && (double) pTop - this.scrollAmount <= (double) (this.getY() + this.height);
    }

    @Override
    protected void renderContents(int pMouseX, int pMouseY, float pPartialTick) {
        String s = this.textField.value();
        if (s.isEmpty() && !this.isFocused()) {
            Tools.drawWordWrap(
                    font,
                    this.placeholder.getUnformattedText(),
                    (int) this.getX() + this.innerPadding(),
                    (int) this.getY() + this.innerPadding(),
                    this.width - this.totalInnerPadding(),
                    -857677600,
                    font.FONT_HEIGHT
            );
        } else {
            int i = this.textField.cursor();
            boolean flag = this.isFocused() && (Minecraft.getSystemTime() - this.focusedTime) / 300L % 2L == 0L;
            boolean flag1 = i < s.length();
            int j = 0;
            int k = 0;
            int l = (int) this.getY() + this.innerPadding();

            for (MultilineTextField.StringView multilinetextfield$stringview : this.textField.iterateLines()) {
                boolean flag2 = this.withinContentAreaTopBottom(l, l + 9);
                if (flag && flag1 && i >= multilinetextfield$stringview.beginIndex && i <= multilinetextfield$stringview.endIndex) {
                    if (flag2) {
                        j = this.font.drawString(
                                s.substring(multilinetextfield$stringview.beginIndex, i),
                                (int) this.getX() + this.innerPadding(),
                                l,
                                -2039584
                        ) - 1;
                        drawRect(j, l - 1, j + 1, l + 1 + 9, -3092272);
                        this.font.drawString(
                                s.substring(i, multilinetextfield$stringview.endIndex),
                                j,
                                l,
                                -2039584
                        );
                    }
                } else {
                    if (flag2) {
                        j = this.font.drawString(
                                s.substring(multilinetextfield$stringview.beginIndex, multilinetextfield$stringview.endIndex),
                                (int) this.getX() + this.innerPadding(),
                                l,
                                -2039584
                        ) - 1;
                    }

                    k = l;
                }

                l += 9;
            }

            if (flag && !flag1 && this.withinContentAreaTopBottom(k, k + 9)) {
                this.font.drawString("_", j, k, -3092272);
            }

            if (this.textField.hasSelection()) {
                MultilineTextField.StringView multilinetextfield$stringview2 = this.textField.getSelected();
                int k1 = (int) this.getX() + this.innerPadding();
                l = (int) this.getY() + this.innerPadding();

                for (MultilineTextField.StringView multilinetextfield$stringview1 : this.textField.iterateLines()) {
                    if (multilinetextfield$stringview2.beginIndex > multilinetextfield$stringview1.endIndex) {
                        l += 9;
                    } else {
                        if (multilinetextfield$stringview1.beginIndex > multilinetextfield$stringview2.endIndex) {
                            break;
                        }

                        if (this.withinContentAreaTopBottom(l, l + 9)) {
                            int i1 = this.font.getStringWidth(
                                    s.substring(
                                            multilinetextfield$stringview1.beginIndex,
                                            Math.max(multilinetextfield$stringview2.beginIndex, multilinetextfield$stringview1.beginIndex)
                                    )
                            );
                            int j1;
                            if (multilinetextfield$stringview2.endIndex > multilinetextfield$stringview1.endIndex) {
                                j1 = this.width - this.innerPadding();
                            } else {
                                j1 = this.font.getStringWidth(s.substring(multilinetextfield$stringview1.beginIndex, multilinetextfield$stringview2.endIndex));
                            }

                            this.renderHighlight(k1 + i1, l, k1 + j1, l + 9);
                        }

                        l += 9;
                    }
                }
            }
        }

    }

    @Override
    public int getInnerHeight() {
        return 9 * this.textField.getLineCount();
    }

    @Override
    protected boolean scrollbarVisible() {
        return (double) this.textField.getLineCount() > this.getDisplayableLineCount();
    }

    @Override
    protected double scrollRate() {
        return 9.0 / 2.0;
    }

    private void renderHighlight(int pMinX, int pMinY, int pMaxX, int pMaxY) {
        drawRect(pMinX, pMinY, pMaxX, pMaxY, -16776961);
    }

    protected void scrollToCursor() {
        double d0 = this.scrollAmount;
        MultilineTextField.StringView multilinetextfield$stringview = this.textField.getLineView((int) (d0 / 9.0));
        if (this.textField.cursor() <= multilinetextfield$stringview.beginIndex) {
            d0 = (double) (this.textField.getLineAtCursor() * 9);
        } else {
            MultilineTextField.StringView multilinetextfield$stringview1 = this.textField.getLineView((int) ((d0 + (double) this.height) / 9.0) - 1);
            if (this.textField.cursor() > multilinetextfield$stringview1.endIndex) {
                d0 = (double) (this.textField.getLineAtCursor() * 9 - this.height + 9 + this.totalInnerPadding());
            }
        }

        this.setScrollAmount(d0);
    }

    private double getDisplayableLineCount() {
        return (double) (this.height - this.totalInnerPadding()) / 9.0;
    }

    protected void seekCursorScreen(double pMouseX, double pMouseY) {
        double d0 = pMouseX - (double) this.getX() - (double) this.innerPadding();
        double d1 = pMouseY - (double) this.getY() - (double) this.innerPadding() + this.scrollAmount();
        this.textField.seekCursorToPoint(d0, d1);
    }

    @Override
    public void setFocused(boolean pFocused) {
        super.setFocused(pFocused);
        if (pFocused) {
            this.focusedTime = Minecraft.getSystemTime();
        }
    }

}