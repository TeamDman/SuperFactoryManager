package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Composable timeline transport with coupled keyframe-space and elapsed-time tracks. */
public final class SFMTimelinePanel implements SFMScreenPanel {
    public static final int TRANSPORT_HEIGHT = 50;
    private static final int PADDING = 8;
    private static final int BUTTON_WIDTH = 22;
    private static final int GAP = 4;
    private static final int TRACK_HEIGHT = 5;
    private static final int READOUT_WIDTH = 108;

    private final SFMSeekableTimelinePanel child;
    private final SFMTimelineModel model;
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMScreenPanelBounds childBounds = bounds;
    private DragTrack draggingTrack = DragTrack.NONE;

    public SFMTimelinePanel(SFMSeekableTimelinePanel child, int defaultTicksPerTransition) {
        this.child = Objects.requireNonNull(child, "child");
        this.model = new SFMTimelineModel(child.timelineBounds(), child.timelineBounds().first(),
                child.animationTimeline(defaultTicksPerTransition));
        child.setTimelinePosition(model.keyframePosition());
    }

    public SFMTimelineModel model() { return model; }
    public void seek(int keyframe) { applyKeyframeSeek(keyframe); }
    public void seekKeyframePosition(double position) { applyKeyframeSeek(position); }
    public void seekElapsedTicks(double ticks) { applyTimeSeek(ticks); }
    public void jumpKeyframe(int direction) { applyKeyframeJump(direction); }

    @Override
    public Component title() { return Component.literal("Timeline: ").append(child.title()); }

