package ca.teamdman.sfm.client.screen.workspace.toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Pure logical-GUI placement for bottom-centred workspace toasts. */
public final class SFMWorkspaceToastLayout {
    public static final int VIEWPORT_INSET = 2;
    public static final int BOTTOM_INSET = 18;
    public static final int GAP = 4;

    public record Measure(SFMWorkspaceToastQueue.ToastId id, int width, int height) {
        public Measure {
            Objects.requireNonNull(id, "id");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Toast dimensions must be positive");
        }
    }

    public record Bounds(SFMWorkspaceToastQueue.ToastId id, int x, int y, int width, int height) {
        public Bounds {
            Objects.requireNonNull(id, "id");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Toast dimensions must be positive");
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    public List<Bounds> place(int viewportWidth, int viewportHeight, List<Measure> measures) {
        Objects.requireNonNull(measures, "measures");
        if (viewportWidth <= VIEWPORT_INSET * 2 || viewportHeight <= VIEWPORT_INSET * 2) return List.of();
        int availableWidth = viewportWidth - VIEWPORT_INSET * 2;
        int bottom = Math.max(VIEWPORT_INSET, viewportHeight - BOTTOM_INSET);
        ArrayList<Bounds> reverse = new ArrayList<>();
        for (int index = measures.size() - 1; index >= 0; index--) {
            Measure measure = measures.get(index);
            int width = Math.min(availableWidth, measure.width());
            int height = measure.height();
            int top = bottom - height;
            if (top < VIEWPORT_INSET) break;
            int left = Math.max(VIEWPORT_INSET, (viewportWidth - width) / 2);
            reverse.add(new Bounds(measure.id(), left, top, width, height));
            bottom = top - GAP;
        }
        Collections.reverse(reverse);
        return List.copyOf(reverse);
    }
}
