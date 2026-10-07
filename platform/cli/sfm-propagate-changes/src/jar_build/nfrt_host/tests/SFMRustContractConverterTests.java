package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only converter regression against an actual retained Rust transfer. No graph execution. */
public final class SFMRustContractConverterTests {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        var transfer = JsonParser.parseString(Files.readString(root.resolve("contract.json"))).getAsJsonObject();
        Path versionPath = null;
        int vineflower = -1;
        var inputs = transfer.getAsJsonArray("inputs");
        for (int index = 0; index < inputs.size(); index++) {
            var row = inputs.get(index).getAsJsonObject();
            if (row.get("request_key").getAsString().equals("version_json")) {
                versionPath = root.resolve(row.get("snapshot_relative_path").getAsString());
            }
            var coordinate = row.get("coordinate");
            if (coordinate != null && !coordinate.isJsonNull()
                    && coordinate.getAsString().startsWith("org.vineflower:vineflower:")) {
                if (vineflower != -1) throw new AssertionError("Ambiguous fixture decompiler");
                vineflower = index;
            }
        }
        if (versionPath == null || vineflower == -1) throw new AssertionError("Missing actual fixture inputs");
        var version = MinecraftVersionManifest.from(versionPath);
        SFMRustContractConverter.convert(transfer, root, version);
        String releaseUserdev = SFMNamedHostPreparationMain.sourceUserdev(transfer);
        var development = transfer.deepCopy();
        development.addProperty("environment", "dev");
        development.addProperty("recipe_id", "sfm:development-native-inputs/"
                + development.get("target_id").getAsString() + "@1");
        for (var element : development.getAsJsonArray("inputs")) {
            var row = element.getAsJsonObject();
            if (row.get("origin").getAsString().equals("original_schema2_pin")) {
                row.addProperty("origin", "captured_schema4_pin");
            }
        }
        SFMRustContractConverter.convert(development, root, version);
        if (!SFMNamedHostPreparationMain.sourceUserdev(development).equals(releaseUserdev)) {
            throw new AssertionError("Source authority changed selected userdev coordinate");
        }
        var duplicateUserdev = development.deepCopy();
        var wrongUserdevOrigin = development.deepCopy();
        for (var element : development.getAsJsonArray("inputs")) {
            var row = element.getAsJsonObject();
            var coordinate = row.get("coordinate");
            if (coordinate != null && !coordinate.isJsonNull() && coordinate.getAsString().endsWith(":userdev")) {
                duplicateUserdev.getAsJsonArray("inputs").add(row.deepCopy());
            }
        }
        for (var element : wrongUserdevOrigin.getAsJsonArray("inputs")) {
            var row = element.getAsJsonObject();
            var coordinate = row.get("coordinate");
            if (coordinate != null && !coordinate.isJsonNull() && coordinate.getAsString().endsWith(":userdev")) {
                row.addProperty("origin", "original_schema2_pin");
            }
        }
        rejectUserdev(duplicateUserdev);
        rejectUserdev(wrongUserdevOrigin);
        if (args.length == 2) {
            Path actualRoot = Path.of(args[1]).toAbsolutePath().normalize();
            var actual = JsonParser.parseString(Files.readString(actualRoot.resolve("contract.json"))).getAsJsonObject();
            if (!SFMRustContractConverter.sourceOrigin(actual).equals("captured_schema4_pin")
                    || !SFMNamedHostPreparationMain.sourceUserdev(actual).equals("net.neoforged:neoforge:20.2.86:userdev")) {
                throw new AssertionError("Actual 1.20.2 development parent lost source binding");
            }
            for (var element : actual.getAsJsonArray("inputs")) {
                var row = element.getAsJsonObject();
                if (row.get("request_key").getAsString().equals("version_json")) {
                    SFMRustContractConverter.convert(actual, actualRoot,
                            MinecraftVersionManifest.from(actualRoot.resolve(row.get("snapshot_relative_path").getAsString())));
                }
            }
        }
        var wrongEnvironment = development.deepCopy();
        wrongEnvironment.addProperty("environment", "release");
        rejectAuthority(wrongEnvironment, root, version);
        var wrongRecipe = development.deepCopy();
        wrongRecipe.addProperty("recipe_id", "sfm:development-native-inputs/unrecorded@1");
        rejectAuthority(wrongRecipe, root, version);
        var mixed = development.deepCopy();
        for (var element : mixed.getAsJsonArray("inputs")) {
            var row = element.getAsJsonObject();
            if (row.get("origin").getAsString().equals("captured_schema4_pin")) {
                row.addProperty("origin", "original_schema2_pin");
                break;
            }
        }
        rejectAuthority(mixed, root, version);
        String slot = "inputs/artifacts/%04d".formatted(vineflower);
        reject(transfer, root, version, vineflower, slot + ".bin", null);
        reject(transfer, root, version, vineflower, "inputs/artifacts/9999.jar", null);
        reject(transfer, root, version, vineflower, "../escape.jar", null);
        reject(transfer, root, version, vineflower, slot + ".jar", "org.vineflower:vineflower:1.10.1@zip");
        reject(transfer, root, version, vineflower, slot + ".jar", "org.vineflower:other:1.10.1");
        for (String[] row : new String[][] {
                {"net.neoforged:neoform:1.20.2-20230921.100330@zip", "zip"},
                {"net.neoforged:neoforge:20.2.93:userdev", "jar"},
                {"net.neoforged:neoforge:20.2.93:sources", "bin"},
                {"org.vineflower:vineflower:1.10.1", "jar"},
                {"org.vineflower:vineflower:1.10.1@jar", "jar"},
                {"org.vineflower:vineflower:1.10.1@zip", "bin"},
                {"org.vineflower:other:1.10.1", "bin"},
                {"other:archive:1@zip", "bin"},
                {null, "bin"}
        }) {
            if (!SFMRustContractConverter.originalSnapshotPath(7, row[0]).equals("inputs/artifacts/0007." + row[1])) {
                throw new AssertionError("Java snapshot role differs from Rust: " + row[0]);
            }
        }
        System.out.println("PASS release/development converter and userdev source authorities, duplicate/mixed userdev refusal, three invalid authority variants, five invalid snapshot variants and nine role rules; no graph executed");
    }

    private static void rejectUserdev(JsonObject transfer) {
        try {
            SFMNamedHostPreparationMain.sourceUserdev(transfer);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid userdev source authority accepted");
    }

    private static void rejectAuthority(JsonObject transfer, Path root, MinecraftVersionManifest version) {
        try {
            SFMRustContractConverter.convert(transfer, root, version);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid source authority accepted");
    }

    private static void reject(JsonObject transfer, Path root, MinecraftVersionManifest version,
            int index, String path, String coordinate) {
        var changed = transfer.deepCopy();
        var row = changed.getAsJsonArray("inputs").get(index).getAsJsonObject();
        row.addProperty("snapshot_relative_path", path);
        if (coordinate != null) row.addProperty("coordinate", coordinate);
        try {
            SFMRustContractConverter.convert(changed, root, version);
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().equals("Non-generated or reordered input snapshot path")) throw expected;
            return;
        }
        throw new AssertionError("Invalid snapshot accepted: " + path);
    }
}
