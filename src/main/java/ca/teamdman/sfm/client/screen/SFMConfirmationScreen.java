package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.util.text.ITextComponent;
import org.jetbrains.annotations.Nullable;

/// Automatically pops the screen after a choice is made
/// Only runs the callback if the user confirms
public class SFMConfirmationScreen extends GuiYesNo implements IStackableScreen {

    protected int ticksUntilEnable;


    private GuiScreen prevScreen;

    @Nullable
    @Override
    public GuiScreen getParent() {
        return this.prevScreen;
    }

    @Override
    public void setParent(GuiScreen parent) {
        this.prevScreen = parent;
    }

    public SFMConfirmationScreen(
            Runnable callback,
            ITextComponent confirmTitle,
            ITextComponent confirmMessage,
            ITextComponent confirmYes,
            ITextComponent confirmNo,
            int delay
    ) {
        super(
                (confirmedYes, parentButton) -> {
                    SFMScreenChangeHelpers.popScreen(); // Close confirm screen
                    if (confirmedYes) {
                        callback.run();
                    }
                },
                confirmTitle.getUnformattedComponentText(),
                confirmMessage.getUnformattedComponentText(),
                confirmYes.getUnformattedComponentText(),
                confirmNo.getUnformattedComponentText(),
                0
        );
        setButtonDelay(delay);
    }

    public SFMConfirmationScreen(
            ConfirmationParams confirmationParams,
            int delay,
            Runnable callback
    ) {
        this(
                callback,
                confirmationParams.confirmTitle(),
                confirmationParams.confirmMessage(),
                confirmationParams.confirmYes(),
                confirmationParams.confirmNo(),
                delay
        );
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void setButtonDelay(int ticksUntilEnableIn) {
        this.ticksUntilEnable = ticksUntilEnableIn;
    }

    @Override
    public void updateScreen() {
        if (ticksUntilEnable > 0) {
            ticksUntilEnable--;
            this.buttonList.get(0).enabled = false;

        } else {
            this.buttonList.get(0).enabled = true;
        }
    }
}
