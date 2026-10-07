package ca.teamdman.sfm.client.screen.workspace.timeline;

import ca.teamdman.sfm.client.screen.SFMFontUtils;
{% if features.timeline_wrapper_transparency and features.trajectory_panels or features.timeline_wrapper_transparency and features.workspace_counterfactuals or features.timeline_wrapper_transparency and features.review_sessions or features.timeline_wrapper_transparency and features.route_comparison %}
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
{% else %}
{% endif %}
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import com.mojang.blaze3d.vertex.PoseStack;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.client.gui.GuiGraphics;
{% when "26.1.2" %}
import net.minecraft.client.gui.GuiGraphicsExtractor;
{% endcase %}
import net.minecraft.client.Minecraft;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
import net.minecraft.client.gui.GuiComponent;
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
{% endcase %}
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** Composable timeline transport with coupled keyframe-space and elapsed-time tracks. */
{% if features.timeline_wrapper_transparency and features.trajectory_panels or features.timeline_wrapper_transparency and features.workspace_counterfactuals or features.timeline_wrapper_transparency and features.review_sessions or features.timeline_wrapper_transparency and features.route_comparison %}
public final class SFMTimelinePanel implements SFMScreenPanel, SFMEpisodeContext {
{% else %}
public final class SFMTimelinePanel implements SFMScreenPanel {
{% endif %}
    public static final int TRANSPORT_HEIGHT = 50;
    private static final int PADDING = 8;
    private static final int BUTTON_WIDTH = 22;
    private static final int GAP = 4;
    private static final int TRACK_HEIGHT = 5;
    private static final int READOUT_WIDTH = 108;

    private final SFMSeekableTimelinePanel child;
{% if features.timeline_dynamic_bounds %}
    private final int defaultTicksPerTransition;
    private SFMTimelineModel model;
    private Double pendingKeyframeSeek;
{% else %}
    private final SFMTimelineModel model;
{% endif %}
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMScreenPanelBounds childBounds = bounds;
    private DragTrack draggingTrack = DragTrack.NONE;

    public SFMTimelinePanel(SFMSeekableTimelinePanel child, int defaultTicksPerTransition) {
        this.child = Objects.requireNonNull(child, "child");
{% if features.timeline_positive_transition_ticks %}
        if (defaultTicksPerTransition <= 0) {
            throw new IllegalArgumentException("defaultTicksPerTransition must be positive");
        }
{% endif %}
{% if features.timeline_dynamic_bounds %}
        this.defaultTicksPerTransition = defaultTicksPerTransition;
{% else %}
{% endif %}
        this.model = new SFMTimelineModel(child.timelineBounds(), child.timelineBounds().first(),
                child.animationTimeline(defaultTicksPerTransition));
        child.setTimelinePosition(model.keyframePosition());
    }

    public SFMTimelineModel model() { return model; }
{% if features.timeline_wrapper_transparency %}
    public SFMSeekableTimelinePanel child() { return child; }
{% else %}
{% endif %}
    public void seek(int keyframe) { applyKeyframeSeek(keyframe); }
{% if features.timeline_dynamic_bounds %}
    /**
     * Pins a requested keyframe across asynchronous timeline materialization.
     *
     * <p>Ordinary {@link #seek(int)} retains its immediate clamping semantics.
     * Reopen/navigation flows use this method because a lazy child may expose
     * only its provisional frame-zero bounds until a later tick.</p>
     */
    public void seekWhenAvailable(int keyframe) {
        model.pause();
        pendingKeyframeSeek = (double) keyframe;
        applyPendingKeyframeSeek();
    }
{% else %}
{% endif %}
    public void seekKeyframePosition(double position) { applyKeyframeSeek(position); }
    public void seekElapsedTicks(double ticks) { applyTimeSeek(ticks); }
    public void jumpKeyframe(int direction) { applyKeyframeJump(direction); }

