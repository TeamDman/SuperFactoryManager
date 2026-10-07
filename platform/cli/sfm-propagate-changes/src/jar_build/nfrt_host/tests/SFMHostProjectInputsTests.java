package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Project-input identity checks only; does not run an engine or child tool. */
public final class SFMHostProjectInputsTests {
    private static final String CONTRACT = "sha256:" + "a".repeat(64);
    private static final String SOURCE = "sha256:" + "b".repeat(64);
    @FunctionalInterface interface Checked { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sfm-project-input-test-");
        Path snapshot = root.resolve("inputs/project/accesstransformer.cfg");
        Files.createDirectories(snapshot.getParent());
        byte[] bytes = "public fixture.Example field\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(snapshot, bytes);
        var document = new JsonObject();
        document.addProperty("schema", "sfm:nfrt_project_inputs@1");
        document.addProperty("contract_identity", CONTRACT);
        document.addProperty("project_preparation_identity", SOURCE);
        var inputs = new JsonArray();
        var input = new JsonObject();
        input.addProperty("source_output", "src/main/resources/META-INF/accesstransformer.cfg");
        input.addProperty("snapshot_relative_path", "inputs/project/accesstransformer.cfg");
        input.addProperty("bytes", bytes.length);
        input.addProperty("sha256", "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        inputs.add(input);
        document.add("inputs", inputs);
        write(root, document);
        if (!SFMHostProjectInputs.read(root, CONTRACT, SOURCE).equals(List.of(snapshot))) throw new AssertionError("valid project snapshot not selected");
        refused(() -> SFMHostProjectInputs.read(root, SOURCE, SOURCE));
        refused(() -> SFMHostProjectInputs.read(root, CONTRACT, CONTRACT));
        Files.writeString(snapshot, "changed");
        refused(() -> SFMHostProjectInputs.read(root, CONTRACT, SOURCE));
        Files.write(snapshot, bytes);
        input.addProperty("snapshot_relative_path", "../escape.cfg");
        write(root, document);
        refused(() -> SFMHostProjectInputs.read(root, CONTRACT, SOURCE));
        input.addProperty("snapshot_relative_path", "inputs/project/accesstransformer.cfg");
        inputs.add(input.deepCopy());
        write(root, document);
        refused(() -> SFMHostProjectInputs.read(root, CONTRACT, SOURCE));
        inputs.remove(1);
        document.addProperty("unapproved", true);
        write(root, document);
        refused(() -> SFMHostProjectInputs.read(root, CONTRACT, SOURCE));
        document.remove("unapproved");
        document.add("inputs", new JsonArray());
        write(root, document);
        if (!SFMHostProjectInputs.read(root, CONTRACT, SOURCE).isEmpty()) throw new AssertionError("explicit absence not preserved");
        System.out.println("PASS project input acceptance, explicit absence and six identity/path/cardinality refusals");
    }

    private static void write(Path root, JsonObject document) throws Exception {
        Files.writeString(root.resolve("project-inputs.json"), document.toString());
    }

    private static void refused(Checked operation) throws Exception {
        try { operation.run(); } catch (java.io.IOException expected) { return; }
        throw new AssertionError("invalid project snapshot accepted");
    }
}
