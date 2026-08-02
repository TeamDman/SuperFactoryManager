package ca.teamdman.sfm.client.screen.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable workspace state backed by a normalized n-ary tree of linear splits.
 * Panel ids survive insertion, removal, normalization, and viewport changes.
 */
public final class SFMWorkspaceLayout {
    public static final int DEFAULT_MINIMUM_PIXELS = 1;

    private Node root;
    private long nextPanelId;
    private SFMWorkspacePanelId focusedPanel;
    private final IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelId> persistentPanelIds;
    private final IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelMetadata> persistentPanelMetadata;

    private SFMWorkspaceLayout(Node root, long nextPanelId, SFMWorkspacePanelId focusedPanel) {
        this.root = root;
        this.nextPanelId = nextPanelId;
        this.focusedPanel = focusedPanel;
        this.persistentPanelIds = new IdentityHashMap<>();
        this.persistentPanelMetadata = new IdentityHashMap<>();
        List<PanelEntry> entries = new ArrayList<>();
        collectPanels(root, entries);
        entries.forEach(entry -> {
            persistentPanelIds.put(entry.panel(), entry.id());
            persistentPanelMetadata.put(entry.panel(), entry.metadata());
        });
    }

    public static SFMWorkspaceLayout single(SFMScreenPanel panel) {
        Objects.requireNonNull(panel);
        SFMWorkspacePanelId id = new SFMWorkspacePanelId(0);
        return new SFMWorkspaceLayout(new PanelNode(id, panel), 1, id);
    }

    public static SFMWorkspaceLayout sideBySide(SFMScreenPanel left, SFMScreenPanel right) {
        Objects.requireNonNull(left);
        Objects.requireNonNull(right);
        SFMWorkspacePanelId leftId = new SFMWorkspacePanelId(0);
        SFMWorkspacePanelId rightId = new SFMWorkspacePanelId(1);
        return new SFMWorkspaceLayout(
                new LinearNode(SFMWorkspaceAxis.HORIZONTAL, List.of(
                        new Track(new PanelNode(leftId, left), 1.0, DEFAULT_MINIMUM_PIXELS),
                        new Track(new PanelNode(rightId, right), 1.0, DEFAULT_MINIMUM_PIXELS)
                )),
                2,
                rightId
        );
    }

    /** Builds one validated subtree before exposing any of its panels to a host. */
    public static SFMWorkspaceLayout group(LayoutSpec spec) {
        Objects.requireNonNull(spec);
        validateUniquePanels(spec);
        IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelId> ids = new IdentityHashMap<>();
        long[] nextId = {0};
        IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelMetadata> metadata = new IdentityHashMap<>();
        Node root = materialize(spec, ids, metadata, nextId);
        List<PanelEntry> panels = new ArrayList<>();
        collectPanels(root, panels);
        if (panels.isEmpty()) throw new IllegalArgumentException("Panel group must contain a panel");
        SFMWorkspaceLayout layout = new SFMWorkspaceLayout(root, nextId[0], panels.get(0).id());
        layout.persistentPanelIds.putAll(ids);
        layout.persistentPanelMetadata.putAll(metadata);
        return layout;
    }

    /**
     * Atomically replaces the shape while retaining ids for the same panel instances.
     * This is used for responsive transitions; panel-local/model state is never rebuilt.
     */
    public void recompose(LayoutSpec spec) {
        Objects.requireNonNull(spec);
        validateUniquePanels(spec);
        long[] candidateNextId = {nextPanelId};
        Node candidate = materialize(spec, persistentPanelIds, persistentPanelMetadata, candidateNextId);
        List<PanelEntry> candidatePanels = new ArrayList<>();
        collectPanels(candidate, candidatePanels);
        if (candidatePanels.isEmpty()) throw new IllegalArgumentException("Panel group must contain a panel");
        root = candidate;
        nextPanelId = candidateNextId[0];
        if (focusedPanel == null || !isVisible(root, focusedPanel)) {
            focusedPanel = firstVisible(root).id();
        }
    }

    public SFMWorkspacePanelId focusedPanel() {
        return focusedPanel;
    }

    public boolean focus(SFMWorkspacePanelId panelId) {
        Activation activated = activate(root, panelId);
        if (!activated.found()) return false;
        root = activated.node();
        focusedPanel = panelId;
        return true;
    }

    public SFMWorkspacePanelId insert(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side,
            SFMScreenPanel panel
    ) {
        return insert(source, side, panel, SFMWorkspacePanelMetadata.ordinary());
    }

