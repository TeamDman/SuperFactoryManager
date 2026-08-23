package ca.teamdman.sfm.client.screen.history.workspace;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualController;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualRuntime;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextDocumentPanelState;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelCloseState;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ordinary writable Text Editor V3 projected over one immutable X5 workspace frame.
 *
 * <p>Natural key events remain editor-local until the text has been stable for a
 * few ticks. The resulting complete UTF-8 value is then committed atomically
 * against the exact parent frame captured when this panel opened.</p>
 */
public final class SFMWorkspaceCounterfactualDocumentPanel
        implements SFMScreenPanel, SFMEpisodeContext, SFMTextDocumentPanelState, SFMContextContributor {
    private static final int STABLE_TICKS_BEFORE_COMMIT = 4;

    private final SFMWorkspaceCounterfactualRuntime runtime;
    private final SFMWorkspaceCounterfactualController controller;
    private final SFMPath path;
    private final String logicalPath;
    private final SFMTextEditorPanel editor;
    private String expectedParentStateId;
    private String observedText;
    private int stableTicks;
    private boolean dirty;
    private boolean closed;

    public SFMWorkspaceCounterfactualDocumentPanel(
            SFMWorkspaceCounterfactualRuntime runtime,
            SFMWorkspaceCounterfactualController controller,
            SFMPath path,
            String expectedParentStateId
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.path = Objects.requireNonNull(path, "path");
        this.logicalPath = SFMWorkspaceCounterfactualRuntime.logicalPath(path);
        this.expectedParentStateId = Objects.requireNonNull(expectedParentStateId, "expectedParentStateId");
        this.observedText = controller.documentText(logicalPath);
        SFMTextDocumentSnapshot snapshot = addressedSnapshot(observedText);
        editor = SFMTextEditorPanel.textEditorV3(new SFMTextEditorPanelOpenContext(
                "sfm:text_editor_v3",
                snapshot,
                false,
                logicalPath + " — counterfactual workspace",
                this::saveNow
        ));
    }

    @Override
    public Optional<String> episodeId() {
        return Optional.of(controller.machineId());
    }

    @Override public Component title() { return Component.literal(logicalPath); }
    @Override public Component narration() { return editor.narration(); }
    @Override public ResourceLocation keyboardUsageSituationId() { return editor.keyboardUsageSituationId(); }
    @Override public Optional<SFMPanelWidgetHost> widgetHost() { return editor.widgetHost(); }
    @Override public boolean widgetHostOwnsInput() { return editor.widgetHostOwnsInput(); }
    @Override public boolean isReadOnly() { return false; }
    @Override public SFMPanelCloseState closeState() { return new SFMPanelCloseState(dirty, false); }
    @Override public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
        return Optional.of(addressedSnapshot(editor.currentText()));
    }
    @Override public boolean navigateToRange(SFMTextDocumentRange range) { return editor.navigateToRange(range); }
    @Override public String id() { return editor.id(); }
    @Override public Optional<SFMContextOriginId> focusedOriginId() { return editor.focusedOriginId(); }
    @Override public List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
        return editor.capture(request);
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        editor.opened(minecraft, bounds, context);
    }

    @Override public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        editor.resized(minecraft, bounds);
    }

    @Override
    public void closed() {
        if (closed) return;
        commitIfDirty();
        closed = true;
        editor.closed();
    }

    @Override
    public void tick() {
        editor.tick();
        observeText();
        if (dirty && ++stableTicks >= STABLE_TICKS_BEFORE_COMMIT) commitIfDirty();
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        editor.render(poseStack, minecraft, bounds, mouseX, mouseY, partialTick, focused);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = editor.keyPressed(keyCode, scanCode, modifiers);
        observeText();
        return handled;
    }
    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean handled = editor.keyReleased(keyCode, scanCode, modifiers);
        observeText();
        return handled;
    }
    @Override public boolean charTyped(char character, int modifiers) {
        boolean handled = editor.charTyped(character, modifiers);
        observeText();
        return handled;
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = editor.mouseClicked(mouseX, mouseY, button);
        observeText();
        return handled;
    }
    @Override public void mouseMoved(double mouseX, double mouseY) { editor.mouseMoved(mouseX, mouseY); }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean handled = editor.mouseReleased(mouseX, mouseY, button);
        observeText();
        return handled;
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        boolean handled = editor.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        observeText();
        return handled;
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return editor.mouseScrolled(mouseX, mouseY, delta);
    }

    private void observeText() {
        String current = restoreTrailingLineEnding(controller.documentText(logicalPath), editor.currentText());
        if (current.equals(observedText)) return;
        observedText = current;
        dirty = true;
        stableTicks = 0;
    }

    private SFMTextDocumentSaveResult saveNow(String text) {
        observedText = restoreTrailingLineEnding(controller.documentText(logicalPath), text);
        dirty = true;
        stableTicks = STABLE_TICKS_BEFORE_COMMIT;
        return commitIfDirty()
                ? SFMTextDocumentSaveResult.success()
                : SFMTextDocumentSaveResult.rejected(Component.literal(
                        "The counterfactual document no longer matches its captured parent"));
    }

    private boolean commitIfDirty() {
        if (!dirty) return true;
        SFMHistoryGraphRuntime.OperationResult result = controller.acceptEditorText(
                expectedParentStateId,
                logicalPath,
                observedText
        );
        if (result.status() == SFMHistoryGraphRuntime.OperationStatus.APPLIED) {
            dirty = false;
            stableTicks = 0;
            expectedParentStateId = controller.currentFrame().id();
            runtime.publish(controller);
            return true;
        }
        if (result.status() == SFMHistoryGraphRuntime.OperationStatus.NO_CHANGE) {
            dirty = false;
            stableTicks = 0;
            return true;
        }
        editor.checkoutDocument(controller.documentText(logicalPath), List.of());
        observedText = controller.documentText(logicalPath);
        dirty = false;
        stableTicks = 0;
        return false;
    }

    private SFMTextDocumentSnapshot addressedSnapshot(String text) {
        SFMTextDocumentSnapshot literal = SFMTextDocumentSnapshot.literal(text);
        return new SFMTextDocumentSnapshot(
                SFMTextDocumentSnapshot.State.READY,
                text,
                SFMTextDocumentSnapshot.MutationCapability.EDITABLE_IN_MEMORY,
                Optional.of(path),
                Optional.of(SFMWorkspaceCounterfactualRuntime.rootPath(controller.machineId())),
                literal.sha256(),
                literal.byteLength(),
                Optional.empty(),
                Optional.of(SFMResolverTextResult.LineEndingKind.LF),
                Optional.empty(),
                List.of("episode-owned in-memory document; ambient checkout is not writable"),
                Optional.empty()
        );
    }

    private static String restoreTrailingLineEnding(String authoritative, String projected) {
        String ending = authoritative.endsWith("\r\n") ? "\r\n"
                : authoritative.endsWith("\n") || authoritative.endsWith("\r")
                        ? authoritative.substring(authoritative.length() - 1)
                        : "";
        return ending.isEmpty() || projected.endsWith(ending) ? projected : projected + ending;
    }
}
