package ca.teamdman.sfm.client.overlay.scene;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMEntitySelectorResolver;
import ca.teamdman.sfm.client.explorer.SFMSelectorDomain;
import ca.teamdman.sfm.client.explorer.SFMSelectorRepository;
import ca.teamdman.sfm.client.explorer.SFMSelectorRepositoryEntry;
import ca.teamdman.sfm.client.explorer.SFMSelectorRepositorySnapshot;
import ca.teamdman.sfm.client.explorer.SFMSelectorResolution;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Bounds;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.InputMode;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayInstanceId;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Placement;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SceneState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure mutation and selector boundary for one declarative client scene. */
public final class SFMOverlaySceneController {
    public sealed interface Operation permits SetVisibility, ToggleVisibility, SetPlacement, SetInputMode,
            Focus, Release, SetZOrder {
    }

    public record SetVisibility(boolean visible) implements Operation {
    }

    public record ToggleVisibility() implements Operation {
    }

    public record SetPlacement(Placement placement) implements Operation {
        public SetPlacement {
            Objects.requireNonNull(placement, "placement");
        }
    }

    public record SetInputMode(InputMode inputMode) implements Operation {
        public SetInputMode {
            Objects.requireNonNull(inputMode, "inputMode");
        }
    }

    public record Focus() implements Operation {
    }

    public record Release() implements Operation {
    }

    public record SetZOrder(int zOrder) implements Operation {
        public SetZOrder {
            if (Math.abs((long) zOrder) > 1_000_000L) {
                throw new IllegalArgumentException("Overlay z-order is outside the bounded range");
            }
        }
    }

    public enum Status {
        APPLIED,
        NO_CHANGE,
        REJECTED
    }

    public record TargetResult(OverlayInstanceId id, Status status, String message) {
        public TargetResult {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(message, "message");
        }
    }

    public record BatchResult(
            String selector,
            long beforeRevision,
            long afterRevision,
            List<TargetResult> targets,
            List<String> diagnostics
    ) {
        public BatchResult {
            Objects.requireNonNull(selector, "selector");
            targets = List.copyOf(targets);
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean appliedAny() {
            return targets.stream().anyMatch(target -> target.status() == Status.APPLIED);
        }
    }

    private SceneState state;

    public SFMOverlaySceneController() {
        this(SceneState.defaults());
    }

    public SFMOverlaySceneController(SceneState initial) {
        state = Objects.requireNonNull(initial, "initial");
    }

    public synchronized SceneState snapshot() {
        return state;
    }

    /** Replaces state directly; no historical layout action is replayed. */
    public synchronized void restore(SceneState restored) {
        state = Objects.requireNonNull(restored, "restored");
    }

    public synchronized void restoreCanonical(String canonicalJson) {
        restore(SFMOverlaySceneJsonCodec.read(canonicalJson));
    }

    public synchronized String encodeCanonical() {
        return SFMOverlaySceneJsonCodec.write(state);
    }

    public synchronized SFMSelectorResolution<OverlayInstanceId> resolve(SFMEntitySelector selector) {
        return SFMEntitySelectorResolver.resolve(selector, selectorDomain(state));
    }

    public synchronized BatchResult execute(SFMEntitySelector selector, Operation operation) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(operation, "operation");
        long before = state.revision();
        SFMSelectorResolution<OverlayInstanceId> resolution = resolve(selector);
        ArrayList<String> diagnostics = new ArrayList<>();
        resolution.diagnostics().forEach(diagnostic -> diagnostics.add(diagnostic.code() + ": " + diagnostic.message()));
        if (!resolution.complete()) {
            return new BatchResult(selector.canonical(), before, state.revision(), List.of(), diagnostics);
        }
        if (operation instanceof Focus && resolution.identities().size() != 1) {
            diagnostics.add("overlay.focus-cardinality: focus requires exactly one matched overlay");
            return new BatchResult(selector.canonical(), before, state.revision(), List.of(), diagnostics);
        }

        ArrayList<TargetResult> results = new ArrayList<>();
        for (OverlayInstanceId id : resolution.identities()) {
            OverlayState overlay = state.overlay(id).orElse(null);
            if (overlay == null) {
                results.add(new TargetResult(id, Status.REJECTED, "Overlay became stale before mutation"));
                continue;
            }
            results.add(apply(overlay, operation));
        }
        return new BatchResult(selector.canonical(), before, state.revision(), results, diagnostics);
    }

    public synchronized Optional<OverlayState> topInteractiveAt(
            double mouseX,
            double mouseY,
            int viewportWidth,
            int viewportHeight
    ) {
        return state.overlays().stream()
                .filter(OverlayState::visible)
                .filter(overlay -> overlay.inputMode() == InputMode.INTERACTIVE)
                .filter(overlay -> bounds(overlay, viewportWidth, viewportHeight).contains(mouseX, mouseY))
                .max(Comparator.comparingInt(OverlayState::zOrder).thenComparing(OverlayState::id));
    }

