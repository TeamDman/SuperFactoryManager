package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
{% if features.keybinding_settings %}
{% else %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingDisplay;
{% endif %}
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
{% if features.keybinding_settings %}
{% else %}
import ca.teamdman.sfm.client.keybinding.SFMKeyModifier;
{% endif %}
import ca.teamdman.sfm.client.keybinding.SFMKeySequence;
{% if features.keybinding_settings %}
import ca.teamdman.sfm.client.keybinding.SFMKeySequenceCapture;
{% else %}
import ca.teamdman.sfm.client.keybinding.SFMKeyStroke;
{% endif %}
import ca.teamdman.sfm.client.registry.SFMClientActions;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
{% if features.keybinding_settings %}
import ca.teamdman.sfm.client.screen.widget.SFMKeycapRenderer;
import ca.teamdman.sfm.client.screen.widget.SFMKeySequenceCaptureWidget;
import com.mojang.blaze3d.vertex.PoseStack;
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
{% endif %}
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% endcase %}
import org.lwjgl.glfw.GLFW;

{% if features.keybinding_settings %}
{% else %}
import java.util.ArrayList;
import java.util.EnumSet;
{% endif %}
import java.util.List;
import java.util.UUID;

public final class SFMKeyBindingDetailsScreen extends Screen {
    private final Screen parent;
{% if features.keybinding_settings %}
    private final ResourceLocation actionId;
    private final SFMKeySequenceCapture capture = new SFMKeySequenceCapture();
    private SFMKeySequenceCaptureWidget captureWidget;
    private net.minecraft.client.gui.components.Button saveCaptureButton;
    private net.minecraft.client.gui.components.Button cancelCaptureButton;
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    private final ResourceLocation actionId;
    private final List<SFMKeyStroke> captured = new ArrayList<>();
{% when "26.1.2" %}
    private final Identifier actionId;
    private final List<SFMKeyStroke> captured = new ArrayList<>();
{% endcase %}
{% endif %}
    private boolean recording;
    private String replacingBindingId;
    private String replacingCommandDraft;
{% if features.keybinding_settings %}
    private ResourceLocation selectedSituationId = SFMKeyboardUsageSituations.GLOBAL;
{% else %}
{% endif %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public SFMKeyBindingDetailsScreen(Screen parent, ResourceLocation actionId) {
{% when "26.1.2" %}
    public SFMKeyBindingDetailsScreen(Screen parent, Identifier actionId) {
{% endcase %}
        super(Component.literal("Action details"));
        this.parent = parent;
        this.actionId = actionId;
    }

    @Override
    protected void init() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        var action = SFMClientActions.registry().get(actionId);
{% when "26.1.2" %}
        var action = SFMClientActions.registry().get(actionId).map(reference -> reference.value()).orElse(null);
{% endcase %}
        if (action == null) return;
        int left = width / 2 - 190;
        int y = 112;
        for (SFMKeyBinding binding : SFMKeyBindingService.INSTANCE.bindingsForAction(actionId)) {
            addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(left + 178, y)
                    .setSize(56, 20)
                    .setText(Component.literal("Edit"))
                    .setOnPress(button -> {
{% if features.keybinding_settings %}
                        beginRecording();
{% else %}
                        recording = true;
{% endif %}
                        replacingBindingId = binding.bindingId();
                        replacingCommandDraft = binding.commandDraft();
{% if features.keybinding_settings %}
                        selectedSituationId = binding.situationId();
{% else %}
                        captured.clear();
                        SFMKeyBindingService.INSTANCE.setDispatchSuspended(true);
{% endif %}
                    })
                    .build());
            addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(left + 240, y)
                    .setSize(58, 20)
                    .setText(Component.literal(binding.enabled() ? "Disable" : "Enable"))
                    .setOnPress(button -> {
                        SFMKeyBindingService.INSTANCE.setEnabled(binding.bindingId(), !binding.enabled());
                        reopen();
                    })
                    .build());
            addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(left + 304, y)
                    .setSize(72, 20)
                    .setText(Component.literal("Remove"))
                    .setOnPress(button -> {
                        SFMKeyBindingService.INSTANCE.remove(binding.bindingId());
                        reopen();
                    })
                    .build());
            y += 26;
        }
{% if features.keybinding_settings %}
        for (SFMKeyBinding binding : SFMKeyBindingService.INSTANCE.tombstonedBuiltInsForAction(actionId)) {
            addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(left + 304, y)
                    .setSize(72, 20)
                    .setText(Component.literal("Restore"))
                    .setOnPress(button -> {
                        SFMKeyBindingService.INSTANCE.restoreBuiltIn(binding.bindingId());
                        reopen();
                    })
                    .build());
            y += 26;
        }
{% else %}
{% endif %}
        addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left, Math.min(height - 52, y + 6))
                .setSize(150, 20)
                .setText(Component.literal("Add key sequence"))
                .setOnPress(button -> {
{% if features.keybinding_settings %}
                    beginRecording();
{% else %}
                    recording = true;
{% endif %}
                    replacingBindingId = null;
                    replacingCommandDraft = null;
{% if features.keybinding_settings %}
                    selectedSituationId = SFMKeyboardUsageSituations.GLOBAL;
{% else %}
                    captured.clear();
                    SFMKeyBindingService.INSTANCE.setDispatchSuspended(true);
{% endif %}
                })
                .build());
        addRenderableWidget(new SFMButtonBuilder()
{% if features.keybinding_settings %}
                .setPosition(left + 158, Math.min(height - 52, y + 6))
                .setSize(150, 20)
                .setText(scopeLabel())
                .setOnPress(button -> {
                    cycleSituation();
                    button.setMessage(scopeLabel());
                })
                .build());
        addRenderableWidget(new SFMButtonBuilder()
{% else %}
{% endif %}
                .setPosition(left + 226, height - 30)
                .setSize(150, 20)
                .setText(CommonComponents.GUI_DONE)
                .setOnPress(button -> onClose())
                .build());
{% if features.keybinding_settings %}
        captureWidget = addRenderableWidget(new SFMKeySequenceCaptureWidget(
                font,
                left,
                height - 82,
                376,
                30,
                capture,
                () -> { },
                this::cancelRecording));
        captureWidget.visible = recording;
        saveCaptureButton = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left, height - 30)
                .setSize(70, 20)
                .setText(Component.literal("Save"))
                .setOnPress(button -> {
                    capture.commitPendingEscapes();
                    if (!capture.strokes().isEmpty()) saveCaptured();
                })
                .build());
        cancelCaptureButton = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + 76, height - 30)
                .setSize(70, 20)
                .setText(CommonComponents.GUI_CANCEL)
                .setOnPress(button -> cancelRecording())
                .build());
        saveCaptureButton.visible = recording;
        cancelCaptureButton.visible = recording;
        if (recording) {
            setFocused(captureWidget);
            captureWidget.setFocused(true);
        }
{% else %}
{% endif %}
    }

    @Override
{% if features.keybinding_settings %}
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        var action = SFMClientActions.registry().get(actionId);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(PoseStack graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        var action = SFMClientActions.registry().get(actionId);
{% when "1.20", "1.20.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        var action = SFMClientActions.registry().get(actionId);
{% when "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        var action = SFMClientActions.registry().get(actionId);
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var action = SFMClientActions.registry().get(actionId).map(reference -> reference.value()).orElse(null);
{% endcase %}
{% endif %}
        if (action == null) return;
        int left = width / 2 - 190;
{% if features.keybinding_settings %}
        SFMFontUtils.draw(poseStack, font, action.title().copy().withStyle(ChatFormatting.BOLD), left, 18, 0xFFFFFFFF, false);
        SFMFontUtils.draw(poseStack, font, actionId.toString(), left, 34, 0xFF80D8FF, false);
        SFMFontUtils.draw(poseStack, font, action.description(), left, 52, 0xFFCCCCCC, false);
{% else %}
        SFMFontUtils.draw(graphics, font, action.title().copy().withStyle(ChatFormatting.BOLD), left, 18, 0xFFFFFFFF, false);
        SFMFontUtils.draw(graphics, font, actionId.toString(), left, 34, 0xFF80D8FF, false);
        SFMFontUtils.draw(graphics, font, action.description(), left, 52, 0xFFCCCCCC, false);
{% endif %}
        var availability = action.requirement().resolve(SFMClientActionContext.create(parent, () -> true));
        Component status = availability.isAvailable()
                ? Component.literal("Available").withStyle(ChatFormatting.GREEN)
                : Component.literal("Unavailable: ").append(availability.unavailableReason()).withStyle(ChatFormatting.RED);
{% if features.keybinding_settings %}
        SFMFontUtils.draw(poseStack, font, status, left, 72, 0xFFFFFFFF, false);
        SFMFontUtils.draw(poseStack, font, "Key sequences", left, 94, 0xFFFFFFFF, false);
{% else %}
        SFMFontUtils.draw(graphics, font, status, left, 72, 0xFFFFFFFF, false);
        SFMFontUtils.draw(graphics, font, "Key sequences", left, 94, 0xFFFFFFFF, false);
{% endif %}
        int y = 118;
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId);
{% if features.keybinding_settings %}
        List<SFMKeyBinding> tombstones = SFMKeyBindingService.INSTANCE.tombstonedBuiltInsForAction(actionId);
        if (bindings.isEmpty() && tombstones.isEmpty()) {
            SFMFontUtils.draw(poseStack, font, "No bindings", left, y, 0xFF999999, false);
{% else %}
        if (bindings.isEmpty()) {
            SFMFontUtils.draw(graphics, font, "No bindings", left, y, 0xFF999999, false);
{% endif %}
        } else {
            for (SFMKeyBinding binding : bindings) {
{% if features.keybinding_settings %}
{% else %}
                String text = SFMKeyBindingDisplay.format(binding.sequence());
                if (!binding.enabled()) text += " (disabled)";
{% endif %}
                boolean conflict = !SFMKeyBindingService.INSTANCE.profile().conflictsWith(binding).isEmpty();
{% if features.keybinding_settings %}
                int keycapsWidth = SFMKeycapRenderer.draw(
                        poseStack,
{% else %}
                if (conflict) text += "  CONFLICT";
                SFMFontUtils.draw(
                        graphics,
{% endif %}
                        font,
{% if features.keybinding_settings %}
                        binding.sequence(),
{% else %}
                        text,
{% endif %}
                        left,
                        y,
{% if features.keybinding_settings %}
                        conflict || !binding.enabled() ? 100 : 168,
                        binding.enabled()
{% else %}
                        conflict ? 0xFFFF5555 : binding.enabled() ? 0xFFFFFFFF : 0xFF888888,
                        false
{% endif %}
                );
{% if features.keybinding_settings %}
                String state = conflict ? "CONFLICT" : binding.enabled() ? "" : "disabled";
                if (!state.isEmpty()) {
                    SFMFontUtils.draw(poseStack, font, state, left + keycapsWidth + 5, y,
                            conflict ? 0xFFFF5555 : 0xFF888888, false);
                }
                String detail = scopeDisplay(binding.situationId()) + "  |  "
                        + originName(binding.bindingId()) + "  |  " + binding.commandDraft();
                SFMFontUtils.draw(poseStack, font, font.plainSubstrByWidth(detail, 168),
{% else %}
                SFMFontUtils.draw(graphics, font, font.plainSubstrByWidth(binding.commandDraft(), 168),
{% endif %}
                        left, y + 10, 0xFF777777, false);
                y += 26;
            }
        }
{% if features.keybinding_settings %}
        for (SFMKeyBinding binding : tombstones) {
            int keycapsWidth = SFMKeycapRenderer.draw(
                    poseStack, font, binding.sequence(), left, y, 100, false);
            SFMFontUtils.draw(poseStack, font, "removed default", left + keycapsWidth + 5, y, 0xFF888888, false);
            SFMFontUtils.draw(poseStack, font,
                    font.plainSubstrByWidth(scopeDisplay(binding.situationId()) + "  |  Built-in tombstone", 280),
                    left, y + 10, 0xFF777777, false);
            y += 26;
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        if (recording) {
            String preview = captured.isEmpty()
                    ? "Press a shortcut, then Enter to save"
                    : SFMKeyBindingDisplay.format(new SFMKeySequence(captured)) + "   [Enter to save]";
            GuiComponent.fill(graphics, left, height - 58, left + 376, height - 38, 0xEE303030);
            SFMFontUtils.draw(graphics, font, preview, left + 6, height - 52, 0xFFFFFF55, false);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        if (recording) {
            String preview = captured.isEmpty()
                    ? "Press a shortcut, then Enter to save"
                    : SFMKeyBindingDisplay.format(new SFMKeySequence(captured)) + "   [Enter to save]";
            graphics.fill(left, height - 58, left + 376, height - 38, 0xEE303030);
            SFMFontUtils.draw(graphics, font, preview, left + 6, height - 52, 0xFFFFFF55, false);
{% endcase %}
{% endif %}
        }
{% if features.keybinding_settings %}
        if (recording) SFMFontUtils.draw(poseStack, font,
                "Scope: " + scopeDisplay(selectedSituationId) + "  |  Enter saves  |  Esc x3 cancels",
                left, height - 96, 0xFFFFFF55, false);
        super.render(poseStack, mouseX, mouseY, partialTick);
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        super.render(graphics, mouseX, mouseY, partialTick);
{% when "26.1.2" %}
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
{% endcase %}
{% endif %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
{% when "26.1.2" %}
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.scancode();
        int modifiers = event.modifiers();
{% endcase %}
        if (!recording) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                onClose();
                return true;
            }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            return super.keyPressed(keyCode, scanCode, modifiers);
{% when "26.1.2" %}
            return super.keyPressed(event);
{% endcase %}
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
{% if features.keybinding_settings %}
            if (recording) return captureWidget.keyPressed(keyCode, scanCode, modifiers);
            onClose();
{% else %}
            recording = false;
            replacingBindingId = null;
            replacingCommandDraft = null;
            captured.clear();
            SFMKeyBindingService.INSTANCE.setDispatchSuspended(false);
{% endif %}
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
{% if features.keybinding_settings %}
            if (captureWidget.hasFocusedToken()) return captureWidget.keyPressed(keyCode, scanCode, modifiers);
            capture.commitPendingEscapes();
            if (!capture.strokes().isEmpty()) saveCaptured();
{% else %}
            if (!captured.isEmpty()) saveCaptured();
{% endif %}
            return true;
        }
{% if features.keybinding_settings %}
        return captureWidget.keyPressed(keyCode, scanCode, modifiers);
{% else %}
        if (isModifierKey(keyCode)) return true;
        captured.add(new SFMKeyStroke(keyCode, modifiers(modifiers)));
        return true;
{% endif %}
    }

    @Override
    public void onClose() {
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(false);
        SFMScreenChangeHelpers.popScreen();
    }

    public void beginRecordingForAutomation() {
{% if features.keybinding_settings %}
        beginRecording();
{% else %}
        recording = true;
{% endif %}
        replacingBindingId = null;
{% if features.keybinding_settings %}
{% else %}
        captured.clear();
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(true);
{% endif %}
    }

    @Override
    public void removed() {
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(false);
        super.removed();
    }

    private void saveCaptured() {
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(false);
        String id = replacingBindingId == null
                ? actionId + "/user-" + UUID.randomUUID()
                : replacingBindingId;
        String commandDraft = replacingCommandDraft == null
                ? "sfm action invoke " + actionId
                : replacingCommandDraft;
        SFMKeyBindingService.INSTANCE.put(new SFMKeyBinding(
                id,
                actionId.toString(),
                commandDraft,
{% if features.keybinding_settings %}
                selectedSituationId,
                new SFMKeySequence(capture.strokes()),
{% else %}
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
                SFMKeyboardUsageSituations.GLOBAL,
                new SFMKeySequence(captured),
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
                new SFMKeySequence(captured),
{% endcase %}
{% endif %}
                true
        ));
        reopen();
    }

{% if features.keybinding_settings %}
    private void beginRecording() {
        recording = true;
        capture.clear();
        if (captureWidget != null) {
            captureWidget.visible = true;
            setFocused(captureWidget);
            captureWidget.setFocused(true);
        }
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(true);
{% else %}
    private void reopen() {
        minecraft.setScreen(new SFMKeyBindingDetailsScreen(parent, actionId));
{% endif %}
    }

{% if features.keybinding_settings %}
    private void cancelRecording() {
        recording = false;
        replacingBindingId = null;
        replacingCommandDraft = null;
        capture.clear();
        if (captureWidget != null) captureWidget.visible = false;
        if (saveCaptureButton != null) saveCaptureButton.visible = false;
        if (cancelCaptureButton != null) cancelCaptureButton.visible = false;
        setFocused(null);
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(false);
{% else %}
    private static boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT
                || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_SUPER || keyCode == GLFW.GLFW_KEY_RIGHT_SUPER;
{% endif %}
    }

{% if features.keybinding_settings %}
    private void cycleSituation() {
        List<ResourceLocation> ids = SFMKeyBindingService.INSTANCE.situationIds();
        if (ids.isEmpty()) return;
        int current = ids.indexOf(selectedSituationId);
        selectedSituationId = ids.get(Math.floorMod(current + 1, ids.size()));
{% else %}
    private static EnumSet<SFMKeyModifier> modifiers(int mask) {
        EnumSet<SFMKeyModifier> result = EnumSet.noneOf(SFMKeyModifier.class);
        if ((mask & GLFW.GLFW_MOD_CONTROL) != 0) result.add(SFMKeyModifier.CONTROL);
        if ((mask & GLFW.GLFW_MOD_ALT) != 0) result.add(SFMKeyModifier.ALT);
        if ((mask & GLFW.GLFW_MOD_SHIFT) != 0) result.add(SFMKeyModifier.SHIFT);
        if ((mask & GLFW.GLFW_MOD_SUPER) != 0) result.add(SFMKeyModifier.SUPER);
        return result;
{% endif %}
    }
{% if features.keybinding_settings %}

    private Component scopeLabel() {
        return Component.literal("Scope: " + scopeName(selectedSituationId));
    }

    private String scopeName(ResourceLocation situationId) {
        return SFMKeyBindingService.INSTANCE.situation(situationId)
                .map(situation -> situation.title().getString())
                .orElse(situationId.toString());
    }

    private String scopeDisplay(ResourceLocation situationId) {
        return scopeName(situationId) + " [" + situationId + "]";
    }

    private String originName(String bindingId) {
        return switch (SFMKeyBindingService.INSTANCE.profile().origin(bindingId)) {
            case BUILT_IN -> "Built-in";
            case OVERRIDDEN_DEFAULT -> "Overridden default";
            case USER -> "User";
            case EPHEMERAL -> "Session-only";
        };
    }

    private void reopen() {
        minecraft.setScreen(new SFMKeyBindingDetailsScreen(parent, actionId));
    }

{% else %}
{% endif %}
}
