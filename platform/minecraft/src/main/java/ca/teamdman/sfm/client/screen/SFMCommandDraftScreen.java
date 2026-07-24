package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.keybinding.SFMActionInvocationIntent;
import ca.teamdman.sfm.client.keybinding.SFMCommandDraftAnalysis;
import ca.teamdman.sfm.client.registry.SFMClientActions;
import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Sequential typed completion and explicit confirmation for an incomplete shortcut command draft. */
public final class SFMCommandDraftScreen extends Screen {
    private enum Stage { PROMPT, CONFIRM, EXECUTED }

    private final Screen origin;
    private final boolean pushed;
    private final SFMActionInvocationIntent intent;
    private final SFMClientActionContext context;
    private final List<CompletedParameter> completed = new ArrayList<>();
    private String command;
    private Stage stage;
    private SFMCommandDraftAnalysis analysis;
    private @Nullable EditBox editor;
    private @Nullable Button primary;
    private final List<Component> feedback = new ArrayList<>();

    private SFMCommandDraftScreen(Screen origin, String command, SFMActionInvocationIntent intent) {
        super(Component.literal("Complete shortcut command"));
        this.origin = origin;
        this.pushed = origin != null;
        this.command = command;
        this.intent = intent;
        this.context = SFMClientActionContext.create(origin, () -> Minecraft.getInstance().screen == this);
        this.analysis = analyze();
        this.stage = analysis.state() == SFMCommandDraftAnalysis.State.COMPLETE ? Stage.CONFIRM : Stage.PROMPT;
    }

