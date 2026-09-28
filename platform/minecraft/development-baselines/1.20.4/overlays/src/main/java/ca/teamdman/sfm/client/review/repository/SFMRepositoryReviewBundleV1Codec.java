package ca.teamdman.sfm.client.review.repository;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Strict bounded parser and validator for the frozen repository-review bundle contract. */
public final class SFMRepositoryReviewBundleV1Codec {
    public static final int MAX_FILES_PER_SNAPSHOT = 100_000;
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    public static final long MAX_SNAPSHOT_BYTES = 256L * 1024 * 1024;
    public static final long MAX_JSON_BYTES = 512L * 1024 * 1024;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern BASE64 = Pattern.compile("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?");
    private static final Comparator<String> UTF8_ORDER = SFMRepositoryReviewBundleV1Codec::compareUtf8;

    private SFMRepositoryReviewBundleV1Codec() {}

    public static SFMRepositoryReviewBundleV1 parse(Path path) throws IOException {
        long size = Files.size(path);
        if (size > MAX_JSON_BYTES) throw invalid("Bundle exceeds 512 MiB encoded JSON limit");
        return parse(Files.readAllBytes(path));
    }

    public static SFMRepositoryReviewBundleV1 parse(byte[] jsonBytes) {
        if (jsonBytes.length > MAX_JSON_BYTES) throw invalid("Bundle exceeds 512 MiB encoded JSON limit");
        String json = decodeUtf8(jsonBytes);
        rejectDuplicateKeys(json);
        JsonElement parsed = JsonParser.parseString(json);
        if (!parsed.isJsonObject()) throw invalid("Bundle root must be an object");
        JsonObject root = parsed.getAsJsonObject();
        requireEqual(requiredText(root, "schema"), SFMRepositoryReviewBundleV1.SCHEMA, "bundle schema");
        JsonObject repositoryObject = object(root, "repository");
        String repositoryName = nfcNonEmpty(requiredText(repositoryObject, "name"), "repository.name");
        SFMRepositoryReviewBundleV1.Repository repository = new SFMRepositoryReviewBundleV1.Repository(
                repositoryName, requiredText(repositoryObject, "display_path"));
        JsonObject producerObject = object(root, "producer");
        SFMRepositoryReviewBundleV1.Producer producer = new SFMRepositoryReviewBundleV1.Producer(
                nonEmpty(requiredText(producerObject, "id"), "producer.id"),
                nonEmpty(requiredText(producerObject, "contract_version"), "producer.contract_version"));
        SFMRepositoryReviewBundleV1.Snapshot before = parseSnapshot(object(root, "before"), "before");
        SFMRepositoryReviewBundleV1.Snapshot after = parseSnapshot(object(root, "after"), "after");
        if (before.id().equals(after.id())) throw invalid("Before and after snapshots must have distinct ids");
        SFMRepositoryReviewBundleV1.Comparison comparison = parseComparison(
                object(root, "comparison"), before, after);
        String id = requiredText(root, "id");
        String expectedId = bundleId(repositoryName, before.id(), after.id());
        requireEqual(id, expectedId, "bundle id");
        return new SFMRepositoryReviewBundleV1(requiredText(root, "schema"), id,
                nonEmpty(requiredText(root, "name"), "name"), repository, producer, before, after, comparison);
    }

    public static String bundleId(String repositoryName, String beforeSnapshotId, String afterSnapshotId) {
        repositoryName = nfcNonEmpty(repositoryName, "repository.name");
        byte[] name = repositoryName.getBytes(StandardCharsets.UTF_8);
        byte[] before = digestFromId(beforeSnapshotId, "before snapshot id");
        byte[] after = digestFromId(afterSnapshotId, "after snapshot id");
        ByteBuffer framing = ByteBuffer.allocate(SFMRepositoryReviewBundleV1.SCHEMA.getBytes(StandardCharsets.UTF_8).length
                + 1 + Integer.BYTES + name.length + before.length + after.length);
        framing.put(SFMRepositoryReviewBundleV1.SCHEMA.getBytes(StandardCharsets.UTF_8)).put((byte) 0);
        framing.putInt(name.length).put(name).put(before).put(after);
        return "sha256:" + SFMReviewSessionV1Kernel.sha256(framing.array());
    }

