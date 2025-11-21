package ca.teamdman.sfm.client.screen;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.util.text.ITextComponent;

import org.jetbrains.annotations.Nullable;

import ca.teamdman.sfm.common.util.ConfirmationParams;

/// Automatically pops the screen after a choice is made
/// Only runs the callback if the user confirms
public class SFMConfirmationScreen extends GuiYesNo implements IStackableScreen {

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
                                 int delay) {
        super(
                (confirmedYes, parentButton) -> {
                    SFMScreenChangeHelpers.popScreen(); // Close confirm screen
                    if (confirmedYes) {
                        callback.run();
                    }
                },
                confirmTitle.getFormattedText(),
                confirmMessage.getFormattedText(),
                0);
        setButtonDelay(delay);
    }

    public SFMConfirmationScreen(
                                 ConfirmationParams confirmationParams,
                                 int delay,
                                 Runnable callback) {
        this(
                callback,
                confirmationParams.confirmTitle(),
                confirmationParams.confirmMessage(),
                confirmationParams.confirmYes(),
                confirmationParams.confirmNo(),
                delay);
    }
}
