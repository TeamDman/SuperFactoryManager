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
    private final Map<SFMWorkspacePanelId, SFMScreenPanel> openedPanelInstances = new java.util.HashMap<>();
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

    /** Opens an already-composed panel tree without flattening it into one application-specific panel. */
    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMWorkspaceLayout layout
    ) {
        return new SFMScreenMultiplexer(previousScreen, layout);
    }

    public static SFMScreenMultiplexer create(
            @Nullable Screen previousScreen,
            SFMWorkspacePanelGroup group
    ) {
        SFMScreenPanelBounds initialBounds = new SFMScreenPanelBounds(0, 0, 1, 1);
        return new SFMScreenMultiplexer(previousScreen, SFMWorkspaceLayout.group(group.layout(initialBounds)), group);
    }

    public static void openToSide(@Nullable Screen origin, SFMScreenPanel panel) {
        openToSide(origin, SFMWorkspaceSide.RIGHT, panel);
    }

    public static void openToSide(
            @Nullable Screen origin,
            SFMWorkspaceSide side,
            SFMScreenPanel panel
    ) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.submit(
                    multiplexer.layout.focusedPanel(),
                    new SFMWorkspacePanelIntent.OpenToSide(side, panel)
            );
            return;
        }
        SFMScreenPanel previous = new SFMPreviousScreenPanel(origin);
        SFMScreenPanel first = side == SFMWorkspaceSide.LEFT || side == SFMWorkspaceSide.ABOVE
                ? panel : previous;
        SFMScreenPanel second = side == SFMWorkspaceSide.LEFT || side == SFMWorkspaceSide.ABOVE
                ? previous : panel;
        SFMWorkspaceLayout layout = side.axis() == SFMWorkspaceAxis.HORIZONTAL
                ? SFMWorkspaceLayout.group(SFMWorkspaceLayout.horizontal(
                SFMWorkspaceLayout.panel(first), SFMWorkspaceLayout.panel(second)))
                : SFMWorkspaceLayout.group(SFMWorkspaceLayout.vertical(
                SFMWorkspaceLayout.panel(first), SFMWorkspaceLayout.panel(second)));
        SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(origin, layout));
    }

    public static void openFocused(
            @Nullable Screen origin,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        if (origin instanceof SFMScreenMultiplexer multiplexer) {
            multiplexer.openFocused(panel, metadata);
            return;
        }
        SFMScreenChangeHelpers.setScreen(SFMScreenMultiplexer.create(origin, panel));
    }

    public void openToSide(SFMScreenPanel panel) {
        submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.OpenToSide(SFMWorkspaceSide.RIGHT, panel));
    }

    public SFMWorkspacePanelIntentResult openFocused(
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.OpenAsTab(panel, metadata));
    }

    public SFMWorkspacePanelIntentResult openIntoSlot(
            SFMWorkspacePanelId slot,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        if (layout.panel(slot) == null) return SFMWorkspacePanelIntentResult.UNAVAILABLE;
        return submit(slot, new SFMWorkspacePanelIntent.OpenAsTab(panel, metadata));
    }

    public boolean focusPanel(SFMWorkspacePanelId panelId) {
        boolean focused = layout.focus(panelId);
        if (focused) refreshLayout(true);
        return focused;
    }

    public boolean containsPanel(SFMWorkspacePanelId panelId) {
        return layout.panel(panelId) != null;
    }

    public SFMWorkspacePanelIntentResult closeFocused() {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.Close());
    }

    public SFMWorkspacePanelIntentResult moveFocused(SFMWorkspaceSide side) {
        return submit(layout.focusedPanel(), new SFMWorkspacePanelIntent.Move(side));
    }

    public boolean setFocusedGuiScale(@Nullable Integer scale) {
        boolean changed = layout.setFocusedGuiScale(scale);
        if (changed) refreshLayout(true);
        return changed;
    }

    public boolean rotateVisibleContent(int direction) {
        boolean changed = layout.rotateVisibleContent(direction);
        if (changed) refreshLayout(true);
        return changed;
    }

    public boolean rotateVisibleScale(int direction) {
        boolean changed = layout.rotateVisibleScale(direction);
        if (changed) refreshLayout(true);
        return changed;
    }

    public List<SFMWorkspaceLayout.PanelEntry> visiblePanelEntries() {
        return layout.visiblePanels();
    }

    public List<SFMWorkspaceLayout.PanelEntry> focusedSlotEntries() {
        return layout.focusedSlotEntries();
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
        if (outcome.moved()) {
            refreshLayout(true);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
        if (outcome.inserted() != null) {
            refreshLayout(true);
            return SFMWorkspacePanelIntentResult.APPLIED;
        }
        outcome.removedPanel().closed();
        openedPanels.remove(outcome.removed());
        openedPanelInstances.remove(outcome.removed());
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
        Set<SFMWorkspacePanelId> visibleIds = layout.visiblePanels().stream()
                .map(SFMWorkspaceLayout.PanelEntry::id)
                .collect(java.util.stream.Collectors.toSet());
        for (SFMWorkspacePanelId openedId : List.copyOf(openedPanels)) {
            if (visibleIds.contains(openedId)) continue;
            SFMScreenPanel hidden = openedPanelInstances.remove(openedId);
            if (hidden != null) hidden.closed();
            openedPanels.remove(openedId);
        }
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
            if (openedPanels.contains(entry.id())) {
                SFMScreenPanel opened = openedPanelInstances.get(entry.id());
                if (opened != entry.panel()) {
                    if (opened != null) opened.closed();
                    openPanel(entry.id(), entry.panel());
                } else {
                    entry.panel().resized(this.minecraft, contentBounds(entry));
                }
            } else {
                openPanel(entry.id(), entry.panel());
            }
        }
    }

    private void openPanel(SFMWorkspacePanelId id, SFMScreenPanel panel) {
        SFMWorkspaceLayout.PanelEntry entry = layout.panels().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst().orElseThrow();
        panel.opened(this.minecraft, contentBounds(entry), new SFMWorkspacePanelContext(id, this));
        openedPanels.add(id);
        openedPanelInstances.put(id, panel);
    }

    @Override
    public void tick() {
        if (panelGroup != null && panelGroupRevision != panelGroup.revision()) refreshLayout(true);
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) entry.panel().tick();
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
        openedPanelInstances.clear();
        SFMScreenChangeHelpers.setScreen(previousScreen);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds == null) continue;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), PANEL_BACKGROUND);
            int border = entry.id().equals(layout.focusedPanel()) ? FOCUSED_BORDER : UNFOCUSED_BORDER;
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, border);
            fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1, bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), border);
            fill(poseStack, bounds.x() + bounds.width() - 1, bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), border);
            enableScissor(bounds.inset(1));
            renderPanelEntry(poseStack, entry, bounds.inset(1), mouseX, mouseY, partialTick);
            RenderSystem.disableScissor();
            renderEntryAffordances(poseStack, entry, bounds);
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
            List<SFMWorkspaceLayout.PanelEntry> panels = layout.visiblePanels();
            if (requestedIndex < panels.size()) layout.focus(panels.get(requestedIndex).id());
            return requestedIndex < panels.size();
        }
        if (Screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_TAB) {
            int direction = Screen.hasShiftDown() ? -1 : 1;
            if (layout.traverse(direction)) {
                refreshLayout(true);
                return true;
            }
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
            int[] local = localMouse(entry, mouseX, mouseY);
            entry.panel().mouseClicked(local[0], local[1], button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) {
            int[] local = localMouse(entry, mouseX, mouseY);
            entry.panel().mouseMoved(local[0], local[1]);
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.entry(layout.focusedPanel());
        int[] local = entry == null ? new int[]{0, 0} : localMouse(entry, mouseX, mouseY);
        return focused != null && (focused.mouseReleased(local[0], local[1], button)
                || super.mouseReleased(mouseX, mouseY, button));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        SFMWorkspaceLayout.PanelEntry entry = focused == null ? null : layout.entry(layout.focusedPanel());
        int[] local = entry == null ? new int[]{0, 0} : localMouse(entry, mouseX, mouseY);
        return focused != null && (focused.mouseDragged(local[0], local[1], button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        SFMWorkspaceLayout.PanelEntry entry = panelAt(mouseX, mouseY);
        if (entry != null) layout.focus(entry.id());
        SFMScreenPanel focused = layout.panel(layout.focusedPanel());
        SFMWorkspaceLayout.PanelEntry focusedEntry = focused == null ? null : layout.entry(layout.focusedPanel());
        int[] local = focusedEntry == null ? new int[]{0, 0} : localMouse(focusedEntry, mouseX, mouseY);
        return focused != null && (focused.mouseScrolled(local[0], local[1], delta)
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
        for (SFMWorkspaceLayout.PanelEntry entry : layout.visiblePanels()) {
            SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
            if (bounds != null && bounds.contains(mouseX, mouseY)) return entry;
        }
        return null;
    }

    private SFMScreenPanelBounds contentBounds(SFMWorkspaceLayout.PanelEntry entry) {
        SFMScreenPanelBounds bounds = panelBounds.get(entry.id());
        if (bounds == null) return new SFMScreenPanelBounds(0, 0, 0, 0);
        SFMScreenPanelBounds physical = bounds.inset(1);
        double scale = panelRenderScale(entry);
        return new SFMScreenPanelBounds(
                0,
                0,
                Math.max(1, (int) Math.ceil(physical.width() / scale)),
                Math.max(1, (int) Math.ceil(physical.height() / scale))
        );
    }

    private void renderPanelEntry(
            PoseStack poseStack,
            SFMWorkspaceLayout.PanelEntry entry,
            SFMScreenPanelBounds physicalBounds,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        double scale = panelRenderScale(entry);
        SFMScreenPanelBounds logicalBounds = contentBounds(entry);
        int[] local = localMouse(entry, mouseX, mouseY);
        poseStack.pushPose();
        poseStack.translate(physicalBounds.x(), physicalBounds.y(), 0.0D);
        poseStack.scale((float) scale, (float) scale, 1.0F);
        entry.panel().render(
                poseStack,
                this.minecraft,
                logicalBounds,
                local[0],
                local[1],
                partialTick,
                entry.id().equals(layout.focusedPanel())
        );
        poseStack.popPose();
    }

    private double panelRenderScale(SFMWorkspaceLayout.PanelEntry entry) {
        Integer override = entry.metadata().guiScaleOverride();
        if (override == null || this.minecraft == null) return 1.0D;
        return override / Math.max(1.0D, this.minecraft.getWindow().getGuiScale());
    }

    private int[] localMouse(SFMWorkspaceLayout.PanelEntry entry, double mouseX, double mouseY) {
        SFMScreenPanelBounds physical = panelBounds.get(entry.id());
        if (physical == null) return new int[]{0, 0};
        SFMScreenPanelBounds content = physical.inset(1);
        double scale = panelRenderScale(entry);
        return new int[]{
                (int) Math.floor((mouseX - content.x()) / scale),
                (int) Math.floor((mouseY - content.y()) / scale)
        };
    }

    private void renderEntryAffordances(
            PoseStack poseStack,
            SFMWorkspaceLayout.PanelEntry entry,
            SFMScreenPanelBounds bounds
    ) {
        List<SFMWorkspaceLayout.PanelEntry> slot = layout.slotEntries(entry.id());
        int x = bounds.x() + bounds.width() - 4;
        int y = bounds.y() + bounds.height() - 12;
        if (slot.size() > 1) {
            StringBuilder tabs = new StringBuilder();
            for (int index = 0; index < slot.size(); index++) {
                if (index > 0) tabs.append(' ');
                tabs.append(index + 1);
            }
            SFMFontUtils.draw(poseStack, this.font, tabs.toString(),
                    Math.max(bounds.x() + 2, x - this.font.width(tabs.toString())), y, 0xFFFFFFFF, true);
        }
        if (entry.metadata().guiScaleOverride() != null) {
            String label = "gui scale " + entry.metadata().guiScaleOverride();
            SFMFontUtils.draw(poseStack, this.font, label,
                    Math.max(bounds.x() + 2, bounds.x() + bounds.width() - this.font.width(label) - 4),
                    Math.max(bounds.y() + 2, bounds.y() + bounds.height() - 24), 0xFFFFFFFF, true);
        }
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
