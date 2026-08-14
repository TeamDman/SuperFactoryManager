package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextDocumentProjection;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
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

import java.util.Optional;
import java.util.function.Function;

/**
 * Panel adapter for text editors.  Text Editor v3 uses the specialised
 * callback-aware screen below; legacy registrations still get a useful
 * lifecycle adapter while they are being migrated.
 */
public final class SFMTextEditorPanel implements SFMScreenPanel, SFMTextDocumentPanelState, SFMContextContributor {
    private static final String CONTEXT_CONTRIBUTOR_ID = "sfm:text-editor";
    private final SFMTextEditorPanelOpenContext openContext;
    private final Screen screen;
    private SFMWorkspacePanelContext panelContext;

    private SFMTextEditorPanel(
            SFMTextEditorPanelOpenContext openContext,
            Screen screen
    ) {
        this.openContext = openContext;
        this.screen = screen;
    }

    public static SFMTextEditorPanel textEditorV3(SFMTextEditorPanelOpenContext context) {
        SFMTextEditorPanel[] holder = new SFMTextEditorPanel[1];
        ISFMTextEditScreenOpenContext screenContext = screenContext(
                context,
                () -> {
                    if (holder[0] != null) holder[0].requestClose();
                }
        );
        PanelTextEditorScreen editor = new PanelTextEditorScreen(screenContext, () -> {
            if (holder[0] != null) holder[0].requestClose();
        });
        holder[0] = new SFMTextEditorPanel(context, editor);
        return holder[0];
    }

    public static SFMTextEditorPanel legacy(
            SFMTextEditorPanelOpenContext context,
            Function<ISFMTextEditScreenOpenContext, ISFMTextEditScreen> screenFactory
    ) {
        SFMTextEditorPanel[] holder = new SFMTextEditorPanel[1];
        ISFMTextEditScreenOpenContext screenContext = screenContext(
                context,
                () -> {
                    if (holder[0] != null) holder[0].requestClose();
                }
        );
        Screen screen = screenFactory.apply(screenContext).asScreen();
        holder[0] = new SFMTextEditorPanel(context, screen);
        return holder[0];
    }

    public String editorId() {
        return openContext.editorId();
    }

    /** Explorer previews are reusable only while their document is immutable. */
    public boolean isReadOnly() {
        return openContext.readOnly();
    }

    @Override
    public Optional<ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot> documentSnapshot() {
        return Optional.of(openContext.document());
    }

    @Override
    public String id() {
        return CONTEXT_CONTRIBUTOR_ID;
    }

    @Override
    public Optional<SFMContextOriginId> focusedOriginId() {
        return panelContext == null ? Optional.empty() : Optional.of(contextOrigin());
    }

    @Override
    public java.util.List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
        if (panelContext == null) return java.util.List.of();
        SFMContextDocumentProjection projection;
        long generation;
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            projection = drawCanvas.captureContextProjection(
                    openContext.editorId(),
                    openContext.document(),
                    isReadOnly()
            );
            generation = drawCanvas.contextGeneration();
        } else {
            projection = SFMContextDocumentProjection.capture(
                    openContext.editorId(),
                    openContext.document(),
                    openContext.document().text(),
                    false,
                    isReadOnly(),
                    java.util.List.of(),
                    java.util.List.of()
            );
            generation = 0;
        }
        return java.util.List.of(new SFMContextContribution(
                contextOrigin(),
                new SFMContextGenerationEvidence(generation, generation, generation, 0),
                projection
        ));
    }

    private SFMContextOriginId contextOrigin() {
        return new SFMContextOriginId(
                CONTEXT_CONTRIBUTOR_ID,
                "panel-" + panelContext.panelId().value(),
                "document"
        );
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
        screen.resize(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
    }

    private void init(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        if (screen instanceof SFMDrawCanvasScreen drawCanvas) {
            drawCanvas.init(minecraft, Math.max(1, bounds.width()), Math.max(1, bounds.height()));
            openContext.document().targetRange().ifPresent(drawCanvas::openAtTextRange);
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

    static ISFMTextEditScreenOpenContext screenContext(
            SFMTextEditorPanelOpenContext context,
            Runnable closePanel
    ) {
        return new ISFMTextEditScreenOpenContext() {
            @Override public String initialValue() { return context.initialValue(); }
            @Override public boolean readOnly() { return context.readOnly(); }
            @Override public java.util.function.Consumer<String> saveWriter() {
                return value -> context.saveHandler().save(value);
            }
            @Override public ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult saveDocument(
                    String value
            ) {
                return context.saveHandler().save(value);
            }
            @Override public ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult trySaveAndClose(
                    String value
            ) {
                ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult result = saveDocument(value);
                if (result.saved()) closePanel.run();
                return result;
            }
            @Override public void onTryClose(String latestContent, Runnable ignoredFullScreenClose) {
                ISFMTextEditScreenOpenContext.super.onTryClose(latestContent, closePanel);
            }
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
