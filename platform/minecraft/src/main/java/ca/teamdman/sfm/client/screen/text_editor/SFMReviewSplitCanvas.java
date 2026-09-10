package ca.teamdman.sfm.client.screen.text_editor;

import ca.teamdman.sfm.client.review.release_review.*;
import ca.teamdman.sfm.client.context.*;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.SFMDrawCanvasRemoteSyntaxStyles.FormattingSpan;
import ca.teamdman.sfm.client.text_editor.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1.SnapshotSide;

/** A single measured canvas: visual columns are not concatenated into a fictitious source file. */
public final class SFMReviewSplitCanvas {
    private record Hit(SnapshotSide side, int row, int column) { }
    private final SFMReleaseReviewSurfaceRuntime.SplitDocument document;
    private final Font font;
    private final double rightX;
    private final double width;
    private Hit anchor, active;
    private boolean dragging;
    private boolean allSelected;
    private List<FormattingSpan> styles = List.of();
    private List<SFMTextDocumentDecoration> decorations = List.of();

    public SFMReviewSplitCanvas(SFMReleaseReviewSurfaceRuntime.SplitDocument document, Font font) {
        this.document = document; this.font = font;
        int leftWidth = 120, rightWidth = 120;
        for (var row : document.layout().rows()) {
            if (row.before().isPresent()) leftWidth = Math.max(leftWidth, font.width(row.before().orElseThrow().displayText()));
            if (row.after().isPresent()) rightWidth = Math.max(rightWidth, font.width(row.after().orElseThrow().displayText()));
        }
        rightX = leftWidth + 40;
        width = rightX + rightWidth + 20;
    }
    public double width() { return width; }
    public double contentLeft() { return document.pair().before().isEmpty() ? rightX : 0; }
    public double contentRight() { return document.pair().after().isEmpty() ? rightX - 40 : width; }
    public double contentHeight() { return Math.max(font.lineHeight, document.layout().rows().size() * font.lineHeight); }
    public void styles(List<FormattingSpan> value) { styles = List.copyOf(value); }
    public void decorations(List<SFMTextDocumentDecoration> value) { decorations = List.copyOf(value); }
    public SFMReleaseReviewSplitLayout.Selection selection() {
        if (allSelected) return document.layout().selectAll(selectedSide());
        return anchor == null ? new SFMReleaseReviewSplitLayout.Selection(List.of(), "")
                : document.layout().select(anchor.side(), anchor.row(), anchor.column(), active.row(), active.column());
    }
    public boolean press(double x, double y) {
        allSelected = false;
        Hit hit = hit(x, y, null);
        // Empty-side clicks still belong to this canvas and must focus its panel.
        if (hit == null) { dragging = false; return true; }
        anchor = active = hit; dragging = true; return true;
    }
    public boolean drag(double x, double y) {
        if (!dragging) return false;
        Hit hit = hit(x, y, anchor.side());
        if (hit != null) active = hit;
        return true;
    }
    public boolean release(double x, double y) {
        boolean result = drag(x, y); dragging = false; return result;
    }
    public void focus(double x, double y) { press(x, y); dragging = false; }
    private SnapshotSide selectedSide() {
        return anchor == null ? (document.pair().after().isPresent() ? SnapshotSide.AFTER : SnapshotSide.BEFORE) : anchor.side();
    }