    public SFMWorkspacePanelId insert(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(side);
        Objects.requireNonNull(panel);
        Objects.requireNonNull(metadata);
        if (find(source) == null) throw new IllegalArgumentException("Unknown source panel: " + source);
        if (persistentPanelIds.containsKey(panel)) throw new IllegalArgumentException("Panel instance is already attached");
        SFMWorkspacePanelId inserted = new SFMWorkspacePanelId(nextPanelId++);
        persistentPanelIds.put(panel, inserted);
        persistentPanelMetadata.put(panel, metadata);
        root = normalize(insert(root, source, side, new PanelNode(inserted, panel, metadata)));
        focusedPanel = inserted;
        return inserted;
    }

    /** Pushes a new entry into the focused slot and makes it the visible entry. */
    public SFMWorkspacePanelId pushToFocusedStack(
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        return pushToStack(focusedPanel, panel, metadata);
    }

    /** Pushes a new entry into the slot containing {@code source}, even when that slot is not focused. */
    public SFMWorkspacePanelId pushToStack(
            SFMWorkspacePanelId source,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        Objects.requireNonNull(panel);
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(source);
        if (find(root, source) == null) throw new IllegalArgumentException("Unknown source panel: " + source);
        if (persistentPanelIds.containsKey(panel)) throw new IllegalArgumentException("Panel instance is already attached");
        SFMWorkspacePanelId inserted = new SFMWorkspacePanelId(nextPanelId++);
        persistentPanelIds.put(panel, inserted);
        persistentPanelMetadata.put(panel, metadata);
        root = pushIntoNearestStack(root, source, new PanelNode(inserted, panel, metadata));
        focusedPanel = inserted;
        return inserted;
    }

    /** Moves the visible entry into the adjacent slot without cloning its identity or content. */
    public boolean move(SFMWorkspacePanelId panelId, SFMWorkspaceSide side) {
        Objects.requireNonNull(panelId);
        Objects.requireNonNull(side);
        PanelNode moved = find(root, panelId);
        if (moved == null) return false;
        if (!visiblePanels().stream().anyMatch(entry -> entry.id().equals(panelId))) return false;
        List<PanelEntry> sourceSlot = slotEntries(panelId);
        SFMWorkspacePanelId destination = directionalNeighbor(panelId, side);
        if (destination == null && sourceSlot.size() <= 1) return false;
        root = normalize(remove(root, panelId));
        if (destination != null) {
            root = pushIntoNearestStack(root, destination, moved);
        } else {
            // An edge move is still useful when the source slot has a stack: detach the
            // visible entry into a newly-created neighboring slot rather than silently
            // treating the action as a no-op. A single-entry edge slot has nowhere to go.
            SFMWorkspacePanelId anchor = sourceSlot.stream()
                    .map(PanelEntry::id)
                    .filter(id -> find(root, id) != null)
                    .findFirst()
                    .orElse(null);
            if (anchor == null) return false;
            root = normalize(insert(root, anchor, side, moved));
        }
        focusedPanel = panelId;
        return true;
    }

    /** Traverses visible slots and then entries within a stacked slot. */
    public boolean traverse(int direction) {
        if (direction == 0) return false;
        List<PanelEntry> stack = focusedSlotEntries();
        if (stack.size() > 1) return rotateFocusedSlot(direction);
        List<PanelEntry> visible = visiblePanels();
        int index = indexOf(visible, focusedPanel);
        if (index < 0 || visible.isEmpty()) return false;
        int next = Math.floorMod(index + direction, visible.size());
        return focus(visible.get(next).id());
    }

    public boolean rotateVisibleContent(int direction) {
        if (direction == 0) return false;
        List<PanelEntry> visible = visiblePanels();
        if (visible.size() < 2) return false;
        List<PanelEntry> ordered = new ArrayList<>(visible);
        if (direction < 0) Collections.reverse(ordered);
        for (int index = 0; index < ordered.size() - 1; index++) {
            PanelEntry current = ordered.get(index);
            PanelEntry next = ordered.get(index + 1);
            PanelNode currentNode = find(root, current.id());
            PanelNode nextNode = find(root, next.id());
            root = replacePayload(root, current.id(), nextNode.panel(), currentNode.metadata());
            root = replacePayload(root, next.id(), currentNode.panel(), nextNode.metadata());
        }
        rebuildPersistentIndexes();
        return true;
    }

    public boolean rotateVisibleScale(int direction) {
        if (direction == 0) return false;
        List<PanelEntry> visible = visiblePanels();
        if (visible.size() < 2) return false;
        List<SFMWorkspacePanelMetadata> metadata = visible.stream().map(PanelEntry::metadata).toList();
        for (int index = 0; index < visible.size(); index++) {
            int source = direction > 0
                    ? Math.floorMod(index - 1, visible.size())
                    : Math.floorMod(index + 1, visible.size());
            updateMetadata(visible.get(index).id(), metadata.get(source));
        }
        return true;
    }

