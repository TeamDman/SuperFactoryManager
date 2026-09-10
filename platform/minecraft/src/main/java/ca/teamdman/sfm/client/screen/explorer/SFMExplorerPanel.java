package ca.teamdman.sfm.client.screen.explorer;

import static ca.teamdman.sfm.client.search.SFMExplorerSearchText.*;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMExplorerActions;
import ca.teamdman.sfm.client.action.SFMExplorerSearchAction;
import ca.teamdman.sfm.client.search.SFMTextMatchOptions;
import ca.teamdman.sfm.client.action.SFMRevealHereAction;
import ca.teamdman.sfm.client.action.SFMReviewLensSetAction;
import ca.teamdman.sfm.client.action.SFMReviewWorkQueueControls;
import ca.teamdman.sfm.client.context.SFMContextCaptureRequest;
import ca.teamdman.sfm.client.context.SFMContextContribution;
import ca.teamdman.sfm.client.context.SFMContextContributor;
import ca.teamdman.sfm.client.context.SFMContextGenerationEvidence;
import ca.teamdman.sfm.client.context.SFMContextOriginId;
import ca.teamdman.sfm.client.context.SFMContextPathProjection;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.input.SFMSingleLineInput;
import ca.teamdman.sfm.client.input.SFMSingleLineInputView;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerPathReveal;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.workspace.SFMFileDropTarget;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelTooltip;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
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
    private static final int SCROLLBAR_TRACK = 0xFF181818;
    private static final int SCROLLBAR_THUMB = 0xFF7A7A7A;
    private static final int SCROLLBAR_THUMB_HOVERED = 0xFFA0A0A0;
    private static final SFMItemIcon REVEAL_ICON = SFMItemIcon.vanilla(
            "target", "Reveal the most recently focused document in this Explorer");

    private final SFMExplorerSession session;
    private final SFMLazyExplorerLoader loader;
    private final SFMExplorerPanelModel model;
    private final SFMExplorerPanelFind finder;
    private final SFMExplorerPresentationRegistry presentationRegistry;
    private final Runnable focusObserver;
    private final Runnable closeObserver;
    private final Consumer<String> clipboardSink;
    private final Runnable revealActivationSound;
    private final SFMExplorerContextActionRegistry contextActions =
            SFMExplorerContextActionRegistry.minecraftDefaults();
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private int mouseX;
    private int mouseY;
    private Optional<SFMPath> lastClickPath = Optional.empty();
    private long lastClickTime;
    private boolean wasFocused;
    private boolean closed;
    private final long panelCreatedNanos = System.nanoTime();
    private boolean firstEntriesRendered;
    private KeyboardFocus keyboardFocus = KeyboardFocus.BODY;
    private String filterDraft;
    private final SFMSingleLineInput filterInput = new SFMSingleLineInput("");
    private final SFMSingleLineInputView filterInputView = new SFMSingleLineInputView();
    private boolean filterInputDragging;
    private final SFMSingleLineInput findInput = new SFMSingleLineInput("");
    private final SFMSingleLineInputView findInputView = new SFMSingleLineInputView();
    private boolean findHighlight = true;
    private boolean filterHighlight = true;
    private SFMWorkspacePanelContext panelContext;
    private volatile Optional<SFMPath> pendingRevealSelection = Optional.empty();
    private boolean scrollbarDragging;
    private int scrollbarGrabOffset;

    private enum KeyboardFocus {
        LOCATION,
        LENS,
        REVEAL,
        FILTER,
        FIND,
        WORK_PREVIOUS,
        WORK_NEXT,
        WORK_DEFER,
        WORK_RESUME,
        WORK_SHOW,
        BODY
    }

    public record FocusChrome(boolean location, boolean lens, boolean reveal, boolean filter, boolean body) {
    }

    record FilterRowPresentation(String label, int textColour, String narration) {
    }

    record ScrollbarGeometry(
            SFMExplorerPanelViewport.Rect track,
            SFMExplorerPanelViewport.Rect thumb
    ) {
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
            case CONTEXT_DESCENDANT -> new FilterRowPresentation(
                    label,
                    CONTEXT_TEXT,
                    "context child shown because its matching parent is expanded"
            );
        };
    }

    static String filterSummary(SFMExplorerProjection.FilterEvidence filter) {
        Objects.requireNonNull(filter, "filter");
        return countLabel(filter.matchCount(), "match", "matches") + " + "
                + countLabel(filter.contextAncestorCount(), "context ancestor", "context ancestors")
                + " + "
                + countLabel(filter.contextDescendantCount(), "context child", "context children")
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
                + " and "
                + countLabel(filter.contextDescendantCount(), "context child", "context children")
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
        this(
                session,
                loader,
                actionSink,
                focusObserver,
                closeObserver,
                presentationRegistry,
                clipboardSink,
                SFMExplorerPanel::playButtonActivationSound
        );
    }

    SFMExplorerPanel(
            SFMExplorerSession session,
            SFMLazyExplorerLoader loader,
            SFMExplorerSemanticActionSink actionSink,
            Runnable focusObserver,
            Runnable closeObserver,
            SFMExplorerPresentationRegistry presentationRegistry,
            Consumer<String> clipboardSink,
            Runnable revealActivationSound
    ) {
        this.session = Objects.requireNonNull(session, "session");
        this.loader = Objects.requireNonNull(loader, "loader");
        model = new SFMExplorerPanelModel(session, this.loader, actionSink);
        model.setToolbarHeight(this::reviewToolbarHeight);
        model.setFindVisible(true);
        finder = new SFMExplorerPanelFind(session, loader, model);
        this.focusObserver = Objects.requireNonNull(focusObserver, "focusObserver");
        this.closeObserver = Objects.requireNonNull(closeObserver, "closeObserver");
        this.presentationRegistry = Objects.requireNonNull(presentationRegistry, "presentationRegistry");
        this.clipboardSink = Objects.requireNonNull(clipboardSink, "clipboardSink");
        this.revealActivationSound = Objects.requireNonNull(revealActivationSound, "revealActivationSound");
        filterDraft = session.snapshot().settings().filterQuery();
        filterInput.setText(filterDraft);
    }

    @Override
    public Component title() {
        return Component.literal(session.snapshot().location().canonical());
    }

    @Override public net.minecraft.resources.ResourceLocation keyboardUsageSituationId() {
        return keyboardFocus == KeyboardFocus.FIND ? ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations.EXPLORER_FIND
                : keyboardFocus == KeyboardFocus.FILTER ? ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations.EXPLORER_FILTER
                : ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations.EXPLORER;
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
                    return ". Primary " + presentation.label() + ", "
                            + iconNarration(presentation.icon()) + filterRole;
                })
                .orElse("");
        String filter = state.projection().filter().active()
                ? ". " + (state.projection().rows().stream().anyMatch(row -> row.segments().size() > 1)
                    ? filterNarration(state.projection().filter()).replace("visible rows", "logical entries")
                        + ". " + state.projection().rows().size() + " rendered rows"
                    : filterNarration(state.projection().filter()))
                : "";
        return Component.literal(
                "Explorer location " + state.session().location().canonical() + ". "
                        + state.projection().rows().size() + " entries" + filter + selection
                        + ". " + state.selectedPaths().size() + " selected paths (including hidden rows)"
                        + ". Find " + finder.query() + ". " + finder.status()
                        + (keyboardFocus == KeyboardFocus.FIND ? ". Find control focused" : "")
                        + (keyboardFocus == KeyboardFocus.LOCATION ? ". Location control focused" : "")
                        + (keyboardFocus == KeyboardFocus.LENS ? ". Review lens control focused" : "")
                        + (keyboardFocus == KeyboardFocus.REVEAL ? ". Reveal control focused" : "")
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
        scrollbarDragging = false;
        panelContext = null;
        finder.userNavigated();
        session.close();
        closeObserver.run();
    }

    @Override
    public void tick() {
        if (closed) return;
        // Start the filter lane before Find can consume its query result. A cached Find
        // must not intersect an as-yet-unrequested, partial materialized filter projection.
        SFMExplorerSession.Snapshot snapshot = session.snapshot();
        if (snapshot.settings().filterActive()) snapshot.roots().forEach(root -> loader.ensureFilterDomain(
                root, snapshot.settings().filterQuery(), snapshot.settings().filterOptions()));
        finder.tick(bounds);
        reviewLensDescriptor().ifPresent(lens -> {
            var review = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.get().snapshot();
            if (review.openEpoch() == lens.reviewOpenEpoch() && review.path().filter(lens.reviewPath()::equals).isPresent()) {
                ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewFreshnessRuntime.get().ensure(review, false);
            }
        });
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
        normalizeKeyboardFocus();
        boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        if (keyCode == GLFW.GLFW_KEY_TAB && !control) {
            cycleKeyboardFocus((modifiers & GLFW.GLFW_MOD_SHIFT) != 0);
            return true;
        }
        int workFocus = workFocusIndex();
        if (workFocus >= 0) {
            if (keyCode == GLFW.GLFW_KEY_SPACE || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                invokeWorkControl(workFocus);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                int next = Math.floorMod(workFocus + (keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1), 5);
                keyboardFocus = KeyboardFocus.values()[KeyboardFocus.WORK_PREVIOUS.ordinal() + next];
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_ESCAPE) {
                keyboardFocus = KeyboardFocus.BODY;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                focusFilter();
                return true;
            }
            return false;
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
            if (keyCode == GLFW.GLFW_KEY_RIGHT && lensControlVisible()) {
                keyboardFocus = KeyboardFocus.LENS;
                return true;
            }
        }
        if (keyboardFocus == KeyboardFocus.LENS) {
            if (keyCode == GLFW.GLFW_KEY_SPACE
                    || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                invokeReviewLensChoice();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                keyboardFocus = KeyboardFocus.LOCATION;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
                keyboardFocus = revealControlVisible() ? KeyboardFocus.REVEAL : KeyboardFocus.FILTER;
                return true;
            }
        }
        if (keyboardFocus == KeyboardFocus.REVEAL) {
            if (keyCode == GLFW.GLFW_KEY_SPACE
                    || keyCode == GLFW.GLFW_KEY_ENTER
                    || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                invokeRevealHere();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                keyboardFocus = KeyboardFocus.LOCATION;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN) {
                keyboardFocus = KeyboardFocus.FILTER;
                return true;
            }
        }
        if (keyboardFocus == KeyboardFocus.FIND) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                finder.userNavigated();
                keyboardFocus = KeyboardFocus.BODY;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_MENU || (alt && keyCode == GLFW.GLFW_KEY_ENTER)) {
                openSearchContextActions(true);
                return true;
            }
            boolean handled = findInput.keyPressed(keyCode, modifiers,
                    () -> Minecraft.getInstance().keyboardHandler.getClipboard(), clipboardSink);
            if (handled && !finder.query().equals(findInput.text())) setFindDraft(findInput.text());
            return handled;
        }
        if (keyboardFocus == KeyboardFocus.FILTER) {
            if (keyCode == GLFW.GLFW_KEY_MENU || (alt && keyCode == GLFW.GLFW_KEY_ENTER)) {
                openSearchContextActions(false);
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
            boolean handled = filterInput.keyPressed(keyCode, modifiers,
                    () -> Minecraft.getInstance().keyboardHandler.getClipboard(), clipboardSink);
            if (handled && !filterDraft.equals(filterInput.text())) setFilterDraft(filterInput.text());
            return handled;
        }
        if (alt && !control
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            openSelectedContextActions();
            return true;
        }
        finder.userNavigated();
        var gesture = selectionGesture(control, (modifiers & GLFW.GLFW_MOD_SHIFT) != 0, true);
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> model.moveSelection(-1, bounds, gesture);
            case GLFW.GLFW_KEY_DOWN -> model.moveSelection(1, bounds, gesture);
            case GLFW.GLFW_KEY_HOME -> model.selectBoundary(bounds, false, gesture);
            case GLFW.GLFW_KEY_END -> model.selectBoundary(bounds, true, gesture);
            case GLFW.GLFW_KEY_RIGHT -> model.emitExpandSelected(bounds);
            case GLFW.GLFW_KEY_LEFT -> model.emitCollapseSelectedOrParent(bounds);
            case GLFW.GLFW_KEY_SPACE -> activateSelected(SFMExplorerPreviewPlacement.Mode.PREVIEW);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> activateSelected(
                    control
                            ? SFMExplorerPreviewPlacement.Mode.ADJACENT
                            : SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW
            );
            case GLFW.GLFW_KEY_MENU -> openSelectedContextActions();
            case GLFW.GLFW_KEY_F10 -> {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) == 0) return false;
                openSelectedContextActions();
            }
            case GLFW.GLFW_KEY_F5 -> model.emitRefreshSelected(bounds);
            case GLFW.GLFW_KEY_R -> {
                if ((modifiers & GLFW.GLFW_MOD_CONTROL) == 0) return false;
                model.emitRefreshSelected(bounds);
                currentReviewSnapshot().ifPresent(review ->
                        ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewFreshnessRuntime.get().ensure(review, true));
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
        if ((keyboardFocus != KeyboardFocus.FILTER && keyboardFocus != KeyboardFocus.FIND)
                || Character.isISOControl(character)
                || (modifiers & commandModifiers) != 0) return false;
        if (keyboardFocus == KeyboardFocus.FIND) {
            boolean handled = findInput.charTyped(character, modifiers);
            if (handled && !finder.query().equals(findInput.text())) setFindDraft(findInput.text());
            return handled;
        }
        boolean handled = filterInput.charTyped(character, modifiers);
        if (handled && !filterDraft.equals(filterInput.text())) setFilterDraft(filterInput.text());
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean left = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        boolean right = button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
        if (!left && !right) return false;
        SFMExplorerPanelModel.State state = model.state(bounds);
        SFMExplorerPanelViewport.Layout layout = effectiveLayout(state);
        if (searchBarClicked(layout.filterControl(), false, mouseX, mouseY, left, right)
                || searchBarClicked(layout.findControl(), true, mouseX, mouseY, left, right)) return true;
        if (freshnessBounds(layout).contains(mouseX, mouseY) && panelContext != null) {
            boolean accepted = ca.teamdman.sfm.client.action.SFMReviewFreshnessAction.openChoices(
                    new SFMClientActionContext(panelContext.host(), () -> panelContext != null && !closed, panelContext.panelId()));
            if (accepted) revealActivationSound.run();
            return accepted;
        }
        var workCells = workCells(layout);
        for (int index = 0; index < workCells.size(); index++) {
            if (left && workCells.get(index).contains(mouseX, mouseY)) {
                keyboardFocus = KeyboardFocus.values()[KeyboardFocus.WORK_PREVIOUS.ordinal() + index];
                invokeWorkControl(index);
                return true;
            }
        }
        if (left && layout.lensControl().contains(mouseX, mouseY)) {
            keyboardFocus = KeyboardFocus.LENS;
            invokeReviewLensChoice();
            return true;
        }
        if (left && layout.revealControl().contains(mouseX, mouseY)) {
            keyboardFocus = KeyboardFocus.REVEAL;
            invokeRevealHere();
            return true;
        }
        if (left && layout.locationControl().contains(mouseX, mouseY)) {
            keyboardFocus = KeyboardFocus.LOCATION;
            model.emitLocationEdit();
            return true;
        }
        if (right && layout.locationControl().contains(mouseX, mouseY)) {
            keyboardFocus = KeyboardFocus.LOCATION;
            openLocationContextActions();
            return true;
        }
        finder.userNavigated();
        Optional<ScrollbarGeometry> scrollbar = scrollbarGeometry(state);
        if (left && scrollbar.filter(value -> value.track().contains(mouseX, mouseY)).isPresent()) {
            keyboardFocus = KeyboardFocus.BODY;
            ScrollbarGeometry geometry = scrollbar.orElseThrow();
            scrollbarDragging = true;
            scrollbarGrabOffset = geometry.thumb().contains(mouseX, mouseY)
                    ? (int) Math.floor(mouseY - geometry.thumb().y())
                    : geometry.thumb().height() / 2;
            scrollToPointer(state, geometry, mouseY);
            return true;
        }
        Optional<SFMExplorerPanelViewport.Cell> hit = state.viewport().hit(mouseX, mouseY);
        if (hit.isEmpty()) {
            if (layout.bodyFrame().contains(mouseX, mouseY)) {
                keyboardFocus = KeyboardFocus.BODY;
                if (right) openLocationContextActions();
                return true;
            }
            return false;
        }
        keyboardFocus = KeyboardFocus.BODY;
        SFMExplorerPanelViewport.Cell cell = hit.orElseThrow();
        if (cell.row().loading()) {
            if (right) openContextActions(cell.row(), SFMExplorerContextActionProvider.Target.ROW);
            return true;
        }
        var eventModifiers = ca.teamdman.sfm.client.input.SFMPointerInputModifiers.current();
        boolean control = eventModifiers.isPresent() ? (eventModifiers.getAsInt() & GLFW.GLFW_MOD_CONTROL) != 0
                : Minecraft.getInstance() != null && net.minecraft.client.gui.screens.Screen.hasControlDown();
        boolean shift = eventModifiers.isPresent() ? (eventModifiers.getAsInt() & GLFW.GLFW_MOD_SHIFT) != 0
                : Minecraft.getInstance() != null && net.minecraft.client.gui.screens.Screen.hasShiftDown();
        model.select(cell.row().path(), bounds, right
                ? cell.row().paths().stream().anyMatch(state.selectedPaths()::contains)
                    ? ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.CURSOR
                    : ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.REPLACE
                : selectionGesture(control, shift, false));
        if (right) {
            openContextActions(cell.row(), SFMExplorerPanelViewport.iconBounds(cell,state.viewport().view()).contains(mouseX,mouseY)
                    ? SFMExplorerContextActionProvider.Target.ICON : SFMExplorerContextActionProvider.Target.ROW);
            return true;
        }
        if (control || shift) { lastClickPath = Optional.empty(); return true; }
        if (cell.chevron().contains(mouseX, mouseY) && cell.row().entry().expandable()) {
            model.emitToggleSelected(bounds);
            rememberClick(cell.row().path());
            return true;
        }
        long now = Util.getMillis();
        if (lastClickPath.equals(Optional.of(cell.row().path())) && now - lastClickTime <= 300L) {
            if (!cell.row().entry().opensOnActivate()) model.emitToggleSelected(bounds);
            else model.emitOpenSelected(bounds, SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW);
        }
        lastClickPath = Optional.of(cell.row().path());
        lastClickTime = now;
        return true;
    }

    private static ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture selectionGesture(
            boolean control, boolean shift, boolean keyboard) {
        return shift ? control ? ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.TOGGLE_RANGE
                : ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.RANGE
                : control ? keyboard ? ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.CURSOR
                : ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.TOGGLE
                : ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.REPLACE;
    }

    private void openContextActions(SFMExplorerProjection.Row row) {
        openContextActions(row,SFMExplorerContextActionProvider.Target.KEYBOARD);
    }

    private void openContextActions(SFMExplorerProjection.Row row,SFMExplorerContextActionProvider.Target target) {
        if (panelContext == null || closed) return;
        SFMClientActionContext actionContext = new SFMClientActionContext(
                panelContext.host(),
                () -> panelContext != null && !closed,
                panelContext.panelId()
        );
        SFMExplorerSession.Snapshot snapshot = session.snapshot();
        SFMExplorerRowInspection inspection = SFMExplorerRowInspection.capture(
                snapshot,
                row,
                loader.relationSnapshot(),
                session.activeRequestEvidence(),
                wasFocused,
                keyboardFocus.name().toLowerCase(Locale.ROOT).replace('_', '-')
        );
        var resolved=presentationRegistry.resolve(row);
        var icon=resolved.presentation().icon();
        if (icon instanceof SFMExplorerPresentation.DegradedIcon degraded) icon=degraded.baseline();
        java.util.Optional<ca.teamdman.sfm.client.presentation.SFMItemIcon> requested=icon instanceof SFMExplorerPresentation.ItemIcon item
                ? java.util.Optional.of(item.item()) : java.util.Optional.empty();
        var rendered=requested.map(value->{
            var evidence=ca.teamdman.sfm.client.presentation.SFMItemIconRenderer.inspect(Minecraft.getInstance(),value);
            var actual=ca.teamdman.sfm.common.registry.SFMWellKnownRegistries.ITEMS.getId(evidence.resolved().stack().getItem());
            return new ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewInspection.Rendered(
                    actual==null ? "unavailable" : actual.toString(),evidence.resolved().usedFallback(),evidence.levelAvailable(),evidence.reason());
        });
        var viewport=model.state(bounds).viewport();
        var geometry=viewport.cells().stream().filter(cell->cell.row().path().equals(row.path())).findFirst()
                .map(cell->SFMExplorerPanelViewport.iconBounds(cell,viewport.view()));
        var iconInspection=new ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewInspection(
                inspection,ca.teamdman.sfm.client.theme.preview.SFMItemstackPreviewSubject.from(row.entry()),
                ca.teamdman.sfm.client.theme.SFMClientThemeService.activeAuthority(),presentationRegistry.previewDecision(row),
                resolved.contributorId(),requested,rendered,geometry);
        List<SFMActionChoice> choices = contextActions.resolve(new SFMExplorerContextActionProvider.Request(
                actionContext,
                snapshot.id(),
                row.path(),
                row.entry(),
                inspection,
                target,
                java.util.Optional.of(iconInspection)
        ));
        choices = new ArrayList<>(choices);
        if (target != SFMExplorerContextActionProvider.Target.ICON) {
            choices.addAll(SFMExplorerNavigationChoices.row(snapshot, row.entry()));
            choices.addAll(compactChoices(row));
        }
        if (choices.isEmpty()) return;
        SFMCommandPaletteScreen.openChoices(
                actionContext,
                Component.literal((target==SFMExplorerContextActionProvider.Target.ICON ? "Icon actions · " : "Row actions · ") + row.entry().label()
                        + (snapshot.selectedPaths().size() > 1 ? " · this row only (" + snapshot.selectedPaths().size() + " selected)" : "")),
                choices
        );
    }

    private void openLocationContextActions() {
        if (panelContext == null || closed) return;
        SFMClientActionContext actionContext = new SFMClientActionContext(
                panelContext.host(),
                () -> panelContext != null && !closed,
                panelContext.panelId()
        );
        String selector = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EXPLORER,
                session.snapshot().id().value()
        ).canonical();
        List<SFMActionChoice> choices = new ArrayList<>(SFMExplorerNavigationChoices.roots(session.snapshot()));
        choices.addAll(List.of(
                        SFMActionChoice.invoke(
                                SFMExplorerActions.LOCATION_COPY.getId().orElseThrow().location(),
                                selector,
                                "Copy location"
                        ),
                        SFMActionChoice.invoke(
                                SFMExplorerActions.LOCATION_EDIT.getId().orElseThrow().location(),
                                selector,
                                "Edit location"
                        )
                ));
        SFMCommandPaletteScreen.openChoices(actionContext, Component.literal("Explorer location"), choices);
    }

    private void openFilterContextActions() {
        if (panelContext == null || closed) return;
        // The field yields focus while its contextual action runs; returning to it adopts
        // the action's current value rather than resurrecting the old draft.
        keyboardFocus = KeyboardFocus.BODY;
        var context = new SFMClientActionContext(panelContext.host(),
                () -> panelContext != null && !closed, panelContext.panelId());
        String selector = SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER,
                session.snapshot().id().value()).canonical();
        SFMCommandPaletteScreen.openChoices(context, Component.literal("Explorer filter"), List.of(
                SFMActionChoice.invoke(SFMExplorerActions.FILTER_CLEAR.getId().orElseThrow().location(),
                        selector, "Clear input")));
    }

    private void openSelectedContextActions() {
        model.state(bounds).selectedRow().ifPresent(this::openContextActions);
    }

    private boolean invokeRevealHere() {
        if (panelContext == null || closed) return false;
        SFMClientActionContext actionContext = new SFMClientActionContext(
                panelContext.host(),
                () -> panelContext != null && !closed,
                panelContext.panelId()
        );
        boolean accepted = SFMRevealHereAction.invokeFromControl(actionContext, this::revealFeedback);
        if (accepted) revealActivationSound.run();
        return accepted;
    }

    private boolean invokeReviewLensChoice() {
        if (panelContext == null || closed) return false;
        SFMClientActionContext actionContext = new SFMClientActionContext(
                panelContext.host(),
                () -> panelContext != null && !closed,
                panelContext.panelId()
        );
        boolean accepted = SFMReviewLensSetAction.openChoicesFromControl(
                actionContext,
                this::reviewLensFeedback
        );
        if (accepted) revealActivationSound.run();
        return accepted;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        this.mouseX = (int) mouseX;
        this.mouseY = (int) mouseY;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (filterInputDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            moveFilterCaret(mouseX, true);
            return true;
        }
        if (!scrollbarDragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        SFMExplorerPanelModel.State state = model.state(bounds);
        scrollbarGeometry(state).ifPresent(geometry -> scrollToPointer(state, geometry, mouseY));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (filterInputDragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            filterInputDragging = false;
            return true;
        }
        if (!scrollbarDragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        scrollbarDragging = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0) return false;
        // The list is the panel's primary scroll surface. Requiring the pointer
        // to land inside only the inset body made wheel input appear dead over
        // the location/filter/status chrome and near the one-pixel frame.
        if (!bounds.contains(mouseX, mouseY)) return false;
        finder.userNavigated();
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
        normalizeKeyboardFocus();
        if (pendingRevealSelection.isPresent()
                && state.selectedRow().map(row -> row.contains(pendingRevealSelection.orElseThrow())).orElse(false)) {
            pendingRevealSelection = Optional.empty();
        }
        if (keyboardFocus != KeyboardFocus.FILTER) {
            filterDraft = state.session().settings().filterQuery();
            filterInput.setText(filterDraft);
        }
        SFMExplorerPanelViewport.Layout layout = effectiveLayout(state);
        FocusChrome chrome = focusChrome(focused);
        fill(poseStack, layout.content(), ca.teamdman.sfm.client.theme.SFMClientThemeService.active()
                .colour(ca.teamdman.sfm.client.theme.SFMColourRole.PANEL_BACKGROUND));
        fill(poseStack, layout.header(), HEADER);
        fill(poseStack, layout.filter(), HEADER);
        fill(poseStack, layout.bodyFrame(), ca.teamdman.sfm.client.theme.SFMClientThemeService.active()
                .colour(ca.teamdman.sfm.client.theme.SFMColourRole.PANEL_BACKGROUND));
        border(poseStack, layout.content(), BORDER);
        renderHeader(poseStack, minecraft, state, chrome.location(), chrome.lens(), chrome.reveal());
        renderFilter(poseStack, minecraft, state, chrome.filter());
        renderSearchBar(poseStack, minecraft, layout.findControl(), true, focused && keyboardFocus == KeyboardFocus.FIND);
        renderFreshness(poseStack, minecraft, layout);
        renderWorkControls(poseStack, minecraft, layout, focused);
        for (SFMExplorerPanelViewport.Cell cell : state.viewport().cells()) {
            renderCell(poseStack, minecraft, state, cell);
        }
        renderStatus(poseStack, minecraft, state);
        border(poseStack, layout.bodyFrame(), chrome.body() ? FOCUSED_BORDER : BORDER);
        renderScrollbar(poseStack, state);
        model.observeVisibleFrame(state.viewport().scrollRow());
        // Revision zero can contain only the synthetic initial root, before any
        // resolver publication. Do not mistake that shell for usable results.
        if (!firstEntriesRendered && state.projection().relationRevision() > 0) {
            long entryCount = SFMExplorerPanelViewport.publishedContentCount(
                    state.viewport(), state.projection().relationRevision());
            if (entryCount > 0) {
                firstEntriesRendered = true;
                ca.teamdman.sfm.SFM.LOGGER.info(
                        "SFM_EXPLORER_FIRST_ENTRIES_RENDERED explorer={} location={} relation_revision={} visible_entries={} panel_to_frame_micros={}",
                        state.session().id(), state.session().location().canonical(),
                        state.projection().relationRevision(), entryCount,
                        (System.nanoTime() - panelCreatedNanos) / 1_000L);
            }
        }
    }

    static Optional<ScrollbarGeometry> scrollbarGeometry(SFMExplorerPanelModel.State state) {
        SFMExplorerPanelViewport.Snapshot viewport = state.viewport();
        if (viewport.maximumScrollRow() <= 0) return Optional.empty();
        SFMExplorerPanelViewport.Rect frame = viewport.layout().bodyFrame();
        int trackHeight = Math.max(0, frame.height() - 4);
        if (frame.width() < 5 || trackHeight < 8) return Optional.empty();
        SFMExplorerPanelViewport.Rect track = new SFMExplorerPanelViewport.Rect(
                frame.x() + frame.width() - 6,
                frame.y() + 2,
                4,
                trackHeight
        );
        int totalGridRows = viewport.maximumScrollRow() + viewport.visibleGridRows();
        int proportional = totalGridRows <= 0
                ? track.height()
                : (int) Math.round((double) track.height() * viewport.visibleGridRows() / totalGridRows);
        int thumbHeight = Math.min(track.height(), Math.max(8, proportional));
        int travel = Math.max(0, track.height() - thumbHeight);
        int thumbY = track.y() + (int) Math.round(
                (double) travel * viewport.scrollRow() / viewport.maximumScrollRow());
        return Optional.of(new ScrollbarGeometry(
                track,
                new SFMExplorerPanelViewport.Rect(track.x(), thumbY, track.width(), thumbHeight)
        ));
    }

    private void renderScrollbar(PoseStack poseStack, SFMExplorerPanelModel.State state) {
        scrollbarGeometry(state).ifPresent(geometry -> {
            fill(poseStack, geometry.track(), SCROLLBAR_TRACK);
            boolean hovered = geometry.thumb().contains(mouseX, mouseY) || scrollbarDragging;
            fill(poseStack, geometry.thumb(), hovered ? SCROLLBAR_THUMB_HOVERED : SCROLLBAR_THUMB);
        });
    }

    private void scrollToPointer(
            SFMExplorerPanelModel.State state,
            ScrollbarGeometry geometry,
            double mouseY
    ) {
        int travel = geometry.track().height() - geometry.thumb().height();
        if (travel <= 0) return;
        double requested = mouseY - scrollbarGrabOffset - geometry.track().y();
        double fraction = Math.max(0.0D, Math.min(1.0D, requested / travel));
        int target = (int) Math.round(fraction * state.viewport().maximumScrollRow());
        model.scrollRows(target - state.viewport().scrollRow(), bounds);
    }

    @Override
    public Optional<SFMPanelTooltip> tooltipAt(double mouseX, double mouseY) {
        if (closed) return Optional.empty();
        SFMExplorerPanelModel.State state = model.state(bounds);
        SFMExplorerPanelViewport.Layout layout = effectiveLayout(state);
        var searchTooltip = searchTooltipAt(layout, mouseX, mouseY);
        if (searchTooltip.isPresent()) return searchTooltip;
        if (freshnessBounds(layout).contains(mouseX, mouseY)) {
            var evidence = freshnessEvidence();
            return Optional.of(SFMPanelTooltip.of(Component.literal(freshnessSummary(evidence) + ". "
                    + evidence.age(System.currentTimeMillis())
                    + ". Click for recheck, full revision details, copy, or current files. Original review content is unchanged.")));
        }
        var workCells = workCells(layout);
        for (int index = 0; index < workCells.size(); index++) {
            if (workCells.get(index).contains(mouseX, mouseY)) {
                var control = SFMReviewWorkQueueControls.CONTROLS.get(index);
                boolean writable = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.get()
                        .snapshot().writable();
                return Optional.of(SFMPanelTooltip.of(Component.literal(control.description()
                        + ". Acts on [Saved cursor], not the blue Explorer selection. "
                        + (index < 4 && !writable ? "Requires writable review. " : "") + control.choice().command())));
            }
        }
        if (layout.locationControl().contains(mouseX, mouseY)) {
            return Optional.of(SFMPanelTooltip.of(Component.literal(
                    state.session().location().canonical()
            )));
        }
        if (layout.lensControl().contains(mouseX, mouseY)) {
            return reviewLensDescriptor().map(lens -> SFMPanelTooltip.of(Component.literal(
                    "Review lens: " + lens.title()
                            + SFMReleaseReviewExplorerRuntime.get().queryExpression(lens)
                                    .map(query -> ". Query: " + query).orElse("")
                            + ". " + SFMReviewLensSetAction.filterDescription(state.session().settings().filterQuery())
                            + ". Click for remaining work, exact coverage, lens or layout choices"
            )));
        }
        if (layout.revealControl().contains(mouseX, mouseY)) {
            return Optional.of(SFMPanelTooltip.of(Component.literal(
                    "Reveal the most recently focused document in this Explorer"
            )));
        }
        var cell = state.viewport().hit(mouseX, mouseY);
        if (cell.isPresent() && (state.session().settings().filterActive() || !finder.query().isEmpty())) {
            var row = cell.orElseThrow().row();
            var filter = state.projection().matchEvidence().get(row.path());
            var find = finder.evidence().get(row.path());
            String details = presentation(state, row).label();
            if (state.session().settings().filterActive()) details += "\nFilter: " + matchDescription(
                    filter == null ? null : filter.self(), filter != null && filter.descendantMatch(),
                    filter != null && filter.descendantsComplete());
            if (!finder.query().isEmpty()) details += "\nFind: " + matchDescription(find,
                    finder.descendantMatch(row.path()), finder.complete());
            return Optional.of(SFMPanelTooltip.of(Component.literal(details)));
        }
        return Optional.empty();
    }

    private static String matchDescription(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntryMatch self,
                                            boolean descendant, boolean complete) {
        String fields = self == null ? "" : self.fields().stream().map(field -> field.role().name().toLowerCase(Locale.ROOT))
                .distinct().limit(4).collect(java.util.stream.Collectors.joining(", "));
        return "self " + (self != null && self.matches() ? "matches (" + fields + ")" : "does not match")
                + "; descendants " + (descendant ? "contain a match (filled dot)" : complete ? "no matches" : "unknown (hollow dot)");
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

    /** Already-published label for an exact path; no filesystem or resolver read. */
    public Optional<String> publishedLabel(SFMPath path) {
        return model.state(bounds).projection().rows().stream()
                .filter(row -> row.path().equals(path)).map(row -> row.entry().label()).findFirst();
    }

    public void compactPaths(ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind kind, String argument) {
        var options = session.snapshot().settings().compaction();
        Optional<SFMPath> local = Optional.empty();
        switch (kind) {
            case COPY -> { clipboardSink.accept(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCompactionPreset.encode(options)); return; }
            case APPLY -> options = ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCompactionPreset.decode(argument);
            case SET -> options = options.enabled(Boolean.parseBoolean(argument));
            case UNMERGE, RESET -> {
                var path = SFMPath.parse(argument);
                var row = model.state(bounds).projection().rows().stream().filter(candidate -> candidate.contains(path))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Path is no longer in this Explorer projection"));
                local = Optional.of(row.segments().get(0).path());
                if (kind == ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind.UNMERGE) {
                    if (row.segments().size() < 2) throw new IllegalArgumentException("Path is no longer a compact chain");
                    options = options.override(row.paths().subList(0, row.paths().size() - 1), Optional.of(false));
                } else options = options.override(options.overrides().keySet().stream()
                        .filter(candidate -> ca.teamdman.sfm.client.explorer.SFMPathHierarchy.contains(path, candidate)).toList(), Optional.empty());
            }
        }
        finder.userNavigated();
        model.changeCompaction(options, local, bounds);
    }

    public void restoreCompaction(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerCompaction.Options options) {
        session.setSettings(session.snapshot().settings().withCompaction(options));
    }

    List<SFMActionChoice> compactChoices(SFMExplorerProjection.Row row) {
        var options = session.snapshot().settings().compaction();
        var result = new ArrayList<SFMActionChoice>();
        var root = row.segments().get(0).path();
        String path = root.canonical();
        if (row.segments().size() > 1) result.add(SFMActionChoice.invoke(ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind.UNMERGE.id(), path,
                ca.teamdman.sfm.client.search.SFMExplorerCompactText.UNMERGE.getComponent().getString()));
        if (options.overrides().keySet().stream().anyMatch(candidate -> ca.teamdman.sfm.client.explorer.SFMPathHierarchy.contains(root, candidate)))
            result.add(SFMActionChoice.invoke(ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind.RESET.id(), path,
                    ca.teamdman.sfm.client.search.SFMExplorerCompactText.RESET.getComponent().getString()));
        result.add(SFMActionChoice.invoke(ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind.SET.id(), Boolean.toString(!options.enabled()),
                (options.enabled() ? ca.teamdman.sfm.client.search.SFMExplorerCompactText.DISABLE : ca.teamdman.sfm.client.search.SFMExplorerCompactText.ENABLE).getComponent().getString()));
        result.add(SFMActionChoice.invoke(ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind.COPY.id(), "",
                ca.teamdman.sfm.client.search.SFMExplorerCompactText.COPY.getComponent().getString()));
        result.add(SFMActionChoice.continuation(ca.teamdman.sfm.client.action.SFMExplorerCompactAction.Kind.APPLY.id(), "",
                ca.teamdman.sfm.client.search.SFMExplorerCompactText.APPLY.getComponent().getString()));
        return List.copyOf(result);
    }

    /**
     * Returns the same context-sensitive chrome geometry used for rendering,
     * tooltips, and pointer hit-testing. Automation must not infer review or
     * reveal controls from the model's context-free viewport layout.
     */
    public SFMExplorerPanelViewport.Layout interactionLayout() {
        return effectiveLayout(model.state(bounds));
    }

    /**
     * Materializes every missing ancestor page and queues exact row selection
     * against the next real panel bounds, so reveal never guesses scroll geometry.
     */
    public CompletionStage<SFMExplorerPathReveal.Result> revealPath(SFMPath containingRoot, SFMPath path) {
        return revealPath(containingRoot, path, SFMExplorerPathReveal.FilterPolicy.CLEAR);
    }

    public CompletionStage<SFMExplorerPathReveal.Result> revealPathRetainingFilter(SFMPath containingRoot, SFMPath path) {
        return revealPath(containingRoot, path, SFMExplorerPathReveal.FilterPolicy.RETAIN);
    }

    private CompletionStage<SFMExplorerPathReveal.Result> revealPath(
            SFMPath containingRoot, SFMPath path, SFMExplorerPathReveal.FilterPolicy filterPolicy) {
        return SFMExplorerPathReveal.reveal(
                session,
                loader,
                containingRoot,
                path,
                128,
                () -> pendingRevealSelection = Optional.of(path),
                filterPolicy
        );
    }

    /**
     * Replaces a contributed projection in place while retaining this
     * Explorer's visible location, settings, identity, and panel slot.
     */
    public CompletionStage<Void> replaceProjectionRoot(SFMPath nextRoot) {
        Objects.requireNonNull(nextRoot, "nextRoot");
        SFMExplorerSession.Snapshot before = session.snapshot();
        if (before.roots().equals(java.util.Set.of(nextRoot))) {
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        before.roots().forEach(session::cancelChildrenRequest);
        session.replaceLocation(before.location(), java.util.Set.of(nextRoot));
        pendingRevealSelection = Optional.empty();
        return loader.openRoot(nextRoot).thenCompose(entry -> {
            if (closed || !session.snapshot().roots().equals(java.util.Set.of(nextRoot))) {
                return java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("The Explorer changed before its review lens loaded")
                );
            }
            session.expand(nextRoot);
            return session.requestChildren(nextRoot, loader, 128).completion().thenApply(ignored -> null);
        });
    }

    /**
     * Reloads every currently expanded projection relation against the
     * resolver's latest generation. This is used after an atomic review-file
     * mutation so Comments, Hashtags, Query, and Status panes update in place
     * without replacing their explorer identity or view settings.
     */
    public CompletionStage<Void> refreshExpandedProjection() {
        SFMExplorerSession.Snapshot snapshot = session.snapshot();
        java.util.ArrayList<SFMPath> paths = new java.util.ArrayList<>(snapshot.expanded());
        snapshot.roots().stream().filter(path -> !paths.contains(path)).forEach(paths::add);
        paths.sort(java.util.Comparator
                .comparingInt((SFMPath path) -> path.segments().size())
                .thenComparing(SFMPath::canonical));
        java.util.concurrent.CompletableFuture<Void> result =
                java.util.concurrent.CompletableFuture.completedFuture(null);
        for (SFMPath path : paths) {
            result = result.thenCompose(ignored -> {
                if (closed) return java.util.concurrent.CompletableFuture.completedFuture(null);
                return session.requestChildren(path, loader, 128).completion().thenApply(load -> null);
            });
        }
        return result;
    }

    private void activateSelected(SFMExplorerPreviewPlacement.Mode mode) {
        SFMExplorerPanelModel.State state = model.state(bounds);
        if (state.selectedRow().map(row -> !row.entry().opensOnActivate()).orElse(false)) {
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
            boolean locationFocused,
            boolean lensFocused,
            boolean revealFocused
    ) {
        SFMExplorerPanelViewport.Layout layout = effectiveLayout(state);
        int textY = layout.header().y() + Math.max(1, (layout.header().height() - minecraft.font.lineHeight) / 2);
        fill(poseStack, layout.locationControl(), 0xF0353535);
        border(
                poseStack,
                layout.locationControl(),
                locationFocused ? FOCUSED_BORDER : BORDER
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
        if (layout.lensControl().width() > 0) {
            fill(poseStack, layout.lensControl(), 0xF0353535);
            border(poseStack, layout.lensControl(), lensFocused ? FOCUSED_BORDER : BORDER);
            String title = reviewLensDescriptor().map(SFMReviewLensSetAction::shortTitle)
                    .orElse("Review");
            drawTrimmed(
                    poseStack,
                    minecraft,
                    title + " v",
                    layout.lensControl().x() + 5,
                    textY,
                    Math.max(0, layout.lensControl().width() - 10),
                    TEXT
            );
        }
        if (layout.revealControl().width() > 0) {
            fill(poseStack, layout.revealControl(), 0xF0353535);
            border(poseStack, layout.revealControl(), revealFocused ? FOCUSED_BORDER : BORDER);
            int iconX = layout.revealControl().x()
                    + Math.max(0, (layout.revealControl().width() - SFMItemIconRenderer.SIZE) / 2);
            int iconY = layout.revealControl().y()
                    + Math.max(0, (layout.revealControl().height() - SFMItemIconRenderer.SIZE) / 2);
            SFMItemIconRenderer.render(poseStack, minecraft, REVEAL_ICON, iconX, iconY);
        }
    }

    boolean locationControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.LOCATION;
    }

    boolean filterControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.FILTER;
    }

    boolean findControlHasKeyboardFocus() { return keyboardFocus == KeyboardFocus.FIND; }

    boolean lensControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.LENS;
    }

    boolean revealControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.REVEAL;
    }

    boolean bodyControlHasKeyboardFocus() {
        return keyboardFocus == KeyboardFocus.BODY;
    }

    public FocusChrome focusChrome(boolean panelFocused) {
        return new FocusChrome(
                panelFocused && keyboardFocus == KeyboardFocus.LOCATION,
                panelFocused && lensControlVisible() && keyboardFocus == KeyboardFocus.LENS,
                panelFocused && revealControlVisible() && keyboardFocus == KeyboardFocus.REVEAL,
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

    private static void playButtonActivationSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSoundManager() == null) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void revealFeedback(Component component) {
        ca.teamdman.sfm.SFM.LOGGER.info("SFM_EXPLORER_REVEAL_FEEDBACK {}", component.getString());
        if (panelContext != null && panelContext.host() instanceof ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer workspace) {
            String lower = component.getString().toLowerCase(java.util.Locale.ROOT);
            boolean shake = lower.contains("failed") || lower.contains("unavailable")
                    || lower.contains("no recently") || lower.contains("stale")
                    || lower.contains("not authorized") || lower.contains("more than one");
            workspace.showWorkspaceToast("sfm:explorer-reveal", component, shake);
        }
    }

    private void reviewLensFeedback(Component component) {
        ca.teamdman.sfm.SFM.LOGGER.info("SFM_REVIEW_LENS_FEEDBACK {}", component.getString());
        if (panelContext != null
                && panelContext.host() instanceof ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer workspace) {
            workspace.showWorkspaceToast("sfm:review-lens", component, true);
        }
    }

    private Optional<SFMReleaseReviewExplorerRuntime.LensDescriptor> reviewLensDescriptor() {
        return SFMReleaseReviewExplorerRuntime.get().lensDescriptor(session.snapshot().roots());
    }

    private boolean lensControlVisible() {
        if (panelContext == null || closed) return false;
        return SFMReviewLensSetAction.isControlVisible(new SFMClientActionContext(
                panelContext.host(),
                () -> panelContext != null && !closed,
                panelContext.panelId()
        ));
    }

    private boolean revealControlVisible() {
        if (panelContext == null || closed) return false;
        return SFMRevealHereAction.isControlVisible(new SFMClientActionContext(
                panelContext.host(),
                () -> panelContext != null && !closed,
                panelContext.panelId()
        ));
    }

    private SFMExplorerPanelViewport.Layout effectiveLayout(SFMExplorerPanelModel.State state) {
        Objects.requireNonNull(state, "state");
        return SFMExplorerPanelViewport.layout(bounds, lensControlVisible(), revealControlVisible(), reviewToolbarHeight(), true);
    }

    private int freshnessHeight() {
        return !closed && panelContext != null && reviewLensDescriptor().isPresent() ? 20 : 0;
    }

    private int reviewToolbarHeight() { return freshnessHeight() + workToolbarHeight(); }

    private Optional<ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.Snapshot> currentReviewSnapshot() {
        var review = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.get().snapshot();
        return reviewLensDescriptor().filter(lens -> review.openEpoch() == lens.reviewOpenEpoch()
                && review.path().filter(lens.reviewPath()::equals).isPresent()).map(ignored -> review);
    }

    private ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewFreshness.Evidence freshnessEvidence() {
        return currentReviewSnapshot().map(review ->
                ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewFreshnessRuntime.get().evidence(review))
                .orElseGet(() -> ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewFreshness.unavailable(
                        "This Explorer's review lease is no longer open; reopen its review to check freshness"));
    }

    private String freshnessSummary(ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewFreshness.Evidence evidence) {
        boolean live = currentReviewSnapshot().flatMap(review -> review.document()).map(review ->
                ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1.OBSERVATION_SCHEMA.equals(review.schema())
                        && review.repositoryBindings().stream().anyMatch(binding -> binding.workingTreeCapture().isPresent())).orElse(false);
        if (!live) return evidence.summary();
        return "Live review · " + switch (evidence.state()) {
            case CURRENT_AT_CHECK -> "observation matches source at check";
            case OUTDATED -> "newer source available; refresh observation";
            case CHECKING -> "checking observed source";
            case UNKNOWN -> "source comparison unavailable";
        };
    }

    private SFMExplorerPanelViewport.Rect freshnessBounds(SFMExplorerPanelViewport.Layout layout) {
        var toolbar = layout.toolbar();
        return new SFMExplorerPanelViewport.Rect(toolbar.x(), toolbar.y(), toolbar.width(),
                Math.min(freshnessHeight(), toolbar.height()));
    }

    private List<SFMExplorerPanelViewport.Rect> workCells(SFMExplorerPanelViewport.Layout layout) {
        if (workToolbarHeight() == 0) return List.of();
        var toolbar = layout.toolbar();
        int banner = Math.min(freshnessHeight(), toolbar.height());
        return SFMExplorerPanelViewport.toolbarCells(new SFMExplorerPanelViewport.Rect(
                toolbar.x(), toolbar.y() + banner, toolbar.width(), toolbar.height() - banner),
                SFMReviewWorkQueueControls.CONTROLS.size());
    }

    private void renderFreshness(PoseStack poseStack, Minecraft minecraft, SFMExplorerPanelViewport.Layout layout) {
        var area = freshnessBounds(layout);
        if (area.height() == 0) return;
        var evidence = freshnessEvidence();
        fill(poseStack, area, 0xF0353030);
        int color = switch (evidence.state()) {
            case CURRENT_AT_CHECK -> 0xFFBBEEBB;
            case CHECKING -> MUTED;
            case OUTDATED, UNKNOWN -> 0xFFFFCC66;
        };
        drawTrimmed(poseStack, minecraft, freshnessSummary(evidence) + " · " + evidence.age(System.currentTimeMillis()),
                area.x() + 4, area.y() + Math.max(1, (area.height() - minecraft.font.lineHeight) / 2),
                Math.max(0, area.width() - 8), color);
    }

    private int workToolbarHeight() {
        return !closed && panelContext != null && reviewLensDescriptor().filter(lens ->
                lens.projection() == ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType.Projection.QUERY)
                .isPresent() ? 20 : 0;
    }

    private int workFocusIndex() {
        int index = keyboardFocus.ordinal() - KeyboardFocus.WORK_PREVIOUS.ordinal();
        return index >= 0 && index < SFMReviewWorkQueueControls.CONTROLS.size() ? index : -1;
    }

    private boolean invokeWorkControl(int index) {
        if (panelContext == null || closed) return false;
        var context = new SFMClientActionContext(panelContext.host(), () -> panelContext != null && !closed,
                panelContext.panelId());
        boolean accepted = SFMReviewWorkQueueControls.invoke(context,
                SFMReviewWorkQueueControls.CONTROLS.get(index).kind(), Optional.empty(),
                message -> ca.teamdman.sfm.SFM.LOGGER.info("SFM_REVIEW_WORK_CONTROL_FEEDBACK {}", message.getString()));
        if (accepted) revealActivationSound.run();
        return accepted;
    }

    private void renderWorkControls(PoseStack poseStack, Minecraft minecraft,
                                    SFMExplorerPanelViewport.Layout layout, boolean focused) {
        if (workToolbarHeight() == 0) return;
        var cells = workCells(layout);
        var review = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime.get().snapshot();
        for (int index = 0; index < cells.size(); index++) {
            var cell = cells.get(index);
            var control = SFMReviewWorkQueueControls.CONTROLS.get(index);
            fill(poseStack, cell, cell.contains(mouseX, mouseY) ? HOVERED : HEADER);
            border(poseStack, cell, focused && workFocusIndex() == index ? FOCUSED_BORDER : BORDER);
            drawTrimmed(poseStack, minecraft, control.label(), cell.x() + 3,
                    cell.y() + Math.max(1, (cell.height() - minecraft.font.lineHeight) / 2),
                    Math.max(0, cell.width() - 6), review.writable() || index == 4 ? TEXT : MUTED);
        }
    }

    private void renderFilter(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state,
            boolean focused
    ) {
        renderSearchBar(poseStack, minecraft, state.viewport().layout().filterControl(), false, focused);
    }

    private void renderCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state,
            SFMExplorerPanelViewport.Cell cell
    ) {
        boolean selected = cell.row().paths().stream().anyMatch(state.selectedPaths()::contains);
        boolean hovered = cell.bounds().contains(mouseX, mouseY);
        SFMExplorerPresentation presentation = presentation(state, cell.row());
        FilterRowPresentation filterPresentation = filterRowPresentation(cell.row(), presentation.label());
        if (selected) fill(poseStack, cell.bounds(), SELECTED);
        else if (hovered) fill(poseStack, cell.bounds(), HOVERED);
        if (finder.preview().filter(cell.row()::contains).isPresent() && !selected)
            border(poseStack, cell.bounds(), ca.teamdman.sfm.client.theme.SFMClientThemeService.active()
                    .colour(ca.teamdman.sfm.client.theme.SFMColourRole.SEARCH_FIND));
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
            renderListCell(poseStack, minecraft, state, cell, presentation, filterPresentation);
        }
        if (state.selectedPath().filter(cell.row()::contains).isPresent())
            border(poseStack, cell.bounds(), 0xFF55EEEE);
    }

    private void renderListCell(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMExplorerPanelModel.State state,
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
        drawMatchLabel(
                poseStack,
                minecraft,
                filterPresentation.label(),
                labelX,
                textY,
                Math.max(0, cell.bounds().x() + cell.bounds().width() - labelX - 4),
                filterPresentation.textColour(), state, cell
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
        drawMatchLabel(
                poseStack,
                minecraft,
                filterPresentation.label(),
                labelX,
                cell.bounds().y() + 5,
                width,
                filterPresentation.textColour(), state, cell
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
        int activeRequests = loader.activeParents().size();
        if (activeRequests > 0) {
            summary = loadingSummary(activeRequests, net.minecraft.Util.getMillis()) + " · " + summary;
            colour = TEXT;
        } else if (state.projection().filter().active()) {
            Optional<String> filterFailure = state.session().roots().stream()
                    .map(root -> loader.filterDomainFailure(root, state.session().settings().filterQuery(),
                            state.session().settings().filterOptions()))
                    .flatMap(Optional::stream)
                    .findFirst();
            if (filterFailure.isPresent()) {
                summary = "Complete filter index unavailable: " + filterFailure.orElseThrow();
                colour = DIAGNOSTIC;
            } else {
                summary = filterSummary(state.projection().filter());
                if (state.projection().rows().size() != state.projection().filter().visibleRowCount())
                    summary = state.projection().rows().size() + " compact rows · " + summary.replace(" visible / ", " logical entries / ");
            }
        } else if (!state.projection().diagnostics().isEmpty()) {
            summary += "; " + state.projection().diagnostics().get(0);
            colour = DIAGNOSTIC;
        }
        if (keyboardFocus == KeyboardFocus.FIND || !finder.query().isEmpty()) summary = finder.status() + " · " + summary;
        if (state.selectedPaths().size() > 1) {
            long visible = state.viewport().cells().stream().flatMap(cell -> cell.row().paths().stream())
                    .filter(state.selectedPaths()::contains).distinct().count();
            summary = state.selectedPaths().size() + " selected (" + (state.selectedPaths().size() - visible)
                    + " off-screen) · " + summary;
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

    static String loadingSummary(int activeRequests, long elapsedMillis) {
        if (activeRequests <= 0) throw new IllegalArgumentException("activeRequests must be positive");
        String[] frames = {"|", "/", "-", "\\"};
        String frame = frames[Math.floorMod(elapsedMillis / 150L, frames.length)];
        return frame + " Loading " + activeRequests + (activeRequests == 1 ? " branch" : " branches") + "...";
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
        if (row.loading()) {
            return new SFMExplorerPresentation(
                    loadingRowLabel(net.minecraft.Util.getMillis()),
                    resolved.icon()
            );
        }
        return new SFMExplorerPresentation(
                SFMExplorerPathLabeler.label(row, state.session()),
                resolved.icon()
        );
    }

    static String loadingRowLabel(long elapsedMillis) {
        String[] frames = {"|", "/", "-", "\\"};
        return frames[Math.floorMod(elapsedMillis / 150L, frames.length)] + " Loading children...";
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
        if (icon instanceof SFMExplorerPresentation.DegradedIcon degraded) {
            return iconNarration(degraded.baseline()) + ", preview rule warning; inspect icon actions";
        }
        if (icon instanceof SFMExplorerPresentation.ItemIcon item) {
            return item.item().accessibleLabel() + " ItemStack icon";
        }
        if (icon instanceof SFMExplorerPresentation.MarkerIcon marker) {
            return marker.marker() + " marker icon";
        }
        throw new AssertionError("Unhandled explorer icon " + icon);
    }

    private void cycleKeyboardFocus(boolean reverse) {
        java.util.ArrayList<KeyboardFocus> order = new java.util.ArrayList<>();
        order.add(KeyboardFocus.BODY);
        order.add(KeyboardFocus.LOCATION);
        if (lensControlVisible()) order.add(KeyboardFocus.LENS);
        if (revealControlVisible()) order.add(KeyboardFocus.REVEAL);
        order.add(KeyboardFocus.FILTER);
        order.add(KeyboardFocus.FIND);
        if (workToolbarHeight() > 0) {
            for (int index = 0; index < SFMReviewWorkQueueControls.CONTROLS.size(); index++) {
                order.add(KeyboardFocus.values()[KeyboardFocus.WORK_PREVIOUS.ordinal() + index]);
            }
        }
        int current = order.indexOf(keyboardFocus);
        if (current < 0) current = 0;
        int delta = reverse ? -1 : 1;
        keyboardFocus = order.get(Math.floorMod(current + delta, order.size()));
        if (keyboardFocus == KeyboardFocus.FILTER) {
            filterDraft = session.snapshot().settings().filterQuery();
            filterInput.setText(filterDraft);
        }
        if (keyboardFocus == KeyboardFocus.FIND) {
            finder.capture(bounds);
            if (!findInput.text().isEmpty()) setFindDraft(findInput.text());
        }
    }

    private void normalizeKeyboardFocus() {
        if (workFocusIndex() >= 0 && workToolbarHeight() == 0) keyboardFocus = KeyboardFocus.BODY;
        if (keyboardFocus == KeyboardFocus.LENS && !lensControlVisible()) {
            keyboardFocus = KeyboardFocus.LOCATION;
        }
        if (keyboardFocus == KeyboardFocus.REVEAL && !revealControlVisible()) {
            keyboardFocus = lensControlVisible() ? KeyboardFocus.LENS : KeyboardFocus.LOCATION;
        }
    }

    private void focusFilter() {
        keyboardFocus = KeyboardFocus.FILTER;
        filterDraft = session.snapshot().settings().filterQuery();
        filterInput.setText(filterDraft);
    }

    public void focusSearch(String field) {
        switch (field) {
            case "filter" -> focusFilter();
            case "find" -> {
                keyboardFocus = KeyboardFocus.FIND;
                finder.capture(bounds);
                if (!findInput.text().isEmpty()) setFindDraft(findInput.text());
            }
            case "body" -> { keyboardFocus = KeyboardFocus.BODY; finder.userNavigated(); }
            default -> throw new IllegalArgumentException("Unknown Explorer focus: " + field);
        }
    }

    private boolean searchField(String field) {
        return switch (field) {
            case "find" -> true;
            case "filter" -> false;
            case "focused" -> {
                if (keyboardFocus != KeyboardFocus.FIND && keyboardFocus != KeyboardFocus.FILTER)
                    throw new IllegalArgumentException("Focus Find or Filter first");
                yield keyboardFocus == KeyboardFocus.FIND;
            }
            default -> throw new IllegalArgumentException("Unknown Explorer search field: " + field);
        };
    }

    public void toggleSearchOption(String field, String option) {
        boolean find = searchField(field);
        if (option.equals("highlight")) {
            if (find) findHighlight = !findHighlight;
            else filterHighlight = !filterHighlight;
            return;
        }
        var before = find ? finder.options() : session.snapshot().settings().filterOptions();
        var after = switch (option) {
            case "case" -> before.withCase(!before.matchCase());
            case "whole-word" -> before.withWholeWord(!before.wholeWord());
            case "regex" -> before.toggleRegex();
            case "fuzzy" -> before.toggleFuzzy();
            case "dot-all" -> {
                if (before.mode() != SFMTextMatchOptions.Mode.REGEX)
                    throw new IllegalArgumentException("Dot-all applies only in Regex mode");
                yield before.withDotAll(!before.dotAll());
            }
            default -> throw new IllegalArgumentException("Unknown search option: " + option);
        };
        if (find) finder.setQuery(findInput.text(), after, bounds);
        else { session.setFilterOptions(after); setFilterDraft(filterInput.text()); }
    }

    public void clearSearch(String field) {
        if (searchField(field)) setFindDraft("");
        else setFilterDraft("");
    }

    public void moveSearch(int direction, boolean wrap) { finder.move(direction, wrap, bounds); }
    public void selectSearchMatches(boolean all) { finder.selectMatches(all, bounds); }
    public void selectRow(String gesture, SFMPath path) {
        var mode = ca.teamdman.sfm.client.explorer.lazy.SFMExplorerRowSelection.Gesture.valueOf(
                gesture.toUpperCase(Locale.ROOT).replace('-', '_'));
        if (model.state(bounds).projection().rows().stream().noneMatch(row -> !row.loading() && row.contains(path)))
            throw new IllegalArgumentException("Selection target must be a displayed logical row; use complete Find for unmaterialized matches");
        finder.userNavigated();
        model.select(path, bounds, mode);
    }
    public void setFindMaterializedOnly(boolean value) { finder.setMaterializedOnly(value, bounds); }

    private void setFindDraft(String value) {
        findInput.setText(value);
        finder.setQuery(value, finder.options(), bounds);
    }

    private void submitSearch(SFMExplorerSearchAction.Kind kind, String arguments) {
        model.submitSearchCommand("sfm action invoke " + kind.id() + " " + arguments);
    }

    private boolean searchBarClicked(SFMExplorerPanelViewport.Rect row, boolean find, double x, double y,
                                     boolean left, boolean right) {
        if (!row.contains(x, y)) return false;
        String field = find ? "find" : "filter";
        var bar = SFMExplorerSearchBar.layout(row);
        for (var button : bar.buttons()) {
            if (!button.bounds().contains(x, y)) continue;
            if (right || button.option().equals("menu")) openSearchContextActions(find);
            else {
                var options = find ? finder.options() : session.snapshot().settings().filterOptions();
                if (!button.option().equals("dot-all") || options.mode() == SFMTextMatchOptions.Mode.REGEX)
                    submitSearch(SFMExplorerSearchAction.Kind.TOGGLE, field + " " + button.option());
            }
            revealActivationSound.run();
            return true;
        }
        if (right) openSearchContextActions(find);
        else if (left) {
            // Refocusing within the same Find input must not recapture its frozen anchor.
            if (keyboardFocus != (find ? KeyboardFocus.FIND : KeyboardFocus.FILTER))
                submitSearch(SFMExplorerSearchAction.Kind.FOCUS, field);
            moveFilterCaret(x, Minecraft.getInstance() != null && net.minecraft.client.gui.screens.Screen.hasShiftDown());
            filterInputDragging = true;
        }
        return true;
    }

    private void openSearchContextActions(boolean find) {
        if (panelContext == null || closed) return;
        String field = find ? "find" : "filter";
        var options = find ? finder.options() : session.snapshot().settings().filterOptions();
        boolean highlighted = find ? findHighlight : filterHighlight;
        var choices = new java.util.ArrayList<SFMActionChoice>();
        choices.add(SFMActionChoice.invoke(SFMExplorerSearchAction.Kind.CLEAR.id(), field, value(CLEAR)));
        if (find) {
            choices.add(SFMActionChoice.invoke(SFMExplorerSearchAction.Kind.SELECT.id(), "add-next",
                    value(ADD_NEXT)));
            choices.add(SFMActionChoice.invoke(SFMExplorerSearchAction.Kind.SELECT.id(), "all",
                    value(SELECT_ALL)));
            for (String move : SFMExplorerSearchAction.MOVES)
                choices.add(SFMActionChoice.invoke(SFMExplorerSearchAction.Kind.MOVE.id(), move,
                        value(switch (move) { case "next" -> NEXT; case "previous" -> PREVIOUS; case "next-wrapping" -> NEXT_WRAPPING; default -> PREVIOUS_WRAPPING; })));
            for (String scope : List.of("complete", "materialized"))
                choices.add(SFMActionChoice.invoke(SFMExplorerSearchAction.Kind.SCOPE.id(), scope,
                        value(scope.equals("materialized") ? SCOPE_MATERIALIZED : SCOPE_COMPLETE)));
        }
        for (String option : SFMExplorerSearchAction.OPTIONS) {
            if (option.equals("dot-all") && options.mode() != SFMTextMatchOptions.Mode.REGEX) continue;
            choices.add(SFMActionChoice.invoke(SFMExplorerSearchAction.Kind.TOGGLE.id(), field + " " + option,
                    value(option(option)) + ": " + value(SFMExplorerSearchBar.selected(option, options, highlighted) ? DISABLE : ENABLE)));
        }
        SFMCommandPaletteScreen.openChoices(new SFMClientActionContext(panelContext.host(),
                () -> panelContext != null && !closed, panelContext.panelId()),
                Component.literal(find ? value(FIND_PREFIX) + finder.status() : value(FILTER_PREFIX) + mode(options)), choices);
    }

    private Optional<SFMPanelTooltip> searchTooltipAt(SFMExplorerPanelViewport.Layout layout, double x, double y) {
        for (boolean find : List.of(false, true)) {
            var row = find ? layout.findControl() : layout.filterControl();
            if (!row.contains(x, y)) continue;
            var options = find ? finder.options() : session.snapshot().settings().filterOptions();
            for (var button : SFMExplorerSearchBar.layout(row).buttons()) {
                if (!button.bounds().contains(x, y)) continue;
                if (button.option().equals("menu")) return Optional.of(SFMPanelTooltip.of(Component.literal(
                        value(find ? ACTIONS_FIND : ACTIONS_FILTER))));
                String label = value(option(button.option())) + ": " + value(SFMExplorerSearchBar.selected(button.option(), options,
                        find ? findHighlight : filterHighlight) ? ON : OFF);
                if (button.option().equals("dot-all") && options.mode() != SFMTextMatchOptions.Mode.REGEX)
                    label += " · " + value(REGEX_ONLY);
                return Optional.of(SFMPanelTooltip.of(Component.literal(label + " · sfm:explorer/search/toggle "
                        + (find ? "find " : "filter ") + button.option())));
            }
            return Optional.of(SFMPanelTooltip.of(Component.literal(find ? finder.status()
                    : value(FILTER_HINT, mode(options)))));
        }
        return Optional.empty();
    }

    private void renderSearchBar(PoseStack pose, Minecraft client, SFMExplorerPanelViewport.Rect row,
                                 boolean find, boolean focused) {
        var bar = SFMExplorerSearchBar.layout(row);
        var control = bar.input();
        fill(pose, row, HEADER);
        border(pose, control, focused ? FOCUSED_BORDER : BORDER);
        int y = control.y() + Math.max(1, (control.height() - client.font.lineHeight) / 2);
        String prefix = value(find ? FIND_PREFIX : FILTER_PREFIX);
        int prefixWidth = Math.min(client.font.width(prefix), Math.max(0, control.width() - 8));
        drawTrimmed(pose, client, prefix, control.x() + 4, y, prefixWidth, MUTED);
        (find ? findInputView : filterInputView).render(pose, client.font, find ? findInput : filterInput,
                control.x() + 4 + prefixWidth, y, Math.max(0, control.width() - 8 - prefixWidth), focused,
                value(find ? FIND_PLACEHOLDER : FILTER_PLACEHOLDER), TEXT, MUTED);
        var options = find ? finder.options() : session.snapshot().settings().filterOptions();
        for (var button : bar.buttons()) {
            boolean enabled = !button.option().equals("dot-all") || options.mode() == SFMTextMatchOptions.Mode.REGEX;
            boolean selected = SFMExplorerSearchBar.selected(button.option(), options, find ? findHighlight : filterHighlight);
            fill(pose, button.bounds(), selected && enabled ? SELECTED : HEADER);
            border(pose, button.bounds(), selected && enabled ? FOCUSED_BORDER : BORDER);
            drawTrimmed(pose, client, button.label(), button.bounds().x() + 2, y, 12, enabled ? TEXT : MUTED);
        }
    }

    private void setFilterDraft(String query) {
        filterDraft = Objects.requireNonNull(query, "query");
        filterInput.setText(filterDraft);
        if (filterDraft.isEmpty()) model.emitFilterClear();
        else model.emitFilterSet(filterDraft);
        if (!finder.query().isEmpty()) finder.setQuery(finder.query(), finder.options(), bounds);
    }

    private void moveFilterCaret(double mouseX, boolean extend) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return; // Pure panel models have no font/window; runtime supplies both.
        boolean find = keyboardFocus == KeyboardFocus.FIND;
        var layout = model.state(bounds).viewport().layout();
        var control = SFMExplorerSearchBar.layout(find ? layout.findControl() : layout.filterControl()).input();
        int prefix = client.font.width(value(find ? FIND_PREFIX : FILTER_PREFIX));
        var input = find ? findInput : filterInput;
        var view = find ? findInputView : filterInputView;
        int index = view.indexAt(input, client.font,
                Math.max(0, control.width() - 8 - prefix), mouseX - control.x() - 4 - prefix);
        input.select(extend ? input.anchor() : index, index);
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

    private void drawMatchLabel(PoseStack pose, Minecraft client, String label, int x, int y, int width,
                                int colour, SFMExplorerPanelModel.State state, SFMExplorerPanelViewport.Cell cell) {
        if (width <= 0) return;
        boolean clipped = client.font.width(label) > width;
        int ellipsisWidth = clipped ? client.font.width("...") : 0;
        String visible = clipped ? client.font.plainSubstrByWidth(label, Math.max(0, width - ellipsisWidth)) : label;
        if (!visible.isEmpty() && Character.isHighSurrogate(visible.charAt(visible.length() - 1)))
            visible = visible.substring(0, visible.length() - 1);
        var path = cell.row().path();
        var filterEvidence = state.projection().matchEvidence().get(path);
        var findFragments = findHighlight ? SFMExplorerMatchHighlights.project(label, cell.row(), finder.evidence()::get) : List.<ca.teamdman.sfm.client.search.SFMTextMatcher.Fragment>of();
        var filterFragments = filterHighlight
                ? SFMExplorerMatchHighlights.project(label, cell.row(), alias -> {
                    var evidence = state.projection().matchEvidence().get(alias);
                    return evidence == null ? null : evidence.self();
                }) : List.<ca.teamdman.sfm.client.search.SFMTextMatcher.Fragment>of();
        var theme = ca.teamdman.sfm.client.theme.SFMClientThemeService.active();
        boolean selected = cell.row().paths().stream().anyMatch(state.selectedPaths()::contains);
        int base = selected ? SELECTED
                : cell.bounds().contains(mouseX, mouseY) ? HOVERED
                : theme.colour(ca.teamdman.sfm.client.theme.SFMColourRole.PANEL_BACKGROUND);
        int normal = colour == TEXT ? theme.colour(ca.teamdman.sfm.client.theme.SFMColourRole.TEXT_PRIMARY) : colour == CONTEXT_TEXT ? theme.colour(ca.teamdman.sfm.client.theme.SFMColourRole.TEXT_MUTED) : colour;
        if (selected || cell.bounds().contains(mouseX, mouseY))
            normal = SFMExplorerMatchHighlights.foreground(base);
        for (var run : SFMExplorerMatchHighlights.runs(visible, findFragments, filterFragments)) {
            String text = visible.substring(run.start(), run.end());
            int left = x + client.font.width(visible.substring(0, run.start()));
            int runWidth = client.font.width(text);
            int foreground = normal;
            if (run.membership() != SFMExplorerMatchHighlights.Membership.NONE) {
                int background = SFMExplorerMatchHighlights.background(run.membership(), theme, base);
                fill(pose, new SFMExplorerPanelViewport.Rect(left, y - 1, runWidth, client.font.lineHeight + 1), background);
                foreground = SFMExplorerMatchHighlights.foreground(background);
            }
            SFMFontUtils.draw(pose, client.font, text, left, y, foreground, false);
        }
        if (clipped && ellipsisWidth <= width)
            SFMFontUtils.draw(pose, client.font, "...", x + client.font.width(visible), y, normal, false);
        boolean filterDescendant = filterEvidence != null && filterEvidence.descendantMatch();
        boolean findDescendant = !finder.query().isEmpty() && finder.descendantMatch(path);
        boolean unknown = cell.row().entry().expandable() && ((filterEvidence != null && !filterEvidence.descendantsComplete())
                || (!finder.query().isEmpty() && !finder.complete()));
        if (filterDescendant || findDescendant || unknown) {
            int marker = unknown && !filterDescendant && !findDescendant ? MUTED
                    : theme.colour(filterDescendant && findDescendant ? ca.teamdman.sfm.client.theme.SFMColourRole.SEARCH_INTERSECTION
                    : findDescendant ? ca.teamdman.sfm.client.theme.SFMColourRole.SEARCH_FIND : ca.teamdman.sfm.client.theme.SFMColourRole.SEARCH_FILTER);
            var dot = new SFMExplorerPanelViewport.Rect(Math.max(cell.bounds().x(), x - 4), y + 3, 3, 3);
            if (filterDescendant || findDescendant) fill(pose, dot, marker);
            else border(pose, dot, marker);
        }
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