    public boolean keyPressed(int key, int modifiers) {
        boolean control = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        SnapshotSide side = selectedSide();
        if (control && key == org.lwjgl.glfw.GLFW.GLFW_KEY_A) {
            allSelected = true; dragging = false; return true;
        }
        SFMReleaseReviewSplitLayout.Motion motion = switch (key) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> SFMReleaseReviewSplitLayout.Motion.LEFT;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> SFMReleaseReviewSplitLayout.Motion.RIGHT;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> SFMReleaseReviewSplitLayout.Motion.UP;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> SFMReleaseReviewSplitLayout.Motion.DOWN;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> SFMReleaseReviewSplitLayout.Motion.HOME;
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> SFMReleaseReviewSplitLayout.Motion.END;
            default -> null;
        };
        if (motion == null) return false;
        var layout = document.layout();
        if (active == null || allSelected) {
            boolean end = allSelected && (motion == SFMReleaseReviewSplitLayout.Motion.RIGHT
                    || motion == SFMReleaseReviewSplitLayout.Motion.DOWN || motion == SFMReleaseReviewSplitLayout.Motion.END);
            var start = layout.move(side, null, end ? SFMReleaseReviewSplitLayout.Motion.END : SFMReleaseReviewSplitLayout.Motion.HOME, true);
            if (start.isEmpty()) return true;
            anchor = active = new Hit(side, start.get().row(), start.get().column());
            if (allSelected && !shift) { allSelected = false; dragging = false; return true; }
        }
        var destination = layout.move(side, new SFMReleaseReviewSplitLayout.Position(active.row(), active.column()), motion, control);
        destination.ifPresent(position -> active = new Hit(side, position.row(), position.column()));
        if (!shift) anchor = active;
        allSelected = false; dragging = false;
        return true;
    }
    private Hit hit(double x, double y, SnapshotSide fixedSide) {
        if (document.layout().rows().isEmpty()) return null;
        int row = Math.max(0, Math.min(document.layout().rows().size() - 1, (int)Math.floor(y / font.lineHeight)));
        SnapshotSide side = fixedSide != null ? fixedSide : x < rightX - 20 ? SnapshotSide.BEFORE : SnapshotSide.AFTER;
        if (document.pair().source(side).isEmpty()) return null;
        String text = document.layout().rows().get(row).cell(side).map(SFMReleaseReviewSplitLayout.Cell::displayText).orElse("");
        double local = x - (side == SnapshotSide.AFTER ? rightX : 0);
        int column = 0; double advance = 0;
        for (int offset = 0; offset < text.length();) {
            int next = offset + Character.charCount(text.codePointAt(offset));
            int glyphWidth = font.width(text.substring(offset, next));
            if (local < advance + glyphWidth / 2.0) break;
            advance += glyphWidth; column++; offset = next;
        }
        return new Hit(side, row, column);
    }
    public List<SFMTextDocumentDecoration> hits(double x, double y) {
        if (x < 0 || x > width || y < 0 || y >= document.layout().rows().size() * font.lineHeight) return List.of();
        Hit hit = hit(x, y, null);
        if (hit == null) return List.of();
        var cell = document.layout().rows().get(hit.row()).cell(hit.side());
        if (cell.isEmpty()) return List.of();
        String text = cell.orElseThrow().displayText();
        int offset = text.offsetByCodePoints(0, hit.column());
        int byteOffset = cell.orElseThrow().surfaceRange().startByte() + text.substring(0, offset).getBytes(StandardCharsets.UTF_8).length;
        return decorations.stream().filter(value -> value.interactiveObject().isPresent()
                && value.range().start().byteOffset() <= byteOffset && value.range().end().byteOffset() > byteOffset).toList();
    }
    public SFMContextDocumentProjection projection(String editorId) {
        SnapshotSide side = anchor == null ? (document.pair().after().isPresent() ? SnapshotSide.AFTER : SnapshotSide.BEFORE) : anchor.side();
        var source = document.pair().source(side).orElseThrow(() -> new IllegalStateException("The selected split side has no source"));
        SFMPath root = new SFMPath(SFMPath.Kind.CONTRIBUTED, "review", "document",
                List.of(source.documentRevisionId()), Optional.empty(), true);
        var segments = new ArrayList<>(root.segments());
        segments.addAll(List.of(source.path().split("/")));
        SFMPath path = new SFMPath(SFMPath.Kind.CONTRIBUTED, "review", "document", segments, Optional.empty(), false);
        var snapshot = SFMTextDocumentSnapshot.pinned(path, root, source.text(), source.sha256().substring(7),
                Optional.empty(), Optional.empty(), Optional.empty(), SFMTextDocumentLanguage.fromPath(path));
        var ranges = selection().ranges().stream().map(range -> new SFMTextDocumentRange(
                SFMTextDocumentRange.positionAtByteOffset(source.text(), range.range().startByte()),
                SFMTextDocumentRange.positionAtByteOffset(source.text(), range.range().endByte()))).toList();
        var selected = ranges.isEmpty() ? List.<SFMContextSelectionProjection>of()
                : List.of(new SFMContextSelectionProjection("split-selection", ranges, true));
        return SFMContextDocumentProjection.capture(editorId, snapshot, source.text(), false, true, List.of(), selected);
    }
    public boolean containsSelection(double x, double y) {
        Hit hit = hit(x, y, null);
        if (hit == null || (anchor == null && !allSelected) || hit.side() != selectedSide()) return false;
        var cell = document.layout().rows().get(hit.row()).cell(hit.side());
        if (cell.isEmpty()) return false;
        String text = cell.orElseThrow().displayText();
        int position = cell.orElseThrow().source().range().startByte()
                + text.substring(0, text.offsetByCodePoints(0, hit.column())).getBytes(StandardCharsets.UTF_8).length;
        return selection().ranges().stream().anyMatch(range -> range.range().startByte() <= position && range.range().endByte() > position);
    }
    public void render(PoseStack pose, double originX, double originY, double zoom, int viewportHeight) {
        int first = Math.max(0, (int)Math.floor(-originY / zoom / font.lineHeight));
        int last = Math.min(document.layout().rows().size(), (int)Math.ceil((viewportHeight - originY) / zoom / font.lineHeight) + 1);
        pose.pushPose(); pose.translate(originX, originY, 0); pose.scale((float)zoom, (float)zoom, 1);
        font.draw(pose, "Before", 0, -18, 0xFFFFAAAA);
        font.draw(pose, "After", (float)rightX, -18, 0xFFAAFFAA);
        var selected = selection();
        for (int row = first; row < last; row++) for (SnapshotSide side : SnapshotSide.values()) {
            var optional = document.layout().rows().get(row).cell(side);
            if (optional.isEmpty()) continue;
            var cell = optional.orElseThrow();
            String text = cell.displayText();
            int x = side == SnapshotSide.BEFORE ? 0 : (int)rightX, y = row * font.lineHeight;
            for (var decoration : decorations) {
                int start = Math.max((int)decoration.range().start().byteOffset(), cell.surfaceRange().startByte());
                int end = Math.min((int)decoration.range().end().byteOffset(), cell.surfaceRange().startByte() + text.getBytes(StandardCharsets.UTF_8).length);
                if (start >= end) continue;
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                int left = font.width(new String(bytes, 0, start - cell.surfaceRange().startByte(), StandardCharsets.UTF_8));
                int right = font.width(new String(bytes, 0, end - cell.surfaceRange().startByte(), StandardCharsets.UTF_8));
                if (decoration.backgroundArgb().isPresent())
                    GuiComponent.fill(pose, x + left, y, x + right, y + font.lineHeight, decoration.backgroundArgb().getAsInt());
                if (decoration.underlineArgb().isPresent())
                    GuiComponent.fill(pose, x + left, y + font.lineHeight - 1, x + right, y + font.lineHeight, decoration.underlineArgb().getAsInt());
            }
            for (var range : selected.ranges()) if (range.documentRevisionId().equals(cell.source().documentRevisionId())) {
                int start = Math.max(range.range().startByte(), cell.source().range().startByte());
                int end = Math.min(range.range().endByte(), cell.source().range().startByte() + text.getBytes(StandardCharsets.UTF_8).length);
                if (start < end) {
                    byte[] bytes = cell.exactText().getBytes(StandardCharsets.UTF_8);
                    int left = font.width(new String(bytes, 0, start - cell.source().range().startByte(), StandardCharsets.UTF_8));
                    int right = font.width(new String(bytes, 0, end - cell.source().range().startByte(), StandardCharsets.UTF_8));
                    GuiComponent.fill(pose, x + left, y, x + right, y + font.lineHeight, 0x806090EE);
                }
            }
            int byteOffset = cell.surfaceRange().startByte(), advance = 0;
            for (int offset = 0; offset < text.length();) {
                int end = offset + Character.charCount(text.codePointAt(offset));
                String glyph = text.substring(offset, end);
                Component component = Component.literal(glyph);
                int lo = 0, hi = styles.size();
                while (lo < hi) { int mid = (lo + hi) >>> 1; if (styles.get(mid).startByte() <= byteOffset) lo = mid + 1; else hi = mid; }
                if (lo > 0 && styles.get(lo - 1).endByte() > byteOffset)
                    component = component.copy().withStyle(styles.get(lo - 1).formatting().toArray(ChatFormatting[]::new));
                font.draw(pose, component, x + advance, y, 0xFFDDDDDD);
                advance += font.width(glyph); byteOffset += glyph.getBytes(StandardCharsets.UTF_8).length; offset = end;
            }
        }
        if (active != null && !allSelected && active.row() >= first && active.row() < last) {
            String text = document.layout().rows().get(active.row()).cell(active.side())
                    .map(SFMReleaseReviewSplitLayout.Cell::displayText).orElse("");
            int column = Math.min(active.column(), text.codePointCount(0, text.length()));
            int x = (active.side() == SnapshotSide.AFTER ? (int)rightX : 0)
                    + font.width(text.substring(0, text.offsetByCodePoints(0, column)));
            int y = active.row() * font.lineHeight;
            GuiComponent.fill(pose, x, y, x + 1, y + font.lineHeight, 0xFFFFFFFF);
        }
        pose.popPose();
        String status = document.surface().algorithm();
        if (document.surface().fallbackKind().isPresent()) status += " · text fallback";
        if (document.layout().rows().isEmpty()) status += " · no mapped source changes";
        font.draw(pose, status, 6, 6, 0xFFCCCCCC);
        int diagnosticY = 18;
        for (var diagnostic : document.surface().diagnostics()) {
            font.draw(pose, diagnostic.code() + ": " + diagnostic.message(), 6, diagnosticY, 0xFFFFCC88);
            diagnosticY += font.lineHeight;
            if (diagnosticY >= 6 + 4 * font.lineHeight) break;
        }
    }
}
