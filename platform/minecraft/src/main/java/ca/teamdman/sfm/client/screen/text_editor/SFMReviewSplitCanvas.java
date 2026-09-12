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
    public record DecorationHit(SFMTextDocumentDecoration decoration, boolean gutterMarker) {
        public DecorationHit {
            Objects.requireNonNull(decoration, "decoration");
        }
    }
    private record GutterMarker(
            SnapshotSide side,
            int row,
            int lane,
            String text,
            SFMTextDocumentDecoration decoration
    ) { }
    public record MetadataObject(String kind, String headline, List<String> detailLines, String detailsPayload) {
        public MetadataObject {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(headline, "headline");
            detailLines = List.copyOf(detailLines);
            Objects.requireNonNull(detailsPayload, "detailsPayload");
        }
    }
    private final SFMReleaseReviewSurfaceRuntime.SplitDocument document;
    private final Font font;
    private final int leftWidth;
    private final int rightWidth;
    private double gutterWidth = 16;
    private double rightX;
    private double width;
    private Hit anchor, active;
    private boolean dragging;
    private boolean allSelected;
    private List<FormattingSpan> styles = List.of();
    private List<SFMTextDocumentDecoration> decorations = List.of();
    private List<GutterMarker> gutterMarkers = List.of();

    public SFMReviewSplitCanvas(SFMReleaseReviewSurfaceRuntime.SplitDocument document, Font font) {
        this.document = document; this.font = font;
        int measuredLeftWidth = 120, measuredRightWidth = 120;
        for (var row : document.layout().rows()) {
            if (row.before().isPresent()) measuredLeftWidth = Math.max(
                    measuredLeftWidth, font.width(row.before().orElseThrow().displayText()));
            if (row.after().isPresent()) measuredRightWidth = Math.max(
                    measuredRightWidth, font.width(row.after().orElseThrow().displayText()));
        }
        leftWidth = measuredLeftWidth;
        rightWidth = measuredRightWidth;
        updateGeometry();
    }
    public double width() { return width; }
    public double contentLeft() { return document.pair().before().isEmpty() ? rightX : 0; }
    public double contentRight() { return document.pair().after().isEmpty() ? rightX - 40 : width; }
    public double contentHeight() { return Math.max(font.lineHeight, document.layout().rows().size() * font.lineHeight); }
    public void styles(List<FormattingSpan> value) { styles = List.copyOf(value); }
    public void decorations(List<SFMTextDocumentDecoration> value) {
        decorations = List.copyOf(value);
        rebuildGutterMarkers();
    }
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
        double local = x - textX(side);
        int column = 0; double advance = 0;
        for (int offset = 0; offset < text.length();) {
            int next = offset + Character.charCount(text.codePointAt(offset));
            int glyphWidth = font.width(text.substring(offset, next));
            if (local < advance + glyphWidth / 2.0) break;
            advance += glyphWidth; column++; offset = next;
        }
        return new Hit(side, row, column);
    }
    public List<DecorationHit> hits(double x, double y) {
        if (x < 0 || x > width || y < 0 || y >= document.layout().rows().size() * font.lineHeight) return List.of();
        int row = (int)Math.floor(y / font.lineHeight);
        ArrayList<DecorationHit> answer = new ArrayList<>();
        for (GutterMarker marker : gutterMarkers) {
            if (marker.row() != row) continue;
            double left = gutterX(marker.side(), marker.lane(), marker.text());
            double right = left + Math.max(1, font.width(marker.text()));
            if (x >= left && x < right) answer.add(new DecorationHit(marker.decoration(), true));
        }
        if (!answer.isEmpty()) return List.copyOf(answer);
        Hit hit = hit(x, y, null);
        if (hit == null) return List.of();
        var cell = document.layout().rows().get(hit.row()).cell(hit.side());
        if (cell.isEmpty()) return List.of();
        String text = cell.orElseThrow().displayText();
        int offset = text.offsetByCodePoints(0, hit.column());
        int byteOffset = cell.orElseThrow().surfaceRange().startByte() + text.substring(0, offset).getBytes(StandardCharsets.UTF_8).length;
        return decorations.stream().filter(value -> value.interactiveObject().isPresent()
                        && value.range().start().byteOffset() <= byteOffset
                        && value.range().end().byteOffset() > byteOffset)
                .map(value -> new DecorationHit(value, false)).toList();
    }

    /** Canvas-relative Before/After headers remain inspectable while the camera pans. */
    public Optional<MetadataObject> canvasMetadataAt(double x, double y) {
        if (y < -18 || y >= -18 + font.lineHeight) return Optional.empty();
        SnapshotSide side;
        if (x >= textX(SnapshotSide.BEFORE)
                && x < textX(SnapshotSide.BEFORE) + font.width("Before")) side = SnapshotSide.BEFORE;
        else if (x >= textX(SnapshotSide.AFTER)
                && x < textX(SnapshotSide.AFTER) + font.width("After")) side = SnapshotSide.AFTER;
        else return Optional.empty();
        return document.pair().source(side).map(source -> sourceMetadata(side, source));
    }

    /** Panel-relative generated status/diagnostic rows stay addressable above the panning canvas. */
    public Optional<MetadataObject> fixedMetadataAt(double x, double y) {
        String status = statusText();
        if (containsTextRow(x, y, 6, status)) return Optional.of(statusMetadata(status));
        int diagnosticY = 18;
        int index = 0;
        for (var diagnostic : document.surface().diagnostics()) {
            if (diagnosticY >= 6 + 4 * font.lineHeight) break;
            String text = diagnostic.displayText();
            if (containsTextRow(x, y, diagnosticY, text)) {
                return Optional.of(diagnosticMetadata(index, diagnostic));
            }
            diagnosticY += font.lineHeight;
            index++;
        }
        return Optional.empty();
    }

    private boolean containsTextRow(double x, double y, int top, String text) {
        return x >= 6 && x < 6 + font.width(text) && y >= top && y < top + font.lineHeight;
    }

    private MetadataObject sourceMetadata(SnapshotSide side, SFMReleaseReviewSurfaceV1.Source source) {
        String title = side == SnapshotSide.BEFORE ? "Before source" : "After source";
        String payload = "schema: sfm.review-surface-ui-region/1\n"
                + "region-kind: source-header\n"
                + "side: " + side.name().toLowerCase(Locale.ROOT) + "\n"
                + "file-pair-id: " + document.surface().filePairId() + "\n"
                + "document-revision-id: " + source.documentRevisionId() + "\n"
                + "path: " + source.path() + "\n"
                + "language: " + source.language() + "\n"
                + "sha256: " + source.sha256() + "\n";
        return new MetadataObject("source-header", title,
                List.of(source.path(), source.documentRevisionId(), "Right-click for actions"), payload);
    }

    private MetadataObject statusMetadata(String status) {
        var surface = document.surface();
        String payload = "schema: sfm.review-surface-ui-region/1\n"
                + "region-kind: generation-status\n"
                + "file-pair-id: " + surface.filePairId() + "\n"
                + "surface-kind: " + surface.surfaceKind().wireName() + "\n"
                + "algorithm: " + surface.algorithm() + "\n"
                + "outcome: " + surface.outcome().wireName() + "\n"
                + "complete: " + surface.complete() + "\n"
                + "fallback-kind: " + surface.fallbackKind().map(SFMReleaseReviewSurfaceV1.SurfaceKind::wireName)
                .orElse("none") + "\n"
                + "text-sha256: " + surface.textSha256() + "\n";
        return new MetadataObject("generation-status", "Generated diff status",
                List.of(status, "Right-click for actions"), payload);
    }

    private MetadataObject diagnosticMetadata(int index, SFMReleaseReviewSurfaceV1.Diagnostic diagnostic) {
        String payload = "schema: sfm.review-surface-ui-region/1\n"
                + "region-kind: diagnostic\n"
                + "diagnostic-index: " + index + "\n"
                + "file-pair-id: " + document.surface().filePairId() + "\n"
                + "code: " + diagnostic.code() + "\n"
                + "severity: " + diagnostic.severity().wireName() + "\n"
                + "message: " + diagnostic.message() + "\n"
                + "side: " + diagnostic.side().map(value -> value.name().toLowerCase(Locale.ROOT)).orElse("none") + "\n"
                + "source-range: " + diagnostic.sourceRange()
                .map(value -> value.startByte() + ".." + value.endByte()).orElse("none") + "\n";
        return new MetadataObject("diagnostic", "Generated diff diagnostic",
                List.of(diagnostic.displayText(), "Right-click for actions"), payload);
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
        font.draw(pose, "Before", (float)textX(SnapshotSide.BEFORE), -18, 0xFFFFAAAA);
        font.draw(pose, "After", (float)textX(SnapshotSide.AFTER), -18, 0xFFAAFFAA);
        var selected = selection();
        for (int row = first; row < last; row++) for (SnapshotSide side : SnapshotSide.values()) {
            var optional = document.layout().rows().get(row).cell(side);
            if (optional.isEmpty()) continue;
            var cell = optional.orElseThrow();
            String text = cell.displayText();
            int x = (int)textX(side), y = row * font.lineHeight;
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
            int x = (int)textX(active.side())
                    + font.width(text.substring(0, text.offsetByCodePoints(0, column)));
            int y = active.row() * font.lineHeight;
            GuiComponent.fill(pose, x, y, x + 1, y + font.lineHeight, 0xFFFFFFFF);
        }
        for (GutterMarker marker : gutterMarkers) {
            if (marker.row() < first || marker.row() >= last) continue;
            int colour = marker.decoration().underlineArgb().orElse(0xFF60A5FA);
            font.draw(pose, marker.text(), (float)gutterX(marker.side(), marker.lane(), marker.text()),
                    marker.row() * font.lineHeight, colour);
        }
        pose.popPose();
        String status = statusText();
        font.draw(pose, status, 6, 6, 0xFFCCCCCC);
        int diagnosticY = 18;
        for (var diagnostic : document.surface().diagnostics()) {
            font.draw(pose, diagnostic.code() + ": " + diagnostic.message(), 6, diagnosticY, 0xFFFFCC88);
            diagnosticY += font.lineHeight;
            if (diagnosticY >= 6 + 4 * font.lineHeight) break;
        }
    }

    private String statusText() {
        String status = document.surface().algorithm();
        if (document.surface().fallbackKind().isPresent()) status += " · text fallback";
        if (document.layout().rows().isEmpty()) status += " · no mapped source changes";
        return status;
    }

    private double textX(SnapshotSide side) {
        return (side == SnapshotSide.AFTER ? rightX : 0) + gutterWidth;
    }

    private double gutterX(SnapshotSide side, int lane, String marker) {
        int step = Math.max(3, font.width(marker) + 2);
        return (side == SnapshotSide.AFTER ? rightX : 0) + 2.0 + (double)lane * step;
    }

    private void updateGeometry() {
        rightX = gutterWidth + leftWidth + 40;
        width = rightX + gutterWidth + rightWidth + 20;
    }

    private void rebuildGutterMarkers() {
        record Key(SnapshotSide side, String kind, String id) { }
        LinkedHashMap<Key, GutterMarker> first = new LinkedHashMap<>();
        for (SFMTextDocumentDecoration decoration : decorations) {
            if (decoration.gutterMarker().isEmpty() || decoration.interactiveObject().isEmpty()) continue;
            var object = decoration.interactiveObject().orElseThrow();
            for (SnapshotSide side : SnapshotSide.values()) {
                Key key = new Key(side, object.kind(), object.id());
                for (int row = 0; row < document.layout().rows().size(); row++) {
                    var cell = document.layout().rows().get(row).cell(side);
                    if (cell.isEmpty() || !overlaps(decoration, cell.orElseThrow())) continue;
                    GutterMarker current = first.get(key);
                    if (current == null || row < current.row()) {
                        first.put(key, new GutterMarker(
                                side, row, 0, decoration.gutterMarker().orElseThrow(), decoration));
                    }
                    break;
                }
            }
        }
        Map<String, Integer> nextLane = new HashMap<>();
        ArrayList<GutterMarker> positioned = new ArrayList<>();
        int widestRow = 0;
        int widestMarker = 1;
        for (GutterMarker marker : first.values()) {
            String rowKey = marker.side().name() + ":" + marker.row();
            int lane = nextLane.getOrDefault(rowKey, 0);
            nextLane.put(rowKey, lane + 1);
            positioned.add(new GutterMarker(
                    marker.side(), marker.row(), lane, marker.text(), marker.decoration()));
            widestRow = Math.max(widestRow, lane + 1);
            widestMarker = Math.max(widestMarker, font.width(marker.text()) + 2);
        }
        gutterMarkers = List.copyOf(positioned);
        gutterWidth = Math.max(16, 4.0 + (double)widestRow * widestMarker);
        updateGeometry();
    }

    private static boolean overlaps(SFMTextDocumentDecoration decoration, SFMReleaseReviewSplitLayout.Cell cell) {
        long start = decoration.range().start().byteOffset();
        long end = decoration.range().end().byteOffset();
        return start < cell.surfaceRange().endByte() && end > cell.surfaceRange().startByte();
    }
}
