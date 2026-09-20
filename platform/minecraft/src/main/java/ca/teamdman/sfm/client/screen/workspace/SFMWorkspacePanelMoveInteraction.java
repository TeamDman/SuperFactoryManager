package ca.teamdman.sfm.client.screen.workspace;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit middle-button panel-management gesture.
 *
 * <p>Ordinary middle input belongs to panel content (for example Text Editor
 * v3 panning). Workspace capture begins only from a numbered panel-entry
 * affordance or from content while the caller reports the explicit move
 * modifier. A captured click opens the exact-entry action surface; a captured
 * drag moves that entry into the pane under the release point.</p>
 */
public final class SFMWorkspacePanelMoveInteraction {
    public static final int BUTTON = GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
    static final double DRAG_THRESHOLD_SQUARED = 16.0D;

    public interface Host {
        @Nullable Target targetAt(double mouseX, double mouseY);

        void openActions(Target source);

        boolean moveToStack(Target source, Target destination);
    }

    public record Target(
            SFMWorkspacePanelId entryId,
            SFMScreenPanel panel,
            SFMScreenPanelBounds bounds
    ) {
        public Target {
            Objects.requireNonNull(entryId, "entryId");
            Objects.requireNonNull(panel, "panel");
            Objects.requireNonNull(bounds, "bounds");
        }
    }

    public record Snapshot(
            Optional<Target> source,
            Optional<Target> destination,
            boolean dragging
    ) {
        public Snapshot {
            source = Objects.requireNonNull(source, "source");
            destination = Objects.requireNonNull(destination, "destination");
        }
    }

    private final Host host;
    private @Nullable Target source;
    private @Nullable Target destination;
    private double pressX;
    private double pressY;
    private boolean dragging;

    public SFMWorkspacePanelMoveInteraction(Host host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    /**
     * Attempts the modified-drag route from ordinary panel content.
     * Returning {@code false} deliberately leaves the complete gesture to the
     * panel content when the modifier is not active.
     */
    public boolean pointerPressedFromContent(
            double mouseX,
            double mouseY,
            int button,
            boolean moveModifierDown
    ) {
        if (!moveModifierDown) return false;
        return capture(host.targetAt(mouseX, mouseY), mouseX, mouseY, button);
    }

    /** Captures from an explicit numbered panel-entry affordance. */
    public boolean pointerPressedFromAffordance(
            Target hit,
            double mouseX,
            double mouseY,
            int button
    ) {
        return capture(Objects.requireNonNull(hit, "hit"), mouseX, mouseY, button);
    }

    private boolean capture(@Nullable Target hit, double mouseX, double mouseY, int button) {
        if (button != BUTTON || source != null || hit == null) return false;
        source = hit;
        destination = null;
        pressX = mouseX;
        pressY = mouseY;
        dragging = false;
        return true;
    }

    public boolean pointerDragged(double mouseX, double mouseY, int button) {
        if (button != BUTTON || source == null) return false;
        double dx = mouseX - pressX;
        double dy = mouseY - pressY;
        if (!dragging && dx * dx + dy * dy >= DRAG_THRESHOLD_SQUARED) dragging = true;
        destination = dragging ? eligibleDestination(host.targetAt(mouseX, mouseY)) : null;
        return true;
    }

    public boolean pointerReleased(double mouseX, double mouseY, int button) {
        if (button != BUTTON || source == null) return false;
        Target capturedSource = source;
        Target releasedOver = eligibleDestination(host.targetAt(mouseX, mouseY));
        boolean wasDragging = dragging;
        clear();
        if (wasDragging) {
            if (releasedOver != null) host.moveToStack(capturedSource, releasedOver);
        } else {
            host.openActions(capturedSource);
        }
        return true;
    }

    public void cancel() {
        clear();
    }

    public boolean isCaptured() {
        return source != null;
    }

    public Snapshot snapshot() {
        return new Snapshot(Optional.ofNullable(source), Optional.ofNullable(destination), dragging);
    }

    private @Nullable Target eligibleDestination(@Nullable Target candidate) {
        return candidate == null || source == null || candidate.entryId().equals(source.entryId())
                ? null
                : candidate;
    }

    private void clear() {
        source = null;
        destination = null;
        dragging = false;
    }
}
