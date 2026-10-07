package ca.teamdman.sfm.client.keybinding;

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.resources.ResourceLocation;

{% endcase %}
import java.util.Objects;

public record SFMKeyBinding(
        String bindingId,
        String actionId,
        String commandDraft,
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        ResourceLocation situationId,
{% endcase %}
        SFMKeySequence sequence,
        boolean enabled
) {
    public SFMKeyBinding {
        Objects.requireNonNull(bindingId);
        Objects.requireNonNull(actionId);
        Objects.requireNonNull(commandDraft);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        Objects.requireNonNull(situationId);
{% endcase %}
        Objects.requireNonNull(sequence);
    }

    public SFMKeyBinding withEnabled(boolean value) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        return new SFMKeyBinding(bindingId, actionId, commandDraft, situationId, sequence, value);
{% else %}
        return new SFMKeyBinding(bindingId, actionId, commandDraft, sequence, value);
{% endcase %}
    }
}
