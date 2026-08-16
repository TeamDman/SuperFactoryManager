package ca.teamdman.sfm.client.screen;

import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Deterministic command-line stage probe for the exact EditorV3 large-document
 * witness. It deliberately exercises product code rather than a facsimile.
 */
public final class SFMDrawCanvasPerformanceProbe {
    private static final int LINE_HEIGHT = 9;
    private static final int VIEWPORT_WIDTH = 1_280;
    private static final int VIEWPORT_HEIGHT = 720;

    private SFMDrawCanvasPerformanceProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: SFMDrawCanvasPerformanceProbe <source.java> <output.json>");
        }
        Path sourcePath = Path.of(args[0]).toAbsolutePath().normalize();
        Path outputPath = Path.of(args[1]).toAbsolutePath().normalize();
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        ThreadMXBean allocationBean = allocationBean();

        long loadAllocatedBefore = allocatedBytes(allocationBean);
        long loadStarted = System.nanoTime();
        SFMDrawCanvasModel model = new SFMDrawCanvasModel();
        model.replaceText(source, SFMDrawCanvasPerformanceProbe::glyphWidth, LINE_HEIGHT);
        long loadNanos = System.nanoTime() - loadStarted;
        long loadAllocated = allocatedBytes(allocationBean) - loadAllocatedBefore;

        long projectionAllocatedBefore = allocatedBytes(allocationBean);
        long projectionStarted = System.nanoTime();
        SFMDrawCanvasDocumentIndex index = model.documentIndex(glyphWidth(" "), LINE_HEIGHT);
        var projection = index.projection();
        long projectionNanos = System.nanoTime() - projectionStarted;
        long projectionAllocated = allocatedBytes(allocationBean) - projectionAllocatedBefore;

        long frameStarted = System.nanoTime();
        SFMDrawCanvasDocumentIndex.VisibleSlice visibleSlice = index.visible(
                new SFMDrawCanvasDocumentIndex.Viewport(0, 0, VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
        );
        int visited = visibleSlice.glyphsVisited();
        int visible = visibleSlice.glyphs().size();
        long frameScanNanos = System.nanoTime() - frameStarted;

        long hitStarted = System.nanoTime();
        double hitX = 240;
        double hitY = LINE_HEIGHT * 400.0D;
        SFMDrawCanvasDocumentIndex.VisibleSlice hitSlice = index.visible(
                new SFMDrawCanvasDocumentIndex.Viewport(hitX, hitY, Math.nextUp(hitX), Math.nextUp(hitY))
        );
        SFMDrawCanvasModel.CanvasGlyph hit = index.glyphAt(hitX, hitY).orElse(null);
        int hitVisited = hitSlice.glyphsVisited();
        long hitNanos = System.nanoTime() - hitStarted;

        if (!normalize(source).equals(normalize(projection.text()))) {
            throw new IllegalStateException("Probe load/projection did not preserve the source text");
        }
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, """
                {
                  \"schema\": \"sfm.editor-v3-performance-probe/1\",
                  \"captured_at\": \"%s\",
                  \"source_path\": \"%s\",
                  \"source_sha256\": \"%s\",
                  \"source_bytes\": %d,
                  \"source_lines\": %d,
                  \"java_version\": \"%s\",
                  \"os\": \"%s\",
                  \"viewport\": {\"width\": %d, \"height\": %d, \"line_height\": %d},
                  \"cold_load\": {\"nanos\": %d, \"allocated_bytes\": %d, \"glyphs\": %d},
                  \"projection\": {\"nanos\": %d, \"allocated_bytes\": %d, \"utf16_units\": %d},
                  \"frame_scan\": {\"nanos\": %d, \"glyphs_visited\": %d, \"visible_glyphs\": %d},
                  \"pointer_hit_scan\": {\"nanos\": %d, \"glyphs_visited\": %d, \"hit\": %s}
                }
                """.formatted(
                Instant.now(),
                json(sourcePath.toString()),
                sha256(source),
                source.getBytes(StandardCharsets.UTF_8).length,
                source.lines().count(),
                json(System.getProperty("java.version")),
                json(System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")),
                VIEWPORT_WIDTH,
                VIEWPORT_HEIGHT,
                LINE_HEIGHT,
                loadNanos,
                loadAllocated,
                model.glyphs().size(),
                projectionNanos,
                projectionAllocated,
                projection.text().length(),
                frameScanNanos,
                visited,
                visible,
                hitNanos,
                hitVisited,
                hit == null ? "false" : "true"
        ), StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "EDITOR_V3_PROBE output=%s load_ms=%.3f projection_ms=%.3f glyphs=%d visited=%d visible=%d%n",
                outputPath,
                loadNanos / 1_000_000.0D,
                projectionNanos / 1_000_000.0D,
                model.glyphs().size(),
                visited,
                visible);
    }

    private static int glyphWidth(String glyph) {
        if (glyph.isEmpty()) return 0;
        int codePoint = glyph.codePointAt(0);
        if (codePoint == '\t') return 16;
        if (codePoint == ' ') return 4;
        if ("ilI.,'`:;|!".indexOf(codePoint) >= 0) return 2;
        if ("MW@#%&".indexOf(codePoint) >= 0) return 7;
        return 6;
    }

    private static ThreadMXBean allocationBean() {
        if (!(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean)) return null;
        if (!bean.isThreadAllocatedMemorySupported()) return null;
        if (!bean.isThreadAllocatedMemoryEnabled()) bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }

    private static long allocatedBytes(ThreadMXBean bean) {
        return bean == null ? -1L : bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replaceAll("[ \\t]+(?=\\n|$)", "").strip();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