    @Override
    public Component title() { return Component.literal("Timeline: ").append(child.title()); }

{% if features.timeline_wrapper_transparency and features.trajectory_panels or features.timeline_wrapper_transparency and features.workspace_counterfactuals or features.timeline_wrapper_transparency and features.review_sessions or features.timeline_wrapper_transparency and features.route_comparison %}
    @Override
    public java.util.Optional<String> episodeId() {
        return child instanceof SFMEpisodeContext context ? context.episodeId() : java.util.Optional.empty();
    }

    @Override
{% else %}
    @Override
{% endif %}
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
{% if features.timeline_dynamic_bounds %}
        child.tick();
        refreshTimelineBounds();
        if (model.tick()) child.setTimelinePosition(model.keyframePosition());
{% else %}
        if (model.tick()) child.setTimelinePosition(model.keyframePosition());
        child.tick();
{% endif %}
    }

    @Override
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds ignored, int mouseX, int mouseY,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(GuiGraphics graphics, Minecraft minecraft, SFMScreenPanelBounds ignored, int mouseX, int mouseY,
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    public void render(GuiGraphicsExtractor graphics, Minecraft minecraft, SFMScreenPanelBounds ignored, int mouseX, int mouseY,
{% endcase %}
                       float partialTick, boolean focused) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        child.render(poseStack, minecraft, childBounds, mouseX, mouseY, partialTick, focused);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        child.render(graphics, minecraft, childBounds, mouseX, mouseY, partialTick, focused);
{% endcase %}
        SFMClientTheme theme = SFMClientThemeService.active();
        int transportY = transportY();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, bounds.x() + 1, transportY, bounds.x() + bounds.width() - 1,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(bounds.x() + 1, transportY, bounds.x() + bounds.width() - 1,
{% endcase %}
                bounds.y() + bounds.height() - 1, theme.colour(SFMColourRole.TIMELINE_BACKGROUND));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderButton(poseStack, minecraft, previousButtonX(), transportY + 16, "|<", theme);
        renderButton(poseStack, minecraft, playButtonX(), transportY + 16, model.playing() ? "||" : ">", theme);
        renderButton(poseStack, minecraft, nextButtonX(), transportY + 16, ">|", theme);
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        renderButton(graphics, minecraft, previousButtonX(), transportY + 16, "|<", theme);
        renderButton(graphics, minecraft, playButtonX(), transportY + 16, model.playing() ? "||" : ">", theme);
        renderButton(graphics, minecraft, nextButtonX(), transportY + 16, ">|", theme);
{% endcase %}

{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderTrack(poseStack, keyframeTrackY(), xForKeyframePosition(model.keyframePosition()),
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        renderTrack(graphics, keyframeTrackY(), xForKeyframePosition(model.keyframePosition()),
{% endcase %}
                theme.colour(SFMColourRole.TIMELINE_KEYFRAME), theme);
        for (int keyframe = model.bounds().first(); keyframe <= model.bounds().last(); keyframe++) {
            int markerX = xForKeyframePosition(keyframe);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
            GuiComponent.fill(poseStack, markerX, keyframeTrackY() - 2,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
            graphics.fill(markerX, keyframeTrackY() - 2,
{% endcase %}
                    markerX + 1, keyframeTrackY() + TRACK_HEIGHT + 2, theme.colour(SFMColourRole.TIMELINE_MARKER));
        }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        renderTrack(poseStack, timeTrackY(), xForElapsedTicks(model.elapsedTicks()),
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        renderTrack(graphics, timeTrackY(), xForElapsedTicks(model.elapsedTicks()),
{% endcase %}
                theme.colour(SFMColourRole.TIMELINE_TIME), theme);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
                String.format("K %.2f / %d", model.keyframePosition(), model.bounds().last()),
                readoutX(), transportY + 4, theme.colour(SFMColourRole.TIMELINE_KEYFRAME), false);
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font,
{% endcase %}
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

{% if features.timeline_dynamic_bounds %}
    private void refreshTimelineBounds() {
        SFMTimelineBounds nextBounds = child.timelineBounds();
        if (!nextBounds.equals(model.bounds())) {
            double retainedPosition = nextBounds.clamp(model.keyframePosition());
            model = new SFMTimelineModel(
                    nextBounds,
                    retainedPosition,
                    child.animationTimeline(defaultTicksPerTransition)
            );
            child.setTimelinePosition(retainedPosition);
        }
        applyPendingKeyframeSeek();
    }

    private void applyKeyframeJump(int direction) {
{% else %}
    private void applyKeyframeJump(int direction) {
{% endif %}
{% if features.timeline_dynamic_bounds %}
        pendingKeyframeSeek = null;
{% else %}
{% endif %}
        if (model.jumpKeyframe(direction)) child.setTimelinePosition(model.keyframePosition());
    }
    private void applyKeyframeSeek(double position) {
{% if features.timeline_dynamic_bounds %}
        pendingKeyframeSeek = null;
{% else %}
{% endif %}
        model.pause();
        if (model.seekKeyframePosition(position)) child.setTimelinePosition(model.keyframePosition());
    }
    private void applyTimeSeek(double ticks) {
{% if features.timeline_dynamic_bounds %}
        pendingKeyframeSeek = null;
{% else %}
{% endif %}
        model.pause();
        if (model.seekElapsedTicks(ticks)) child.setTimelinePosition(model.keyframePosition());
    }
{% if features.timeline_dynamic_bounds %}
    private void applyPendingKeyframeSeek() {
        if (pendingKeyframeSeek == null
                || pendingKeyframeSeek < model.bounds().first()
                || pendingKeyframeSeek > model.bounds().last()) return;
        double requested = pendingKeyframeSeek;
        pendingKeyframeSeek = null;
        if (model.seekKeyframePosition(requested)) child.setTimelinePosition(model.keyframePosition());
    }
{% else %}
{% endif %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private void renderTrack(PoseStack poseStack, int y, int thumb, int color, SFMClientTheme theme) {
        GuiComponent.fill(poseStack, trackStartX(), y, trackEndX(), y + TRACK_HEIGHT,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderTrack(GuiGraphics graphics, int y, int thumb, int color, SFMClientTheme theme) {
        graphics.fill(trackStartX(), y, trackEndX(), y + TRACK_HEIGHT,
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private void renderTrack(GuiGraphicsExtractor graphics, int y, int thumb, int color, SFMClientTheme theme) {
        graphics.fill(trackStartX(), y, trackEndX(), y + TRACK_HEIGHT,
{% endcase %}
                theme.colour(SFMColourRole.TIMELINE_TRACK));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, trackStartX(), y, thumb, y + TRACK_HEIGHT, color);
        GuiComponent.fill(poseStack, thumb - 2, y - 2, thumb + 3, y + TRACK_HEIGHT + 2,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(trackStartX(), y, thumb, y + TRACK_HEIGHT, color);
        graphics.fill(thumb - 2, y - 2, thumb + 3, y + TRACK_HEIGHT + 2,
{% endcase %}
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
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
    private static void renderButton(PoseStack poseStack, Minecraft minecraft, int x, int y, String label,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void renderButton(GuiGraphics graphics, Minecraft minecraft, int x, int y, String label,
{% when "26.1.2" %}
    @ca.teamdman.sfm.common.util.MCVersionDependentBehaviour
    private static void renderButton(GuiGraphicsExtractor graphics, Minecraft minecraft, int x, int y, String label,
{% endcase %}
                                     SFMClientTheme theme) {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        GuiComponent.fill(poseStack, x, y, x + BUTTON_WIDTH, y + 18,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        graphics.fill(x, y, x + BUTTON_WIDTH, y + 18,
{% endcase %}
                theme.colour(SFMColourRole.PANEL_SELECTION));
{% case minecraft_version %}
{% when "1.19.2", "1.19.4" %}
        SFMFontUtils.draw(poseStack, minecraft.font, label, x + (BUTTON_WIDTH - minecraft.font.width(label)) / 2,
{% when "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2" %}
        SFMFontUtils.draw(graphics, minecraft.font, label, x + (BUTTON_WIDTH - minecraft.font.width(label)) / 2,
{% endcase %}
                y + 5, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
    }
    private enum DragTrack { NONE, KEYFRAME, TIME }
}
