package ca.teamdman.sfm.client.action;

import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

public record SFMClientActionSource(
        SFMClientActionContext context,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.structured_action_results %}
        Consumer<Component> feedback,
        Consumer<SFMClientActionStructuredResult> structuredResult
{% else %}
        Consumer<Component> feedback
{% endif %}
{% else %}
        Consumer<Component> feedback
{% endcase %}
) {
    public SFMClientActionSource(SFMClientActionContext context) {
        this(context, ignored -> {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.structured_action_results %}
        }, ignored -> {
{% endif %}
{% endcase %}
        });
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.structured_action_results %}
    public SFMClientActionSource(
            SFMClientActionContext context,
            Consumer<Component> feedback
    ) {
        this(context, feedback, ignored -> {
        });
    }

{% endif %}
{% endcase %}
    public SFMClientActionSource {
        Objects.requireNonNull(context);
        Objects.requireNonNull(feedback);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.structured_action_results %}
        Objects.requireNonNull(structuredResult);
{% endif %}
{% endcase %}
    }

    public void sendFeedback(Component message) {
        feedback.accept(Objects.requireNonNull(message));
    }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
{% if features.structured_action_results %}

    public void publishStructuredResult(SFMClientActionStructuredResult result) {
        structuredResult.accept(Objects.requireNonNull(result));
    }
{% endif %}
{% endcase %}
}
