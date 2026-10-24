package ca.teamdman.sfm.client.inspection;

import ca.teamdman.sfm.client.action.*;
import ca.teamdman.sfm.client.explorer.*;
import ca.teamdman.sfm.client.explorer.action.*;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryTextExplorerResolver;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Leased comparison evidence; native comparison runs off the render thread. */
public final class SFMInspectionRuntime {
    public static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sfm-explorer-inspection"); thread.setDaemon(true); return thread;
    });
    // Browsing an existing result must not queue behind a native comparison process.
    private static final ExecutorService READS = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sfm-inspection-read"); thread.setDaemon(true); return thread;
    });
    private static final SFMInMemoryTextExplorerResolver RESOLVER = new SFMInMemoryTextExplorerResolver(SFMInspectionTree.SCHEME, READS, 128);
    private static final Map<SFMPath, SFMInspectionTree.Surface> SURFACES = new ConcurrentHashMap<>();
    private SFMInspectionRuntime() {}

    public static Optional<SFMInspectionTree.Surface> surface(SFMTextDocumentSnapshot snapshot) {
        return snapshot.path().map(SURFACES::get).filter(value -> snapshot.text().equals(value.surface().text()));
    }
    public static Map<String, SFMReviewSessionV1.DocumentRevision> sources(SFMInspectionTree.Surface value) {
        var result = new LinkedHashMap<String, SFMReviewSessionV1.DocumentRevision>();
        java.util.stream.Stream.concat(value.request().filePair().before().stream(), value.request().filePair().after().stream())
                .forEach(source -> result.put(source.documentRevisionId(), new SFMReviewSessionV1.DocumentRevision(
                        source.documentRevisionId(), source.path(), "utf-8", source.sha256().substring(7), source.text())));
        return result;
    }
    public record PreparedWorkspace(List<SFMInspectionTree> trees,
                                    List<SFMInMemoryTextExplorerResolver.PreparedMount> mounts) {}
    public static PreparedWorkspace read(Path path) throws Exception {
        var trees = SFMInspectionTree.read(path, UUID.randomUUID().toString());
        var mounts = trees.stream().map(tree -> RESOLVER.prepareMount(tree.root().authority(), tree.nodes())).toList();
        return new PreparedWorkspace(trees, mounts);
    }
    public static PreparedWorkspace compare(Path old, Path newer) throws Exception {
        old = old.toAbsolutePath().normalize(); newer = newer.toAbsolutePath().normalize();
        if (old.equals(newer)) throw new IllegalArgumentException("Select two distinct inputs");
        if (Files.isSymbolicLink(old) || Files.isSymbolicLink(newer)) throw new IllegalArgumentException("Comparison inputs must not be symbolic links");
        var request = new com.google.gson.JsonObject();
        request.addProperty("schema", "sfm:explorer-inspection-request@1");
        var comparisons = new com.google.gson.JsonArray(); request.add("comparisons", comparisons);
        var wrapper = new com.google.gson.JsonObject(); comparisons.add(wrapper);
        var input = new com.google.gson.JsonObject();
        input.addProperty("label", inputLabel(old, newer) + " → " + inputLabel(newer, old));
        if (Files.isDirectory(old) && Files.isDirectory(newer)) {
            wrapper.add("Source", input);
            var source = new com.google.gson.JsonObject(); input.add("request", source);
            source.addProperty("schema", "sfm:source-comparison-request@1");
            source.add("left", directory(old)); source.add("right", directory(newer));
            var policy = new com.google.gson.JsonObject(); policy.addProperty("schema_version", 1);
            for (String flag : List.of("allow_generated_banner", "normalize_crlf", "allow_final_newline_difference", "allow_settings_project_identity_override")) policy.addProperty(flag, false);
            source.add("policy", policy);
        } else if (old.toString().toLowerCase(Locale.ROOT).endsWith(".jar") && newer.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            wrapper.add("Jar", input); input.addProperty("old", old.toString()); input.addProperty("new", newer.toString());
            input.add("class_evidence", com.google.gson.JsonNull.INSTANCE);
        } else throw new IllegalArgumentException("Show diff requires two directories/working trees or two JAR files");
        // The report producer has an explicit free-space admission check. A system
        // temporary filesystem can be much smaller than the selected game workspace.
        Path scratch = Path.of(System.getProperty("sfm.inspection.scratch", ".sfm-inspection-work"))
                .toAbsolutePath().normalize();
        if (Files.isSymbolicLink(scratch)) throw new java.io.IOException("Comparison scratch directory must not be a symbolic link");
        if (wrapper.has("Source")) {
            Path ancestor = scratch;
            while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) ancestor = ancestor.getParent();
            Path resolved = ancestor.toRealPath().resolve(ancestor.relativize(scratch)).normalize();
            if (resolved.startsWith(old.toRealPath()) || resolved.startsWith(newer.toRealPath()))
                throw new java.io.IOException("Comparison scratch must be outside both selected trees; set sfm.inspection.scratch to a writable external directory");
        }
        Files.createDirectories(scratch);
        Path requestFile = Files.createTempFile(scratch, "sfm-inspection-", ".request.json");
        Path output = requestFile.resolveSibling(requestFile.getFileName() + ".result.json");
        Path log = requestFile.resolveSibling(requestFile.getFileName() + ".log");
        Process process = null;
        Path classDirectory = null;
        try {
            String producer = System.getProperty("sfm.inspection.classProducer", "");
            if (wrapper.has("Jar") && !producer.isBlank() && Files.isRegularFile(old) && Files.isRegularFile(newer)) {
                classDirectory = Files.createTempDirectory(scratch, "sfm-inspection-classes-");
                boolean windows = System.getProperty("os.name", "").startsWith("Windows");
                Path javap = Path.of(System.getProperty("java.home"), "bin", windows ? "javap.exe" : "javap");
                process = new ProcessBuilder(System.getProperty("sfm.inspection.python", windows ? "python" : "python3"),
                        producer, "--old", old.toString(), "--new", newer.toString(), "--javap", javap.toString(),
                        "--output", classDirectory.toString()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(log.toFile()).start();
                if (!process.waitFor(120, TimeUnit.SECONDS)) throw new java.io.IOException("Class evidence exceeded 120 seconds");
                if (process.exitValue() != 0) {
                    try (var stream = Files.newInputStream(log)) {
                        throw new java.io.IOException("Class evidence producer failed: " + new String(stream.readNBytes(4096), StandardCharsets.UTF_8));
                    }
                }
                input.addProperty("class_evidence", classDirectory.resolve("classes/pair.json").toString());
            }
            Files.writeString(requestFile, request.toString(), StandardCharsets.UTF_8);
            String executable = System.getProperty("sfm.inspection.executable",
                    System.getProperty("os.name", "").startsWith("Windows") ? "sfm-propagate-changes.exe" : "sfm-propagate-changes");
            process = new ProcessBuilder(executable, "--output-format", "json", "source", "inspect-prepare",
                    "--request", requestFile.toString(), "--output", output.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(log.toFile()).start();
            if (!process.waitFor(120, TimeUnit.SECONDS)) throw new java.io.IOException("Comparison exceeded 120 seconds");
            if (process.exitValue() != 0) {
                String details;
                try (var stream = Files.newInputStream(log)) { details = new String(stream.readNBytes(4096), StandardCharsets.UTF_8); }
                throw new java.io.IOException("Comparison failed: " + details);
            }
            return read(output);
        } finally {
            if (process != null && process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroy); process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
            }
            Files.deleteIfExists(requestFile); Files.deleteIfExists(output); Files.deleteIfExists(log);
            if (classDirectory != null) try (var paths = Files.walk(classDirectory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static com.google.gson.JsonObject directory(Path path) {
        var wrapper = new com.google.gson.JsonObject(); var body = new com.google.gson.JsonObject();
        body.addProperty("root", path.toString()); wrapper.add("Directory", body); return wrapper;
    }
    /** Keep both directions distinguishable when projection roots share a basename. */
    public static String inputLabel(Path value, Path other) {
        value = value.toAbsolutePath().normalize(); other = other.toAbsolutePath().normalize();
        if (value.getFileName() == null || other.getFileName() == null) return value.toString();
        if (!value.getFileName().equals(other.getFileName())) return value.getFileName().toString();
        Path common = value.getParent();
        while (common != null && !other.getParent().startsWith(common)) common = common.getParent();
        return common == null ? value.toString() : common.relativize(value).toString();
    }
    public static void open(PreparedWorkspace prepared, SFMClientActionContext context, boolean append) {
        var trees = prepared.trees();
        var runtime = SFMExplorerRuntime.get(); runtime.registerResolverIfAbsent(RESOLVER);
        var leases = new ArrayList<SFMInMemoryTextExplorerResolver.MountLease>();
        Runnable dispose = () -> { trees.forEach(tree -> tree.surfaces().keySet().forEach(SURFACES::remove)); leases.forEach(SFMInMemoryTextExplorerResolver.MountLease::close); };
        SFMExplorerPanel panel = null; boolean owned = false;
        try {
            for (int i = 0; i < trees.size(); i++) { leases.add(RESOLVER.mount(prepared.mounts().get(i))); SURFACES.putAll(trees.get(i).surfaces()); }
            if (append && context.originatingHost() instanceof SFMScreenMultiplexer workspace && context.originatingPanelId() != null
                    && workspace.panelInstance(context.originatingPanelId()) instanceof SFMExplorerPanel existing) {
                panel = existing;
                for (var tree : trees) {
                    var result = runtime.execute(new SFMExplorerActionRequest(
                            SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, panel.explorerId().value()),
                            new SFMExplorerActionRequest.RootAdd(tree.root()), SFMExplorerActionRequest.IfNoMatch.FAIL));
                    if (result.status() != SFMExplorerActionResult.Status.SUCCEEDED) throw new IllegalStateException("Could not add comparison root: " + result);
                }
                runtime.appendProjectionLease(panel.explorerId(), dispose); owned = true;
            } else {
                var roots = new LinkedHashSet<SFMPath>(); trees.forEach(tree -> roots.add(tree.root()));
                panel = (SFMExplorerPanel) runtime.openProjectedScene(new SFMPathExpression.Literal(trees.get(0).root()), roots, false);
                runtime.ownProjection(panel.explorerId(), dispose); owned = true;
                if (OpenPanelAction.openPanel(context, panel, OpenPanelAction.Direction.FOCUSED) == 0) {
                    runtime.discardExplorer(panel.explorerId()); throw new IllegalStateException("Could not open comparison workspace");
                }
            }
        } finally { if (!owned) dispose.run(); }
    }
}
