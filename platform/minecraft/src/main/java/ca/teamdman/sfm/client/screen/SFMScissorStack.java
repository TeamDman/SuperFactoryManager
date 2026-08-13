package ca.teamdman.sfm.client.screen;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector4f;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Nestable GUI scissoring that understands the active pose transform.
 *
 * <p>Vanilla's scroll widgets treat their coordinates as screen-global and
 * disable scissoring when they finish.  Panels render in translated/scaled
 * local coordinates, so using those helpers directly both clips at the wrong
 * place and loses the panel's parent clip.  This stack intersects child clips
 * and restores the parent after every pop.</p>
 */
public final class SFMScissorStack {
    private static final Deque<FramebufferRect> STACK = new ArrayDeque<>();

    private SFMScissorStack() {
    }

    public static void pushGui(double left, double top, double right, double bottom) {
        pushFramebuffer(guiToFramebuffer(left, top, right, bottom));
    }

    public static void pushGui(
            PoseStack poseStack,
            double left,
            double top,
            double right,
            double bottom
    ) {
        Vector4f topLeft = transformed(poseStack, left, top);
        Vector4f topRight = transformed(poseStack, right, top);
        Vector4f bottomLeft = transformed(poseStack, left, bottom);
        Vector4f bottomRight = transformed(poseStack, right, bottom);
        double transformedLeft = Math.min(Math.min(topLeft.x(), topRight.x()),
                                          Math.min(bottomLeft.x(), bottomRight.x()));
        double transformedRight = Math.max(Math.max(topLeft.x(), topRight.x()),
                                           Math.max(bottomLeft.x(), bottomRight.x()));
        double transformedTop = Math.min(Math.min(topLeft.y(), topRight.y()),
                                         Math.min(bottomLeft.y(), bottomRight.y()));
        double transformedBottom = Math.max(Math.max(topLeft.y(), topRight.y()),
                                            Math.max(bottomLeft.y(), bottomRight.y()));
        pushGui(transformedLeft, transformedTop, transformedRight, transformedBottom);
    }

    public static void pop() {
        if (STACK.isEmpty()) {
            throw new IllegalStateException("SFM scissor stack underflow");
        }
        STACK.pop();
        if (STACK.isEmpty()) {
            RenderSystem.disableScissor();
        } else {
            apply(STACK.peek());
        }
    }

    private static Vector4f transformed(PoseStack poseStack, double x, double y) {
        Vector4f answer = new Vector4f((float) x, (float) y, 0.0F, 1.0F);
        answer.transform(poseStack.last().pose());
        return answer;
    }

    private static void pushFramebuffer(FramebufferRect requested) {
        FramebufferRect clipped = STACK.isEmpty()
                ? requested
                : intersect(STACK.peek(), requested);
        STACK.push(clipped);
        apply(clipped);
    }

    @MCVersionDependentBehaviour
    private static FramebufferRect guiToFramebuffer(
            double left,
            double top,
            double right,
            double bottom
    ) {
        var window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();
        int framebufferLeft = (int) Math.floor(left * scale);
        int framebufferRight = (int) Math.ceil(right * scale);
        int framebufferTop = (int) Math.floor(top * scale);
        int framebufferBottom = (int) Math.ceil(bottom * scale);
        return new FramebufferRect(
                framebufferLeft,
                window.getHeight() - framebufferBottom,
                Math.max(0, framebufferRight - framebufferLeft),
                Math.max(0, framebufferBottom - framebufferTop)
        );
    }

    static FramebufferRect intersect(FramebufferRect parent, FramebufferRect child) {
        int left = Math.max(parent.x(), child.x());
        int bottom = Math.max(parent.y(), child.y());
        int right = Math.min(parent.right(), child.right());
        int top = Math.min(parent.top(), child.top());
        return new FramebufferRect(
                left,
                bottom,
                Math.max(0, right - left),
                Math.max(0, top - bottom)
        );
    }

    private static void apply(FramebufferRect rect) {
        RenderSystem.enableScissor(rect.x(), rect.y(), rect.width(), rect.height());
    }

    record FramebufferRect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int top() {
            return y + height;
        }
    }
}
