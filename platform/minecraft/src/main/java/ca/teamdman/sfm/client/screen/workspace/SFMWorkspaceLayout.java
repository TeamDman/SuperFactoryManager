package ca.teamdman.sfm.client.screen.workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Mutable workspace state backed by a normalized n-ary tree of linear splits.
 * Panel ids survive insertion, removal, normalization, and viewport changes.
 */
public final class SFMWorkspaceLayout {
    public static final int DEFAULT_MINIMUM_PIXELS = 1;
    /** Microsoft Terminal-compatible deterministic resize increment. */
    public static final double DIRECTIONAL_RESIZE_STEP_FRACTION = 0.05;

    private Node root;
    private long nextPanelId;
    private SFMWorkspacePanelId focusedPanel;
    private long mutationRevision;
    private final IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelId> persistentPanelIds;
    private final IdentityHashMap<SFMScreenPanel, SFMWorkspacePanelMetadata> persistentPanelMetadata;
    private final Map<SFMWorkspaceDividerLinkId, List<SFMWorkspaceDividerId>> dividerLinks;

    private SFMWorkspaceLayout(Node root, long nextPanelId, SFMWorkspacePanelId focusedPanel) {
        this.root = root;
        this.nextPanelId = nextPanelId;
        this.focusedPanel = focusedPanel;
        this.mutationRevision = 0;
        this.persistentPanelIds = new IdentityHashMap<>();
        this.persistentPanelMetadata = new IdentityHashMap<>();
        this.dividerLinks = new LinkedHashMap<>();
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
        mutationRevision++;
        pruneDividerLinks();
    }

    /** Changes for which an in-flight absolute resize capture must be discarded. */
    public long mutationRevision() {
        return mutationRevision;
    }

    public SFMWorkspacePanelId focusedPanel() {
        return focusedPanel;
    }

    public boolean focus(SFMWorkspacePanelId panelId) {
        Activation activated = activate(root, panelId);
        if (!activated.found()) return false;
        if (!root.equals(activated.node())) mutationRevision++;
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
        mutationRevision++;
        pruneDividerLinks();
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
        mutationRevision++;
        pruneDividerLinks();
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
        mutationRevision++;
        pruneDividerLinks();
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
        mutationRevision++;
        pruneDividerLinks();
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
        return setGuiScale(focusedPanel, scale);
    }

    public boolean setGuiScale(SFMWorkspacePanelId panelId, @Nullable Integer scale) {
        PanelNode focused = find(root, panelId);
        if (focused == null) return false;
        SFMWorkspacePanelMetadata metadata = scale == null
                ? focused.metadata().clearGuiScaleOverride()
                : focused.metadata().withGuiScaleOverride(scale);
        return updateMetadata(panelId, metadata);
    }

    /** Sets the allocation constraint on the track directly containing this panel. */
    public boolean configurePanel(SFMWorkspacePanelId panelId, double share, int minimumPixels) {
        if (!(share > 0.0) || !Double.isFinite(share)) {
            throw new IllegalArgumentException("Track share must be finite and positive");
        }
        if (minimumPixels < 0) throw new IllegalArgumentException("Track minimum must be non-negative");
        Configuration configured = configure(root, panelId, share, minimumPixels);
        root = configured.node();
        if (configured.changed()) mutationRevision++;
        return configured.changed();
    }

    /** Returns whether the corresponding resize would change the layout without mutating it. */
    public boolean canResize(
            SFMWorkspacePanelId panelId,
            SFMWorkspaceSide side,
            SFMScreenPanelBounds viewport,
            int dividerPixels,
            int minimumPanelPixels
    ) {
        return computeResize(panelId, side, viewport, dividerPixels, minimumPanelPixels).changed();
    }

    /**
     * Grows the slot containing {@code panelId} toward {@code side} by one named
     * resize step. Space comes from the adjacent track at the deepest matching-
     * axis split. If that split has no neighbor on the requested side, the search
     * continues toward the root. The nearest eligible split owns the request even
     * when its neighbor is already at minimum, so a constrained inner split never
     * unexpectedly resizes an outer split.
     *
     * @return {@code true} when the layout changed; {@code false} when the panel,
     * direction, neighbor, or remaining capacity is unavailable
     */
    public boolean resize(
            SFMWorkspacePanelId panelId,
            SFMWorkspaceSide side,
            SFMScreenPanelBounds viewport,
            int dividerPixels,
            int minimumPanelPixels
    ) {
        Resize resized = computeResize(panelId, side, viewport, dividerPixels, minimumPanelPixels);
        if (!resized.changed()) return false;
        root = resized.node();
        mutationRevision++;
        return true;
    }

