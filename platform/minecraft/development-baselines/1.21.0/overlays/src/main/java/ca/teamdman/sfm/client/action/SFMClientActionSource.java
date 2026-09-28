package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

public record SFMClientActionSource(
        SFMClientActionContext context,
        Consumer<Component> feedback
) {
    public SFMClientActionSource(SFMClientActionContext context) {
        this(context, ignored -> {
        });
    }

    public SFMClientActionSource {
        Objects.requireNonNull(context);
        Objects.requireNonNull(feedback);
    }

    public void sendFeedback(Component message) {
        feedback.accept(Objects.requireNonNull(message));
    }
}
