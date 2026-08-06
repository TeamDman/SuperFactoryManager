package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Ordered focus/event host for children embedded in one workspace panel. */
public final class SFMPanelWidgetHost {
    private final List<SFMPanelWidget> children = new ArrayList<>();
    private @Nullable SFMPanelWidget focused;
    private @Nullable ResourceLocation rememberedFocus;
    private boolean active = true;
    private boolean dragging;

    public List<SFMPanelWidget> children() {
        return List.copyOf(children);
    }

    public void setChildren(Collection<? extends SFMPanelWidget> nextChildren) {
        Objects.requireNonNull(nextChildren);
        ResourceLocation previous = focused == null ? rememberedFocus : focused.elementId();
        clearFocus(false);
        children.clear();
        for (SFMPanelWidget child : nextChildren) {
            if (children.stream().anyMatch(existing -> existing.elementId().equals(child.elementId()))) {
                throw new IllegalArgumentException("Duplicate panel element id " + child.elementId());
            }
            children.add(Objects.requireNonNull(child));
        }
        rememberedFocus = previous;
        restoreRememberedFocus();
    }

    public void clear() {
        clearFocus(false);
        children.clear();
        rememberedFocus = null;
        dragging = false;
    }

    public Optional<SFMPanelWidget> focusedChild() {
        reconcileFocus();
        return Optional.ofNullable(focused);
    }

    public Optional<ResourceLocation> focusedElementId() {
        return focusedChild().map(SFMPanelWidget::elementId);
    }

    public Optional<Component> focusedNarration() {
        return focusedChild().map(SFMPanelWidget::narration);
    }

    public void updateFocusedNarration(NarrationElementOutput output) {
        focusedChild().ifPresent(child -> child.updateNarration(output));
    }

    /**
     * Keeps panel-local focus remembered while exposing Vanilla's focused
     * state only for the active workspace panel.
     */
    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (focused != null) setFocused(focused, active);
        if (!active) dragging = false;
    }

    public boolean focus(ResourceLocation elementId) {
        return children.stream()
                .filter(child -> child.elementId().equals(elementId))
                .filter(SFMPanelWidgetHost::focusable)
                .findFirst()
                .map(child -> {
                    focus(child);
                    return true;
                }).orElse(false);
    }

    public boolean changeFocus(boolean forward) {
        reconcileFocus();
        List<SFMPanelWidget> focusable = children.stream()
                .filter(SFMPanelWidgetHost::focusable)
                .toList();
        if (focusable.isEmpty()) {
            clearFocus(true);
            return false;
        }
        int current = focused == null ? -1 : focusable.indexOf(focused);
        int next;
        if (current < 0) {
            next = forward ? 0 : focusable.size() - 1;
        } else {
            next = Math.floorMod(current + (forward ? 1 : -1), focusable.size());
        }
        focus(focusable.get(next));
        return true;
    }

    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        reconcileFocus();
        for (SFMPanelWidget child : List.copyOf(children)) {
            if (child.isPanelVisible()) child.render(poseStack, mouseX, mouseY, partialTick);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        reconcileFocus();
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode != GLFW.GLFW_KEY_TAB) return false;
        boolean reverse = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        return changeFocus(!reverse);
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        reconcileFocus();
        return focused != null && focused.keyReleased(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char character, int modifiers) {
        reconcileFocus();
        return focused != null && focused.charTyped(character, modifiers);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        reconcileFocus();
        // Later children are visually on top (for example an open selector).
        for (int index = children.size() - 1; index >= 0; index--) {
            SFMPanelWidget child = children.get(index);
            if (!child.isPanelVisible() || !child.isMouseOver(mouseX, mouseY)) continue;
            if (child.isPanelEnabled() && child.mouseClicked(mouseX, mouseY, button)) {
                // Activation may hide this child and deliberately transfer
                // focus (for example, selecting an item in a drop-down).
                if (focusable(child)) focus(child);
                else reconcileFocus();
                dragging = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
            }
            // A visible top child owns its rectangle even when disabled or when
            // that mouse button is unsupported; never leak into an overlapped
            // terminal viewport.
            return true;
        }
        return false;
    }

    public void mouseMoved(double mouseX, double mouseY) {
        for (SFMPanelWidget child : List.copyOf(children)) {
            if (child.isPanelVisible()) child.mouseMoved(mouseX, mouseY);
        }
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        reconcileFocus();
        dragging = false;
        return focused != null && focused.mouseReleased(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        reconcileFocus();
        return focused != null && dragging
                && focused.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (int index = children.size() - 1; index >= 0; index--) {
            SFMPanelWidget child = children.get(index);
            if (!child.isPanelVisible() || !child.isMouseOver(mouseX, mouseY)) continue;
            return child.mouseScrolled(mouseX, mouseY, delta);
        }
        return false;
    }

    public void closed() {
        clearFocus(false);
        dragging = false;
    }

    /** Shared inverse transform used by the multiplexer and pure unit tests. */
    public static int[] panelCoordinates(
            SFMScreenPanelBounds workspaceContentBounds,
            double panelScale,
            double workspaceX,
            double workspaceY
    ) {
        double scale = panelScale <= 0.0D ? 1.0D : panelScale;
        return new int[]{
                (int) Math.floor((workspaceX - workspaceContentBounds.x()) / scale),
                (int) Math.floor((workspaceY - workspaceContentBounds.y()) / scale)
        };
    }

    private void reconcileFocus() {
        if (focused != null && (!children.contains(focused) || !focusable(focused))) {
            clearFocus(false);
        }
        if (focused == null) restoreRememberedFocus();
    }

    private void restoreRememberedFocus() {
        if (rememberedFocus == null || focused != null) return;
        children.stream()
                .filter(child -> child.elementId().equals(rememberedFocus))
                .filter(SFMPanelWidgetHost::focusable)
                .findFirst()
                .ifPresent(this::focus);
    }

    private void focus(SFMPanelWidget child) {
        if (focused == child) {
            setFocused(child, active);
            rememberedFocus = child.elementId();
            return;
        }
        if (focused != null) setFocused(focused, false);
        focused = child;
        rememberedFocus = child.elementId();
        setFocused(child, active);
    }

    private void clearFocus(boolean forget) {
        if (focused != null) setFocused(focused, false);
        focused = null;
        if (forget) rememberedFocus = null;
    }

    private static boolean focusable(SFMPanelWidget child) {
        return child.isPanelVisible() && child.isPanelEnabled();
    }

    @MCVersionDependentBehaviour
    private static void setFocused(SFMPanelWidget child, boolean focused) {
        child.setPanelFocused(focused);
    }
}