    private Resize computeResize(
            SFMWorkspacePanelId panelId,
            SFMWorkspaceSide side,
            SFMScreenPanelBounds viewport,
            int dividerPixels,
            int minimumPanelPixels
    ) {
        Objects.requireNonNull(panelId);
        Objects.requireNonNull(side);
        Objects.requireNonNull(viewport);
        if (dividerPixels < 0) throw new IllegalArgumentException("Divider width must be non-negative");
        if (minimumPanelPixels < 0) throw new IllegalArgumentException("Panel minimum must be non-negative");
        if (root == null || find(root, panelId) == null) return Resize.unmatched(root);

        return resize(
                root,
                panelId,
                side,
                viewport,
                dividerPixels,
                minimumPanelPixels
        );
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
        mutationRevision++;
        pruneDividerLinks();
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
        mutationRevision++;
        pruneDividerLinks();
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

    /** Stable slot identity; every entry in a stack resolves to the same value. */
    public Optional<SFMWorkspaceStackId> stackId(SFMWorkspacePanelId panelId) {
        Objects.requireNonNull(panelId);
        return Optional.ofNullable(stackId(root, panelId));
    }

    /** Describes all currently visible dividers in deterministic tree order. */
    public List<SFMWorkspaceDivider> dividers(
            SFMScreenPanelBounds viewport,
            int dividerPixels,
            int hitSlopPixels,
            int minimumPanelPixels
    ) {
        Objects.requireNonNull(viewport);
        validateDividerGeometryArguments(dividerPixels, hitSlopPixels, minimumPanelPixels);
        List<SFMWorkspaceDivider> answer = new ArrayList<>();
        Map<SFMWorkspaceDividerId, SFMWorkspaceDividerLinkId> linksByDivider = linksByDivider();
        collectDividers(
                root,
                viewport,
                viewport,
                "r",
                dividerPixels,
                hitSlopPixels,
                minimumPanelPixels,
                linksByDivider,
                answer
        );
        return List.copyOf(answer);
    }

    /**
     * Selects at most one directly-hit divider per axis, then expands only its
     * explicit link group. Coincident same-axis geometry is never an implicit link.
     */
    public SFMWorkspaceDividerHit hitTestDividers(
            double mouseX,
            double mouseY,
            SFMScreenPanelBounds viewport,
            int dividerPixels,
            int hitSlopPixels,
            int minimumPanelPixels
    ) {
        List<SFMWorkspaceDivider> descriptions = dividers(
                viewport, dividerPixels, hitSlopPixels, minimumPanelPixels);
        Map<SFMWorkspaceDividerId, SFMWorkspaceDivider> byId = new LinkedHashMap<>();
        descriptions.forEach(divider -> byId.put(divider.id(), divider));
        LinkedHashSet<SFMWorkspaceDividerId> selected = new LinkedHashSet<>();
        for (SFMWorkspaceAxis axis : SFMWorkspaceAxis.values()) {
            descriptions.stream()
                    .filter(divider -> divider.axis() == axis)
                    .filter(divider -> divider.hitBounds().contains(mouseX, mouseY))
                    .min(dividerHitComparator(mouseX, mouseY))
                    .ifPresent(divider -> {
                        selected.add(divider.id());
                        if (divider.linkId() != null) {
                            dividerLinks.getOrDefault(divider.linkId(), List.of()).forEach(selected::add);
                        }
                    });
        }
        List<SFMWorkspaceDivider> hits = selected.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SFMWorkspaceDivider::id))
                .toList();
        boolean horizontal = hits.stream().anyMatch(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL);
        boolean vertical = hits.stream().anyMatch(divider -> divider.axis() == SFMWorkspaceAxis.VERTICAL);
        SFMWorkspaceDividerCursor cursor = horizontal && vertical
                ? SFMWorkspaceDividerCursor.RESIZE_BOTH
                : horizontal
                ? SFMWorkspaceDividerCursor.HORIZONTAL_RESIZE
                : vertical
                ? SFMWorkspaceDividerCursor.VERTICAL_RESIZE
                : SFMWorkspaceDividerCursor.DEFAULT;
        return hits.isEmpty() ? SFMWorkspaceDividerHit.empty() : new SFMWorkspaceDividerHit(hits, cursor);
    }

    /** Explicitly links same-axis dividers. This is the only source of synchronized same-axis motion. */
    public boolean linkDividers(
            SFMWorkspaceDividerLinkId linkId,
            List<SFMWorkspaceDividerId> dividerIds
    ) {
        Objects.requireNonNull(linkId);
        Objects.requireNonNull(dividerIds);
        List<SFMWorkspaceDividerId> ids = List.copyOf(new LinkedHashSet<>(dividerIds));
        if (ids.size() < 2) throw new IllegalArgumentException("A divider link requires at least two dividers");
        Set<SFMWorkspaceDividerId> current = structuralDividerIds();
        if (!current.containsAll(ids)) throw new IllegalArgumentException("A linked divider is not in the current layout");
        SFMWorkspaceAxis axis = ids.get(0).axis();
        if (ids.stream().anyMatch(id -> id.axis() != axis)) {
            throw new IllegalArgumentException("Explicit links join same-axis dividers only");
        }
        List<SFMWorkspaceDividerId> sorted = ids.stream().sorted().toList();
        if (sorted.equals(dividerLinks.get(linkId))) return false;
        dividerLinks.replaceAll((existing, members) -> members.stream()
                .filter(id -> !ids.contains(id))
                .toList());
        dividerLinks.values().removeIf(members -> members.size() < 2);
        dividerLinks.put(linkId, sorted);
        mutationRevision++;
        return true;
    }

    public boolean unlinkDividers(SFMWorkspaceDividerLinkId linkId) {
        Objects.requireNonNull(linkId);
        if (dividerLinks.remove(linkId) == null) return false;
        mutationRevision++;
        return true;
    }

    public Map<SFMWorkspaceDividerLinkId, List<SFMWorkspaceDividerId>> dividerLinks() {
        Map<SFMWorkspaceDividerLinkId, List<SFMWorkspaceDividerId>> answer = new LinkedHashMap<>();
        dividerLinks.forEach((id, members) -> answer.put(id, List.copyOf(members)));
        return Collections.unmodifiableMap(answer);
    }

    /** Captures absolute drag state so repeated pointer events never accumulate rounding drift. */
    public Optional<DividerResizeSession> captureDividerResize(
            List<SFMWorkspaceDividerId> requestedIds,
            SFMScreenPanelBounds viewport,
            int dividerPixels,
            int hitSlopPixels,
            int minimumPanelPixels
    ) {
        Objects.requireNonNull(requestedIds);
        validateDividerGeometryArguments(dividerPixels, hitSlopPixels, minimumPanelPixels);
        Map<SFMWorkspaceDividerId, SFMWorkspaceDivider> descriptions = new LinkedHashMap<>();
        dividers(viewport, dividerPixels, hitSlopPixels, minimumPanelPixels)
                .forEach(divider -> descriptions.put(divider.id(), divider));
        LinkedHashSet<SFMWorkspaceDividerId> expanded = new LinkedHashSet<>();
        for (SFMWorkspaceDividerId requested : requestedIds) {
            SFMWorkspaceDivider divider = descriptions.get(requested);
            if (divider == null) return Optional.empty();
            expanded.add(requested);
            if (divider.linkId() != null) {
                expanded.addAll(dividerLinks.getOrDefault(divider.linkId(), List.of()));
            }
        }
        if (expanded.isEmpty()) return Optional.empty();
        List<SFMWorkspaceDividerId> ids = expanded.stream().sorted().toList();
        if (!descriptions.keySet().containsAll(ids)) return Optional.empty();
        Map<SFMWorkspaceDividerId, SFMWorkspaceDividerLinkId> capturedLinks = new LinkedHashMap<>();
        ids.forEach(id -> {
            SFMWorkspaceDividerLinkId linkId = descriptions.get(id).linkId();
            if (linkId != null) capturedLinks.put(id, linkId);
        });
        return Optional.of(new DividerResizeSession(
                this,
                root,
                mutationRevision,
                ids,
                capturedLinks,
                viewport,
                dividerPixels,
                hitSlopPixels,
                minimumPanelPixels,
                bounds(viewport, dividerPixels)
        ));
    }

    /** Re-evaluates one capture from its immutable starting tree using absolute deltas. */
    public SFMWorkspaceDividerResizeResult updateDividerResize(
            DividerResizeSession session,
            int deltaX,
            int deltaY
    ) {
        Objects.requireNonNull(session);
        if (!session.active || session.owner != this || mutationRevision != session.mutationRevision) {
            return sessionResult(session, SFMWorkspaceDividerResizeResult.Status.STALE, Map.of());
        }
        Node candidate = session.originalRoot;
        Map<SFMWorkspaceDividerId, Integer> appliedDeltas = new LinkedHashMap<>();
        boolean clamped = false;
        Map<Object, List<SFMWorkspaceDividerId>> resizeGroups = new LinkedHashMap<>();
        for (SFMWorkspaceDividerId dividerId : session.dividerIds) {
            SFMWorkspaceDividerLinkId linkId = session.linkIds.get(dividerId);
            Object groupKey = linkId == null ? dividerId : linkId;
            resizeGroups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(dividerId);
        }
        for (List<SFMWorkspaceDividerId> group : resizeGroups.values()) {
            int requested = group.get(0).axis() == SFMWorkspaceAxis.HORIZONTAL ? deltaX : deltaY;
            int sharedDelta = requested;
            if (group.size() > 1) {
                for (SFMWorkspaceDividerId dividerId : group) {
                    DividerApplication probe = applyDividerDelta(
                            session.originalRoot,
                            "r",
                            dividerId,
                            requested,
                            session.viewport,
                            session.dividerPixels,
                            session.minimumPanelPixels
                    );
                    if (!probe.matched()) {
                        return sessionResult(session, SFMWorkspaceDividerResizeResult.Status.STALE, appliedDeltas);
                    }
                    sharedDelta = sharedClampedDelta(sharedDelta, probe.appliedDelta(), requested);
                }
            }
            clamped |= sharedDelta != requested;
            for (SFMWorkspaceDividerId dividerId : group) {
                DividerApplication applied = applyDividerDelta(
                        candidate,
                        "r",
                        dividerId,
                        sharedDelta,
                        session.viewport,
                        session.dividerPixels,
                        session.minimumPanelPixels
                );
                if (!applied.matched()) {
                    return sessionResult(session, SFMWorkspaceDividerResizeResult.Status.STALE, appliedDeltas);
                }
                candidate = applied.node();
                appliedDeltas.put(dividerId, applied.appliedDelta());
                clamped |= applied.appliedDelta() != sharedDelta;
            }
        }
        root = candidate;
        session.lastDeltaX = deltaX;
        session.lastDeltaY = deltaY;
        session.lastAppliedDeltas = Map.copyOf(appliedDeltas);
        session.currentBounds = bounds(session.viewport, session.dividerPixels);
        SFMWorkspaceDividerResizeResult.Status status = session.beforeBounds.equals(session.currentBounds)
                ? clamped
                ? SFMWorkspaceDividerResizeResult.Status.CLAMPED
                : SFMWorkspaceDividerResizeResult.Status.UNCHANGED
                : clamped
                ? SFMWorkspaceDividerResizeResult.Status.CLAMPED
                : SFMWorkspaceDividerResizeResult.Status.APPLIED;
        return sessionResult(session, status, appliedDeltas);
    }

    private static int sharedClampedDelta(int current, int candidate, int requested) {
        if (requested > 0) return Math.min(current, candidate);
        if (requested < 0) return Math.max(current, candidate);
        return 0;
    }

    /** Commits the current share state and invalidates the capture. */
    public boolean finishDividerResize(DividerResizeSession session) {
        Objects.requireNonNull(session);
        if (!session.active || session.owner != this || mutationRevision != session.mutationRevision) {
            session.active = false;
            return false;
        }
        session.active = false;
        if (!session.originalRoot.equals(root)) mutationRevision++;
        return true;
    }

    /** Restores the exact starting tree unless another layout mutation superseded the capture. */
    public boolean cancelDividerResize(DividerResizeSession session) {
        Objects.requireNonNull(session);
        if (!session.active || session.owner != this || mutationRevision != session.mutationRevision) {
            session.active = false;
            return false;
        }
        boolean changed = !session.originalRoot.equals(root);
        root = session.originalRoot;
        session.currentBounds = session.beforeBounds;
        session.active = false;
        return changed;
    }

    /** Ends a superseded capture without restoring stale state over a newer layout. */
    public void abandonDividerResize(DividerResizeSession session) {
        Objects.requireNonNull(session);
        session.active = false;
    }

    public SFMWorkspaceDividerResizeResult resizeDividers(
            SFMWorkspaceResizeDividersIntent intent,
            SFMScreenPanelBounds viewport,
            int dividerPixels,
            int hitSlopPixels,
            int minimumPanelPixels
    ) {
        Objects.requireNonNull(intent);
        Optional<DividerResizeSession> captured = captureDividerResize(
                intent.dividerIds(), viewport, dividerPixels, hitSlopPixels, minimumPanelPixels);
        if (captured.isEmpty()) {
            Map<SFMWorkspacePanelId, SFMScreenPanelBounds> current = bounds(viewport, dividerPixels);
            return new SFMWorkspaceDividerResizeResult(
                    SFMWorkspaceDividerResizeResult.Status.UNAVAILABLE,
                    Map.of(), current, current);
        }
        DividerResizeSession session = captured.orElseThrow();
        SFMWorkspaceDividerResizeResult result = updateDividerResize(session, intent.deltaX(), intent.deltaY());
        finishDividerResize(session);
        return result;
    }

    private static void validateDividerGeometryArguments(
            int dividerPixels,
            int hitSlopPixels,
            int minimumPanelPixels
    ) {
        if (dividerPixels < 0) throw new IllegalArgumentException("Divider width must be non-negative");
        if (hitSlopPixels < 0) throw new IllegalArgumentException("Divider hit slop must be non-negative");
        if (minimumPanelPixels < 0) throw new IllegalArgumentException("Panel minimum must be non-negative");
    }

    private Map<SFMWorkspaceDividerId, SFMWorkspaceDividerLinkId> linksByDivider() {
        Map<SFMWorkspaceDividerId, SFMWorkspaceDividerLinkId> answer = new LinkedHashMap<>();
        dividerLinks.forEach((linkId, members) -> members.forEach(member -> answer.put(member, linkId)));
        return answer;
    }

    private Set<SFMWorkspaceDividerId> structuralDividerIds() {
        Set<SFMWorkspaceDividerId> answer = new LinkedHashSet<>();
        collectStructuralDividerIds(root, "r", answer);
        return Set.copyOf(answer);
    }

    private void pruneDividerLinks() {
        Set<SFMWorkspaceDividerId> current = structuralDividerIds();
        dividerLinks.replaceAll((link, members) -> members.stream()
                .filter(current::contains)
                .sorted()
                .toList());
        dividerLinks.values().removeIf(members -> members.size() < 2);
    }

    private static void collectStructuralDividerIds(
            Node node,
            String path,
            Set<SFMWorkspaceDividerId> answer
    ) {
        if (node == null || node instanceof PanelNode) return;
        if (node instanceof StackNode stack) {
            collectStructuralDividerIds(
                    stack.children().get(stack.active()),
                    path + "_s" + stack.active(),
                    answer);
            return;
        }
        LinearNode linear = (LinearNode) node;
        for (int index = 0; index < linear.children().size() - 1; index++) {
            answer.add(dividerId(linear, path, index));
        }
        for (int index = 0; index < linear.children().size(); index++) {
            collectStructuralDividerIds(linear.children().get(index).node(), path + "_l" + index, answer);
        }
    }

    private static void collectDividers(
            Node node,
            SFMScreenPanelBounds nodeBounds,
            SFMScreenPanelBounds viewport,
            String path,
            int dividerPixels,
            int hitSlopPixels,
            int minimumPanelPixels,
            Map<SFMWorkspaceDividerId, SFMWorkspaceDividerLinkId> linksByDivider,
            List<SFMWorkspaceDivider> answer
    ) {
        if (node == null || node instanceof PanelNode) return;
        if (node instanceof StackNode stack) {
            collectDividers(
                    stack.children().get(stack.active()),
                    nodeBounds,
                    viewport,
                    path + "_s" + stack.active(),
                    dividerPixels,
                    hitSlopPixels,
                    minimumPanelPixels,
                    linksByDivider,
                    answer);
            return;
        }

        LinearNode linear = (LinearNode) node;
        int totalLength = axisLength(nodeBounds, linear.axis());
        int available = Math.max(0, totalLength - dividerPixels * (linear.children().size() - 1));
        int[] lengths = allocateTracks(linear.children(), available);
        List<SFMScreenPanelBounds> childBounds = new ArrayList<>(linear.children().size());
        for (int index = 0; index < linear.children().size(); index++) {
            childBounds.add(trackBounds(nodeBounds, linear.axis(), lengths, index, dividerPixels));
        }

        for (int index = 0; index < linear.children().size() - 1; index++) {
            Track before = linear.children().get(index);
            Track after = linear.children().get(index + 1);
            SFMScreenPanelBounds beforeBounds = childBounds.get(index);
            int position = linear.axis() == SFMWorkspaceAxis.HORIZONTAL
                    ? beforeBounds.x() + beforeBounds.width()
                    : beforeBounds.y() + beforeBounds.height();
            int visibleThickness = Math.max(1, dividerPixels);
            SFMScreenPanelBounds lineBounds = linear.axis() == SFMWorkspaceAxis.HORIZONTAL
                    ? new SFMScreenPanelBounds(position, nodeBounds.y(), visibleThickness, nodeBounds.height())
                    : new SFMScreenPanelBounds(nodeBounds.x(), position, nodeBounds.width(), visibleThickness);
            SFMScreenPanelBounds hitBounds = expandAndClip(lineBounds, hitSlopPixels, viewport);
            int beforeMinimum = Math.max(
                    before.minimumPixels(),
                    minimumExtent(before.node(), linear.axis(), dividerPixels, minimumPanelPixels));
            int afterMinimum = Math.max(
                    after.minimumPixels(),
                    minimumExtent(after.node(), linear.axis(), dividerPixels, minimumPanelPixels));
            int minimumPosition = position - Math.max(0, lengths[index] - beforeMinimum);
            int maximumPosition = position + Math.max(0, lengths[index + 1] - afterMinimum);
            SFMWorkspaceDividerId id = dividerId(linear, path, index);
            answer.add(new SFMWorkspaceDivider(
                    id,
                    linear.axis(),
                    lineBounds,
                    hitBounds,
                    position,
                    minimumPosition,
                    maximumPosition,
                    lengths[index],
                    lengths[index + 1],
                    beforeMinimum,
                    afterMinimum,
                    before.share(),
                    after.share(),
                    panelIds(before.node()),
                    panelIds(after.node()),
                    linksByDivider.get(id)
            ));
        }

        for (int index = 0; index < linear.children().size(); index++) {
            collectDividers(
                    linear.children().get(index).node(),
                    childBounds.get(index),
                    viewport,
                    path + "_l" + index,
                    dividerPixels,
                    hitSlopPixels,
                    minimumPanelPixels,
                    linksByDivider,
                    answer);
        }
    }

    private static SFMWorkspaceDividerId dividerId(LinearNode linear, String path, int boundaryIndex) {
        return new SFMWorkspaceDividerId(
                path,
                linear.axis(),
                boundaryIndex,
                anchorPanelId(linear.children().get(boundaryIndex).node()),
                anchorPanelId(linear.children().get(boundaryIndex + 1).node())
        );
    }

    private static SFMWorkspacePanelId anchorPanelId(Node node) {
        return panelIds(node).stream().min(Comparator.comparingLong(SFMWorkspacePanelId::value)).orElseThrow();
    }

    private static List<SFMWorkspacePanelId> panelIds(Node node) {
        List<SFMWorkspacePanelId> answer = new ArrayList<>();
        collectPanelIds(node, answer);
        return answer.stream().distinct().sorted(Comparator.comparingLong(SFMWorkspacePanelId::value)).toList();
    }

    private static void collectPanelIds(Node node, List<SFMWorkspacePanelId> answer) {
        if (node instanceof PanelNode panel) {
            answer.add(panel.id());
        } else if (node instanceof StackNode stack) {
            stack.children().forEach(child -> collectPanelIds(child, answer));
        } else if (node instanceof LinearNode linear) {
            linear.children().forEach(track -> collectPanelIds(track.node(), answer));
        }
    }

    private static @Nullable SFMWorkspaceStackId stackId(Node node, SFMWorkspacePanelId panelId) {
        if (node == null) return null;
        if (node instanceof PanelNode panel) {
            return panel.id().equals(panelId)
                    ? new SFMWorkspaceStackId("panel." + panel.id().value())
                    : null;
        }
        if (node instanceof StackNode stack && contains(stack, panelId)) {
            String members = String.join("_", panelIds(stack).stream()
                    .map(id -> Long.toString(id.value()))
                    .toList());
            return new SFMWorkspaceStackId("stack." + members);
        }
        if (node instanceof StackNode stack) {
            for (Node child : stack.children()) {
                SFMWorkspaceStackId found = stackId(child, panelId);
                if (found != null) return found;
            }
            return null;
        }
        for (Track track : ((LinearNode) node).children()) {
            SFMWorkspaceStackId found = stackId(track.node(), panelId);
            if (found != null) return found;
        }
        return null;
    }

    private static SFMScreenPanelBounds expandAndClip(
            SFMScreenPanelBounds bounds,
            int pixels,
            SFMScreenPanelBounds clip
    ) {
        long left = Math.max((long) clip.x(), (long) bounds.x() - pixels);
        long top = Math.max((long) clip.y(), (long) bounds.y() - pixels);
        long right = Math.min((long) clip.x() + clip.width(), (long) bounds.x() + bounds.width() + pixels);
        long bottom = Math.min((long) clip.y() + clip.height(), (long) bounds.y() + bounds.height() + pixels);
        return new SFMScreenPanelBounds(
                (int) left,
                (int) top,
                Math.max(0, (int) (right - left)),
                Math.max(0, (int) (bottom - top))
        );
    }

    private static Comparator<SFMWorkspaceDivider> dividerHitComparator(double mouseX, double mouseY) {
        return Comparator
                .comparingInt((SFMWorkspaceDivider divider) -> divider.lineBounds().contains(mouseX, mouseY) ? 0 : 1)
                .thenComparingDouble(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL
                        ? Math.abs(mouseX - divider.position())
                        : Math.abs(mouseY - divider.position()))
                .thenComparingInt(divider -> divider.axis() == SFMWorkspaceAxis.HORIZONTAL
                        ? divider.lineBounds().height()
                        : divider.lineBounds().width())
                .thenComparing(SFMWorkspaceDivider::id);
    }

    private static DividerApplication applyDividerDelta(
            Node node,
            String path,
            SFMWorkspaceDividerId target,
            int requestedDelta,
            SFMScreenPanelBounds nodeBounds,
            int dividerPixels,
            int minimumPanelPixels
    ) {
        if (node instanceof PanelNode) return DividerApplication.unmatched(node);
        if (node instanceof StackNode stack) {
            int active = stack.active();
            DividerApplication nested = applyDividerDelta(
                    stack.children().get(active),
                    path + "_s" + active,
                    target,
                    requestedDelta,
                    nodeBounds,
                    dividerPixels,
                    minimumPanelPixels);
            if (!nested.matched()) return DividerApplication.unmatched(stack);
            if (nested.node().equals(stack.children().get(active))) {
                return new DividerApplication(stack, true, nested.appliedDelta());
            }
            List<Node> children = new ArrayList<>(stack.children());
            children.set(active, nested.node());
            return new DividerApplication(new StackNode(children, active), true, nested.appliedDelta());
        }

        LinearNode linear = (LinearNode) node;
        int totalLength = axisLength(nodeBounds, linear.axis());
        int available = Math.max(0, totalLength - dividerPixels * (linear.children().size() - 1));
        int[] lengths = allocateTracks(linear.children(), available);
        if (path.equals(target.nodePath()) && linear.axis() == target.axis()
                && target.boundaryIndex() < linear.children().size() - 1
                && dividerId(linear, path, target.boundaryIndex()).equals(target)) {
            return applyLinearDividerDelta(
                    linear,
                    target.boundaryIndex(),
                    requestedDelta,
                    lengths,
                    available,
                    dividerPixels,
                    minimumPanelPixels);
        }

        for (int index = 0; index < linear.children().size(); index++) {
            Track track = linear.children().get(index);
            DividerApplication nested = applyDividerDelta(
                    track.node(),
                    path + "_l" + index,
                    target,
                    requestedDelta,
                    trackBounds(nodeBounds, linear.axis(), lengths, index, dividerPixels),
                    dividerPixels,
                    minimumPanelPixels);
            if (!nested.matched()) continue;
            if (nested.node().equals(track.node())) {
                return new DividerApplication(linear, true, nested.appliedDelta());
            }
            List<Track> children = new ArrayList<>(linear.children());
            children.set(index, track.withNode(nested.node()));
            return new DividerApplication(new LinearNode(linear.axis(), children), true, nested.appliedDelta());
        }
        return DividerApplication.unmatched(linear);
    }

    /** Shared minimum/clamping/share-normalization model for keyboard, pointer, and action resizing. */
    private static DividerApplication applyLinearDividerDelta(
            LinearNode linear,
            int boundaryIndex,
            int requestedDelta,
            int[] lengths,
            int available,
            int dividerPixels,
            int minimumPanelPixels
    ) {
        int beforeIndex = boundaryIndex;
        int afterIndex = beforeIndex + 1;
        Track before = linear.children().get(beforeIndex);
        Track after = linear.children().get(afterIndex);
        int beforeMinimum = Math.max(
                before.minimumPixels(),
                minimumExtent(before.node(), linear.axis(), dividerPixels, minimumPanelPixels));
        int afterMinimum = Math.max(
                after.minimumPixels(),
                minimumExtent(after.node(), linear.axis(), dividerPixels, minimumPanelPixels));
        int minimumDelta = beforeMinimum - lengths[beforeIndex];
        int maximumDelta = lengths[afterIndex] - afterMinimum;
        int appliedDelta = minimumDelta > maximumDelta
                ? 0
                : Math.max(minimumDelta, Math.min(maximumDelta, requestedDelta));
        if (appliedDelta == 0) return new DividerApplication(linear, true, 0);
        lengths[beforeIndex] += appliedDelta;
        lengths[afterIndex] -= appliedDelta;
        return new DividerApplication(
                new LinearNode(linear.axis(), normalizedTracksForLengths(linear.children(), lengths, available)),
                true,
                appliedDelta);
    }

    private SFMWorkspaceDividerResizeResult sessionResult(
            DividerResizeSession session,
            SFMWorkspaceDividerResizeResult.Status status,
            Map<SFMWorkspaceDividerId, Integer> appliedDeltas
    ) {
        Map<SFMWorkspacePanelId, SFMScreenPanelBounds> current = session.owner == this
                ? bounds(session.viewport, session.dividerPixels)
                : session.currentBounds;
        session.currentBounds = current;
        return new SFMWorkspaceDividerResizeResult(
                status,
                appliedDeltas,
                session.beforeBounds,
                current
        );
    }

    public static final class DividerResizeSession {
        private final SFMWorkspaceLayout owner;
        private final Node originalRoot;
        private final long mutationRevision;
        private final List<SFMWorkspaceDividerId> dividerIds;
        private final Map<SFMWorkspaceDividerId, SFMWorkspaceDividerLinkId> linkIds;
        private final SFMScreenPanelBounds viewport;
        private final int dividerPixels;
        private final int hitSlopPixels;
        private final int minimumPanelPixels;
        private final Map<SFMWorkspacePanelId, SFMScreenPanelBounds> beforeBounds;
        private Map<SFMWorkspacePanelId, SFMScreenPanelBounds> currentBounds;
        private Map<SFMWorkspaceDividerId, Integer> lastAppliedDeltas = Map.of();
        private int lastDeltaX;
        private int lastDeltaY;
        private boolean active = true;

        private DividerResizeSession(
                SFMWorkspaceLayout owner,
                Node originalRoot,
                long mutationRevision,
                List<SFMWorkspaceDividerId> dividerIds,
                Map<SFMWorkspaceDividerId, SFMWorkspaceDividerLinkId> linkIds,
                SFMScreenPanelBounds viewport,
                int dividerPixels,
                int hitSlopPixels,
                int minimumPanelPixels,
                Map<SFMWorkspacePanelId, SFMScreenPanelBounds> beforeBounds
        ) {
            this.owner = owner;
            this.originalRoot = originalRoot;
            this.mutationRevision = mutationRevision;
            this.dividerIds = List.copyOf(dividerIds);
            this.linkIds = Map.copyOf(linkIds);
            this.viewport = viewport;
            this.dividerPixels = dividerPixels;
            this.hitSlopPixels = hitSlopPixels;
            this.minimumPanelPixels = minimumPanelPixels;
            this.beforeBounds = Map.copyOf(beforeBounds);
            this.currentBounds = this.beforeBounds;
        }

        public long mutationRevision() {
            return mutationRevision;
        }

        public List<SFMWorkspaceDividerId> dividerIds() {
            return dividerIds;
        }

        public SFMScreenPanelBounds viewport() {
            return viewport;
        }

        public int dividerPixels() {
            return dividerPixels;
        }

        public int hitSlopPixels() {
            return hitSlopPixels;
        }

        public int minimumPanelPixels() {
            return minimumPanelPixels;
        }

        public Map<SFMWorkspacePanelId, SFMScreenPanelBounds> beforeBounds() {
            return beforeBounds;
        }

        public Map<SFMWorkspacePanelId, SFMScreenPanelBounds> currentBounds() {
            return currentBounds;
        }

        public Map<SFMWorkspaceDividerId, Integer> lastAppliedDeltas() {
            return lastAppliedDeltas;
        }

        public int lastDeltaX() {
            return lastDeltaX;
        }

        public int lastDeltaY() {
            return lastDeltaY;
        }

        public boolean active() {
            return active;
        }
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
                    : (int) Math.floor(Math.nextUp(remaining * tracks.get(i).share() / shares));
            answer[i] += extra;
            distributed += extra;
        }
        return answer;
    }

    private static Resize resize(
            Node node,
            SFMWorkspacePanelId panelId,
            SFMWorkspaceSide side,
            SFMScreenPanelBounds nodeBounds,
            int dividerPixels,
            int minimumPanelPixels
    ) {
        if (node instanceof PanelNode) return Resize.unmatched(node);
        if (node instanceof StackNode stack) {
            for (int index = 0; index < stack.children().size(); index++) {
                Node child = stack.children().get(index);
                if (!contains(child, panelId)) continue;
                Resize nested = resize(
                        child,
                        panelId,
                        side,
                        nodeBounds,
                        dividerPixels,
                        minimumPanelPixels
                );
                if (!nested.changed()) return new Resize(stack, nested.matched(), false);
                List<Node> children = new ArrayList<>(stack.children());
                children.set(index, nested.node());
                return new Resize(new StackNode(children, stack.active()), true, true);
            }
            return Resize.unmatched(stack);
        }

        LinearNode linear = (LinearNode) node;
        int sourceIndex = -1;
        for (int index = 0; index < linear.children().size(); index++) {
            if (contains(linear.children().get(index).node(), panelId)) {
                sourceIndex = index;
                break;
            }
        }
        if (sourceIndex < 0) return Resize.unmatched(linear);

        int totalLength = axisLength(nodeBounds, linear.axis());
        int available = Math.max(0, totalLength - dividerPixels * (linear.children().size() - 1));
        int[] lengths = allocateTracks(linear.children(), available);
        Track sourceTrack = linear.children().get(sourceIndex);
        Resize nested = resize(
                sourceTrack.node(),
                panelId,
                side,
                trackBounds(nodeBounds, linear.axis(), lengths, sourceIndex, dividerPixels),
                dividerPixels,
                minimumPanelPixels
        );
        if (nested.matched()) {
            if (!nested.changed()) return new Resize(linear, true, false);
            List<Track> children = new ArrayList<>(linear.children());
            children.set(sourceIndex, sourceTrack.withNode(nested.node()));
            return new Resize(new LinearNode(linear.axis(), children), true, true);
        }

        if (linear.axis() != side.axis()) return Resize.unmatched(linear);
        int neighborIndex = side.before() ? sourceIndex - 1 : sourceIndex + 1;
        if (neighborIndex < 0 || neighborIndex >= linear.children().size()) {
            return Resize.unmatched(linear);
        }

        int namedStep = Math.max(1, (int) Math.round(available * DIRECTIONAL_RESIZE_STEP_FRACTION));
        int boundaryIndex = side.before() ? neighborIndex : sourceIndex;
        int requestedDelta = side.before() ? -namedStep : namedStep;
        DividerApplication applied = applyLinearDividerDelta(
                linear,
                boundaryIndex,
                requestedDelta,
                lengths,
                available,
                dividerPixels,
                minimumPanelPixels);
        return new Resize(applied.node(), true, applied.appliedDelta() != 0);
    }

    private static int axisLength(SFMScreenPanelBounds bounds, SFMWorkspaceAxis axis) {
        return axis == SFMWorkspaceAxis.HORIZONTAL ? bounds.width() : bounds.height();
    }

    private static SFMScreenPanelBounds trackBounds(
            SFMScreenPanelBounds bounds,
            SFMWorkspaceAxis axis,
            int[] lengths,
            int trackIndex,
            int dividerPixels
    ) {
        int cursor = axis == SFMWorkspaceAxis.HORIZONTAL ? bounds.x() : bounds.y();
        for (int index = 0; index < trackIndex; index++) cursor += lengths[index] + dividerPixels;
        return axis == SFMWorkspaceAxis.HORIZONTAL
                ? new SFMScreenPanelBounds(cursor, bounds.y(), lengths[trackIndex], bounds.height())
                : new SFMScreenPanelBounds(bounds.x(), cursor, bounds.width(), lengths[trackIndex]);
    }

    private static int minimumExtent(
            Node node,
            SFMWorkspaceAxis axis,
            int dividerPixels,
            int minimumPanelPixels
    ) {
        if (node instanceof PanelNode) return minimumPanelPixels;
        if (node instanceof StackNode stack) {
            return stack.children().stream()
                    .mapToInt(child -> minimumExtent(child, axis, dividerPixels, minimumPanelPixels))
                    .max()
                    .orElse(minimumPanelPixels);
        }

        LinearNode linear = (LinearNode) node;
        List<Integer> childMinimums = linear.children().stream()
                .map(track -> Math.max(
                        track.minimumPixels(),
                        minimumExtent(track.node(), axis, dividerPixels, minimumPanelPixels)
                ))
                .toList();
        if (linear.axis() != axis) return childMinimums.stream().mapToInt(Integer::intValue).max().orElse(0);

        long total = (long) dividerPixels * (linear.children().size() - 1);
        for (int childMinimum : childMinimums) total += childMinimum;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Re-encodes exact allocated lengths as normalized flexible shares. */
    private static List<Track> normalizedTracksForLengths(List<Track> tracks, int[] lengths, int available) {
        int[] bases = new int[tracks.size()];
        int minimumTotal = tracks.stream().mapToInt(Track::minimumPixels).sum();
        int assigned = 0;
        if (minimumTotal <= available) {
            for (int index = 0; index < tracks.size(); index++) {
                bases[index] = tracks.get(index).minimumPixels();
                assigned += bases[index];
            }
        } else if (available >= tracks.size()) {
            java.util.Arrays.fill(bases, 1);
            assigned = tracks.size();
        }

        int flexible = available - assigned;
        if (flexible <= 0) return tracks;
        double[] shares = new double[tracks.size()];
        int lastPositive = -1;
        for (int index = 0; index < tracks.size(); index++) {
            int extra = lengths[index] - bases[index];
            if (extra < 0) throw new IllegalStateException("Requested allocation is below its base");
            if (extra > 0) lastPositive = index;
            shares[index] = (double) extra / flexible;
        }
        if (lastPositive < 0) throw new IllegalStateException("Allocation has no flexible track");

        double preceding = 0.0;
        for (int index = 0; index < tracks.size(); index++) {
            if (index == lastPositive) continue;
            preceding += shares[index];
        }
        shares[lastPositive] = Math.max(0.0, 1.0 - preceding);

        List<Track> answer = new ArrayList<>(tracks.size());
        for (int index = 0; index < tracks.size(); index++) {
            Track track = tracks.get(index);
            answer.add(new Track(track.node(), shares[index], track.minimumPixels()));
        }
        return List.copyOf(answer);
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
            if (share < 0.0 || !Double.isFinite(share)) {
                throw new IllegalArgumentException("Track share must be finite and non-negative");
            }
            if (minimumPixels < 0) throw new IllegalArgumentException("Track minimum must be non-negative");
        }

        private Track withNode(Node replacement) {
            return new Track(replacement, share, minimumPixels);
        }
    }

    private record Configuration(Node node, boolean changed) {
    }

    private record Resize(Node node, boolean matched, boolean changed) {
        private static Resize unmatched(Node node) {
            return new Resize(node, false, false);
        }
    }

    private record DividerApplication(Node node, boolean matched, int appliedDelta) {
        private static DividerApplication unmatched(Node node) {
            return new DividerApplication(node, false, 0);
        }
    }

    private record Rotation(Node node, SFMWorkspacePanelId focused, boolean changed) {
    }

    private record Activation(Node node, boolean found) {
    }
}
