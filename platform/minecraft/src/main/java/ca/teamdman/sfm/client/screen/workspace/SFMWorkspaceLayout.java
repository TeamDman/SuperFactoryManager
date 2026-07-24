package ca.teamdman.sfm.client.screen.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private SFMWorkspaceLayout(Node root, long nextPanelId, SFMWorkspacePanelId focusedPanel) {
        this.root = root;
        this.nextPanelId = nextPanelId;
        this.focusedPanel = focusedPanel;
        this.persistentPanelIds = new IdentityHashMap<>();
        List<PanelEntry> entries = new ArrayList<>();
        collectPanels(root, entries);
        entries.forEach(entry -> persistentPanelIds.put(entry.panel(), entry.id()));
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
        Node root = materialize(spec, ids, nextId);
        List<PanelEntry> panels = new ArrayList<>();
        collectPanels(root, panels);
        if (panels.isEmpty()) throw new IllegalArgumentException("Panel group must contain a panel");
        SFMWorkspaceLayout layout = new SFMWorkspaceLayout(root, nextId[0], panels.get(0).id());
        layout.persistentPanelIds.putAll(ids);
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
        Node candidate = materialize(spec, persistentPanelIds, candidateNextId);
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
        Objects.requireNonNull(source);
        Objects.requireNonNull(side);
        Objects.requireNonNull(panel);
        if (find(source) == null) throw new IllegalArgumentException("Unknown source panel: " + source);
        if (persistentPanelIds.containsKey(panel)) throw new IllegalArgumentException("Panel instance is already attached");
        SFMWorkspacePanelId inserted = new SFMWorkspacePanelId(nextPanelId++);
        persistentPanelIds.put(panel, inserted);
        root = normalize(insert(root, source, side, new PanelNode(inserted, panel)));
        focusedPanel = inserted;
        return inserted;
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
        int removedIndex = indexOf(before, panelId);
        if (removedIndex < 0) return false;
        SFMScreenPanel removedPanel = before.get(removedIndex).panel();
        root = normalize(remove(root, panelId));
        persistentPanelIds.remove(removedPanel);
        List<PanelEntry> after = panels();
        if (after.isEmpty()) {
            focusedPanel = null;
        } else if (panelId.equals(focusedPanel)) {
            focusedPanel = after.get(Math.min(removedIndex, after.size() - 1)).id();
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
                .map(entry -> new PanelEntry(entry.getValue(), entry.getKey()))
                .sorted(java.util.Comparator.comparingLong(entry -> entry.id().value()))
                .toList();
    }

    public SFMScreenPanel panel(SFMWorkspacePanelId panelId) {
        PanelNode found = find(panelId);
        return found == null ? null : found.panel();
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
        if (node instanceof PanelNode panel) {
            if (!panel.id().equals(source)) return panel;
            Track oldTrack = new Track(panel, 1.0, DEFAULT_MINIMUM_PIXELS);
            Track newTrack = new Track(inserted, 1.0, DEFAULT_MINIMUM_PIXELS);
            return new LinearNode(
                    side.axis(),
                    side.before() ? List.of(newTrack, oldTrack) : List.of(oldTrack, newTrack)
            );
        }
        if (node instanceof StackNode stack) {
            List<Node> children = new ArrayList<>(stack.children());
            for (int index = 0; index < children.size(); index++) {
                children.set(index, insert(children.get(index), source, side, inserted));
            }
            return new StackNode(children, stack.active());
        }
        LinearNode linear = (LinearNode) node;
        List<Track> children = new ArrayList<>(linear.children().size());
        for (Track child : linear.children()) {
            children.add(child.withNode(insert(child.node(), source, side, inserted)));
        }
        return new LinearNode(linear.axis(), children);
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
            answer.add(new PanelEntry(panel.id(), panel.panel()));
            return;
        }
        if (node instanceof StackNode stack) {
            for (Node child : stack.children()) collectPanels(child, answer);
            return;
        }
        for (Track child : ((LinearNode) node).children()) collectPanels(child.node(), answer);
    }

    private static int indexOf(List<PanelEntry> panels, SFMWorkspacePanelId panelId) {
        for (int i = 0; i < panels.size(); i++) if (panels.get(i).id().equals(panelId)) return i;
        return -1;
    }

    public record PanelEntry(SFMWorkspacePanelId id, SFMScreenPanel panel) {
    }

    private static Node materialize(
            LayoutSpec spec,
            IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelId> ids,
            long[] nextId
    ) {
        if (spec instanceof PanelSpec panel) {
            SFMWorkspacePanelId id = ids.computeIfAbsent(panel.panel(), ignored -> new SFMWorkspacePanelId(nextId[0]++));
            return new PanelNode(id, panel.panel());
        }
        if (spec instanceof StackSpec stack) {
            return new StackNode(stack.children().stream().map(child -> materialize(child, ids, nextId)).toList(), stack.active());
        }
        LinearSpec linear = (LinearSpec) spec;
        return new LinearNode(linear.axis(), linear.children().stream()
                .map(child -> new Track(materialize(child, ids, nextId), 1.0, DEFAULT_MINIMUM_PIXELS)).toList());
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

    private record PanelNode(SFMWorkspacePanelId id, SFMScreenPanel panel) implements Node {
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

    private record Activation(Node node, boolean found) {
    }
}
