package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.screen.widget.SFMButtonBuilder;
import ca.teamdman.sfm.client.terminal.SFMTerminalPasteWarning;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Bounded multiline-paste warning which retains no clipboard body. */
public final class SFMTerminalPasteConfirmationScreen extends Screen {
    private static final int PANEL_WIDTH = 540;
    private static final int PANEL_HEIGHT = 250;
    private static final int MARGIN = 12;
    private static final int LINE_HEIGHT = 12;
    private static final int MAX_PREVIEW_LINES = 8;

    private final String preview;
    private final Consumer<Boolean> completion;
    private boolean closing;
    private Button cancelButton;

    public SFMTerminalPasteConfirmationScreen(String preview, Consumer<Boolean> completion) {
        super(Component.literal(SFMTerminalPasteWarning.TITLE));
        this.preview = Objects.requireNonNull(preview, "preview");
        this.completion = Objects.requireNonNull(completion, "completion");
    }

    public static void open(String preview, Consumer<Boolean> completion) {
        SFMScreenChangeHelpers.setOrPushScreen(
                new SFMTerminalPasteConfirmationScreen(preview, completion));
    }

    @Override
    protected void init() {
        int left = panelLeft();
        int bottom = panelTop() + panelHeight();
        int buttonWidth = 130;
        int gap = 12;
        int buttonsLeft = left + (panelWidth() - buttonWidth * 2 - gap) / 2;
        addRenderableWidget(new SFMButtonBuilder()
                .setPosition(buttonsLeft, bottom - 32)
                .setSize(buttonWidth, 20)
                .setText(Component.literal(SFMTerminalPasteWarning.APPROVE_LABEL))
                .setOnPress(ignored -> choose(true))
                .build());
        cancelButton = addRenderableWidget(new SFMButtonBuilder()
                .setPosition(buttonsLeft + buttonWidth + gap, bottom - 32)
                .setSize(buttonWidth, 20)
                .setText(Component.literal(SFMTerminalPasteWarning.CANCEL_LABEL))
                .setOnPress(ignored -> choose(false))
                .build());
        setInitialFocus(cancelButton);
        setFocused(cancelButton);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            choose(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        choose(false);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        fill(poseStack, 0, 0, width, height, 0xB0000000);
        int left = panelLeft();
        int top = panelTop();
        int right = left + panelWidth();
        int bottom = top + panelHeight();
        fill(poseStack, left, top, right, bottom, 0xFF101218);
        outline(poseStack, left, top, right, bottom, 0xFFFF7777);

        int textLeft = left + MARGIN;
        int textWidth = panelWidth() - MARGIN * 2;
        int y = top + MARGIN;
        SFMFontUtils.draw(poseStack, font, title, textLeft, y, 0xFFFF7777, false);
        y += 18;
        for (var line : font.split(Component.literal(SFMTerminalPasteWarning.MESSAGE), textWidth)) {
            SFMFontUtils.draw(poseStack, font, line, textLeft, y, 0xFFE8F0F2, false);
            y += LINE_HEIGHT;
        }
        y += 4;
        SFMFontUtils.draw(poseStack, font, SFMTerminalPasteWarning.PREVIEW_LABEL,
                textLeft, y, 0xFF8AA0A8, false);
        y += LINE_HEIGHT;
        List<String> previewLines = preview.lines().limit(MAX_PREVIEW_LINES).toList();
        for (String line : previewLines) {
            String shown = font.plainSubstrByWidth(line, textWidth - 8);
            SFMFontUtils.draw(poseStack, font, shown, textLeft + 4, y, 0xFFFFFFFF, false);
            y += LINE_HEIGHT;
        }
        if (preview.lines().count() > MAX_PREVIEW_LINES) {
            SFMFontUtils.draw(poseStack, font, "...", textLeft + 4, y, 0xFF8AA0A8, false);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    public String warningTextForAutomation() {
        return SFMTerminalPasteWarning.text(preview);
    }

    public boolean cancelFocusedForAutomation() {
        return getFocused() == cancelButton;
    }

    private void choose(boolean approved) {
        if (closing || Minecraft.getInstance().screen != this) return;
        closing = true;
        SFMScreenChangeHelpers.popScreen();
        completion.accept(approved);
    }

    private int panelWidth() {
        return Math.min(PANEL_WIDTH, width - MARGIN * 2);
    }

    private int panelHeight() {
        return Math.min(PANEL_HEIGHT, height - MARGIN * 2);
    }

    private int panelLeft() {
        return (width - panelWidth()) / 2;
    }

    private int panelTop() {
        return (height - panelHeight()) / 2;
    }

    private static void outline(PoseStack poseStack, int left, int top, int right, int bottom, int colour) {
        fill(poseStack, left, top, right, top + 1, colour);
        fill(poseStack, left, bottom - 1, right, bottom, colour);
        fill(poseStack, left, top, left + 1, bottom, colour);
        fill(poseStack, right - 1, top, right, bottom, colour);
    }
}
