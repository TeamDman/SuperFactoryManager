package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMFileDropTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Small mutable, read-only document viewport used by the explorer workspace. */
public final class SFMReadOnlyTextPanel implements SFMScreenPanel, SFMFileDropTarget {
    private static final int ROW_HEIGHT = 11;
    private final Runnable closeCallback;
    private final Consumer<List<Path>> dropConsumer;
    private String sourceName = "";
    private String path = "";
    private List<String> lines = List.of();
    private int firstLine;

    public SFMReadOnlyTextPanel(Runnable closeCallback, Consumer<List<Path>> dropConsumer) {
        this.closeCallback = closeCallback;
        this.dropConsumer = dropConsumer;
    }

    public void show(String sourceName, SFMFileExplorerEntry entry, String text) {
        this.sourceName = sourceName;
        this.path = entry.path();
        this.lines = List.of(text.split("\\R", -1));
        this.firstLine = 0;
    }

    public String path() { return path; }
    public String text() { return String.join("\n", lines); }
    int firstLine() { return firstLine; }

    @Override
    public Component title() { return Component.literal(path.isBlank() ? "Read-only text" : path); }

    @Override
    public Component narration() {
        return Component.literal("Read-only text " + path + " from " + sourceName);
    }

    @Override
    public void closed() { closeCallback.run(); }

    @Override
    public void onFilesDrop(List<Path> paths) { dropConsumer.accept(List.copyOf(paths)); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) firstLine = Math.max(0, firstLine - 1);
        else if (keyCode == GLFW.GLFW_KEY_DOWN) firstLine = Math.min(Math.max(0, lines.size() - 1), firstLine + 1);
        else if (keyCode == GLFW.GLFW_KEY_HOME) firstLine = 0;
        else if (keyCode == GLFW.GLFW_KEY_END) firstLine = Math.max(0, lines.size() - 1);
        else return false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        firstLine = Math.max(0, Math.min(Math.max(0, lines.size() - 1), firstLine + (delta > 0 ? -1 : 1)));
        return true;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 24, 0xF02A2A2A);
        SFMFontUtils.draw(poseStack, minecraft.font, title().copy().withStyle(ChatFormatting.BOLD),
                bounds.x() + 8, bounds.y() + 7, 0xFFFFFFFF, true);
        int visible = Math.max(0, (bounds.height() - 30) / ROW_HEIGHT);
        for (int i = 0; i < visible && firstLine + i < lines.size(); i++) {
            String line = minecraft.font.plainSubstrByWidth(lines.get(firstLine + i), Math.max(0, bounds.width() - 16));
            SFMFontUtils.draw(poseStack, minecraft.font, line, bounds.x() + 8, bounds.y() + 28 + i * ROW_HEIGHT,
                    0xFFE0E0E0, true);
        }
    }
}
