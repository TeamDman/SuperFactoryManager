package ca.teamdman.sfm.client.overlay.scene;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Immutable, host-independent state for SFM's non-pausing client overlays. */
public final class SFMOverlaySceneContract {
    public static final String SCHEMA = "sfm.client-scene/1";
    public static final String HISTORY_OVERLAY_ID = "sfm:history";
    public static final String HISTORY_RECIPE_ID = "sfm:episode/history";
    public static final String DOCUMENT_HISTORY_RECIPE_ID = "sfm:document/history";
    public static final int MAX_OVERLAYS = 64;
    public static final int MAX_TEXT_BYTES = 16 * 1024;
    public static final int MAX_SCENE_JSON_BYTES = 1024 * 1024;
    public static final int DEFAULT_CONTENT_WIDTH = 320;
    public static final int DEFAULT_CONTENT_HEIGHT = 190;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private SFMOverlaySceneContract() {
    }

    public enum InputMode {
        PASSIVE,
        INTERACTIVE;

        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static InputMode parse(String value) {
            return valueOf(requireText(value, "input mode").toUpperCase(Locale.ROOT));
        }
    }

    public enum ReferenceFrame {
        GUI_SAFE_VIEWPORT;

        public String wire() {
            return "gui-safe";
        }

        public static ReferenceFrame parse(String value) {
            if ("gui-safe".equals(value)) return GUI_SAFE_VIEWPORT;
            throw new IllegalArgumentException("Unsupported overlay reference frame: " + value);
        }
    }

    public enum ClipPolicy {
        CLAMP_TO_SAFE_VIEWPORT,
        CLIP_TO_VIEWPORT;

        public String wire() {
            return this == CLAMP_TO_SAFE_VIEWPORT ? "clamp" : "clip";
        }

        public static ClipPolicy parse(String value) {
            return switch (requireText(value, "clip policy")) {
                case "clamp" -> CLAMP_TO_SAFE_VIEWPORT;
                case "clip" -> CLIP_TO_VIEWPORT;
                default -> throw new IllegalArgumentException("Unsupported overlay clip policy: " + value);
            };
        }
    }

    public record OverlayInstanceId(String value) implements Comparable<OverlayInstanceId> {
        public OverlayInstanceId {
            value = requireResourceId(value, "overlay instance id");
        }

