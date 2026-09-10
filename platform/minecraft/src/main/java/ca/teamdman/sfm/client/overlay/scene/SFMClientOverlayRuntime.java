package ca.teamdman.sfm.client.overlay.scene;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Bounds;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ContentRecipe;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.InputMode;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayInstanceId;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SceneState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Viewport;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.SFMScissorStack;
import ca.teamdman.sfm.client.screen.history.SFMHistoryGraphPanel;
import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelWidgetHost;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Declarative, selector-addressable host for non-pausing SFM client overlays.
 *
 * <p>The scene owns only presentation identity/state. Content instances retain
 * host-local selection/scroll state while sharing their authoritative domain
 * runtime (for example {@link ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime}).</p>
 */
public final class SFMClientOverlayRuntime {
    private static final int HEADER_HEIGHT = 14;
    private static final int MAX_EVIDENCE_EVENTS = 256;
    private static final int BORDER_PASSIVE = 0xFF587078;
    private static final int BORDER_INTERACTIVE = 0xFF63D7D0;
    private static final int BORDER_FOCUSED = 0xFFFFC35A;
    private static final SFMClientOverlayRuntime INSTANCE = new SFMClientOverlayRuntime();

    @FunctionalInterface
    public interface ContentFactory {
        SFMScreenPanel create(OverlayState state, Consumer<String> actionInvoker);
    }

    public record InputEvidence(
            long sequence,
            long keyConsumed,
            long keyForwarded,
            long characterConsumed,
            long characterForwarded,
            long pointerConsumed,
            long pointerForwarded,
            long scrollConsumed,
            long scrollForwarded,
            List<String> recentEvents
    ) {
        public InputEvidence {
            recentEvents = List.copyOf(recentEvents);
        }
    }

    public record RuntimeSnapshot(
            SceneState scene,
            List<String> hostedOverlayIds,
            Map<String, Bounds> resolvedBounds,
            Map<String, String> contentNarration,
            InputEvidence input,
            long actionCount,
            long directRestoreCount,
            String lastLifecycleReason,
            Map<String, String> lastFeedback
    ) {
        public RuntimeSnapshot {
            hostedOverlayIds = List.copyOf(hostedOverlayIds);
            resolvedBounds = Map.copyOf(resolvedBounds);
            contentNarration = Map.copyOf(contentNarration);
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(lastLifecycleReason, "lastLifecycleReason");
            lastFeedback = Map.copyOf(lastFeedback);
        }
    }

    private final SFMOverlaySceneController controller;
    private final TreeMap<String, ContentFactory> factories = new TreeMap<>();
    private final TreeMap<OverlayInstanceId, HostedOverlay> hosted = new TreeMap<>();
    private final TreeMap<String, String> lastFeedback = new TreeMap<>();
    private final ArrayDeque<String> recentEvents = new ArrayDeque<>();
    private final Set<Integer> heldKeys = new LinkedHashSet<>();
    private final Set<Integer> suppressedKeyReleases = new LinkedHashSet<>();
    private final Set<Integer> heldButtons = new LinkedHashSet<>();
    private final Set<Integer> suppressedButtonReleases = new LinkedHashSet<>();
    private long inputSequence;
    private long keyConsumed;
    private long keyForwarded;
    private long characterConsumed;
    private long characterForwarded;
    private long pointerConsumed;
    private long pointerForwarded;
    private long scrollConsumed;
    private long scrollForwarded;
    private long actionCount;
    private long directRestoreCount;
    private boolean mouseWasGrabbedBeforeFocus;
    private boolean cursorReleasedForOverlay;
    private @Nullable Object observedLevel;
    private double lastMouseX;
    private double lastMouseY;
    private boolean hasMousePosition;
    private int viewportWidth;
    private int viewportHeight;
    private String lastLifecycleReason = "not-started";

    public SFMClientOverlayRuntime() {
        this(SceneState.defaults());
    }