    public static Bounds bounds(OverlayState overlay, int viewportWidth, int viewportHeight) {
        return overlay.placement().resolve(
                viewportWidth,
                viewportHeight,
                SFMOverlaySceneContract.DEFAULT_CONTENT_WIDTH,
                SFMOverlaySceneContract.DEFAULT_CONTENT_HEIGHT
        );
    }

    private TargetResult apply(OverlayState overlay, Operation operation) {
        if (operation instanceof SetVisibility visibility) {
            if (overlay.visible() == visibility.visible()) return noChange(overlay, "Visibility already " + visibility.visible());
            Optional<OverlayInstanceId> focus = visibility.visible()
                    ? state.focusedOverlay()
                    : withoutFocus(overlay.id());
            state = state.replace(overlay.withVisible(visibility.visible()), focus);
            return applied(overlay, "Visibility set to " + visibility.visible());
        }
        if (operation instanceof ToggleVisibility) {
            boolean next = !overlay.visible();
            state = state.replace(overlay.withVisible(next), next ? state.focusedOverlay() : withoutFocus(overlay.id()));
            return applied(overlay, "Visibility toggled to " + next);
        }
        if (operation instanceof SetPlacement placement) {
            if (overlay.placement().equals(placement.placement())) return noChange(overlay, "Placement already matches");
            state = state.replace(overlay.withPlacement(placement.placement()), state.focusedOverlay());
            return applied(overlay, "Placement set to " + placement.placement().canonical());
        }
        if (operation instanceof SetInputMode mode) {
            if (overlay.inputMode() == mode.inputMode()) return noChange(overlay, "Input mode already " + mode.inputMode().wire());
            Optional<OverlayInstanceId> focus = mode.inputMode() == InputMode.INTERACTIVE
                    ? state.focusedOverlay()
                    : withoutFocus(overlay.id());
            state = state.replace(overlay.withInputMode(mode.inputMode()), focus);
            return applied(overlay, "Input mode set to " + mode.inputMode().wire());
        }
        if (operation instanceof Focus) {
            if (!overlay.visible()) return rejected(overlay, "Cannot focus a hidden overlay");
            if (overlay.inputMode() != InputMode.INTERACTIVE) {
                return rejected(overlay, "Cannot focus an overlay in passive mode");
            }
            if (state.focusedOverlay().filter(overlay.id()::equals).isPresent()) {
                return noChange(overlay, "Overlay is already focused");
            }
            state = state.withFocus(Optional.of(overlay.id()));
            return applied(overlay, "Overlay focused");
        }
        if (operation instanceof Release) {
            if (state.focusedOverlay().filter(overlay.id()::equals).isEmpty()) {
                return noChange(overlay, "Overlay did not own focus");
            }
            state = state.withFocus(Optional.empty());
            return applied(overlay, "Overlay focus released");
        }
        if (operation instanceof SetZOrder zOrder) {
            if (overlay.zOrder() == zOrder.zOrder()) return noChange(overlay, "Z-order already " + zOrder.zOrder());
            state = state.replace(overlay.withZOrder(zOrder.zOrder()), state.focusedOverlay());
            return applied(overlay, "Z-order set to " + zOrder.zOrder());
        }
        throw new AssertionError("Unsupported overlay operation " + operation);
    }

    private Optional<OverlayInstanceId> withoutFocus(OverlayInstanceId id) {
        return state.focusedOverlay().filter(focused -> !focused.equals(id));
    }

    private static TargetResult applied(OverlayState overlay, String message) {
        return new TargetResult(overlay.id(), Status.APPLIED, message);
    }

    private static TargetResult noChange(OverlayState overlay, String message) {
        return new TargetResult(overlay.id(), Status.NO_CHANGE, message);
    }

    private static TargetResult rejected(OverlayState overlay, String message) {
        return new TargetResult(overlay.id(), Status.REJECTED, message);
    }

    private static SFMSelectorDomain<OverlayInstanceId> selectorDomain(SceneState scene) {
        List<SFMSelectorRepositoryEntry<OverlayInstanceId>> entries = scene.overlays().stream()
                .map(overlay -> SFMSelectorRepositoryEntry.unnamed(
                        overlay.id(),
                        scene.focusedOverlay().filter(overlay.id()::equals).isPresent()
                ))
                .toList();
        SFMSelectorRepositorySnapshot<OverlayInstanceId> snapshot = new SFMSelectorRepositorySnapshot<>(
                scene.revision(),
                entries
        );
        return new SFMSelectorDomain<>() {
            @Override public SFMEntitySelector.Domain domain() { return SFMEntitySelector.Domain.OVERLAY; }
            @Override public SFMSelectorRepository<OverlayInstanceId> repository() {
                return SFMSelectorRepository.immutable(snapshot);
            }
            @Override public String stableText(OverlayInstanceId id) { return id.value(); }
            @Override public boolean supportsFocus() { return true; }
            @Override public boolean supportsNames() { return false; }
            @Override public boolean supportsResolution() { return true; }
            @Override public Optional<SFMSelectorResolution.Diagnostic> unsupportedDiagnostic() {
                return Optional.empty();
            }
        };
    }
}
