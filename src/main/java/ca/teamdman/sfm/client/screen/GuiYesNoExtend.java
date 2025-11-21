package ca.teamdman.sfm.client.screen;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;

public class GuiYesNoExtend extends GuiYesNo implements IStackableScreen {

    private GuiScreen prevScreen;

    public GuiYesNoExtend(GuiYesNoCallback parentScreenIn, String messageLine1In, String messageLine2In,
                          int parentButtonClickedIdIn) {
        super(parentScreenIn, messageLine1In, messageLine2In, parentButtonClickedIdIn);
    }

    public GuiYesNoExtend(GuiYesNoCallback parentScreenIn, String messageLine1In, String messageLine2In,
                          String confirmButtonTextIn, String cancelButtonTextIn, int parentButtonClickedIdIn) {
        super(parentScreenIn, messageLine1In, messageLine2In, confirmButtonTextIn, cancelButtonTextIn,
                parentButtonClickedIdIn);
    }

    @Override
    public void setParent(GuiScreen parent) {
        this.prevScreen = parent;
    }

    @Override
    public GuiScreen getParent() {
        return this.prevScreen;
    }
}
