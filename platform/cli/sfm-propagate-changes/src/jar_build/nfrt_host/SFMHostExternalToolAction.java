package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.neoform.runtime.actions.ExternalJavaToolAction;
import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.graph.ExecutionNodeAction;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Pinned NFRT 2.0.19 command construction with no Java-owned child process.
 * The callback must validate and execute the request in the Rust-owned host.
 * This adapter is not yet installed into the executable native route.
 */
final class SFMHostExternalToolAction implements ExecutionNodeAction {
    @FunctionalInterface
    interface Launcher {
        String execute(String request) throws IOException, InterruptedException;
    }

    private final String nodeId;
    private final ExternalJavaToolAction original;
    private final Launcher launcher;
    private final Path invocationRoot;
    private final String originalConfiguration;

    SFMHostExternalToolAction(String nodeId, ExternalJavaToolAction original,
            Path invocationRoot, Launcher launcher) {
        if (!nodeId.matches("[A-Za-z0-9_-]{1,128}") || original == null || launcher == null
                || original.getClass() != ExternalJavaToolAction.class) {
            throw new IllegalArgumentException("Invalid owned child-tool binding");
        }
        this.nodeId = nodeId;
        this.original = original;
        this.invocationRoot = invocationRoot.toAbsolutePath().normalize();
        this.launcher = launcher;
        originalConfiguration = configuration(original);
    }

    ExternalJavaToolAction original() { return original; }

    static String configuration(ExternalJavaToolAction action) {
        var config = new JsonObject();
        config.addProperty("class", action.getClass().getName());
        config.add("classpath", strings(action.getClasspath().stream().map(Object::toString).toList()));
        config.addProperty("main_class", action.getMainClass());
        config.addProperty("repository", action.getRepositoryUrl() == null ? null : action.getRepositoryUrl().toString());
        config.add("args", strings(action.getArgs()));
        config.add("jvm_args", strings(action.getJvmArgs()));
        config.addProperty("library_options_attached", action.getListLibraries() != null);
        return config.toString();
    }

    @Override
    public void computeCacheKey(CacheKeyBuilder builder) {
        original.computeCacheKey(builder); // Retain original data dependencies as well as tool configuration.
    }

    @Override
    public void run(ProcessingEnvironment environment) throws IOException, InterruptedException {
        if (!originalConfiguration.equals(configuration(original))) {
            throw new IOException("Original child-tool configuration changed after graph registration");
        }
        Path expectedWorkspace = invocationRoot.resolve("work").resolve(nodeId);
        if (!expectedWorkspace.equals(environment.getWorkspace().toAbsolutePath().normalize())) {
            throw new IOException("Child tool belongs to another declared workspace");
        }
        Path libraryOptions = original.getListLibraries() == null ? null
                : original.getListLibraries().writeFile(environment);
        launchPrepared(environment, nodeId, original, invocationRoot, launcher,
                originalConfiguration, original.getArgs(), libraryOptions);
        if (!originalConfiguration.equals(configuration(original))) throw new IOException("Tool changed during command construction");
    }

    /** Shared transport only. Specialized callers own preparation/postconditions. */
    static void launchPrepared(ProcessingEnvironment environment, String nodeId, ExternalJavaToolAction original,
            Path invocationRoot, Launcher launcher, String originalConfiguration,
            List<String> templates, Path libraryOptions) throws IOException, InterruptedException {
        Path expectedWorkspace = invocationRoot.resolve("work").resolve(nodeId);
        if (!expectedWorkspace.equals(environment.getWorkspace().toAbsolutePath().normalize())) {
            throw new IOException("Prepared tool belongs to another declared workspace");
        }
        var toolPaths = new ArrayList<String>();
        for (var coordinate : original.getClasspath()) {
            var artifact = original.getRepositoryUrl() == null
                    ? environment.getArtifactManager().get(coordinate)
                    : environment.getArtifactManager().get(coordinate, original.getRepositoryUrl());
            toolPaths.add(relative(invocationRoot, artifact.path()));
        }
        if (toolPaths.isEmpty() || (original.getMainClass() == null && toolPaths.size() != 1)) {
            throw new IOException("Invalid pinned child-tool classpath/main class");
        }
        var jvm = new ArrayList<String>();
        // NFRT adds this switch on Java 23 through 25, including host-Java tools.
        // The Rust host selects the same SDK for the engine and these children.
        int major = Runtime.version().feature();
        if (major >= 23 && major < 26) jvm.add("--sun-misc-unsafe-memory-access=allow");
        for (String argument : original.getJvmArgs()) jvm.add(environment.interpolateString(argument));
        boolean vineflower = original.getClasspath().stream().anyMatch(coordinate ->
                coordinate.groupId().equals("org.vineflower") && coordinate.artifactId().equals("vineflower"));
        var arguments = new ArrayList<String>();
        for (String argument : templates) {
            if (vineflower) argument = argument.replace("TRACE", "WARN");
            if (libraryOptions != null) {
                argument = argument.replace("{listLibrariesOutput}", environment.getPathArgument(libraryOptions));
            }
            arguments.add(environment.interpolateString(argument));
        }
        var request = new JsonObject();
        request.addProperty("schema", "sfm:nfrt_child_tool_request@1");
        request.addProperty("node_id", nodeId);
        request.addProperty("configuration_json", originalConfiguration);
        request.addProperty("workspace_relative_path", relative(invocationRoot, expectedWorkspace));
        request.add("classpath_relative_paths", strings(toolPaths));
        request.addProperty("main_class", original.getMainClass());
        request.add("jvm_args", strings(jvm));
        request.add("args", strings(arguments));
        if (original.getClass() != ExternalJavaToolAction.class) request.add("argument_templates", strings(templates));
        if (original.getClass() == ExternalJavaToolAction.class && !originalConfiguration.equals(configuration(original))) {
            throw new IOException("Tool changed during command construction");
        }
        var response = JsonParser.parseString(launcher.execute(request.toString())).getAsJsonObject();
        if (!response.get("schema").getAsString().equals("sfm:nfrt_child_tool_result@1")
                || !response.get("node_id").getAsString().equals(nodeId)
                || response.get("exit_code").getAsInt() != 0) {
            throw new IOException("Rust child tool did not acknowledge successful execution for this node");
        }
    }

    private static String relative(Path invocationRoot, Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        if (!absolute.startsWith(invocationRoot)) throw new IOException("Tool path leaves owned invocation");
        String relative = invocationRoot.relativize(absolute).toString().replace('\\', '/');
        if (relative.isEmpty() || relative.contains("..")) throw new IOException("Unsafe tool path");
        return relative;
    }

    private static JsonArray strings(List<String> values) {
        var array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
