package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.common.registry.SFMRegistryWrapper;
import ca.teamdman.sfm.common.registry.SFMWellKnownRegistries;
import ca.teamdman.sfm.common.util.MCVersionDependentBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
{% case minecraft_version %}
{% when "1.19.2" %}
import net.minecraft.data.BuiltinRegistries;
import net.minecraft.resources.ResourceLocation;
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
{% when "26.1.2" %}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
{% endcase %}
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Writes a human-readable snapshot of registries available to the client. */
public final class SFMRegistryDump {
    private SFMRegistryDump() {
    }

    public static Result write(Minecraft minecraft) {
        Path directory = minecraft.gameDirectory.toPath().resolve("SFM").resolve("registries");
        try {
            Files.createDirectories(directory);
            Map<String, RegistryResult> results = new LinkedHashMap<>();
            dumpWellKnownRegistries(directory, results);
            dumpBuiltinRegistries(directory, results);
            dumpLevelRegistries(minecraft, directory, results);
            writeRootSummary(directory, results);
            long unavailable = results.values().stream().filter(result -> !result.available()).count();
            return new Result(directory, results.size(), (int) unavailable);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write SFM registry dump to " + directory, exception);
        }
    }

    @MCVersionDependentBehaviour
    private static void dumpLevelRegistries(
            Minecraft minecraft,
            Path directory,
            Map<String, RegistryResult> results
    ) {
        if (minecraft.level == null) {
            return;
        }
        minecraft.level.registryAccess().registries().forEach(entry -> {
{% case minecraft_version %}
{% when "1.19.2" %}
            ResourceLocation registryId = entry.key().location();
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
            ResourceLocation registryId = entry.key().location();
{% when "26.1.2" %}
            Identifier registryId = entry.key().identifier();
{% endcase %}
            if (results.containsKey(registryId.toString())) return;
            try {
                List<String> ids = entry.value().keySet().stream()
{% case minecraft_version %}
{% when "1.19.2" %}
                        .map(ResourceLocation::toString)
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                        .map(ResourceLocation::toString)
{% when "26.1.2" %}
                        .map(Identifier::toString)
{% endcase %}
                        .sorted()
                        .toList();
                writeAvailable(directory, results, registryId.toString(), "client-level", ids);
            } catch (Throwable throwable) {
                recordUnavailable(directory, results, registryId.toString(), "client-level", throwable);
            }
        });
    }

