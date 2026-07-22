package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Composable timeline transport which delegates the content region to a random-access child panel. */
public final class SFMTimelinePanel implements SFMScreenPanel {
    public static final int TRANSPORT_HEIGHT = 32;
    private static final int PADDING = 8;
    private static final int BUTTON_WIDTH = 22;
    private static final int GAP = 4;
    private static final int TRACK_HEIGHT = 6;

    private final SFMSeekableTimelinePanel child;
    private final SFMTimelineModel model;
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMScreenPanelBounds childBounds = bounds;
    private boolean draggingTrack;

    public SFMTimelinePanel(SFMSeekableTimelinePanel child, int ticksPerStep) {
        this.child = Objects.requireNonNull(child, "child");
        this.model = new SFMTimelineModel(child.timelineBounds(), child.timelineBounds().first(), ticksPerStep);
        child.setTimelinePosition(model.current());
    }

    public SFMTimelineModel model() { return model; }

    /** Explicit random-access seam used by episode inspectors and deterministic automation. */
    public void seek(int timestep) {
        applySeek(timestep);
    }

    @Override
    public Component title() {
        return Component.literal("Timeline: ").append(child.title());
    }

    @Override
    public Component narration() {
        return title().copy().append(Component.literal(
                ". Timestep " + model.current() + " of " + model.bounds().last()
                        + (model.playing() ? ". Playing" : ". Paused")
        ));
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        updateBounds(bounds);
        child.opened(minecraft, childBounds, context);
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        updateBounds(bounds);
        child.resized(minecraft, childBounds);
    }

    @Override
    public void closed() {
        child.closed();
    }

    @Override
    public void tick() {
        if (model.tick()) child.setTimelinePosition(model.current());
        child.tick();
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds ignored, int mouseX, int mouseY,
                       float partialTick, boolean focused) {
        child.render(poseStack, minecraft, childBounds, mouseX, mouseY, partialTick, focused);
        int transportY = transportY();
        GuiComponent.fill(poseStack, bounds.x() + 1, transportY, bounds.x() + bounds.width() - 1,
                bounds.y() + bounds.height() - 1, 0xEE11151A);
        renderButton(poseStack, minecraft, previousButtonX(), transportY + 7, "<");
        renderButton(poseStack, minecraft, playButtonX(), transportY + 7, model.playing() ? "||" : ">");
        renderButton(poseStack, minecraft, nextButtonX(), transportY + 7, ">");

        int trackStart = trackStartX();
        int trackEnd = trackEndX();
        int trackY = transportY + 12;
        GuiComponent.fill(poseStack, trackStart, trackY, trackEnd, trackY + TRACK_HEIGHT, 0xFF4A5159);
        int thumb = xForTimestep(model.current());
        GuiComponent.fill(poseStack, trackStart, trackY, thumb, trackY + TRACK_HEIGHT, 0xFF55FFFF);
        GuiComponent.fill(poseStack, thumb - 2, trackY - 3, thumb + 3, trackY + TRACK_HEIGHT + 3, 0xFFFFFFFF);

        String time = model.current() + " / " + model.bounds().last();
        SFMFontUtils.draw(poseStack, minecraft.font, time, bounds.x() + bounds.width() - PADDING - minecraft.font.width(time),
                transportY + 10, 0xFFFFFFFF, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> model.togglePlaying();
            case GLFW.GLFW_KEY_LEFT -> applyStep(-1);
            case GLFW.GLFW_KEY_RIGHT -> applyStep(1);
            case GLFW.GLFW_KEY_HOME -> applySeek(model.bounds().first());
            case GLFW.GLFW_KEY_END -> applySeek(model.bounds().last());
            default -> { return child.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return child.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return child.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= transportY()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (insideX(mouseX, previousButtonX(), BUTTON_WIDTH)) applyStep(-1);
                else if (insideX(mouseX, playButtonX(), BUTTON_WIDTH)) model.togglePlaying();
                else if (insideX(mouseX, nextButtonX(), BUTTON_WIDTH)) applyStep(1);
                else if (mouseX >= trackStartX() && mouseX <= trackEndX()) {
                    draggingTrack = true;
                    seekFromTrack(mouseX);
                }
            }
            return true;
        }
        return child.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingTrack && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            seekFromTrack(mouseX);
            return true;
        }
        if (mouseY >= transportY()) return true;
        return child.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingTrack && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            seekFromTrack(mouseX);
            draggingTrack = false;
            return true;
        }
        if (mouseY >= transportY()) return true;
        return child.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (mouseY < transportY()) child.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseY < transportY() && child.mouseScrolled(mouseX, mouseY, delta);
    }

    public int xForTimestep(int timestep) {
        int span = model.bounds().last() - model.bounds().first();
        if (span == 0) return trackStartX();
        double share = (model.bounds().clamp(timestep) - model.bounds().first()) / (double) span;
        return trackStartX() + (int) Math.round(share * (trackEndX() - trackStartX()));
    }

    public int trackY() {
        return transportY() + 15;
    }

    private void updateBounds(SFMScreenPanelBounds bounds) {
        this.bounds = bounds;
        int contentHeight = Math.max(1, bounds.height() - TRANSPORT_HEIGHT);
        this.childBounds = new SFMScreenPanelBounds(bounds.x(), bounds.y(), bounds.width(), contentHeight);
    }

    private void applyStep(int delta) {
        if (model.step(delta)) child.setTimelinePosition(model.current());
    }

    private void applySeek(int timestep) {
        model.pause();
        if (model.seek(timestep)) child.setTimelinePosition(model.current());
    }

    private void seekFromTrack(double mouseX) {
        double share = (mouseX - trackStartX()) / Math.max(1D, trackEndX() - trackStartX());
        int span = model.bounds().last() - model.bounds().first();
        applySeek(model.bounds().first() + (int) Math.round(Math.max(0D, Math.min(1D, share)) * span));
    }

    private int transportY() { return bounds.y() + bounds.height() - TRANSPORT_HEIGHT; }
    private int previousButtonX() { return bounds.x() + PADDING; }
    private int playButtonX() { return previousButtonX() + BUTTON_WIDTH + GAP; }
    private int nextButtonX() { return playButtonX() + BUTTON_WIDTH + GAP; }
    private int trackStartX() { return nextButtonX() + BUTTON_WIDTH + PADDING; }
    private int trackEndX() { return Math.max(trackStartX() + 1, bounds.x() + bounds.width() - 68); }

    private static boolean insideX(double mouseX, int left, int width) {
        return mouseX >= left && mouseX < left + width;
    }

    private static void renderButton(PoseStack poseStack, Minecraft minecraft, int x, int y, String label) {
        GuiComponent.fill(poseStack, x, y, x + BUTTON_WIDTH, y + 18, 0xFF303840);
        SFMFontUtils.draw(poseStack, minecraft.font, label, x + (BUTTON_WIDTH - minecraft.font.width(label)) / 2,
                y + 5, 0xFFFFFFFF, false);
    }
}
