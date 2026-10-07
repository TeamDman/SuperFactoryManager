package ca.teamdman.sfm.toolchain.nfrt;

import net.neoforged.neoform.runtime.actions.DownloadLauncherManifestAction;
import net.neoforged.neoform.runtime.actions.DownloadVersionManifestAction;
import net.neoforged.neoform.runtime.graph.ExecutionNode;
import net.neoforged.neoform.runtime.graph.NodeOutput;
import net.neoforged.neoform.runtime.graph.NodeOutputType;
import java.util.List;
import java.util.Map;

/** Pure graph-binding proof; no downloads, files, tools or graph execution. */
public final class SFMExactVersionDiscoveryTests {
    public static void main(String[] args) throws Exception {
        var exact = fixture("downloadManifest", "downloadJson");
        var original = exact.getFirst().action();
        for (String foreignVersion : List.of("1.19.2", "1.21.0", "26.1.3")) {
            refuse(() -> SFMNamedHostPreparationMain.bindExactVersionDiscovery(exact, foreignVersion));
        }
        if (exact.getFirst().action() != original) throw new AssertionError("Rejected graph was mutated");
        SFMNamedHostPreparationMain.bindExactVersionDiscovery(exact, "26.1.2");
        if (exact.getFirst().action() != exact.getLast().action()
                || exact.getFirst().action().getClass() != DownloadVersionManifestAction.class
                || !exact.getLast().getPredecessors().contains(exact.getFirst())) {
            throw new AssertionError("Exact alias changed graph identity or dependencies");
        }
        // Rechecking an already-bound graph and graphs without discovery is inert.
        SFMNamedHostPreparationMain.bindExactVersionDiscovery(exact, "26.1.2");
        SFMNamedHostPreparationMain.bindExactVersionDiscovery(List.of(), "1.19.2");
        for (String reviewedVersion : List.of("1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1")) {
            var reviewed = fixture("downloadManifest", "downloadJson", false);
            var orphanAction = reviewed.getFirst().action();
            SFMNamedHostPreparationMain.bindExactVersionDiscovery(reviewed, reviewedVersion);
            if (reviewed.getFirst().action() != orphanAction
                    || !reviewed.getLast().getPredecessors().isEmpty()) {
                throw new AssertionError("Orphan discovery node was changed or connected");
            }
            refuse(() -> SFMNamedHostPreparationMain.bindExactVersionDiscovery(
                    fixture("downloadManifest", "downloadJson"), reviewedVersion));
        }
        refuse(() -> SFMNamedHostPreparationMain.bindExactVersionDiscovery(
                fixture("downloadManifest", "downloadJson", false), "26.1.2"));
        refuse(() -> SFMNamedHostPreparationMain.bindExactVersionDiscovery(
                fixture("other", "downloadJson"), "26.1.2"));
        refuse(() -> SFMNamedHostPreparationMain.bindExactVersionDiscovery(
                fixture("downloadManifest", "other"), "26.1.2"));
        var extra = fixture("downloadManifest", "downloadJson");
        var consumer = new ExecutionNode("unexpected", Map.of("json", extra.getFirst()
                .getRequiredOutput("output").asInput()), output(), environment -> { });
        refuse(() -> SFMNamedHostPreparationMain.bindExactVersionDiscovery(
                List.of(extra.getFirst(), extra.getLast(), consumer), "26.1.2"));
        var foreign = fixture("downloadManifest", "downloadJson");
        foreign.getLast().setAction(environment -> { });
        refuse(() -> SFMNamedHostPreparationMain.bindExactVersionDiscovery(foreign, "26.1.2"));
        if (args.length == 1) actualGraph(java.nio.file.Path.of(args[0]).toAbsolutePath().normalize());
        else if (args.length != 0) throw new IllegalArgumentException("Expected optional retained invocation root");
        System.out.println("PASS exact version alias; wrong version/id/action and extra consumer refused; no discovery executed");
    }