    @Override
    public Component narration() {
        return title().copy().append(Component.literal(String.format(
                ". Keyframe %.2f of %d. Time %.0f of %d ticks%s",
                model.keyframePosition(), model.bounds().last(), model.elapsedTicks(),
                model.timeline().totalTicks(), model.playing() ? ". Playing" : ". Paused"
        )));
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

    @Override public void closed() { child.closed(); }

    @Override
    public void tick() {
        if (model.tick()) child.setTimelinePosition(model.keyframePosition());
        child.tick();
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds ignored, int mouseX, int mouseY,
                       float partialTick, boolean focused) {
        child.render(poseStack, minecraft, childBounds, mouseX, mouseY, partialTick, focused);
        SFMClientTheme theme = SFMClientThemeService.active();
        int transportY = transportY();
        GuiComponent.fill(poseStack, bounds.x() + 1, transportY, bounds.x() + bounds.width() - 1,
                bounds.y() + bounds.height() - 1, theme.colour(SFMColourRole.TIMELINE_BACKGROUND));
        renderButton(poseStack, minecraft, previousButtonX(), transportY + 16, "|<", theme);
        renderButton(poseStack, minecraft, playButtonX(), transportY + 16, model.playing() ? "||" : ">", theme);
        renderButton(poseStack, minecraft, nextButtonX(), transportY + 16, ">|", theme);

        renderTrack(poseStack, keyframeTrackY(), xForKeyframePosition(model.keyframePosition()),
                theme.colour(SFMColourRole.TIMELINE_KEYFRAME), theme);
        for (int keyframe = model.bounds().first(); keyframe <= model.bounds().last(); keyframe++) {
            int markerX = xForKeyframePosition(keyframe);
            GuiComponent.fill(poseStack, markerX, keyframeTrackY() - 2,
                    markerX + 1, keyframeTrackY() + TRACK_HEIGHT + 2, theme.colour(SFMColourRole.TIMELINE_MARKER));
        }
        renderTrack(poseStack, timeTrackY(), xForElapsedTicks(model.elapsedTicks()),
                theme.colour(SFMColourRole.TIMELINE_TIME), theme);
        SFMFontUtils.draw(poseStack, minecraft.font,
                String.format("K %.2f / %d", model.keyframePosition(), model.bounds().last()),
                readoutX(), transportY + 4, theme.colour(SFMColourRole.TIMELINE_KEYFRAME), false);
        SFMFontUtils.draw(poseStack, minecraft.font,
                String.format("T %.0f / %d ticks", model.elapsedTicks(), model.timeline().totalTicks()),
                readoutX(), transportY + 29, theme.colour(SFMColourRole.TIMELINE_TIME), false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> model.togglePlaying();
            case GLFW.GLFW_KEY_LEFT -> applyKeyframeJump(-1);
            case GLFW.GLFW_KEY_RIGHT -> applyKeyframeJump(1);
            case GLFW.GLFW_KEY_HOME -> applyKeyframeSeek(model.bounds().first());
            case GLFW.GLFW_KEY_END -> applyKeyframeSeek(model.bounds().last());
            default -> { return child.keyPressed(keyCode, scanCode, modifiers); }
        }
        return true;
    }

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return child.keyReleased(keyCode, scanCode, modifiers);
    }
    @Override public boolean charTyped(char character, int modifiers) { return child.charTyped(character, modifiers); }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY >= transportY()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (insideX(mouseX, previousButtonX(), BUTTON_WIDTH)) applyKeyframeJump(-1);
                else if (insideX(mouseX, playButtonX(), BUTTON_WIDTH)) model.togglePlaying();
                else if (insideX(mouseX, nextButtonX(), BUTTON_WIDTH)) applyKeyframeJump(1);
                else if (insideTrack(mouseX, mouseY, keyframeTrackY())) {
                    draggingTrack = DragTrack.KEYFRAME;
                    seekKeyframeFromTrack(mouseX);
                } else if (insideTrack(mouseX, mouseY, timeTrackY())) {
                    draggingTrack = DragTrack.TIME;
                    seekTimeFromTrack(mouseX);
                }
            }
            return true;
        }
        return child.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTrack != DragTrack.NONE) {
            if (draggingTrack == DragTrack.KEYFRAME) seekKeyframeFromTrack(mouseX);
            else seekTimeFromTrack(mouseX);
            return true;
        }
        if (mouseY >= transportY()) return true;
        return child.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && draggingTrack != DragTrack.NONE) {
            if (draggingTrack == DragTrack.KEYFRAME) seekKeyframeFromTrack(mouseX);
            else seekTimeFromTrack(mouseX);
            draggingTrack = DragTrack.NONE;
            return true;
        }
        if (mouseY >= transportY()) return true;
        return child.mouseReleased(mouseX, mouseY, button);
    }

    @Override public void mouseMoved(double mouseX, double mouseY) {
        if (mouseY < transportY()) child.mouseMoved(mouseX, mouseY);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseY < transportY() && child.mouseScrolled(mouseX, mouseY, delta);
    }

    public int xForTimestep(int keyframe) { return xForKeyframePosition(keyframe); }
    public int xForKeyframePosition(double position) {
        int span = model.bounds().last() - model.bounds().first();
        if (span == 0) return trackStartX();
        double share = (model.bounds().clamp(position) - model.bounds().first()) / span;
        return trackStartX() + (int) Math.round(share * (trackEndX() - trackStartX()));
    }
    public int xForElapsedTicks(double ticks) {
        if (model.timeline().totalTicks() == 0) return trackEndX();
        double share = model.timeline().clampElapsedTicks(ticks) / model.timeline().totalTicks();
        return trackStartX() + (int) Math.round(share * (trackEndX() - trackStartX()));
    }
    public int trackY() { return keyframeTrackY() + TRACK_HEIGHT / 2; }
    public int elapsedTrackY() { return timeTrackY() + TRACK_HEIGHT / 2; }

    private void updateBounds(SFMScreenPanelBounds bounds) {
        this.bounds = bounds;
        childBounds = new SFMScreenPanelBounds(bounds.x(), bounds.y(), bounds.width(),
                Math.max(1, bounds.height() - TRANSPORT_HEIGHT));
    }

    private void applyKeyframeJump(int direction) {
        if (model.jumpKeyframe(direction)) child.setTimelinePosition(model.keyframePosition());
    }
    private void applyKeyframeSeek(double position) {
        model.pause();
        if (model.seekKeyframePosition(position)) child.setTimelinePosition(model.keyframePosition());
    }
    private void applyTimeSeek(double ticks) {
        model.pause();
        if (model.seekElapsedTicks(ticks)) child.setTimelinePosition(model.keyframePosition());
    }
    private void seekKeyframeFromTrack(double mouseX) {
        double share = trackShare(mouseX);
        applyKeyframeSeek(model.bounds().first() + share * (model.bounds().last() - model.bounds().first()));
    }
    private void seekTimeFromTrack(double mouseX) {
        applyTimeSeek(trackShare(mouseX) * model.timeline().totalTicks());
    }
    private double trackShare(double mouseX) {
        return Math.max(0D, Math.min(1D,
                (mouseX - trackStartX()) / Math.max(1D, trackEndX() - trackStartX())));
    }
    private void renderTrack(PoseStack poseStack, int y, int thumb, int color, SFMClientTheme theme) {
        GuiComponent.fill(poseStack, trackStartX(), y, trackEndX(), y + TRACK_HEIGHT,
                theme.colour(SFMColourRole.TIMELINE_TRACK));
        GuiComponent.fill(poseStack, trackStartX(), y, thumb, y + TRACK_HEIGHT, color);
        GuiComponent.fill(poseStack, thumb - 2, y - 2, thumb + 3, y + TRACK_HEIGHT + 2,
                theme.colour(SFMColourRole.TEXT_PRIMARY));
    }
    private boolean insideTrack(double mouseX, double mouseY, int y) {
        return mouseX >= trackStartX() && mouseX <= trackEndX() && mouseY >= y - 3 && mouseY <= y + TRACK_HEIGHT + 3;
    }
    private int transportY() { return bounds.y() + bounds.height() - TRANSPORT_HEIGHT; }
    private int previousButtonX() { return bounds.x() + PADDING; }
    private int playButtonX() { return previousButtonX() + BUTTON_WIDTH + GAP; }
    private int nextButtonX() { return playButtonX() + BUTTON_WIDTH + GAP; }
    private int trackStartX() { return nextButtonX() + BUTTON_WIDTH + PADDING; }
    private int trackEndX() { return Math.max(trackStartX() + 1, bounds.x() + bounds.width() - READOUT_WIDTH - PADDING); }
    private int readoutX() { return bounds.x() + bounds.width() - READOUT_WIDTH; }
    private int keyframeTrackY() { return transportY() + 8; }
    private int timeTrackY() { return transportY() + 33; }
    private static boolean insideX(double mouseX, int left, int width) { return mouseX >= left && mouseX < left + width; }
    private static void renderButton(PoseStack poseStack, Minecraft minecraft, int x, int y, String label,
                                     SFMClientTheme theme) {
        GuiComponent.fill(poseStack, x, y, x + BUTTON_WIDTH, y + 18,
                theme.colour(SFMColourRole.PANEL_SELECTION));
        SFMFontUtils.draw(poseStack, minecraft.font, label, x + (BUTTON_WIDTH - minecraft.font.width(label)) / 2,
                y + 5, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
    }
    private enum DragTrack { NONE, KEYFRAME, TIME }
}
