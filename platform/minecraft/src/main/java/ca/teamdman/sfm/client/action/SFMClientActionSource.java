package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

public record SFMClientActionSource(
        SFMClientActionContext context,
        Consumer<Component> feedback,
        Consumer<SFMClientActionStructuredResult> structuredResult
) {
    public SFMClientActionSource(SFMClientActionContext context) {
        this(context, ignored -> {
        }, ignored -> {
        });
    }

    public SFMClientActionSource(
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) {
        this(context, feedback, ignored -> {
        });
    }

    public SFMClientActionSource {
        Objects.requireNonNull(context);
        Objects.requireNonNull(feedback);
        Objects.requireNonNull(structuredResult);
    }

    public void sendFeedback(Component message) {
        feedback.accept(Objects.requireNonNull(message));
    }

    public void publishStructuredResult(SFMClientActionStructuredResult result) {
        structuredResult.accept(Objects.requireNonNull(result));
    }
}