    public boolean setFocusedGuiScale(@Nullable Integer scale) {
        PanelNode focused = find(root, focusedPanel);
        if (focused == null) return false;
        SFMWorkspacePanelMetadata metadata = scale == null
                ? focused.metadata().clearGuiScaleOverride()
                : focused.metadata().withGuiScaleOverride(scale);
        return updateMetadata(focusedPanel, metadata);
    }

    /** Sets the allocation constraint on the track directly containing this panel. */
    public boolean configurePanel(SFMWorkspacePanelId panelId, double share, int minimumPixels) {
        if (!(share > 0.0) || !Double.isFinite(share)) {
            throw new IllegalArgumentException("Track share must be finite and positive");
        }
        if (minimumPixels < 0) throw new IllegalArgumentException("Track minimum must be non-negative");
        Configuration configured = configure(root, panelId, share, minimumPixels);
        root = configured.node();
        return configured.changed();
    }

    public boolean remove(SFMWorkspacePanelId panelId) {
        Objects.requireNonNull(panelId);
        List<PanelEntry> before = panels();
        List<PanelEntry> visibleBefore = visiblePanels();
        int removedIndex = indexOf(before, panelId);
        int removedVisibleIndex = indexOf(visibleBefore, panelId);
        if (removedIndex < 0) return false;
        SFMScreenPanel removedPanel = before.get(removedIndex).panel();
        root = normalize(remove(root, panelId));
        persistentPanelIds.remove(removedPanel);
        persistentPanelMetadata.remove(removedPanel);
        List<PanelEntry> after = panels();
        if (after.isEmpty()) {
            focusedPanel = null;
        } else if (panelId.equals(focusedPanel)) {
            List<PanelEntry> visibleAfter = visiblePanels();
            int targetIndex = removedVisibleIndex < 0 ? 0 : removedVisibleIndex;
            focusedPanel = visibleAfter.get(Math.min(targetIndex, visibleAfter.size() - 1)).id();
        }
        return true;
    }

    public List<PanelEntry> panels() {
        List<PanelEntry> answer = new ArrayList<>();
        collectPanels(root, answer);
        return List.copyOf(answer);
    }

    /** Every panel identity seen by this group, including leaves hidden by a temporary maximize shape. */
    public List<PanelEntry> allPanels() {
        return persistentPanelIds.entrySet().stream()
                .map(entry -> new PanelEntry(
                        entry.getValue(),
                        entry.getKey(),
                        persistentPanelMetadata.getOrDefault(entry.getKey(), SFMWorkspacePanelMetadata.ordinary())))
                .sorted(java.util.Comparator.comparingLong(entry -> entry.id().value()))
                .toList();
    }

    public SFMScreenPanel panel(SFMWorkspacePanelId panelId) {
        PanelNode found = find(panelId);
        return found == null ? null : found.panel();
    }

    public @Nullable PanelEntry entry(SFMWorkspacePanelId panelId) {
        PanelNode found = find(panelId);
        return found == null ? null : new PanelEntry(found.id(), found.panel(), found.metadata());
    }

    public SFMWorkspacePanelMetadata metadata(SFMWorkspacePanelId panelId) {
        PanelNode found = find(root, panelId);
        return found == null ? null : found.metadata();
    }

    public boolean updateMetadata(SFMWorkspacePanelId panelId, SFMWorkspacePanelMetadata metadata) {
        Objects.requireNonNull(metadata);
        PanelNode found = find(root, panelId);
        if (found == null) return false;
        persistentPanelMetadata.put(found.panel(), metadata);
        root = updateMetadata(root, panelId, metadata);
        return true;
    }

    /** Visible entries in layout order; hidden stack entries are intentionally omitted. */
    public List<PanelEntry> visiblePanels() {
        List<PanelEntry> answer = new ArrayList<>();
        collectVisiblePanels(root, answer);
        return List.copyOf(answer);
    }

    /** Entries that share the focused panel's slot, in stack order. */
    public List<PanelEntry> focusedSlotEntries() {
        return slotEntries(focusedPanel);
    }

    /** Entries that share the slot containing the supplied visible or hidden entry. */
    public List<PanelEntry> slotEntries(SFMWorkspacePanelId panelId) {
        List<PanelEntry> answer = new ArrayList<>();
        collectFocusedSlot(root, panelId, answer);
        return List.copyOf(answer);
    }

    public boolean rotateFocusedSlot(int direction) {
        if (direction == 0) return false;
        Rotation rotation = rotateSlot(root, focusedPanel, direction);
        if (!rotation.changed()) return false;
        root = rotation.node();
        focusedPanel = rotation.focused();
        return true;
    }

    public Map<SFMWorkspacePanelId, SFMScreenPanelBounds> bounds(
            SFMScreenPanelBounds viewport,
            int dividerPixels
    ) {
        if (dividerPixels < 0) throw new IllegalArgumentException("Divider width must be non-negative");
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> answer = new LinkedHashMap<>();
        allocate(root, viewport, dividerPixels, answer);
        return Collections.unmodifiableMap(answer);
    }

