package ca.teamdman.sfm.client.screen.review.explorer;

import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.review.comment.SFMCommentHashtags;
import ca.teamdman.sfm.client.screen.review.comment.SFMFixtureReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentDataSource;
import ca.teamdman.sfm.client.screen.review.comment.SFMReviewCommentKernelDataSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Pure tree/navigation model shared by changes, comments, and hashtag explorers. */
public final class SFMReviewExplorerModel {
    public enum Kind { ROOT, FILE, LANE, REVISION, COMMENT, HASHTAG, REGION, CANDIDATE_TARGET }

    public record SourceLeaf(String id, String title, String path, String text, boolean missing) {
        public SourceLeaf {
            Objects.requireNonNull(id);
            Objects.requireNonNull(title);
            Objects.requireNonNull(path);
            Objects.requireNonNull(text);
        }
    }

    /** Typed action payload retained independently from the human-readable row label. */
    public sealed interface NodeAction permits CandidateNavigation {
    }

    /** Exact immutable candidate identity used to reopen the pinned plan/route/frame. */
    public record CandidateNavigation(
            String commentId,
            SFMReviewSessionV2.CandidateTrajectoryTarget target
    ) implements NodeAction {
        public CandidateNavigation {
            Objects.requireNonNull(commentId, "commentId");
            Objects.requireNonNull(target, "target");
            if (commentId.isBlank()) throw new IllegalArgumentException("commentId must not be blank");
        }
    }

    public static final class Node {
        private final String id;
        private final String label;
        private final Kind kind;
        private final List<Node> children;
        private final SourceLeaf leaf;
        private final NodeAction action;
        private boolean expanded;

        private Node(
                String id,
                String label,
                Kind kind,
                List<Node> children,
                SourceLeaf leaf,
                NodeAction action,
                boolean expanded
        ) {
            this.id = Objects.requireNonNull(id);
            this.label = Objects.requireNonNull(label);
            this.kind = Objects.requireNonNull(kind);
            this.children = new ArrayList<>(children);
            this.leaf = leaf;
            this.action = action;
            this.expanded = expanded;
        }

        public String id() { return id; }
        public String label() { return label; }
        public Kind kind() { return kind; }
        public List<Node> children() { return List.copyOf(children); }
        public SourceLeaf leaf() { return leaf; }
        public Optional<NodeAction> action() { return Optional.ofNullable(action); }
        public boolean expanded() { return expanded; }
        public boolean expandable() { return !children.isEmpty(); }
        public void setExpanded(boolean expanded) { this.expanded = expanded; }
    }

    public record VisibleNode(Node node, int depth) {}

    public record LaneData(String id, String beforeSelector, String afterSelector, List<FileData> files) {
        public LaneData {
            files = List.copyOf(files);
        }
    }

    public record FileData(String path, String beforeText, String afterText) {}

    private final Node root;
    private int selectionIndex;

    private SFMReviewExplorerModel(Node root) {
        this.root = root;
    }

