package ca.teamdman.sfm.client.keybinding;

import com.mojang.blaze3d.platform.InputConstants;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import org.lwjgl.glfw.GLFW;
{% else %}
{% endcase %}

import java.util.ArrayList;
import java.util.List;

public final class SFMKeyBindingDisplay {
    private SFMKeyBindingDisplay() {
    }

    public static String format(SFMKeySequence sequence) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        return tokens(sequence).stream()
                .map(stroke -> String.join(" ", stroke))
                .reduce((a, b) -> a + "  " + b)
                .orElse("");
{% else %}
        return sequence.strokes().stream().map(SFMKeyBindingDisplay::format).reduce((a, b) -> a + " " + b).orElse("");
{% endcase %}
    }

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public static String format(SFMKeyStroke stroke) {
        return String.join(" ", tokens(stroke));
    }

    /** Physical-key tokens shared by plain-text fallbacks and GUI keycaps. */
    public static List<List<String>> tokens(SFMKeySequence sequence) {
        return sequence.strokes().stream().map(SFMKeyBindingDisplay::tokens).toList();
    }

    public static List<String> tokens(SFMKeyStroke stroke) {
        List<String> parts = new ArrayList<>();
        if (stroke.modifiers().contains(SFMKeyModifier.CONTROL)) parts.add("Ctrl");
        if (stroke.modifiers().contains(SFMKeyModifier.ALT)) parts.add("Alt");
        if (stroke.modifiers().contains(SFMKeyModifier.SHIFT)) parts.add("Shift");
        if (stroke.modifiers().contains(SFMKeyModifier.SUPER)) parts.add("Super");
        parts.add(keyName(stroke.keyCode()));
        return List.copyOf(parts);
    }
{% else %}
    public static String format(SFMKeyStroke stroke) {
        List<String> parts = new ArrayList<>();
        if (stroke.modifiers().contains(SFMKeyModifier.CONTROL)) parts.add("Ctrl");
        if (stroke.modifiers().contains(SFMKeyModifier.ALT)) parts.add("Alt");
        if (stroke.modifiers().contains(SFMKeyModifier.SHIFT)) parts.add("Shift");
        if (stroke.modifiers().contains(SFMKeyModifier.SUPER)) parts.add("Super");
        parts.add(InputConstants.Type.KEYSYM.getOrCreate(stroke.keyCode()).getDisplayName().getString());
        return String.join("+", parts);
    }
{% endcase %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}

    private static String keyName(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_0 -> "0";
            case GLFW.GLFW_KEY_KP_ADD -> "Num +";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num -";
            case GLFW.GLFW_KEY_KP_0 -> "Num 0";
            default -> InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
        };
    }
{% else %}
{% endcase %}
}
