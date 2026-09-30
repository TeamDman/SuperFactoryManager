package ca.teamdman.sfm.releaseprobe;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Optional test-only helper. No production JAR or logged-in player is modified. */
public final class ReleaseCommandPermissionEffectProbe {
    private static final String[] CONTEXT_IDS = {"low", "operator", "owner"};
    private static final int[] LEVELS = {0, 2, 4};
    private static final String[] PERMISSION_FIELDS = {"ALL", "GAMEMASTER", "OWNER"};

    private ReleaseCommandPermissionEffectProbe() { }

    public static String captureJson(Object server, String target, String loader) throws Exception {
        boolean legacy = switch (target) {
            case "1.19.2" -> true;
            case "26.1.2" -> false;
            default -> throw new IllegalStateException("Unsupported command permission target");
        };
        if (!Boolean.TRUE.equals(invoke(server, legacy ? "m_18695_" : "isSameThread"))) {
            throw new IllegalStateException("Command witness must run synchronously on the server thread");
        }
        CommandSourceStack base = (CommandSourceStack) invoke(server,
                legacy ? "m_129893_" : "createCommandSourceStack");
        Object commands = invoke(server, legacy ? "m_129892_" : "getCommands");
        @SuppressWarnings("unchecked")
        CommandDispatcher<CommandSourceStack> dispatcher = (CommandDispatcher<CommandSourceStack>)
                invoke(commands, legacy ? "m_82094_" : "getDispatcher");
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("sfm");
        if (root == null) throw new IllegalStateException("Missing sfm command root");

        List<CommandSourceStack> contexts = new ArrayList<>();
        StringBuilder contextJson = new StringBuilder();
        for (int i = 0; i < CONTEXT_IDS.length; i++) {
            CommandSourceStack source = context(base, legacy, i);
            contexts.add(source);
            boolean hasEntity = invoke(source, legacy ? "m_81373_" : "getEntity") != null;
            boolean hasPlayer = invoke(source, legacy ? "m_230896_" : "getPlayer") != null;
            if (hasEntity || hasPlayer) throw new IllegalStateException("Expected a server console source without an entity or player");
            boolean all = hasPermission(source, legacy, 0);
            boolean operator = hasPermission(source, legacy, 1);
            boolean owner = hasPermission(source, legacy, 2);
            if (!all || operator != (i >= 1) || owner != (i == 2)) {
                throw new IllegalStateException("The actual command source does not have the selected permission shape");
            }
            if (i != 0) contextJson.append(',');
            contextJson.append("{\"id\":\"").append(CONTEXT_IDS[i])
                    .append("\",\"source_kind\":\"server_console_without_entity\",\"entity_present\":")
                    .append(hasEntity).append(",\"player_present\":").append(hasPlayer)
                    .append(",\"permission_level\":").append(LEVELS[i])
                    .append(",\"has_all\":").append(all)
                    .append(",\"has_operator\":").append(operator)
                    .append(",\"has_owner\":").append(owner).append('}');
        }
        requirePermissions(root, "bust_cable_network_cache", contexts, true, true, true);
        requirePermissions(root, "kit", contexts, false, true, true);
        requirePermissions(root.getChild("config").getChild("edit"), "SERVER", contexts, false, false, true);

        StringBuilder nodes = new StringBuilder();
        int nodeCount = appendNodes(nodes, root, "sfm", contexts, new boolean[]{true, true, true});
        if (nodeCount != 14) throw new IllegalStateException("Expected the released fourteen-node sfm command tree");
        String effect = captureEffect(dispatcher, contexts.get(0), legacy);
        return "{\"schema\":\"sfm:release_command_permission_effect_snapshot@1\",\"target\":\"" + target
                + "\",\"loader\":\"" + loader + "\",\"context_semantics\":\"server_console_permission_replacement_not_player_identity\","
                + "\"permission_model\":\"" + (legacy ? "numeric_command_level" : "level_based_permission_set")
                + "\",\"contexts\":[" + contextJson + "],\"nodes\":[" + nodes + "],\"effect\":" + effect + "}";
    }

    private static CommandSourceStack context(CommandSourceStack base, boolean legacy, int index) throws Exception {
        if (legacy) {
            return (CommandSourceStack) base.getClass().getMethod("m_81325_", int.class).invoke(base, LEVELS[index]);
        }
        Class<?> permissionSet = Class.forName("net.minecraft.server.permissions.PermissionSet");
        Object permissions = Class.forName("net.minecraft.server.permissions.LevelBasedPermissionSet")
                .getField(PERMISSION_FIELDS[index]).get(null);
        return (CommandSourceStack) base.getClass().getMethod("withPermission", permissionSet).invoke(base, permissions);
    }

