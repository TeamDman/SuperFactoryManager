package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.screen.SFMScreenChangeHelpers;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns Minecraft's Screen lifecycle while hosting a normalized tree of SFM panels. */
public final class SFMScreenMultiplexer extends Screen implements SFMWorkspacePanelHost {
    private static final int DIVIDER_WIDTH = 2;
    private static final int PANEL_BACKGROUND = 0xE0202020;
    private static final int FOCUSED_BORDER = 0xFF55FFFF;
    private static final int UNFOCUSED_BORDER = 0xFF606060;

    private final @Nullable Screen previousScreen;
    private final SFMWorkspaceLayout layout;
    private final @Nullable SFMWorkspacePanelGroup panelGroup;
    private final Set<SFMWorkspacePanelId> openedPanels = new HashSet<>();
    private Map<SFMWorkspacePanelId, SFMScreenPanelBounds> panelBounds = Map.of();
    private boolean closing;
    private @Nullable Component dropFeedback;
    private long panelGroupRevision = Long.MIN_VALUE;

    private SFMScreenMultiplexer(
            @Nullable Screen previousScreen,
            SFMScreenPanel left,
            SFMScreenPanel right
    ) {
        this(previousScreen, SFMWorkspaceLayout.sideBySide(left, right), null);
    }

    private SFMScreenMultiplexer(
            @Nullable Screen previousScreen,
            SFMWorkspaceLayout layout
    ) {
        this(previousScreen, layout, null);
    }

    private SFMScreenMultiplexer(
            @Nullable Screen previousScreen,
            SFMWorkspaceLayout layout,
            @Nullable SFMWorkspacePanelGroup panelGroup
    ) {
        super(Component.literal("SFM workspace"));
        this.previousScreen = previousScreen;
        this.layout = layout;
        this.panelGroup = panelGroup;
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMScreenPanel initialPanel
    ) {
        return new SFMScreenMultiplexer(previousScreen, SFMWorkspaceLayout.single(initialPanel));
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMWorkspacePanelGroup group
    ) {
        SFMScreenPanelBounds initialBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
        return new SFMScreenMultiplexer(previousScreen, SFMWorkspaceLayout.group(group.layout(initialBounds)), group);
    }

