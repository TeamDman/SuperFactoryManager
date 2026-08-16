package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerIoCounter;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.presentation.SFMItemIcon;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelModel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanelViewport;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPathLabeler;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPresentation;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPresentationRegistry;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanelBounds;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.theme.SFMClientTheme;
import ca.teamdman.sfm.client.theme.SFMClientThemeService;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Self-contained live proof for X-8b. It uses the real workspace key, character,
 * and wheel callbacks, captures each focus/filter stage, and emits bounded
 * machine evidence beside the screenshots.
 */
public final class ExerciseExplorerInteractionFidelityPuppetAction implements SFMPuppetAction {
    private static final String SCHEMA = "sfm.explorer-interaction-fidelity/1";
    private static final SFMPath STONE = SFMPath.parse("registry://minecraft/item/minecraft/stone");
    private static final String FILTER_QUERY = "stne";

    private final SFMPath javaPath;
    private Stage stage = Stage.WAIT_READY;
    private int stageTicks;
    private SFMExplorerId explorerId;
    private SFMExplorerPanel.FocusChrome bodyFocus;
    private SFMExplorerPanel.FocusChrome locationFocus;
    private SFMExplorerPanel.FocusChrome filterFocus;
    private SFMExplorerPanel.FocusChrome finalBodyFocus;
    private Map<String, Object> javaPresentation = Map.of();
    private Map<String, Object> stonePresentation = Map.of();
    private SFMExplorerIoCounter.Snapshot ioBeforeFilter;
    private SFMExplorerIoCounter.Snapshot ioAfterFilter;
    private long relationBeforeFilter;
    private long relationAfterFilter;
    private Map<String, Object> filterEvidence = Map.of();
    private int scrollTraceStart;
    private final List<Integer> immediateScrollRows = new ArrayList<>();

    public ExerciseExplorerInteractionFidelityPuppetAction(SFMPath javaPath) {
        this.javaPath = Objects.requireNonNull(javaPath, "javaPath");
        if (javaPath.kind() != SFMPath.Kind.FILE) {
            throw new IllegalArgumentException("Explorer fidelity Java path must use the file resolver");
        }
    }

    @Override
    public String description() {
        return "exercise live explorer projection, focus, fuzzy filter, and ordered wheel behavior";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        Handle handle = handle();
        if (handle == null) return waitOrFail("the target explorer workspace");
        return switch (stage) {
            case WAIT_READY -> waitReady(handle);
            case SETTLE_BODY -> settleThen(Stage.CAPTURE_BODY);
            case CAPTURE_BODY -> capture(runtime, "x8b-small-icons-absolute-body-focus", caption(
                    "Small icons and absolute labels compose; Java uses its extension theme while registry rows remain real ItemStacks."
            ), Stage.FOCUS_LOCATION);
            case FOCUS_LOCATION -> focusLocation(handle);
            case SETTLE_LOCATION -> settleThen(Stage.CAPTURE_LOCATION);
            case CAPTURE_LOCATION -> capture(runtime, "x8b-location-focus", caption(
                    "Only the canonical location control owns cyan focus chrome."
            ), Stage.FOCUS_FILTER);
            case FOCUS_FILTER -> focusFilterAndType(handle);
            case SETTLE_FILTER -> settleFilter(handle);
            case CAPTURE_FILTER -> capture(runtime, "x8b-fuzzy-filter-focus", caption(
                    "The narrated filter ranks the current lazy materialization and truthfully reports excluded unmaterialized subtrees."
            ), Stage.CLEAR_AND_SCROLL);
            case CLEAR_AND_SCROLL -> clearFilterAndScroll(handle);
            case SETTLE_SCROLL -> settleScroll(handle);
            case CAPTURE_SCROLL -> capture(runtime, "x8b-ordered-wheel-body-focus", caption(
                    "Three wheel callbacks apply as three ordered model transitions and the next rendered frame shows the resulting body focus."
            ), Stage.WRITE_EVIDENCE);
            case WRITE_EVIDENCE -> writeEvidence(runtime, handle);
        };
    }