    public static void open(Screen origin, String command, SFMActionInvocationIntent intent) {
        SFMScreenChangeHelpers.setOrPushScreen(new SFMCommandDraftScreen(origin, command, intent));
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int top = panelTop();
        if (stage == Stage.PROMPT) initPrompt(left, top);
        else if (stage == Stage.CONFIRM) initConfirmation(left, top);
        addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + panelWidth() - 88, top + panelHeight() - 28)
                .setSize(76, 20)
                .setText(Component.literal(stage == Stage.EXECUTED ? "Done" : "Cancel"))
                .setOnPress(ignored -> onClose())
                .build());
    }

    private void initPrompt(int left, int top) {
        editor = addRenderableWidget(new EditBox(font, left + 12, top + 86, panelWidth() - 24, 20,
                Component.literal("Argument value")));
        editor.setMaxLength(1024);
        primary = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + 12, top + panelHeight() - 28)
                .setSize(108, 20)
                .setText(Component.literal("Continue"))
                .setOnPress(ignored -> acceptPrompt())
                .build());
        setInitialFocus(editor);
        editor.setFocused(true);
    }

    private void initConfirmation(int left, int top) {
        editor = addRenderableWidget(new EditBox(font, left + 12, top + 86, panelWidth() - 24, 20,
                Component.literal("Final command")));
        editor.setMaxLength(2048);
        editor.setValue(command);
        editor.setResponder(value -> {
            command = value;
            analysis = analyze();
            if (primary != null) primary.active = analysis.state() == SFMCommandDraftAnalysis.State.COMPLETE;
        });
        primary = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(left + 12, top + panelHeight() - 28)
                .setSize(132, 20)
                .setText(Component.literal("Confirm and run"))
                .setOnPress(ignored -> confirmAndRun())
                .build());
        primary.active = analysis.state() == SFMCommandDraftAnalysis.State.COMPLETE;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && primary != null && primary.active) {
            primary.onPress();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();
        fill(poseStack, left, top, right, bottom, 0xF0202020);
        outline(poseStack, left, top, right, bottom, 0xFF55FFFF);
        SFMFontUtils.draw(poseStack, font, title.copy().withStyle(ChatFormatting.BOLD), left + 12, top + 12,
                0xFFFFFFFF, false);
        SFMFontUtils.draw(poseStack, font,
                "Binding " + intent.bindingId() + "  •  revision " + intent.bindingRevision(),
                left + 12, top + 28, 0xFF80D8FF, false);

        if (stage == Stage.PROMPT) renderPrompt(poseStack, left, top);
        else renderSummary(poseStack, left, top);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private void renderPrompt(PoseStack poseStack, int left, int top) {
        SFMCommandDraftAnalysis.MissingParameter missing = analysis.missingParameter();
        String name = missing == null ? "unknown" : missing.name();
        String type = missing == null ? "unsupported" : missing.displayType();
        SFMFontUtils.draw(poseStack, font, "Required parameter", left + 12, top + 50, 0xFFAAAAAA, false);
        SFMFontUtils.draw(poseStack, font, name + " : " + type, left + 12, top + 64, 0xFFFFFF55, false);
        SFMFontUtils.draw(poseStack, font, analysis.diagnostic(), left + 12, top + 114,
                analysis.state() == SFMCommandDraftAnalysis.State.INVALID ? 0xFFFF7777 : 0xFFAAAAAA, false);
    }

    private void renderSummary(PoseStack poseStack, int left, int top) {
        SFMFontUtils.draw(poseStack, font, "Properties", left + 12, top + 50, 0xFFFFFFFF, false);
        int y = top + 64;
        if (completed.isEmpty()) {
            SFMFontUtils.draw(poseStack, font, "No supplied parameters", left + 18, y, 0xFF999999, false);
            y += 14;
        } else {
            for (CompletedParameter parameter : completed) {
                SFMFontUtils.draw(poseStack, font,
                        parameter.name() + " : " + parameter.type() + " = " + parameter.value() + "  [typed]",
                        left + 18, y, 0xFFDDDDDD, false);
                y += 14;
            }
        }
        SFMFontUtils.draw(poseStack, font, "Final command (click to edit; Brigadier reparses)", left + 12,
                top + 74, 0xFFAAAAAA, false);
        int statusY = Math.max(top + 116, y + 8);
        int colour = analysis.state() == SFMCommandDraftAnalysis.State.COMPLETE ? 0xFF55FF88 : 0xFFFF7777;
        String status = stage == Stage.EXECUTED ? "Executed through contextual action dispatcher" : analysis.diagnostic();
        SFMFontUtils.draw(poseStack, font, status, left + 12, statusY, colour, false);
        int feedbackY = statusY + 15;
        for (Component line : feedback) {
            SFMFontUtils.draw(poseStack, font, line, left + 18, feedbackY, 0xFF80D8FF, false);
            feedbackY += 12;
        }
    }

    private void acceptPrompt() {
        if (editor == null || editor.getValue().isBlank() || analysis.missingParameter() == null) return;
        String value = editor.getValue();
        completed.add(new CompletedParameter(
                analysis.missingParameter().name(),
                analysis.missingParameter().displayType(),
                value
        ));
        command = analysis.preparedCommand() + value;
        analysis = analyze();
        if (analysis.state() == SFMCommandDraftAnalysis.State.COMPLETE) stage = Stage.CONFIRM;
        else if (analysis.state() != SFMCommandDraftAnalysis.State.INCOMPLETE) return;
        rebuild();
    }

    private void confirmAndRun() {
        if (analysis.state() != SFMCommandDraftAnalysis.State.COMPLETE) return;
        feedback.clear();
        try {
            SFMClientActionExecutor.execute(command, context, feedback::add);
            stage = Stage.EXECUTED;
            rebuild();
        } catch (CommandSyntaxException | RuntimeException exception) {
            SFM.LOGGER.warn("Shortcut command confirmation failed: {}", command, exception);
            feedback.add(Component.literal(exception.getMessage()).withStyle(ChatFormatting.RED));
        }
    }

    public void supplyMissingArgumentForAutomation(String value) {
        if (stage != Stage.PROMPT || editor == null) {
            throw new IllegalStateException("Command draft is not prompting for an argument");
        }
        editor.setValue(value);
        acceptPrompt();
        if (stage != Stage.CONFIRM) throw new IllegalStateException("Typed argument did not produce confirmation");
    }

    public String commandForAutomation() {
        return command;
    }

    private SFMCommandDraftAnalysis analyze() {
        return SFMCommandDraftAnalysis.analyze(
                command,
                SFMClientActions.commandTree(),
                new SFMClientActionSource(context)
        );
    }

    private void rebuild() {
        clearWidgets();
        editor = null;
        primary = null;
        init();
    }

    @Override
    public void onClose() {
        if (pushed) SFMScreenChangeHelpers.popScreen();
        else SFMScreenChangeHelpers.setScreen(null);
    }

    private int panelWidth() { return Math.min(460, width - 24); }
    private int panelHeight() { return Math.min(220, height - 24); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return (height - panelHeight()) / 2; }

    private static void outline(PoseStack poseStack, int left, int top, int right, int bottom, int colour) {
        fill(poseStack, left, top, right, top + 1, colour);
        fill(poseStack, left, bottom - 1, right, bottom, colour);
        fill(poseStack, left, top, left + 1, bottom, colour);
        fill(poseStack, right - 1, top, right, bottom, colour);
    }

    private record CompletedParameter(String name, String type, String value) {
    }
}
