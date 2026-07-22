package ca.teamdman.sfm.client.screen.review;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Review decisions keyed by stable snapshot/operation identity, optionally persisted atomically as JSON. */
public final class SFMReviewLedger {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SCHEMA = 1;

    public enum ReviewState { UNSEEN, REVIEWED }
    public enum HumanDecision { UNDECIDED, APPROVED, REJECTED }

    public record Key(String beforeSnapshot, String afterSnapshot, String operationId) {}
    public record Decision(ReviewState reviewState, HumanDecision humanDecision,
                           String expectedBeforeHash, String expectedAfterHash) {
        public boolean isStale(SFMSourceComparison.SourceOperation operation) {
            return !expectedBeforeHash.equals(operation.beforeHash()) || !expectedAfterHash.equals(operation.afterHash());
        }
    }

    private final Map<Key, Decision> decisions;
    private final Path storagePath;
    private String persistenceStatus;

    public SFMReviewLedger() {
        this(new LinkedHashMap<>(), null, "Memory-only ledger");
    }

    private SFMReviewLedger(Map<Key, Decision> decisions, Path storagePath, String status) {
        this.decisions = decisions;
        this.storagePath = storagePath;
        this.persistenceStatus = status;
    }

    public static SFMReviewLedger open(Path path) {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) return new SFMReviewLedger(new LinkedHashMap<>(), path, "Ledger ready: 0 persisted decisions");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schema").getAsInt() != SCHEMA) throw new IllegalArgumentException("Unsupported ledger schema");
            Map<Key, Decision> loaded = new LinkedHashMap<>();
            for (var element : root.getAsJsonArray("decisions")) {
                JsonObject item = element.getAsJsonObject();
                Key key = new Key(text(item, "beforeSnapshot"), text(item, "afterSnapshot"), text(item, "operationId"));
                Decision decision = new Decision(
                        ReviewState.valueOf(text(item, "reviewState")),
                        HumanDecision.valueOf(text(item, "humanDecision")),
                        text(item, "expectedBeforeHash"), text(item, "expectedAfterHash")
                );
                loaded.put(key, decision);
            }
            return new SFMReviewLedger(loaded, path, "Loaded " + loaded.size() + " persisted decision(s)");
        } catch (RuntimeException | IOException exception) {
            return new SFMReviewLedger(new LinkedHashMap<>(), path, "Ledger load failed: " + exception.getMessage());
        }
    }

    public Decision get(SFMSourceComparison comparison, SFMSourceComparison.SourceOperation operation) {
        return decisions.getOrDefault(key(comparison, operation), fresh(operation));
    }

    public void put(SFMSourceComparison comparison, SFMSourceComparison.SourceOperation operation,
                    ReviewState review, HumanDecision humanDecision) {
        decisions.put(key(comparison, operation), new Decision(Objects.requireNonNull(review),
                Objects.requireNonNull(humanDecision), operation.beforeHash(), operation.afterHash()));
        save();
    }

    public int size() { return decisions.size(); }
    public String persistenceStatus() { return persistenceStatus; }
    public Path storagePath() { return storagePath; }

    public void clear() {
        decisions.clear();
        save();
    }

    private void save() {
        if (storagePath == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        JsonArray array = new JsonArray();
        decisions.forEach((key, decision) -> {
            JsonObject item = new JsonObject();
            item.addProperty("beforeSnapshot", key.beforeSnapshot());
            item.addProperty("afterSnapshot", key.afterSnapshot());
            item.addProperty("operationId", key.operationId());
            item.addProperty("reviewState", decision.reviewState().name());
            item.addProperty("humanDecision", decision.humanDecision().name());
            item.addProperty("expectedBeforeHash", decision.expectedBeforeHash());
            item.addProperty("expectedAfterHash", decision.expectedAfterHash());
            array.add(item);
        });
        root.add("decisions", array);
        try {
            Files.createDirectories(storagePath.getParent());
            Path temporary = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnavailable) {
                Files.move(temporary, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
            persistenceStatus = "Saved " + decisions.size() + " persisted decision(s)";
        } catch (IOException exception) {
            persistenceStatus = "Ledger save failed: " + exception.getMessage();
        }
    }

    private static Decision fresh(SFMSourceComparison.SourceOperation operation) {
        return new Decision(ReviewState.UNSEEN, HumanDecision.UNDECIDED, operation.beforeHash(), operation.afterHash());
    }

    private static Key key(SFMSourceComparison comparison, SFMSourceComparison.SourceOperation operation) {
        return new Key(comparison.beforeSnapshot(), comparison.afterSnapshot(), operation.id());
    }

    private static String text(JsonObject object, String name) { return object.get(name).getAsString(); }
}
