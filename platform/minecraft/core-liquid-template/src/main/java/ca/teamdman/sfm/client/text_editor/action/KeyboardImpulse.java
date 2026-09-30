package ca.teamdman.sfm.client.text_editor.action;

{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
{% when '26.1.2' %}
import net.minecraft.client.input.KeyEvent;

{% endcase %}
public record KeyboardImpulse(
{% case minecraft_version %}
{% when '1.19.2', '1.19.4', '1.20', '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.21', '1.21.1' %}
        int keyCode,
        int scanCode,
        int modifiers
{% when '26.1.2' %}
        KeyEvent event
{% endcase %}
) {
}
