package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction;
import net.neoforged.neoform.runtime.actions.ApplySourceTransformAction;
import net.neoforged.neoform.runtime.actions.ExternalJavaToolAction;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.graph.ExecutionNodeAction;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Exact pinned source/dev transform adapter. Never calls original run(),
 * which would create a Java-owned child. The Rust callback owns execution.
 */
final class SFMHostTransformToolAction implements ExecutionNodeAction {
    private final String nodeId;
    private final ExternalJavaToolAction original;
    private final Path root;
    private final SFMHostExternalToolAction.Launcher launcher;
    private final String boundConfiguration;

    SFMHostTransformToolAction(String nodeId, ExternalJavaToolAction original, Path root,
            SFMHostExternalToolAction.Launcher launcher) throws IOException {
        if (!nodeId.matches("[A-Za-z0-9_-]{1,128}") || original == null || launcher == null
                || (original.getClass() != ApplySourceTransformAction.class
                && original.getClass() != ApplyDevTransformsAction.class)) throw new IOException("Unreviewed owned transform binding");
        this.nodeId = nodeId;
        this.original = original;
        this.root = root.toAbsolutePath().normalize();
        this.launcher = launcher;
        boundConfiguration = configuration(original, this.root);
    }

    ExternalJavaToolAction original() { return original; }
    String configuration() throws IOException { return configuration(original, root); }

    static String configuration(ExternalJavaToolAction original, Path root) throws IOException {
        var configuration = JsonParser.parseString(SFMHostExternalToolAction.configuration(original)).getAsJsonObject();
        var preparation = new JsonObject();
        if (original.getClass() == ApplySourceTransformAction.class) {
            var source = (ApplySourceTransformAction) original;
            preparation.addProperty("schema", "sfm:nfrt_source_transform@1");
            preparation.add("data_ids", strings(source.getAccessTransformersData()));
            preparation.add("additional_at", paths(source.getAdditionalAccessTransformers(), root));
            preparation.add("validated_at", paths(source.getValidatedAccessTransformers(), root));
            preparation.add("interfaces", paths(SFMHostTransformPreparation.sourceInterfaces(source), root));
            preparation.addProperty("parchment", source.getParchmentData() == null ? null : relative(source.getParchmentData(), root));
            var parser = new JsonArray();
            for (var item : source.getParserClasspath().getEffectiveClasspath()) {
                if (item instanceof ClasspathItem.MavenCoordinateItem maven) {
                    parser.add("maven:" + maven.coordinate() + "@" + maven.repositoryBaseUrl());
                } else if (item instanceof ClasspathItem.MinecraftLibraryItem library) {
                    parser.add("minecraft:" + library.library().getMavenCoordinate());
                } else if (item instanceof ClasspathItem.PathItem path) {
                    parser.add("path:" + relative(path.path(), root));
                } else if (item instanceof ClasspathItem.NodeOutputItem output) {
                    parser.add("node:" + output.output().getNode().id() + "/" + output.output().id());
                } else throw new IOException("Unreviewed transform parser classpath item");
            }
            preparation.add("parser_bindings", parser);
            preparation.add("additional_arguments", strings(source.getAdditionalArguments()));
        } else if (original.getClass() == ApplyDevTransformsAction.class) {
            var dev = (ApplyDevTransformsAction) original;
            preparation.addProperty("schema", "sfm:nfrt_dev_transform@1");
            preparation.add("data_ids", strings(dev.getAccessTransformersData()));
            preparation.add("additional_at", paths(dev.getAdditionalAccessTransformers(), root));
            preparation.add("validated_at", new JsonArray());
            preparation.add("interfaces", paths(dev.getInjectedInterfaces(), root));
            preparation.addProperty("parchment", (String) null);
            preparation.add("parser_bindings", new JsonArray());
            preparation.add("additional_arguments", new JsonArray());
        } else throw new IOException("Unreviewed transform class");
        configuration.addProperty("transform_preparation_json", preparation.toString());
        return configuration.toString();
    }

    @Override public void computeCacheKey(CacheKeyBuilder builder) { original.computeCacheKey(builder); }

    @Override public void run(ProcessingEnvironment environment) throws IOException, InterruptedException {
        unchanged();
        List<String> templates = original instanceof ApplySourceTransformAction source
                ? SFMHostTransformPreparation.sourceArguments(source, environment)
                : SFMHostTransformPreparation.devArguments((ApplyDevTransformsAction) original, environment);
        unchanged();
        try {
            SFMHostExternalToolAction.launchPrepared(environment, nodeId, original, root,
                    request -> { unchanged(); return launcher.execute(request); }, boundConfiguration, templates, null);
        } catch (IOException | InterruptedException | RuntimeException failure) {
            if (original instanceof ApplySourceTransformAction) SFMHostTransformPreparation.sourceFailed(environment, failure);
            throw failure;
        }
        unchanged();
        if (original instanceof ApplySourceTransformAction source) SFMHostTransformPreparation.sourceFinished(source, environment);
    }

    private void unchanged() throws IOException {
        if (!boundConfiguration.equals(configuration())) throw new IOException("Transform configuration changed after graph registration");
    }

    private static JsonArray strings(List<String> values) {
        var array = new JsonArray(); values.forEach(array::add); return array;
    }
    private static JsonArray paths(List<Path> values, Path root) throws IOException {
        var array = new JsonArray(); for (Path value : values) array.add(relative(value, root)); return array;
    }
    private static String relative(Path value, Path root) throws IOException {
        Path absolute = value.toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) throw new IOException("Transform input leaves owned invocation");
        return root.relativize(absolute).toString().replace('\\', '/');
    }
}
