package ca.teamdman.sfm.client.screen.history;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMEpisodeContext;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.SFMRouteComparisonRuntime;
import ca.teamdman.sfm.client.history.SFMTrajectoryContract;
import ca.teamdman.sfm.client.history.comparison.SFMRouteComparisonSession;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Composite, read-only side-by-side presentation of two retained candidate routes.
 *
 * <p>Both children retain their existing candidate-history rendering and async
 * projection behavior. This panel contributes comparison metadata and independently
 * applies the two persisted cursors; it never invokes a trajectory operation.</p>
 */
public final class SFMRouteComparisonPanel implements SFMScreenPanel, SFMEpisodeContext {
    private static final int BACKGROUND = 0xF0101218;
    private static final int HEADER = 0xFFEDF6F7;
    private static final int MUTED = 0xFF93A7AC;
    private static final int ACCENT = 0xFF72B7FF;
    private static final int WARNING = 0xFFFFC35A;
    private static final int BAD = 0xFFFF7373;
    private static final int PADDING = 7;
    private static final int HEADER_HEIGHT = 58;

    private final SFMRouteComparisonRuntime comparisonRuntime;
    private final SFMHistoryGraphRuntime historyRuntime;
    private final SFMReviewSessionRuntime reviewRuntime;
    private final String sessionId;
    private final SFMCandidateHistoryPanel leftPanel;
    private final SFMCandidateHistoryPanel rightPanel;

    private SFMRouteComparisonSession session;
    private SFMScreenPanelBounds bounds = new SFMScreenPanelBounds(0, 0, 1, 1);
    private SFMScreenPanelBounds leftBounds = bounds;
    private SFMScreenPanelBounds rightBounds = bounds;
    private SFMRouteComparisonSession.Side activeSide = SFMRouteComparisonSession.Side.LEFT;
    private Optional<MachineInvariant> openingInvariant = Optional.empty();

    public SFMRouteComparisonPanel(String sessionId) {
        this(
                SFMRouteComparisonRuntime.get(),
                SFMHistoryGraphRuntime.get(),
                SFMReviewSessionRuntime.get(),
                sessionId
        );
    }

    SFMRouteComparisonPanel(
            SFMRouteComparisonRuntime comparisonRuntime,
            SFMHistoryGraphRuntime historyRuntime,
            SFMReviewSessionRuntime reviewRuntime,
            String sessionId
    ) {
        this.comparisonRuntime = Objects.requireNonNull(comparisonRuntime, "comparisonRuntime");
        this.historyRuntime = Objects.requireNonNull(historyRuntime, "historyRuntime");
        this.reviewRuntime = Objects.requireNonNull(reviewRuntime, "reviewRuntime");
        this.sessionId = requireText(sessionId, "sessionId");
        this.session = comparisonRuntime.require(sessionId);
        SFMEntitySelector exactMachine = SFMEntitySelector.exact(
                SFMEntitySelector.Domain.EPISODE,
                session.left().machineId()
        );
        this.leftPanel = new SFMCandidateHistoryPanel(
                exactMachine,
                Optional.of(session.left().planRevisionId()),
                Optional.of(session.left().routeId())
        );
        this.rightPanel = new SFMCandidateHistoryPanel(
                exactMachine,
                Optional.of(session.right().planRevisionId()),
                Optional.of(session.right().routeId())
        );
    }

    @Override
    public Component title() {
        return Component.literal("Route Comparison");
    }

    @Override
    public Component narration() {
        return Component.literal("Route Comparison " + sessionId
                + ". " + session.mode().name().toLowerCase(java.util.Locale.ROOT)
                + ". Left frame " + session.leftCursor() + " of " + session.leftLastPosition()
                + ". Right frame " + session.rightCursor() + " of " + session.rightLastPosition()
                + ". Comparison state does not change actual trajectory history.");
    }

    @Override
    public Optional<String> episodeId() {
        return Optional.of(session.left().machineId());
    }

