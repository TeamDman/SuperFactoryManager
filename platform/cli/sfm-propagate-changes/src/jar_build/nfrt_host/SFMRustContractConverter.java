package ca.teamdman.sfm.toolchain.nfrt;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.neoforged.neoform.runtime.manifests.MinecraftDownload;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External pure converter draft. The host must authenticate and hold the Rust
 * transfer, snapshot root and exact version JSON before calling this method.
 * Parsing a DTO does not establish that authority or permit engine execution.
 */
final class SFMRustContractConverter {
    private SFMRustContractConverter() {}

    static SFMNamedNeoFormLauncher.LaunchContract convert(
            JsonObject transfer, Path ownedRoot, MinecraftVersionManifest authenticatedVersion) {
        if (!text(transfer, "schema").equals("sfm:named_nfrt_invocation_input@1")) {
            throw new IllegalArgumentException("Unexpected Rust transfer schema");
        }
        String sourceOrigin = sourceOrigin(transfer);
        requireFlag(transfer, "fresh_work_root_required", true);
        for (String field : List.of("native_execution_enabled", "os_filesystem_attested",
                "intermediate_cache_restore", "intermediate_cache_persistence", "launcher_probing")) {
            requireFlag(transfer, field, false);
        }
        if (!ownedRoot.isAbsolute() || !ownedRoot.normalize().equals(ownedRoot)) {
            throw new IllegalArgumentException("Root must be absolute and normalized");
        }
        var inputs = transfer.getAsJsonArray("inputs");
        if (inputs == null || inputs.isEmpty() || inputs.size() > 8192) {
            throw new IllegalArgumentException("Invalid transferred input cardinality");
        }
        var requestKeys = new HashSet<String>();
        var pins = new ArrayList<SFMNamedNeoFormLauncher.ArtifactPin>();
        var childrenByKey = new LinkedHashMap<String, SFMNamedNeoFormLauncher.ArtifactPin>();
        SFMNamedNeoFormLauncher.ArtifactPin version = null;
        long total = 0;
        for (int index = 0; index < inputs.size(); index++) {
            var row = inputs.get(index).getAsJsonObject();
            String key = text(row, "request_key");
            if (!requestKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate Rust request key");
            }
            String relative = text(row, "snapshot_relative_path");
            String coordinate = optionalText(row, "coordinate");
            if (!relative.equals(originalSnapshotPath(index, coordinate))) {
                throw new IllegalArgumentException("Non-generated or reordered input snapshot path");
            }
            long bytes = integer(row, "bytes");
            total = Math.addExact(total, bytes);
            if (total > 1024L * 1024L * 1024L) {
                throw new IllegalArgumentException("Transfer input batch exceeds byte limit");
            }
            String origin = text(row, "origin");
            if ((origin.equals("original_schema2_pin") || origin.equals("captured_schema4_pin"))
                    && !origin.equals(sourceOrigin)) {
                throw new IllegalArgumentException("Source pin differs from transfer source authority");
            }
            String authority = switch (origin) {
                case "original_schema2_pin", "captured_schema4_pin" -> digest(transfer, "original_request_catalog_identity");
                case "approved_nfrt_child" -> digest(transfer, "supplement_identity");
                case "authenticated_minecraft_child" -> ""; // Bound below to version JSON bytes.
                default -> throw new IllegalArgumentException("Unreviewed artifact origin");
            };
            // Child rows follow original rows in the actual Rust exporter.
            if (origin.equals("authenticated_minecraft_child")) {
                if (version == null) {
                    throw new IllegalArgumentException("Minecraft child precedes its authenticated parent");
                }
                authority = version.sha256();
            }
            var pin = new SFMNamedNeoFormLauncher.ArtifactPin(
                    optionalText(row, "coordinate"), optionalText(row, "exact_url"),
                    ownedRoot.resolve(relative), bytes, digest(row, "full_sha256"), origin, authority);
            pins.add(pin);
            if (key.equals("version_json")) {
                if (!origin.equals(sourceOrigin) || pin.coordinate() != null) {
                    throw new IllegalArgumentException("Version JSON lost original non-Maven identity");
                }
                version = pin;
            }
            if (origin.equals("authenticated_minecraft_child")) {
                if (pin.coordinate() != null || !key.startsWith("minecraft/")) {
                    throw new IllegalArgumentException("Minecraft child acquired a synthetic Maven identity");
                }
                childrenByKey.put(key, pin);
            }
        }
        if (version == null || !text(transfer, "minecraft_version").equals(authenticatedVersion.id())) {
            throw new IllegalArgumentException("Missing or mismatched authenticated version JSON");
        }
        var libraries = new ArrayList<SFMNamedNeoFormLauncher.ManifestChild>();
        var downloads = new ArrayList<SFMNamedNeoFormLauncher.ManifestChild>();
        var consumed = new HashSet<String>();
        for (var library : authenticatedVersion.libraries()) {
            MinecraftDownload download = library.getArtifactDownload();
            if (download == null) continue;
            String key = "minecraft/" + download.path();
            var pin = childrenByKey.get(key);
            if (pin != null) {
                libraries.add(new SFMNamedNeoFormLauncher.ManifestChild(
                        library.getMavenCoordinate().toString(), download, pin));
                consumed.add(key);
            }
        }
        for (var download : authenticatedVersion.downloads().entrySet()) {
            String key = "minecraft/" + download.getValue().uri();
            var pin = childrenByKey.get(key);
            if (pin != null) {
                downloads.add(new SFMNamedNeoFormLauncher.ManifestChild(
                        download.getKey(), download.getValue(), pin));
                consumed.add(key);
            }
        }
        if (!consumed.equals(childrenByKey.keySet())) {
            throw new IllegalArgumentException("Minecraft child detached from exact version manifest");
        }
        var runtime = new ArrayList<String>();
        for (String rows : List.of("ordered_runtime_roots", "ordered_transitive_runtime_rows")) {
            for (JsonElement row : transfer.getAsJsonArray(rows)) {
                runtime.add(text(row.getAsJsonObject(), "resolved_coordinate"));
            }
        }
        return new SFMNamedNeoFormLauncher.LaunchContract(
                text(transfer, "target_id"), text(transfer, "minecraft_version"),
                Math.toIntExact(integer(transfer, "compiler_release")),
                Math.toIntExact(integer(transfer, "minimum_tool_jvm")),
                digest(transfer, "preparation_identity"), digest(transfer, "source_lock_sha256"),
                digest(transfer, "supplement_sha256"), ownedRoot, version,
                pins, libraries, downloads, runtime);
    }