        @Override
        public int compareTo(OverlayInstanceId other) {
            return value.compareTo(other.value);
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public record ContentRecipe(String contentId, String argument) {
        public ContentRecipe {
            contentId = requireResourceId(contentId, "overlay content id");
            argument = requireBoundedText(Objects.requireNonNull(argument, "argument"), "overlay argument");
        }
    }

    public record SizeConstraints(
            int minimumWidth,
            int minimumHeight,
            int preferredWidth,
            int preferredHeight,
            int maximumWidth,
            int maximumHeight
    ) {
        public SizeConstraints {
            requireDimension(minimumWidth, "minimum width");
            requireDimension(minimumHeight, "minimum height");
            requireDimension(preferredWidth, "preferred width");
            requireDimension(preferredHeight, "preferred height");
            requireDimension(maximumWidth, "maximum width");
            requireDimension(maximumHeight, "maximum height");
            if (minimumWidth > preferredWidth || preferredWidth > maximumWidth
                    || minimumHeight > preferredHeight || preferredHeight > maximumHeight) {
                throw new IllegalArgumentException("Overlay size constraints must satisfy minimum <= preferred <= maximum");
            }
        }

        public int width(int fallback, int viewportWidth) {
            return clamp(preferredWidth > 0 ? preferredWidth : fallback, minimumWidth,
                    Math.min(maximumWidth, Math.max(1, viewportWidth)));
        }

        public int height(int fallback, int viewportHeight) {
            return clamp(preferredHeight > 0 ? preferredHeight : fallback, minimumHeight,
                    Math.min(maximumHeight, Math.max(1, viewportHeight)));
        }
    }

    public record Placement(
            ReferenceFrame referenceFrame,
            double referenceAnchorU,
            double referenceAnchorV,
            double contentAnchorU,
            double contentAnchorV,
            int logicalOffsetX,
            int logicalOffsetY,
            Optional<SizeConstraints> sizeConstraints,
            ClipPolicy clipPolicy
    ) {
        public Placement {
            Objects.requireNonNull(referenceFrame, "referenceFrame");
            requireAnchor(referenceAnchorU, "reference anchor u");
            requireAnchor(referenceAnchorV, "reference anchor v");
            requireAnchor(contentAnchorU, "content anchor u");
            requireAnchor(contentAnchorV, "content anchor v");
            if (Math.abs((long) logicalOffsetX) > 1_000_000L
                    || Math.abs((long) logicalOffsetY) > 1_000_000L) {
                throw new IllegalArgumentException("Overlay logical offset is outside the bounded range");
            }
            sizeConstraints = Objects.requireNonNull(sizeConstraints, "sizeConstraints");
            Objects.requireNonNull(clipPolicy, "clipPolicy");
        }

        public static Placement topRight(int width, int height) {
            return new Placement(
                    ReferenceFrame.GUI_SAFE_VIEWPORT,
                    1.0D,
                    0.0D,
                    1.0D,
                    0.0D,
                    -8,
                    8,
                    Optional.of(new SizeConstraints(96, 72, width, height, 4096, 4096)),
                    ClipPolicy.CLAMP_TO_SAFE_VIEWPORT
            );
        }

        public Bounds resolve(int viewportWidth, int viewportHeight, int defaultWidth, int defaultHeight) {
            return resolve(new Viewport(0, 0, viewportWidth, viewportHeight), defaultWidth, defaultHeight);
        }

        public Bounds resolve(Viewport viewport, int defaultWidth, int defaultHeight) {
            Objects.requireNonNull(viewport, "viewport");
            int viewportWidth = viewport.width();
            int viewportHeight = viewport.height();
            if (viewportWidth <= 0 || viewportHeight <= 0) {
                return new Bounds(viewport.x(), viewport.y(), 0, 0);
            }
            int width = sizeConstraints
                    .map(value -> value.width(defaultWidth, viewportWidth))
                    .orElse(Math.min(Math.max(1, defaultWidth), viewportWidth));
            int height = sizeConstraints
                    .map(value -> value.height(defaultHeight, viewportHeight))
                    .orElse(Math.min(Math.max(1, defaultHeight), viewportHeight));
            int referenceX = viewport.x() + (int) Math.round(referenceAnchorU * viewportWidth);
            int referenceY = viewport.y() + (int) Math.round(referenceAnchorV * viewportHeight);
            int x = referenceX + logicalOffsetX - (int) Math.round(contentAnchorU * width);
            int y = referenceY + logicalOffsetY - (int) Math.round(contentAnchorV * height);
            if (clipPolicy == ClipPolicy.CLAMP_TO_SAFE_VIEWPORT) {
                int margin = Math.min(4, Math.min(viewportWidth, viewportHeight) / 2);
                int minimumX = viewport.x() + margin;
                int minimumY = viewport.y() + margin;
                int maximumX = Math.max(minimumX, viewport.x() + viewportWidth - margin - width);
                int maximumY = Math.max(minimumY, viewport.y() + viewportHeight - margin - height);
                x = clamp(x, minimumX, maximumX);
                y = clamp(y, minimumY, maximumY);
            }
            return new Bounds(x, y, width, height);
        }

        /** Compact canonical command argument; no shell whitespace is required. */
        public String canonical() {
            String size = sizeConstraints
                    .map(value -> value.minimumWidth() + ","
                            + value.minimumHeight() + ","
                            + value.preferredWidth() + ","
                            + value.preferredHeight() + ","
                            + value.maximumWidth() + ","
                            + value.maximumHeight())
                    .orElse("auto,auto,auto,auto,auto,auto");
            return referenceFrame.wire() + "("
                    + decimal(referenceAnchorU) + ","
                    + decimal(referenceAnchorV) + ","
                    + decimal(contentAnchorU) + ","
                    + decimal(contentAnchorV) + ","
                    + logicalOffsetX + ","
                    + logicalOffsetY + ","
                    + size + ","
                    + clipPolicy.wire() + ")";
        }

        public static Placement parseCanonical(String text) {
            text = requireText(text, "placement");
            int open = text.indexOf('(');
            if (open <= 0 || !text.endsWith(")")) {
                throw new IllegalArgumentException(
                        "Placement must use frame(u,v,cu,cv,dx,dy,minw,minh,prefw,prefh,maxw,maxh,policy)"
                );
            }
            ReferenceFrame frame = ReferenceFrame.parse(text.substring(0, open));
            String[] parts = text.substring(open + 1, text.length() - 1).split(",", -1);
            if (parts.length != 13) throw new IllegalArgumentException("Placement requires exactly thirteen arguments");
            double referenceU = exactDouble(parts[0], "reference u");
            double referenceV = exactDouble(parts[1], "reference v");
            double contentU = exactDouble(parts[2], "content u");
            double contentV = exactDouble(parts[3], "content v");
            int offsetX = exactInt(parts[4], "offset x");
            int offsetY = exactInt(parts[5], "offset y");
            Optional<SizeConstraints> size;
            boolean allAuto = java.util.Arrays.stream(parts, 6, 12).allMatch("auto"::equals);
            boolean anyAuto = java.util.Arrays.stream(parts, 6, 12).anyMatch("auto"::equals);
            if (allAuto) {
                size = Optional.empty();
            } else {
                if (anyAuto) throw new IllegalArgumentException("Placement size constraints must be all auto or all numeric");
                size = Optional.of(new SizeConstraints(
                        exactInt(parts[6], "minimum width"),
                        exactInt(parts[7], "minimum height"),
                        exactInt(parts[8], "preferred width"),
                        exactInt(parts[9], "preferred height"),
                        exactInt(parts[10], "maximum width"),
                        exactInt(parts[11], "maximum height")
                ));
            }
            Placement result = new Placement(
                    frame,
                    referenceU,
                    referenceV,
                    contentU,
                    contentV,
                    offsetX,
                    offsetY,
                    size,
                    ClipPolicy.parse(parts[12])
            );
            if (!result.canonical().equals(text)) {
                throw new IllegalArgumentException("Placement must use canonical spelling: " + result.canonical());
            }
            return result;
        }
    }

    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Overlay bounds cannot be negative");
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /** Safe GUI viewport in logical coordinates, including non-zero insets/origin. */
    public record Viewport(int x, int y, int width, int height) {
        public Viewport {
            if (width < 0 || height < 0) throw new IllegalArgumentException("Viewport dimensions cannot be negative");
        }
    }

    public record OverlayState(
            OverlayInstanceId id,
            ContentRecipe recipe,
            boolean visible,
            Placement placement,
            InputMode inputMode,
            int zOrder,
            Map<String, String> persistedState
    ) {
        public OverlayState {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(recipe, "recipe");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(inputMode, "inputMode");
            if (Math.abs((long) zOrder) > 1_000_000L) {
                throw new IllegalArgumentException("Overlay z-order is outside the bounded range");
            }
            TreeMap<String, String> canonical = new TreeMap<>();
            Objects.requireNonNull(persistedState, "persistedState").forEach((key, value) ->
                    canonical.put(requireBoundedText(requireText(key, "persisted-state key"), "persisted-state key"),
                            requireBoundedText(Objects.requireNonNull(value, "persisted-state value"),
                                    "persisted-state value")));
            persistedState = Map.copyOf(canonical);
        }

        public OverlayState withVisible(boolean next) {
            return new OverlayState(id, recipe, next, placement, inputMode, zOrder, persistedState);
        }

        public OverlayState withPlacement(Placement next) {
            return new OverlayState(id, recipe, visible, next, inputMode, zOrder, persistedState);
        }

        public OverlayState withInputMode(InputMode next) {
            return new OverlayState(id, recipe, visible, placement, next, zOrder, persistedState);
        }

        public OverlayState withZOrder(int next) {
            return new OverlayState(id, recipe, visible, placement, inputMode, next, persistedState);
        }
    }

    public record SceneState(
            String schema,
            long revision,
            List<OverlayState> overlays,
            Optional<OverlayInstanceId> focusedOverlay
    ) {
        public SceneState {
            if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("Unsupported client-scene schema: " + schema);
            if (revision < 0) throw new IllegalArgumentException("Scene revision must not be negative");
            Objects.requireNonNull(overlays, "overlays");
            if (overlays.size() > MAX_OVERLAYS) throw new IllegalArgumentException("Too many overlay instances");
            ArrayList<OverlayState> canonical = new ArrayList<>(overlays);
            canonical.sort(Comparator.comparing(OverlayState::id));
            for (int index = 1; index < canonical.size(); index++) {
                if (canonical.get(index - 1).id().equals(canonical.get(index).id())) {
                    throw new IllegalArgumentException("Duplicate overlay instance id " + canonical.get(index).id());
                }
            }
            List<OverlayState> canonicalOverlays = List.copyOf(canonical);
            overlays = canonicalOverlays;
            focusedOverlay = Objects.requireNonNull(focusedOverlay, "focusedOverlay");
            focusedOverlay.ifPresent(id -> {
                OverlayState focused = canonicalOverlays.stream()
                        .filter(overlay -> overlay.id().equals(id))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Focused overlay does not exist: " + id));
                if (!focused.visible() || focused.inputMode() != InputMode.INTERACTIVE) {
                    throw new IllegalArgumentException("Focused overlay must be visible and interactive: " + id);
                }
            });
        }

        public static SceneState defaults() {
            return new SceneState(
                    SCHEMA,
                    0,
                    List.of(new OverlayState(
                            new OverlayInstanceId(HISTORY_OVERLAY_ID),
                            new ContentRecipe(HISTORY_RECIPE_ID, "focused"),
                            false,
                            Placement.topRight(DEFAULT_CONTENT_WIDTH, DEFAULT_CONTENT_HEIGHT),
                            InputMode.PASSIVE,
                            100,
                            Map.of()
                    )),
                    Optional.empty()
            );
        }

        public Optional<OverlayState> overlay(OverlayInstanceId id) {
            return overlays.stream().filter(overlay -> overlay.id().equals(id)).findFirst();
        }

        public SceneState replace(OverlayState replacement, Optional<OverlayInstanceId> nextFocus) {
            Objects.requireNonNull(replacement, "replacement");
            ArrayList<OverlayState> next = new ArrayList<>(overlays);
            int index = -1;
            for (int candidate = 0; candidate < next.size(); candidate++) {
                if (next.get(candidate).id().equals(replacement.id())) {
                    index = candidate;
                    break;
                }
            }
            if (index < 0) throw new IllegalArgumentException("Unknown overlay instance " + replacement.id());
            next.set(index, replacement);
            return new SceneState(schema, revision + 1, next, nextFocus);
        }

        public SceneState withFocus(Optional<OverlayInstanceId> nextFocus) {
            return new SceneState(schema, revision + 1, overlays, nextFocus);
        }
    }

    private static String decimal(double value) {
        if (value == 0D) return "0";
        if (value == 1D) return "1";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static double exactDouble(String value, String label) {
        try {
            double result = Double.parseDouble(value);
            if (!Double.isFinite(result)) throw new NumberFormatException("non-finite");
            return result;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value, failure);
        }
    }

    private static int exactInt(String value, String label) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid " + label + ": " + value, failure);
        }
    }

    private static void requireAnchor(double value, String label) {
        if (!Double.isFinite(value) || value < 0D || value > 1D) {
            throw new IllegalArgumentException(label + " must be finite and within [0,1]");
        }
    }

    private static void requireDimension(int value, String label) {
        if (value < 1 || value > 4096) throw new IllegalArgumentException(label + " must be within 1..4096");
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (maximum < minimum) return maximum;
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static String requireResourceId(String value, String label) {
        value = requireText(value, label);
        if (!RESOURCE_ID.matcher(value).matches()) throw new IllegalArgumentException("Invalid " + label + ": " + value);
        return value;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return requireBoundedText(value, label);
    }

    private static String requireBoundedText(String value, String label) {
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException(label + " contains NUL");
        if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
            throw new IllegalArgumentException(label + " exceeds the bounded UTF-8 length");
        }
        return value;
    }
}
