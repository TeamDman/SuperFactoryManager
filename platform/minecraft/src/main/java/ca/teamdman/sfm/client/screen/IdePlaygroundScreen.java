package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.client.ide.layout.IdeArea;
import ca.teamdman.sfm.client.ide.layout.IdeDockDirection;
import ca.teamdman.sfm.client.ide.layout.IdeDockLayout;
import ca.teamdman.sfm.client.ide.layout.IdeDockPiece;
import ca.teamdman.sfm.client.ide.session.IdeSession;
import ca.teamdman.sfm.client.ide.session.IdeSessionCapture;
import ca.teamdman.sfm.client.registry.SFMKeyMappings;
import ca.teamdman.sfm.common.localization.IdeLocalizationKeys;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IdePlaygroundScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int HEADER_HEIGHT = 28;
    private static final int MIN_SIDE_PANEL_WIDTH = 96;
    private static final int MIN_BOTTOM_PANEL_HEIGHT = 64;
    private static final int PANEL_TEXT_PADDING = 6;

    private int shellPanelWidth = 160;
    private int layoutPanelWidth = 180;
    private int terminalPanelHeight = 96;
    private PlaygroundPanel focusedPanel = PlaygroundPanel.WORKSPACE;
    private final IdeSession session = new IdeSession();

    public IdePlaygroundScreen() {
        super(IdeLocalizationKeys.IDE_PLAYGROUND_TITLE.getComponent());
    }

    @Override
    protected void init() {
        super.init();
        clampPanelSizes();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (SFMKeyMappings.IDE_OPEN_PLAYGROUND_KEY.get().isActiveAndMatches(key)) {
            this.onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            focusedPanel = focusedPanel.next(hasShiftDown());
            return true;
        }
        if (handleResizeKey(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handleResizeKey(int keyCode) {
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
                    layoutPanelWidth -= 8;
                    clampPanelSizes();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                    layoutPanelWidth += 8;
                    clampPanelSizes();
                    return true;
                }
            }
            case TERMINAL -> {
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

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        clampPanelSizes();

        drawString(poseStack, font, title, MARGIN, MARGIN, 0xFFFFFF);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_PLAYGROUND_SUBTITLE.getComponent(), MARGIN, MARGIN + 12, 0xA0A0A0);
        IdeSessionCapture.capture(session, Minecraft.getInstance(), focusedPanel.display().getString());

        IdeArea rootArea = new IdeArea(
                MARGIN,
                MARGIN + HEADER_HEIGHT,
                Math.max(0, width - MARGIN * 2),
                Math.max(0, height - MARGIN * 2 - HEADER_HEIGHT)
        );

        List<IdeDockPiece<PlaygroundPanel>> pieces = List.of(
                new IdeDockPiece<>(PlaygroundPanel.SHELL, IdeDockDirection.LEFT, shellPanelWidth),
                new IdeDockPiece<>(PlaygroundPanel.WORKSPACE, IdeDockDirection.CENTER, 0),
                new IdeDockPiece<>(PlaygroundPanel.LAYOUT, IdeDockDirection.RIGHT, layoutPanelWidth),
                new IdeDockPiece<>(PlaygroundPanel.TERMINAL, IdeDockDirection.DOWN, terminalPanelHeight)
        );
        Map<PlaygroundPanel, IdeArea> layout = IdeDockLayout.calculate(rootArea, pieces);

        drawPanel(poseStack, layout.get(PlaygroundPanel.SHELL), IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_SHELL.getComponent(), focusedPanel == PlaygroundPanel.SHELL, buildShellLines());
        drawWorkspacePanel(poseStack, layout.get(PlaygroundPanel.WORKSPACE), focusedPanel == PlaygroundPanel.WORKSPACE, layout);
        drawPanel(poseStack, layout.get(PlaygroundPanel.LAYOUT), IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_LAYOUT.getComponent(), focusedPanel == PlaygroundPanel.LAYOUT, buildLayoutLines());
        drawPanel(poseStack, layout.get(PlaygroundPanel.TERMINAL), IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_TERMINAL.getComponent(), focusedPanel == PlaygroundPanel.TERMINAL, buildTerminalLines());

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
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(displayOrNone(session.focusedTarget().summary())),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_FOCUS.getComponent(focusedPanel.display().getString())
        );
    }

    private List<Component> buildTerminalLines() {
        return List.of(
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TERMINAL.getComponent(),
                Component.literal("/sfm ide ..."),
                Component.literal("sfm:panel.toggle_terminal"),
                Component.literal("sfm:layout.split_down"),
                IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_TARGET.getComponent(displayOrNone(session.focusedTarget().summary()))
        );
    }

    private String displayOrNone(Optional<String> value) {
        return value.orElse("none");
    }

    private void drawWorkspacePanel(PoseStack poseStack, IdeArea area, boolean focused, Map<PlaygroundPanel, IdeArea> outerLayout) {
        drawPanelFrame(poseStack, area, IdeLocalizationKeys.IDE_PLAYGROUND_PANEL_WORKSPACE.getComponent(), focused);
        drawString(poseStack, font, IdeLocalizationKeys.IDE_PLAYGROUND_LABEL_WORKSPACE.getComponent(), area.x() + PANEL_TEXT_PADDING, area.y() + 18, 0xE0E0E0);

        IdeArea previewArea = new IdeArea(
                area.x() + 10,
                area.y() + 32,
                Math.max(0, area.width() - 20),
                Math.max(0, area.height() - 42)
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
            drawCenteredString(poseStack, font, panel.display(), previewX + previewW / 2, previewY + Math.max(1, previewH / 2 - 4), 0xFFFFFF);
        }
    }

    private int scale(int value, int sourceSize, int targetSize) {
        if (sourceSize <= 0 || targetSize <= 0) {
            return 0;
        }
        return (int) Math.round((double) value * targetSize / sourceSize);
    }

    private void drawPanel(PoseStack poseStack, IdeArea area, Component title, boolean focused, List<Component> lines) {
        drawPanelFrame(poseStack, area, title, focused);
        int y = area.y() + 18;
        for (Component line : lines) {
            drawString(poseStack, font, line, area.x() + PANEL_TEXT_PADDING, y, 0xE0E0E0);
            y += font.lineHeight + 2;
            if (y > area.bottom() - font.lineHeight - PANEL_TEXT_PADDING) {
                break;
            }
        }
    }

    private void drawPanelFrame(PoseStack poseStack, IdeArea area, Component panelTitle, boolean focused) {
        int background = focused ? 0xCC18212A : 0xB8141414;
        int border = focused ? 0xFF66CCFF : 0xFF303030;
        fill(poseStack, area.x(), area.y(), area.right(), area.bottom(), background);
        fill(poseStack, area.x(), area.y(), area.right(), area.y() + 1, border);
        fill(poseStack, area.x(), area.bottom() - 1, area.right(), area.bottom(), border);
        fill(poseStack, area.x(), area.y(), area.x() + 1, area.bottom(), border);
        fill(poseStack, area.right() - 1, area.y(), area.right(), area.bottom(), border);
        drawString(poseStack, font, panelTitle, area.x() + PANEL_TEXT_PADDING, area.y() + 5, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum PlaygroundPanel {
        SHELL,
        WORKSPACE,
        LAYOUT,
        TERMINAL;

        public PlaygroundPanel next(boolean reverse) {
            PlaygroundPanel[] values = values();
            int index = ordinal() + (reverse ? -1 : 1);
            if (index < 0) {
                index = values.length - 1;
            }
            if (index >= values.length) {
                index = 0;
            }
            return values[index];
        }

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
