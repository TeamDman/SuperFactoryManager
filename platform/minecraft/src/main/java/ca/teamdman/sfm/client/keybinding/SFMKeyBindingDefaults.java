package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Immutable contextual defaults; user state stores only overrides/tombstones. */
public final class SFMKeyBindingDefaults {
    private SFMKeyBindingDefaults() {
    }

    public static List<SFMKeyBinding> definitions() {
        return List.of(
                binding(
                        "builtin/workspace/close-panel",
                        "sfm:panel/close",
                        "sfm action invoke sfm:panel/close",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_W,
                                SFMKeyModifier.CONTROL, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/diagnostics",
                        "sfm:panel/diagnostics/open",
                        "sfm action invoke sfm:panel/diagnostics/open",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_F3)),
                binding(
                        "builtin/workspace/scale/increase-main",
                        "sfm:panel/scale/increase",
                        "sfm action invoke sfm:panel/scale/increase",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_EQUAL,
                                SFMKeyModifier.CONTROL, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/scale/increase-keypad",
                        "sfm:panel/scale/increase",
                        "sfm action invoke sfm:panel/scale/increase",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_KP_ADD, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/scale/decrease-main",
                        "sfm:panel/scale/decrease",
                        "sfm action invoke sfm:panel/scale/decrease",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_MINUS, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/scale/decrease-keypad",
                        "sfm:panel/scale/decrease",
                        "sfm action invoke sfm:panel/scale/decrease",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_KP_SUBTRACT, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/scale/clear-main",
                        "sfm:panel/scale/clear",
                        "sfm action invoke sfm:panel/scale/clear",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_0, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/scale/clear-keypad",
                        "sfm:panel/scale/clear",
                        "sfm action invoke sfm:panel/scale/clear",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_KP_0, SFMKeyModifier.CONTROL))
        );
    }

    public static Map<String, String> fingerprints() {
        return fingerprints(definitions());
    }

    static Map<String, String> fingerprints(List<SFMKeyBinding> definitions) {
        Map<String, String> result = new LinkedHashMap<>();
        for (SFMKeyBinding binding : definitions) {
            result.put(binding.bindingId(), fingerprint(binding));
        }
        return Map.copyOf(result);
    }

    private static String fingerprint(SFMKeyBinding binding) {
        String strokes = binding.sequence().strokes().stream()
                .map(stroke -> stroke.keyCode() + ":" + stroke.modifiers().stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(Collectors.joining("+")))
                .collect(Collectors.joining(","));
        return String.join("|",
                binding.actionId(),
                binding.commandDraft(),
                binding.situationId().toString(),
                Boolean.toString(binding.enabled()),
                strokes);
    }

    private static SFMKeyBinding binding(
            String id,
            String actionId,
            String commandDraft,
            SFMKeyStroke stroke
    ) {
        return new SFMKeyBinding(
                id,
                actionId,
                commandDraft,
                SFMKeyboardUsageSituations.DEFAULT,
                SFMKeySequence.of(stroke),
                true);
    }
}
