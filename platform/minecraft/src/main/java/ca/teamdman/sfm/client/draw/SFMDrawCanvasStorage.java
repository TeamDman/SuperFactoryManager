package ca.teamdman.sfm.client.draw;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public final class SFMDrawCanvasStorage {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private SFMDrawCanvasStorage() {
    }

    public static SFMDrawCanvasDocument loadOrCreate(SFMDrawVirtualPath canvasPath) throws IOException {
        if (Files.exists(canvasPath.mountedPath())) {
            return read(canvasPath);
        }

        SFMDrawCanvasDocument blankCanvas = SFMDrawCanvasDocument.blank();
        write(canvasPath, blankCanvas);
        return blankCanvas;
    }

    public static SFMDrawCanvasDocument read(SFMDrawVirtualPath canvasPath) throws IOException {
        String json = Files.readString(canvasPath.mountedPath(), StandardCharsets.UTF_8);
        return parse(json, canvasPath.virtualPath());
    }

    public static SFMDrawCanvasDocument parse(String json) throws IOException {
        return parse(json, "clipboard");
    }

    public static String toJson(SFMDrawCanvasDocument document) {
        return GSON.toJson(writeDocument(document));
    }

    private static SFMDrawCanvasDocument parse(
            String json,
            String sourceName
    ) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return new SFMDrawCanvasDocument(
                    getInt(root, "version", SFMDrawCanvasDocument.CURRENT_VERSION),
                    getDouble(root, "cameraX", 0.0D),
                    getDouble(root, "cameraY", 0.0D),
                    getDouble(root, "zoom", 1.0D),
                    getString(root, "activeLayer", "ELEMENTS"),
                    getBoolean(root, "elementsLayerMuted", false),
                    getBoolean(root, "chromeLayerMuted", false),
                    getBoolean(root, "historyLayerMuted", true),
                    readLayerOrigins(root.getAsJsonArray("layerOrigins")),
                    getInt(root, "nextElementId", 1),
                    getInt(root, "nextGroupId", 1),
                    readElements(root.getAsJsonArray("elements")),
                    readUndoTree(root.getAsJsonObject("undoTree"))
            );
        } catch (JsonParseException exception) {
            throw new IOException("Failed to parse canvas file: " + sourceName, exception);
        }
    }

    public static void write(
            SFMDrawVirtualPath canvasPath,
            SFMDrawCanvasDocument document
    ) throws IOException {
        Files.createDirectories(canvasPath.mountedPath().getParent());
        Files.writeString(canvasPath.mountedPath(), toJson(document), StandardCharsets.UTF_8);
    }

    private static JsonObject writeDocument(SFMDrawCanvasDocument document) {
        JsonObject root = new JsonObject();
        root.addProperty("version", document.version());
        root.addProperty("cameraX", document.cameraX());
        root.addProperty("cameraY", document.cameraY());
        root.addProperty("zoom", document.zoom());
        root.addProperty("activeLayer", document.activeLayer());
        root.addProperty("elementsLayerMuted", document.elementsLayerMuted());
        root.addProperty("chromeLayerMuted", document.chromeLayerMuted());
        root.addProperty("historyLayerMuted", document.historyLayerMuted());
        root.add("layerOrigins", writeLayerOrigins(document.layerOrigins()));
        root.addProperty("nextElementId", document.nextElementId());
        root.addProperty("nextGroupId", document.nextGroupId());
        root.add("elements", writeElements(document.elements()));
        root.add("undoTree", writeUndoTree(document.undoTree()));
        return root;
    }

    private static JsonArray writeElements(List<SFMDrawCanvasDocument.Element> elements) {
        JsonArray serializedElements = new JsonArray();
        for (SFMDrawCanvasDocument.Element element : elements) {
            serializedElements.add(writeElement(element));
        }
        return serializedElements;
    }

    private static JsonObject writeElement(SFMDrawCanvasDocument.Element element) {
        JsonObject serializedElement = new JsonObject();
        serializedElement.addProperty("type", element.type());
        serializedElement.addProperty("id", element.id());
        serializedElement.addProperty("layer", element.layer());
        serializedElement.addProperty("hidden", element.hidden());
        serializedElement.addProperty("locked", element.locked());
        serializedElement.addProperty("commandSourceElementId", element.commandSourceElementId());

        JsonArray groupIds = new JsonArray();
        for (Integer groupId : element.groupIds()) {
            groupIds.add(groupId);
        }
        serializedElement.add("groupIds", groupIds);

        addNullableDouble(serializedElement, "x1", element.x1());
        addNullableDouble(serializedElement, "y1", element.y1());
        addNullableDouble(serializedElement, "x2", element.x2());
        addNullableDouble(serializedElement, "y2", element.y2());
        addNullableInt(serializedElement, "fillColor", element.fillColor());
        addNullableInt(serializedElement, "strokeColor", element.strokeColor());

        JsonArray points = new JsonArray();
        for (SFMDrawCanvasDocument.Point point : element.points()) {
            JsonObject serializedPoint = new JsonObject();
            serializedPoint.addProperty("x", point.x());
            serializedPoint.addProperty("y", point.y());
            points.add(serializedPoint);
        }
        serializedElement.add("points", points);

        JsonArray hiddenAnchorIndexes = new JsonArray();
        for (Integer hiddenAnchorIndex : element.hiddenAnchorIndexes()) {
            hiddenAnchorIndexes.add(hiddenAnchorIndex);
        }
        serializedElement.add("hiddenAnchorIndexes", hiddenAnchorIndexes);

        addNullableBinding(serializedElement, "startBinding", element.startBinding());
        addNullableBinding(serializedElement, "endBinding", element.endBinding());

        addNullableDouble(serializedElement, "x", element.x());
        addNullableDouble(serializedElement, "y", element.y());
        serializedElement.addProperty("text", element.text());
        addNullableInt(serializedElement, "color", element.color());
        addNullableDouble(serializedElement, "textScale", element.textScale());
        return serializedElement;
    }

    private static JsonArray writeLayerOrigins(List<SFMDrawCanvasDocument.LayerOrigin> layerOrigins) {
        JsonArray array = new JsonArray();
        for (SFMDrawCanvasDocument.LayerOrigin layerOrigin : layerOrigins) {
            JsonObject serializedLayerOrigin = new JsonObject();
            serializedLayerOrigin.addProperty("layer", layerOrigin.layer());
            serializedLayerOrigin.addProperty("x", layerOrigin.x());
            serializedLayerOrigin.addProperty("y", layerOrigin.y());
            array.add(serializedLayerOrigin);
        }
        return array;
    }

    private static JsonObject writeUndoTree(SFMDrawCanvasDocument.UndoTree undoTree) {
        JsonObject serializedUndoTree = new JsonObject();
        serializedUndoTree.addProperty("nextNodeId", undoTree.nextNodeId());
        serializedUndoTree.addProperty("currentNodeId", undoTree.currentNodeId());
        JsonArray nodes = new JsonArray();
        for (SFMDrawCanvasDocument.UndoNode node : undoTree.nodes()) {
            JsonObject serializedNode = new JsonObject();
            serializedNode.addProperty("id", node.id());
            serializedNode.addProperty("parentId", node.parentId());
            serializedNode.addProperty("label", node.label());
            JsonArray childIds = new JsonArray();
            for (Integer childId : node.childIds()) {
                childIds.add(childId);
            }
            serializedNode.add("childIds", childIds);
            serializedNode.add("snapshot", writeSceneSnapshot(node.snapshot()));
            nodes.add(serializedNode);
        }
        serializedUndoTree.add("nodes", nodes);
        return serializedUndoTree;
    }

    private static JsonObject writeSceneSnapshot(SFMDrawCanvasDocument.SceneSnapshot snapshot) {
        JsonObject serializedSnapshot = new JsonObject();
        serializedSnapshot.addProperty("cameraX", snapshot.cameraX());
        serializedSnapshot.addProperty("cameraY", snapshot.cameraY());
        serializedSnapshot.addProperty("zoom", snapshot.zoom());
        serializedSnapshot.addProperty("activeLayer", snapshot.activeLayer());
        serializedSnapshot.addProperty("elementsLayerMuted", snapshot.elementsLayerMuted());
        serializedSnapshot.addProperty("chromeLayerMuted", snapshot.chromeLayerMuted());
        serializedSnapshot.addProperty("historyLayerMuted", snapshot.historyLayerMuted());
        serializedSnapshot.add("layerOrigins", writeLayerOrigins(snapshot.layerOrigins()));
        serializedSnapshot.addProperty("nextElementId", snapshot.nextElementId());
        serializedSnapshot.addProperty("nextGroupId", snapshot.nextGroupId());
        serializedSnapshot.add("elements", writeElements(snapshot.elements()));
        return serializedSnapshot;
    }

    private static List<SFMDrawCanvasDocument.Element> readElements(JsonArray elements) {
        if (elements == null) {
            return List.of();
        }

        List<SFMDrawCanvasDocument.Element> result = new ArrayList<>(elements.size());
        for (JsonElement element : elements) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject serializedElement = element.getAsJsonObject();
            result.add(new SFMDrawCanvasDocument.Element(
                    getString(serializedElement, "type", ""),
                    getInt(serializedElement, "id", 0),
                    getString(serializedElement, "layer", "ELEMENTS"),
                    getBoolean(serializedElement, "hidden", false),
                    getBoolean(serializedElement, "locked", false),
                    getNullableInt(serializedElement, "commandSourceElementId"),
                    readIntList(serializedElement.getAsJsonArray("groupIds")),
                    getNullableDouble(serializedElement, "x1"),
                    getNullableDouble(serializedElement, "y1"),
                    getNullableDouble(serializedElement, "x2"),
                    getNullableDouble(serializedElement, "y2"),
                    getNullableInt(serializedElement, "fillColor"),
                    getNullableInt(serializedElement, "strokeColor"),
                    readPoints(serializedElement.getAsJsonArray("points")),
                    readIntList(serializedElement.getAsJsonArray("hiddenAnchorIndexes")),
                    readEndpointBinding(serializedElement.getAsJsonObject("startBinding")),
                    readEndpointBinding(serializedElement.getAsJsonObject("endBinding")),
                    getNullableDouble(serializedElement, "x"),
                    getNullableDouble(serializedElement, "y"),
                    getString(serializedElement, "text", ""),
                    getNullableInt(serializedElement, "color"),
                    getNullableDouble(serializedElement, "textScale")
            ));
        }
        return result;
    }

    private static List<SFMDrawCanvasDocument.LayerOrigin> readLayerOrigins(JsonArray layerOrigins) {
        if (layerOrigins == null) {
            return List.of();
        }
        List<SFMDrawCanvasDocument.LayerOrigin> result = new ArrayList<>(layerOrigins.size());
        for (JsonElement layerOriginElement : layerOrigins) {
            if (!layerOriginElement.isJsonObject()) {
                continue;
            }
            JsonObject serializedLayerOrigin = layerOriginElement.getAsJsonObject();
            result.add(new SFMDrawCanvasDocument.LayerOrigin(
                    getString(serializedLayerOrigin, "layer", "ELEMENTS"),
                    getDouble(serializedLayerOrigin, "x", 0.0D),
                    getDouble(serializedLayerOrigin, "y", 0.0D)
            ));
        }
        return result;
    }

    private static SFMDrawCanvasDocument.UndoTree readUndoTree(JsonObject undoTree) {
        if (undoTree == null) {
            return null;
        }
        JsonArray nodes = undoTree.getAsJsonArray("nodes");
        List<SFMDrawCanvasDocument.UndoNode> readNodes = new ArrayList<>(nodes == null ? 0 : nodes.size());
        if (nodes != null) {
            for (JsonElement nodeElement : nodes) {
                if (!nodeElement.isJsonObject()) {
                    continue;
                }
                JsonObject serializedNode = nodeElement.getAsJsonObject();
                readNodes.add(new SFMDrawCanvasDocument.UndoNode(
                        getInt(serializedNode, "id", 0),
                        getInt(serializedNode, "parentId", -1),
                        getString(serializedNode, "label", "Action"),
                        readIntList(serializedNode.getAsJsonArray("childIds")),
                        readSceneSnapshot(serializedNode.getAsJsonObject("snapshot"))
                ));
            }
        }
        return new SFMDrawCanvasDocument.UndoTree(
                getInt(undoTree, "nextNodeId", 1),
                getInt(undoTree, "currentNodeId", 0),
                readNodes
        );
    }

    private static SFMDrawCanvasDocument.SceneSnapshot readSceneSnapshot(JsonObject serializedSnapshot) {
        if (serializedSnapshot == null) {
            return null;
        }
        return new SFMDrawCanvasDocument.SceneSnapshot(
                getDouble(serializedSnapshot, "cameraX", 0.0D),
                getDouble(serializedSnapshot, "cameraY", 0.0D),
                getDouble(serializedSnapshot, "zoom", 1.0D),
                getString(serializedSnapshot, "activeLayer", "ELEMENTS"),
                getBoolean(serializedSnapshot, "elementsLayerMuted", false),
                getBoolean(serializedSnapshot, "chromeLayerMuted", false),
                getBoolean(serializedSnapshot, "historyLayerMuted", true),
                readLayerOrigins(serializedSnapshot.getAsJsonArray("layerOrigins")),
                getInt(serializedSnapshot, "nextElementId", 1),
                getInt(serializedSnapshot, "nextGroupId", 1),
                readElements(serializedSnapshot.getAsJsonArray("elements"))
        );
    }

    private static List<SFMDrawCanvasDocument.Point> readPoints(JsonArray points) {
        if (points == null) {
            return List.of();
        }

        List<SFMDrawCanvasDocument.Point> result = new ArrayList<>(points.size());
        for (JsonElement pointElement : points) {
            if (!pointElement.isJsonObject()) {
                continue;
            }
            JsonObject point = pointElement.getAsJsonObject();
            result.add(new SFMDrawCanvasDocument.Point(
                    getDouble(point, "x", 0.0D),
                    getDouble(point, "y", 0.0D)
            ));
        }
        return result;
    }

    private static SFMDrawCanvasDocument.EndpointBinding readEndpointBinding(JsonObject serializedBinding) {
        if (serializedBinding == null) {
            return null;
        }
        return new SFMDrawCanvasDocument.EndpointBinding(
                getInt(serializedBinding, "targetElementId", -1),
                getDouble(serializedBinding, "focusX", 0.5D),
                getDouble(serializedBinding, "focusY", 0.5D)
        );
    }

    private static List<Integer> readIntList(JsonArray values) {
        if (values == null) {
            return List.of();
        }

        List<Integer> result = new ArrayList<>(values.size());
        for (JsonElement value : values) {
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                result.add(value.getAsInt());
            }
        }
        return result;
    }

    private static void addNullableInt(
            JsonObject target,
            String key,
            Integer value
    ) {
        if (value != null) {
            target.addProperty(key, value);
        }
    }

    private static void addNullableBinding(
            JsonObject target,
            String key,
            SFMDrawCanvasDocument.EndpointBinding binding
    ) {
        if (binding == null) {
            return;
        }
        JsonObject serializedBinding = new JsonObject();
        serializedBinding.addProperty("targetElementId", binding.targetElementId());
        serializedBinding.addProperty("focusX", binding.focusX());
        serializedBinding.addProperty("focusY", binding.focusY());
        target.add(key, serializedBinding);
    }

    private static void addNullableDouble(
            JsonObject target,
            String key,
            Double value
    ) {
        if (value != null) {
            target.addProperty(key, value);
        }
    }

    private static String getString(
            JsonObject source,
            String key,
            String fallback
    ) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static boolean getBoolean(
            JsonObject source,
            String key,
            boolean fallback
    ) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    private static int getInt(
            JsonObject source,
            String key,
            int fallback
    ) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    private static Integer getNullableInt(
            JsonObject source,
            String key
    ) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : null;
    }

    private static double getDouble(
            JsonObject source,
            String key,
            double fallback
    ) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : fallback;
    }

    private static Double getNullableDouble(
            JsonObject source,
            String key
    ) {
        JsonElement value = source.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsDouble() : null;
    }
}