    private static void dumpWellKnownRegistries(
            Path directory,
            Map<String, RegistryResult> results
    ) {
        List<Field> fields = List.of(SFMWellKnownRegistries.class.getFields()).stream()
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> SFMRegistryWrapper.class.isAssignableFrom(field.getType()))
                .sorted(Comparator.comparing(Field::getName))
                .toList();
        for (Field field : fields) {
            try {
                SFMRegistryWrapper<?> wrapper = (SFMRegistryWrapper<?>) field.get(null);
                dumpWrapper(directory, results, field.getName(), wrapper);
            } catch (Throwable throwable) {
                recordUnavailable(directory, results, field.getName(), field.getName(), throwable);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void dumpBuiltinRegistries(
            Path directory,
            Map<String, RegistryResult> results
    ) {
{% case minecraft_version %}
{% when "1.19.2" %}
        for (ResourceLocation registryId : BuiltinRegistries.REGISTRY.keySet()) {
            net.minecraft.core.Registry registry = BuiltinRegistries.REGISTRY.get(registryId);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        for (ResourceLocation registryId : BuiltInRegistries.REGISTRY.keySet()) {
            net.minecraft.core.Registry registry = BuiltInRegistries.REGISTRY.get(registryId);
{% when "26.1.2" %}
        for (Identifier registryId : BuiltInRegistries.REGISTRY.keySet()) {
            net.minecraft.core.Registry registry = BuiltInRegistries.REGISTRY.getValue(registryId);
{% endcase %}
            if (registry == null) continue;
            if (results.containsKey(registryId.toString())) continue;
            try {
                List<String> ids = registry.keySet().stream()
{% case minecraft_version %}
{% when "1.19.2" %}
                        .map(value -> ((ResourceLocation) value).toString())
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                        .map(value -> ((ResourceLocation) value).toString())
{% when "26.1.2" %}
                        .map(value -> ((Identifier) value).toString())
{% endcase %}
                        .sorted()
                        .toList();
                writeAvailable(directory, results, registryId.toString(), registryId.toString(), ids);
            } catch (Throwable throwable) {
                recordUnavailable(directory, results, registryId.toString(), registryId.toString(), throwable);
            }
        }
    }

    private static void dumpWrapper(
            Path directory,
            Map<String, RegistryResult> results,
            String fieldName,
            SFMRegistryWrapper<?> wrapper
    ) {
{% case minecraft_version %}
{% when "1.19.2" %}
        String registryId = wrapper.registryKey().location().toString();
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        String registryId = wrapper.registryKey().location().toString();
{% when "26.1.2" %}
        String registryId = wrapper.registryKey().identifier().toString();
{% endcase %}
        if (results.containsKey(registryId)) return;
        try {
            List<String> ids = wrapper.keys().stream()
{% case minecraft_version %}
{% when "1.19.2" %}
                    .map(ResourceLocation::toString)
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
                    .map(ResourceLocation::toString)
{% when "26.1.2" %}
                    .map(Identifier::toString)
{% endcase %}
                    .sorted()
                    .toList();
            writeAvailable(directory, results, registryId, fieldName, ids);
        } catch (Throwable throwable) {
            recordUnavailable(directory, results, registryId, fieldName, throwable);
        }
    }

    private static void writeAvailable(
            Path directory,
            Map<String, RegistryResult> results,
            String registryId,
            String source,
            List<String> ids
    ) {
        Path registryDirectory = registryDirectory(directory, registryId);
        try {
            Files.createDirectories(registryDirectory);
            writeLines(registryDirectory.resolve("entries.txt"), ids);
            writeLines(registryDirectory.resolve("summary.txt"), List.of(
                    "registry=" + registryId,
                    "source=" + source,
                    "status=available",
                    "count=" + ids.size()
            ));
            results.put(registryId, new RegistryResult(registryId, source, true, ids.size(), ""));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write registry " + registryId, exception);
        }
    }

    private static void recordUnavailable(
            Path directory,
            Map<String, RegistryResult> results,
            String registryId,
            String source,
            Throwable throwable
    ) {
        String reason = throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
        Path registryDirectory = registryDirectory(directory, registryId);
        try {
            Files.createDirectories(registryDirectory);
            writeLines(registryDirectory.resolve("entries.txt"), List.of("# unavailable: " + reason));
            writeLines(registryDirectory.resolve("summary.txt"), List.of(
                    "registry=" + registryId,
                    "source=" + source,
                    "status=unavailable",
                    "reason=" + reason,
                    "note=This registry may require an active client level on this Minecraft version."
            ));
            results.put(registryId, new RegistryResult(registryId, source, false, 0, reason));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write unavailable registry " + registryId, exception);
        }
    }

    private static void writeRootSummary(Path directory, Map<String, RegistryResult> results) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("SFM registry dump");
        lines.add("registry_count=" + results.size());
        lines.add("available_count=" + results.values().stream().filter(RegistryResult::available).count());
        lines.add("unavailable_count=" + results.values().stream().filter(result -> !result.available()).count());
        lines.add("");
        for (RegistryResult result : results.values()) {
            lines.add(result.registryId() + "\t" + (result.available() ? "available" : "unavailable")
                    + "\tcount=" + result.count() + "\tsource=" + result.source());
        }
        writeLines(directory.resolve("summary.txt"), lines);
    }

    private static Path registryDirectory(Path root, String registryId) {
{% case minecraft_version %}
{% when "1.19.2" %}
        ResourceLocation id = ResourceLocation.tryParse(registryId);
{% when "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        ResourceLocation id = ResourceLocation.tryParse(registryId);
{% when "26.1.2" %}
        Identifier id = Identifier.tryParse(registryId);
{% endcase %}
        String namespace = id == null ? "unknown" : id.getNamespace();
        String path = id == null ? registryId : id.getPath();
        return root.resolve(namespace).resolve(path.replace('/', '_'));
    }

    private static void writeLines(Path path, List<String> lines) throws IOException {
        Files.write(
                path,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    public record Result(
            Path directory,
            int registryCount,
            int unavailableCount
    ) {
    }

    private record RegistryResult(
            String registryId,
            String source,
            boolean available,
            int count,
            String reason
    ) {
    }
}
