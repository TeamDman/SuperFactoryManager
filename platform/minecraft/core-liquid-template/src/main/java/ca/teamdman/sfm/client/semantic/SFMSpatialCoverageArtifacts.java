package ca.teamdman.sfm.client.semantic;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Adjacent JSON/compact-map/heatmap artifacts for one reproducible run. */
public final class SFMSpatialCoverageArtifacts {
    public record Written(Path report, Path map, Path heatmap) {
    }

    private SFMSpatialCoverageArtifacts() {
    }

    public static Written write(SFMSpatialCoverageService.Run run, Path directory) throws IOException {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(directory, "directory");
        Files.createDirectories(directory);
        Path report = directory.resolve("coverage-report.json");
        Path map = directory.resolve("coverage-map.json");
        Path heatmap = directory.resolve("coverage-heatmap.png");
        writeAtomically(report, SFMSpatialSemanticJsonCodec.encodeCoverageReport(run.report()));
        writeAtomically(map, compactMap(run));
        writeHeatmapAtomically(heatmap, run.samples());
        return new Written(report, map, heatmap);
    }

    private static String compactMap(SFMSpatialCoverageService.Run run) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "sfm.spatial-coverage-map/1");
        root.addProperty("request_id", run.request().requestId());
        root.addProperty("policy", run.report().policy());
        root.addProperty("policy_version", run.report().policyVersion());
        root.addProperty("seed", run.report().seed());
        root.addProperty("budget", run.report().budget());
        JsonArray samples = new JsonArray();
        for (SFMSpatialCoverageService.Sample sample : run.samples()) {
            JsonObject json = new JsonObject();
            json.addProperty("document_address", sample.documentAddress());
            json.addProperty("x", sample.x());
            json.addProperty("y", sample.y());
            json.addProperty("sequence", sample.sequence());
            json.addProperty("cache_hit", sample.cacheHit());
            json.addProperty("classification", sample.observation().probe().classification().status().wireName());
            json.addProperty("certified_region_id", sample.observation().probe().certifiedRegion().id());
            json.addProperty("provider_branch", sample.observation().providerBranch());
            json.addProperty("navigation", sample.observation().probe().outlinks().stream().anyMatch(outlink ->
                    outlink.intent() == SFMSpatialSemanticContract.Intent.NAVIGATE));
            samples.add(json);
        }
        root.add("samples", samples);
        JsonArray partitions = new JsonArray();
        run.certifiedPartitions().forEach(partition -> {
            JsonObject json = new JsonObject();
            json.addProperty("document_address", partition.documentAddress());
            json.addProperty("region_id", partition.regionId());
            json.addProperty("signature", partition.signature());
            json.addProperty("reused_points", partition.reusedPoints());
            json.addProperty("validated", partition.validated());
            partitions.add(json);
        });
        root.add("certified_partitions", partitions);
        JsonArray exceptions = new JsonArray();
        run.exceptions().forEach(exception -> {
            JsonObject json = new JsonObject();
            json.addProperty("document_address", exception.documentAddress());
            json.addProperty("x", exception.x());
            json.addProperty("y", exception.y());
            json.addProperty("kind", exception.kind());
            json.addProperty("detail", exception.detail());
            exceptions.add(json);
        });
        root.add("exceptions", exceptions);
        JsonArray next = new JsonArray();
        run.nextCandidates().forEach(next::add);
        root.add("next_candidates", next);
        return new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create().toJson(root) + "\n";
    }

    private static void writeHeatmapAtomically(
            Path destination,
            List<SFMSpatialCoverageService.Sample> samples
    ) throws IOException {
        int width = samples.stream().max(Comparator.comparingInt(SFMSpatialCoverageService.Sample::x))
                .map(sample -> sample.x() + 1).orElse(1);
        int height = samples.stream().max(Comparator.comparingInt(SFMSpatialCoverageService.Sample::y))
                .map(sample -> sample.y() + 1).orElse(1);
        if (width > 8192 || height > 8192 || (long) width * height > 16_777_216L) {
            throw new IOException("Coverage heatmap exceeds bounded raster dimensions: " + width + "x" + height);
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (SFMSpatialCoverageService.Sample sample : samples) {
            boolean navigation = sample.observation().probe().outlinks().stream().anyMatch(outlink ->
                    outlink.intent() == SFMSpatialSemanticContract.Intent.NAVIGATE);
            int color = switch (sample.observation().probe().classification().status()) {
                case UNCLASSIFIED -> 0xFFFF2255;
                case UNSUPPORTED -> 0xFFFFAA22;
                case EXPLICIT_NO_ACTION -> 0xFF777777;
                case ACTIONABLE -> navigation ? 0xFF44DD77 : 0xFFCC66FF;
            };
            image.setRGB(sample.x(), sample.y(), color);
        }
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        if (!ImageIO.write(image, "png", temporary.toFile())) throw new IOException("PNG writer unavailable");
        moveReplace(temporary, destination);
    }

    private static void writeAtomically(Path destination, String content) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        moveReplace(temporary, destination);
    }

    private static void moveReplace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
