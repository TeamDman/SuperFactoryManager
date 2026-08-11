package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.MutableComponent;

/// Automatically pops the screen after a choice is made
/// Only runs the callback if the user confirms
public class SFMConfirmationScreen extends ConfirmScreen {
    public SFMConfirmationScreen(
            Runnable callback,
            MutableComponent confirmTitle,
            MutableComponent confirmMessage,
            MutableComponent confirmYes,
            MutableComponent confirmNo,
            int delay
    ) {
        super(
                confirmedYes -> {
                    SFMScreenChangeHelpers.popScreen(); // Close confirm screen
                    if (confirmedYes) {
                        callback.run();
                    }
                },
                confirmTitle,
                confirmMessage,
                confirmYes,
                confirmNo
        );
        setDelay(delay);
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
