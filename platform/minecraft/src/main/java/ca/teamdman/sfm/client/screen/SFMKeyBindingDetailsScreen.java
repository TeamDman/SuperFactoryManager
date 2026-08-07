package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.keybinding.SFMKeyBinding;
import ca.teamdman.sfm.client.keybinding.SFMKeyBindingService;
import ca.teamdman.sfm.client.keybinding.SFMKeySequence;
import ca.teamdman.sfm.client.keybinding.SFMKeySequenceCapture;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.screen.widget.SFMKeycapRenderer;
import ca.teamdman.sfm.client.screen.widget.SFMKeySequenceCaptureWidget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;

public final class SFMKeyBindingDetailsScreen extends Screen {
    private final Screen parent;
    private final ResourceLocation actionId;
    private final SFMKeySequenceCapture capture = new SFMKeySequenceCapture();
    private SFMKeySequenceCaptureWidget captureWidget;
    private boolean recording;
    private String replacingBindingId;
    private String replacingCommandDraft;
    private ResourceLocation selectedSituationId = SFMKeyboardUsageSituations.GLOBAL;

    public SFMKeyBindingDetailsScreen(Screen parent, ResourceLocation actionId) {
        super(Component.literal("Action details"));
        this.parent = parent;
        this.actionId = actionId;
    }

    @Override
    protected void init() {
        var action = SFMClientActions.registry().get(actionId);
        if (action == null) return;
        int left = width / 2 - 190;
        int y = 112;
        for (SFMKeyBinding binding : SFMKeyBindingService.INSTANCE.bindingsForAction(actionId)) {
            addRenderableWidget(new SFMButtonBuilder()
                    .setPosition(left + 178, y)
                    .setSize(56, 20)
                    .setText(Component.literal("Edit"))
                    .setOnPress(button -> {
                        beginRecording();
                        replacingBindingId = binding.bindingId();
                        replacingCommandDraft = binding.commandDraft();
                        selectedSituationId = binding.situationId();
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
        addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left, Math.min(height - 52, y + 6))
                .setSize(150, 20)
                .setText(Component.literal("Add key sequence"))
                .setOnPress(button -> {
                    beginRecording();
                    replacingBindingId = null;
                    replacingCommandDraft = null;
                    selectedSituationId = SFMKeyboardUsageSituations.GLOBAL;
                })
                .build());
        addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + 158, Math.min(height - 52, y + 6))
                .setSize(150, 20)
                .setText(scopeLabel())
                .setOnPress(button -> {
                    cycleSituation();
                    button.setMessage(scopeLabel());
                })
                .build());
        addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + 226, height - 30)
                .setSize(150, 20)
                .setText(CommonComponents.GUI_DONE)
                .setOnPress(button -> onClose())
                .build());
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
        if (recording) {
            setFocused(captureWidget);
            captureWidget.setFocused(true);
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        var action = SFMClientActions.registry().get(actionId);
        if (action == null) return;
        int left = width / 2 - 190;
        SFMFontUtils.draw(poseStack, font, action.title().copy().withStyle(ChatFormatting.BOLD), left, 18, 0xFFFFFFFF, false);
        SFMFontUtils.draw(poseStack, font, actionId.toString(), left, 34, 0xFF80D8FF, false);
        SFMFontUtils.draw(poseStack, font, action.description(), left, 52, 0xFFCCCCCC, false);
        var availability = action.requirement().resolve(SFMClientActionContext.create(parent, () -> true));
        Component status = availability.isAvailable()
                ? Component.literal("Available").withStyle(ChatFormatting.GREEN)
                : Component.literal("Unavailable: ").append(availability.unavailableReason()).withStyle(ChatFormatting.RED);
        SFMFontUtils.draw(poseStack, font, status, left, 72, 0xFFFFFFFF, false);
        SFMFontUtils.draw(poseStack, font, "Key sequences", left, 94, 0xFFFFFFFF, false);
        int y = 118;
        List<SFMKeyBinding> bindings = SFMKeyBindingService.INSTANCE.bindingsForAction(actionId);
        List<SFMKeyBinding> tombstones = SFMKeyBindingService.INSTANCE.tombstonedBuiltInsForAction(actionId);
        if (bindings.isEmpty() && tombstones.isEmpty()) {
            SFMFontUtils.draw(poseStack, font, "No bindings", left, y, 0xFF999999, false);
        } else {
            for (SFMKeyBinding binding : bindings) {
                boolean conflict = !SFMKeyBindingService.INSTANCE.profile().conflictsWith(binding).isEmpty();
                int keycapsWidth = SFMKeycapRenderer.draw(
                        poseStack,
                        font,
                        binding.sequence(),
                        left,
                        y,
                        conflict || !binding.enabled() ? 100 : 168,
                        binding.enabled()
                );
                String state = conflict ? "CONFLICT" : binding.enabled() ? "" : "disabled";
                if (!state.isEmpty()) {
                    SFMFontUtils.draw(poseStack, font, state, left + keycapsWidth + 5, y,
                            conflict ? 0xFFFF5555 : 0xFF888888, false);
                }
                String detail = scopeDisplay(binding.situationId()) + "  |  "
                        + originName(binding.bindingId()) + "  |  " + binding.commandDraft();
                SFMFontUtils.draw(poseStack, font, font.plainSubstrByWidth(detail, 168),
                        left, y + 10, 0xFF777777, false);
                y += 26;
            }
        }
        for (SFMKeyBinding binding : tombstones) {
            int keycapsWidth = SFMKeycapRenderer.draw(
                    poseStack, font, binding.sequence(), left, y, 100, false);
            SFMFontUtils.draw(poseStack, font, "removed default", left + keycapsWidth + 5, y, 0xFF888888, false);
            SFMFontUtils.draw(poseStack, font,
                    font.plainSubstrByWidth(scopeDisplay(binding.situationId()) + "  |  Built-in tombstone", 280),
                    left, y + 10, 0xFF777777, false);
            y += 26;
        }
        if (recording) SFMFontUtils.draw(poseStack, font,
                "Scope: " + scopeDisplay(selectedSituationId) + "  |  Enter saves  |  Esc x3 cancels",
                left, height - 96, 0xFFFFFF55, false);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!recording) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (recording) return captureWidget.keyPressed(keyCode, scanCode, modifiers);
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            capture.commitPendingEscapes();
            if (!capture.strokes().isEmpty()) saveCaptured();
            return true;
        }
        return captureWidget.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(false);
        SFMScreenChangeHelpers.popScreen();
    }

    public void beginRecordingForAutomation() {
        beginRecording();
        replacingBindingId = null;
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
                selectedSituationId,
                new SFMKeySequence(capture.strokes()),
                true
        ));
        reopen();
    }

    private void beginRecording() {
        recording = true;
        capture.clear();
        if (captureWidget != null) {
            captureWidget.visible = true;
            setFocused(captureWidget);
            captureWidget.setFocused(true);
        }
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(true);
    }

    private void cancelRecording() {
        recording = false;
        replacingBindingId = null;
        replacingCommandDraft = null;
        capture.clear();
        if (captureWidget != null) captureWidget.visible = false;
        SFMKeyBindingService.INSTANCE.setDispatchSuspended(false);
    }

    private void cycleSituation() {
        List<ResourceLocation> ids = SFMKeyBindingService.INSTANCE.situationIds();
        if (ids.isEmpty()) return;
        int current = ids.indexOf(selectedSituationId);
        selectedSituationId = ids.get(Math.floorMod(current + 1, ids.size()));
    }

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

}