    private static SFMRepositoryReviewBundleV1.Snapshot parseSnapshot(JsonObject value, String side) {
        requireEqual(requiredText(value, "schema"), SFMRepositoryReviewBundleV1.SNAPSHOT_SCHEMA, side + " snapshot schema");
        JsonObject sourceObject = object(value, "source");
        SFMRepositoryReviewBundleV1.Source source = new SFMRepositoryReviewBundleV1.Source(
                nonEmpty(requiredText(sourceObject, "kind"), side + ".source.kind"),
                nonEmpty(requiredText(sourceObject, "revision"), side + ".source.revision"),
                nonEmpty(requiredText(sourceObject, "label"), side + ".source.label"));
        JsonArray fileValues = array(value, "files");
        validateBounds(fileValues.size(), 0, 0, side);
        List<SFMRepositoryReviewBundleV1.FileEntry> files = new ArrayList<>(fileValues.size());
        Set<String> paths = new HashSet<>();
        String previous = null;
        long total = 0;
        for (JsonElement element : fileValues) {
            JsonObject file = asObject(element, side + " snapshot file");
            String path = validatePath(requiredText(file, "path"));
            if (!paths.add(path)) throw invalid("Duplicate path in " + side + " snapshot: " + path);
            if (previous != null && UTF8_ORDER.compare(previous, path) >= 0)
                throw invalid(side + " snapshot files are not strictly path-sorted: " + path);
            previous = path;
            String encodingName = requiredText(file, "encoding");
            SFMRepositoryReviewBundleV1.Encoding encoding;
            String text = null;
            String data = null;
            byte[] content;
            if ("utf8".equals(encodingName)) {
                encoding = SFMRepositoryReviewBundleV1.Encoding.UTF8;
                if (!file.has("text") || file.has("data")) throw invalid(path + " must contain text xor data");
                text = requiredText(file, "text");
                content = encodeUtf8(text, path);
            } else if ("base64".equals(encodingName)) {
                encoding = SFMRepositoryReviewBundleV1.Encoding.BASE64;
                if (!file.has("data") || file.has("text")) throw invalid(path + " must contain data xor text");
                data = requiredText(file, "data");
                if (!BASE64.matcher(data).matches()) throw invalid("Invalid padded RFC 4648 base64 for " + path);
                try { content = Base64.getDecoder().decode(data); }
                catch (IllegalArgumentException exception) { throw invalid("Invalid base64 for " + path); }
            } else throw invalid("Unknown encoding '" + encodingName + "' for " + path);
            total += content.length;
            validateBounds(fileValues.size(), content.length, total, side + ":" + path);
            String sha256 = hash(requiredText(file, "sha256"), path + " sha256");
            requireEqual(SFMReviewSessionV1Kernel.sha256(content), sha256, path + " content sha256");
            files.add(new SFMRepositoryReviewBundleV1.FileEntry(path, encoding, text, data, sha256, content));
        }
        String id = requiredText(value, "id");
        String expected = snapshotId(files);
        requireEqual(id, expected, side + " snapshot id");
        return new SFMRepositoryReviewBundleV1.Snapshot(requiredText(value, "schema"), id, source, files);
    }

