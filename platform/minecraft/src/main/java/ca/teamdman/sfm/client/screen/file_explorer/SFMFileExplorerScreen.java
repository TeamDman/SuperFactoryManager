package ca.teamdman.sfm.client.screen.file_explorer;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Compatibility full-screen wrapper around the composable explorer panel. */
public class SFMFileExplorerScreen extends Screen {
    private final @Nullable Screen previousScreen;
    private final SFMFileExplorerPanel panel;
    private boolean opened;

    public SFMFileExplorerScreen(
            @Nullable Screen previousScreen,
            SFMFileExplorerSource source,
            Consumer<SFMFileExplorerModel.OpenIntent> openIntentConsumer
    ) {
        super(Component.literal("SFM File Explorer"));
        this.previousScreen = previousScreen;
        this.panel = new SFMFileExplorerPanel(source, openIntentConsumer);
    }

    public static SFMFileExplorerScreen createFixture(@Nullable Screen previousScreen) {
        return new SFMFileExplorerScreen(previousScreen, new SFMFileExplorerFixtureSource(), intent -> {});
    }

    public SFMFileExplorerPanel panel() { return panel; }

    public void acceptSnapshot(SFMFileExplorerSnapshot snapshot) { panel.acceptSnapshot(snapshot); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, width, height);
        if (opened) panel.resized(minecraft, bounds);
        else {
            opened = true;
            panel.opened(minecraft, bounds, SFMWorkspacePanelContext.unhosted(new SFMWorkspacePanelId(0)));
        }
    }

    @Override
    public void onClose() {
        closePanel();
        Minecraft.getInstance().setScreen(previousScreen);
    }

    @Override
    public void removed() {
        closePanel();
        super.removed();
    }

    private void closePanel() {
        if (!opened) return;
        opened = false;
        panel.closed();
    }

    @Override
    public Component getNarrationMessage() { return panel.narration(); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return panel.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return panel.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return panel.mouseScrolled(mouseX, mouseY, delta) || super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onFilesDrop(List<Path> paths) { panel.onFilesDrop(List.copyOf(paths)); }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        renderBackground(poseStack);
        panel.render(poseStack, minecraft, new SFMScreenPanelBounds(0, 0, width, height),
                mouseX, mouseY, partialTick, true);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }
}
