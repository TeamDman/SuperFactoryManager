package ca.teamdman.sfm.client.screen.review;

import ca.teamdman.sfm.client.presentation.SFMItemIconRenderer;
import ca.teamdman.sfm.client.screen.SFMFontUtils;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.client.theme.SFMColourRole;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;

/** Responsive themed comparison panel with independent review, approval, audit, and stale dimensions. */
public final class SFMSourceComparisonPanel implements SFMScreenPanel {
    private static final int HEADER_HEIGHT = 57;
    private static final int ROW_HEIGHT = 30;
    private static final int SIDEBAR_WIDE = 220;

    private SFMSourceComparison comparison;
    private SFMReviewLedger ledger;
    private final Path ledgerPath;
    private int selectedIndex;
    private int operationListTop;
    private int operationListLeft;
    private int operationListRight;

    public SFMSourceComparisonPanel(SFMSourceComparison comparison, SFMReviewLedger ledger) {
        this.comparison = comparison;
        this.ledger = ledger;
        this.ledgerPath = ledger.storagePath();
        if (comparison.operations().isEmpty()) throw new IllegalArgumentException("Comparison requires operations");
    }

    public SFMSourceComparison comparison() { return comparison; }
    public SFMSourceComparison.SourceOperation selectedOperation() { return comparison.operations().get(selectedIndex); }
    public SFMReviewLedger.Decision selectedDecision() { return ledger.get(comparison, selectedOperation()); }
    public String persistenceStatus() { return ledger.persistenceStatus(); }

    public void select(int index) { selectedIndex = Math.max(0, Math.min(comparison.operations().size() - 1, index)); }

    public void markReviewed() {
        var operation = selectedOperation();
        var old = selectedDecision();
        ledger.put(comparison, operation, SFMReviewLedger.ReviewState.REVIEWED, old.humanDecision());
    }

    public void approve() {
        ledger.put(comparison, selectedOperation(), SFMReviewLedger.ReviewState.REVIEWED,
                SFMReviewLedger.HumanDecision.APPROVED);
    }

    public void reject() {
        ledger.put(comparison, selectedOperation(), SFMReviewLedger.ReviewState.REVIEWED,
                SFMReviewLedger.HumanDecision.REJECTED);
    }

    public void reloadPersistedLedger() {
        if (ledgerPath == null) throw new IllegalStateException("Panel ledger is not persistent");
        ledger = SFMReviewLedger.open(ledgerPath);
    }

    public void clearPersistedLedger() { ledger.clear(); }

    public void changeBodySourceForFixture() {
        comparison = SFMSourceComparisonFixtures.changeBodySource(comparison);
        select(comparison.operations().size() - 1);
    }

    public void applyFixtureCommand(String command) {
        if (command.startsWith("select:")) select(Integer.parseInt(command.substring("select:".length())));
        else switch (command) {
            case "review" -> markReviewed();
            case "approve" -> approve();
            case "reject" -> reject();
            case "reload" -> reloadPersistedLedger();
            case "clear" -> clearPersistedLedger();
            case "change-body-source" -> changeBodySourceForFixture();
            default -> throw new IllegalArgumentException("Unknown source-review fixture command: " + command);
        }
    }

    @Override public Component title() { return Component.literal("Source Review Ledger"); }

