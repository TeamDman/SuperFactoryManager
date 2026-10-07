package ca.teamdman.sfm.client.explorer.lazy;

import ca.teamdman.sfm.client.explorer.SFMPath;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.util.*;

/** Explicit portable preferences, not ambient workspace or review state. No IO or evaluation on decode. */
public final class SFMExplorerCompactionPreset {
    public static final String SCHEMA = "sfm.explorer-compaction/1";
    public static final int MAX_CHARACTERS = 131072;
    private SFMExplorerCompactionPreset() { }
    public static String encode(SFMExplorerCompaction.Options value) {
        var output = new StringWriter();
        try (var json = new JsonWriter(output)) {
            json.beginObject().name("schema").value(SCHEMA).name("enabled").value(value.enabled()).name("overrides").beginObject();
            for (var item : new TreeMap<>(value.overrides()).entrySet()) json.name(item.getKey().canonical()).value(item.getValue());
            json.endObject().endObject();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        if (output.getBuffer().length() > MAX_CHARACTERS) throw new IllegalArgumentException("Compaction preset exceeds 128 Ki characters");
        return output.toString();
    }
    public static SFMExplorerCompaction.Options decode(String text) {
        if (text.length() > MAX_CHARACTERS) throw new IllegalArgumentException("Compaction preset exceeds 128 Ki characters");
        try (var json = new JsonReader(new StringReader(text))) {
            json.setLenient(false);
            String schema = null; Boolean enabled = null;
            var seen = new HashSet<String>(); var overrides = new TreeMap<SFMPath, Boolean>();
            json.beginObject();
            while (json.hasNext()) {
                String key = json.nextName();
                if (!seen.add(key)) throw new IllegalArgumentException("Duplicate compaction preset field: " + key);
                switch (key) {
                    case "schema" -> { if (json.peek() != JsonToken.STRING) throw new IllegalArgumentException("Schema must be text"); schema = json.nextString(); }
                    case "enabled" -> enabled = json.nextBoolean();
                    case "overrides" -> {
                        json.beginObject();
                        while (json.hasNext()) {
                            if (overrides.size() >= SFMExplorerCompaction.MAX_OVERRIDES) throw new IllegalArgumentException("Too many overrides");
                            String spelling = json.nextName(); var path = SFMPath.parse(spelling);
                            if (!path.canonical().equals(spelling) || overrides.containsKey(path))
                                throw new IllegalArgumentException("Override paths must be unique canonical addresses");
                            overrides.put(path, json.nextBoolean());
                        }
                        json.endObject();
                    }
                    default -> throw new IllegalArgumentException("Unknown compaction preset field: " + key);
                }
            }
            json.endObject();
            if (!SCHEMA.equals(schema) || enabled == null || !seen.contains("overrides") || json.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("Expected complete " + SCHEMA + " preset");
            return new SFMExplorerCompaction.Options(enabled, overrides);
        } catch (IOException | IllegalStateException failure) { throw new IllegalArgumentException("Invalid compaction preset: " + failure.getMessage(), failure); }
    }
}