    static String sourceOrigin(JsonObject transfer) {
        String environment = text(transfer, "environment");
        String target = text(transfer, "target_id");
        String recipe = text(transfer, "recipe_id");
        if ((environment.equals("release") || environment.equals("dev"))
                && recipe.equals("sfm:released-native-inputs/4.34.0/" + target + "@1")) {
            return "original_schema2_pin";
        }
        if (environment.equals("dev")
                && recipe.equals("sfm:development-native-inputs/" + target + "@1")) {
            return "captured_schema4_pin";
        }
        throw new IllegalArgumentException("Unexpected transfer source authority");
    }

    // Match Rust's original_snapshot_path role rules; never accept arbitrary suffixes.
    static String originalSnapshotPath(int index, String coordinate) {
        String extension = "bin";
        if (coordinate != null && coordinate.startsWith("net.neoforged:neoform:") && coordinate.endsWith("@zip")) {
            extension = "zip";
        } else if (coordinate != null && (coordinate.endsWith(":userdev")
                || coordinate.startsWith("org.vineflower:vineflower:")
                && (!coordinate.contains("@") || coordinate.endsWith("@jar")))) {
            extension = "jar";
        }
        return "inputs/artifacts/%04d.%s".formatted(index, extension);
    }

    private static String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string field: " + field);
        }
        return value.getAsString();
    }

    private static String optionalText(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() ? null : text(object, field);
    }

    private static String digest(JsonObject object, String field) {
        String value = text(object, field);
        if (!value.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Expected exact prefixed SHA-256: " + field);
        }
        return value.substring("sha256:".length());
    }

    private static long integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()
                || !value.getAsString().matches("[0-9]+")) {
            throw new IllegalArgumentException("Expected unsigned integer field: " + field);
        }
        return Long.parseLong(value.getAsString());
    }

    private static void requireFlag(JsonObject object, String field, boolean expected) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()
                || value.getAsBoolean() != expected) {
            throw new IllegalArgumentException("Unexpected transfer flag: " + field);
        }
    }
}