    public static LayoutSpec panel(SFMScreenPanel panel) {
        return new PanelSpec(panel);
    }

    public static LayoutSpec horizontal(LayoutSpec... children) {
        return new LinearSpec(SFMWorkspaceAxis.HORIZONTAL, List.of(children));
    }

    public static LayoutSpec vertical(LayoutSpec... children) {
        return new LinearSpec(SFMWorkspaceAxis.VERTICAL, List.of(children));
    }

    public static LayoutSpec stack(int active, LayoutSpec... children) {
        return new StackSpec(List.of(children), active);
    }

    private static Node insert(Node node, SFMWorkspacePanelId source, SFMWorkspaceSide side, PanelNode inserted) {
        if (node == null || !contains(node, source)) return node;
        if (node instanceof PanelNode panel) return wrapAdjacent(panel, side, inserted);
        if (node instanceof StackNode) return wrapAdjacent(node, side, inserted);

        LinearNode linear = (LinearNode) node;
        List<Track> children = new ArrayList<>(linear.children());
        for (int index = 0; index < children.size(); index++) {
            Track child = children.get(index);
            if (!contains(child.node(), source)) continue;
            if (linear.axis() == side.axis() && child.node() instanceof StackNode) {
                Track insertedTrack = new Track(inserted, 1.0, DEFAULT_MINIMUM_PIXELS);
                if (side.before()) children.add(index, insertedTrack);
                else children.add(index + 1, insertedTrack);
                return new LinearNode(linear.axis(), children);
            }
            children.set(index, child.withNode(insert(child.node(), source, side, inserted)));
            return new LinearNode(linear.axis(), children);
        }
        return node;
    }

    private static Node wrapAdjacent(Node existing, SFMWorkspaceSide side, PanelNode inserted) {
        Track oldTrack = new Track(existing, 1.0, DEFAULT_MINIMUM_PIXELS);
        Track newTrack = new Track(inserted, 1.0, DEFAULT_MINIMUM_PIXELS);
        return new LinearNode(
                side.axis(),
                side.before() ? List.of(newTrack, oldTrack) : List.of(oldTrack, newTrack)
        );
    }

    private static Node remove(Node node, SFMWorkspacePanelId panelId) {
        if (node instanceof PanelNode panel) return panel.id().equals(panelId) ? null : panel;
        if (node instanceof StackNode stack) {
            List<Node> children = new ArrayList<>();
            for (Node child : stack.children()) {
                Node remaining = remove(child, panelId);
                if (remaining != null) children.add(remaining);
            }
            if (children.isEmpty()) return null;
            if (children.size() == 1) return children.get(0);
            return new StackNode(children, Math.min(stack.active(), children.size() - 1));
        }
        LinearNode linear = (LinearNode) node;
        List<Track> children = new ArrayList<>();
        for (Track child : linear.children()) {
            Node remaining = remove(child.node(), panelId);
            if (remaining != null) children.add(child.withNode(remaining));
        }
        if (children.isEmpty()) return null;
        if (children.size() == 1) return children.get(0).node();
        return new LinearNode(linear.axis(), children);
    }

    private static Configuration configure(
            Node node,
            SFMWorkspacePanelId panelId,
            double share,
            int minimumPixels
    ) {
        if (node instanceof StackNode stack) {
            List<Node> children = new ArrayList<>(stack.children());
            for (int i = 0; i < children.size(); i++) {
                Configuration nested = configure(children.get(i), panelId, share, minimumPixels);
                if (nested.changed()) {
                    children.set(i, nested.node());
                    return new Configuration(new StackNode(children, stack.active()), true);
                }
            }
            return new Configuration(node, false);
        }
        if (!(node instanceof LinearNode linear)) return new Configuration(node, false);
        List<Track> children = new ArrayList<>(linear.children());
        for (int i = 0; i < children.size(); i++) {
            Track child = children.get(i);
            if (child.node() instanceof PanelNode panel && panel.id().equals(panelId)) {
                children.set(i, new Track(panel, share, minimumPixels));
                return new Configuration(new LinearNode(linear.axis(), children), true);
            }
            Configuration nested = configure(child.node(), panelId, share, minimumPixels);
            if (nested.changed()) {
                children.set(i, child.withNode(nested.node()));
                return new Configuration(new LinearNode(linear.axis(), children), true);
            }
        }
        return new Configuration(node, false);
    }