    public static SFMReviewExplorerModel changes(String beforeSelector, String afterSelector) {
        if (beforeSelector == null || beforeSelector.isBlank()) throw new IllegalArgumentException("before selector is blank");
        if (afterSelector == null || afterSelector.isBlank()) throw new IllegalArgumentException("after selector is blank");
        List<LaneData> lanes = List.of(
                new LaneData("1.19.2", beforeSelector, afterSelector, List.of(
                        new FileData("src/Example.java", "class Example {\n    void oldName() {}\n}\n",
                                "class Example {\n    void newName() {}\n}\n"),
                        new FileData("src/Added.java", null, "final class Added {}\n"),
                        new FileData("src/Deleted.java", "final class Deleted {}\n", null)
                )),
                new LaneData("1.19.4", beforeSelector, afterSelector, List.of(
                        new FileData("src/Example.java", "class Example {\n    void oldName() {}\n}\n",
                                "class Example {\n    void newName() { audit(); }\n}\n"),
                        new FileData("src/Added.java", null, "final class Added {}\n"),
                        new FileData("src/Deleted.java", "final class Deleted {}\n", null)
                ))
        );
        Map<String, Node> files = new TreeMap<>();
        for (LaneData lane : lanes) {
            for (FileData file : lane.files()) {
                files.computeIfAbsent(file.path(), path -> node("file/" + path, path, Kind.FILE, List.of(), null, true));
            }
        }
        for (Node fileNode : files.values()) {
            List<Node> laneNodes = new ArrayList<>();
            for (LaneData lane : lanes) {
                FileData file = lane.files().stream().filter(candidate -> candidate.path().equals(fileNode.label())).findFirst().orElseThrow();
                SourceLeaf before = revisionLeaf(lane, file, true);
                SourceLeaf after = revisionLeaf(lane, file, false);
                laneNodes.add(node(fileNode.id() + "/lane/" + lane.id(),
                        lane.id() + "  " + lane.beforeSelector() + " → " + lane.afterSelector(), Kind.LANE,
                        List.of(node(before.id(), before.title(), Kind.REVISION, List.of(), before, true),
                                node(after.id(), after.title(), Kind.REVISION, List.of(), after, true)), null, true));
            }
            fileNode.children.addAll(laneNodes);
        }
        return new SFMReviewExplorerModel(node("changes", "Changes · " + beforeSelector + " → " + afterSelector,
                Kind.ROOT, new ArrayList<>(files.values()), null, true));
    }

    public static SFMReviewExplorerModel comments() {
        return fixtureComments(false);
    }

    public static SFMReviewExplorerModel hashtags() {
        return fixtureComments(true);
    }

    /** Production projection over the canonical immutable V2 review session. */
    public static SFMReviewExplorerModel comments(SFMReviewSessionV2 session) {
        return sessionComments(session, false);
    }

    /** Production hashtag projection over the canonical immutable V2 review session. */
    public static SFMReviewExplorerModel hashtags(SFMReviewSessionV2 session) {
        return sessionComments(session, true);
    }

    private static SFMReviewExplorerModel fixtureComments(boolean hashtags) {
        SFMReviewCommentDataSource.SessionView session = new SFMFixtureReviewCommentDataSource().refresh();
        return comments(session, Map.of(), hashtags);
    }

    private static SFMReviewExplorerModel sessionComments(SFMReviewSessionV2 session, boolean hashtags) {
        Objects.requireNonNull(session, "session");
        SFMReviewCommentDataSource.SessionView view = new SFMReviewCommentKernelDataSource(session).refresh();
        Map<String, CandidateNavigation> candidateTargets = new LinkedHashMap<>();
        for (SFMReviewSessionV2.Comment comment : session.comments()) {
            if (comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target) {
                candidateTargets.put(comment.id(), new CandidateNavigation(comment.id(), target));
            }
        }
        return comments(view, candidateTargets, hashtags);
    }

    private static SFMReviewExplorerModel comments(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, CandidateNavigation> candidateTargets,
            boolean hashtags
    ) {
        Map<String, SFMReviewCommentDataSource.DocumentView> documents = new LinkedHashMap<>();
        for (SFMReviewCommentDataSource.DocumentView document : session.documents()) {
            documents.put(document.id(), document);
        }
        Node root = hashtags
                ? hashtagTree(session, documents, candidateTargets)
                : commentTree(session, documents, candidateTargets);
        return new SFMReviewExplorerModel(root);
    }

