package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceAxis;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDivider;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspaceDividerInteraction;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetPointer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Drives the real workspace mouse callbacks while retaining capture between puppet actions. */
public record MoveWorkspaceDividerPointerPuppetAction(
        Operation operation,
        int deltaX,
        int deltaY
) implements SFMPuppetAction {
    @Override
    public String description() {
        return operation.name().toLowerCase() + " workspace divider intersection";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected SFM workspace for divider pointer input");
        }
        if (operation == Operation.RELEASE) {
            SFMWorkspaceDividerInteraction.Snapshot snapshot = workspace.dividerInteractionSnapshot();
            if (snapshot.capturedDividerIds().isEmpty()) {
                throw new IllegalStateException("Workspace divider release has no active capture");
            }
            if (!workspace.mouseReleased(
                    snapshot.currentMouseX(),
                    snapshot.currentMouseY(),
                    GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
                throw new IllegalStateException("Workspace divider release was not consumed");
            }
            return true;
        }

        Point target = intersection(workspace.dividerDescriptions());
        SFMGamePuppetPointer.moveNative(workspace, target.x(), target.y());
        SFMGamePuppetPointer.moveWorkspace(workspace, target.x(), target.y());
        if (workspace.dividerInteractionSnapshot().hoveredDividerIds().isEmpty()) {
            throw new IllegalStateException("Workspace divider hover did not resolve a divider");
        }
        if (operation == Operation.HOVER) return true;

        if (!workspace.mouseClicked(target.x(), target.y(), GLFW.GLFW_MOUSE_BUTTON_LEFT)) {
            throw new IllegalStateException("Workspace divider press was not consumed");
        }
        double endX = target.x() + deltaX;
        double endY = target.y() + deltaY;
        SFMGamePuppetPointer.moveNative(workspace, endX, endY);
        SFMGamePuppetPointer.moveWorkspace(workspace, endX, endY);
        if (!workspace.mouseDragged(
                endX,
                endY,
                GLFW.GLFW_MOUSE_BUTTON_LEFT,
                deltaX,
                deltaY)) {
            throw new IllegalStateException("Workspace divider drag was not consumed");
        }
        return true;
    }

    private static Point intersection(List<SFMWorkspaceDivider> dividers) {
        for (SFMWorkspaceDivider horizontal : dividers) {
            if (horizontal.axis() != SFMWorkspaceAxis.HORIZONTAL) continue;
            for (SFMWorkspaceDivider vertical : dividers) {
                if (vertical.axis() != SFMWorkspaceAxis.VERTICAL) continue;
                SFMScreenPanelBounds overlap = overlap(horizontal.hitBounds(), vertical.hitBounds());
                if (overlap.width() > 0 && overlap.height() > 0) {
                    return new Point(
                            overlap.x() + overlap.width() / 2.0D,
                            overlap.y() + overlap.height() / 2.0D);
                }
            }
        }
        SFMWorkspaceDivider divider = dividers.stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace has no resizable divider"));
        SFMScreenPanelBounds line = divider.lineBounds();
        return new Point(
                line.x() + line.width() / 2.0D,
                line.y() + line.height() / 2.0D);
    }

    private static SFMScreenPanelBounds overlap(SFMScreenPanelBounds left, SFMScreenPanelBounds right) {
        int x = Math.max(left.x(), right.x());
        int y = Math.max(left.y(), right.y());
        int rightEdge = Math.min(left.x() + left.width(), right.x() + right.width());
        int bottomEdge = Math.min(left.y() + left.height(), right.y() + right.height());
        return new SFMScreenPanelBounds(x, y, Math.max(0, rightEdge - x), Math.max(0, bottomEdge - y));
    }

    public enum Operation {
        HOVER,
        PRESS_AND_DRAG,
        RELEASE
    }

    private record Point(double x, double y) {
    }
}