    private static Node normalize(Node node) {
        if (node == null) return null;
        if (node instanceof StackNode stack) {
            return new StackNode(stack.children().stream().map(SFMWorkspaceLayout::normalize).toList(), stack.active());
        }
        if (!(node instanceof LinearNode linear)) return node;
        List<Track> normalized = new ArrayList<>();
        for (Track outer : linear.children()) {
            Node child = normalize(outer.node());
            if (child instanceof LinearNode inner && inner.axis() == linear.axis()) {
                double innerShares = inner.children().stream().mapToDouble(Track::share).sum();
                for (Track grandchild : inner.children()) {
                    double share = outer.share() * grandchild.share() / innerShares;
                    int inheritedMinimum = (int) Math.ceil(outer.minimumPixels() * grandchild.share() / innerShares);
                    normalized.add(new Track(
                            grandchild.node(),
                            share,
                            Math.max(grandchild.minimumPixels(), inheritedMinimum)
                    ));
                }
            } else {
                normalized.add(outer.withNode(child));
            }
        }
        return normalized.size() == 1 ? normalized.get(0).node() : new LinearNode(linear.axis(), normalized);
    }

    private static void allocate(
            Node node,
            SFMScreenPanelBounds bounds,
            int dividerPixels,
            Map<SFMWorkspacePanelId, SFMScreenPanelBounds> answer
    ) {
        if (node == null) return;
        if (node instanceof PanelNode panel) {
            answer.put(panel.id(), bounds);
            return;
        }
        if (node instanceof StackNode stack) {
            allocate(stack.children().get(stack.active()), bounds, dividerPixels, answer);
            return;
        }
        LinearNode linear = (LinearNode) node;
        int totalLength = linear.axis() == SFMWorkspaceAxis.HORIZONTAL ? bounds.width() : bounds.height();
        int available = Math.max(0, totalLength - dividerPixels * (linear.children().size() - 1));
        int[] lengths = allocateTracks(linear.children(), available);
        int cursor = linear.axis() == SFMWorkspaceAxis.HORIZONTAL ? bounds.x() : bounds.y();
        for (int i = 0; i < linear.children().size(); i++) {
            int length = lengths[i];
            SFMScreenPanelBounds childBounds = linear.axis() == SFMWorkspaceAxis.HORIZONTAL
                    ? new SFMScreenPanelBounds(cursor, bounds.y(), length, bounds.height())
                    : new SFMScreenPanelBounds(bounds.x(), cursor, bounds.width(), length);
            allocate(linear.children().get(i).node(), childBounds, dividerPixels, answer);
            cursor += length + dividerPixels;
        }
    }

    private static int[] allocateTracks(List<Track> tracks, int available) {
        int[] answer = new int[tracks.size()];
        int minimumTotal = tracks.stream().mapToInt(Track::minimumPixels).sum();
        int assigned = 0;
        if (minimumTotal <= available) {
            for (int i = 0; i < tracks.size(); i++) {
                answer[i] = tracks.get(i).minimumPixels();
                assigned += answer[i];
            }
        } else if (available >= tracks.size()) {
            for (int i = 0; i < tracks.size(); i++) answer[i] = 1;
            assigned = tracks.size();
        }
        double shares = tracks.stream().mapToDouble(Track::share).sum();
        int remaining = available - assigned;
        int distributed = 0;
        for (int i = 0; i < tracks.size(); i++) {
            int extra = i == tracks.size() - 1
                    ? remaining - distributed
                    : (int) Math.floor(remaining * tracks.get(i).share() / shares);
            answer[i] += extra;
            distributed += extra;
        }
        return answer;
    }

    private PanelNode find(SFMWorkspacePanelId panelId) {
        return find(root, panelId);
    }

    private static PanelNode find(Node node, SFMWorkspacePanelId panelId) {
        if (node == null) return null;
        if (node instanceof PanelNode panel) return panel.id().equals(panelId) ? panel : null;
        if (node instanceof StackNode stack) {
            for (Node child : stack.children()) {
                PanelNode found = find(child, panelId);
                if (found != null) return found;
            }
            return null;
        }
        for (Track child : ((LinearNode) node).children()) {
            PanelNode found = find(child.node(), panelId);
            if (found != null) return found;
        }
        return null;
    }

    /** Activates every Stack encountered on the path to the requested panel. */
    private static Activation activate(Node node, SFMWorkspacePanelId panelId) {
        if (node instanceof PanelNode panel) return new Activation(panel, panel.id().equals(panelId));
        if (node instanceof StackNode stack) {
            List<Node> children = new ArrayList<>(stack.children());
            for (int index = 0; index < children.size(); index++) {
                Activation nested = activate(children.get(index), panelId);
                if (nested.found()) {
                    children.set(index, nested.node());
                    return new Activation(new StackNode(children, index), true);
                }
            }
            return new Activation(stack, false);
        }
        LinearNode linear = (LinearNode) node;
        List<Track> children = new ArrayList<>(linear.children());
        for (int index = 0; index < children.size(); index++) {
            Track child = children.get(index);
            Activation nested = activate(child.node(), panelId);
            if (nested.found()) {
                children.set(index, child.withNode(nested.node()));
                return new Activation(new LinearNode(linear.axis(), children), true);
            }
        }
        return new Activation(linear, false);
    }

