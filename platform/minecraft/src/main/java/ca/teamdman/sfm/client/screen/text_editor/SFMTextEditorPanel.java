package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.screen.SFMDrawCanvasScreen;
import ca.teamdman.sfm.client.screen.SFMTextEditorV3Screen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditScreenOpenContext;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Panel adapter for text editors.  Text Editor v3 uses the specialised
 * callback-aware screen below; legacy registrations still get a useful
 * lifecycle adapter while they are being migrated.
 */
public final class SFMTextEditorPanel implements SFMScreenPanel {
    private final SFMTextEditorPanelOpenContext openContext;
    private final Screen screen;
    private final Consumer<String> savedContent;
    private SFMWorkspacePanelContext panelContext;

    private SFMTextEditorPanel(
            SFMTextEditorPanelOpenContext openContext,
            Screen screen,
            Consumer<String> savedContent
    ) {
        this.openContext = openContext;
        this.screen = screen;
        this.savedContent = savedContent;
    }

    public static SFMTextEditorPanel textEditorV3(SFMTextEditorPanelOpenContext context) {
        final String[] saved = {context.initialValue()};
        ISFMTextEditScreenOpenContext screenContext = screenContext(context, value -> saved[0] = value);
        SFMTextEditorPanel[] holder = new SFMTextEditorPanel[1];
        PanelTextEditorScreen editor = new PanelTextEditorScreen(screenContext, () -> {
            if (holder[0] != null) holder[0].requestClose();
        });
        holder[0] = new SFMTextEditorPanel(context, editor, value -> saved[0] = value);
        return holder[0];
    }

    public static SFMTextEditorPanel legacy(
            SFMTextEditorPanelOpenContext context,
            Screen screen
    ) {
        return new SFMTextEditorPanel(context, screen, ignored -> { });
    }

    public String editorId() {
        return openContext.editorId();
    }

    @Override
    public Component title() {
        return Component.literal(openContext.title());
    }

    @Override
    public Component narration() {
        return title().copy().append(Component.literal(openContext.readOnly() ? " (read-only)" : ""));
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.panelContext = context;
        init(minecraft, bounds);
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        init(minecraft, bounds);
    }

    private void init(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            drawCanvas.init(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
        } else {
            screen.init(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
        }
    }

    @Override
    public void closed() {
        panelContext = null;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        screen.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        screen.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return screen.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return screen.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return screen.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return screen.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        screen.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return screen.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return screen.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return screen.mouseScrolled(mouseX, mouseY, delta);
    }

    private void requestClose() {
        if (panelContext != null) {
            panelContext.submit(new ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent.Close());
        }
    }

    private static ISFMTextEditScreenOpenContext screenContext(
            SFMTextEditorPanelOpenContext context,
            Consumer<String> saveWriter
    ) {
        return new ISFMTextEditScreenOpenContext() {
            @Override public String initialValue() { return context.initialValue(); }
            @Override public boolean readOnly() { return context.readOnly(); }
            @Override public Consumer<String> saveWriter() { return saveWriter; }
            @Override public LabelPositionHolder labelPositionHolder() { return LabelPositionHolder.empty(); }
        };
    }

    private static final class PanelTextEditorScreen extends SFMTextEditorV3Screen {
        private final Runnable close;

        private PanelTextEditorScreen(ISFMTextEditScreenOpenContext context, Runnable close) {
            super(context, null, false);
            this.close = close;
        }

        @Override
        protected void finishClose() {
            close.run();
        }
    }
}