    private static SFMRepositoryReviewBundleV1.Comparison parseComparison(
            JsonObject value,
            SFMRepositoryReviewBundleV1.Snapshot before,
            SFMRepositoryReviewBundleV1.Snapshot after
    ) {
        requireEqual(requiredText(value, "schema"), SFMRepositoryReviewBundleV1.COMPARISON_SCHEMA, "comparison schema");
        requireEqual(requiredText(value, "before_snapshot_id"), before.id(), "comparison before_snapshot_id");
        requireEqual(requiredText(value, "after_snapshot_id"), after.id(), "comparison after_snapshot_id");
        Map<String, SFMRepositoryReviewBundleV1.FileEntry> beforeFiles = filesByPath(before);
        Map<String, SFMRepositoryReviewBundleV1.FileEntry> afterFiles = filesByPath(after);
        List<SFMRepositoryReviewBundleV1.FileChange> changes = new ArrayList<>();
        Set<String> operationIds = new HashSet<>();
        Set<String> changeKeys = new HashSet<>();
        String previousKey = null;
        for (JsonElement element : array(value, "file_changes")) {
            JsonObject change = asObject(element, "file change");
            SFMRepositoryReviewBundleV1.ChangeKind kind = enumValue(
                    SFMRepositoryReviewBundleV1.ChangeKind.class, requiredText(change, "kind"), "change kind");
            String beforePath = nullableText(change, "before_path");
            String afterPath = nullableText(change, "after_path");
            if (beforePath != null) beforePath = validatePath(beforePath);
            if (afterPath != null) afterPath = validatePath(afterPath);
            validateChangePaths(kind, beforePath, afterPath, beforeFiles, afterFiles);
            String key = afterPath != null ? afterPath : beforePath;
            if (!changeKeys.add(key)) throw invalid("Duplicate file change path " + key);
            if (previousKey != null && UTF8_ORDER.compare(previousKey, key) >= 0)
                throw invalid("File changes are not strictly path-sorted: " + key);
            previousKey = key;
            boolean binary = bool(change, "binary");
            List<String> diagnostics = strings(change, "diagnostics");
            List<SFMRepositoryReviewBundleV1.Operation> operations = new ArrayList<>();
            for (JsonElement operationElement : array(change, "operations")) {
                JsonObject operation = asObject(operationElement, "comparison operation");
                String operationId = nonEmpty(requiredText(operation, "id"), "operation.id");
                if (!operationIds.add(operationId)) throw invalid("Duplicate operation id " + operationId);
                SFMRepositoryReviewBundleV1.OperationKind operationKind = enumValue(
                        SFMRepositoryReviewBundleV1.OperationKind.class, requiredText(operation, "kind"), "operation kind");
                SFMRepositoryReviewBundleV1.Selection beforeSelection = nullableSelection(
                        operation, "before", beforeFiles);
                SFMRepositoryReviewBundleV1.Selection afterSelection = nullableSelection(
                        operation, "after", afterFiles);
                validateOperation(operationId, operationKind, beforeSelection, afterSelection);
                if (binary) throw invalid("Binary change " + key + " must carry diagnostics instead of text operations");
                boolean empty = (beforeSelection != null && beforeSelection.startByte() == beforeSelection.endByte())
                        || (afterSelection != null && afterSelection.startByte() == afterSelection.endByte());
                if (empty && diagnostics.isEmpty())
                    throw invalid("Empty operation " + operationId + " requires a visible diagnostic");
                operations.add(new SFMRepositoryReviewBundleV1.Operation(
                        operationId, operationKind, beforeSelection, afterSelection));
            }
            if (binary && diagnostics.isEmpty()) throw invalid("Binary change " + key + " requires diagnostics");
            changes.add(new SFMRepositoryReviewBundleV1.FileChange(
                    kind, beforePath, afterPath, binary, operations, diagnostics));
        }
        return new SFMRepositoryReviewBundleV1.Comparison(requiredText(value, "schema"), before.id(), after.id(), changes);
    }

    private static void validateChangePaths(SFMRepositoryReviewBundleV1.ChangeKind kind, String beforePath,
                                            String afterPath, Map<String, ?> beforeFiles, Map<String, ?> afterFiles) {
        boolean needsBefore = kind != SFMRepositoryReviewBundleV1.ChangeKind.ADDED;
        boolean needsAfter = kind != SFMRepositoryReviewBundleV1.ChangeKind.REMOVED;
        if (needsBefore != (beforePath != null) || needsAfter != (afterPath != null))
            throw invalid("Invalid before/after paths for " + kind.name().toLowerCase(Locale.ROOT));
        if (beforePath != null && !beforeFiles.containsKey(beforePath)) throw invalid("Dangling before path " + beforePath);
        if (afterPath != null && !afterFiles.containsKey(afterPath)) throw invalid("Dangling after path " + afterPath);
        if ((kind == SFMRepositoryReviewBundleV1.ChangeKind.MODIFIED
                || kind == SFMRepositoryReviewBundleV1.ChangeKind.UNCHANGED) && !beforePath.equals(afterPath))
            throw invalid(kind.name().toLowerCase(Locale.ROOT) + " change must retain its path");
    }