    private static void collectPanels(Node node, List<PanelEntry> answer) {
        if (node == null) return;
        if (node instanceof PanelNode panel) {
            answer.add(new PanelEntry(panel.id(), panel.panel(), panel.metadata()));
            return;
        }
        if (node instanceof StackNode stack) {
            for (Node child : stack.children()) collectPanels(child, answer);
            return;
        }
        for (Track child : ((LinearNode) node).children()) collectPanels(child.node(), answer);
    }

    private static void collectVisiblePanels(Node node, List<PanelEntry> answer) {
        if (node == null) return;
        if (node instanceof PanelNode panel) {
            answer.add(new PanelEntry(panel.id(), panel.panel(), panel.metadata()));
            return;
        }
        if (node instanceof StackNode stack) {
            Node active = stack.children().get(stack.active());
            collectVisiblePanels(active, answer);
            return;
        }
        for (Track child : ((LinearNode) node).children()) collectVisiblePanels(child.node(), answer);
    }

    private static boolean contains(Node node, SFMWorkspacePanelId panelId) {
        return find(node, panelId) != null;
    }

    /** Adds to the deepest existing stack on the focused path, or creates one at a leaf. */
    private static Node pushIntoNearestStack(
            Node node,
            SFMWorkspacePanelId focused,
            PanelNode inserted
    ) {
        if (node instanceof StackNode stack) {
            List<Node> children = new ArrayList<>(stack.children());
            for (int index = 0; index < children.size(); index++) {
                Node child = children.get(index);
                if (!contains(child, focused)) continue;
                if (child instanceof PanelNode panel && panel.id().equals(focused)) {
                    children.add(inserted);
                    return new StackNode(children, children.size() - 1);
                }
                Node changed = pushIntoNearestStack(child, focused, inserted);
                if (changed != child) {
                    children.set(index, changed);
                    return new StackNode(children, stack.active());
                }
                children.add(inserted);
                return new StackNode(children, children.size() - 1);
            }
            return node;
        }
        if (node instanceof LinearNode linear) {
            List<Track> children = new ArrayList<>(linear.children());
            for (int index = 0; index < children.size(); index++) {
                Track child = children.get(index);
                if (!contains(child.node(), focused)) continue;
                Node changed = pushIntoNearestStack(child.node(), focused, inserted);
                children.set(index, child.withNode(changed));
                return new LinearNode(linear.axis(), children);
            }
            return node;
        }
        PanelNode panel = (PanelNode) node;
        if (!panel.id().equals(focused)) return node;
        return new StackNode(List.of(panel, inserted), 1);
    }

    private static void collectFocusedSlot(
            Node node,
            SFMWorkspacePanelId focused,
            List<PanelEntry> answer
    ) {
        if (node == null) return;
        if (node instanceof StackNode stack) {
            for (Node child : stack.children()) {
                if (contains(child, focused)) {
                    for (Node entry : stack.children()) collectPanels(entry, answer);
                    return;
                }
            }
            return;
        }
        if (node instanceof LinearNode linear) {
            for (Track child : linear.children()) {
                if (contains(child.node(), focused)) {
                    collectFocusedSlot(child.node(), focused, answer);
                    return;
                }
            }
            return;
        }
        PanelNode panel = (PanelNode) node;
        if (panel.id().equals(focused)) answer.add(new PanelEntry(panel.id(), panel.panel(), panel.metadata()));
    }

    private static Node updateMetadata(
            Node node,
            SFMWorkspacePanelId panelId,
            SFMWorkspacePanelMetadata metadata
    ) {
        if (node instanceof PanelNode panel) {
            return panel.id().equals(panelId)
                    ? new PanelNode(panel.id(), panel.panel(), metadata)
                    : panel;
        }
        if (node instanceof StackNode stack) {
            List<Node> children = stack.children().stream()
                    .map(child -> updateMetadata(child, panelId, metadata)).toList();
            return new StackNode(children, stack.active());
        }
        LinearNode linear = (LinearNode) node;
        List<Track> children = linear.children().stream()
                .map(child -> child.withNode(updateMetadata(child.node(), panelId, metadata))).toList();
        return new LinearNode(linear.axis(), children);
    }

    private static Node replacePayload(
            Node node,
            SFMWorkspacePanelId panelId,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        if (node instanceof PanelNode current) {
            return current.id().equals(panelId)
                    ? new PanelNode(current.id(), panel, metadata)
                    : current;
        }
        if (node instanceof StackNode stack) {
            return new StackNode(stack.children().stream()
                    .map(child -> replacePayload(child, panelId, panel, metadata)).toList(), stack.active());
        }
        LinearNode linear = (LinearNode) node;
        return new LinearNode(linear.axis(), linear.children().stream()
                .map(child -> child.withNode(replacePayload(child.node(), panelId, panel, metadata))).toList());
    }

