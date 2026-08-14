package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPathProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMFileDropTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Bounded generic explorer presentation hosted through the ordinary panel
 * scene while all semantic mutations cross the supplied action execution seam.
 */
public final class SFMExplorerPanel implements SFMScreenPanel, SFMFileDropTarget, SFMContextContributor {
    private static final String CONTEXT_CONTRIBUTOR_ID = "sfm:explorer";
    private static final int PANEL = 0xF0202020;
    private static final int HEADER = 0xF02A2A2A;
    private static final int BORDER = 0xFF606060;
    private static final int FOCUSED_BORDER = 0xFF55FFFF;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFFAAAAAA;
    private static final int SELECTED = 0xFF264F78;
    private static final int HOVERED = 0xFF303A44;
    private static final int DIAGNOSTIC = 0xFFFF7777;

    private final SFMExplorerSession session;
    private final SFMLazyExplorerLoader loader;
    private final SFMExplorerPanelModel model;
    private final SFMExplorerPresentationRegistry presentationRegistry;
    private final Runnable focusObserver;
    private final Runnable closeObserver;
    private final Consumer<String> clipboardSink;
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private int mouseX;
    private int mouseY;
    private Optional<SFMPath> lastClickPath = Optional.empty();
    private long lastClickTime;
    private boolean wasFocused;
    private boolean closed;
    private KeyboardFocus keyboardFocus = KeyboardFocus.BODY;
    private SFMWorkspacePanelContext panelContext;

    private enum KeyboardFocus {
        LOCATION,
        BODY
    }