    private boolean waitReady(Handle handle) {
        SFMExplorerPanelModel.State state = handle.state();
        boolean hasJava = row(state, javaPath).isPresent();
        boolean hasStone = row(state, STONE).isPresent();
        Optional<SFMExplorerRuntime.ExplorerEvidence> runtimeEvidence = explorerEvidence();
        if (!hasJava
                || !hasStone
                || state.session().settings().view() != SFMExplorerProjection.View.SMALL_ICONS
                || state.session().settings().pathDisplay() != SFMExplorerProjection.PathDisplay.ABSOLUTE_PATH
                || runtimeEvidence.isEmpty()
                || !runtimeEvidence.orElseThrow().activeRequests().isEmpty()) {
            return waitOrFail("the Java and stone rows with settled small-icons/absolute-path state");
        }

        handle.workspace().focusPanel(handle.panelId());
        focusBody(handle);
        bodyFocus = handle.panel().focusChrome(true);
        require(bodyFocus.body() && !bodyFocus.location() && !bodyFocus.filter(),
                "body focus must be exclusive before the first capture");

        SFMExplorerProjection.Row javaRow = row(state, javaPath).orElseThrow();
        SFMExplorerProjection.Row stoneRow = row(state, STONE).orElseThrow();
        javaPresentation = presentationEvidence(javaRow, state, true);
        stonePresentation = presentationEvidence(stoneRow, state, false);
        stage = Stage.SETTLE_BODY;
        stageTicks = 0;
        return false;
    }

    private boolean focusLocation(Handle handle) {
        require(handle.workspace().keyPressed(GLFW.GLFW_KEY_TAB, 0, 0),
                "workspace did not route Tab into the explorer");
        locationFocus = handle.panel().focusChrome(true);
        require(locationFocus.location() && !locationFocus.filter() && !locationFocus.body(),
                "location focus must be exclusive");
        stage = Stage.SETTLE_LOCATION;
        stageTicks = 0;
        return false;
    }

    private boolean focusFilterAndType(Handle handle) {
        SFMExplorerRuntime.Evidence before = SFMExplorerRuntime.get().evidence();
        ioBeforeFilter = before.filesystemIo();
        relationBeforeFilter = before.childRelationRevision();

        require(handle.workspace().keyPressed(GLFW.GLFW_KEY_TAB, 0, 0),
                "workspace did not route Tab from location to filter");
        for (char character : FILTER_QUERY.toCharArray()) {
            require(handle.workspace().charTyped(character, 0),
                    "workspace did not route filter character " + character);
        }
        filterFocus = handle.panel().focusChrome(true);
        require(filterFocus.filter() && !filterFocus.location() && !filterFocus.body(),
                "filter focus must be exclusive");
        stage = Stage.SETTLE_FILTER;
        stageTicks = 0;
        return false;
    }

    private boolean settleFilter(Handle handle) {
        SFMExplorerPanelModel.State state = handle.state();
        if (!state.projection().filter().active()
                || !state.projection().filter().query().equals(FILTER_QUERY)
                || row(state, STONE).isEmpty()) return waitOrFail("the live fuzzy-filter projection");
        if (++stageTicks <= SFMGamePuppetHelper.RENDER_SETTLE_TICKS) return false;

        SFMExplorerRuntime.Evidence after = SFMExplorerRuntime.get().evidence();
        ioAfterFilter = after.filesystemIo();
        relationAfterFilter = after.childRelationRevision();
        require(relationAfterFilter == relationBeforeFilter,
                "filtering changed semantic child relations");
        require(ioAfterFilter.latestSequence() == ioBeforeFilter.latestSequence(),
                "filtering caused filesystem resolver I/O");
        require(explorerEvidence().map(SFMExplorerRuntime.ExplorerEvidence::activeRequests)
                        .orElseThrow().isEmpty(),
                "filtering started a resolver request");

        SFMExplorerProjection.FilterEvidence filter = state.projection().filter();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("query", filter.query());
        evidence.put("candidate_count", filter.candidateCount());
        evidence.put("match_count", filter.matchCount());
        evidence.put("incomplete_materialization", filter.incompleteMaterialization());
        evidence.put("visible_paths", state.projection().rows().stream()
                .map(result -> result.path().canonical()).toList());
        filterEvidence = evidence;
        stage = Stage.CAPTURE_FILTER;
        stageTicks = 0;
        return false;
    }

