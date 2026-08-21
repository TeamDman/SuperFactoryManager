package ca.teamdman.sfm.client.screen.history.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualController;
import ca.teamdman.sfm.client.history.workspace.SFMWorkspaceCounterfactualRuntime;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.history.SFMHistoryGraphPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMFileDropTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceSide;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordinary explorer projection plus the bounded X5 episode observation seam. */
public final class SFMWorkspaceCounterfactualExplorerPanel
        implements SFMScreenPanel, SFMEpisodeContext, SFMContextContributor, SFMFileDropTarget {
    private final SFMWorkspaceCounterfactualRuntime runtime;
    private final SFMWorkspaceCounterfactualController controller;
    private final SFMExplorerPanel delegate;
    private SFMWorkspacePanelContext panelContext;
    private Optional<SFMPath> observedSelection = Optional.empty();
    private boolean observationInitialized;
    private boolean historyOpened;
    private boolean closed;

    public SFMWorkspaceCounterfactualExplorerPanel(
            SFMWorkspaceCounterfactualRuntime runtime,
            SFMWorkspaceCounterfactualController controller,
            SFMExplorerPanel delegate
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.controller = Objects.requireNonNull(controller, "controller");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public SFMExplorerId explorerId() {
        return delegate.explorerId();
    }

    @Override
    public Optional<String> episodeId() {
        return Optional.of(controller.machineId());
    }

    @Override public Component title() { return delegate.title(); }
    @Override public Component narration() { return delegate.narration(); }
    @Override public ResourceLocation keyboardUsageSituationId() { return delegate.keyboardUsageSituationId(); }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        panelContext = Objects.requireNonNull(context, "context");
        delegate.opened(minecraft, bounds, context);
        observeSelection(false);
        if (!historyOpened) {
            historyOpened = true;
            minecraft.execute(() -> {
                if (closed || panelContext == null) return;
                panelContext.submit(new SFMWorkspacePanelIntent.OpenToSide(
                        SFMWorkspaceSide.RIGHT,
                        new SFMHistoryGraphPanel(SFMEntitySelector.exact(
                                SFMEntitySelector.Domain.EPISODE,
                                controller.machineId()
                        )),
                        SFMWorkspacePanelMetadata.ordinary(),
                        null
                ));
            });
        }
    }

    @Override public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        delegate.resized(minecraft, bounds);
    }

    @Override
    public void closed() {
        if (closed) return;
        closed = true;
        delegate.closed();
        runtime.closeEpisode(controller.machineId());
        panelContext = null;
    }

    @Override public void tick() { delegate.tick(); }

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
        delegate.render(poseStack, minecraft, bounds, mouseX, mouseY, partialTick, focused);
        observeSelection(false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = delegate.keyPressed(keyCode, scanCode, modifiers);
        observeSelection(handled);
        return handled;
    }

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return delegate.keyReleased(keyCode, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return delegate.charTyped(character, modifiers);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = delegate.mouseClicked(mouseX, mouseY, button);
        observeSelection(handled);
        return handled;
    }
    @Override public void mouseMoved(double mouseX, double mouseY) { delegate.mouseMoved(mouseX, mouseY); }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return delegate.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return delegate.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return delegate.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override public void onFilesDrop(List<Path> paths) { delegate.onFilesDrop(paths); }

    @Override public Optional<SFMContextOriginId> focusedOriginId() { return delegate.focusedOriginId(); }
    @Override public String id() { return delegate.id(); }
    @Override public List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
        return delegate.capture(request);
    }

    private void observeSelection(boolean userInputHandled) {
        Optional<SFMPath> current = delegate.sessionSnapshot().navigationCursor();
        if (!observationInitialized) {
            observationInitialized = true;
            observedSelection = current;
            return;
        }
        if (!userInputHandled || current.equals(observedSelection)) return;
        observedSelection = current;
        current.filter(path -> path.scheme().equals(SFMWorkspaceCounterfactualRuntime.PATH_SCHEME))
                .filter(path -> path.segments().size() == 1)
                .ifPresent(this::submitSelectionAction);
    }

    private void submitSelectionAction(SFMPath path) {
        SFMWorkspacePanelContext context = panelContext;
        if (context == null) return;
        String selector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EPISODE,
                controller.machineId()
        ).canonical();
        String command = "sfm action invoke sfm:episode/workspace-counterfactual/selection/set "
                + selector + " " + SFMWorkspaceCounterfactualRuntime.logicalPath(path);
        Object host = context.host();
        SFMClientActionContext actionContext = new SFMClientActionContext(
                host,
                () -> Minecraft.getInstance().screen == host,
                context.panelId()
        );
        try {
            SFMClientActionExecutor.execute(
                    command,
                    actionContext,
                    feedback -> SFM.LOGGER.info("SFM_WORKSPACE_COUNTERFACTUAL_SELECTION {}", feedback.getString())
            );
        } catch (CommandSyntaxException failure) {
            SFM.LOGGER.warn("Could not record workspace fixture selection {}", path, failure);
        }
    }
}