    public SFMExplorerPanel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink
    ) {
        this(
                session,
                loader,
                actionSink,
                () -> {},
                () -> {},
                SFMExplorerPresentationRegistry.minecraftDefaults()
        );
    }

    public SFMExplorerPanel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink,
            SFMExplorerPresentationRegistry presentationRegistry
    ) {
        this(session, loader, actionSink, () -> {}, () -> {}, presentationRegistry);
    }

    public SFMExplorerPanel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink,
            Runnable focusObserver,
            Runnable closeObserver
    ) {
        this(
                session,
                loader,
                actionSink,
                focusObserver,
                closeObserver,
                SFMExplorerPresentationRegistry.minecraftDefaults()
        );
    }

    public SFMExplorerPanel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink,
            Runnable focusObserver,
            Runnable closeObserver,
            SFMExplorerPresentationRegistry presentationRegistry
    ) {
        this(
                session,
                loader,
                actionSink,
                focusObserver,
                closeObserver,
                presentationRegistry,
                SFMExplorerPanel::copyToMinecraftClipboard
        );
    }

    SFMExplorerPanel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink,
            Runnable focusObserver,
            Runnable closeObserver,
            SFMExplorerPresentationRegistry presentationRegistry,
            Consumer<String> clipboardSink
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.loader = Objects.requireNonNull(loader, "loader");
        model = new SFMExplorerPanelModel(session, this.loader, actionSink);
        this.focusObserver = Objects.requireNonNull(focusObserver, "focusObserver");
        this.closeObserver = Objects.requireNonNull(closeObserver, "closeObserver");
        this.presentationRegistry = Objects.requireNonNull(presentationRegistry, "presentationRegistry");
        this.clipboardSink = Objects.requireNonNull(clipboardSink, "clipboardSink");
    }

    @Override
    public Component title() {
        return Component.literal(session.snapshot().location().canonical());
    }

    @Override
    public Component narration() {
        SFMExplorerPanelModel.State state = model.state(bounds);
        String selection = state.selectedRow()
                .map(row -> ". Selected " + row.entry().label())
                .orElse("");
        return Component.literal(
                "Explorer location " + state.session().location().canonical() + ". "
                        + state.projection().rows().size() + " entries" + selection
                        + (keyboardFocus == KeyboardFocus.LOCATION ? ". Location control focused" : "")
        );
    }

    @Override
    public String id() {
        return CONTEXT_CONTRIBUTOR_ID;
    }

    @Override
    public Optional<SFMContextOriginId> focusedOriginId() {
        if (panelContext == null || closed) return Optional.empty();
        SFMExplorerSession.Snapshot snapshot = session.snapshot();
        if (snapshot.navigationCursor().isPresent()) return Optional.of(origin("selection"));
        return snapshot.roots().stream().sorted().findFirst().map(root -> origin(rootLocalId(root)));
    }

    @Override
    public List<SFMContextContribution> capture(SFMContextCaptureRequest request) {
        if (panelContext == null || closed) return List.of();
        SFMExplorerSession.Snapshot snapshot = session.snapshot();
        long relationGeneration = loader.relationSnapshot().relation().id();
        SFMContextGenerationEvidence evidence = new SFMContextGenerationEvidence(
                snapshot.revision(),
                snapshot.revision(),
                snapshot.revision(),
                relationGeneration
        );
        java.util.ArrayList<SFMContextContribution> result = new java.util.ArrayList<>();
        List<SFMPath> roots = snapshot.roots().stream().sorted().toList();
        for (SFMPath root : roots) {
            result.add(new SFMContextContribution(
                    origin(rootLocalId(root)),
                    evidence,
                    new SFMContextPathProjection(root, Optional.of(root), "explorer-root")
            ));
        }
        snapshot.navigationCursor().ifPresent(path -> result.add(new SFMContextContribution(
                origin("selection"),
                evidence,
                new SFMContextPathProjection(path, authorizedRoot(path, roots), "explorer-selection")
        )));
        return List.copyOf(result);
    }

    @Override
    public void opened(
            Minecraft minecraft,
            SFMScreenPanelBounds bounds,
            SFMWorkspacePanelContext context
    ) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        this.panelContext = Objects.requireNonNull(context, "context");
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        this.bounds = Objects.requireNonNull(bounds, "bounds");
        model.state(bounds);
    }

    @Override
    public void closed() {
        if (closed) return;
        closed = true;
        panelContext = null;
        session.close();
        closeObserver.run();
    }

    private SFMContextOriginId origin(String localId) {
        return new SFMContextOriginId(
                CONTEXT_CONTRIBUTOR_ID,
                "panel-" + panelContext.panelId().value(),
                localId
        );
    }

    private static String rootLocalId(SFMPath root) {
        return "root-" + UUID.nameUUIDFromBytes(root.canonical().getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<SFMPath> authorizedRoot(SFMPath path, List<SFMPath> roots) {
        return roots.stream()
                .filter(root -> contains(root, path))
                .max(java.util.Comparator.comparingInt(root -> root.segments().size()));
    }

    private static boolean contains(SFMPath root, SFMPath candidate) {
        if (root.kind() != candidate.kind()
                || !root.scheme().equals(candidate.scheme())
                || !root.authority().equals(candidate.authority())
                || root.segments().size() > candidate.segments().size()) return false;
        return candidate.segments().subList(0, root.segments().size()).equals(root.segments());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (keyCode == GLFW.GLFW_KEY_TAB && !control) {
            keyboardFocus = keyboardFocus == KeyboardFocus.LOCATION
                    ? KeyboardFocus.BODY
                    : KeyboardFocus.LOCATION;
            return true;
        }
        if (keyboardFocus == KeyboardFocus.LOCATION) {
            if (control && keyCode == GLFW.GLFW_KEY_C) {
                clipboardSink.accept(session.snapshot().location().canonical());
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_SPACE
                    || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                model.emitLocationEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_END) {
                keyboardFocus = KeyboardFocus.BODY;
                return true;
            }
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> model.moveSelection(-1, bounds);
            case GLFW.GLFW_KEY_DOWN -> model.moveSelection(1, bounds);
            case GLFW.GLFW_KEY_HOME -> model.selectFirst(bounds);
            case GLFW.GLFW_KEY_END -> model.selectLast(bounds);
            case GLFW.GLFW_KEY_RIGHT -> model.emitExpandSelected(bounds);
            case GLFW.GLFW_KEY_LEFT -> model.emitCollapseSelected(bounds);
            case GLFW.GLFW_KEY_SPACE -> activateSelected(SFMExplorerPreviewPlacement.Mode.PREVIEW);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateSelected(
                    control
                            ? SFMExplorerPreviewPlacement.Mode.ADJACENT
                            : SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW
            );
            case GLFW.GLFW_KEY_F5 -> model.emitRefreshSelected(bounds);
            case GLFW.GLFW_KEY_R -> {
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) return false;
                model.emitRefreshSelected(bounds);
            }
            case GLFW.GLFW_KEY_G -> {
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) return false;
                toggleView();
            }
            case GLFW.GLFW_KEY_L -> {
                if (!control) return false;
                keyboardFocus = KeyboardFocus.LOCATION;
                model.emitLocationEdit();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        SFMExplorerPanelModel.State state = model.state(bounds);
        if (state.viewport().layout().locationControl().contains(mouseX, mouseY)) {
            keyboardFocus = KeyboardFocus.LOCATION;
            model.emitLocationEdit();
            return true;
        }
        Optional<SFMExplorerPanelViewport.Cell> hit = state.viewport().hit(mouseX, mouseY);
        if (hit.isEmpty()) return false;
        keyboardFocus = KeyboardFocus.BODY;
        SFMExplorerPanelViewport.Cell cell = hit.orElseThrow();
        model.select(cell.row().path(), bounds);
        if (cell.chevron().contains(mouseX, mouseY) && cell.row().entry().expandable()) {
            model.emitToggleSelected(bounds);
            rememberClick(cell.row().path());
            return true;
        }
        long now = Util.getMillis();
        if (lastClickPath.equals(Optional.of(cell.row().path())) && now - lastClickTime <= 300L) {
            if (cell.row().entry().expandable()) model.emitToggleSelected(bounds);
            else model.emitOpenSelected(bounds, SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW);
        }
        lastClickPath = Optional.of(cell.row().path());
        lastClickTime = now;
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        SFMExplorerPanelModel.State state = model.state(bounds);
        if (!state.viewport().layout().body().contains(mouseX, mouseY)) return false;
        model.scrollRows(delta > 0 ? -1 : 1, bounds);
        return true;
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        model.emitDroppedRoots(paths);
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
        if (focused && !wasFocused) focusObserver.run();
        wasFocused = focused;
        this.bounds = bounds;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        SFMExplorerPanelModel.State state = model.state(bounds);
        SFMExplorerPanelViewport.Layout layout = state.viewport().layout();
        fill(poseStack, layout.content(), PANEL);
        fill(poseStack, layout.header(), HEADER);
        border(poseStack, layout.content(), focused ? FOCUSED_BORDER : BORDER);
        renderHeader(poseStack, minecraft, state);
        for (SFMExplorerPanelViewport.Cell cell : state.viewport().cells()) {
            renderCell(poseStack, minecraft, state, cell);
        }
        renderStatus(poseStack, minecraft, state);
        if (state.viewport().layout().locationControl().contains(mouseX, mouseY)
                && minecraft.screen != null) {
            minecraft.screen.renderComponentTooltip(
                    poseStack,
                    List.of(Component.literal(state.session().location().canonical())),
                    mouseX,
                    mouseY
            );
        }
    }

    public SFMExplorerPanelModel model() {
        return model;
    }

    public ca.teamdman.sfm.client.explorer.SFMExplorerId explorerId() {
        return session.snapshot().id();
    }

    public SFMExplorerSession.Snapshot sessionSnapshot() {
        return session.snapshot();
    }

    private void activateSelected(SFMExplorerPreviewPlacement.Mode mode) {
        SFMExplorerPanelModel.State state = model.state(bounds);
        if (state.selectedRow().map(row -> row.entry().expandable()).orElse(false)) {
            model.emitToggleSelected(bounds);
        } else {
            model.emitOpenSelected(bounds, mode);
        }
    }

    private void toggleView() {
        SFMExplorerProjection.View current = model.state(bounds).projection().settings().view();
        model.emitViewSet(current == SFMExplorerProjection.View.LIST
                ? SFMExplorerProjection.View.SMALL_ICONS
                : SFMExplorerProjection.View.LIST);
    }

    private void renderHeader(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state
    ) {
        SFMExplorerPanelViewport.Layout layout = state.viewport().layout();
        int textY = layout.header().y() + Math.max(1, (layout.header().height() - minecraft.font.lineHeight) / 2);
        fill(poseStack, layout.locationControl(), 0xF0353535);
        border(
                poseStack,
                layout.locationControl(),
                keyboardFocus == KeyboardFocus.LOCATION ? FOCUSED_BORDER : BORDER
        );
        drawTrimmed(
                poseStack,
                minecraft,
                state.session().location().canonical(),
                layout.locationControl().x() + 6,
                textY,
                Math.max(0, layout.locationControl().width() - 12),
                TEXT
        );
    }

    boolean locationControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.LOCATION;
    }

    private static void copyToMinecraftClipboard(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.keyboardHandler != null) {
            minecraft.keyboardHandler.setClipboard(text);
        }
    }

    private void renderCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state,
            SFMExplorerPanelViewport.Cell cell
    ) {
        boolean selected = state.selectedPath().equals(Optional.of(cell.row().path()));
        boolean hovered = cell.bounds().contains(mouseX, mouseY);
        SFMExplorerPresentation presentation = presentationRegistry.resolve(cell.row()).presentation();
        if (selected) fill(poseStack, cell.bounds(), SELECTED);
        else if (hovered) fill(poseStack, cell.bounds(), HOVERED);
        if (state.viewport().view() == SFMExplorerProjection.View.SMALL_ICONS) {
            border(poseStack, cell.bounds(), 0xFF3A3A3A);
            renderSmallIconCell(poseStack, minecraft, cell, presentation);
        } else {
            renderListCell(poseStack, minecraft, cell, presentation);
        }
    }

    private void renderListCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelViewport.Cell cell,
            SFMExplorerPresentation presentation
    ) {
        int baseX = cell.chevron().x();
        int textY = cell.bounds().y() + Math.max(1, (cell.bounds().height() - minecraft.font.lineHeight) / 2);
        if (cell.row().entry().expandable()) {
            SFMFontUtils.draw(
                    poseStack,
                    minecraft.font,
                    cell.row().expanded() ? "v" : ">",
                    baseX + 2,
                    textY,
                    MUTED,
                    true
            );
        }
        int iconX = baseX + 13;
        presentation.icon().render(
                poseStack,
                minecraft,
                iconX,
                cell.bounds().y() + 1,
                Math.max(0, cell.bounds().height() - 2),
                MUTED
        );
        int labelX = iconX + 21;
        drawTrimmed(
                poseStack,
                minecraft,
                presentation.label(),
                labelX,
                textY,
                Math.max(0, cell.bounds().x() + cell.bounds().width() - labelX - 4),
                TEXT
        );
    }

    private void renderSmallIconCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelViewport.Cell cell,
            SFMExplorerPresentation presentation
    ) {
        int iconX = cell.bounds().x() + 4;
        int iconY = cell.bounds().y() + 3;
        presentation.icon().render(
                poseStack,
                minecraft,
                iconX,
                iconY,
                Math.max(0, cell.bounds().height() - 6),
                MUTED
        );
        int labelX = cell.bounds().x() + 25;
        int width = Math.max(0, cell.bounds().width() - 29);
        drawTrimmed(poseStack, minecraft, presentation.label(), labelX, cell.bounds().y() + 5, width, TEXT);
        drawTrimmed(
                poseStack,
                minecraft,
                cell.row().path().canonical(),
                labelX,
                cell.bounds().y() + 19,
                width,
                MUTED
        );
        if (cell.row().entry().expandable()) {
            SFMFontUtils.draw(
                    poseStack,
                    minecraft.font,
                    cell.row().expanded() ? "v" : ">",
                    cell.chevron().x() + 2,
                    cell.chevron().y() + 1,
                    MUTED,
                    true
            );
        }
    }

    private void renderStatus(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state
    ) {
        SFMExplorerPanelViewport.Rect status = state.viewport().layout().status();
        fill(poseStack, status, HEADER);
        String summary = state.viewport().cells().size() + " visible / "
                + state.viewport().totalItems() + " entries; relation r"
                + state.projection().relationRevision();
        int colour = MUTED;
        if (!state.projection().diagnostics().isEmpty()) {
            summary += "; " + state.projection().diagnostics().get(0);
            colour = DIAGNOSTIC;
        }
        drawTrimmed(
                poseStack,
                minecraft,
                summary,
                status.x() + 4,
                status.y() + 2,
                Math.max(0, status.width() - 8),
                colour
        );
    }

    private void rememberClick(SFMPath path) {
        lastClickPath = Optional.of(path);
        lastClickTime = Util.getMillis();
    }

    private static void fill(PoseStack poseStack, SFMExplorerPanelViewport.Rect rect, int colour) {
        GuiComponent.fill(
                poseStack,
                rect.x(),
                rect.y(),
                rect.x() + rect.width(),
                rect.y() + rect.height(),
                colour
        );
    }

    private static void border(PoseStack poseStack, SFMExplorerPanelViewport.Rect rect, int colour) {
        if (rect.width() <= 0 || rect.height() <= 0) return;
        int right = rect.x() + rect.width();
        int bottom = rect.y() + rect.height();
        GuiComponent.fill(poseStack, rect.x(), rect.y(), right, rect.y() + 1, colour);
        GuiComponent.fill(poseStack, rect.x(), bottom - 1, right, bottom, colour);
        GuiComponent.fill(poseStack, rect.x(), rect.y(), rect.x() + 1, bottom, colour);
        GuiComponent.fill(poseStack, right - 1, rect.y(), right, bottom, colour);
    }

    private static void drawTrimmed(
            PoseStack poseStack,
            Minecraft minecraft,
            String text,
            int x,
            int y,
            int width,
            int colour
    ) {
        if (width <= 0) return;
        String rendered = text;
        if (minecraft.font.width(rendered) > width) {
            int ellipsis = minecraft.font.width("...");
            rendered = minecraft.font.plainSubstrByWidth(rendered, Math.max(0, width - ellipsis)) + "...";
        }
        SFMFontUtils.draw(poseStack, minecraft.font, rendered, x, y, colour, true);
    }
}