    private static Node commentTree(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, CandidateNavigation> candidateTargets
    ) {
        List<Node> comments = new ArrayList<>();
        for (SFMReviewCommentDataSource.CommentView comment : session.comments()) {
            List<Node> regions = new ArrayList<>();
            CandidateNavigation candidateTarget = candidateTargets.get(comment.id());
            if (candidateTarget != null) {
                regions.add(candidateTargetNode("comment/" + comment.id() + "/candidate", candidateTarget));
            }
            int index = 0;
            for (SFMReviewCommentDataSource.RangeView range : comment.ranges()) {
                SFMReviewCommentDataSource.DocumentView document = documents.get(range.documentRevisionId());
                if (document == null) continue;
                SourceLeaf leaf = leafForRange("comment/" + comment.id() + "/" + index, comment.id(), document, range);
                regions.add(node(leaf.id(), document.side() + " · " + document.path() + " ["
                                + range.startByte() + ".." + range.endByte() + ")", Kind.REGION,
                        List.of(), leaf, true));
                index++;
            }
            String label = candidateTarget == null
                    ? comment.id() + "  " + comment.text()
                    : "[candidate · " + projectionStatusLabel(candidateTarget.target()) + "] "
                            + comment.id() + "  " + comment.text();
            comments.add(node("comment/" + comment.id(), label, Kind.COMMENT,
                    regions, null, candidateTarget, true));
        }
        return node("comments", "Comments · " + session.title(), Kind.ROOT, comments, null, true);
    }

    private static Node hashtagTree(
            SFMReviewCommentDataSource.SessionView session,
            Map<String, SFMReviewCommentDataSource.DocumentView> documents,
            Map<String, CandidateNavigation> candidateTargets
    ) {
        Map<String, Map<String, List<Node>>> grouped = new TreeMap<>();
        Map<String, List<Node>> candidateGrouped = new TreeMap<>();
        for (SFMReviewCommentDataSource.CommentView comment : session.comments()) {
            Set<String> tags = new LinkedHashSet<>(SFMCommentHashtags.derive(comment.text()));
            CandidateNavigation candidateTarget = candidateTargets.get(comment.id());
            if (candidateTarget != null) {
                for (String tag : tags) {
                    candidateGrouped.computeIfAbsent(tag, ignored -> new ArrayList<>())
                            .add(candidateTargetNode(
                                    "hashtag/" + tag + "/candidate/" + comment.id(),
                                    candidateTarget
                            ));
                }
            }
            int index = 0;
            for (SFMReviewCommentDataSource.RangeView range : comment.ranges()) {
                SFMReviewCommentDataSource.DocumentView document = documents.get(range.documentRevisionId());
                if (document == null) continue;
                SourceLeaf leaf = leafForRange("hashtag/" + comment.id() + "/" + index, comment.id(), document, range);
                Node region = node(leaf.id(), comment.id() + " · " + document.side() + " ["
                                + range.startByte() + ".." + range.endByte() + ")", Kind.REGION,
                        List.of(), leaf, true);
                for (String tag : tags) {
                    grouped.computeIfAbsent(tag, ignored -> new TreeMap<>())
                            .computeIfAbsent(document.path(), ignored -> new ArrayList<>())
                            .add(region);
                }
                index++;
            }
        }
        List<Node> tags = new ArrayList<>();
        Set<String> allTags = new java.util.TreeSet<>(grouped.keySet());
        allTags.addAll(candidateGrouped.keySet());
        for (String tag : allTags) {
            List<Node> files = grouped.getOrDefault(tag, Map.<String, List<Node>>of()).entrySet().stream()
                    .map(file -> node("hashtag/" + tag + "/" + file.getKey(), file.getKey(), Kind.FILE,
                            file.getValue(), null, true))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            files.addAll(candidateGrouped.getOrDefault(tag, List.of()));
            tags.add(node("hashtag/" + tag, tag, Kind.HASHTAG, files, null, true));
        }
        return node("hashtags", "Hashtags · " + session.title(), Kind.ROOT, tags, null, true);
    }

    private static Node candidateTargetNode(String id, CandidateNavigation navigation) {
        return node(id, candidateTargetLabel(navigation), Kind.CANDIDATE_TARGET,
                List.of(), null, navigation, true);
    }

    private static String candidateTargetLabel(CandidateNavigation navigation) {
        SFMReviewSessionV2.CandidateTrajectoryTarget target = navigation.target();
        StringBuilder label = new StringBuilder("candidate ")
                .append(target.targetKind().name().toLowerCase(Locale.ROOT))
                .append(" · plan=").append(target.trajectoryPlanRevisionId())
                .append(" · route=").append(target.routeId())
                .append(" · frame=").append(target.routeStepPosition());
        target.trajectoryStepId().ifPresent(step -> label.append(" · step=").append(step));
        target.actionIntentId().ifPresent(action -> label.append(" · action=").append(action));
        label.append(" · state=").append(target.predictedStateId())
                .append(" · status=").append(projectionStatusLabel(target));
        target.projectedDocumentSelection().ifPresent(selection -> label
                .append(" · document=").append(selection.documentId())
                .append('[').append(selection.startByte()).append(',').append(selection.endByte()).append(')'));
        return label.toString();
    }

