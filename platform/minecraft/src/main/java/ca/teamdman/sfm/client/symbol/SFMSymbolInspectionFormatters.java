package ca.teamdman.sfm.client.symbol;

import ca.teamdman.sfm.client.text_editor.SFMTextDocumentPosition;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Deterministic granular and aggregate clipboard projections of one snapshot. */
public final class SFMSymbolInspectionFormatters {
    public static final String DETAILS_SCHEMA = "sfm.symbol-inspection-details/1";

    public enum Projection {
        DETAILS("details"),
        FILE("file"),
        LINE("line"),
        COLUMN("column"),
        BOUNDS("bounds"),
        LOGICAL_PATH("logical-path"),
        ACCESS_TRANSFORMER_REFERENCE("access-transformer-reference");

        private final String path;

        Projection(String path) {
            this.path = path;
        }

        public String path() {
            return path;
        }
    }

    private SFMSymbolInspectionFormatters() {
    }

    public static String format(SFMSymbolInspectionSnapshot snapshot, Projection projection) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(projection, "projection");
        return switch (projection) {
            case DETAILS -> details(snapshot);
            case FILE -> file(snapshot);
            case LINE -> Integer.toString(snapshot.point().text().line() + 1);
            case COLUMN -> Integer.toString(snapshot.point().text().column() + 1);
            case BOUNDS -> bounds(snapshot);
            case LOGICAL_PATH -> logicalPath(snapshot);
            case ACCESS_TRANSFORMER_REFERENCE -> accessTransformerReference(snapshot);
        };
    }

    private static String details(SFMSymbolInspectionSnapshot snapshot) {
        StringBuilder answer = new StringBuilder()
                .append("schema: ").append(DETAILS_SCHEMA).append('\n')
                .append("snapshot-schema: ").append(snapshot.schema()).append('\n')
                .append("file: ").append(singleLine(file(snapshot))).append('\n')
                .append("line: ").append(format(snapshot, Projection.LINE)).append('\n')
                .append("column: ").append(format(snapshot, Projection.COLUMN)).append('\n')
                .append("bounds:\n").append(indent(bounds(snapshot))).append('\n')
                .append("logical-path: ").append(singleLine(logicalPath(snapshot))).append('\n')
                .append("access-transformer-reference: ")
                .append(singleLine(accessTransformerReference(snapshot))).append('\n')
                .append("editor-id: ").append(escape(snapshot.document().editorId())).append('\n')
                .append("origin-id: ").append(escape(snapshot.originId())).append('\n')
                .append("document-state: ").append(snapshot.document().state()).append('\n')
                .append("resolver-id: ").append(optional(snapshot.document().resolverId(), "unavailable")).append('\n')
                .append("root-id: ").append(optional(snapshot.document().rootId(), "unavailable")).append('\n')
                .append("authorized-root: ")
                .append(optional(snapshot.document().authorizedRoot(), "unavailable")).append('\n')
                .append("root-relative-path: ")
                .append(optional(snapshot.document().rootRelativePath(), "unavailable")).append('\n')
                .append("report-path: ").append(optional(snapshot.document().reportPath(), "unavailable")).append('\n')
                .append("source-set: ").append(optional(snapshot.document().sourceSet(), "unavailable")).append('\n')
                .append("baseline-sha256: ")
                .append(snapshot.document().baselineSha256().map(value -> "sha256:" + value).orElse("unavailable"))
                .append('\n')
                .append("current-sha256: sha256:").append(snapshot.document().currentSha256()).append('\n')
                .append("dirty: ").append(snapshot.document().dirty()).append('\n')
                .append("read-only: ").append(snapshot.document().readOnly()).append('\n')
                .append("byte-offset: ").append(snapshot.point().text().byteOffset()).append('\n')
                .append("selected-text: ").append(quote(snapshot.region().selectedText())).append('\n')
                .append("semantic-region-id: ")
                .append(optional(snapshot.region().semanticRegionId(), "unavailable")).append('\n')
                .append("semantic-kind: ").append(snapshot.region().semanticKind()).append('\n')
                .append("confidence: ").append(snapshot.confidence()).append('\n')
                .append("completeness: ").append(snapshot.completeness()).append('\n')
                .append("symbols: ").append(snapshot.symbols().size()).append('\n');
        for (int index = 0; index < snapshot.symbols().size(); index++) {
            SFMSymbolInspectionSnapshot.Symbol symbol = snapshot.symbols().get(index);
            answer.append("symbol[").append(index).append("].kind: ").append(symbol.kind()).append('\n')
                    .append("symbol[").append(index).append("].owner: ").append(escape(symbol.owner())).append('\n')
                    .append("symbol[").append(index).append("].name: ").append(escape(symbol.name())).append('\n')
                    .append("symbol[").append(index).append("].descriptor: ")
                    .append(optional(symbol.descriptor(), "unavailable")).append('\n')
                    .append("symbol[").append(index).append("].qualified-name: ")
                    .append(escape(symbol.qualifiedName())).append('\n')
                    .append("symbol[").append(index).append("].canonical-selector: ")
                    .append(escape(symbol.canonicalSelector())).append('\n');
        }
        appendOutlinks(answer, "definition-outlink", snapshot.definitionOutlinks());
        appendOutlinks(answer, "reference-outlink", snapshot.referenceOutlinks());
        answer.append("capture-generation: ").append(snapshot.captureGeneration()).append('\n')
                .append("workspace-generation: ").append(snapshot.workspaceGeneration()).append('\n')
                .append("focus-generation: ").append(snapshot.focusGeneration()).append('\n')
                .append("contributor-generation: ").append(snapshot.contributorGeneration()).append('\n')
                .append("content-generation: ").append(snapshot.contentGeneration()).append('\n')
                .append("selection-generation: ").append(snapshot.selectionGeneration()).append('\n')
                .append("relation-generation: ").append(snapshot.relationGeneration()).append('\n')
                .append("replay-command: ").append(snapshot.replayCommand()).append('\n')
                .append("diagnostics: ").append(snapshot.diagnostics().size());
        for (int index = 0; index < snapshot.diagnostics().size(); index++) {
            answer.append('\n').append("diagnostic[").append(index).append("]: ")
                    .append(escape(snapshot.diagnostics().get(index)));
        }
        return answer.toString();
    }

    private static String file(SFMSymbolInspectionSnapshot snapshot) {
        return snapshot.document().address()
                .or(() -> snapshot.document().rootRelativePath())
                .orElse("unavailable: no resolver address or root-relative path was captured");
    }

    private static String bounds(SFMSymbolInspectionSnapshot snapshot) {
        SFMTextDocumentRange range = snapshot.region().textRange();
        StringBuilder answer = new StringBuilder()
                .append("text-utf8: [").append(range.start().byteOffset()).append(',')
                .append(range.end().byteOffset()).append(")\n")
                .append("text-unicode: ").append(position(range.start())).append(" -> ")
                .append(position(range.end())).append('\n')
                .append("canvas-point: ")
                .append(snapshot.point().canvas()
                        .map(value -> point(value.x(), value.y()))
                        .orElse("unavailable"))
                .append('\n')
                .append("canvas-regions: ")
                .append(rectangles(snapshot.region().canvasBounds())).append('\n')
                .append("screen-local-regions: ")
                .append(rectangles(snapshot.region().localScreenBounds())).append('\n')
                .append("screen-global-regions: ")
                .append(rectangles(snapshot.region().globalScreenBounds())).append('\n')
                .append("framebuffer-pixel-regions: ")
                .append(rectangles(snapshot.region().physicalPixelBounds())).append('\n')
                .append("screen-local-viewport: ")
                .append(snapshot.region().localScreenViewport().map(SFMSymbolInspectionFormatters::rectangle)
                        .orElse("unavailable"))
                .append('\n')
                .append("selected-glyphs: ").append(snapshot.region().glyphs().size());
        for (int index = 0; index < snapshot.region().glyphs().size(); index++) {
            SFMSymbolInspectionSnapshot.Glyph glyph = snapshot.region().glyphs().get(index);
            answer.append('\n').append("glyph[").append(index).append("]: ordinal=")
                    .append(glyph.ordinal())
                    .append(", utf16=").append(glyph.utf16Offset())
                    .append(", text=").append(quote(glyph.text()))
                    .append(", canvas=").append(rectangle(glyph.canvasBounds()));
        }
        return answer.toString();
    }

    private static String logicalPath(SFMSymbolInspectionSnapshot snapshot) {
        if (snapshot.region().logicalPath().isEmpty()) {
            return "unavailable: no exact semantic ancestor path was published";
        }
        return String.join(" > ", snapshot.region().logicalPath());
    }

    private static String accessTransformerReference(SFMSymbolInspectionSnapshot snapshot) {
        return snapshot.exactAccessTransformerReference()
                .orElseGet(() -> "unavailable: " + snapshot.accessTransformerUnavailableReason());
    }

    private static void appendOutlinks(
            StringBuilder answer,
            String label,
            List<SFMSymbolInspectionSnapshot.Outlink> values
    ) {
        answer.append(label).append("s: ").append(values.size()).append('\n');
        for (int index = 0; index < values.size(); index++) {
            var value = values.get(index);
            String prefix = label + "[" + index + "].";
            answer.append(prefix).append("id: ").append(value.id()).append('\n')
                    .append(prefix).append("destination: ")
                    .append(optional(value.destinationQuery(), "unavailable")).append('\n')
                    .append(prefix).append("provider: ").append(value.providerId())
                    .append('@').append(value.providerGeneration()).append('\n')
                    .append(prefix).append("confidence: ").append(value.confidence()).append('\n')
                    .append(prefix).append("completeness: ").append(value.completeness()).append('\n')
                    .append(prefix).append("projection: ").append(value.recommendedProjection()).append('\n')
                    .append(prefix).append("reason: ").append(escape(value.reason())).append('\n')
                    .append(prefix).append("provenance: ").append(escape(value.provenance())).append('\n');
        }
    }

    private static String rectangles(List<SFMSymbolInspectionSnapshot.Rectangle> values) {
        if (values.isEmpty()) return "unavailable";
        ArrayList<String> formatted = new ArrayList<>(values.size());
        for (SFMSymbolInspectionSnapshot.Rectangle value : values) formatted.add(rectangle(value));
        return String.join("; ", formatted);
    }

    private static String rectangle(SFMSymbolInspectionSnapshot.Rectangle value) {
        return "[" + number(value.left()) + ',' + number(value.top()) + " -> "
                + number(value.right()) + ',' + number(value.bottom()) + ")";
    }

    private static String point(double x, double y) {
        return "(" + number(x) + ',' + number(y) + ')';
    }

    private static String position(SFMTextDocumentPosition value) {
        return "line " + (value.line() + 1) + ", column " + (value.column() + 1)
                + ", byte " + value.byteOffset();
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) <= Long.MAX_VALUE) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.6f", value).replaceFirst("0+$", "").replaceFirst("\\.$", "");
    }

    private static String optional(Optional<String> value, String unavailable) {
        return value.map(SFMSymbolInspectionFormatters::escape).orElse(unavailable);
    }

    private static String indent(String value) {
        return "  " + value.replace("\n", "\n  ");
    }

    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String quote(String value) {
        return '"' + escape(value) + '"';
    }

    private static String escape(String value) {
        StringBuilder answer = new StringBuilder();
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            switch (codePoint) {
                case '\\' -> answer.append("\\\\");
                case '"' -> answer.append("\\\"");
                case '\n' -> answer.append("\\n");
                case '\r' -> answer.append("\\r");
                case '\t' -> answer.append("\\t");
                default -> {
                    if (Character.isISOControl(codePoint)) {
                        answer.append(String.format(Locale.ROOT, "\\u%04x", codePoint));
                    } else {
                        answer.appendCodePoint(codePoint);
                    }
                }
            }
            index += Character.charCount(codePoint);
        }
        return answer.toString();
    }
}
