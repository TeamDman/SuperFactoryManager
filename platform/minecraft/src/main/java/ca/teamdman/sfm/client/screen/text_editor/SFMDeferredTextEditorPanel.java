package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCancellationToken;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Non-blocking panel that resolves one addressed document before hosting its editor. */
public final class SFMDeferredTextEditorPanel implements SFMScreenPanel, SFMTextDocumentPanelState {
    private final SFMTextEditorPanelRecipe recipe;
    private final SFMExplorerCancellationToken cancellation = new SFMExplorerCancellationToken();
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMWorkspacePanelContext panelContext;
    private SFMScreenPanel delegate;
    private CompletableFuture<SFMTextDocumentSnapshot> load;
    private Optional<SFMTextDocumentSnapshot> snapshot = Optional.empty();
    private String status = "Loading addressed document...";
    private boolean closed;

    public SFMDeferredTextEditorPanel(SFMTextEditorPanelRecipe recipe) {
        this.recipe = Objects.requireNonNull(recipe, "recipe");
    }

    @Override
    public Component title() {
        return Component.literal(recipe.title());
    }

    @Override
    public Component narration() {
        return delegate == null ? Component.literal(recipe.title() + ". " + status) : delegate.narration();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public Optional<SFMTextDocumentSnapshot> documentSnapshot() {
        return snapshot;
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        panelContext = Objects.requireNonNull(context, "context");
        load = recipe.documentSource().load(cancellation);
        load.whenComplete((loaded, failure) -> minecraft.execute(() -> complete(minecraft, loaded, failure)));
    }

    private void complete(Minecraft minecraft, SFMTextDocumentSnapshot loaded, Throwable failure) {
        if (closed) return;
        if (failure != null) {
            status = "Document load failed: " + rootMessage(failure);
            return;
        }
        snapshot = Optional.of(Objects.requireNonNull(loaded, "loaded"));
        SFMTextEditorPanelRecipe resolvedRecipe = recipe;
        if (loaded.ready()
                && recipe.documentSource() instanceof SFMTextDocumentSource.PathAddress pathSource
                && pathSource.expectedSha256().isEmpty()
                && loaded.sha256().isPresent()) {
            resolvedRecipe = recipe.withDocumentSource(pathSource.withExpectedSha256(loaded.sha256().orElseThrow()));
            if (panelContext.host() instanceof SFMScreenMultiplexer workspace) {
                workspace.setPanelReopenRecipe(panelContext.panelId(), resolvedRecipe);
            }
        }
        delegate = resolvedRecipe.createResolvedPanel(loaded);
        delegate.opened(minecraft, bounds, panelContext);
        status = loaded.ready() ? "Ready" : "Document unavailable: " + loaded.state().name().toLowerCase();
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        if (delegate != null) delegate.resized(minecraft, bounds);
    }

    @Override
    public void closed() {
        if (closed) return;
        closed = true;
        cancellation.cancel();
        if (load != null) load.cancel(false);
        if (delegate != null) delegate.closed();
        delegate = null;
        panelContext = null;
    }

    @Override public void tick() { if (delegate != null) delegate.tick(); }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        if (delegate != null) {
            delegate.render(poseStack, minecraft, bounds, mouseX, mouseY, partialTick, focused);
            return;
        }
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(),
                bounds.y() + bounds.height(), 0xFF15191E);
        SFMFontUtils.draw(poseStack, minecraft.font, recipe.title(), bounds.x() + 8, bounds.y() + 8,
                0xFFE6EDF3, true);
        SFMFontUtils.draw(poseStack, minecraft.font,
                minecraft.font.plainSubstrByWidth(status, Math.max(0, bounds.width() - 16)),
                bounds.x() + 8, bounds.y() + 24, 0xFFAAAAAA, false);
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return delegate != null && delegate.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return delegate != null && delegate.keyReleased(keyCode, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) {
        return delegate != null && delegate.charTyped(character, modifiers);
    }
    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return delegate != null && delegate.mouseClicked(mouseX, mouseY, button);
    }
    @Override public void mouseMoved(double mouseX, double mouseY) {
        if (delegate != null) delegate.mouseMoved(mouseX, mouseY);
    }
    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return delegate != null && delegate.mouseReleased(mouseX, mouseY, button);
    }
    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return delegate != null && delegate.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return delegate != null && delegate.mouseScrolled(mouseX, mouseY, delta);
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
