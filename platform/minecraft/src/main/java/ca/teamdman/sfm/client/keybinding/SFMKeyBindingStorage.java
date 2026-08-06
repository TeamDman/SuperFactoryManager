package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMKeyboardUsageSituations;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SFMKeyBindingStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA_1 = 1;
    private static final int SCHEMA_2 = 2;

    private SFMKeyBindingStorage() {
    }

    static LoadResult load() {
        return load(path());
    }

    static LoadResult load(Path path) {
        if (!Files.isRegularFile(path)) return LoadResult.empty();
        try {
            String original = Files.readString(path, StandardCharsets.UTF_8);
            LoadResult result = parse(original);
            result.diagnostics().forEach(diagnostic ->
                    SFM.LOGGER.warn("Dynamic keybinding load diagnostic at {}: {}", path, diagnostic));
            if (result.status() == LoadStatus.MIGRATED_SCHEMA_1) {
                try {
                    Path backup = nextSchema1Backup(path);
                    Files.copy(path, backup);
                    write(path, result.state());
                } catch (IOException migrationFailure) {
                    String diagnostic = "Schema-1 migration is pending; original file was not replaced: "
                            + message(migrationFailure);
                    SFM.LOGGER.warn("{} at {}", diagnostic, path, migrationFailure);
                    return new LoadResult(
                            result.state(),
                            LoadStatus.MIGRATION_PENDING,
                            SCHEMA_1,
                            false,
                            false,
                            List.of(diagnostic));
                }
            }
            return result;
        } catch (IOException exception) {
            SFM.LOGGER.warn("Unable to load dynamic SFM keybindings from {}", path, exception);
            return new LoadResult(
                    SFMKeyBindingUserState.EMPTY,
                    LoadStatus.CORRUPT,
                    0,
                    false,
                    false,
                    List.of(exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage()));
        }
    }

    static LoadResult parse(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            int schema = root.get("schema").getAsInt();
            if (schema != SCHEMA_1 && schema != SCHEMA_2) {
                return new LoadResult(
                        SFMKeyBindingUserState.EMPTY,
                        LoadStatus.UNSUPPORTED_SCHEMA,
                        schema,
                        false,
                        false,
                        List.of("Unsupported dynamic keybinding schema " + schema));
            }
            if (!root.has("bindings") || !root.get("bindings").isJsonArray()) {
                throw new IllegalArgumentException("Missing bindings array");
            }
            if (schema == SCHEMA_2
                    && (!root.has("tombstones") || !root.get("tombstones").isJsonArray())) {
                throw new IllegalArgumentException("Missing tombstones array");
            }
            List<SFMKeyBinding> bindings = new ArrayList<>();
            Set<String> bindingIds = new LinkedHashSet<>();
            JsonArray storedBindings = root.getAsJsonArray("bindings");
            for (var bindingElement : storedBindings) {
                SFMKeyBinding binding = readBinding(
                        bindingElement.getAsJsonObject(),
                        schema == SCHEMA_1 ? SFMKeyboardUsageSituations.GLOBAL : null);
                if (!bindingIds.add(binding.bindingId())) {
                    throw new IllegalArgumentException("Duplicate binding id " + binding.bindingId());
                }
                bindings.add(binding);
            }
            Map<String, SFMKeyBinding> builtInsById = new LinkedHashMap<>();
            for (SFMKeyBinding builtIn : SFMKeyBindingDefaults.definitions()) {
                builtInsById.put(builtIn.bindingId(), builtIn);
            }
            Map<String, SFMKeyBindingOverride> defaultOverrides = new LinkedHashMap<>();
            if (schema == SCHEMA_2 && root.has("defaultOverrides")) {
                if (!root.get("defaultOverrides").isJsonArray()) {
                    throw new IllegalArgumentException("defaultOverrides must be an array");
                }
                for (var overrideElement : root.getAsJsonArray("defaultOverrides")) {
                    SFMKeyBindingOverride override = readOverride(overrideElement.getAsJsonObject());
                    if (defaultOverrides.put(override.bindingId(), override) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate default override id " + override.bindingId());
                    }
                }
            }
            if (schema == SCHEMA_2) {
                // Early schema-2 prototypes stored built-in edits as full
                // user records. Convert only records whose immutable fields
                // still identify the current built-in definition.
                bindings.removeIf(binding -> {
                    SFMKeyBinding definition = builtInsById.get(binding.bindingId());
                    if (definition == null
                            || !definition.actionId().equals(binding.actionId())
                            || !definition.commandDraft().equals(binding.commandDraft())) return false;
                    defaultOverrides.putIfAbsent(
                            binding.bindingId(),
                            SFMKeyBindingOverride.from(binding));
                    return true;
                });
            }
            Set<String> tombstones = new LinkedHashSet<>();
            if (schema == SCHEMA_2) {
                for (var tombstone : root.getAsJsonArray("tombstones")) {
                    tombstones.add(tombstone.getAsString());
                }
            }
            Map<String, String> observedDefaults = new LinkedHashMap<>();
            if (schema == SCHEMA_2 && root.has("defaultFingerprints")) {
                if (!root.get("defaultFingerprints").isJsonObject()) {
                    throw new IllegalArgumentException("defaultFingerprints must be an object");
                }
                for (var entry : root.getAsJsonObject("defaultFingerprints").entrySet()) {
                    observedDefaults.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            Map<String, String> currentDefaults = SFMKeyBindingDefaults.fingerprints();
            List<SFMKeyBinding> userRelationships = new ArrayList<>(bindings);
            for (SFMKeyBindingOverride override : defaultOverrides.values()) {
                SFMKeyBinding definition = builtInsById.get(override.bindingId());
                if (definition != null) userRelationships.add(override.applyTo(definition));
            }
            for (SFMKeyBinding builtIn : SFMKeyBindingDefaults.definitions()) {
                if (currentDefaults.get(builtIn.bindingId()).equals(
                        observedDefaults.get(builtIn.bindingId()))) continue;
                // A migrated global relationship or an older schema-2 user
                // relationship remains authoritative inside workspaces. New
                // or changed contextual defaults with the same gesture are
                // tombstoned instead of silently shadowing it.
                if (userRelationships.stream().anyMatch(existing ->
                        !existing.bindingId().equals(builtIn.bindingId())
                                && existing.sequence().equals(builtIn.sequence()))) {
                    tombstones.add(builtIn.bindingId());
                }
            }
            return new LoadResult(
                    new SFMKeyBindingUserState(
                            bindings,
                            defaultOverrides,
                            tombstones,
                            currentDefaults),
                    schema == SCHEMA_1 ? LoadStatus.MIGRATED_SCHEMA_1 : LoadStatus.LOADED_SCHEMA_2,
                    schema,
                    schema == SCHEMA_1,
                    true,
                    List.of());
        } catch (RuntimeException exception) {
            return new LoadResult(
                    SFMKeyBindingUserState.EMPTY,
                    LoadStatus.CORRUPT,
                    0,
                    false,
                    false,
                    List.of("Corrupt dynamic keybinding file: "
                            + (exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage())));
        }
    }

    static SaveResult save(SFMKeyBindingUserState state) {
        return save(path(), state);
    }

    static SaveResult save(Path path, SFMKeyBindingUserState state) {
        try {
            write(path, state);
            return SaveResult.success();
        } catch (IOException exception) {
            SFM.LOGGER.warn("Unable to save dynamic SFM keybindings to {}", path, exception);
            return SaveResult.failure("Unable to save dynamic keybindings: " + message(exception));
        }
    }

    static String serialize(SFMKeyBindingUserState state) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA_2);
        JsonArray bindings = new JsonArray();
        state.bindings().stream()
                .sorted(java.util.Comparator.comparing(SFMKeyBinding::bindingId))
                .forEach(binding -> bindings.add(toJson(binding)));
        root.add("bindings", bindings);
        JsonArray defaultOverrides = new JsonArray();
        state.defaultOverrides().values().stream()
                .sorted(java.util.Comparator.comparing(SFMKeyBindingOverride::bindingId))
                .forEach(override -> defaultOverrides.add(toJson(override)));
        root.add("defaultOverrides", defaultOverrides);
        JsonArray tombstones = new JsonArray();
        state.tombstones().stream()
                .sorted()
                .forEach(tombstones::add);
        root.add("tombstones", tombstones);
        JsonObject defaultFingerprints = new JsonObject();
        state.defaultFingerprints().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> defaultFingerprints.addProperty(entry.getKey(), entry.getValue()));
        root.add("defaultFingerprints", defaultFingerprints);
        return GSON.toJson(root);
    }

    private static SFMKeyBinding readBinding(
            JsonObject binding,
            ResourceLocation defaultSituation
    ) {
        List<SFMKeyStroke> strokes = new ArrayList<>();
        for (var strokeElement : binding.getAsJsonArray("strokes")) {
            JsonObject stroke = strokeElement.getAsJsonObject();
            EnumSet<SFMKeyModifier> modifiers = EnumSet.noneOf(SFMKeyModifier.class);
            for (var modifier : stroke.getAsJsonArray("modifiers")) {
                modifiers.add(SFMKeyModifier.valueOf(modifier.getAsString()));
            }
            strokes.add(new SFMKeyStroke(stroke.get("keyCode").getAsInt(), modifiers));
        }
        ResourceLocation situation = defaultSituation == null
                ? new ResourceLocation(binding.get("situationId").getAsString())
                : defaultSituation;
        return new SFMKeyBinding(
                binding.get("bindingId").getAsString(),
                binding.get("actionId").getAsString(),
                binding.get("commandDraft").getAsString(),
                situation,
                new SFMKeySequence(strokes),
                binding.get("enabled").getAsBoolean());
    }

    private static SFMKeyBindingOverride readOverride(JsonObject override) {
        List<SFMKeyStroke> strokes = readStrokes(override);
        return new SFMKeyBindingOverride(
                override.get("bindingId").getAsString(),
                new ResourceLocation(override.get("situationId").getAsString()),
                new SFMKeySequence(strokes),
                override.get("enabled").getAsBoolean());
    }

    private static JsonObject toJson(SFMKeyBinding binding) {
        JsonObject result = new JsonObject();
        result.addProperty("bindingId", binding.bindingId());
        result.addProperty("actionId", binding.actionId());
        result.addProperty("commandDraft", binding.commandDraft());
        result.addProperty("situationId", binding.situationId().toString());
        result.addProperty("enabled", binding.enabled());
        JsonArray strokes = new JsonArray();
        for (SFMKeyStroke stroke : binding.sequence().strokes()) {
            JsonObject strokeJson = new JsonObject();
            strokeJson.addProperty("keyCode", stroke.keyCode());
            JsonArray modifiers = new JsonArray();
            stroke.modifiers().stream().sorted().forEach(modifier -> modifiers.add(modifier.name()));
            strokeJson.add("modifiers", modifiers);
            strokes.add(strokeJson);
        }
        result.add("strokes", strokes);
        return result;
    }

    private static JsonObject toJson(SFMKeyBindingOverride override) {
        JsonObject result = new JsonObject();
        result.addProperty("bindingId", override.bindingId());
        result.addProperty("situationId", override.situationId().toString());
        result.addProperty("enabled", override.enabled());
        JsonArray strokes = new JsonArray();
        for (SFMKeyStroke stroke : override.sequence().strokes()) {
            JsonObject strokeJson = new JsonObject();
            strokeJson.addProperty("keyCode", stroke.keyCode());
            JsonArray modifiers = new JsonArray();
            stroke.modifiers().stream().sorted().forEach(modifier -> modifiers.add(modifier.name()));
            strokeJson.add("modifiers", modifiers);
            strokes.add(strokeJson);
        }
        result.add("strokes", strokes);
        return result;
    }

    private static List<SFMKeyStroke> readStrokes(JsonObject owner) {
        List<SFMKeyStroke> strokes = new ArrayList<>();
        for (var strokeElement : owner.getAsJsonArray("strokes")) {
            JsonObject stroke = strokeElement.getAsJsonObject();
            EnumSet<SFMKeyModifier> modifiers = EnumSet.noneOf(SFMKeyModifier.class);
            for (var modifier : stroke.getAsJsonArray("modifiers")) {
                modifiers.add(SFMKeyModifier.valueOf(modifier.getAsString()));
            }
            strokes.add(new SFMKeyStroke(stroke.get("keyCode").getAsInt(), modifiers));
        }
        return strokes;
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("sfm-dynamic-keybindings.json");
    }

    private static void write(Path path, SFMKeyBindingUserState state) throws IOException {
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, serialize(state), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveUnavailable) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path nextSchema1Backup(Path path) throws IOException {
        Path candidate = path.resolveSibling(path.getFileName() + ".schema-1.bak");
        for (int suffix = 1; Files.exists(candidate); suffix++) {
            candidate = path.resolveSibling(path.getFileName() + ".schema-1.bak." + suffix);
        }
        return candidate;
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    enum LoadStatus {
        ABSENT,
        LOADED_SCHEMA_2,
        MIGRATED_SCHEMA_1,
        CORRUPT,
        UNSUPPORTED_SCHEMA,
        MIGRATION_PENDING
    }

    record LoadResult(
            SFMKeyBindingUserState state,
            LoadStatus status,
            int sourceSchema,
            boolean migrated,
            boolean writable,
            List<String> diagnostics
    ) {
        LoadResult {
            diagnostics = List.copyOf(diagnostics);
        }

        static LoadResult empty() {
            return new LoadResult(
                    SFMKeyBindingUserState.EMPTY,
                    LoadStatus.ABSENT,
                    0,
                    false,
                    true,
                    List.of());
        }

        boolean recovered() {
            return diagnostics.isEmpty();
        }
    }

    record SaveResult(boolean succeeded, List<String> diagnostics) {
        SaveResult {
            diagnostics = List.copyOf(diagnostics);
        }

        static SaveResult success() {
            return new SaveResult(true, List.of());
        }

        static SaveResult failure(String diagnostic) {
            return new SaveResult(false, List.of(diagnostic));
        }
    }
}
