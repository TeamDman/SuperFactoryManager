package ca.teamdman.sfm.client.overlay.scene;

import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ContentRecipe;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.InputMode;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayInstanceId;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.OverlayState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.Placement;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.ReferenceFrame;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SceneState;
import ca.teamdman.sfm.client.overlay.scene.SFMOverlaySceneContract.SizeConstraints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Strict, deterministic codec for {@code sfm.client-scene/1}. */
public final class SFMOverlaySceneJsonCodec {
    private static final int MAX_JSON_NESTING_DEPTH = 32;
    private static final int MAX_OBJECT_MEMBERS = 256;
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setPrettyPrinting()
            .create();

    private SFMOverlaySceneJsonCodec() {
    }

    public static String write(SceneState scene) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", scene.schema());
        root.addProperty("revision", scene.revision());
        root.add("focused_overlay", scene.focusedOverlay()
                .<JsonElement>map(id -> new JsonPrimitive(id.value()))
                .orElse(JsonNull.INSTANCE));
        JsonArray overlays = new JsonArray();
        scene.overlays().forEach(overlay -> overlays.add(writeOverlay(overlay)));
        root.add("overlays", overlays);
        return GSON.toJson(root) + "\n";
    }

    public static SceneState read(String text) {
        if (text == null) throw new NullPointerException("text");
        if (text.getBytes(StandardCharsets.UTF_8).length > SFMOverlaySceneContract.MAX_SCENE_JSON_BYTES) {
            throw new IllegalArgumentException("Client scene JSON exceeds the bounded UTF-8 size");
        }
        JsonObject root = object(parseStrict(text), "client scene");
        fields(root, "client scene", "schema", "revision", "focused_overlay", "overlays");
        JsonArray encodedOverlays = array(root, "overlays", SFMOverlaySceneContract.MAX_OVERLAYS);
        ArrayList<OverlayState> overlays = new ArrayList<>(encodedOverlays.size());
        for (JsonElement encoded : encodedOverlays) overlays.add(readOverlay(object(encoded, "overlay")));
        return new SceneState(
                string(root, "schema"),
                exactLong(root, "revision"),
                overlays,
                optionalString(root, "focused_overlay").map(OverlayInstanceId::new)
        );
    }

    private static JsonObject writeOverlay(OverlayState overlay) {
        JsonObject result = new JsonObject();
        result.addProperty("id", overlay.id().value());
        JsonObject recipe = new JsonObject();
        recipe.addProperty("content_id", overlay.recipe().contentId());
        recipe.addProperty("argument", overlay.recipe().argument());
        result.add("recipe", recipe);
        result.addProperty("visible", overlay.visible());
        result.add("placement", writePlacement(overlay.placement()));
        result.addProperty("input_mode", overlay.inputMode().wire());
        result.addProperty("z_order", overlay.zOrder());
        JsonObject persisted = new JsonObject();
        new TreeMap<>(overlay.persistedState()).forEach(persisted::addProperty);
        result.add("persisted_state", persisted);
        return result;
    }

    private static OverlayState readOverlay(JsonObject value) {
        fields(value, "overlay", "id", "recipe", "visible", "placement", "input_mode", "z_order",
                "persisted_state");
        JsonObject recipe = requiredObject(value, "recipe");
        fields(recipe, "content recipe", "content_id", "argument");
        JsonObject persisted = requiredObject(value, "persisted_state");
        if (persisted.size() > MAX_OBJECT_MEMBERS) {
            throw new IllegalArgumentException("persisted_state exceeds the bounded member count");
        }
        TreeMap<String, String> persistedState = new TreeMap<>();
        persisted.entrySet().forEach(entry -> persistedState.put(entry.getKey(), string(entry.getValue(),
                "persisted_state." + entry.getKey())));
        return new OverlayState(
                new OverlayInstanceId(string(value, "id")),
                new ContentRecipe(string(recipe, "content_id"), string(recipe, "argument")),
                bool(value, "visible"),
                readPlacement(requiredObject(value, "placement")),
                InputMode.parse(string(value, "input_mode")),
                exactInt(value, "z_order"),
                persistedState
        );
    }

    private static JsonObject writePlacement(Placement placement) {
        JsonObject result = new JsonObject();
        result.addProperty("reference_frame", placement.referenceFrame().wire());
        result.add("reference_anchor", pair(placement.referenceAnchorU(), placement.referenceAnchorV()));
        result.add("content_anchor", pair(placement.contentAnchorU(), placement.contentAnchorV()));
        result.add("logical_offset", pair(placement.logicalOffsetX(), placement.logicalOffsetY()));
        if (placement.sizeConstraints().isPresent()) {
            SizeConstraints size = placement.sizeConstraints().orElseThrow();
            JsonObject encoded = new JsonObject();
            encoded.addProperty("minimum_width", size.minimumWidth());
            encoded.addProperty("minimum_height", size.minimumHeight());
            encoded.addProperty("preferred_width", size.preferredWidth());
            encoded.addProperty("preferred_height", size.preferredHeight());
            encoded.addProperty("maximum_width", size.maximumWidth());
            encoded.addProperty("maximum_height", size.maximumHeight());
            result.add("size_constraints", encoded);
        } else {
            result.add("size_constraints", JsonNull.INSTANCE);
        }
        result.addProperty("clip_policy", placement.clipPolicy().wire());
        return result;
    }

    private static Placement readPlacement(JsonObject value) {
        fields(value, "placement", "reference_frame", "reference_anchor", "content_anchor", "logical_offset",
                "size_constraints", "clip_policy");
        JsonArray reference = array(value, "reference_anchor", 2);
        JsonArray content = array(value, "content_anchor", 2);
        JsonArray offset = array(value, "logical_offset", 2);
        requirePair(reference, "reference_anchor");
        requirePair(content, "content_anchor");
        requirePair(offset, "logical_offset");
        JsonElement encodedSize = required(value, "size_constraints");
        Optional<SizeConstraints> size;
        if (encodedSize.isJsonNull()) {
            size = Optional.empty();
        } else {
            JsonObject encoded = object(encodedSize, "size_constraints");
            fields(encoded, "size constraints", "minimum_width", "minimum_height", "preferred_width",
                    "preferred_height", "maximum_width", "maximum_height");
            size = Optional.of(new SizeConstraints(
                    exactInt(encoded, "minimum_width"),
                    exactInt(encoded, "minimum_height"),
                    exactInt(encoded, "preferred_width"),
                    exactInt(encoded, "preferred_height"),
                    exactInt(encoded, "maximum_width"),
                    exactInt(encoded, "maximum_height")
            ));
        }
        return new Placement(
                ReferenceFrame.parse(string(value, "reference_frame")),
                exactDouble(reference.get(0), "reference_anchor[0]"),
                exactDouble(reference.get(1), "reference_anchor[1]"),
                exactDouble(content.get(0), "content_anchor[0]"),
                exactDouble(content.get(1), "content_anchor[1]"),
                exactInt(offset.get(0), "logical_offset[0]"),
                exactInt(offset.get(1), "logical_offset[1]"),
                size,
                SFMOverlaySceneContract.ClipPolicy.parse(string(value, "clip_policy"))
        );
    }

    private static JsonArray pair(Number first, Number second) {
        JsonArray result = new JsonArray();
        result.add(first);
        result.add(second);
        return result;
    }

    private static void requirePair(JsonArray value, String label) {
        if (value.size() != 2) throw new IllegalArgumentException(label + " must contain exactly two values");
    }

    private static JsonElement parseStrict(String text) {
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            reader.setLenient(false);
            JsonElement value = readElement(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("Client scene JSON must contain exactly one value");
            }
            return value;
        } catch (IOException | IllegalStateException | NumberFormatException invalid) {
            throw new IllegalArgumentException("Malformed client scene JSON", invalid);
        }
    }

    private static JsonElement readElement(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_NESTING_DEPTH) {
            throw new IllegalArgumentException("Client scene JSON exceeds the bounded nesting depth");
        }
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readObject(reader, depth);
            case BEGIN_ARRAY -> readArray(reader, depth);
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> new JsonPrimitive(new BigDecimal(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new IllegalArgumentException("Unexpected JSON token " + reader.peek());
        };
    }

    private static JsonObject readObject(JsonReader reader, int depth) throws IOException {
        reader.beginObject();
        JsonObject result = new JsonObject();
        int count = 0;
        while (reader.hasNext()) {
            if (++count > MAX_OBJECT_MEMBERS) {
                throw new IllegalArgumentException("JSON object exceeds the bounded member count");
            }
            String name = reader.nextName();
            if (result.has(name)) throw new IllegalArgumentException("Duplicate JSON field: " + name);
            result.add(name, readElement(reader, depth + 1));
        }
        reader.endObject();
        return result;
    }

    private static JsonArray readArray(JsonReader reader, int depth) throws IOException {
        reader.beginArray();
        JsonArray result = new JsonArray();
        while (reader.hasNext()) {
            if (result.size() >= SFMOverlaySceneContract.MAX_OVERLAYS) {
                throw new IllegalArgumentException("JSON array exceeds the bounded element count");
            }
            result.add(readElement(reader, depth + 1));
        }
        reader.endArray();
        return result;
    }

    private static JsonObject requiredObject(JsonObject owner, String key) {
        return object(required(owner, key), key);
    }

    private static JsonObject object(JsonElement value, String label) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException(label + " must be an object");
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject owner, String key, int maximum) {
        JsonElement value = required(owner, key);
        if (!value.isJsonArray()) throw new IllegalArgumentException(key + " must be an array");
        JsonArray result = value.getAsJsonArray();
        if (result.size() > maximum) throw new IllegalArgumentException(key + " exceeds the bounded element count");
        return result;
    }

    private static Optional<String> optionalString(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        return value.isJsonNull() ? Optional.empty() : Optional.of(string(value, key));
    }

    private static String string(JsonObject owner, String key) {
        return string(required(owner, key), key);
    }

    private static String string(JsonElement value, String label) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(label + " must be a string");
        }
        return value.getAsString();
    }

    private static boolean bool(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static int exactInt(JsonObject owner, String key) {
        return exactInt(required(owner, key), key);
    }

    private static int exactInt(JsonElement value, String label) {
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be an exact 32-bit integer", invalid);
        }
    }

    private static long exactLong(JsonObject owner, String key) {
        JsonElement value = required(owner, key);
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new ArithmeticException();
            return value.getAsBigDecimal().longValueExact();
        } catch (ArithmeticException | NumberFormatException invalid) {
            throw new IllegalArgumentException(key + " must be an exact 64-bit integer", invalid);
        }
    }

    private static double exactDouble(JsonElement value, String label) {
        try {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new NumberFormatException();
            double result = value.getAsDouble();
            if (!Double.isFinite(result)) throw new NumberFormatException();
            return result;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " must be a finite number", invalid);
        }
    }

    private static JsonElement required(JsonObject owner, String key) {
        JsonElement value = owner.get(key);
        if (value == null) throw new IllegalArgumentException("Missing " + key);
        return value;
    }

    private static void fields(JsonObject value, String label, String... names) {
        Set<String> expected = new HashSet<>(Arrays.asList(names));
        for (String present : value.keySet()) {
            if (!expected.remove(present)) throw new IllegalArgumentException("Unknown " + label + " field: " + present);
        }
        if (!expected.isEmpty()) {
            throw new IllegalArgumentException("Missing " + label + " fields: " + String.join(", ", expected));
        }
    }
}