    private static void validateOperation(String id, SFMRepositoryReviewBundleV1.OperationKind kind,
                                          SFMRepositoryReviewBundleV1.Selection before,
                                          SFMRepositoryReviewBundleV1.Selection after) {
        boolean valid = switch (kind) {
            case INSERT -> before == null && after != null;
            case DELETE -> before != null && after == null;
            case REPLACE -> before != null && after != null;
        };
        if (!valid) throw invalid("Invalid selection sides for operation " + id + " (" + kind + ")");
    }

    private static SFMRepositoryReviewBundleV1.Selection nullableSelection(
            JsonObject parent, String name, Map<String, SFMRepositoryReviewBundleV1.FileEntry> files
    ) {
        if (!parent.has(name)) throw invalid("Missing explicit operation side '" + name + "'");
        if (parent.get(name) instanceof JsonNull) return null;
        JsonObject value = object(parent, name);
        String path = validatePath(requiredText(value, "path"));
        SFMRepositoryReviewBundleV1.FileEntry file = files.get(path);
        if (file == null) throw invalid("Dangling selection path " + path);
        if (file.encoding() != SFMRepositoryReviewBundleV1.Encoding.UTF8)
            throw invalid("Text selection references binary file " + path);
        int start = boundedInt(value, "start_byte");
        int end = boundedInt(value, "end_byte");
        byte[] bytes = file.content();
        if (start < 0 || end < start || end > bytes.length || !utf8Boundary(bytes, start) || !utf8Boundary(bytes, end))
            throw invalid("Selection range is out of bounds or splits UTF-8 for " + path);
        String selectionHash = hash(requiredText(value, "sha256"), "selection sha256");
        requireEqual(SFMReviewSessionV1Kernel.sha256(java.util.Arrays.copyOfRange(bytes, start, end)),
                selectionHash, "selected bytes sha256 for " + path);
        return new SFMRepositoryReviewBundleV1.Selection(path, start, end, selectionHash);
    }

