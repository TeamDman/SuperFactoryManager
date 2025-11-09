package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.util.text.ITextComponent;

/// Automatically pops the screen after a choice is made
/// Only runs the callback if the user confirms
public class SFMConfirmationScreen extends GuiYesNo {
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
                confirmTitle.getFormattedText(),
                confirmMessage.getFormattedText(),
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


}