    private void rebuildPersistentIndexes() {
        persistentPanelIds.clear();
        persistentPanelMetadata.clear();
        List<PanelEntry> entries = new ArrayList<>();
        collectPanels(root, entries);
        for (PanelEntry entry : entries) {
            persistentPanelIds.put(entry.panel(), entry.id());
            persistentPanelMetadata.put(entry.panel(), entry.metadata());
        }
    }

    private static Rotation rotateSlot(Node node, SFMWorkspacePanelId focused, int direction) {
        if (node instanceof StackNode stack) {
            for (Node child : stack.children()) {
                if (!contains(child, focused)) continue;
                if (child instanceof PanelNode) {
                    List<Node> children = new ArrayList<>(stack.children());
                    int active = Math.floorMod(stack.active() + direction, children.size());
                    return new Rotation(new StackNode(children, active), ((PanelNode) children.get(active)).id(), true);
                }
                Rotation nested = rotateSlot(child, focused, direction);
                if (nested.changed()) {
                    List<Node> children = new ArrayList<>(stack.children());
                    int index = children.indexOf(child);
                    children.set(index, nested.node());
                    return new Rotation(new StackNode(children, stack.active()), nested.focused(), true);
                }
            }
            return new Rotation(node, focused, false);
        }
        if (node instanceof LinearNode linear) {
            for (Track child : linear.children()) {
                if (!contains(child.node(), focused)) continue;
                Rotation nested = rotateSlot(child.node(), focused, direction);
                if (!nested.changed()) return nested;
                List<Track> children = new ArrayList<>(linear.children());
                int index = children.indexOf(child);
                children.set(index, child.withNode(nested.node()));
                return new Rotation(new LinearNode(linear.axis(), children), nested.focused(), true);
            }
        }
        return new Rotation(node, focused, false);
    }

    private static int indexOf(List<PanelEntry> panels, SFMWorkspacePanelId panelId) {
        for (int i = 0; i < panels.size(); i++) if (panels.get(i).id().equals(panelId)) return i;
        return -1;
    }

    private @Nullable SFMWorkspacePanelId directionalNeighbor(
            SFMWorkspacePanelId source,
            SFMWorkspaceSide side
    ) {
        SFMScreenPanelBounds viewport = new SFMScreenPanelBounds(0, 0, 1_000_000, 1_000_000);
        SFMScreenPanelBounds sourceBounds = bounds(viewport, 0).get(source);
        if (sourceBounds == null) return null;

        SFMWorkspacePanelId best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (PanelEntry candidate : visiblePanels()) {
            if (candidate.id().equals(source)) continue;
            SFMScreenPanelBounds candidateBounds = bounds(viewport, 0).get(candidate.id());
            if (candidateBounds == null) continue;
            int gap;
            int crossDistance;
            if (side.axis() == SFMWorkspaceAxis.HORIZONTAL) {
                gap = side.before()
                        ? sourceBounds.x() - candidateBounds.x() - candidateBounds.width()
                        : candidateBounds.x() - sourceBounds.x() - sourceBounds.width();
                if (gap < 0) continue;
                crossDistance = intervalDistance(
                        sourceBounds.y(), sourceBounds.y() + sourceBounds.height(),
                        candidateBounds.y(), candidateBounds.y() + candidateBounds.height()
                );
            } else {
                gap = side.before()
                        ? sourceBounds.y() - candidateBounds.y() - candidateBounds.height()
                        : candidateBounds.y() - sourceBounds.y() - sourceBounds.height();
                if (gap < 0) continue;
                crossDistance = intervalDistance(
                        sourceBounds.x(), sourceBounds.x() + sourceBounds.width(),
                        candidateBounds.x(), candidateBounds.x() + candidateBounds.width()
                );
            }
            double score = gap * 1_000_000.0 + crossDistance;
            if (score < bestScore) {
                bestScore = score;
                best = candidate.id();
            }
        }
        return best;
    }

    private static int intervalDistance(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        if (firstEnd < secondStart) return secondStart - firstEnd;
        if (secondEnd < firstStart) return firstStart - secondEnd;
        return 0;
    }

    public record PanelEntry(
            SFMWorkspacePanelId id,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) {
        public PanelEntry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(panel);
            Objects.requireNonNull(metadata);
        }

