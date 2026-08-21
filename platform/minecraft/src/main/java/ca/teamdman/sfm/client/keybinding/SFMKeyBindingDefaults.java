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
                        "builtin/workspace/focus/next",
                        "sfm:panel/focus/next",
                        "sfm action invoke sfm:panel/focus/next",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_TAB, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/previous",
                        "sfm:panel/focus/previous",
                        "sfm action invoke sfm:panel/focus/previous",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_TAB,
                                SFMKeyModifier.CONTROL, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/maximize/toggle",
                        "sfm:panel/maximize/toggle",
                        "sfm action invoke sfm:panel/maximize/toggle",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_M, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-1",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 1",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_1, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-2",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 2",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_2, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-3",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 3",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_3, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-4",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 4",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_4, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-5",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 5",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_5, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-6",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 6",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_6, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-7",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 7",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_7, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-8",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 8",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_8, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/focus/index-9",
                        "sfm:panel/focus/index",
                        "sfm action invoke sfm:panel/focus/index 9",
                        SFMKeyboardUsageSituations.WORKSPACE,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_9, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/diagnostics",
                        "sfm:panel/diagnostics/open",
                        "sfm action invoke sfm:panel/diagnostics/open",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_F3)),
                binding(
                        "builtin/workspace/scale/increase-main",
                        "sfm:panel/scale/increase",
                        "sfm action invoke sfm:panel/scale/increase",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_EQUAL, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/scale/decrease-main",
                        "sfm:panel/scale/decrease",
                        "sfm action invoke sfm:panel/scale/decrease",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_MINUS, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/scale/clear-main",
                        "sfm:panel/scale/clear",
                        "sfm action invoke sfm:panel/scale/clear",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_0, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/workspace/resize/left",
                        "sfm:panel/resize/left",
                        "sfm action invoke sfm:panel/resize/left",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_LEFT,
                                SFMKeyModifier.ALT, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/resize/right",
                        "sfm:panel/resize/right",
                        "sfm action invoke sfm:panel/resize/right",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_RIGHT,
                                SFMKeyModifier.ALT, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/resize/above",
                        "sfm:panel/resize/above",
                        "sfm action invoke sfm:panel/resize/above",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_UP,
                                SFMKeyModifier.ALT, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/resize/below",
                        "sfm:panel/resize/below",
                        "sfm action invoke sfm:panel/resize/below",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_DOWN,
                                SFMKeyModifier.ALT, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/duplicate/right",
                        "sfm:panel/duplicate/right",
                        "sfm action invoke sfm:panel/duplicate/right",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_EQUAL,
                                SFMKeyModifier.ALT, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/workspace/duplicate/below",
                        "sfm:panel/duplicate/below",
                        "sfm action invoke sfm:panel/duplicate/below",
                        SFMKeyStroke.of(GLFW.GLFW_KEY_MINUS,
                                SFMKeyModifier.ALT, SFMKeyModifier.SHIFT)),
                binding(
                        "builtin/temporal-document/history/undo",
                        "sfm:document/history/undo",
                        "sfm action invoke sfm:document/history/undo",
                        SFMKeyboardUsageSituations.TEMPORAL_DOCUMENT,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_Z, SFMKeyModifier.CONTROL)),
                binding(
                        "builtin/text-editor/symbol/definition/open",
                        "sfm:symbol/definition/open",
                        "sfm action invoke sfm:symbol/definition/open",
                        SFMKeyboardUsageSituations.TEXT_EDITOR,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_F12)),
                binding(
                        "builtin/text-editor/symbol/references/open",
                        "sfm:symbol/references/open",
                        "sfm action invoke sfm:symbol/references/open",
                        SFMKeyboardUsageSituations.TEXT_EDITOR,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_F7, SFMKeyModifier.ALT)),
                binding(
                        "builtin/text-editor/context-actions/open",
                        "sfm:context/actions/open",
                        "sfm action invoke sfm:context/actions/open",
                        SFMKeyboardUsageSituations.TEXT_EDITOR,
                        SFMKeyStroke.of(GLFW.GLFW_KEY_ENTER, SFMKeyModifier.ALT))
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
        return binding(id, actionId, commandDraft, SFMKeyboardUsageSituations.DEFAULT, stroke);
    }

    private static SFMKeyBinding binding(
            String id,
            String actionId,
            String commandDraft,
            net.minecraft.resources.ResourceLocation situationId,
            SFMKeyStroke stroke
    ) {
        return new SFMKeyBinding(
                id,
                actionId,
                commandDraft,
                situationId,
                SFMKeySequence.of(stroke),
                true);
    }
}