    @Override
    public Component narration() {
        var operation = selectedOperation();
        var decision = selectedDecision();
        return Component.literal(operation.label() + ", reviewed " + decision.reviewState() + ", approval "
                + decision.humanDecision() + ", audit " + operation.auditStatus()
                + (decision.isStale(operation) ? ", stale" : ""));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP) select(selectedIndex - 1);
        else if (keyCode == GLFW.GLFW_KEY_DOWN) select(selectedIndex + 1);
        else if (keyCode == GLFW.GLFW_KEY_R) markReviewed();
        else if (keyCode == GLFW.GLFW_KEY_A) approve();
        else if (keyCode == GLFW.GLFW_KEY_X) reject();
        else return false;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseY < operationListTop
                || mouseX < operationListLeft || mouseX >= operationListRight) return false;
        int row = ((int) mouseY - operationListTop) / ROW_HEIGHT;
        if (row < 0 || row >= comparison.operations().size()) return false;
        select(row);
        return true;
    }

    @Override
    public void render(PoseStack poseStack, Minecraft minecraft, SFMScreenPanelBounds bounds,
                       int mouseX, int mouseY, float partialTick, boolean focused) {
        SFMClientTheme theme = SFMClientThemeService.active();
        int x = bounds.x() + 8;
        int y = bounds.y() + 7;
        GuiComponent.fill(poseStack, bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(),
                theme.colour(SFMColourRole.PANEL_BACKGROUND));
        draw(poseStack, minecraft, "Source Review Ledger", x, y, bounds.width() - 16,
                theme.colour(SFMColourRole.TEXT_PRIMARY), true);
        draw(poseStack, minecraft, comparison.beforeSnapshot() + "  ->  " + comparison.afterSnapshot(), x, y + 13,
                bounds.width() - 16, theme.colour(SFMColourRole.TEXT_MUTED), false);
        draw(poseStack, minecraft, "UP/DOWN select   R reviewed   A approve   X reject", x, y + 26,
                bounds.width() - 16, theme.colour(SFMColourRole.TEXT_MUTED), false);
        draw(poseStack, minecraft, ledger.persistenceStatus(), x, y + 39, bounds.width() - 16,
                theme.colour(SFMColourRole.TEXT_ACCENT), false);

        int top = bounds.y() + HEADER_HEIGHT;
        int listWidth = Math.min(SIDEBAR_WIDE, Math.max(230, bounds.width() / 3));
        operationListTop = top;
        operationListLeft = x;
        operationListRight = x + listWidth;
        renderOperationList(poseStack, minecraft, theme, x, top, listWidth);
        int detailX = x + listWidth + 8;
        renderDetail(poseStack, minecraft, theme, detailX, top,
                Math.max(0, bounds.x() + bounds.width() - detailX - 8), bounds.y() + bounds.height());
    }

    private void renderOperationList(PoseStack poseStack, Minecraft minecraft, SFMClientTheme theme,
                                     int x, int top, int width) {
        for (int i = 0; i < comparison.operations().size(); i++) {
            var operation = comparison.operations().get(i);
            var decision = ledger.get(comparison, operation);
            int y = top + i * ROW_HEIGHT;
            if (i == selectedIndex) GuiComponent.fill(poseStack, x, y, x + width, y + ROW_HEIGHT - 1,
                    theme.colour(SFMColourRole.PANEL_SELECTION));
            SFMItemIconRenderer.render(minecraft, theme.fileIcon(extensionKey(operation.displayPath())), x + 3, y + 7);
            String stale = decision.isStale(operation) ? " | STALE" : "";
            String row = operationMarker(operation.kind()) + " " + operation.label();
            draw(poseStack, minecraft, row, x + 23, y + 4, width - 26,
                    decision.isStale(operation) ? theme.colour(SFMColourRole.TEXT_ERROR)
                            : theme.colour(SFMColourRole.TEXT_PRIMARY), false);
            draw(poseStack, minecraft, reviewIndicator(decision) + " | audit " + operation.auditStatus() + stale,
                    x + 23, y + 17, width - 26,
                    decision.isStale(operation) ? theme.colour(SFMColourRole.TEXT_ERROR)
                            : theme.colour(SFMColourRole.TEXT_MUTED), false);
        }
    }

    private void renderDetail(PoseStack poseStack, Minecraft minecraft, SFMClientTheme theme,
                              int x, int y, int width, int bottom) {
        var operation = selectedOperation();
        var decision = selectedDecision();
        SFMItemIconRenderer.render(minecraft, theme.fileIcon(extensionKey(operation.displayPath())), x, y);
        draw(poseStack, minecraft, operation.label(), x + 21, y + 3, width - 21,
                theme.colour(SFMColourRole.TEXT_PRIMARY), true);
        draw(poseStack, minecraft, operation.displayPath() + " | " + operation.equivalence(), x, y + 21, width,
                theme.colour(SFMColourRole.TEXT_MUTED), false);
        draw(poseStack, minecraft, "Reviewed: " + decision.reviewState(), x, y + 36, width,
                theme.colour(SFMColourRole.TEXT_ACCENT), true);
        draw(poseStack, minecraft, "Human approval: " + decision.humanDecision(), x, y + 49, width,
                decision.isStale(operation) ? theme.colour(SFMColourRole.TEXT_ERROR)
                        : theme.colour(SFMColourRole.TEXT_ACCENT), true);
        draw(poseStack, minecraft, "Audit policy: " + operation.auditStatus(), x, y + 62, width,
                operation.auditStatus() == SFMSourceComparison.AuditStatus.FORBIDDEN
                        ? theme.colour(SFMColourRole.TEXT_ERROR) : theme.colour(SFMColourRole.TEXT_ACCENT), true);
        if (decision.isStale(operation)) draw(poseStack, minecraft, "!!! STALE: HASH CHANGED !!!",
                x, y + 75, width, theme.colour(SFMColourRole.TEXT_ERROR), true);
        draw(poseStack, minecraft, operation.diagnostic(), x, y + 88, width,
                theme.colour(SFMColourRole.TEXT_MUTED), false);

        int codeY = y + 108;
        int gap = 6;
        int columnWidth = Math.max(0, (width - gap) / 2);
        GuiComponent.fill(poseStack, x, codeY, x + columnWidth, bottom - 7, 0x803C2020);
        GuiComponent.fill(poseStack, x + columnWidth + gap, codeY, x + width, bottom - 7, 0x80203C28);
        draw(poseStack, minecraft, "BEFORE", x + 4, codeY + 5, columnWidth - 8,
                theme.colour(SFMColourRole.TEXT_ERROR), true);
        draw(poseStack, minecraft, "AFTER", x + columnWidth + gap + 4, codeY + 5, columnWidth - 8,
                theme.colour(SFMColourRole.TEXT_ACCENT), true);
        renderCode(poseStack, minecraft, theme, operation.beforeLines(), x, codeY + 20, columnWidth, "- ");
        renderCode(poseStack, minecraft, theme, operation.afterLines(), x + columnWidth + gap, codeY + 20, columnWidth, "+ ");
    }

    private static void renderCode(PoseStack poseStack, Minecraft minecraft, SFMClientTheme theme,
                                   List<String> lines, int x, int y, int width, String prefix) {
        if (lines.isEmpty()) draw(poseStack, minecraft, "(none)", x + 4, y, width - 8,
                theme.colour(SFMColourRole.TEXT_MUTED), false);
        for (int i = 0; i < lines.size(); i++) draw(poseStack, minecraft, prefix + lines.get(i), x + 4,
                y + i * 13, width - 8, theme.colour(SFMColourRole.TEXT_PRIMARY), false);
    }

    private static String operationMarker(SFMSourceComparison.OperationKind kind) {
        return switch (kind) { case INSERT -> "+"; case DELETE -> "-"; case RENAME -> ">"; default -> "~"; };
    }

    private static String reviewIndicator(SFMReviewLedger.Decision decision) {
        if (decision.humanDecision() == SFMReviewLedger.HumanDecision.APPROVED) return "APPROVED";
        if (decision.humanDecision() == SFMReviewLedger.HumanDecision.REJECTED) return "REJECTED";
        return decision.reviewState() == SFMReviewLedger.ReviewState.REVIEWED ? "REVIEWED" : "UNSEEN";
    }

    private static String extensionKey(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "extensionless" : path.substring(dot).toLowerCase(java.util.Locale.ROOT);
    }

    private static void draw(PoseStack poseStack, Minecraft minecraft, String text, int x, int y,
                             int width, int colour, boolean shadow) {
        if (width <= 0) return;
        SFMFontUtils.draw(poseStack, minecraft.font, minecraft.font.plainSubstrByWidth(text, width),
                x, y, colour, shadow);
    }
}
