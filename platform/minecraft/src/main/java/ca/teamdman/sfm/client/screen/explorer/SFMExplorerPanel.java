package ca.teamdman.sfm.client.screen.explorer;

import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPathProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerPathReveal;
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
import java.util.concurrent.CompletionStage;
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
    private static final int CONTEXT_TEXT = 0xFF91A5AE;
    private static final int CONTEXT_ACCENT = 0xFF526C78;
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
    private String filterDraft;
    private SFMWorkspacePanelContext panelContext;
    private volatile Optional<SFMPath> pendingRevealSelection = Optional.empty();

    private enum KeyboardFocus {
        LOCATION,
        FILTER,
        BODY
    }

    public record FocusChrome(boolean location, boolean filter, boolean body) {
    }

    record FilterRowPresentation(String label, int textColour, String narration) {
    }

    static FilterRowPresentation filterRowPresentation(
            SFMExplorerProjection.Row row,
            String label
    ) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(label, "label");
        return switch (row.filterRole()) {
            case NONE -> new FilterRowPresentation(label, TEXT, "");
            case MATCH -> new FilterRowPresentation(label, TEXT, "filter match");
            case CONTEXT_ANCESTOR -> new FilterRowPresentation(
                    "[context] " + label,
                    CONTEXT_TEXT,
                    "context ancestor included to locate a filter match"
            );
        };
    }

    static String filterSummary(SFMExplorerProjection.FilterEvidence filter) {
        Objects.requireNonNull(filter, "filter");
        return countLabel(filter.matchCount(), "match", "matches") + " + "
                + countLabel(filter.contextAncestorCount(), "context ancestor", "context ancestors")
                + " = " + filter.visibleRowCount() + " visible / "
                + filter.candidateCount() + " materialized entries"
                + (filter.incompleteMaterialization()
                ? "; unmaterialized subtrees excluded"
                : "; materialization complete");
    }

    static String filterNarration(SFMExplorerProjection.FilterEvidence filter) {
        Objects.requireNonNull(filter, "filter");
        return "Filter showing " + countLabel(filter.matchCount(), "match", "matches")
                + " and "
                + countLabel(filter.contextAncestorCount(), "context ancestor", "context ancestors")
                + ", " + countLabel(filter.visibleRowCount(), "visible row", "visible rows")
                + " from " + filter.candidateCount() + " materialized entries. "
                + (filter.incompleteMaterialization()
                ? "Unmaterialized subtrees are excluded"
                : "Materialization is complete");
    }

    private static String countLabel(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
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
        filterDraft = session.snapshot().settings().filterQuery();
    }

    @Override
    public Component title() {
        return Component.literal(session.snapshot().location().canonical());
    }

    @Override
    public Component narration() {
        SFMExplorerPanelModel.State state = model.state(bounds);
        String selection = state.selectedRow()
                .map(row -> {
                    SFMExplorerPresentation presentation = presentation(state, row);
                    FilterRowPresentation filterPresentation = filterRowPresentation(row, presentation.label());
                    String filterRole = filterPresentation.narration().isEmpty()
                            ? ""
                            : ", " + filterPresentation.narration();
                    return ". Selected " + presentation.label() + ", "
                            + iconNarration(presentation.icon()) + filterRole;
                })
                .orElse("");
        String filter = state.projection().filter().active()
                ? ". " + filterNarration(state.projection().filter())
                : "";
        return Component.literal(
                "Explorer location " + state.session().location().canonical() + ". "
                        + state.projection().rows().size() + " entries" + filter + selection
                        + (keyboardFocus == KeyboardFocus.LOCATION ? ". Location control focused" : "")
                        + (keyboardFocus == KeyboardFocus.FILTER
                        ? ". Filter control focused. Current filter "
                        + (filterDraft.isEmpty() ? "empty" : filterDraft)
                        : "")
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
            cycleKeyboardFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        if (control && keyCode == GLFW.GLFW_KEY_F) {
            focusFilter();
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
        if (keyboardFocus == KeyboardFocus.FILTER) {
            if (control && keyCode == GLFW.GLFW_KEY_C) {
                clipboardSink.accept(filterDraft);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!filterDraft.isEmpty()) setFilterDraft(
                        filterDraft.substring(0, filterDraft.offsetByCodePoints(filterDraft.length(), -1))
                );
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                setFilterDraft("");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (filterDraft.isEmpty()) keyboardFocus = KeyboardFocus.BODY;
                else setFilterDraft("");
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER
                    || keyCode == GLFW.GLFW_KEY_DOWN) {
                keyboardFocus = KeyboardFocus.BODY;
                return true;
            }
            return false;
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
    public boolean charTyped(char character, int modifiers) {
        int commandModifiers = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
        if (keyboardFocus != KeyboardFocus.FILTER
                || Character.isISOControl(character)
                || (modifiers & commandModifiers) != 0) return false;
        setFilterDraft(filterDraft + character);
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
        if (state.viewport().layout().filterControl().contains(mouseX, mouseY)) {
            focusFilter();
            return true;
        }
        Optional<SFMExplorerPanelViewport.Cell> hit = state.viewport().hit(mouseX, mouseY);
        if (hit.isEmpty()) {
            if (state.viewport().layout().bodyFrame().contains(mouseX, mouseY)) {
                keyboardFocus = KeyboardFocus.BODY;
                return true;
            }
            return false;
        }
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
        if (delta == 0) return false;
        SFMExplorerPanelViewport.Layout layout = SFMExplorerPanelViewport.layout(bounds);
        if (!layout.bodyFrame().contains(mouseX, mouseY)) return false;
        int magnitude = Math.max(1, (int) Math.ceil(Math.abs(delta)));
        model.scrollRows(delta > 0 ? -magnitude : magnitude, bounds);
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
        pendingRevealSelection.ifPresent(path -> model.select(path, bounds));
        SFMExplorerPanelModel.State state = model.state(bounds);
        if (pendingRevealSelection.isPresent()
                && state.selectedRow().map(row -> row.path().equals(pendingRevealSelection.orElseThrow())).orElse(false)) {
            pendingRevealSelection = Optional.empty();
        }
        if (keyboardFocus != KeyboardFocus.FILTER) {
            filterDraft = state.session().settings().filterQuery();
        }
        SFMExplorerPanelViewport.Layout layout = state.viewport().layout();
        FocusChrome chrome = focusChrome(focused);
        fill(poseStack, layout.content(), PANEL);
        fill(poseStack, layout.header(), HEADER);
        fill(poseStack, layout.filter(), HEADER);
        fill(poseStack, layout.bodyFrame(), PANEL);
        border(poseStack, layout.content(), BORDER);
        renderHeader(poseStack, minecraft, state, chrome.location());
        renderFilter(poseStack, minecraft, state, chrome.filter());
        for (SFMExplorerPanelViewport.Cell cell : state.viewport().cells()) {
            renderCell(poseStack, minecraft, state, cell);
        }
        renderStatus(poseStack, minecraft, state);
        border(poseStack, layout.bodyFrame(), chrome.body() ? FOCUSED_BORDER : BORDER);
        model.observeVisibleFrame(state.viewport().scrollRow());
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

    /**
     * Materializes every missing ancestor page and queues exact row selection
     * against the next real panel bounds, so reveal never guesses scroll geometry.
     */
    public CompletionStage<SFMExplorerPathReveal.Result> revealPath(SFMPath containingRoot, SFMPath path) {
        return SFMExplorerPathReveal.reveal(
                session,
                loader,
                containingRoot,
                path,
                128,
                () -> pendingRevealSelection = Optional.of(path)
        );
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
            SFMExplorerPanelModel.State state,
            boolean focused
    ) {
        SFMExplorerPanelViewport.Layout layout = state.viewport().layout();
        int textY = layout.header().y() + Math.max(1, (layout.header().height() - minecraft.font.lineHeight) / 2);
        fill(poseStack, layout.locationControl(), 0xF0353535);
        border(
                poseStack,
                layout.locationControl(),
                focused ? FOCUSED_BORDER : BORDER
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

    boolean filterControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.FILTER;
    }

    boolean bodyControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.BODY;
    }

    public FocusChrome focusChrome(boolean panelFocused) {
        return new FocusChrome(
                panelFocused && keyboardFocus == KeyboardFocus.LOCATION,
                panelFocused && keyboardFocus == KeyboardFocus.FILTER,
                panelFocused && keyboardFocus == KeyboardFocus.BODY
        );
    }

    private static void copyToMinecraftClipboard(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.keyboardHandler != null) {
            minecraft.keyboardHandler.setClipboard(text);
        }
    }

    private void renderFilter(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state,
            boolean focused
    ) {
        SFMExplorerPanelViewport.Rect control = state.viewport().layout().filterControl();
        fill(poseStack, control, 0xF02F2F2F);
        border(poseStack, control, focused ? FOCUSED_BORDER : BORDER);
        String visibleQuery = keyboardFocus == KeyboardFocus.FILTER
                ? filterDraft
                : state.session().settings().filterQuery();
        String text = visibleQuery.isEmpty()
                ? "Filter current materialization (Ctrl+F)"
                : "Filter: " + visibleQuery + (focused ? "_" : "");
        int textY = control.y() + Math.max(1, (control.height() - minecraft.font.lineHeight) / 2);
        drawTrimmed(
                poseStack,
                minecraft,
                text,
                control.x() + 4,
                textY,
                Math.max(0, control.width() - 8),
                visibleQuery.isEmpty() ? MUTED : TEXT
        );
    }

    private void renderCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state,
            SFMExplorerPanelViewport.Cell cell
    ) {
        boolean selected = state.selectedPath().equals(Optional.of(cell.row().path()));
        boolean hovered = cell.bounds().contains(mouseX, mouseY);
        SFMExplorerPresentation presentation = presentation(state, cell.row());
        FilterRowPresentation filterPresentation = filterRowPresentation(cell.row(), presentation.label());
        if (selected) fill(poseStack, cell.bounds(), SELECTED);
        else if (hovered) fill(poseStack, cell.bounds(), HOVERED);
        if (cell.row().filterContextAncestor()) {
            fill(
                    poseStack,
                    new SFMExplorerPanelViewport.Rect(
                            cell.bounds().x(),
                            cell.bounds().y(),
                            Math.min(2, cell.bounds().width()),
                            cell.bounds().height()
                    ),
                    CONTEXT_ACCENT
            );
        }
        if (state.viewport().view() == SFMExplorerProjection.View.SMALL_ICONS) {
            border(poseStack, cell.bounds(), 0xFF3A3A3A);
            renderSmallIconCell(poseStack, minecraft, state, cell, presentation, filterPresentation);
        } else {
            renderListCell(poseStack, minecraft, cell, presentation, filterPresentation);
        }
    }

    private void renderListCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelViewport.Cell cell,
            SFMExplorerPresentation presentation,
            FilterRowPresentation filterPresentation
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
                filterPresentation.label(),
                labelX,
                textY,
                Math.max(0, cell.bounds().x() + cell.bounds().width() - labelX - 4),
                filterPresentation.textColour()
        );
    }

    private void renderSmallIconCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state,
            SFMExplorerPanelViewport.Cell cell,
            SFMExplorerPresentation presentation,
            FilterRowPresentation filterPresentation
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
        drawTrimmed(
                poseStack,
                minecraft,
                filterPresentation.label(),
                labelX,
                cell.bounds().y() + 5,
                width,
                filterPresentation.textColour()
        );
        drawTrimmed(
                poseStack,
                minecraft,
                stateSecondaryLabel(cell.row(), state),
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
        if (state.projection().filter().active()) {
            summary = filterSummary(state.projection().filter());
        } else if (!state.projection().diagnostics().isEmpty()) {
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

    private SFMExplorerPresentation presentation(
            SFMExplorerPanelModel.State state,
            SFMExplorerProjection.Row row
    ) {
        SFMExplorerPresentation resolved = presentationRegistry.resolve(row).presentation();
        return new SFMExplorerPresentation(
                SFMExplorerPathLabeler.label(row, state.session()),
                resolved.icon()
        );
    }

    private static String stateSecondaryLabel(
            SFMExplorerProjection.Row row,
            SFMExplorerPanelModel.State state
    ) {
        return state.session().settings().pathDisplay() == SFMExplorerProjection.PathDisplay.NAME
                ? row.path().canonical()
                : row.entry().label();
    }

    private static String iconNarration(SFMExplorerPresentation.Icon icon) {
        if (icon instanceof SFMExplorerPresentation.ItemIcon item) {
            return item.item().accessibleLabel() + " ItemStack icon";
        }
        if (icon instanceof SFMExplorerPresentation.MarkerIcon marker) {
            return marker.marker() + " marker icon";
        }
        throw new AssertionError("Unhandled explorer icon " + icon);
    }

    private void cycleKeyboardFocus(boolean reverse) {
        keyboardFocus = reverse
                ? switch (keyboardFocus) {
                    case BODY -> KeyboardFocus.FILTER;
                    case FILTER -> KeyboardFocus.LOCATION;
                    case LOCATION -> KeyboardFocus.BODY;
                }
                : switch (keyboardFocus) {
                    case BODY -> KeyboardFocus.LOCATION;
                    case LOCATION -> KeyboardFocus.FILTER;
                    case FILTER -> KeyboardFocus.BODY;
                };
        if (keyboardFocus == KeyboardFocus.FILTER) {
            filterDraft = session.snapshot().settings().filterQuery();
        }
    }

    private void focusFilter() {
        keyboardFocus = KeyboardFocus.FILTER;
        filterDraft = session.snapshot().settings().filterQuery();
    }

    private void setFilterDraft(String query) {
        filterDraft = Objects.requireNonNull(query, "query");
        if (filterDraft.isEmpty()) model.emitFilterClear();
        else model.emitFilterSet(filterDraft);
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