    private static String snapshotId(List<SFMRepositoryReviewBundleV1.FileEntry> files) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream output = new java.io.DataOutputStream(bytes)) {
            output.write(SFMRepositoryReviewBundleV1.SNAPSHOT_SCHEMA.getBytes(StandardCharsets.UTF_8));
            output.writeByte(0);
            for (SFMRepositoryReviewBundleV1.FileEntry file : files) {
                byte[] path = file.path().getBytes(StandardCharsets.UTF_8);
                byte[] content = file.content();
                output.writeInt(path.length);
                output.write(path);
                output.writeByte(file.encoding() == SFMRepositoryReviewBundleV1.Encoding.UTF8 ? 0 : 1);
                output.writeLong(content.length);
                output.write(content);
            }
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return "sha256:" + SFMReviewSessionV1Kernel.sha256(bytes.toByteArray());
    }

    private static Map<String, SFMRepositoryReviewBundleV1.FileEntry> filesByPath(
            SFMRepositoryReviewBundleV1.Snapshot snapshot) {
        Map<String, SFMRepositoryReviewBundleV1.FileEntry> result = new LinkedHashMap<>();
        snapshot.files().forEach(file -> result.put(file.path(), file));
        return result;
    }

    private static void rejectDuplicateKeys(String json) {
        try {
            JsonReader reader = new JsonReader(new StringReader(json));
            reader.setLenient(false);
            consumeUnique(reader, "$");
            if (reader.peek() != JsonToken.END_DOCUMENT) throw invalid("Trailing JSON content");
        } catch (IOException | IllegalStateException exception) {
            throw invalid("Invalid JSON: " + exception.getMessage());
        }
    }

    private static void consumeUnique(JsonReader reader, String path) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) throw invalid("Duplicate object key '" + name + "' at " + path);
                    consumeUnique(reader, path + "." + name);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                int index = 0;
                while (reader.hasNext()) consumeUnique(reader, path + "[" + index++ + "]");
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw invalid("Unexpected JSON token " + reader.peek() + " at " + path);
        }
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) { throw invalid("Bundle is not strict UTF-8"); }
    }

    private static byte[] encodeUtf8(String value, String label) {
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(value));
            byte[] result = new byte[encoded.remaining()]; encoded.get(result); return result;
        } catch (CharacterCodingException exception) { throw invalid(label + " text is not valid Unicode"); }
    }

    private static String validatePath(String path) {
        if (!Normalizer.isNormalized(path, Normalizer.Form.NFC)) throw invalid("Path is not Unicode NFC: " + path);
        if (path.isEmpty() || path.indexOf('\0') >= 0 || path.contains("\\") || path.startsWith("/")
                || path.startsWith("//") || path.matches("^[A-Za-z]:.*"))
            throw invalid("Path is not normalized repository-relative: " + path);
        for (String segment : path.split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals(".."))
                throw invalid("Path contains invalid segment: " + path);
        encodeUtf8(path, "path");
        return path;
    }

    private static int compareUtf8(String left, String right) {
        byte[] a = left.getBytes(StandardCharsets.UTF_8), b = right.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int compared = Integer.compare(Byte.toUnsignedInt(a[i]), Byte.toUnsignedInt(b[i]));
            if (compared != 0) return compared;
        }
        return Integer.compare(a.length, b.length);
    }

    private static boolean utf8Boundary(byte[] bytes, int offset) {
        return offset >= 0 && offset <= bytes.length && (offset == bytes.length || (bytes[offset] & 0xC0) != 0x80);
    }

    static void validateBounds(int fileCount, long fileBytes, long snapshotBytes, String label) {
        if (fileCount > MAX_FILES_PER_SNAPSHOT) throw invalid(label + " exceeds 100,000-file limit");
        if (fileBytes > MAX_FILE_BYTES) throw invalid(label + " exceeds 16 MiB decoded file limit");
        if (snapshotBytes > MAX_SNAPSHOT_BYTES) throw invalid(label + " exceeds 256 MiB decoded snapshot limit");
    }

    private static byte[] digestFromId(String id, String label) {
        if (!id.startsWith("sha256:") || !SHA256.matcher(id.substring(7)).matches())
            throw invalid(label + " must be sha256:<lowercase hex>");
        return HexFormat.of().parseHex(id.substring(7));
    }

    private static String hash(String value, String label) {
        if (!SHA256.matcher(value).matches()) throw invalid(label + " must be lowercase SHA-256 hex");
        return value;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw invalid("Unknown " + label + " '" + value + "'"); }
    }

    private static String nfcNonEmpty(String value, String label) {
        if (!Normalizer.isNormalized(value, Normalizer.Form.NFC)) throw invalid(label + " must be Unicode NFC");
        encodeUtf8(value, label);
        return nonEmpty(value, label);
    }
    private static String nonEmpty(String value, String label) {
        if (value.isEmpty()) throw invalid(label + " must not be empty"); return value;
    }
    private static int boundedInt(JsonObject value, String name) {
        long number = value.get(name).getAsLong();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw invalid(name + " is out of integer range");
        return (int) number;
    }
    private static List<String> strings(JsonObject value, String name) {
        List<String> result = new ArrayList<>();
        for (JsonElement item : array(value, name)) result.add(item.getAsString());
        return result;
    }
    private static String nullableText(JsonObject value, String name) {
        if (!value.has(name)) throw invalid("Missing field '" + name + "'");
        return value.get(name).isJsonNull() ? null : value.get(name).getAsString();
    }
    private static String requiredText(JsonObject value, String name) {
        if (!value.has(name) || value.get(name).isJsonNull()) throw invalid("Missing field '" + name + "'");
        return value.get(name).getAsString();
    }
    private static boolean bool(JsonObject value, String name) {
        if (!value.has(name) || value.get(name).isJsonNull()) throw invalid("Missing field '" + name + "'");
        return value.get(name).getAsBoolean();
    }
    private static JsonArray array(JsonObject value, String name) {
        if (!value.has(name) || !value.get(name).isJsonArray()) throw invalid("Field '" + name + "' must be an array");
        return value.getAsJsonArray(name);
    }
    private static JsonObject object(JsonObject value, String name) {
        if (!value.has(name) || !value.get(name).isJsonObject()) throw invalid("Field '" + name + "' must be an object");
        return value.getAsJsonObject(name);
    }
    private static JsonObject asObject(JsonElement value, String label) {
        if (!value.isJsonObject()) throw invalid(label + " must be an object"); return value.getAsJsonObject();
    }
    private static void requireEqual(String actual, String expected, String label) {
        if (!expected.equals(actual)) throw invalid("Invalid " + label + ": expected '" + expected + "' but found '" + actual + "'");
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
