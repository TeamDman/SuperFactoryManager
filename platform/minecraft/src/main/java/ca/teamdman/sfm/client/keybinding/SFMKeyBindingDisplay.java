package ca.teamdman.sfm.client.keybinding;

import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

public final class SFMKeyBindingDisplay {
    private SFMKeyBindingDisplay() {
    }

    public static String format(SFMKeySequence sequence) {
        return sequence.strokes().stream().map(SFMKeyBindingDisplay::format).reduce((a, b) -> a + " " + b).orElse("");
    }

    public static String format(SFMKeyStroke stroke) {
        List<String> parts = new ArrayList<>();
        if (stroke.modifiers().contains(SFMKeyModifier.CONTROL)) parts.add("Ctrl");
        if (stroke.modifiers().contains(SFMKeyModifier.ALT)) parts.add("Alt");
        if (stroke.modifiers().contains(SFMKeyModifier.SHIFT)) parts.add("Shift");
        if (stroke.modifiers().contains(SFMKeyModifier.SUPER)) parts.add("Super");
        parts.add(InputConstants.Type.KEYSYM.getOrCreate(stroke.keyCode()).getDisplayName().getString());
        return String.join("+", parts);
    }
}
