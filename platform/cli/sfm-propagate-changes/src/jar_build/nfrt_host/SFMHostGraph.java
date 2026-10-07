package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.neoforged.neoform.runtime.actions.ExternalJavaToolAction;
import net.neoforged.neoform.runtime.graph.ExecutionNode;
import net.neoforged.neoform.runtime.graph.NodeState;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;

/** Describe the actual fresh graph and tool configuration before host effects. */
final class SFMHostGraph {
    static String describe(List<ExecutionNode> nodes) throws IOException {
        if (nodes.isEmpty() || nodes.size() > 256) throw new IOException("Invalid host graph size");
        var graph = new JsonObject();
        graph.addProperty("schema", "sfm:nfrt_host_graph@1");
        var rows = new JsonArray();
        for (var node : nodes.stream().sorted(Comparator.comparing(ExecutionNode::id)).toList()) {
            if (node.getState() != NodeState.NOT_STARTED) throw new IOException("Host graph already started");
            var row = new JsonObject();
            row.addProperty("id", node.id());
            row.addProperty("action_class", node.action().getClass().getName());
            var predecessors = new JsonArray();
            node.getPredecessors().stream().map(ExecutionNode::id).sorted().forEach(predecessors::add);
            row.add("predecessors", predecessors);
            var inputs = new JsonArray();
            for (var input : node.inputs().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                var binding = new JsonObject();
                binding.addProperty("id", input.getKey());
                binding.addProperty("input_class", input.getValue().getClass().getName());
                var dependencies = new JsonArray();
                input.getValue().getNodeDependencies().stream().map(ExecutionNode::id).sorted().forEach(dependencies::add);
                binding.add("predecessors", dependencies);
                inputs.add(binding);
            }
            row.addProperty("input_contract_json", inputs.toString());
            if (node.action() instanceof ExternalJavaToolAction tool) {
                row.addProperty("action_contract_json", SFMHostExternalToolAction.configuration(tool));
            } else if (node.action() instanceof SFMHostExternalToolAction tool) {
                // The bridge's executable source is separately bound by the host.
                // Preserve the pinned original action contract, not the wrapper name.
                row.addProperty("action_class", tool.original().getClass().getName());
                row.addProperty("action_contract_json", SFMHostExternalToolAction.configuration(tool.original()));
            } else if (node.action() instanceof SFMHostTransformToolAction tool) {
                row.addProperty("action_class", tool.original().getClass().getName());
                row.addProperty("action_contract_json", tool.configuration());
            } else if (node.action() instanceof SFMHostRecompileAction compiler) {
                row.addProperty("action_class", compiler.original().getClass().getName());
                row.addProperty("action_contract_json", compiler.configuration());
            } else if (node.action().getClass() == net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK.class) {
                var compiler = (net.neoforged.neoform.runtime.actions.RecompileSourcesActionWithJDK) node.action();
                var action = new JsonObject();
                action.addProperty("class", compiler.getClass().getName());
                action.addProperty("target_java_version", compiler.getTargetJavaVersion());
                var options = new JsonArray();
                try {
                    var getter = compiler.getClass().getDeclaredMethod("getCompilerOptions");
                    getter.setAccessible(true);
                    @SuppressWarnings("unchecked") var values = (List<String>) getter.invoke(compiler);
                    values.forEach(options::add);
                } catch (ReflectiveOperationException failure) {
                    throw new IOException("Pinned in-process compiler options are unavailable", failure);
                }
                action.add("compiler_options", options);
                row.addProperty("action_contract_json", action.toString());
            } else {
                var action = new JsonObject();
                action.addProperty("class", node.action().getClass().getName());
                row.addProperty("action_contract_json", action.toString());
            }
            var outputs = new JsonArray();
            for (var entry : node.outputs().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                var output = new JsonObject();
                output.addProperty("id", entry.getKey());
                output.addProperty("output_type", entry.getValue().type().name());
                outputs.add(output);
            }
            row.add("outputs", outputs);
            rows.add(row);
        }
        graph.add("nodes", rows);
        return graph.toString();
    }
}
