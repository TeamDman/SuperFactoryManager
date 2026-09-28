package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.SFM;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

final class SFMKeyBindingStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA = 1;

    private SFMKeyBindingStorage() {
    }

    static List<SFMKeyBinding> load() {
        Path path = path();
        if (!Files.isRegularFile(path)) return List.of();
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != SCHEMA) {
                SFM.LOGGER.warn("Ignoring unsupported dynamic keybinding schema at {}", path);
                return List.of();
            }
            List<SFMKeyBinding> result = new ArrayList<>();
            for (var bindingElement : root.getAsJsonArray("bindings")) {
                JsonObject binding = bindingElement.getAsJsonObject();
                List<SFMKeyStroke> strokes = new ArrayList<>();
                for (var strokeElement : binding.getAsJsonArray("strokes")) {
                    JsonObject stroke = strokeElement.getAsJsonObject();
                    EnumSet<SFMKeyModifier> modifiers = EnumSet.noneOf(SFMKeyModifier.class);
                    for (var modifier : stroke.getAsJsonArray("modifiers")) {
                        modifiers.add(SFMKeyModifier.valueOf(modifier.getAsString()));
                    }
                    strokes.add(new SFMKeyStroke(stroke.get("keyCode").getAsInt(), modifiers));
                }
                result.add(new SFMKeyBinding(
                        binding.get("bindingId").getAsString(),
                        binding.get("actionId").getAsString(),
                        binding.get("commandDraft").getAsString(),
                        new SFMKeySequence(strokes),
                        binding.get("enabled").getAsBoolean()
                ));
            }
            return result;
        } catch (RuntimeException | IOException exception) {
            SFM.LOGGER.warn("Unable to load dynamic SFM keybindings from {}", path, exception);
            return List.of();
        }
    }

    static void save(SFMKeyBindingSnapshot snapshot) {
        Path path = path();
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        JsonArray bindings = new JsonArray();
        snapshot.bindings().stream()
                .filter(binding -> !binding.bindingId().startsWith("puppet-"))
                .forEach(binding -> bindings.add(toJson(binding)));
        root.add("bindings", bindings);
        try {
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SFM.LOGGER.warn("Unable to save dynamic SFM keybindings to {}", path, exception);
        }
    }

    private static JsonObject toJson(SFMKeyBinding binding) {
        JsonObject result = new JsonObject();
        result.addProperty("bindingId", binding.bindingId());
        result.addProperty("actionId", binding.actionId());
        result.addProperty("commandDraft", binding.commandDraft());
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

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("sfm-dynamic-keybindings.json");
    }
}
