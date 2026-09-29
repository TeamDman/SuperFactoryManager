package ca.teamdman.sfm.releaseprobe;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** Test-only Forge 43.4.0 registry probe, never part of an SFM production JAR. */
@Mod("sfmreleaseprobe")
public final class ReleaseServerRegistryProbe {
    private static final String OUTPUT_PROPERTY = "sfm.releaseWitness.registrySnapshot";
    private static final String TARGET_PROPERTY = "sfm.releaseWitness.target";
    private static final String LOADER_PROPERTY = "sfm.releaseWitness.loader";
    private static final String COMMAND_OUTPUT_PROPERTY = "sfm.releaseWitness.commandSnapshot";
    private static final String NETWORK_OUTPUT_PROPERTY = "sfm.releaseWitness.networkSnapshot";
    private static final String[] CUSTOM_REGISTRIES = {
            "program_linters", "resource_type", "capability_provider_mappers"
    };

    public ReleaseServerRegistryProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
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
            captureCustomForgeRegistries(registries);
            require(registries, "minecraft:block", "sfm:manager");
            require(registries, "minecraft:item", "sfm:disk");
            for (String category : new String[]{
                    "minecraft:block_entity_type", "minecraft:menu",
                    "minecraft:recipe_serializer", "minecraft:recipe_type"
            }) {
                if (!registries.containsKey(category)) {
                    throw new IllegalStateException("Missing SFM registry category " + category);
                }
            }

            String json = toJson(registries, target, loader);
            Files.writeString(output, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            System.out.println("SFM_REGISTRY_SNAPSHOT_V1 " + json);
            String commandOutput = System.getProperty(COMMAND_OUTPUT_PROPERTY, "");
            if (!commandOutput.isBlank()) {
                writeCommandSnapshot(event.getServer().m_129892_().m_82094_(), commandOutput, target, loader);
            }
            String networkOutput = System.getProperty(NETWORK_OUTPUT_PROPERTY, "");
            if (!networkOutput.isBlank()) {
                writeNetworkSnapshot(networkOutput, target, loader);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("SFM release registry snapshot failed", failure);
        }
    }

    private static void writeNetworkSnapshot(String configuredPath, String target, String loader) throws Exception {
        Path output = Path.of(configuredPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(output.getParent()) || Files.exists(output)) {
            throw new IllegalStateException("Network snapshot parent is missing or output already exists");
        }
        // Keep registry and command modes independent of the optional network helper.
        String json = (String) Class.forName("ca.teamdman.sfm.releaseprobe.ReleaseNetworkProbe")
                .getMethod("captureJson", String.class, String.class)
                .invoke(null, target, loader);
        Files.writeString(output, json + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        System.out.println("SFM_NETWORK_SNAPSHOT_V1 messages_written=true");
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

    /** Forge 43.4.0 uses Registry.REGISTRY, not the later BuiltInRegistries root. */
    private static void captureBuiltInRegistries(TreeMap<String, TreeSet<String>> registries) throws Exception {
        Class<?> registryApi = Class.forName("net.minecraft.core.Registry");
        Object registryOfRegistries = registryApi.getField("f_122897_").get(null);
        Method keySet = registryApi.getMethod("m_6566_");
        Method get = registryApi.getMethod("m_7745_", ResourceLocation.class);
        for (Object registryId : (Set<?>) keySet.invoke(registryOfRegistries)) {
            Object registry = get.invoke(registryOfRegistries, registryId);
            if (registry == null) {
                throw new IllegalStateException("Missing built-in registry " + registryId);
            }
            TreeSet<String> sfmIds = new TreeSet<>();
            for (Object id : (Set<?>) keySet.invoke(registry)) {
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

    private static void captureCustomForgeRegistries(TreeMap<String, TreeSet<String>> registries) {
        for (String name : CUSTOM_REGISTRIES) {
            String registryId = "sfm:" + name;
            ForgeRegistry<?> registry = RegistryManager.ACTIVE.getRegistry(new ResourceLocation(registryId));
            if (registry == null) {
                throw new IllegalStateException("Missing custom Forge registry " + registryId);
            }
            TreeSet<String> sfmIds = new TreeSet<>();
            for (ResourceLocation id : registry.getKeys()) {
                String value = id.toString();
                if (value.startsWith("sfm:")) {
                    sfmIds.add(value);
                }
            }
            if (sfmIds.isEmpty()) {
                throw new IllegalStateException("Empty SFM custom registry " + registryId);
            }
            if (registries.put(registryId, sfmIds) != null) {
                throw new IllegalStateException("Duplicate registry category " + registryId);
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