    public SFMClientOverlayRuntime(SceneState initial) {
        controller = new SFMOverlaySceneController(withBuiltInOverlays(Objects.requireNonNull(initial, "initial")));
        registerContentFactory(SFMOverlaySceneContract.HISTORY_RECIPE_ID, (state, invoker) -> {
            SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                    SFMEntitySelector.Domain.EPISODE,
                    state.recipe().argument()
            );
            return new SFMHistoryGraphPanel(selector, invoker);
        });
        registerContentFactory(SFMOverlaySceneContract.DOCUMENT_HISTORY_RECIPE_ID, (state, invoker) ->
                new SFMDocumentHistoryPanel(SFMDocumentHistorySelector.parseCanonical(
                        state.recipe().argument())));
        registerContentFactory(SFMOverlaySceneContract.FPS_RECIPE_ID, (state, invoker) -> new FpsOverlayPanel());
    }

    public static SFMClientOverlayRuntime get() {
        return INSTANCE;
    }

    public synchronized void registerContentFactory(String contentId, ContentFactory factory) {
        Objects.requireNonNull(contentId, "contentId");
        Objects.requireNonNull(factory, "factory");
        if (factories.putIfAbsent(contentId, factory) != null) {
            throw new IllegalArgumentException("Duplicate overlay content factory " + contentId);
        }
    }

    public synchronized List<String> registeredContentIds() {
        return List.copyOf(factories.keySet());
    }

    public synchronized SceneState scene() {
        return controller.snapshot();
    }

    public synchronized List<String> overlayIds() {
        return scene().overlays().stream().map(overlay -> overlay.id().value()).toList();
    }

    public synchronized String encodeCanonical() {
        return controller.encodeCanonical();
    }

    public synchronized void restoreCanonical(String canonicalJson) {
        restore(SFMOverlaySceneJsonCodec.read(canonicalJson));
    }

    /** Direct state restoration; no historical visibility/placement actions are replayed. */
    public synchronized void restore(SceneState restored) {
        Minecraft minecraft = Minecraft.getInstance();
        Optional<OverlayInstanceId> beforeFocus = scene().focusedOverlay();
        if (beforeFocus.isPresent()) releaseCursor(minecraft, false, "direct-restore");
        SceneState migrated = withBuiltInOverlays(Objects.requireNonNull(restored, "restored"));
        controller.restore(migrated);
        directRestoreCount++;
        reconcileHosted(minecraft, false);
        synchronizeFocus(minecraft, false, "direct-restore");
        event("scene restored directly at revision " + migrated.revision());
    }

    private static SceneState withBuiltInOverlays(SceneState source) {
        ArrayList<OverlayState> overlays = new ArrayList<>(source.overlays());
        for (OverlayState builtIn : SceneState.defaults().overlays()) {
            boolean present = overlays.stream().anyMatch(existing -> existing.id().equals(builtIn.id()));
            if (!present) overlays.add(builtIn);
        }
        if (overlays.size() == source.overlays().size()) return source;
        return new SceneState(source.schema(), source.revision(), overlays, source.focusedOverlay());
    }

    public synchronized SFMOverlaySceneController.BatchResult execute(
            SFMEntitySelector selector,
            SFMOverlaySceneController.Operation operation
    ) {
        Optional<OverlayInstanceId> beforeFocus = scene().focusedOverlay();
        SFMOverlaySceneController.BatchResult result = controller.execute(selector, operation);
        actionCount++;
        Optional<OverlayInstanceId> afterFocus = scene().focusedOverlay();
        Minecraft minecraft = Minecraft.getInstance();
        if (!beforeFocus.equals(afterFocus)) {
            if (beforeFocus.isPresent()) releaseCursor(minecraft, true, "scene-action-release");
            if (afterFocus.isPresent()) acquireCursor(minecraft, afterFocus.orElseThrow());
        }
        result.targets().forEach(target -> event(
                "action " + operation.getClass().getSimpleName() + " " + target.id() + " " + target.status()));
        return result;
    }

    public synchronized RuntimeSnapshot snapshot() {
        TreeMap<String, Bounds> bounds = new TreeMap<>();
        TreeMap<String, String> narration = new TreeMap<>();
        hosted.forEach((id, value) -> {
            if (value.bounds != null) bounds.put(id.value(), value.bounds);
            narration.put(id.value(), value.panel.narration().getString());
        });
        return new RuntimeSnapshot(
                scene(),
                hosted.keySet().stream().map(OverlayInstanceId::value).toList(),
                bounds,
                narration,
                inputEvidence(),
                actionCount,
                directRestoreCount,
                lastLifecycleReason,
                lastFeedback
        );
    }

    public synchronized InputEvidence inputEvidence() {
        return new InputEvidence(
                inputSequence,
                keyConsumed,
                keyForwarded,
                characterConsumed,
                characterForwarded,
                pointerConsumed,
                pointerForwarded,
                scrollConsumed,
                scrollForwarded,
                List.copyOf(recentEvents)
        );
    }

    public synchronized void tick(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        reconcileLifecycle(minecraft);
        if (minecraft.level == null) return;
        reconcileHosted(minecraft, true);
        SceneState scene = scene();
        for (OverlayState state : scene.overlays()) {
            if (!state.visible()) continue;
            HostedOverlay value = hosted.get(state.id());
            if (value != null) value.tick(scene.focusedOverlay().filter(state.id()::equals).isPresent());
        }
    }

    public synchronized void render(
            PoseStack poseStack,
            Minecraft minecraft,
            int screenWidth,
            int screenHeight,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (minecraft.level == null) return;
        viewportWidth = Math.max(0, screenWidth);
        viewportHeight = Math.max(0, screenHeight);
        reconcileHosted(minecraft, true);
        SceneState scene = scene();
        List<OverlayState> visible = scene.overlays().stream()
                .filter(OverlayState::visible)
                .sorted(Comparator.comparingInt(OverlayState::zOrder).thenComparing(OverlayState::id))
                .toList();
        for (OverlayState state : visible) {
            HostedOverlay value = hosted.get(state.id());
            if (value == null) continue;
            Bounds bounds = state.placement().resolve(
                    new Viewport(0, 0, screenWidth, screenHeight),
                    SFMOverlaySceneContract.DEFAULT_CONTENT_WIDTH,
                    SFMOverlaySceneContract.DEFAULT_CONTENT_HEIGHT
            );
            value.ensureBounds(minecraft, bounds);
            boolean focused = scene.focusedOverlay().filter(state.id()::equals).isPresent();
            value.render(poseStack, minecraft, state, mouseX, mouseY, partialTick, focused);
        }
    }

    /** Called at the raw {@code KeyboardHandler.keyPress} ingress. */
    public synchronized boolean key(long window, int keyCode, int scanCode, int action, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()) return false;
        if (action == GLFW.GLFW_RELEASE && suppressedKeyReleases.remove(keyCode)) {
            keyConsumed++;
            event("suppressed stale key release " + keyCode);
            return true;
        }
        HostedOverlay target = focusedTarget(minecraft);
        if (target == null) {
            keyForwarded++;
            event("key forwarded " + keyCode + "/" + action);
            return false;
        }
        if (action == GLFW.GLFW_PRESS) heldKeys.add(keyCode);
        if (action == GLFW.GLFW_RELEASE) heldKeys.remove(keyCode);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
            releaseFocused("escape", true);
            keyConsumed++;
            event("escape released overlay focus");
            return true;
        }
        if (action == GLFW.GLFW_RELEASE) target.keyReleased(keyCode, scanCode, modifiers);
        else target.keyPressed(keyCode, scanCode, modifiers);
        keyConsumed++;
        event("key consumed " + keyCode + "/" + action);
        return true;
    }

    /** Called at the raw {@code KeyboardHandler.charTyped} ingress. */
    public synchronized boolean character(long window, int codePoint, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()) return false;
        HostedOverlay target = focusedTarget(minecraft);
        if (target == null) {
            characterForwarded++;
            event("character forwarded U+" + Integer.toHexString(codePoint));
            return false;
        }
        for (char character : Character.toChars(codePoint)) target.charTyped(character, modifiers);
        characterConsumed++;
        event("character consumed U+" + Integer.toHexString(codePoint));
        return true;
    }

    /** Called before Vanilla mutates mouse-button gameplay state. */
    public synchronized boolean mouseButton(long window, int button, int action, int modifiers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()) return false;
        if (action == GLFW.GLFW_RELEASE && suppressedButtonReleases.remove(button)) {
            pointerConsumed++;
            event("suppressed stale button release " + button);
            return true;
        }
        HostedOverlay target = focusedTarget(minecraft);
        if (target == null) {
            pointerForwarded++;
            event("button forwarded " + button + "/" + action);
            return false;
        }
        double[] point = currentGuiPointer(minecraft);
        if (action == GLFW.GLFW_PRESS) {
            if (isTopInteractiveAt(target, minecraft, point[0], point[1])) {
                heldButtons.add(button);
                target.mouseClicked(point[0], point[1], button);
                event("button routed " + button + "/" + action + " to " + target.id);
            } else {
                event("button consumed outside focused top overlay " + button + "/" + action);
            }
        } else {
            boolean captured = heldButtons.remove(button);
            if (captured) {
                target.mouseReleased(point[0], point[1], button);
                event("captured button release routed " + button + " to " + target.id);
            } else {
                event("uncaptured button release consumed " + button);
            }
        }
        pointerConsumed++;
        return true;
    }

    /** Called before Vanilla accumulates or applies gameplay scroll. */
    public synchronized boolean mouseScroll(long window, double horizontal, double vertical) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()) return false;
        HostedOverlay target = focusedTarget(minecraft);
        if (target == null) {
            scrollForwarded++;
            event("scroll forwarded " + vertical);
            return false;
        }
        double[] point = currentGuiPointer(minecraft);
        if (isTopInteractiveAt(target, minecraft, point[0], point[1])) {
            target.mouseScrolled(point[0], point[1], vertical == 0.0D ? horizontal : vertical);
            event("scroll routed " + vertical + " to " + target.id);
        } else {
            event("scroll consumed outside focused top overlay " + vertical);
        }
        scrollConsumed++;
        return true;
    }

    /** Observes pointer motion while Vanilla keeps its raw coordinates current. */
    public synchronized void mouseMoved(long window, double rawX, double rawY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (window != minecraft.getWindow().getWindow()) return;
        HostedOverlay target = focusedTarget(minecraft);
        if (target == null) {
            pointerForwarded++;
            return;
        }
        double guiX = rawX * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
        double guiY = rawY * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
        boolean captured = !heldButtons.isEmpty();
        if (!captured && !isTopInteractiveAt(target, minecraft, guiX, guiY)) {
            pointerConsumed++;
            return;
        }
        double dragX = hasMousePosition ? guiX - lastMouseX : 0.0D;
        double dragY = hasMousePosition ? guiY - lastMouseY : 0.0D;
        target.mouseMoved(guiX, guiY);
        for (int button : List.copyOf(heldButtons)) target.mouseDragged(guiX, guiY, button, dragX, dragY);
        lastMouseX = guiX;
        lastMouseY = guiY;
        hasMousePosition = true;
        pointerConsumed++;
    }

    public synchronized void releaseFocused(String reason, boolean restoreGameplayCursor) {
        Optional<OverlayInstanceId> focus = scene().focusedOverlay();
        if (focus.isEmpty()) return;
        controller.execute(SFMEntitySelector.exact(SFMEntitySelector.Domain.OVERLAY, focus.orElseThrow().value()),
                new SFMOverlaySceneController.Release());
        releaseCursor(Minecraft.getInstance(), restoreGameplayCursor, reason);
        event("focus released: " + reason);
    }

    public synchronized void closeForWorldUnload(String reason) {
        releaseFocused(reason, false);
        closeAll();
        observedLevel = null;
        lastLifecycleReason = reason;
    }

    private void reconcileLifecycle(Minecraft minecraft) {
        Object currentLevel = minecraft.level;
        if (observedLevel != currentLevel) {
            if (observedLevel != null) closeForWorldUnload("world-changed");
            observedLevel = currentLevel;
            if (currentLevel != null) event("world overlay host attached");
        }
        if (scene().focusedOverlay().isPresent()) {
            if (minecraft.level == null || minecraft.player == null) releaseFocused("world-unavailable", false);
            else if (!minecraft.isWindowActive()) releaseFocused("window-focus-lost", false);
            else if (minecraft.screen != null) releaseFocused("screen-opened", false);
            else if (minecraft.getOverlay() != null) releaseFocused("modal-overlay-opened", false);
            else synchronizeFocus(minecraft, false, "tick-reconcile");
        }
    }

    private void synchronizeFocus(Minecraft minecraft, boolean restoreGameplayCursor, String reason) {
        Optional<OverlayInstanceId> focus = scene().focusedOverlay();
        if (focus.isPresent() && !cursorReleasedForOverlay) acquireCursor(minecraft, focus.orElseThrow());
        if (focus.isEmpty() && cursorReleasedForOverlay) releaseCursor(minecraft, restoreGameplayCursor, reason);
    }

    private void acquireCursor(Minecraft minecraft, OverlayInstanceId id) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || minecraft.getOverlay() != null || !minecraft.isWindowActive()) {
            releaseFocused("focus-precondition-lost", false);
            return;
        }
        mouseWasGrabbedBeforeFocus = minecraft.mouseHandler.isMouseGrabbed();
        KeyMapping.releaseAll();
        minecraft.mouseHandler.releaseMouse();
        cursorReleasedForOverlay = true;
        hasMousePosition = false;
        hosted.values().forEach(value -> value.setActive(value.id.equals(id)));
        event("cursor acquired by " + id);
    }

    private void releaseCursor(Minecraft minecraft, boolean restoreGameplayCursor, String reason) {
        suppressedKeyReleases.addAll(heldKeys);
        suppressedButtonReleases.addAll(heldButtons);
        heldKeys.clear();
        heldButtons.clear();
        KeyMapping.releaseAll();
        hosted.values().forEach(value -> value.setActive(false));
        boolean shouldGrab = cursorReleasedForOverlay
                && mouseWasGrabbedBeforeFocus
                && restoreGameplayCursor
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.screen == null
                && minecraft.getOverlay() == null
                && minecraft.isWindowActive();
        cursorReleasedForOverlay = false;
        mouseWasGrabbedBeforeFocus = false;
        hasMousePosition = false;
        if (shouldGrab) minecraft.mouseHandler.grabMouse();
        lastLifecycleReason = reason;
    }

    private HostedOverlay focusedTarget(Minecraft minecraft) {
        Optional<OverlayInstanceId> focused = scene().focusedOverlay();
        if (focused.isEmpty()) return null;
        OverlayState state = scene().overlay(focused.orElseThrow()).orElse(null);
        if (state == null || !state.visible() || state.inputMode() != InputMode.INTERACTIVE) {
            releaseFocused("stale-focused-overlay", false);
            return null;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null
                || minecraft.getOverlay() != null || !minecraft.isWindowActive()) {
            releaseFocused("input-lifecycle-invalid", false);
            return null;
        }
        reconcileHosted(minecraft, true);
        HostedOverlay target = hosted.get(state.id());
        if (target == null) {
            releaseFocused("focused-content-unavailable", false);
            return null;
        }
        target.setActive(true);
        return target;
    }

    private boolean isTopInteractiveAt(
            HostedOverlay focused,
            Minecraft minecraft,
            double guiX,
            double guiY
    ) {
        int width = viewportWidth > 0 ? viewportWidth : minecraft.getWindow().getGuiScaledWidth();
        int height = viewportHeight > 0 ? viewportHeight : minecraft.getWindow().getGuiScaledHeight();
        return controller.topInteractiveAt(guiX, guiY, width, height)
                .map(state -> state.id().equals(focused.id))
                .orElse(false);
    }

    private void reconcileHosted(Minecraft minecraft, boolean openVisible) {
        SceneState scene = scene();
        Set<OverlayInstanceId> available = new LinkedHashSet<>();
        for (OverlayState state : scene.overlays()) {
            available.add(state.id());
            HostedOverlay current = hosted.get(state.id());
            if (current != null && current.recipe.equals(state.recipe())) continue;
            if (current != null) current.close();
            HostedOverlay replacement = createHosted(state);
            hosted.put(state.id(), replacement);
        }
        List<OverlayInstanceId> stale = hosted.keySet().stream().filter(id -> !available.contains(id)).toList();
        stale.forEach(id -> {
            HostedOverlay removed = hosted.remove(id);
            if (removed != null) removed.close();
        });
        if (openVisible && minecraft.level != null) {
            for (OverlayState state : scene.overlays()) {
                if (!state.visible()) continue;
                HostedOverlay value = hosted.get(state.id());
                if (value == null) continue;
                Bounds bounds = state.placement().resolve(
                        new Viewport(0, 0, viewportWidth > 0 ? viewportWidth : minecraft.getWindow().getGuiScaledWidth(),
                                viewportHeight > 0 ? viewportHeight : minecraft.getWindow().getGuiScaledHeight()),
                        SFMOverlaySceneContract.DEFAULT_CONTENT_WIDTH,
                        SFMOverlaySceneContract.DEFAULT_CONTENT_HEIGHT
                );
                value.ensureBounds(minecraft, bounds);
            }
        }
    }

    private HostedOverlay createHosted(OverlayState state) {
        ContentFactory factory = factories.get(state.recipe().contentId());
        SFMScreenPanel panel;
        if (factory == null) {
            panel = new UnknownContentPanel(state.recipe());
        } else {
            try {
                panel = Objects.requireNonNull(factory.create(state, command -> invokePanelAction(state.id(), command)),
                        "overlay content");
            } catch (RuntimeException failure) {
                SFM.LOGGER.warn("SFM_OVERLAY_CONTENT_CREATE_FAILED id={} recipe={}", state.id(),
                        state.recipe().contentId(), failure);
                panel = new UnknownContentPanel(state.recipe(), failureMessage(failure));
            }
        }
        return new HostedOverlay(state.id(), state.recipe(), panel);
    }

    private void invokePanelAction(OverlayInstanceId id, String command) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            SFMClientActionContext context = SFMClientActionContext.create(
                    this,
                    () -> minecraft.level != null && scene().overlay(id).filter(OverlayState::visible).isPresent()
            );
            SFMClientActionExecutor.execute(command, context,
                    feedback -> lastFeedback.put(id.value(), feedback.getString()));
        } catch (CommandSyntaxException | RuntimeException failure) {
            String message = failureMessage(failure);
            lastFeedback.put(id.value(), message);
            SFM.LOGGER.warn("SFM_OVERLAY_PANEL_ACTION_FAILED id={} command={}", id, command, failure);
        }
    }

    private void closeAll() {
        hosted.values().forEach(HostedOverlay::close);
        hosted.clear();
    }

    private double[] currentGuiPointer(Minecraft minecraft) {
        return new double[]{
                minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth()
                        / minecraft.getWindow().getScreenWidth(),
                minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight()
                        / minecraft.getWindow().getScreenHeight()
        };
    }

    private void event(String text) {
        inputSequence++;
        if (recentEvents.size() >= MAX_EVIDENCE_EVENTS) recentEvents.removeFirst();
        recentEvents.addLast(inputSequence + ":" + text);
    }

    private static String failureMessage(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private final class HostedOverlay {
        private final OverlayInstanceId id;
        private final ContentRecipe recipe;
        private final SFMScreenPanel panel;
        private @Nullable Bounds bounds;
        private boolean opened;

        private HostedOverlay(OverlayInstanceId id, ContentRecipe recipe, SFMScreenPanel panel) {
            this.id = id;
            this.recipe = recipe;
            this.panel = panel;
        }

        private void ensureBounds(Minecraft minecraft, Bounds next) {
            if (next.width() <= 0 || next.height() <= HEADER_HEIGHT) return;
            Bounds content = contentBounds(next);
            SFMScreenPanelBounds logical = logicalBounds(content);
            if (!opened) {
                panel.opened(minecraft, logical, SFMWorkspacePanelContext.unhosted(panelId(id)));
                opened = true;
            } else if (!next.equals(bounds)) {
                panel.resized(minecraft, logical);
            }
            bounds = next;
        }

        private void tick(boolean active) {
            setActive(active);
            if (opened) panel.tick();
        }

        private void setActive(boolean active) {
            panel.widgetHost().ifPresent(host -> host.setActive(active));
        }

        private void render(
                PoseStack poseStack,
                Minecraft minecraft,
                OverlayState state,
                int mouseX,
                int mouseY,
                float partialTick,
                boolean focused
        ) {
            if (!opened || bounds == null) return;
            int colour = focused ? BORDER_FOCUSED
                    : state.inputMode() == InputMode.INTERACTIVE ? BORDER_INTERACTIVE : BORDER_PASSIVE;
            GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, colour);
            GuiComponent.fill(poseStack, bounds.x(), bounds.y() + bounds.height() - 1,
                    bounds.x() + bounds.width(), bounds.y() + bounds.height(), colour);
            GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), colour);
            GuiComponent.fill(poseStack, bounds.x() + bounds.width() - 1, bounds.y(),
                    bounds.x() + bounds.width(), bounds.y() + bounds.height(), colour);
            GuiComponent.fill(poseStack, bounds.x() + 1, bounds.y() + 1,
                    bounds.x() + bounds.width() - 1, bounds.y() + HEADER_HEIGHT, 0xE0181C22);
            String header = id.value() + " · " + state.inputMode().wire()
                    + (focused ? " · focused" : "") + " · z=" + state.zOrder()
                    + " · " + state.placement().canonical();
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(header, Math.max(0, bounds.width() - 8)),
                    bounds.x() + 4, bounds.y() + 3, colour, false);

            Bounds content = contentBounds(bounds);
            int localMouseX = (int) Math.floor(mouseX - content.x());
            int localMouseY = (int) Math.floor(mouseY - content.y());
            SFMScissorStack.pushGui(content.x(), content.y(), content.x() + content.width(),
                    content.y() + content.height());
            poseStack.pushPose();
            poseStack.translate(content.x(), content.y(), 0.0D);
            try {
                SFMScreenPanelBounds logical = logicalBounds(content);
                panel.render(poseStack, minecraft, logical, localMouseX, localMouseY, partialTick, focused);
                panel.widgetHost().ifPresent(host -> host.render(poseStack, localMouseX, localMouseY, partialTick));
            } finally {
                poseStack.popPose();
                SFMScissorStack.pop();
            }
        }

        private boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!opened) return false;
            if (panel.widgetHost().map(host -> host.keyPressed(keyCode, scanCode, modifiers)).orElse(false)) return true;
            return !panel.widgetHostOwnsInput() && panel.keyPressed(keyCode, scanCode, modifiers);
        }

        private boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            if (!opened) return false;
            if (panel.widgetHost().map(host -> host.keyReleased(keyCode, scanCode, modifiers)).orElse(false)) return true;
            return !panel.widgetHostOwnsInput() && panel.keyReleased(keyCode, scanCode, modifiers);
        }

        private boolean charTyped(char character, int modifiers) {
            if (!opened) return false;
            if (panel.widgetHost().map(host -> host.charTyped(character, modifiers)).orElse(false)) return true;
            return !panel.widgetHostOwnsInput() && panel.charTyped(character, modifiers);
        }

        private boolean mouseClicked(double x, double y, int button) {
            if (!inside(x, y)) return false;
            double[] local = local(x, y);
            if (panel.widgetHost().map(host -> host.mouseClicked(local[0], local[1], button)).orElse(false)) return true;
            return !panel.widgetHostOwnsInput() && panel.mouseClicked(local[0], local[1], button);
        }

        private boolean mouseReleased(double x, double y, int button) {
            if (!opened || bounds == null) return false;
            double[] local = local(x, y);
            if (panel.widgetHost().map(host -> host.mouseReleased(local[0], local[1], button)).orElse(false)) return true;
            return !panel.widgetHostOwnsInput() && panel.mouseReleased(local[0], local[1], button);
        }

        private void mouseMoved(double x, double y) {
            if (!inside(x, y)) return;
            double[] local = local(x, y);
            panel.widgetHost().ifPresent(host -> host.mouseMoved(local[0], local[1]));
            if (!panel.widgetHostOwnsInput()) panel.mouseMoved(local[0], local[1]);
        }

        private boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
            if (!opened || bounds == null) return false;
            double[] local = local(x, y);
            if (panel.widgetHost().map(host -> host.mouseDragged(local[0], local[1], button, dragX, dragY))
                    .orElse(false)) return true;
            return !panel.widgetHostOwnsInput() && panel.mouseDragged(local[0], local[1], button, dragX, dragY);
        }

        private boolean mouseScrolled(double x, double y, double delta) {
            if (!inside(x, y)) return false;
            double[] local = local(x, y);
            if (panel.widgetHost().map(host -> host.mouseScrolled(local[0], local[1], delta)).orElse(false)) return true;
            return !panel.widgetHostOwnsInput() && panel.mouseScrolled(local[0], local[1], delta);
        }

        private boolean inside(double x, double y) {
            return opened && bounds != null && contentBounds(bounds).contains(x, y);
        }

        private double[] local(double x, double y) {
            if (bounds == null) return new double[]{0.0D, 0.0D};
            Bounds content = contentBounds(bounds);
            return new double[]{x - content.x(), y - content.y()};
        }

        private void close() {
            if (!opened) return;
            panel.widgetHost().ifPresent(SFMPanelWidgetHost::closed);
            panel.closed();
            opened = false;
            bounds = null;
        }
    }

    private static Bounds contentBounds(Bounds outer) {
        return new Bounds(
                outer.x() + 1,
                outer.y() + HEADER_HEIGHT,
                Math.max(0, outer.width() - 2),
                Math.max(0, outer.height() - HEADER_HEIGHT - 1)
        );
    }

    private static SFMScreenPanelBounds logicalBounds(Bounds content) {
        return new SFMScreenPanelBounds(0, 0, content.width(), content.height());
    }

    private static SFMWorkspacePanelId panelId(OverlayInstanceId id) {
        return new SFMWorkspacePanelId(Integer.toUnsignedLong(id.value().hashCode()));
    }

    private static final class FpsOverlayPanel implements SFMScreenPanel {
        @Override
        public Component title() {
            return Component.literal("FPS");
        }

        @Override
        public Component narration() {
            Minecraft minecraft = Minecraft.getInstance();
            return Component.literal(minecraft == null ? "FPS unavailable" : minecraft.fpsString);
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
            GuiComponent.fill(poseStack, 0, 0, bounds.width(), bounds.height(), 0xD0101418);
            String fps = minecraft.fpsString == null || minecraft.fpsString.isBlank()
                    ? "FPS pending"
                    : minecraft.fpsString;
            SFMFontUtils.draw(
                    poseStack,
                    minecraft.font,
                    minecraft.font.plainSubstrByWidth(fps, Math.max(0, bounds.width() - 12)),
                    6,
                    Math.max(4, (bounds.height() - minecraft.font.lineHeight) / 2),
                    0xFFB7F7C8,
                    true
            );
        }
    }

    private static final class UnknownContentPanel implements SFMScreenPanel {
        private final ContentRecipe recipe;
        private final String reason;

        private UnknownContentPanel(ContentRecipe recipe) {
            this(recipe, "No content factory is registered");
        }

        private UnknownContentPanel(ContentRecipe recipe, String reason) {
            this.recipe = recipe;
            this.reason = reason;
        }

        @Override
        public Component title() {
            return Component.literal("Unknown overlay content");
        }

        @Override
        public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                           int mouseX, int mouseY, float partialTick, boolean focused) {
            GuiComponent.fill(poseStack, 0, 0, bounds.width(), bounds.height(), 0xE0181C22);
            SFMFontUtils.draw(poseStack, minecraft.font, "Unknown overlay content", 6, 6, 0xFFFF7373, false);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(recipe.contentId(), Math.max(0, bounds.width() - 12)),
                    6, 18, 0xFFEDF6F7, false);
            SFMFontUtils.draw(poseStack, minecraft.font,
                    minecraft.font.plainSubstrByWidth(reason, Math.max(0, bounds.width() - 12)),
                    6, 30, 0xFF93A7AC, false);
        }
    }
}
