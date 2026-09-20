package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphContract;
import ca.teamdman.sfm.client.history.canvas.SFMHistoryCanvasLayout;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistorySession;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryPanel;
import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryPanelArtifact;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Contract and artifact witness for ordinary editor history and its live 2D canvas. */
public final class AssertOrdinaryDocumentHistoryPuppetAction implements SFMPuppetAction {
    public static final String DEPARTED_TEXT = "hello world!";
    public static final String BRANCH_TEXT = "new content";
    public static final String PUSHED_BRANCH_TEXT = "new content!";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final ConcurrentHashMap<String, Long> CANVAS_BASELINES = new ConcurrentHashMap<>();

    private final Stage stage;
    private final String artifactName;

    public AssertOrdinaryDocumentHistoryPuppetAction(Stage stage, String artifactName) {
        this.stage = Objects.requireNonNull(stage, "stage");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Document-history artifact name must not be blank");
        }
        this.artifactName = artifactName;
    }

    @Override
    public String description() {
        return "assert ordinary document-history stage " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMScreenMultiplexer workspace = workspace();
        SFMTextEditorPanel editor = editor(workspace);
        SFMDocumentHistorySession session = editor.documentHistorySession();
        Optional<SFMDocumentHistoryPanel> panel = historyPanel(workspace);
        if (requiresReadyPanel() && (panel.isEmpty()
                || panel.orElseThrow().loadStatus() != SFMDocumentHistoryPanel.LoadStatus.READY)) return false;
        if (!stageReady(workspace, editor, session, panel)) return false;
        runtime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(stageEvidence(workspace, editor, session, panel)));
        assertStage(workspace, editor, session, panel);
        if (stage == Stage.TRANSCRIPT) writeFinalArtifacts(runtime, editor, session, panel.orElseThrow());
        return true;
    }

    private boolean stageReady(
            SFMScreenMultiplexer workspace,
            SFMTextEditorPanel editor,
            SFMDocumentHistorySession session,
            Optional<SFMDocumentHistoryPanel> panel
    ) {
        return switch (stage) {
            case TYPED -> editor.currentText().equals(DEPARTED_TEXT);
            case ROOT_AFTER_UNDO -> editor.currentText().isEmpty();
            case BRANCHED -> editor.currentText().equals(BRANCH_TEXT);
            case CANVAS_READY -> panel.orElseThrow().documentProjection()
                    .map(value -> value.currentRevisionId().equals(session.currentRevisionId()))
                    .orElse(false);
            case LIVE_PUSH -> editor.currentText().equals(PUSHED_BRANCH_TEXT)
                    && panel.orElseThrow().documentProjection()
                    .map(value -> value.currentRevisionId().equals(session.currentRevisionId()))
                    .orElse(false);
            case AMBIGUOUS_REDO -> Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette
                    && palette.choiceCommandsForAutomation().size() >= 2;
            case RESTORED_DEPARTED -> editor.currentText().equals(DEPARTED_TEXT)
                    && panel.orElseThrow().documentProjection()
                    .map(value -> value.currentRevisionId().equals(session.currentRevisionId()))
                    .orElse(false);
            case INTERACTED -> panel.orElseThrow().orientation() == SFMHistoryCanvasLayout.Orientation.LEFT_RIGHT
                    && panel.orElseThrow().detailsExpanded();
            case TRANSCRIPT -> panel.orElseThrow().presentationMode()
                    == SFMDocumentHistoryPanel.PresentationMode.TRANSCRIPT;
        };
    }

    private void assertStage(
            SFMScreenMultiplexer workspace,
            SFMTextEditorPanel editor,
            SFMDocumentHistorySession session,
            Optional<SFMDocumentHistoryPanel> panel
    ) {
        SFMDocumentHistoryContract.Projection projection = session.projection();
        switch (stage) {
            case TYPED -> {
                require(editor.currentText().equals(DEPARTED_TEXT), "ordinary typing bytes differ");
                require(projection.semanticTransactions().stream().map(
                        SFMDocumentHistoryContract.SemanticTransaction::label).toList().equals(List.of(
                        "type \"hello\"", "click \"<space>\"", "type \"world\"", "type \"!\""
                )), "ordinary typing did not project into the expected semantic chunks");
                require(projection.rawEvents().size() >= DEPARTED_TEXT.length(),
                        "ordinary typing omitted raw ingress evidence");
            }
            case ROOT_AFTER_UNDO -> {
                require(editor.currentText().isEmpty(), "four natural undo operations did not restore the root");
                require(retainsText(session, DEPARTED_TEXT), "undo discarded the departed hello-world revision");
                require(projection.headMovements().size() == 4,
                        "root rewind should append four semantic head movements");
            }
            case BRANCHED -> {
                require(editor.currentText().equals(BRANCH_TEXT), "sibling branch text differs");
                require(retainsText(session, DEPARTED_TEXT), "new typing clobbered the departed branch");
                long rootChildren = projection.semanticTransactions().stream()
                        .filter(transaction -> transaction.beforeRevisionId().equals(session.rootRevisionId()))
                        .count();
                require(rootChildren == 2, "root should expose exactly two semantic descendants");
            }
            case CANVAS_READY -> {
                SFMDocumentHistoryPanel history = panel.orElseThrow();
                SFMHistoryCanvasLayout.Snapshot layout = history.layoutSnapshot().orElseThrow();
                require(layout.nodes().stream().anyMatch(node -> node.lane() == SFMHistoryCanvasLayout.Lane.ACTION),
                        "history canvas omitted the blue action lane");
                require(layout.nodes().stream().anyMatch(node -> node.lane() == SFMHistoryCanvasLayout.Lane.STATE),
                        "history canvas omitted the amber state lane");
                require(layout.edges().stream().anyMatch(edge ->
                                edge.pathKind() == SFMHistoryCanvasLayout.EdgePathKind.HEAD_MOVEMENT_JUMP),
                        "history canvas omitted curved undo jumps");
                require(!history.accessibleTranscript().isEmpty(), "history canvas omitted its transcript fallback");
                CANVAS_BASELINES.put(session.identity().sessionId(), history.publicationGeneration());
            }
            case LIVE_PUSH -> {
                SFMDocumentHistoryPanel history = panel.orElseThrow();
                require(workspace.focusedPanelInstance() == editor,
                        "history publication stole focus from the ordinary editor");
                long baseline = CANVAS_BASELINES.getOrDefault(session.identity().sessionId(), -1L);
                require(history.publicationGeneration() > baseline,
                        "history canvas did not receive a newer push publication");
                require(editor.currentText().equals(PUSHED_BRANCH_TEXT), "live pushed branch bytes differ");
            }
            case AMBIGUOUS_REDO -> {
                require(editor.currentText().isEmpty(), "ambiguous redo must begin at the shared root");
                SFMCommandPaletteScreen palette = (SFMCommandPaletteScreen) Minecraft.getInstance().screen;
                List<String> choices = palette.choiceCommandsForAutomation();
                require(choices.size() == 2, "ambiguous redo must offer both retained root descendants");
                require(choices.stream().allMatch(command -> command.contains("sfm:document/history/redo")),
                        "ambiguous redo escaped the registered action surface");
                require(hasCandidateLeadingTo(session, DEPARTED_TEXT),
                        "ambiguous redo has no candidate leading to the departed branch");
                require(hasCandidateLeadingTo(session, PUSHED_BRANCH_TEXT),
                        "ambiguous redo has no candidate leading to the new branch");
            }
            case RESTORED_DEPARTED -> {
                require(editor.currentText().equals(DEPARTED_TEXT), "redo did not restore the selected old branch");
                require(retainsText(session, PUSHED_BRANCH_TEXT), "redo discarded the unselected new branch");
                require(projection.headMovements().stream().anyMatch(value ->
                                value.movement().kind() == SFMHistoryGraphContract.HeadMovementKind.REDO),
                        "redo did not append head-movement evidence");
            }
            case INTERACTED -> {
                SFMDocumentHistoryPanel history = panel.orElseThrow();
                require(history.orientation() == SFMHistoryCanvasLayout.Orientation.LEFT_RIGHT,
                        "transpose did not switch the canvas to left-right");
                require(history.detailsExpanded(), "selected history evidence was not expanded");
                require(history.selectedSubject().isPresent(), "history interaction has no stable selected subject");
                require(!history.selectedDetails().isEmpty(), "selected history subject exposes no details");
            }
            case TRANSCRIPT -> {
                SFMDocumentHistoryPanel history = panel.orElseThrow();
                require(history.presentationMode() == SFMDocumentHistoryPanel.PresentationMode.TRANSCRIPT,
                        "V did not expose the visible chronological transcript");
                require(history.accessibleTranscript().stream().anyMatch(line -> line.contains("hello")),
                        "chronological transcript omitted ordinary typing");
                CANVAS_BASELINES.remove(session.identity().sessionId());
            }
        }
    }

    private JsonObject stageEvidence(
            SFMScreenMultiplexer workspace,
            SFMTextEditorPanel editor,
            SFMDocumentHistorySession session,
            Optional<SFMDocumentHistoryPanel> panel
    ) {
        SFMDocumentHistoryContract.Projection projection = session.projection();
        JsonObject root = new JsonObject();
        root.addProperty("schema", "sfm.ordinary-document-history-stage/1");
        root.addProperty("stage", stage.name());
        root.addProperty("session_id", session.identity().sessionId());
        root.addProperty("generation", session.generation());
        root.addProperty("root_revision", session.rootRevisionId());
        root.addProperty("current_revision", session.currentRevisionId());
        root.addProperty("current_text", editor.currentText());
        root.addProperty("focused_panel_type", workspace.focusedPanelInstance() == null
                ? "none" : workspace.focusedPanelInstance().getClass().getName());
        root.addProperty("revision_count", projection.revisions().size());
        root.addProperty("raw_event_count", projection.rawEvents().size());
        root.addProperty("mutation_count", projection.mutations().size());
        root.addProperty("semantic_transaction_count", projection.semanticTransactions().size());
        root.addProperty("head_movement_count", projection.headMovements().size());
        root.add("retained_texts", strings(projection.revisions().stream()
                .map(value -> value.state().text()).distinct().toList()));
        root.add("semantic_labels", strings(projection.semanticTransactions().stream()
                .map(SFMDocumentHistoryContract.SemanticTransaction::label).toList()));
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) {
            root.add("choice_commands", strings(palette.choiceCommandsForAutomation()));
        }
        panel.ifPresent(history -> {
            root.addProperty("panel_load_status", history.loadStatus().name());
            root.addProperty("panel_publication_generation", history.publicationGeneration());
            root.addProperty("orientation", history.orientation().name());
            root.addProperty("presentation_mode", history.presentationMode().name());
            root.addProperty("details_expanded", history.detailsExpanded());
            root.addProperty("transcript_lines", history.accessibleTranscript().size());
        });
        return root;
    }

    private void writeFinalArtifacts(
            ISFMGamePuppetRuntime runtime,
            SFMTextEditorPanel editor,
            SFMDocumentHistorySession session,
            SFMDocumentHistoryPanel panel
    ) {
        SFMDocumentHistoryContract.Projection projection = session.projection();
        runtime.writeArtifact("document-history", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(documentHistory(projection)));
        runtime.writeArtifact("history-canvas-layout", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(canvasArtifact(panel.artifact())));
        runtime.writeArtifact("current-document", SFMGamePuppetArtifactFormat.UTF8, editor.currentText());
        runtime.writeArtifact("retained-hello-world", SFMGamePuppetArtifactFormat.UTF8, DEPARTED_TEXT);
        runtime.writeArtifact("retained-new-content", SFMGamePuppetArtifactFormat.UTF8, PUSHED_BRANCH_TEXT);
        runtime.writeArtifact("document-history-transcript", SFMGamePuppetArtifactFormat.UTF8,
                String.join("\n", panel.accessibleTranscript()) + "\n");
    }

    private static JsonObject documentHistory(SFMDocumentHistoryContract.Projection projection) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "sfm.ordinary-document-history-puppet/1");
        root.addProperty("projection_schema", projection.schema());
        root.addProperty("session_id", projection.identity().sessionId());
        root.addProperty("generation", projection.generation());
        root.addProperty("current_revision", projection.currentRevisionId());
        root.addProperty("projection_digest", projection.digest());

        JsonArray revisions = new JsonArray();
        for (SFMDocumentHistoryContract.DocumentRevision revision : projection.revisions()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", revision.id());
            value.addProperty("parent", revision.parentRevisionId().orElse(null));
            value.addProperty("sequence", revision.sequence());
            value.addProperty("mutation", revision.mutationId().orElse(null));
            value.addProperty("state_hash", revision.stateHash());
            value.addProperty("content_hash", revision.state().contentHash());
            value.addProperty("text", revision.state().text());
            value.addProperty("selection_count", revision.state().selections().size());
            revisions.add(value);
        }
        root.add("revisions", revisions);

        JsonArray rawEvents = new JsonArray();
        for (SFMDocumentHistoryContract.RawInputEvent event : projection.rawEvents()) {
            JsonObject value = new JsonObject();
            value.addProperty("sequence", event.sequence());
            value.addProperty("id", event.id());
            value.addProperty("client_tick", event.input().clientTick());
            value.addProperty("kind", event.input().kind().name());
            value.addProperty("source", event.input().source());
            value.addProperty("code", event.input().code());
            value.addProperty("text", event.input().text().orElse(null));
            value.addProperty("modifiers", event.input().modifiers());
            value.addProperty("consumed", event.input().consumed());
            value.addProperty("delivered", event.input().delivered());
            rawEvents.add(value);
        }
        root.add("raw_events", rawEvents);

        JsonArray mutations = new JsonArray();
        for (SFMDocumentHistoryContract.DocumentMutation mutation : projection.mutations()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", mutation.id());
            value.addProperty("sequence", mutation.sequence());
            value.addProperty("kind", mutation.kind().name());
            value.addProperty("direction", mutation.direction().name());
            value.addProperty("before", mutation.beforeRevisionId());
            value.addProperty("after", mutation.afterRevisionId());
            value.addProperty("changed_text", mutation.changedText().orElse(null));
            value.addProperty("status", mutation.status().name());
            value.add("raw_event_ids", strings(mutation.provenance().rawEventIds()));
            mutations.add(value);
        }
        root.add("mutations", mutations);

        JsonArray transactions = new JsonArray();
        for (SFMDocumentHistoryContract.SemanticTransaction transaction : projection.semanticTransactions()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", transaction.id());
            value.addProperty("sequence", transaction.sequence());
            value.addProperty("kind", transaction.kind().name());
            value.addProperty("label", transaction.label());
            value.addProperty("before", transaction.beforeRevisionId());
            value.addProperty("after", transaction.afterRevisionId());
            value.add("mutation_ids", strings(transaction.mutationIds()));
            value.add("raw_event_ids", strings(transaction.rawEventIds()));
            value.add("state_revision_ids", strings(transaction.stateRevisionIds()));
            transactions.add(value);
        }
        root.add("semantic_transactions", transactions);

        JsonArray movements = new JsonArray();
        for (SFMDocumentHistoryContract.DocumentHeadMovement movement : projection.headMovements()) {
            JsonObject value = new JsonObject();
            value.addProperty("sequence", movement.sequence());
            value.addProperty("id", movement.movement().id());
            value.addProperty("kind", movement.movement().kind().name());
            value.addProperty("from", movement.movement().fromStateRevisionId());
            value.addProperty("to", movement.movement().toStateRevisionId());
            value.add("candidates", strings(movement.movement().candidateStateRevisionIds()));
            value.add("raw_event_ids", strings(movement.rawEventIds()));
            movements.add(value);
        }
        root.add("head_movements", movements);
        root.add("graph_state_ids", strings(projection.graph().states().stream()
                .map(SFMHistoryGraphContract.StateRevision::id).toList()));
        root.add("graph_edge_ids", strings(projection.graph().edges().stream()
                .map(SFMHistoryGraphContract.BranchEdge::id).toList()));
        root.add("graph_head_movement_ids", strings(projection.graph().headMovements().stream()
                .map(SFMHistoryGraphContract.HeadMovement::id).toList()));
        return root;
    }

    private static JsonObject canvasArtifact(SFMDocumentHistoryPanelArtifact artifact) {
        SFMHistoryCanvasLayout.Snapshot layout = artifact.canvasLayout().orElseThrow();
        JsonObject root = new JsonObject();
        root.addProperty("schema", "sfm.ordinary-document-history-canvas-puppet/1");
        root.addProperty("panel_schema", artifact.schema());
        root.addProperty("selector", artifact.requestedSelector());
        root.addProperty("load_status", artifact.loadStatus().name());
        root.addProperty("status", artifact.statusMessage());
        root.addProperty("catalog_revision", artifact.catalogRevision());
        root.addProperty("publication_generation", artifact.publicationGeneration());
        root.addProperty("session_id", artifact.resolvedSessionId().orElse(null));
        root.addProperty("orientation", layout.orientation().name());
        root.add("content_bounds", rect(layout.contentBounds()));
        root.add("viewport", viewport(artifact.viewport()));
        root.add("transcript", strings(artifact.accessibleTranscript()));

        JsonArray nodes = new JsonArray();
        for (SFMHistoryCanvasLayout.Node node : layout.nodes()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", node.stableId());
            value.addProperty("source_id", node.source().id());
            value.addProperty("lane", node.lane().name());
            value.addProperty("style", node.styleId());
            value.addProperty("rank", node.rank());
            value.addProperty("track", node.track());
            value.addProperty("label", node.label().fullText());
            value.add("label_lines", strings(node.label().lines()));
            value.add("bounds", rect(node.bounds()));
            value.add("marker_bounds", rect(node.markerBounds()));
            value.add("label_bounds", rect(node.labelBounds()));
            nodes.add(value);
        }
        root.add("nodes", nodes);

        JsonArray edges = new JsonArray();
        for (SFMHistoryCanvasLayout.Edge edge : layout.edges()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", edge.stableId());
            value.addProperty("source_id", edge.source().id());
            value.addProperty("from", edge.fromNodeId());
            value.addProperty("to", edge.toNodeId());
            value.addProperty("path_kind", edge.pathKind().name());
            value.addProperty("curved", edge.pathKind().curved());
            value.addProperty("ranking", edge.pathKind().ranking());
            value.add("bounds", rect(edge.bounds()));
            JsonArray points = new JsonArray();
            for (SFMHistoryCanvasLayout.Point point : edge.points()) {
                JsonObject p = new JsonObject();
                p.addProperty("x", point.x());
                p.addProperty("y", point.y());
                points.add(p);
            }
            value.add("points", points);
            edges.add(value);
        }
        root.add("edges", edges);
        root.addProperty("stable_node_identity_agreement", layout.nodes().stream()
                .allMatch(node -> node.stableId().equals(node.source().id())));
        root.addProperty("stable_edge_identity_agreement", layout.edges().stream()
                .allMatch(edge -> edge.stableId().equals(edge.source().id())));
        root.addProperty("all_edges_reference_layout_nodes", layout.edges().stream().allMatch(edge ->
                layout.nodeIds().contains(edge.fromNodeId()) && layout.nodeIds().contains(edge.toNodeId())));
        return root;
    }

    private static JsonObject rect(SFMHistoryCanvasLayout.Rect rect) {
        JsonObject value = new JsonObject();
        value.addProperty("x", rect.x());
        value.addProperty("y", rect.y());
        value.addProperty("width", rect.width());
        value.addProperty("height", rect.height());
        return value;
    }

    private static JsonObject viewport(
            ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryViewport viewport
    ) {
        JsonObject value = new JsonObject();
        value.addProperty("pan_x", viewport.panX());
        value.addProperty("pan_y", viewport.panY());
        value.addProperty("zoom", viewport.zoom());
        return value;
    }

    private static JsonArray strings(List<String> values) {
        JsonArray answer = new JsonArray();
        values.forEach(answer::add);
        return answer;
    }

    public static boolean hasCandidateLeadingTo(SFMDocumentHistorySession session, String expectedText) {
        return session.eligibleRedoChildren().stream().anyMatch(candidate ->
                descendantWithText(session, candidate.id(), expectedText).isPresent());
    }

    public static Optional<String> candidateLeadingTo(SFMDocumentHistorySession session, String expectedText) {
        return session.eligibleRedoChildren().stream()
                .map(candidate -> candidate.id())
                .filter(candidate -> descendantWithText(session, candidate, expectedText).isPresent())
                .findFirst();
    }

    private static Optional<String> descendantWithText(
            SFMDocumentHistorySession session,
            String startingRevision,
            String expectedText
    ) {
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        pending.add(startingRevision);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            SFMDocumentHistoryContract.DocumentRevision revision = session.revision(current).orElseThrow();
            if (revision.state().text().equals(expectedText)) return Optional.of(current);
            pending.addAll(session.childRevisionIds(current));
        }
        return Optional.empty();
    }

    private static boolean retainsText(SFMDocumentHistorySession session, String text) {
        return session.retainedRevisions().stream().anyMatch(revision -> revision.state().text().equals(text));
    }

    static SFMScreenMultiplexer workspace() {
        if (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace) return workspace;
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette
                && palette.actionContextForAutomation().originatingHost() instanceof SFMScreenMultiplexer workspace) {
            return workspace;
        }
        throw new IllegalStateException("Expected an SFM workspace or its constrained command palette");
    }

    static SFMTextEditorPanel editor(SFMScreenMultiplexer workspace) {
        return workspace.panels().stream()
                .filter(SFMTextEditorPanel.class::isInstance)
                .map(SFMTextEditorPanel.class::cast)
                .filter(SFMTextEditorPanel::documentHistoryAvailable)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Ordinary writable Text Editor V3 is not open"));
    }

    static Optional<SFMDocumentHistoryPanel> historyPanel(SFMScreenMultiplexer workspace) {
        return workspace.panels().stream()
                .filter(SFMDocumentHistoryPanel.class::isInstance)
                .map(SFMDocumentHistoryPanel.class::cast)
                .findFirst();
    }

    private boolean requiresReadyPanel() {
        return switch (stage) {
            case CANVAS_READY, LIVE_PUSH, RESTORED_DEPARTED, INTERACTED, TRANSCRIPT -> true;
            default -> false;
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public enum Stage {
        TYPED,
        ROOT_AFTER_UNDO,
        BRANCHED,
        CANVAS_READY,
        LIVE_PUSH,
        AMBIGUOUS_REDO,
        RESTORED_DEPARTED,
        INTERACTED,
        TRANSCRIPT
    }
}
