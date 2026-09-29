package ca.teamdman.sfm.releaseprobe;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** Test-only NeoForge 26.1.2.72 server probe, never part of an SFM production JAR. */
@Mod("sfmreleaseprobe")
public final class ReleaseServerRegistryProbe {
    private static final String OUTPUT_PROPERTY = "sfm.releaseWitness.registrySnapshot";
    private static final String TARGET_PROPERTY = "sfm.releaseWitness.target";
    private static final String LOADER_PROPERTY = "sfm.releaseWitness.loader";
    private static final String COMMAND_OUTPUT_PROPERTY = "sfm.releaseWitness.commandSnapshot";
    private static final String NETWORK_OUTPUT_PROPERTY = "sfm.releaseWitness.networkSnapshot";

    public ReleaseServerRegistryProbe() {
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(ServerStartedEvent event) {
        try {
            if (!ModList.get().isLoaded("sfm")) {
                throw new IllegalStateException("The production SFM mod is not loaded");
            }
            String configuredPath = System.getProperty(OUTPUT_PROPERTY, "");
            if (configuredPath.isBlank()) {
                throw new IllegalStateException("Missing " + OUTPUT_PROPERTY);
            }
            String target = requiredIdentity(TARGET_PROPERTY);
            String loader = requiredIdentity(LOADER_PROPERTY);
            Path output = Path.of(configuredPath).toAbsolutePath().normalize();
            if (!Files.isDirectory(output.getParent()) || Files.exists(output)) {
                throw new IllegalStateException("Registry snapshot parent is missing or output already exists");
            }

            TreeMap<String, TreeSet<String>> registries = new TreeMap<>();
            captureBuiltInRegistries(registries);
            require(registries, "minecraft:block", "sfm:manager");
            require(registries, "minecraft:item", "sfm:disk");
            require(registries, "minecraft:creative_mode_tab", "sfm:main");
            for (String component : new String[]{"sfm:program", "sfm:labels", "sfm:errors", "sfm:warnings"}) {
                require(registries, "minecraft:data_component_type", component);
            }
            for (String category : new String[]{
                    "minecraft:block_entity_type", "minecraft:menu",
                    "minecraft:recipe_serializer", "minecraft:recipe_type",
                    "sfm:program_linters", "sfm:resource_type", "sfm:capability_provider_mappers"
            }) {
                if (!registries.containsKey(category) || registries.get(category).isEmpty()) {
                    throw new IllegalStateException("Missing or empty SFM registry category " + category);
                }
            }

            String json = toJson(registries, target, loader);
            Files.writeString(output, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            System.out.println("SFM_REGISTRY_SNAPSHOT_V1 " + json);
            String commandOutput = System.getProperty(COMMAND_OUTPUT_PROPERTY, "");
            if (!commandOutput.isBlank()) {
                writeCommandSnapshot(event.getServer().getCommands().getDispatcher(), commandOutput, target, loader);
            }
            String networkOutput = System.getProperty(NETWORK_OUTPUT_PROPERTY, "");
            if (!networkOutput.isBlank()) {
                writeNetworkSnapshot(networkOutput);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("SFM release registry snapshot failed", failure);
        }
    }

    private static void writeNetworkSnapshot(String configuredPath) throws Exception {
        Path output = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(output.getParent()) || Files.exists(output)) {
            throw new IllegalStateException("Network snapshot parent is missing or output already exists");
        }
        String json = ReleaseNetworkProbe.captureJson();
        Files.writeString(output, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("SFM_NETWORK_SNAPSHOT_V1 payloads_written=true");
    }

    private static void writeCommandSnapshot(CommandDispatcher<CommandSourceStack> dispatcher,
                                             String configuredPath, String target, String loader) throws Exception {
        Path output = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(output.getParent()) || Files.exists(output)) {
            throw new IllegalStateException("Command snapshot parent is missing or output already exists");
        }
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("sfm");
        if (!(root instanceof LiteralCommandNode<?>)) {
            throw new IllegalStateException("Missing literal sfm command root");
        }
        StringBuilder json = new StringBuilder("{\"schema\":\"sfm:release_command_snapshot@1\",\"target\":");
        appendId(json, target);
        json.append(",\"loader\":");
        appendId(json, loader);
        json.append(",\"nodes\":[");
        appendCommandNode(json, root, "sfm", true);
        Files.writeString(output, json.append("]}\n").toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
        System.out.println("SFM_COMMAND_SNAPSHOT_V1 nodes_written=true");
    }

    private static void appendCommandNode(StringBuilder json, CommandNode<CommandSourceStack> node,
                                          String path, boolean first) {
        if (!first) json.append(',');
        json.append("{\"path\":");
        appendCommandValue(json, path);
        json.append(",\"kind\":");
        appendCommandValue(json, node instanceof LiteralCommandNode<?> ? "literal" : "argument");
        json.append(",\"executable\":").append(node.getCommand() != null);
        json.append(",\"redirected\":").append(node.getRedirect() != null);
        if (node instanceof ArgumentCommandNode<?, ?> argument) {
            json.append(",\"argument_type\":");
            appendCommandValue(json, argument.getType().getClass().getName());
        }
        json.append('}');
        List<CommandNode<CommandSourceStack>> children = new ArrayList<>(node.getChildren());
        children.sort(Comparator.comparing(CommandNode::getName));
        for (CommandNode<CommandSourceStack> child : children) {
            if (!(child instanceof LiteralCommandNode<?>) && !(child instanceof ArgumentCommandNode<?, ?>)) {
                throw new IllegalStateException("Unknown command node kind");
            }
            appendCommandNode(json, child, path + "/" + child.getName(), false);
        }
    }

    private static void appendCommandValue(StringBuilder json, String value) {
        if (!value.matches("[A-Za-z0-9_.$:/-]+")) {
            throw new IllegalStateException("Unexpected command snapshot value syntax");
        }
        json.append('"').append(value).append('"');
    }

    private static void captureBuiltInRegistries(TreeMap<String, TreeSet<String>> registries) {
        for (Identifier registryId : BuiltInRegistries.REGISTRY.keySet()) {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(registryId);
            if (registry == null) {
                throw new IllegalStateException("Missing built-in registry " + registryId);
            }
            TreeSet<String> sfmIds = new TreeSet<>();
            for (Identifier id : registry.keySet()) {
                String value = id.toString();
                if (value.startsWith("sfm:")) {
                    sfmIds.add(value);
                }
            }
            if (!sfmIds.isEmpty()) {
                registries.put(registryId.toString(), sfmIds);
            }
        }
    }

    private static void require(TreeMap<String, TreeSet<String>> registries, String registryId, String entryId) {
        if (!registries.containsKey(registryId) || !registries.get(registryId).contains(entryId)) {
            throw new IllegalStateException("Missing required runtime ID " + registryId + "/" + entryId);
        }
    }

    private static String requiredIdentity(String property) {
        String value = System.getProperty(property, "");
        if (value.isBlank() || !value.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalStateException("Missing or invalid " + property);
        }
        return value;
    }

    private static String toJson(TreeMap<String, TreeSet<String>> registries, String target, String loader) {
        StringBuilder json = new StringBuilder("{\"schema\":\"sfm:release_registry_snapshot@1\",\"target\":");
        appendId(json, target);
        json.append(",\"loader\":");
        appendId(json, loader);
        json.append(",\"registries\":{");
        boolean firstRegistry = true;
        for (var entry : registries.entrySet()) {
            if (!firstRegistry) json.append(',');
            firstRegistry = false;
            appendId(json, entry.getKey());
            json.append(": [");
            boolean firstId = true;
            for (String id : entry.getValue()) {
                if (!firstId) json.append(',');
                firstId = false;
                appendId(json, id);
            }
            json.append(']');
        }
        return json.append("}}").toString();
    }

    private static void appendId(StringBuilder json, String id) {
        if (!id.matches("[a-z0-9_.:/-]+")) {
            throw new IllegalStateException("Unexpected registry ID syntax");
        }
        json.append('"').append(id).append('"');
    }
}