    private static boolean hasPermission(CommandSourceStack source, boolean legacy, int index) throws Exception {
        if (legacy) {
            return (boolean) source.getClass().getMethod("m_6761_", int.class).invoke(source, LEVELS[index]);
        }
        Object permissions = invoke(source, "permissions");
        Class<?> permission = Class.forName("net.minecraft.server.permissions.Permission");
        Class<?> level = Class.forName("net.minecraft.server.permissions.PermissionLevel");
        String[] levelNames = {"ALL", "GAMEMASTERS", "OWNERS"};
        Object selected = level.getField(levelNames[index]).get(null);
        Object requirement = Class.forName("net.minecraft.server.permissions.Permission$HasCommandLevel")
                .getConstructor(level).newInstance(selected);
        return (boolean) Class.forName("net.minecraft.server.permissions.PermissionSet")
                .getMethod("hasPermission", permission).invoke(permissions, requirement);
    }

    private static void requirePermissions(CommandNode<CommandSourceStack> parent, String child,
                                          List<CommandSourceStack> contexts,
                                          boolean low, boolean operator, boolean owner) {
        if (parent == null || parent.getChild(child) == null) throw new IllegalStateException("Missing required permission node");
        CommandNode<CommandSourceStack> node = parent.getChild(child);
        boolean[] expected = {low, operator, owner};
        for (int i = 0; i < expected.length; i++) {
            if (node.canUse(contexts.get(i)) != expected[i]) {
                throw new IllegalStateException("Expected non-vacuous permission contrast at " + child);
            }
        }
    }

    private static int appendNodes(StringBuilder json, CommandNode<CommandSourceStack> node, String path,
                                   List<CommandSourceStack> contexts, boolean[] ancestors) {
        if (!path.matches("[A-Za-z0-9_/-]+")) throw new IllegalStateException("Unexpected command path");
        if (json.length() != 0) json.append(',');
        json.append("{\"path\":\"").append(path).append('"');
        boolean[] accessible = new boolean[3];
        for (int i = 0; i < contexts.size(); i++) {
            boolean own = node.canUse(contexts.get(i));
            accessible[i] = ancestors[i] && own;
            json.append(",\"").append(CONTEXT_IDS[i]).append("_can_use\":").append(own);
            json.append(",\"").append(CONTEXT_IDS[i]).append("_path_can_use\":").append(accessible[i]);
        }
        json.append('}');
        List<CommandNode<CommandSourceStack>> children = new ArrayList<>(node.getChildren());
        children.sort(Comparator.comparing(CommandNode::getName));
        int count = 1;
        for (CommandNode<CommandSourceStack> child : children) {
            count += appendNodes(json, child, path + "/" + child.getName(), contexts, accessible);
        }
        return count;
    }

    private static String captureEffect(CommandDispatcher<CommandSourceStack> dispatcher,
                                        CommandSourceStack source, boolean legacy) throws Exception {
        Object level = invoke(source, legacy ? "m_81372_" : "getLevel");
        Class<?> levelClass = Class.forName("net.minecraft.world.level.Level");
        Class<?> posClass = Class.forName("net.minecraft.core.BlockPos");
        Object pos = posClass.getConstructor(int.class, int.class, int.class).newInstance(0, 120, 0);
        Object holder = Class.forName("ca.teamdman.sfm.common.registry.registration.SFMBlocks")
                .getField("MANAGER").get(null);
        Object block = holder.getClass().getMethod("get").invoke(holder);
        Object state = invoke(block, legacy ? "m_49966_" : "defaultBlockState");
        Method setBlock = levelClass.getMethod(legacy ? "m_7731_" : "setBlock", posClass,
                Class.forName("net.minecraft.world.level.block.state.BlockState"), int.class);
        boolean placed = (boolean) setBlock.invoke(level, pos, state, 3);
        if (!placed) throw new IllegalStateException("Scratch manager placement did not change the world");
        Class<?> manager = Class.forName("ca.teamdman.sfm.common.block_network.CableNetworkManager");
        manager.getMethod("clear").invoke(null);
        Object seeded = manager.getMethod("getOrRegisterNetworkFromCablePosition", levelClass, posClass)
                .invoke(null, level, pos);
        if (!(seeded instanceof Optional<?> network) || network.isEmpty()) {
            throw new IllegalStateException("Production cable network API did not seed a scratch network");
        }
        long before = countNetworks(manager, levelClass, posClass, level, pos);
        if (before != 1) throw new IllegalStateException("Scratch cache must contain exactly one network before dispatch");
        int result = dispatcher.execute("sfm bust_cable_network_cache", source);
        long after = countNetworks(manager, levelClass, posClass, level, pos);
        if (result != 1 || after != 0) throw new IllegalStateException("Production cache-bust command did not clear the seeded cache");
        return "{\"command\":\"sfm bust_cable_network_cache\",\"context\":\"low\",\"server_thread\":true,"
                + "\"manager_placed\":" + placed + ",\"cache_seeded\":true,\"cache_before\":" + before
                + ",\"command_result\":" + result + ",\"cache_after\":" + after + "}";
    }

    private static long countNetworks(Class<?> manager, Class<?> levelClass, Class<?> posClass,
                                      Object level, Object pos) throws Exception {
        try (Stream<?> networks = (Stream<?>) manager.getMethod("getNetworksInRange", levelClass, posClass, double.class)
                .invoke(null, level, pos, 8.0)) {
            return networks.count();
        }
    }

    private static Object invoke(Object receiver, String method) throws Exception {
        return receiver.getClass().getMethod(method).invoke(receiver);
    }
}