    private boolean clearFilterAndScroll(Handle handle) {
        require(handle.workspace().keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0),
                "Delete did not clear the focused explorer filter");
        require(handle.workspace().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0),
                "Enter did not return explorer focus to the body");
        require(handle.workspace().keyPressed(GLFW.GLFW_KEY_G, 0, GLFW.GLFW_MOD_CONTROL),
                "Ctrl+G did not toggle the explorer view through its semantic action");
        require(handle.workspace().keyPressed(GLFW.GLFW_KEY_HOME, 0, 0),
                "Home did not select and reveal the first explorer row");

        SFMExplorerPanelModel.State before = handle.state();
        require(before.session().settings().filterQuery().isEmpty(), "filter did not clear");
        require(before.session().settings().view() == SFMExplorerProjection.View.LIST,
                "Ctrl+G did not switch small icons back to list view");
        require(before.viewport().maximumScrollRow() >= 3,
                "live explorer did not materialize enough rows for ordered wheel proof");
        require(before.viewport().scrollRow() == 0, "Home did not restore the first scroll row");

        SFMExplorerPanelViewport.Rect body = before.viewport().layout().body();
        double[] bodyPoint = workspacePoint(handle, body);
        double mouseX = bodyPoint[0];
        double mouseY = bodyPoint[1];
        scrollTraceStart = handle.panel().model().scrollTraceSnapshot().size();
        for (int expected = 1; expected <= 3; expected++) {
            require(handle.workspace().mouseScrolled(mouseX, mouseY, -1D),
                    "workspace did not route wheel callback " + expected);
            int observed = handle.state().viewport().scrollRow();
            immediateScrollRows.add(observed);
            require(observed == expected,
                    "wheel callback " + expected + " produced row " + observed + " instead of " + expected);
        }
        finalBodyFocus = handle.panel().focusChrome(true);
        require(finalBodyFocus.body() && !finalBodyFocus.location() && !finalBodyFocus.filter(),
                "body focus must remain exclusive while scrolling");
        stage = Stage.SETTLE_SCROLL;
        stageTicks = 0;
        return false;
    }

    private boolean settleScroll(Handle handle) {
        List<SFMExplorerPanelModel.ScrollTrace> traces = newScrollTraces(handle);
        if (traces.size() != 3
                || traces.stream().anyMatch(trace -> trace.firstObservedFrame().isEmpty())) {
            return waitOrFail("the first rendered frame observing all wheel callbacks");
        }
        require(traces.stream().allMatch(trace -> trace.eventToFrameNanos().isPresent()),
                "wheel traces are missing callback-to-frame timing");
        require(traces.stream().allMatch(trace -> trace.modelToFrameNanos().isPresent()),
                "wheel traces are missing model-to-frame timing");
        require(traces.stream().map(SFMExplorerPanelModel.ScrollTrace::sequence).distinct().count() == 3,
                "wheel callbacks lost their distinct sequence identities");
        require(traces.stream().map(SFMExplorerPanelModel.ScrollTrace::afterRow).toList()
                        .equals(List.of(1, 2, 3)),
                "wheel model transitions were not retained in receipt order");
        if (++stageTicks <= SFMGamePuppetHelper.RENDER_SETTLE_TICKS) return false;
        stage = Stage.CAPTURE_SCROLL;
        return false;
    }

    private boolean writeEvidence(ISFMGamePuppetRuntime runtime, Handle handle) {
        SFMExplorerPanelModel.State state = handle.state();
        List<SFMExplorerPanelModel.ScrollTrace> traces = newScrollTraces(handle);
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schema", SCHEMA);
        evidence.put("explorer_id", explorerId.value());
        evidence.put("java_path", javaPath.canonical());
        evidence.put("item_path", STONE.canonical());
        evidence.put("settings", settingsEvidence(state));
        evidence.put("java_presentation", javaPresentation);
        evidence.put("item_presentation", stonePresentation);
        evidence.put("focus", focusEvidence());
        evidence.put("filter", filterEvidence);
        evidence.put("filter_relation_revision_before", relationBeforeFilter);
        evidence.put("filter_relation_revision_after", relationAfterFilter);
        evidence.put("filter_io_before", ioEvidence(ioBeforeFilter));
        evidence.put("filter_io_after", ioEvidence(ioAfterFilter));
        evidence.put("immediate_scroll_rows", List.copyOf(immediateScrollRows));
        evidence.put("scroll_traces", traces.stream().map(this::scrollEvidence).toList());
        evidence.put("final_scroll_row", state.viewport().scrollRow());
        evidence.put("final_visible_paths", state.viewport().cells().stream()
                .map(cell -> cell.row().path().canonical()).toList());
        runtime.writeArtifact(
                "explorer-interaction-fidelity",
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        return true;
    }

    private static double[] workspacePoint(Handle handle, SFMExplorerPanelViewport.Rect local) {
        SFMScreenPanelBounds panel = handle.workspace().panelBounds(handle.panelId());
        require(panel != null, "explorer panel lost its workspace allocation");
        SFMScreenPanelBounds physicalContent = panel.inset(1);
        double scaleX = physicalContent.width() / (double) Math.max(1, handle.bounds().width());
        double scaleY = physicalContent.height() / (double) Math.max(1, handle.bounds().height());
        return new double[]{
                physicalContent.x() + (local.x() + Math.max(0.5D, local.width() / 2D)) * scaleX,
                physicalContent.y() + (local.y() + Math.max(0.5D, local.height() / 2D)) * scaleY
        };
    }

    private Map<String, Object> presentationEvidence(
            SFMExplorerProjection.Row row,
            SFMExplorerPanelModel.State state,
            boolean java
    ) {
        SFMExplorerPresentationRegistry.Resolution resolution =
                SFMExplorerPresentationRegistry.minecraftDefaults().resolve(row);
        SFMExplorerPresentation.ItemIcon icon = requireItemIcon(resolution.presentation().icon());
        if (java) {
            SFMItemIcon configured = SFMClientThemeService.active().fileIcons().get(".java");
            require(configured != null, "active theme has no .java contribution");
            require(icon.item().equals(configured), "Java row did not use the active extension contribution");
            require(!resolution.contributorId().equals("sfm:file_path"),
                    "Java row fell through to the ordinary paper presenter");
        } else {
            require(icon.item().requestedItem().toString().equals("minecraft:stone"),
                    "item-registry row did not retain its actual ItemStack id");
        }
        String label = SFMExplorerPathLabeler.label(row, state.session());
        require(label.equals(row.path().canonical()), "absolute path display did not remain independent of view");
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("path", row.path().canonical());
        evidence.put("contributor_id", resolution.contributorId());
        evidence.put("label", label);
        evidence.put("requested_item", icon.item().requestedItem().toString());
        evidence.put("fallback_item", icon.item().fallbackItem().toString());
        evidence.put("accessible_label", icon.item().accessibleLabel());
        evidence.put("default_java_item", java
                ? SFMClientTheme.defaults().fileIcon(".java").requestedItem().toString()
                : null);
        return evidence;
    }

    private static SFMExplorerPresentation.ItemIcon requireItemIcon(SFMExplorerPresentation.Icon icon) {
        if (icon instanceof SFMExplorerPresentation.ItemIcon item) return item;
        throw new IllegalStateException("Expected an ItemStack explorer icon but found " + icon);
    }

    private Map<String, Object> focusEvidence() {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("body", chromeEvidence(bodyFocus));
        evidence.put("location", chromeEvidence(locationFocus));
        evidence.put("filter", chromeEvidence(filterFocus));
        evidence.put("body_after_scroll", chromeEvidence(finalBodyFocus));
        return evidence;
    }

    private static Map<String, Boolean> chromeEvidence(SFMExplorerPanel.FocusChrome chrome) {
        return Map.of(
                "location", chrome.location(),
                "filter", chrome.filter(),
                "body", chrome.body()
        );
    }

    private static Map<String, Object> settingsEvidence(SFMExplorerPanelModel.State state) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("view", state.session().settings().view().name());
        evidence.put("path_display", state.session().settings().pathDisplay().name());
        evidence.put("filter_query", state.session().settings().filterQuery());
        evidence.put("candidate_count", state.projection().filter().candidateCount());
        evidence.put("match_count", state.projection().filter().matchCount());
        return evidence;
    }

    private static Map<String, Object> ioEvidence(SFMExplorerIoCounter.Snapshot io) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("metadata_reads", io.metadataReads());
        evidence.put("directory_enumerations", io.directoryEnumerations());
        evidence.put("entries_observed", io.entriesObserved());
        evidence.put("render_thread_violations", io.renderThreadViolations());
        evidence.put("latest_sequence", io.latestSequence());
        evidence.put("events_dropped", io.eventsDropped());
        return evidence;
    }

    private Map<String, Object> scrollEvidence(SFMExplorerPanelModel.ScrollTrace trace) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sequence", trace.sequence());
        evidence.put("requested_delta", trace.requestedDelta());
        evidence.put("before_row", trace.beforeRow());
        evidence.put("after_row", trace.afterRow());
        evidence.put("callback_nano_time", trace.callbackNanoTime());
        evidence.put("model_mutation_nano_time", trace.modelMutationNanoTime());
        evidence.put("event_to_model_nanos", trace.eventToModelNanos());
        evidence.put("first_observed_frame", trace.firstObservedFrame().orElse(null));
        evidence.put("first_observed_nano_time", trace.firstObservedNanoTime().orElse(null));
        evidence.put("model_to_frame_nanos", trace.modelToFrameNanos().orElse(null));
        evidence.put("event_to_frame_nanos", trace.eventToFrameNanos().orElse(null));
        evidence.put("observed_scroll_row", trace.observedScrollRow().orElse(null));
        return evidence;
    }

    private List<SFMExplorerPanelModel.ScrollTrace> newScrollTraces(Handle handle) {
        List<SFMExplorerPanelModel.ScrollTrace> all = handle.panel().model().scrollTraceSnapshot();
        return all.subList(Math.min(scrollTraceStart, all.size()), all.size());
    }

    private static Optional<SFMExplorerProjection.Row> row(SFMExplorerPanelModel.State state, SFMPath path) {
        return state.projection().rows().stream().filter(candidate -> candidate.path().equals(path)).findFirst();
    }

    private Optional<SFMExplorerRuntime.ExplorerEvidence> explorerEvidence() {
        if (explorerId == null) return Optional.empty();
        return SFMExplorerRuntime.get().evidence().explorers().stream()
                .filter(evidence -> evidence.session().id().equals(explorerId))
                .findFirst();
    }

    private Handle handle() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return null;
        for (SFMWorkspacePanelId panelId : workspace.panelIds()) {
            if (!(workspace.panelInstance(panelId) instanceof SFMExplorerPanel panel)) continue;
            if (explorerId != null && !panel.explorerId().equals(explorerId)) continue;
            SFMScreenPanelBounds bounds = workspace.panelContentBounds(panelId);
            if (bounds == null) continue;
            SFMExplorerPanelModel.State state = panel.model().state(bounds);
            if (explorerId == null
                    && (row(state, javaPath).isEmpty() || row(state, STONE).isEmpty())) continue;
            explorerId = panel.explorerId();
            return new Handle(workspace, panelId, panel, bounds, state);
        }
        return null;
    }

    private void focusBody(Handle handle) {
        for (int attempts = 0; attempts < 3 && !handle.panel().focusChrome(true).body(); attempts++) {
            require(handle.workspace().keyPressed(GLFW.GLFW_KEY_TAB, 0, 0),
                    "workspace did not route Tab while restoring body focus");
        }
        require(handle.panel().focusChrome(true).body(), "could not restore explorer body focus");
    }

    private boolean capture(
            ISFMGamePuppetRuntime runtime,
            String name,
            Component caption,
            Stage next
    ) {
        if (!runtime.capture(name, caption)) return false;
        stage = next;
        stageTicks = 0;
        return false;
    }

    private boolean settleThen(Stage next) {
        if (++stageTicks <= SFMGamePuppetHelper.RENDER_SETTLE_TICKS) return false;
        stage = next;
        stageTicks = 0;
        return false;
    }

    private boolean waitOrFail(String target) {
        if (++stageTicks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS) {
            throw new IllegalStateException("Timed out waiting for " + target + " during " + stage);
        }
        return false;
    }

    private static Component caption(String text) {
        return Component.literal("SFM Explorer X-8b — ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(text).withStyle(ChatFormatting.BLACK));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private enum Stage {
        WAIT_READY,
        SETTLE_BODY,
        CAPTURE_BODY,
        FOCUS_LOCATION,
        SETTLE_LOCATION,
        CAPTURE_LOCATION,
        FOCUS_FILTER,
        SETTLE_FILTER,
        CAPTURE_FILTER,
        CLEAR_AND_SCROLL,
        SETTLE_SCROLL,
        CAPTURE_SCROLL,
        WRITE_EVIDENCE
    }

    private record Handle(
            SFMScreenMultiplexer workspace,
            SFMWorkspacePanelId panelId,
            SFMExplorerPanel panel,
            SFMScreenPanelBounds bounds,
            SFMExplorerPanelModel.State state
    ) {
    }
}
