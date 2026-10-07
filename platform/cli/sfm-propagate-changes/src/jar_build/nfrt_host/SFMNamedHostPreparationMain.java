package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonParser;
import net.neoforged.neoform.runtime.cli.RunNeoFormCommand;
import net.neoforged.neoform.runtime.config.neoforge.NeoForgeConfig;
import net.neoforged.neoform.runtime.engine.NeoFormEngine;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;

/** Owned graph/store integration with bounded built-in and original extractServer proofs. */
public final class SFMNamedHostPreparationMain {
    public static void main(String[] args) throws Exception {
        boolean childToolProof = args.length == 3 && args[2].equals("child-tool-proof");
        boolean compileProof = args.length == 3 && args[2].equals("minecraft-compile-proof");
        boolean sourceTransformProof = compileProof || args.length == 3 && args[2].equals("source-transform-proof");
        boolean sourceRecipeProof = sourceTransformProof || args.length == 3 && args[2].equals("source-recipe-proof");
        boolean producerProof = sourceRecipeProof || childToolProof || args.length == 3 && args[2].equals("builtin-producer-proof");
        if ((args.length != 2 && !producerProof) || !args[1].matches("sha256:[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Expected owned invocation root and exact contract digest");
        }
        var channel = new SFMHostChannel(System.in, System.out, args[1]);
        // Upstream progress and tool logs must never enter the protocol stream.
        System.setOut(System.err);
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        byte[] contractBytes = bounded(root.resolve("contract.json"), 4 * 1024 * 1024);
        if (!sha256(contractBytes).equals(args[1])) throw new IllegalArgumentException("Contract digest changed");
        var transfer = JsonParser.parseString(new String(contractBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        if (!root.getFileName().toString().equals("nfrt-" + transfer.get("invocation_id").getAsString())) {
            throw new IllegalArgumentException("Contract belongs to another invocation root");
        }
        Path version = null;
        String userdev = sourceUserdev(transfer);
        for (var item : transfer.getAsJsonArray("inputs")) {
            var row = item.getAsJsonObject();
            if (row.get("request_key").getAsString().equals("version_json")) {
                String relative = row.get("snapshot_relative_path").getAsString();
                if (!relative.matches("inputs/artifacts/[0-9]{4}\\.bin") || version != null) {
                    throw new IllegalArgumentException("Invalid original version JSON slot");
                }
                version = root.resolve(relative);
                byte[] bytes = bounded(version, 32 * 1024 * 1024);
                if (bytes.length != row.get("bytes").getAsLong()
                        || !sha256(bytes).equals(row.get("full_sha256").getAsString())) {
                    throw new IllegalArgumentException("Original version JSON changed");
                }
            }
        }
        if (version == null) throw new IllegalArgumentException("Missing original parent inputs");
        var contract = SFMRustContractConverter.convert(transfer, root, MinecraftVersionManifest.from(version));
        try (var engine = SFMNamedEngineFactory.createUnstartedEngine(contract)) {
            engine.setJavaHome(Path.of(System.getProperty("java.home")));
            var userdevFile = engine.addManagedResource(new JarFile(engine.getArtifactManager().get(userdev).path().toFile()));
            var config = NeoForgeConfig.from(userdevFile);
            engine.loadNeoFormData(engine.getArtifactManager().get(config.neoformArtifact()).path(), "joined");
            var configure = RunNeoFormCommand.class.getDeclaredMethod("applyNeoForgeProcessTransforms",
                    NeoFormEngine.class, JarFile.class, NeoForgeConfig.class);
            configure.setAccessible(true);
            configure.invoke(null, engine, userdevFile, config);
            SFMHostProjectInputs.install(engine, root, args[1], transfer.get("preparation_identity").getAsString());
            var nodes = List.copyOf(engine.getGraph().getNodes());
            bindExactVersionDiscovery(nodes, transfer.get("minecraft_version").getAsString());
            for (var node : nodes) {
                if (node.action() instanceof net.neoforged.neoform.runtime.actions.ExternalJavaToolAction tool
                        && tool.getClass() == net.neoforged.neoform.runtime.actions.ExternalJavaToolAction.class) {
                    node.setAction(new SFMHostExternalToolAction(node.id(), tool, root,
                            request -> channel.exchange("tool", request)));
                } else if (node.action() instanceof net.neoforged.neoform.runtime.actions.ExternalJavaToolAction tool
                        && (tool.getClass() == net.neoforged.neoform.runtime.actions.ApplySourceTransformAction.class
                        || tool.getClass() == net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction.class)) {
                    node.setAction(new SFMHostTransformToolAction(node.id(), tool, root,
                            request -> channel.exchange("tool", request)));
                } else if (node.action().getClass() == net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK.class) {
                    node.setAction(new SFMHostRecompileAction(
                            (net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK) node.action(), root.resolve("work").resolve(node.id())));
                }
            }
            SFMNamedNeoFormLauncher.declareFreshWorkspaces(engine.getCacheManager(), nodes);
            var ready = JsonParser.parseString(channel.exchange("graph", SFMHostGraph.describe(nodes))).getAsJsonObject();
            if (!ready.get("workspaces_ready").getAsBoolean() || ready.get("execution_enabled").getAsBoolean()) {
                throw new IllegalStateException("Preparation received an invalid execution acknowledgement");
            }
            if (!producerProof) {
                for (var node : nodes) engine.getCacheManager().createWorkspace(node.id());
            }
            SFMNamedNeoFormLauncher.bindFreshProducers(engine, new SFMHostProducerSealer(channel, root));
            if (producerProof) {
                String target = compileProof ? "compiledWithNeoForge" : sourceTransformProof ? "transformSources" : sourceRecipeProof ? "decompile" : childToolProof ? "extractServer" : "stripClient";
                var strip = nodes.stream().filter(node -> node.id().equals(target)).findFirst().orElseThrow();
                var targets = compileProof ? List.of(strip, nodes.stream()
                        .filter(node -> node.id().equals("sourcesWithNeoForge")).findFirst().orElseThrow()) : List.of(strip);
                var closure = java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<net.neoforged.neoform.runtime.graph.ExecutionNode, Boolean>());
                var remaining = new java.util.ArrayDeque<net.neoforged.neoform.runtime.graph.ExecutionNode>();
                remaining.addAll(targets);
                while (!remaining.isEmpty()) {
                    var node = remaining.removeFirst();
                    if (!closure.add(node)) continue;
                    Class<?> action = node.action().getClass();
                    boolean approvedTool = (sourceRecipeProof || childToolProof && node == strip)
                            && (node.action() instanceof SFMHostExternalToolAction
                            || sourceTransformProof && node.action() instanceof SFMHostTransformToolAction);
                    if (!approvedTool && !action.equals(net.neoforged.neoform.runtime.actions.SplitResourcesFromClassesAction.class)
                            && !(sourceRecipeProof && action.equals(net.neoforged.neoform.runtime.actions.CreateLibrariesOptionsFile.class))
                            && !(sourceTransformProof && action.equals(net.neoforged.neoform.runtime.actions.InjectZipContentAction.class))
                            && !(compileProof && action.equals(SFMHostRecompileAction.class))
                            && !action.equals(net.neoforged.neoform.runtime.actions.DownloadVersionManifestAction.class)
                            && !action.equals(net.neoforged.neoform.runtime.actions.DownloadFromVersionManifestAction.class)) {
                        throw new IllegalStateException("Built-in proof refuses " + node.id() + ": " + action.getName());
                    }
                    remaining.addAll(node.getPredecessors());
                }
                for (var result : targets) {
                engine.runNode(result);
                if (result.getState() != net.neoforged.neoform.runtime.graph.NodeState.COMPLETED) {
                    throw new IllegalStateException("Pinned result producer did not complete: " + result.id());
                }
                var original = result.getRequiredOutput("output").getResultPath();
                var paths = engine.getArtifactManager().resolveClasspath(List.of(
                        net.neoforged.neoform.runtime.artifacts.ClasspathItem.of(result.getRequiredOutput("output"))));
                if (paths.size() != 1 || paths.getFirst().equals(original)
                        || !MessageDigest.isEqual(bounded(original, 512 * 1024 * 1024),
                                bounded(paths.getFirst(), 512 * 1024 * 1024))) {
                    throw new IllegalStateException("Owned producer snapshot differs from actual strip output");
                }
                System.err.println("PASS actual producer sealed into " + paths.getFirst());
                }
            }
            String phase = compileProof ? "minecraft_compile_proof" : sourceTransformProof ? "source_transform_proof" : sourceRecipeProof ? "source_recipe_proof" : childToolProof ? "child_tool_proof" : producerProof ? "builtin_producer_proof" : "graph_prepared";
            String complete = channel.exchange("complete", "{\"phase\":\"" + phase + "\"}");
            if (!JsonParser.parseString(complete).getAsJsonObject().get("graph_prepared").getAsBoolean()) {
                throw new IllegalStateException("Host did not acknowledge graph preparation");
            }
            System.err.println("PASS actual host graph/store handshake: " + nodes.size()
                    + " workspaces; full native compilation disabled; producer proof=" + producerProof
                    + "; exact child-tool proof=" + childToolProof);
        }
    }

    /**
     * Reviewed older NFRT graphs retain an orphan launcher-discovery node.
     * Leave that inert. The 26.1.2 recipe instead retains it as a predecessor.
     * NFRT 2.0.19's DownloadVersionManifestAction does not read that input: it
     * asks our closed artifact manager for the already authenticated version.
     * Keep graph identities; only the reviewed predecessor becomes an alias.
     * Never enable getLauncherManifest or fabricate a launcher-wide manifest.
     */
    static void bindExactVersionDiscovery(
            List<net.neoforged.neoform.runtime.graph.ExecutionNode> nodes,
            String minecraftVersion) {
        var discovery = nodes.stream().filter(node -> node.action().getClass()
                == net.neoforged.neoform.runtime.actions.DownloadLauncherManifestAction.class).toList();
        if (discovery.isEmpty()) return;
        if (!java.util.Set.of("1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1", "26.1.2")
                .contains(minecraftVersion) || discovery.size() != 1) {
            throw new IllegalStateException("Unreviewed launcher-discovery graph");
        }
        var launcher = discovery.getFirst();
        var consumers = nodes.stream().filter(node -> node.getPredecessors().contains(launcher)).toList();
        if (!launcher.id().equals("downloadManifest") || !launcher.inputs().isEmpty()
                || launcher.outputs().size() != 1
                || !launcher.hasOutput("output")
                || launcher.getRequiredOutput("output").type()
                    != net.neoforged.neoform.runtime.graph.NodeOutputType.JSON
                || launcher.getState() != net.neoforged.neoform.runtime.graph.NodeState.NOT_STARTED) {
            throw new IllegalStateException("Changed exact-version discovery boundary");
        }
        if (!minecraftVersion.equals("26.1.2") && consumers.isEmpty()) return;
        if (!minecraftVersion.equals("26.1.2") || consumers.size() != 1) {
            throw new IllegalStateException("Unreviewed launcher-discovery consumers");
        }
        var version = consumers.getFirst();
        if (!version.id().equals("downloadJson")
                || version.action().getClass()
                    != net.neoforged.neoform.runtime.actions.DownloadVersionManifestAction.class
                || version.getState() != net.neoforged.neoform.runtime.graph.NodeState.NOT_STARTED
                || version.inputs().size() != 1
                || !version.inputs().containsKey("json")
                || version.getRequiredInput("json").getNodeDependencies().size() != 1) {
            throw new IllegalStateException("Discovery output has an unreviewed consumer");
        }
        launcher.setAction(version.action());
    }

    static String sourceUserdev(com.google.gson.JsonObject transfer) {
        String sourceOrigin = SFMRustContractConverter.sourceOrigin(transfer);
        String selected = null;
        for (var element : transfer.getAsJsonArray("inputs")) {
            var row = element.getAsJsonObject();
            var coordinate = row.get("coordinate");
            if (coordinate == null || coordinate.isJsonNull()
                    || !coordinate.getAsString().endsWith(":userdev")) continue;
            if (!row.get("origin").getAsString().equals(sourceOrigin)) {
                throw new IllegalArgumentException("Userdev input belongs to another source authority");
            }
            if (selected != null) throw new IllegalArgumentException("Ambiguous userdev input");
            selected = coordinate.getAsString();
        }
        if (selected == null) throw new IllegalArgumentException("Missing source userdev input");
        return selected;
    }

    private static byte[] bounded(Path path, int maximum) throws Exception {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(maximum + 1);
            if (bytes.length == 0 || bytes.length > maximum) throw new IllegalArgumentException("Input exceeds bound");
            return bytes;
        }
    }
    private static String sha256(byte[] bytes) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