    public String sessionId() {
        return sessionId;
    }

    public SFMRouteComparisonSession session() {
        return session;
    }

    public SFMCandidateHistoryPanel leftPanel() {
        return leftPanel;
    }

    public SFMCandidateHistoryPanel rightPanel() {
        return rightPanel;
    }

    @Override
    public void opened(Minecraft minecraft, SFMScreenPanelBounds bounds, SFMWorkspacePanelContext context) {
        updateBounds(bounds);
        openingInvariant = currentInvariant();
        leftPanel.opened(minecraft, leftBounds, context);
        rightPanel.opened(minecraft, rightBounds, context);
        synchronizeSession();
    }

    @Override
    public void resized(Minecraft minecraft, SFMScreenPanelBounds bounds) {
        updateBounds(bounds);
        leftPanel.resized(minecraft, leftBounds);
        rightPanel.resized(minecraft, rightBounds);
    }

    @Override
    public void closed() {
        leftPanel.closed();
        rightPanel.closed();
    }

    @Override
    public void tick() {
        synchronizeSession();
        leftPanel.tick();
        rightPanel.tick();
        // Candidate routes materialize asynchronously. Reapplying the persisted
        // positions after each child tick preserves nonzero seeks once bounds grow.
        leftPanel.setTimelinePosition(session.leftCursor());
        rightPanel.setTimelinePosition(session.rightCursor());
    }