        public PanelEntry(SFMWorkspacePanelId id, SFMScreenPanel panel) {
            this(id, panel, SFMWorkspacePanelMetadata.ordinary());
        }
    }

    private static Node materialize(
            LayoutSpec spec,
            IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelId> ids,
            IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelMetadata> metadata,
            long[] nextId
    ) {
        if (spec instanceof PanelSpec panel) {
            SFMWorkspacePanelId id = ids.computeIfAbsent(panel.panel(), ignored -> new SFMWorkspacePanelId(nextId[0]++));
            SFMWorkspacePanelMetadata entryMetadata = metadata.computeIfAbsent(
                    panel.panel(), ignored -> SFMWorkspacePanelMetadata.ordinary());
            return new PanelNode(id, panel.panel(), entryMetadata);
        }
        if (spec instanceof StackSpec stack) {
            return new StackNode(stack.children().stream()
                    .map(child -> materialize(child, ids, metadata, nextId)).toList(), stack.active());
        }
        LinearSpec linear = (LinearSpec) spec;
        return new LinearNode(linear.axis(), linear.children().stream()
                .map(child -> new Track(materialize(child, ids, metadata, nextId), 1.0, DEFAULT_MINIMUM_PIXELS)).toList());
    }

    private static void validateUniquePanels(LayoutSpec spec) {
        IdentityHashMap<SFMScreenPanel, Boolean> seen = new IdentityHashMap<>();
        collectUniquePanels(spec, seen);
    }

    private static void collectUniquePanels(LayoutSpec spec, IdentityHashMap<SFMScreenPanel, Boolean> seen) {
        if (spec instanceof PanelSpec panel) {
            if (seen.put(panel.panel(), Boolean.TRUE) != null) {
                throw new IllegalArgumentException("A panel instance may only be attached once");
            }
            return;
        }
        List<LayoutSpec> children = spec instanceof StackSpec stack
                ? stack.children() : ((LinearSpec) spec).children();
        children.forEach(child -> collectUniquePanels(child, seen));
    }

    private static boolean isVisible(Node node, SFMWorkspacePanelId id) {
        if (node == null) return false;
        if (node instanceof PanelNode panel) return panel.id().equals(id);
        if (node instanceof StackNode stack) return isVisible(stack.children().get(stack.active()), id);
        return ((LinearNode) node).children().stream().anyMatch(track -> isVisible(track.node(), id));
    }

    private static PanelNode firstVisible(Node node) {
        if (node instanceof PanelNode panel) return panel;
        if (node instanceof StackNode stack) return firstVisible(stack.children().get(stack.active()));
        return firstVisible(((LinearNode) node).children().get(0).node());
    }

    public sealed interface LayoutSpec permits PanelSpec, LinearSpec, StackSpec {
    }

    public record PanelSpec(SFMScreenPanel panel) implements LayoutSpec {
        public PanelSpec { Objects.requireNonNull(panel); }
    }

    public record LinearSpec(SFMWorkspaceAxis axis, List<LayoutSpec> children) implements LayoutSpec {
        public LinearSpec {
            Objects.requireNonNull(axis);
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("Linear specs require at least two children");
        }
    }

    public record StackSpec(List<LayoutSpec> children, int active) implements LayoutSpec {
        public StackSpec {
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("Stack specs require at least two children");
            if (active < 0 || active >= children.size()) throw new IllegalArgumentException("Active stack index is outside children");
        }
    }

    private sealed interface Node permits PanelNode, LinearNode, StackNode {
    }

    private record PanelNode(
            SFMWorkspacePanelId id,
            SFMScreenPanel panel,
            SFMWorkspacePanelMetadata metadata
    ) implements Node {
        private PanelNode {
            Objects.requireNonNull(id);
            Objects.requireNonNull(panel);
            Objects.requireNonNull(metadata);
        }

        private PanelNode(SFMWorkspacePanelId id, SFMScreenPanel panel) {
            this(id, panel, SFMWorkspacePanelMetadata.ordinary());
        }
    }

    private record LinearNode(SFMWorkspaceAxis axis, List<Track> children) implements Node {
        private LinearNode {
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("Linear nodes require at least two children");
        }
    }

    private record StackNode(List<Node> children, int active) implements Node {
        private StackNode {
            children = List.copyOf(children);
            if (children.size() < 2) throw new IllegalArgumentException("Stack nodes require at least two children");
            if (active < 0 || active >= children.size()) throw new IllegalArgumentException("Active stack index is outside children");
        }
    }

    private record Track(Node node, double share, int minimumPixels) {
        private Track {
            Objects.requireNonNull(node);
            if (!(share > 0.0) || !Double.isFinite(share)) {
                throw new IllegalArgumentException("Track share must be finite and positive");
            }
            if (minimumPixels < 0) throw new IllegalArgumentException("Track minimum must be non-negative");
        }

        private Track withNode(Node replacement) {
            return new Track(replacement, share, minimumPixels);
        }
    }

    private record Configuration(Node node, boolean changed) {
    }

    private record Rotation(Node node, SFMWorkspacePanelId focused, boolean changed) {
    }

    private record Activation(Node node, boolean found) {
    }
}
