package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

public record SFMClientActionSource(
        SFMClientActionContext context,
{% if features.structured_action_results %}
        Consumer<Component> feedback,
        Consumer<SFMClientActionStructuredResult> structuredResult
{% else %}
        Consumer<Component> feedback
{% endif %}
) {
    public SFMClientActionSource(SFMClientActionContext context) {
        this(context, ignored -> {
{% if features.structured_action_results %}
        }, ignored -> {
{% endif %}
        });
    }

{% if features.structured_action_results %}
    public SFMClientActionSource(
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) {
        this(context, feedback, ignored -> {
        });
    }

{% endif %}
    public SFMClientActionSource {
        Objects.requireNonNull(context);
        Objects.requireNonNull(feedback);
{% if features.structured_action_results %}
        Objects.requireNonNull(structuredResult);
{% endif %}
    }

    public void sendFeedback(Component message) {
        feedback.accept(Objects.requireNonNull(message));
    }
{% if features.structured_action_results %}

    public void publishStructuredResult(SFMClientActionStructuredResult result) {
        structuredResult.accept(Objects.requireNonNull(result));
    }
{% endif %}
}
