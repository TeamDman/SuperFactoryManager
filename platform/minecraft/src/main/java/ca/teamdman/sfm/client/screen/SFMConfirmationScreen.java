package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.ConfirmationParams;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.MutableComponent;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;

/// Automatically pops the screen after a choice is made
/// Only runs the callback if the user confirms
public class SFMConfirmationScreen extends ConfirmScreen {
    private Consumer<Boolean> choice;
    private int remainingDelay;
    public SFMConfirmationScreen(
            Runnable callback,
            MutableComponent confirmTitle,
            MutableComponent confirmMessage,
            MutableComponent confirmYes,
            MutableComponent confirmNo,
            int delay
    ) {
        this(callback, () -> {}, confirmTitle, confirmMessage, confirmYes, confirmNo, delay);
    }

    /** Explicit denial/dismissal callback for consent reviews; existing approval-only callers remain compatible. */
    public SFMConfirmationScreen(
            Runnable callback, Runnable cancelled,
            MutableComponent confirmTitle, MutableComponent confirmMessage,
            MutableComponent confirmYes, MutableComponent confirmNo, int delay
    ) {
        this(decision(callback, cancelled), confirmTitle, confirmMessage, confirmYes, confirmNo, delay);
    }

    private SFMConfirmationScreen(
            Consumer<Boolean> choice,
            MutableComponent confirmTitle, MutableComponent confirmMessage,
            MutableComponent confirmYes, MutableComponent confirmNo, int delay
    ) {
        super(
                choice::accept,
                confirmTitle,
                confirmMessage,
                confirmYes,
                confirmNo
        );
        this.choice = choice;
        setDelay(delay);
    }

    private static Consumer<Boolean> decision(Runnable accepted, Runnable cancelled) {
        AtomicBoolean decided = new AtomicBoolean();
        return yes -> {
            if (!decided.compareAndSet(false, true)) return;
            SFMScreenChangeHelpers.popScreen();
            if (yes) accepted.run(); else cancelled.run();
        };
    }

    @Override
    public void onClose() { choice.accept(false); }

    @Override
    protected void init() {
        super.init();
        // Vanilla setDelay only touches already-created buttons. Applying it
        // in the constructor alone leaves fresh init/resize buttons enabled.
        setDelay(remainingDelay);
        // ConfirmScreen adds the negative choice last. Initial focus must not
        // turn an accidental Enter into permission for program execution.
        if (!children().isEmpty()) setInitialFocus(children().get(children().size() - 1));
    }

    @Override
    public void setDelay(int delay) {
        remainingDelay = Math.max(0, delay);
        if (remainingDelay > 0) super.setDelay(remainingDelay);
        else children().forEach(child -> { if (child instanceof Button button) button.active = true; });
        // Cancelling a security review never needs to wait for the approval delay.
        if (!children().isEmpty() && children().get(children().size() - 1) instanceof Button cancel) cancel.active = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (remainingDelay > 0) remainingDelay--;
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