    private static void actualGraph(java.nio.file.Path root) throws Exception {
        var transfer = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                root.resolve("contract.json"))).getAsJsonObject();
        java.nio.file.Path versionPath = null;
        String userdev = null;
        for (var item : transfer.getAsJsonArray("inputs")) {
            var row = item.getAsJsonObject();
            if (row.get("request_key").getAsString().equals("version_json")) {
                versionPath = root.resolve(row.get("snapshot_relative_path").getAsString());
            }
            var coordinate = row.get("coordinate");
            if (coordinate != null && !coordinate.isJsonNull() && coordinate.getAsString().endsWith(":userdev")) {
                userdev = coordinate.getAsString();
            }
        }
        if (versionPath == null || userdev == null) throw new AssertionError("Missing retained parents");
        var contract = SFMRustContractConverter.convert(transfer, root,
                net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest.from(versionPath));
        try (var engine = SFMNamedEngineFactory.createUnstartedEngine(contract)) {
            var userdevFile = engine.addManagedResource(new java.util.jar.JarFile(
                    engine.getArtifactManager().get(userdev).path().toFile()));
            var config = net.neoforged.neoform.runtime.config.neoforge.NeoForgeConfig.from(userdevFile);
            engine.loadNeoFormData(engine.getArtifactManager().get(config.neoformArtifact()).path(), "joined");
            var configure = net.neoforged.neoform.runtime.cli.RunNeoFormCommand.class.getDeclaredMethod(
                    "applyNeoForgeProcessTransforms", net.neoforged.neoform.runtime.engine.NeoFormEngine.class,
                    java.util.jar.JarFile.class, net.neoforged.neoform.runtime.config.neoforge.NeoForgeConfig.class);
            configure.setAccessible(true);
            configure.invoke(null, engine, userdevFile, config);
            String contractDigest = "sha256:" + java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(
                            java.nio.file.Files.readAllBytes(root.resolve("contract.json"))));
            SFMHostProjectInputs.install(engine, root, contractDigest,
                    transfer.get("preparation_identity").getAsString());
            var nodes = List.copyOf(engine.getGraph().getNodes());
            System.out.println("Retained decompile configuration: " + SFMHostExternalToolAction.configuration(
                    (net.neoforged.neoform.runtime.actions.ExternalJavaToolAction)
                    engine.getGraph().getRequiredNode("decompile").action()));
            var launcher = engine.getGraph().getRequiredNode("downloadManifest");
            if (launcher.action().getClass() != DownloadLauncherManifestAction.class) {
                throw new AssertionError("Retained graph did not reproduce the original discovery node");
            }
            System.out.println("Retained discovery boundary: id=" + launcher.id()
                    + "; inputs=" + launcher.inputs().keySet() + "; outputs=" + launcher.outputs()
                    + "; state=" + launcher.getState() + "; consumers=" + nodes.stream()
                        .filter(node -> node.getPredecessors().contains(launcher)).map(ExecutionNode::id).toList());
            String minecraftVersion = transfer.get("minecraft_version").getAsString();
            SFMNamedHostPreparationMain.bindExactVersionDiscovery(nodes, minecraftVersion);
            if (minecraftVersion.equals("26.1.2")
                    && launcher.action() != engine.getGraph().getRequiredNode("downloadJson").action()) {
                throw new AssertionError("Actual graph did not bind to exact-version lookup");
            }
            if (!minecraftVersion.equals("26.1.2")
                    && (launcher.action().getClass() != DownloadLauncherManifestAction.class
                        || nodes.stream().anyMatch(node -> node.getPredecessors().contains(launcher)))) {
                throw new AssertionError("Actual orphan discovery node was changed or connected");
            }
            // Describe the production adapters without executing any graph node or child tool.
            for (var node : nodes) {
                if (node.action() instanceof net.neoforged.neoform.runtime.actions.ExternalJavaToolAction tool) {
                    if (tool.getClass() == net.neoforged.neoform.runtime.actions.ExternalJavaToolAction.class) {
                        node.setAction(new SFMHostExternalToolAction(node.id(), tool, root,
                                request -> { throw new AssertionError("Audit attempted tool execution"); }));
                    } else if (tool.getClass() == net.neoforged.neoform.runtime.actions.ApplySourceTransformAction.class
                            || tool.getClass() == net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction.class) {
                        node.setAction(new SFMHostTransformToolAction(node.id(), tool, root,
                                request -> { throw new AssertionError("Audit attempted transform execution"); }));
                    }
                } else if (node.action() instanceof net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK compiler) {
                    node.setAction(new SFMHostRecompileAction(compiler, root.resolve("work").resolve(node.id())));
                }
            }
            String graph = SFMHostGraph.describe(nodes);
            var described = com.google.gson.JsonParser.parseString(graph).getAsJsonObject();
            for (var item : described.getAsJsonArray("nodes")) {
                var node = item.getAsJsonObject();
                var action = com.google.gson.JsonParser.parseString(
                        node.get("action_contract_json").getAsString()).getAsJsonObject();
                if (minecraftVersion.equals("26.1.2") && node.get("id").getAsString().equals("decompile")
                        && (!action.get("library_options_attached").getAsBoolean()
                        || !action.getAsJsonArray("args").contains(
                                new com.google.gson.JsonPrimitive("-cfg={listLibrariesOutput}")))) {
                    throw new AssertionError("Actual decompiler library alias changed");
                }
                if (node.get("id").getAsString().equals("recompile")
                        && action.get("target_java_version").getAsInt()
                            != transfer.get("compiler_release").getAsInt()) {
                    throw new AssertionError("Actual compiler release changed");
                }
            }
            System.out.println("Retained transformed graph: " + graph);
            try {
                engine.getArtifactManager().getLauncherManifest();
                throw new AssertionError("Launcher discovery became available");
            } catch (java.io.IOException expected) {
                if (!expected.getMessage().equals("Launcher version discovery is not authorized")) throw expected;
            }
            System.out.println("PASS retained " + minecraftVersion
                    + " graph binding; launcher discovery remains refused; no graph executed");
        }
    }

    private static List<ExecutionNode> fixture(String launcherId, String versionId) {
        return fixture(launcherId, versionId, true);
    }

    private static List<ExecutionNode> fixture(String launcherId, String versionId, boolean connected) {
        var launcher = new ExecutionNode(launcherId, Map.of(), output(), new DownloadLauncherManifestAction(null));
        var version = new ExecutionNode(versionId,
                connected ? Map.of("json", launcher.getRequiredOutput("output").asInput()) : Map.of(), output(),
                new DownloadVersionManifestAction(null, null));
        return List.of(launcher, version);
    }

    private static Map<String, NodeOutput> output() {
        return Map.of("output", new NodeOutput("output", NodeOutputType.JSON, "fixture"));
    }

    private static void refuse(Runnable operation) {
        try { operation.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Unreviewed discovery graph accepted");
    }
}