    @Override
    public void render(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds ignored,
            int mouseX,
            int mouseY,
            float partialTick,
            boolean focused
    ) {
        synchronizeSession();
        leftPanel.render(
                poseStack, minecraft, leftBounds, mouseX, mouseY, partialTick,
                focused && activeSide == SFMRouteComparisonSession.Side.LEFT
        );
        rightPanel.render(
                poseStack, minecraft, rightBounds, mouseX, mouseY, partialTick,
                focused && activeSide == SFMRouteComparisonSession.Side.RIGHT
        );
        GuiComponent.fill(
                poseStack,
                bounds.x(),
                bounds.y(),
                bounds.x() + bounds.width(),
                bounds.y() + HEADER_HEIGHT,
                BACKGROUND
        );
        int divider = rightBounds.x();
        GuiComponent.fill(
                poseStack,
                divider - 1,
                bounds.y(),
                divider + 1,
                bounds.y() + bounds.height(),
                ACCENT
        );
        renderHeader(poseStack, minecraft);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return activeSide == SFMRouteComparisonSession.Side.LEFT
                ? leftPanel.keyPressed(keyCode, scanCode, modifiers)
                : rightPanel.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return activeSide == SFMRouteComparisonSession.Side.LEFT
                ? leftPanel.keyReleased(keyCode, scanCode, modifiers)
                : rightPanel.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        return activeSide == SFMRouteComparisonSession.Side.LEFT
                ? leftPanel.charTyped(character, modifiers)
                : rightPanel.charTyped(character, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (leftBounds.contains(mouseX, mouseY)) {
            activeSide = SFMRouteComparisonSession.Side.LEFT;
            return leftPanel.mouseClicked(mouseX, mouseY, button);
        }
        if (rightBounds.contains(mouseX, mouseY)) {
            activeSide = SFMRouteComparisonSession.Side.RIGHT;
            return rightPanel.mouseClicked(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (leftBounds.contains(mouseX, mouseY)) leftPanel.mouseMoved(mouseX, mouseY);
        else if (rightBounds.contains(mouseX, mouseY)) rightPanel.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return leftBounds.contains(mouseX, mouseY)
                ? leftPanel.mouseReleased(mouseX, mouseY, button)
                : rightBounds.contains(mouseX, mouseY)
                && rightPanel.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return activeSide == SFMRouteComparisonSession.Side.LEFT
                ? leftPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                : rightPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return leftBounds.contains(mouseX, mouseY)
                ? leftPanel.mouseScrolled(mouseX, mouseY, delta)
                : rightBounds.contains(mouseX, mouseY)
                && rightPanel.mouseScrolled(mouseX, mouseY, delta);
    }

    public Optional<MachineInvariant> currentInvariant() {
        return historyRuntime.snapshotEvent().machine(session.left().machineId())
                .map(SFMRouteComparisonPanel::machineInvariant);
    }

    public boolean openingInvariantStillHolds() {
        return openingInvariant.isPresent() && openingInvariant.equals(currentInvariant());
    }

    private void synchronizeSession() {
        SFMRouteComparisonSession latest = comparisonRuntime.require(sessionId);
        if (!latest.equals(session)) session = latest;
        leftPanel.setTimelinePosition(session.leftCursor());
        rightPanel.setTimelinePosition(session.rightCursor());
    }

    private void updateBounds(SFMScreenPanelBounds value) {
        bounds = Objects.requireNonNull(value, "bounds");
        int contentY = bounds.y() + Math.min(HEADER_HEIGHT, Math.max(0, bounds.height() - 1));
        int contentHeight = Math.max(1, bounds.y() + bounds.height() - contentY);
        int leftWidth = Math.max(1, bounds.width() / 2);
        int rightWidth = Math.max(1, bounds.width() - leftWidth);
        leftBounds = new SFMScreenPanelBounds(bounds.x(), contentY, leftWidth, contentHeight);
        rightBounds = new SFMScreenPanelBounds(bounds.x() + leftWidth, contentY, rightWidth, contentHeight);
    }

    private void renderHeader(PoseStack poseStack, Minecraft minecraft) {
        int line = Math.max(10, minecraft.font.lineHeight + 2);
        int x = bounds.x() + PADDING;
        int y = bounds.y() + 3;
        String invariantLabel = openingInvariantStillHolds()
                ? "actual head/IP/selection invariant ✓"
                : "actual trajectory changed externally or by explicit selection";
        drawClipped(poseStack, minecraft, bounds, x, y,
                "Route Comparison · " + session.id() + " · "
                        + session.mode().name().toLowerCase(java.util.Locale.ROOT), HEADER);
        y += line;
        drawClipped(poseStack, minecraft, bounds, x, y, invariantLabel,
                openingInvariantStillHolds() ? ACCENT : WARNING);
        y += line;
        Optional<MachineInvariant> currentInvariant = currentInvariant();
        if (currentInvariant.isPresent()) {
            MachineInvariant invariant = currentInvariant.orElseThrow();
            drawClipped(
                    poseStack,
                    minecraft,
                    bounds,
                    x,
                    y,
                    "actual head " + shortIdentity(invariant.actualHistoryHeadId())
                            + " · IP " + invariant.instructionPointer()
                            + " · selected " + invariant.selection(),
                    MUTED
            );
        }

        int metricsY = bounds.y() + HEADER_HEIGHT - line * 2 - 2;
        renderRouteMetrics(
                poseStack, minecraft, leftBounds.x() + PADDING, metricsY,
                SFMRouteComparisonSession.Side.LEFT, leftPanel, leftBounds
        );
        renderRouteMetrics(
                poseStack, minecraft, rightBounds.x() + PADDING, metricsY,
                SFMRouteComparisonSession.Side.RIGHT, rightPanel, rightBounds
        );
    }

    private void renderRouteMetrics(
            PoseStack poseStack,
            Minecraft minecraft,
            int x,
            int y,
            SFMRouteComparisonSession.Side side,
            SFMCandidateHistoryPanel candidatePanel,
            SFMScreenPanelBounds sideBounds
    ) {
        int line = Math.max(10, minecraft.font.lineHeight + 2);
        SFMRouteComparisonSession.RouteAddress address = session.address(side);
        Optional<SFMRouteComparisonRuntime.RouteDescriptor> descriptor = historyRuntime.snapshotEvent()
                .machine(address.machineId())
                .flatMap(snapshot -> SFMRouteComparisonRuntime.describe(snapshot, address));
        String facts = descriptor.map(value -> "cost " + value.totalCost()
                        + " · " + value.status().name().toLowerCase(java.util.Locale.ROOT))
                .orElse("retained route unavailable");
        String finalHash = candidatePanel.projection()
                .flatMap(SFMRouteComparisonPanel::finalPredictedHash)
                .map(SFMRouteComparisonPanel::shortIdentity)
                .orElse("pending/unavailable");
        drawClipped(poseStack, minecraft, sideBounds, x, y,
                side.name() + " " + shortIdentity(address.planRevisionId())
                        + " · " + shortIdentity(address.routeId())
                        + " · " + facts, HEADER);
        y += line;
        drawClipped(poseStack, minecraft, sideBounds, x, y,
                "frame " + session.cursor(side)
                        + " · final hash " + finalHash
                        + " · comments " + commentTotal(address)
                        + " · " + session.disposition(side).name().toLowerCase(java.util.Locale.ROOT),
                session.disposition(side) == SFMRouteComparisonSession.Disposition.REJECTED ? BAD : MUTED);
    }

    private int commentTotal(SFMRouteComparisonSession.RouteAddress address) {
        return (int) reviewRuntime.session(address.machineId()).comments().stream()
                .filter(comment -> comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget)
                .map(comment -> (SFMReviewSessionV2.CandidateTrajectoryTarget) comment.target())
                .filter(target -> target.trajectoryPlanRevisionId().equals(address.planRevisionId())
                        && target.routeId().equals(address.routeId()))
                .count();
    }

    private static Optional<String> finalPredictedHash(
            SFMCandidateHistoryContract.CandidateRouteProjection projection
    ) {
        return projection.frame(projection.lastPosition()).address().predictedStateHash();
    }

    private static MachineInvariant machineInvariant(SFMHistoryGraphRuntime.MachineSnapshot snapshot) {
        Optional<String> selectedRoute = snapshot.planBook().selectedPlanRevisionId().flatMap(planId ->
                snapshot.planBook().plans().stream()
                        .filter(plan -> plan.id().equals(planId))
                        .findFirst()
                        .flatMap(SFMTrajectoryContract.TrajectoryPlanRevision::selectedRouteId));
        String selection = snapshot.planBook().selectedPlanRevisionId()
                .map(plan -> plan + "/" + selectedRoute.orElse("none"))
                .orElse("none");
        String pointer = snapshot.machine().instructionPointer()
                .map(value -> value.planRevisionId() + "/"
                        + value.routeId() + "@" + value.nextStepIndex())
                .orElse("none");
        return new MachineInvariant(snapshot.machine().actualHistoryHeadId(), pointer, selection);
    }

    private static void drawClipped(
            PoseStack poseStack,
            Minecraft minecraft,
            SFMScreenPanelBounds clippingBounds,
            int x,
            int y,
            String value,
            int colour
    ) {
        int available = Math.max(0, clippingBounds.x() + clippingBounds.width() - PADDING - x);
        SFMFontUtils.draw(
                poseStack,
                minecraft.font,
                minecraft.font.plainSubstrByWidth(value, available),
                x,
                y,
                colour,
                false
        );
    }

    private static String shortIdentity(String value) {
        if (value.length() <= 28) return value;
        return value.substring(0, 15) + "…" + value.substring(value.length() - 10);
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    /** Values that review-only comparison transitions are required to leave untouched. */
    public record MachineInvariant(
            String actualHistoryHeadId,
            String instructionPointer,
            String selection
    ) {
        public MachineInvariant {
            actualHistoryHeadId = requireText(actualHistoryHeadId, "actualHistoryHeadId");
            instructionPointer = requireText(instructionPointer, "instructionPointer");
            selection = requireText(selection, "selection");
        }
    }
}
