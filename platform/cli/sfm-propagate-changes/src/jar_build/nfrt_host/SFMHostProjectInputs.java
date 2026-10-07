package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.neoform.runtime.cli.RunNeoFormCommand;
import net.neoforged.neoform.runtime.engine.NeoFormEngine;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** Consume only Rust's held, source-bound project snapshots, not user paths. */
final class SFMHostProjectInputs {
    private static final String SOURCE = "src/main/resources/META-INF/accesstransformer.cfg";
    private static final String SNAPSHOT = "inputs/project/accesstransformer.cfg";

    static List<Path> read(Path root, String contractIdentity, String preparationIdentity) throws Exception {
        Path descriptor = root.resolve("project-inputs.json");
        var document = JsonParser.parseString(new String(bounded(descriptor, 16384), StandardCharsets.UTF_8)).getAsJsonObject();
        keys(document, Set.of("schema", "contract_identity", "project_preparation_identity", "inputs"));
        if (!document.get("schema").getAsString().equals("sfm:nfrt_project_inputs@1")
                || !document.get("contract_identity").getAsString().equals(contractIdentity)
                || !document.get("project_preparation_identity").getAsString().equals(preparationIdentity)) {
            throw new IOException("Project inputs belong to another source or invocation");
        }
        var inputs = document.getAsJsonArray("inputs");
        if (inputs.size() > 1) throw new IOException("Unapproved project input cardinality");
        if (inputs.isEmpty()) return List.of();
        var input = inputs.get(0).getAsJsonObject();
        keys(input, Set.of("source_output", "snapshot_relative_path", "bytes", "sha256"));
        if (!input.get("source_output").getAsString().equals(SOURCE)
                || !input.get("snapshot_relative_path").getAsString().equals(SNAPSHOT)) {
            throw new IOException("Unapproved project input source or snapshot");
        }
        Path path = root.resolve(SNAPSHOT).toAbsolutePath().normalize();
        byte[] bytes = bounded(path, 128 * 1024);
        String digest = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        if (bytes.length != input.get("bytes").getAsLong()
                || !digest.equals(input.get("sha256").getAsString())) {
            throw new IOException("Held project access-transformer snapshot changed");
        }
        return List.of(path);
    }

    static void install(NeoFormEngine engine, Path root, String contractIdentity, String preparationIdentity) throws Exception {
        List<Path> paths = read(root, contractIdentity, preparationIdentity);
        if (paths.isEmpty()) return;
        // Use the pinned runtime's own graph transformation so it remains in
        // the source/recompile path with its original validation semantics.
        var command = new RunNeoFormCommand();
        var field = RunNeoFormCommand.class.getDeclaredField("additionalAccessTransformers");
        field.setAccessible(true);
        field.set(command, paths.stream().map(Path::toString).toList());
        var hook = RunNeoFormCommand.class.getDeclaredMethod("applyAdditionalAccessTransformers", NeoFormEngine.class);
        hook.setAccessible(true);
        hook.invoke(command, engine);
        long bound = engine.getGraph().getNodes().stream().filter(node ->
                node.action() instanceof net.neoforged.neoform.runtime.actions.ApplySourceTransformAction transform
                        && transform.getAdditionalAccessTransformers().equals(paths)).count();
        if (bound != 1) throw new IOException("Project access transformer did not bind to exactly one source transform");
    }

    private static void keys(JsonObject object, Set<String> expected) throws IOException {
        if (!object.keySet().equals(expected)) throw new IOException("Missing or extra project input fields");
    }

    private static byte[] bounded(Path path, int limit) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Project input is not a regular snapshot");
        try (var stream = Files.newInputStream(path)) {
            byte[] bytes = stream.readNBytes(limit + 1);
            if (bytes.length > limit) throw new IOException("Project input exceeds its bound");
            return bytes;
        }
    }
}
