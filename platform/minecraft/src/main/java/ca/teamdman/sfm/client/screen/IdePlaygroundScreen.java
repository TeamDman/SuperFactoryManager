package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.ide.action.IdePlaygroundActionIds;
import ca.teamdman.sfm.client.ide.action.IdePlaygroundActionRegistry;
import ca.teamdman.sfm.client.ide.layout.IdeArea;
import ca.teamdman.sfm.client.ide.layout.IdeDockDirection;
import ca.teamdman.sfm.client.ide.layout.IdeDockLayout;
import ca.teamdman.sfm.client.ide.layout.IdeDockPiece;
import ca.teamdman.sfm.client.ide.session.IdeSession;
import ca.teamdman.sfm.client.ide.session.IdeSessionCapture;
import ca.teamdman.sfm.client.ide.session.IdeSessionTarget;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

public class IdePlaygroundScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int HEADER_HEIGHT = 28;
    private static final int MIN_SIDE_PANEL_WIDTH = 96;
    private static final int MIN_BOTTOM_PANEL_HEIGHT = 64;
    private static final int PANEL_TEXT_PADDING = 6;
    private static final int TERMINAL_INPUT_HEIGHT = 16;
    private static final int TERMINAL_INPUT_MARGIN = 6;
    private static final int MAX_TERMINAL_MESSAGES = 8;

    private int shellPanelWidth = 160;
    private int layoutPanelWidth = 180;
    private int terminalPanelHeight = 96;
    private boolean shellPanelVisible = true;
    private boolean layoutPanelVisible = true;
    private boolean terminalPanelVisible = true;
    private PlaygroundPanel focusedPanel = PlaygroundPanel.WORKSPACE;
    private final IdeSession session = new IdeSession();
    private final List<Component> terminalMessages = new ArrayList<>();
    private final List<String> terminalHistory = new ArrayList<>();
    private Map<PlaygroundPanel, IdeArea> currentLayout = Map.of();
    private EditBox terminalInput;
    private int terminalHistoryIndex;
    private String terminalHistoryDraft = "";

    public IdePlaygroundScreen() {
        super(IdeLocalizationKeys.IDE_PLAYGROUND_TITLE.getComponent());
    }

    @Override
    protected void init() {
        super.init();
        clampPanelSizes();

        terminalInput = addRenderableWidget(new EditBox(
                font,
                MARGIN,
                height - MARGIN - TERMINAL_INPUT_HEIGHT,
                Math.max(96, width - MARGIN * 2),
                TERMINAL_INPUT_HEIGHT,
                IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_PLACEHOLDER.getComponent()
        ));
        terminalInput.setMaxLength(256);
        terminalInput.setVisible(false);
        terminalHistoryIndex = terminalHistory.size();

        if (terminalMessages.isEmpty()) {
            appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_EMPTY.getComponent().withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (SFMKeyMappings.IDE_OPEN_PLAYGROUND_KEY.get().isActiveAndMatches(key)) {
            this.onClose();
            return true;
        }
        if (SFMKeyMappings.IDE_TOGGLE_LEFT_PANEL_KEY.get().isActiveAndMatches(key)) {
            return dispatchAction(IdePlaygroundActionIds.TOGGLE_SHELL_PANEL);
        }
        if (SFMKeyMappings.IDE_TOGGLE_RIGHT_PANEL_KEY.get().isActiveAndMatches(key)) {
            return dispatchAction(IdePlaygroundActionIds.TOGGLE_LAYOUT_PANEL);
        }
        if (SFMKeyMappings.IDE_TOGGLE_BOTTOM_PANEL_KEY.get().isActiveAndMatches(key)) {
            return dispatchAction(IdePlaygroundActionIds.TOGGLE_TERMINAL_PANEL);
        }

        if (handleResizeKey(keyCode)) {
            return true;
        }
        if (handleFocusNavigationKey(keyCode)) {
            return true;
        }

        if (terminalPanelVisible && terminalInput != null && terminalInput.visible && terminalInput.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitTerminalCommand();
                return true;
            }
            if (!hasAltDown() && !hasShiftDown()) {
                if (keyCode == GLFW.GLFW_KEY_UP && recallTerminalHistory(-1)) {
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN && recallTerminalHistory(1)) {
                    return true;
                }
            }
            if (terminalInput.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleFocus(hasShiftDown());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleFocusNavigationKey(int keyCode) {
        if (!hasAltDown() || hasShiftDown()) {
            return false;
        }

        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> moveFocusInDirection(-1, 0, true);
            case GLFW.GLFW_KEY_RIGHT -> moveFocusInDirection(1, 0, false);
            case GLFW.GLFW_KEY_UP -> moveFocusInDirection(0, -1, true);
            case GLFW.GLFW_KEY_DOWN -> moveFocusInDirection(0, 1, false);
            default -> false;
        };
    }

    private boolean handleResizeKey(int keyCode) {
        if (!hasAltDown() || !hasShiftDown()) {
            return false;
        }

        switch (focusedPanel) {
            case SHELL -> {
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    shellPanelWidth -= 8;
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    shellPanelWidth += 8;
                    clampPanelSizes();
                    return true;
                }
            }
            case LAYOUT -> {
                if (keyCode == GLFW.GLFW_KEY_LEFT) {
                    layoutPanelWidth += 8;
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    layoutPanelWidth -= 8;
                    clampPanelSizes();
                    return true;
                }
            }
            case TERMINAL -> {
                if (!terminalPanelVisible) {
                    return false;
                }
                if (keyCode == GLFW.GLFW_KEY_DOWN) {
                    terminalPanelHeight -= 8;
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_UP) {
                    terminalPanelHeight += 8;
                    clampPanelSizes();
                    return true;
                }
            }
            case WORKSPACE -> {
                return false;
            }
        }
        return false;
    }

    private void clampPanelSizes() {
        int availableWidth = Math.max(0, width - MARGIN * 2);
        int availableHeight = Math.max(0, height - MARGIN * 2 - HEADER_HEIGHT);
        int maxCombinedSideWidth = Math.max(
                MIN_SIDE_PANEL_WIDTH * 2,
                availableWidth - 160
        );

        shellPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, Math.min(shellPanelWidth, maxCombinedSideWidth - MIN_SIDE_PANEL_WIDTH));
        layoutPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, Math.min(layoutPanelWidth, maxCombinedSideWidth - MIN_SIDE_PANEL_WIDTH));
        if (shellPanelWidth + layoutPanelWidth > maxCombinedSideWidth) {
            int overflow = shellPanelWidth + layoutPanelWidth - maxCombinedSideWidth;
            if (focusedPanel == PlaygroundPanel.SHELL) {
                layoutPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, layoutPanelWidth - overflow);
            } else {
                shellPanelWidth = Math.max(MIN_SIDE_PANEL_WIDTH, shellPanelWidth - overflow);
            }
        }

        terminalPanelHeight = Math.max(MIN_BOTTOM_PANEL_HEIGHT, Math.min(terminalPanelHeight, Math.max(MIN_BOTTOM_PANEL_HEIGHT, availableHeight - 96)));
    }

    private Map<PlaygroundPanel, IdeArea> calculateLayout() {
        IdeArea rootArea = new IdeArea(
                MARGIN,
                MARGIN + HEADER_HEIGHT,
                Math.max(0, width - MARGIN * 2),
                Math.max(0, height - MARGIN * 2 - HEADER_HEIGHT)
        );

        List<IdeDockPiece<PlaygroundPanel>> pieces = new ArrayList<>();
        if (shellPanelVisible) {
            pieces.add(new IdeDockPiece<>(PlaygroundPanel.SHELL, IdeDockDirection.LEFT, shellPanelWidth));
        }
        pieces.add(new IdeDockPiece<>(PlaygroundPanel.WORKSPACE, IdeDockDirection.CENTER, 0));
        if (layoutPanelVisible) {
            pieces.add(new IdeDockPiece<>(PlaygroundPanel.LAYOUT, IdeDockDirection.RIGHT, layoutPanelWidth));
        }
        if (terminalPanelVisible) {
            pieces.add(new IdeDockPiece<>(PlaygroundPanel.TERMINAL, IdeDockDirection.DOWN, terminalPanelHeight));
        }
        return IdeDockLayout.calculate(rootArea, pieces);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        clampPanelSizes();

        drawString(poseStack, font, title, MARGIN, MARGIN, 0xFFFFFF);
        drawWrappedText(poseStack, IdeLocalizationKeys.IDE_PLAYGROUND_SUBTITLE.getComponent(), MARGIN, MARGIN + 12, Math.max(120, width - MARGIN * 2), 0xA0A0A0, 2);
        IdeSessionCapture.capture(session, Minecraft.getInstance(), focusedPanel.display().getString());

        Map<PlaygroundPanel, IdeArea> layout = calculateLayout();
        currentLayout = layout;
        ensureFocusedPanelVisible();

        if (shellPanelVisible) {
            drawPanel(poseStack, layout.get(PlaygroundPanel.SHELL), IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_SHELL.getComponent(), focusedPanel == PlaygroundPanel.SHELL, buildShellLines());
        }
        drawWorkspacePanel(poseStack, layout.get(PlaygroundPanel.WORKSPACE), focusedPanel == PlaygroundPanel.WORKSPACE, layout);
        if (layoutPanelVisible) {
            drawPanel(poseStack, layout.get(PlaygroundPanel.LAYOUT), IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_LAYOUT.getComponent(), focusedPanel == PlaygroundPanel.LAYOUT, buildLayoutLines());
        }
        drawTerminalPanel(poseStack, layout.get(PlaygroundPanel.TERMINAL), focusedPanel == PlaygroundPanel.TERMINAL);

        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    private List<Component> buildShellLines() {
        ArrayList<Component> lines = new ArrayList<>();
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_PLAYER.getComponent(displayOrNone(session.shellContext().playerPosition())));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_LOOK.getComponent(displayOrNone(session.shellContext().lookVector())));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_HIT.getComponent(displayOrNone(session.shellContext().hitSummary())));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_DIMENSION.getComponent(displayOrNone(session.shellContext().dimensionId())));
        lines.add(Component.empty());
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(displayOrNone(session.shellContext().focusedTargetSummary())));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_SELECTION.getComponent(Integer.toString(session.shellContext().selectedTargetCount())));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_FOCUS.getComponent(displayOrNone(session.shellContext().focusedPanelDisplay())));
        lines.add(IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_HINTS.getComponent(SFMKeyMappings.getKeyDisplay(SFMKeyMappings.IDE_OPEN_PLAYGROUND_KEY)));
        return lines;
    }

    private List<Component> buildLayoutLines() {
        return List.of(
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_LAYOUT_MODE.getComponent(),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_LAYOUT_PIECES.getComponent(),
                Component.empty(),
            Component.literal(String.format(Locale.ROOT, "shell=%s layout=%s terminal=%s",
                shellPanelVisible ? "shown" : "hidden",
                layoutPanelVisible ? "shown" : "hidden",
                terminalPanelVisible ? "shown" : "hidden"
            )).withStyle(ChatFormatting.GRAY),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(displayOrNone(session.focusedTarget().summary())),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_FOCUS.getComponent(focusedPanel.display().getString())
        );
    }

    private String displayOrNone(Optional<String> value) {
        return value.orElse("none");
    }

    private void drawWorkspacePanel(PoseStack poseStack, IdeArea area, boolean focused, Map<PlaygroundPanel, IdeArea> outerLayout) {
        if (area == null || area.isEmpty()) {
            return;
        }
        drawPanelFrame(poseStack, area, IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_WORKSPACE.getComponent(), focused);
        int textWidth = Math.max(32, area.width() - PANEL_TEXT_PADDING * 2);
        int infoY = area.y() + 18;
        infoY = drawWrappedText(poseStack, IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_WORKSPACE.getComponent(), area.x() + PANEL_TEXT_PADDING, infoY, textWidth, 0xE0E0E0, 2);
        infoY = drawWrappedText(poseStack, IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(displayOrNone(session.focusedTarget().summary())), area.x() + PANEL_TEXT_PADDING, infoY, textWidth, 0xC8C8C8, 2);
        infoY = drawWrappedText(poseStack, IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_SELECTION.getComponent(Integer.toString(session.selectedTargets().size())), area.x() + PANEL_TEXT_PADDING, infoY, textWidth, 0xC8C8C8, 1);

        IdeArea previewArea = new IdeArea(
                area.x() + 10,
                infoY + 4,
                Math.max(0, area.width() - 20),
                Math.max(0, area.bottom() - (infoY + 14))
        );
        fill(poseStack, previewArea.x(), previewArea.y(), previewArea.right(), previewArea.bottom(), 0x33101010);

        for (PlaygroundPanel panel : PlaygroundPanel.values()) {
            IdeArea panelArea = outerLayout.get(panel);
            if (panelArea == null) {
                continue;
            }

            int previewX = previewArea.x() + scale(panelArea.x() - MARGIN, width - MARGIN * 2, previewArea.width());
            int previewY = previewArea.y() + scale(panelArea.y() - (MARGIN + HEADER_HEIGHT), height - MARGIN * 2 - HEADER_HEIGHT, previewArea.height());
            int previewW = Math.max(12, scale(panelArea.width(), width - MARGIN * 2, previewArea.width()));
            int previewH = Math.max(10, scale(panelArea.height(), height - MARGIN * 2 - HEADER_HEIGHT, previewArea.height()));
            int color = panel == focusedPanel ? 0xFF4C7899 : 0xAA2A2A2A;
            fill(poseStack, previewX, previewY, previewX + previewW, previewY + previewH, color);
            String previewLabel = font.plainSubstrByWidth(panel.display().getString(), Math.max(6, previewW - 4));
            drawCenteredString(poseStack, font, previewLabel, previewX + previewW / 2, previewY + Math.max(1, previewH / 2 - 4), 0xFFFFFF);
        }

        int footerY = previewArea.bottom() - (font.lineHeight + 2) * Math.min(3, session.selectedTargets().size()) - 8;
        for (IdeSessionTarget selectedTarget : session.selectedTargets().stream().limit(3).toList()) {
            drawString(
                    poseStack,
                    font,
                    Component.literal("• " + displayOrNone(selectedTarget.summary())).withStyle(ChatFormatting.AQUA),
                    previewArea.x() + PANEL_TEXT_PADDING,
                    footerY,
                    0xAEE8FF
            );
            footerY += font.lineHeight + 2;
        }
    }

    private void drawTerminalPanel(PoseStack poseStack, IdeArea area, boolean focused) {
        updateTerminalInputBounds(area);
        if (!terminalPanelVisible || area == null || area.isEmpty()) {
            return;
        }

        drawPanelFrame(poseStack, area, IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_TERMINAL.getComponent(), focused);

        int inputTop = area.bottom() - TERMINAL_INPUT_HEIGHT - TERMINAL_INPUT_MARGIN - 2;
        int lineStep = font.lineHeight + 2;
        int textWidth = Math.max(32, area.width() - PANEL_TEXT_PADDING * 2);

        int messageTop = drawWrappedText(
                poseStack,
                IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_HINT.getComponent(),
                area.x() + PANEL_TEXT_PADDING,
                area.y() + 18,
                textWidth,
                0xC8C8C8,
                2
        ) + 2;

        int lineY = inputTop - font.lineHeight;
        for (int i = terminalMessages.size() - 1; i >= 0; i--) {
            List<FormattedCharSequence> wrapped = font.split(terminalMessages.get(i), textWidth);
            for (int j = wrapped.size() - 1; j >= 0; j--) {
                if (lineY < messageTop) {
                    return;
                }
                font.draw(poseStack, wrapped.get(j), area.x() + PANEL_TEXT_PADDING, lineY, 0xE0E0E0);
                lineY -= lineStep;
            }
        }
    }

    private int scale(int value, int sourceSize, int targetSize) {
        if (sourceSize <= 0 || targetSize <= 0) {
            return 0;
        }
        return (int) Math.round((double) value * targetSize / sourceSize);
    }

    private void drawPanel(PoseStack poseStack, IdeArea area, Component title, boolean focused, List<Component> lines) {
        if (area == null || area.isEmpty()) {
            return;
        }
        drawPanelFrame(poseStack, area, title, focused);
        int y = area.y() + 18;
        int textWidth = Math.max(32, area.width() - PANEL_TEXT_PADDING * 2);
        for (Component line : lines) {
            y = drawWrappedText(poseStack, line, area.x() + PANEL_TEXT_PADDING, y, textWidth, 0xE0E0E0, Integer.MAX_VALUE);
            if (y > area.bottom() - font.lineHeight - PANEL_TEXT_PADDING) {
                break;
            }
        }
    }

    private void drawPanelFrame(PoseStack poseStack, IdeArea area, Component panelTitle, boolean focused) {
        if (area == null || area.isEmpty()) {
            return;
        }
        int background = focused ? 0xCC18212A : 0xB8141414;
        int border = focused ? 0xFF66CCFF : 0xFF303030;
        fill(poseStack, area.x(), area.y(), area.right(), area.bottom(), background);
        fill(poseStack, area.x(), area.y(), area.right(), area.y() + 1, border);
        fill(poseStack, area.x(), area.bottom() - 1, area.right(), area.bottom(), border);
        fill(poseStack, area.x(), area.y(), area.x() + 1, area.bottom(), border);
        fill(poseStack, area.right() - 1, area.y(), area.right(), area.bottom(), border);
        drawString(poseStack, font, font.plainSubstrByWidth(panelTitle.getString(), Math.max(16, area.width() - PANEL_TEXT_PADDING * 2)), area.x() + PANEL_TEXT_PADDING, area.y() + 5, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (terminalPanelVisible && terminalInput != null && terminalInput.visible && terminalInput.isFocused()) {
            return terminalInput.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (PlaygroundPanel panel : visiblePanels()) {
            IdeArea area = currentLayout.get(panel);
            if (area != null && area.contains(mouseX, mouseY)) {
                focusPanel(panel);
                break;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void toggleShellPanel() {
        shellPanelVisible = !shellPanelVisible;
        ensureFocusedPanelVisible();
    }

    public void toggleLayoutPanel() {
        layoutPanelVisible = !layoutPanelVisible;
        ensureFocusedPanelVisible();
    }

    public void toggleTerminalPanel() {
        terminalPanelVisible = !terminalPanelVisible;
        if (!terminalPanelVisible && terminalInput != null) {
            terminalInput.setFocus(false);
            setFocused(null);
        }
        ensureFocusedPanelVisible();
    }

    public void focusShellPanel() {
        shellPanelVisible = true;
        focusPanel(PlaygroundPanel.SHELL);
    }

    public void focusWorkspacePanel() {
        focusPanel(PlaygroundPanel.WORKSPACE);
    }

    public void focusLayoutPanel() {
        layoutPanelVisible = true;
        focusPanel(PlaygroundPanel.LAYOUT);
    }

    public void focusTerminalPanel() {
        terminalPanelVisible = true;
        focusPanel(PlaygroundPanel.TERMINAL);
    }

    public void selectFocusedTarget() {
        session.selectFocusedTarget();
    }

    public void clearSelectedTargets() {
        session.clearSelectedTargets();
    }

    private boolean dispatchAction(String actionId) {
        boolean executed = IdePlaygroundActionRegistry.run(actionId, this);
        appendTerminalMessage((executed
                              ? IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_SUCCESS.getComponent(actionId).withStyle(ChatFormatting.GREEN)
                              : IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_UNKNOWN.getComponent(actionId).withStyle(ChatFormatting.RED)));
        return executed;
    }

    private void submitTerminalCommand() {
        if (terminalInput == null) {
            return;
        }

        String rawCommand = terminalInput.getValue().trim();
        if (rawCommand.isEmpty()) {
            return;
        }
        terminalInput.setValue("");
        terminalHistory.add(rawCommand);
        terminalHistoryIndex = terminalHistory.size();
        terminalHistoryDraft = "";

        if (rawCommand.equalsIgnoreCase("help") || rawCommand.equalsIgnoreCase("/sfm ide help")) {
            appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_EMPTY.getComponent().withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        Optional<String> resolvedActionId = IdePlaygroundActionRegistry.resolveActionId(rawCommand);
        if (resolvedActionId.isPresent()) {
            dispatchAction(resolvedActionId.get());
        } else {
            appendTerminalMessage(IdeLocalizationKeys.IDE_PLAYGROUND_TERMINAL_UNKNOWN.getComponent(rawCommand).withStyle(ChatFormatting.RED));
        }
    }

    private void appendTerminalMessage(Component message) {
        terminalMessages.add(message);
        while (terminalMessages.size() > MAX_TERMINAL_MESSAGES) {
            terminalMessages.remove(0);
        }
    }

    private boolean recallTerminalHistory(int direction) {
        if (terminalInput == null || terminalHistory.isEmpty()) {
            return false;
        }

        int size = terminalHistory.size();
        if (terminalHistoryIndex == size) {
            terminalHistoryDraft = terminalInput.getValue();
        }

        int nextIndex = Math.max(0, Math.min(size, terminalHistoryIndex + direction));
        if (nextIndex == terminalHistoryIndex) {
            return true;
        }

        terminalHistoryIndex = nextIndex;
        if (terminalHistoryIndex == size) {
            terminalInput.setValue(terminalHistoryDraft);
        } else {
            terminalInput.setValue(terminalHistory.get(terminalHistoryIndex));
        }
        terminalInput.moveCursorToEnd();
        return true;
    }

    private void updateTerminalInputBounds(IdeArea area) {
        if (terminalInput == null) {
            return;
        }
        boolean visible = terminalPanelVisible && area != null && !area.isEmpty();
        terminalInput.visible = visible;
        terminalInput.active = visible;
        if (!visible) {
            terminalInput.setFocus(false);
            return;
        }

        terminalInput.setWidth(Math.max(80, area.width() - PANEL_TEXT_PADDING * 2));
        terminalInput.setX(area.x() + PANEL_TEXT_PADDING);
        terminalInput.y = area.bottom() - TERMINAL_INPUT_HEIGHT - TERMINAL_INPUT_MARGIN;
    }

    private int drawWrappedText(PoseStack poseStack, Component text, int x, int y, int maxWidth, int color, int maxLines) {
        int linesDrawn = 0;
        for (FormattedCharSequence sequence : font.split(text, Math.max(1, maxWidth))) {
            if (linesDrawn >= maxLines) {
                break;
            }
            font.draw(poseStack, sequence, x, y, color);
            y += font.lineHeight + 2;
            linesDrawn++;
        }
        return y;
    }

    private void ensureFocusedPanelVisible() {
        if (isPanelVisible(focusedPanel)) {
            return;
        }
        focusPanel(visiblePanels().get(0));
    }

    private void cycleFocus(boolean reverse) {
        List<PlaygroundPanel> visiblePanels = visiblePanels();
        int currentIndex = visiblePanels.indexOf(focusedPanel);
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = reverse
                        ? (currentIndex - 1 + visiblePanels.size()) % visiblePanels.size()
                        : (currentIndex + 1) % visiblePanels.size();
        focusPanel(visiblePanels.get(nextIndex));
    }

    private boolean moveFocusInDirection(int xDirection, int yDirection, boolean reverseFallback) {
        List<PlaygroundPanel> visiblePanels = visiblePanels();
        if (visiblePanels.size() <= 1) {
            return false;
        }

        Map<PlaygroundPanel, IdeArea> layout = calculateLayout();
        currentLayout = layout;

        IdeArea focusedArea = layout.get(focusedPanel);
        if (focusedArea == null || focusedArea.isEmpty()) {
            cycleFocus(reverseFallback);
            return true;
        }

        int focusedCenterX = focusedArea.x() + focusedArea.width() / 2;
        int focusedCenterY = focusedArea.y() + focusedArea.height() / 2;
        PlaygroundPanel bestPanel = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (PlaygroundPanel panel : visiblePanels) {
            if (panel == focusedPanel) {
                continue;
            }

            IdeArea candidateArea = layout.get(panel);
            if (candidateArea == null || candidateArea.isEmpty()) {
                continue;
            }

            int primaryDistance;
            int secondaryDistance;

            if (xDirection < 0) {
                primaryDistance = focusedArea.x() - candidateArea.right();
                secondaryDistance = perpendicularDistance(focusedArea.y(), focusedArea.bottom(), candidateArea.y(), candidateArea.bottom());
            } else if (xDirection > 0) {
                primaryDistance = candidateArea.x() - focusedArea.right();
                secondaryDistance = perpendicularDistance(focusedArea.y(), focusedArea.bottom(), candidateArea.y(), candidateArea.bottom());
            } else if (yDirection < 0) {
                primaryDistance = focusedArea.y() - candidateArea.bottom();
                secondaryDistance = perpendicularDistance(focusedArea.x(), focusedArea.right(), candidateArea.x(), candidateArea.right());
            } else {
                primaryDistance = candidateArea.y() - focusedArea.bottom();
                secondaryDistance = perpendicularDistance(focusedArea.x(), focusedArea.right(), candidateArea.x(), candidateArea.right());
            }

            if (primaryDistance < 0) {
                continue;
            }

            double score = primaryDistance * 1000.0 + secondaryDistance;
            if (score < bestScore) {
                bestScore = score;
                bestPanel = panel;
            }
        }

        if (bestPanel == null) {
            for (PlaygroundPanel panel : visiblePanels) {
                if (panel == focusedPanel) {
                    continue;
                }

                IdeArea candidateArea = layout.get(panel);
                if (candidateArea == null || candidateArea.isEmpty()) {
                    continue;
                }

                int candidateCenterX = candidateArea.x() + candidateArea.width() / 2;
                int candidateCenterY = candidateArea.y() + candidateArea.height() / 2;
                int primaryDistance;
                int secondaryDistance;

                if (xDirection < 0) {
                    primaryDistance = focusedCenterX - candidateCenterX;
                    secondaryDistance = perpendicularDistance(focusedArea.y(), focusedArea.bottom(), candidateArea.y(), candidateArea.bottom());
                } else if (xDirection > 0) {
                    primaryDistance = candidateCenterX - focusedCenterX;
                    secondaryDistance = perpendicularDistance(focusedArea.y(), focusedArea.bottom(), candidateArea.y(), candidateArea.bottom());
                } else if (yDirection < 0) {
                    primaryDistance = focusedCenterY - candidateCenterY;
                    secondaryDistance = perpendicularDistance(focusedArea.x(), focusedArea.right(), candidateArea.x(), candidateArea.right());
                } else {
                    primaryDistance = candidateCenterY - focusedCenterY;
                    secondaryDistance = perpendicularDistance(focusedArea.x(), focusedArea.right(), candidateArea.x(), candidateArea.right());
                }

                if (primaryDistance <= 0) {
                    continue;
                }

                double score = primaryDistance * 1000.0 + secondaryDistance;
                if (score < bestScore) {
                    bestScore = score;
                    bestPanel = panel;
                }
            }
        }

        if (bestPanel == null) {
            cycleFocus(reverseFallback);
            return true;
        }

        focusPanel(bestPanel);
        return true;
    }

    private int perpendicularDistance(int focusedStart, int focusedEnd, int candidateStart, int candidateEnd) {
        if (candidateEnd <= focusedStart) {
            return focusedStart - candidateEnd;
        }
        if (candidateStart >= focusedEnd) {
            return candidateStart - focusedEnd;
        }
        return 0;
    }

    private void focusPanel(PlaygroundPanel panel) {
        focusedPanel = panel;
        boolean focusTerminalInput = panel == PlaygroundPanel.TERMINAL && terminalPanelVisible && terminalInput != null && terminalInput.visible;
        if (terminalInput != null) {
            terminalInput.setFocus(focusTerminalInput);
        }
        setFocused(focusTerminalInput ? terminalInput : null);
    }

    private List<PlaygroundPanel> visiblePanels() {
        ArrayList<PlaygroundPanel> panels = new ArrayList<>();
        if (shellPanelVisible) {
            panels.add(PlaygroundPanel.SHELL);
        }
        panels.add(PlaygroundPanel.WORKSPACE);
        if (layoutPanelVisible) {
            panels.add(PlaygroundPanel.LAYOUT);
        }
        if (terminalPanelVisible) {
            panels.add(PlaygroundPanel.TERMINAL);
        }
        return panels;
    }

    private boolean isPanelVisible(PlaygroundPanel panel) {
        return switch (panel) {
            case SHELL -> shellPanelVisible;
            case WORKSPACE -> true;
            case LAYOUT -> layoutPanelVisible;
            case TERMINAL -> terminalPanelVisible;
        };
    }

    private enum PlaygroundPanel {
        SHELL,
        WORKSPACE,
        LAYOUT,
        TERMINAL;

        public Component display() {
            return switch (this) {
                case SHELL -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_SHELL.getComponent();
                case WORKSPACE -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_WORKSPACE.getComponent();
                case LAYOUT -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_LAYOUT.getComponent();
                case TERMINAL -> IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_TERMINAL.getComponent();
            };
        }
    }
}