    private static String projectionStatusLabel(SFMReviewSessionV2.CandidateTrajectoryTarget target) {
        return target.projectionStatus().name().toLowerCase(Locale.ROOT);
    }

    private static SourceLeaf revisionLeaf(LaneData lane, FileData file, boolean before) {
        String text = before ? file.beforeText() : file.afterText();
        String side = before ? "before" : "after";
        return new SourceLeaf(
                "revision/" + lane.id() + "/" + file.path() + "/" + side,
                side + " · " + lane.id() + " · " + (text == null ? "missing" : file.path()),
                file.path(), text == null ? "" : text, text == null
        );
    }

    private static SourceLeaf leafForRange(
            String id,
            String commentId,
            SFMReviewCommentDataSource.DocumentView document,
            SFMReviewCommentDataSource.RangeView range
    ) {
        return new SourceLeaf(id, commentId + " · " + document.side() + " · " + document.path(),
                document.path(), document.text(), false);
    }

    private static Node node(String id, String label, Kind kind, List<Node> children, SourceLeaf leaf, boolean expanded) {
        return node(id, label, kind, children, leaf, null, expanded);
    }

    private static Node node(
            String id,
            String label,
            Kind kind,
            List<Node> children,
            SourceLeaf leaf,
            NodeAction action,
            boolean expanded
    ) {
        return new Node(id, label, kind, children, leaf, action, expanded);
    }

    public Node root() { return root; }

    public List<VisibleNode> visibleNodes() {
        List<VisibleNode> result = new ArrayList<>();
        appendVisible(root, 0, result);
        return List.copyOf(result);
    }

    public int selectionIndex() { return selectionIndex; }

    public void select(int index) {
        selectionIndex = Math.max(0, Math.min(Math.max(0, visibleNodes().size() - 1), index));
    }

    public Node selected() {
        List<VisibleNode> visible = visibleNodes();
        if (visible.isEmpty()) return root;
        return visible.get(Math.max(0, Math.min(selectionIndex, visible.size() - 1))).node();
    }

    public SourceLeaf selectedLeaf() { return selected().leaf(); }

    public void selectNext() { selectionIndex = Math.min(Math.max(0, visibleNodes().size() - 1), selectionIndex + 1); }
    public void selectPrevious() { selectionIndex = Math.max(0, selectionIndex - 1); }
    public void selectFirst() { selectionIndex = 0; }
    public void selectLast() { selectionIndex = Math.max(0, visibleNodes().size() - 1); }

    public void expandSelection() {
        Node selected = selected();
        if (selected.expandable()) selected.expanded = true;
    }

    public void collapseSelectionOrSelectParent() {
        Node selected = selected();
        if (selected.expandable() && selected.expanded()) {
            selected.expanded = false;
            return;
        }
        Node parent = parentOf(root, selected);
        if (parent == null) return;
        List<VisibleNode> visible = visibleNodes();
        for (int index = 0; index < visible.size(); index++) {
            if (visible.get(index).node() == parent) {
                selectionIndex = index;
                return;
            }
        }
    }

    public void toggleSelection() {
        Node selected = selected();
        if (selected.expandable()) selected.expanded = !selected.expanded;
    }

    private static void appendVisible(Node node, int depth, List<VisibleNode> result) {
        result.add(new VisibleNode(node, depth));
        if (!node.expanded) return;
        for (Node child : node.children) appendVisible(child, depth + 1, result);
    }

    private static Node parentOf(Node current, Node target) {
        for (Node child : current.children) {
            if (child == target) return current;
            Node nested = parentOf(child, target);
            if (nested != null) return nested;
        }
        return null;
    }
}
