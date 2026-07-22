package ca.teamdman.sfm.client.screen.workspace.diagnostic;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Composable test card for diagnosing viewport allocation and coordinate transforms. */
public final class SFMViewportCalibrationPanel implements SFMScreenPanel {
    private static final int MARKER_COLOUR = 0xFFFF55FF;
    private final String allocationLabel;
    private final SFMViewportDiagnosticsSource diagnosticsSource;

    public SFMViewportCalibrationPanel(String allocationLabel) {
        this(allocationLabel, SFMViewportDiagnosticsSource.minecraftCurrent("diagnostic"));
    }

    public SFMViewportCalibrationPanel(
            String allocationLabel,
            SFMViewportDiagnosticsSource diagnosticsSource
    ) {
        this.allocationLabel = Objects.requireNonNull(allocationLabel);
        this.diagnosticsSource = Objects.requireNonNull(diagnosticsSource);
    }

    @Override
    public Component title() {
        return Component.literal("Viewport calibration · " + allocationLabel);
    }

    @Override
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        SFMViewportCalibrationGeometry geometry = SFMViewportCalibrationGeometry.create(bounds, mouseX, mouseY);
        for (var bar : geometry.colourBars()) fill(poseStack, bar.bounds(), bar.argb());
        for (var checker : geometry.checkerboard()) {
            GuiComponent.fill(poseStack, checker.x(), checker.y(), checker.x() + 1, checker.y() + 1,
                    checker.light() ? 0xFFFFFFFF : 0xFF000000);
        }
        for (var line : geometry.markers()) {
            GuiComponent.fill(poseStack, line.x0(), line.y0(), line.x1(), line.y1(), MARKER_COLOUR);
        }

        SFMViewportDiagnostics diagnostics = diagnosticsSource.snapshot(minecraft);
        List<String> lines = diagnosticLines(allocationLabel, bounds, diagnostics, geometry.pointer());
        int y = bounds.y() + geometry.colourBars().get(0).bounds().height() + 4;
        int availableWidth = Math.max(0, bounds.width() - 8);
        for (int index = 0; index < lines.size() && y + minecraft.font.lineHeight <= bounds.y() + bounds.height() - 2; index++) {
            String visible = minecraft.font.plainSubstrByWidth(lines.get(index), availableWidth);
            SFMFontUtils.draw(poseStack, minecraft.font, visible, bounds.x() + 4, y,
                    index == 0 ? 0xFFFFFFFF : 0xFFDDDDDD, index == 0);
            y += minecraft.font.lineHeight + 1;
        }
        SFMFontUtils.draw(poseStack, minecraft.font,
                focused ? Component.literal("FOCUSED").withStyle(ChatFormatting.AQUA) : Component.literal("unfocused"),
                bounds.x() + 4,
                Math.max(bounds.y() + 1, bounds.y() + bounds.height() - minecraft.font.lineHeight - 2),
                0xFFFFFFFF,
                true);
    }

    private static void fill(PoseStack poseStack, SFMScreenPanelBounds bounds, int colour) {
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), colour);
    }

    static List<String> diagnosticLines(
            String allocationLabel,
            SFMScreenPanelBounds bounds,
            SFMViewportDiagnostics diagnostics,
            SFMViewportCalibrationGeometry.Pointer pointer
    ) {
        String panel = allocationLabel + "  panel " + bounds.width() + "x" + bounds.height()
                + " @ " + bounds.x() + "," + bounds.y();
        String gui = "GUI " + diagnostics.requestedGuiScale()
                + " (effective " + compact(diagnostics.effectiveGuiScale()) + ")";
        String status = pointer.inside() ? "inside" : "outside";
        if (bounds.width() < 300) {
            return List.of(
                    panel,
                    "win " + diagnostics.windowWidth() + "x" + diagnostics.windowHeight(),
                    "fb " + diagnostics.framebufferWidth() + "x" + diagnostics.framebufferHeight(),
                    "logical " + diagnostics.logicalWidth() + "x" + diagnostics.logicalHeight(),
                    gui,
                    "mode " + diagnostics.responsiveMode(),
                    "pointer screen " + pointer.screenX() + "," + pointer.screenY(),
                    "local " + pointer.localX() + "," + pointer.localY() + "  " + status
            );
        }
        return List.of(
                panel,
                "window " + diagnostics.windowWidth() + "x" + diagnostics.windowHeight()
                        + "  framebuffer " + diagnostics.framebufferWidth() + "x" + diagnostics.framebufferHeight(),
                "logical " + diagnostics.logicalWidth() + "x" + diagnostics.logicalHeight() + "  " + gui,
                "mode " + diagnostics.responsiveMode(),
                "pointer screen " + pointer.screenX() + "," + pointer.screenY()
                        + "  local " + pointer.localX() + "," + pointer.localY() + "  " + status
        );
    }

    private static String compact(double value) {
        return value == Math.rint(value)
                ? Integer.toString((int) value)
                : String.format(Locale.ROOT, "%.2f", value);
    }
}