    public static void openToSide(@Nullable Screen origin, SFMScreenPanel panel) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.submit(
                    multiplexer.layout.focusedPanel(),
                    new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, panel)
            );
            return;
        }
        SFMScreenChangeHelpers.setScreen(new SFMScreenMultiplexer(
                origin,
                new SFMPreviousScreenPanel(origin),
                panel
        ));
    }

    public void openToSide(SFMScreenPanel panel) {
        submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, panel));
    }

    /** Traversal index retained for automation; panel identity is exposed separately. */
    public int focusedPanel() {
        List<SFMWorkspaceLayout.PanelEntry> panels = layout.panels();
        for (int i = 0; i < panels.size(); i++) {
            if (panels.get(i).id().equals(layout.focusedPanel())) return i;
        }
        return -1;
    }

    public SFMWorkspacePanelId focusedPanelId() {
        return layout.focusedPanel();
    }

    public List<SFMScreenPanel> panels() {
        return layout.panels().stream().map(SFMWorkspaceLayout.PanelEntry::panel).toList();
    }

    public List<SFMWorkspacePanelId> panelIds() {
        return layout.panels().stream().map(SFMWorkspaceLayout.PanelEntry::id).toList();
    }

    public @Nullable SFMScreenPanelBounds panelBounds(SFMWorkspacePanelId panelId) {
        return panelBounds.get(panelId);
    }

    @Override
    public SFMWorkspacePanelIntentResult submit(
            SFMWorkspacePanelId source,
            SFMWorkspacePanelIntent intent
    ) {
        SFMWorkspacePanelIntentDispatcher.Outcome outcome =
                SFMWorkspacePanelIntentDispatcher.apply(layout, source, intent);
        if (outcome.result() != SFMWorkspacePanelIntentResult.APPLIED) return outcome.result();
        if (outcome.inserted() != null) {
            refreshLayout(true);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
        outcome.removedPanel().closed();
        openedPanels.remove(outcome.removed());
        if (layout.panels().isEmpty()) {
            onClose();
        } else {
            refreshLayout(true);
        }
        return SFMWorkspacePanelIntentResult.APPLIED;
    }

    @Override
    protected void init() {
        super.init();
        refreshLayout(true);
    }

    private void refreshLayout(boolean notifyPanels) {
        if (panelGroup != null) {
            layout.recompose(panelGroup.layout(new SFMScreenPanelBounds(0, 0, this.width, this.height)));
            panelGroupRevision = panelGroup.revision();
        }
        panelBounds = layout.bounds(new SFMScreenPanelBounds(0, 0, this.width, this.height), DIVIDER_WIDTH);
        if (!notifyPanels || this.minecraft == null) return;
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            if (openedPanels.contains(entry.id())) {
                entry.panel().resized(this.minecraft, contentBounds(entry.id()));
            } else {
                openPanel(entry.id(), entry.panel());
            }
        }
    }

    private void openPanel(SFMWorkspacePanelId id, SFMScreenPanel panel) {
        panel.opened(this.minecraft, contentBounds(id), new SFMWorkspacePanelContext(id, this));
        openedPanels.add(id);
    }

    @Override
    public void tick() {
        if (panelGroup != null && panelGroupRevision != panelGroup.revision()) refreshLayout(true);
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) entry.panel().tick();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public Component getNarrationMessage() {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        Component narration = focused == null
                ? Component.literal("Empty SFM workspace")
                : Component.literal("SFM workspace. Focused panel: ").append(focused.narration());
        return dropFeedback == null ? narration : narration.copy().append(Component.literal(". ")).append(dropFeedback);
    }

    @Override
    public void onClose() {
        if (closing) return;
        closing = true;
        for (SFMWorkspaceLayout.PanelEntry entry : layout.allPanels()) entry.panel().closed();
        openedPanels.clear();
        SFMScreenChangeHelpers.setScreen(previousScreen);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds == null) continue;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_BACKGROUND);
            int border = entry.id().equals(layout.focusedPanel()) ? FOCUSED_BORDER : UNFOCUSED_BORDER;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, border);
            fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            enableScissor(bounds.inset(1));
            entry.panel().render(
                    poseStack,
                    this.minecraft,
                    bounds.inset(1),
                    mouseX,
                    mouseY,
                    partialTick,
                    entry.id().equals(layout.focusedPanel())
            );
            RenderSystem.disableScissor();
        }
        if (dropFeedback != null) {
            SFMFontUtils.draw(poseStack, this.font, dropFeedback, 6, Math.max(2, this.height - 12), 0xFFFF7777, true);
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (panelGroup != null && Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_M) {
            SFMScreenPanel focused = layout.panel(layout.focusedPanel());
            if (focused != null) {
                panelGroup.toggleMaximize(focused);
                refreshLayout(true);
                return true;
            }
        }
        if (Screen.hasControlDown() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int requestedIndex = keyCode - GLFW.GLFW_KEY_1;
            List<SFMWorkspaceLayout.PanelEntry> panels = layout.panels();
            if (requestedIndex < panels.size()) layout.focus(panels.get(requestedIndex).id());
            return requestedIndex < panels.size();
        }
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.keyReleased(keyCode, scanCode, modifiers)
                || super.keyReleased(keyCode, scanCode, modifiers));
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.charTyped(character, modifiers)
                || super.charTyped(character, modifiers));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
            layout.focus(entry.id());
            entry.panel().mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) entry.panel().mouseMoved(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) layout.focus(entry.id());
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        return focused != null && (focused.mouseScrolled(mouseX, mouseY, delta)
                || super.mouseScrolled(mouseX, mouseY, delta));
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        if (focused instanceof SFMFileDropTarget dropTarget) {
            dropFeedback = null;
            dropTarget.onFilesDrop(List.copyOf(paths));
        } else {
            dropFeedback = Component.literal("Drop rejected: focused panel does not accept directories");
        }
    }

    private @Nullable SFMWorkspaceLayout.PanelEntry panelAt(double mouseX, double mouseY) {
        for (SFMWorkspaceLayout.PanelEntry entry : layout.panels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds != null && bounds.contains(mouseX, mouseY)) return entry;
        }
        return null;
    }

    private SFMScreenPanelBounds contentBounds(SFMWorkspacePanelId panelId) {
        SFMScreenPanelBounds bounds = panelBounds.get(panelId);
        return bounds == null ? new SFMScreenPanelBounds(0, 0, 0, 0) : bounds.inset(1);
    }

    @MCVersionDependentBehaviour
    private static void enableScissor(SFMScreenPanelBounds bounds) {
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int left = (int) Math.floor(bounds.x() * scale);
        int right = (int) Math.ceil((bounds.x() + bounds.width()) * scale);
        int top = (int) Math.floor(bounds.y() * scale);
        int bottom = (int) Math.ceil((bounds.y() + bounds.height()) * scale);
        RenderSystem.enableScissor(
                left,
                window.getHeight() - bottom,
                Math.max(0, right - left),
                Math.max(0, bottom - top)
        );
    }
}
