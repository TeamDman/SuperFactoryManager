package ca.teamdman.sfm.client.history.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerEntry;
import ca.teamdman.sfm.client.explorer.lazy.SFMInMemoryTextExplorerResolver;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.chamber.SFMChamberAmbientCheckoutProbe;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPreviewPlacement;
import ca.teamdman.sfm.client.screen.history.workspace.SFMWorkspaceCounterfactualDocumentPanel;
import ca.teamdman.sfm.client.screen.history.workspace.SFMWorkspaceCounterfactualExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.properties.SFMProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local owner of bounded X5 fixture episodes and their resolver mounts. */
public final class SFMWorkspaceCounterfactualRuntime {
    public static final String PATH_SCHEME = "episode";
    private static final SFMWorkspaceCounterfactualRuntime INSTANCE =
            new SFMWorkspaceCounterfactualRuntime(SFMHistoryGraphRuntime.get());

    public enum OperationKind {
        FORK_BEFORE_SELECTION,
        CHECKOUT_RECORDED_A,
        REPLAY_FROZEN_A,
        REEVALUATE_INTENT_ON_B
    }

    public record TargetResult(String episodeId, SFMHistoryGraphRuntime.OperationResult result) {
        public TargetResult {
            Objects.requireNonNull(episodeId, "episodeId");
            Objects.requireNonNull(result, "result");
        }
    }

    private record Episode(
            SFMWorkspaceCounterfactualController controller,
            SFMPath root,
            SFMInMemoryTextExplorerResolver.MountLease mount,
            SFMHistoryGraphRuntime.Registration historyRegistration
    ) {
    }

    private final SFMHistoryGraphRuntime historyRuntime;
    private final SFMInMemoryTextExplorerResolver resolver = new SFMInMemoryTextExplorerResolver(
            PATH_SCHEME,
            Runnable::run,
            32
    );
    private final AtomicLong nextEpisode = new AtomicLong();
    private final LinkedHashMap<String, Episode> episodes = new LinkedHashMap<>();
    private boolean resolverRegistered;

    SFMWorkspaceCounterfactualRuntime(SFMHistoryGraphRuntime historyRuntime) {
        this.historyRuntime = Objects.requireNonNull(historyRuntime, "historyRuntime");
    }

    public static SFMWorkspaceCounterfactualRuntime get() {
        return INSTANCE;
    }

    /** Creates a fresh fixture and returns its ordinary explorer wrapped only for episode observation. */
    public synchronized SFMWorkspaceCounterfactualExplorerPanel openScene() {
        SFMExplorerRuntime explorerRuntime = SFMExplorerRuntime.get();
        if (!resolverRegistered) {
            explorerRuntime.registerResolverIfAbsent(resolver);
            resolverRegistered = true;
        }
        String episodeId = "sfm:workspace-counterfactual/session-" + nextEpisode.incrementAndGet();
        SFMWorkspaceCounterfactualController controller = new SFMWorkspaceCounterfactualController(
                episodeId,
                SFMChamberAmbientCheckoutProbe.discover(SFMProperties.userDirectory())
        );
        SFMPath root = rootPath(episodeId);
        SFMInMemoryTextExplorerResolver.MountLease mount = resolver.mount(
                episodeId,
                fixtureNodes(controller, root)
        );
        SFMHistoryGraphRuntime.Registration historyRegistration = historyRuntime.register(controller);
        Episode episode = new Episode(controller, root, mount, historyRegistration);
        episodes.put(episodeId, episode);
        historyRuntime.setActiveMachine(episodeId);
        try {
            SFMExplorerPanel explorer = (SFMExplorerPanel) explorerRuntime.openScene(root);
            return new SFMWorkspaceCounterfactualExplorerPanel(this, controller, explorer);
        } catch (RuntimeException failure) {
            episodes.remove(episodeId);
            historyRegistration.close();
            mount.close();
            throw failure;
        }
    }

    public synchronized Optional<SFMWorkspaceCounterfactualController> controller(String episodeId) {
        Episode episode = episodes.get(Objects.requireNonNull(episodeId, "episodeId"));
        return episode == null ? Optional.empty() : Optional.of(episode.controller());
    }

    public synchronized Optional<SFMWorkspaceCounterfactualController> controller(SFMPath path) {
        if (!path.scheme().equals(PATH_SCHEME)) return Optional.empty();
        return controller(path.authority());
    }

    public synchronized List<String> episodeIds() {
        return List.copyOf(episodes.keySet());
    }

    public synchronized void closeEpisode(String episodeId) {
        Episode episode = episodes.remove(Objects.requireNonNull(episodeId, "episodeId"));
        if (episode == null) return;
        episode.historyRegistration().close();
        episode.mount().close();
    }

    public SFMHistoryGraphRuntime.OperationResult select(SFMPath path) {
        SFMWorkspaceCounterfactualController controller = controller(path)
                .orElseThrow(() -> new IllegalArgumentException("No workspace episode owns " + path));
        SFMHistoryGraphRuntime.OperationResult result = controller.selectDocument(logicalPath(path));
        publishIfChanged(controller, result);
        return result;
    }

    public List<TargetResult> select(
            SFMEntitySelector selector,
            Optional<String> focusedEpisodeId,
            String logicalPath
    ) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(focusedEpisodeId, "focusedEpisodeId");
        if (!List.of(
                SFMWorkspaceCounterfactualController.A_PATH,
                SFMWorkspaceCounterfactualController.B_PATH
        ).contains(logicalPath)) {
            throw new IllegalArgumentException("Unknown workspace fixture document " + logicalPath);
        }
        ArrayList<TargetResult> results = new ArrayList<>();
        historyRuntime.resolveSnapshots(selector, focusedEpisodeId).stream()
                .map(SFMHistoryGraphRuntime.MachineSnapshot::machineId)
                .distinct()
                .sorted()
                .map(this::controller)
                .flatMap(Optional::stream)
                .forEach(controller -> {
                    SFMHistoryGraphRuntime.OperationResult result = controller.selectDocument(logicalPath);
                    publishIfChanged(controller, result);
                    results.add(new TargetResult(controller.machineId(), result));
                });
        return List.copyOf(results);
    }

    public int openDocument(
            SFMClientActionContext actionContext,
            SFMPath path,
            SFMExplorerPreviewPlacement.Mode mode
    ) {
        Objects.requireNonNull(actionContext, "actionContext");
        Objects.requireNonNull(mode, "mode");
        SFMWorkspaceCounterfactualController controller = controller(path)
                .orElseThrow(() -> new IllegalArgumentException("No workspace episode owns " + path));
        String logicalPath = logicalPath(path);
        SFMHistoryGraphRuntime.OperationResult result = controller.openSelectedDocument(logicalPath);
        if (result.status() == SFMHistoryGraphRuntime.OperationStatus.REJECTED) {
            throw new IllegalStateException(result.message());
        }
        publishIfChanged(controller, result);
        String parentStateId = controller.currentFrame().id();
        SFMWorkspaceCounterfactualDocumentPanel panel = new SFMWorkspaceCounterfactualDocumentPanel(
                this,
                controller,
                path,
                parentStateId
        );
        SFMScreenMultiplexer workspace = actionContext.originatingHost() instanceof SFMScreenMultiplexer value
                ? value : null;
        SFMWorkspacePanelId sourcePanelId = actionContext.originatingPanelId();
        if (workspace != null && sourcePanelId != null
                && workspace.panelInstance(sourcePanelId) instanceof SFMWorkspaceCounterfactualExplorerPanel source) {
            SFMExplorerPreviewPlacement.Result placement = SFMExplorerPreviewPlacement.place(
                    workspace,
                    sourcePanelId,
                    source.explorerId().value(),
                    mode,
                    panel,
                    null
            );
            if (!placement.applied()) throw new IllegalStateException("Could not place in-memory editor panel");
            return 1;
        }
        ca.teamdman.sfm.client.action.OpenPanelAction.Direction direction =
                mode == SFMExplorerPreviewPlacement.Mode.ADJACENT
                        ? ca.teamdman.sfm.client.action.OpenPanelAction.Direction.RIGHT
                        : ca.teamdman.sfm.client.action.OpenPanelAction.Direction.FOCUSED;
        int opened = ca.teamdman.sfm.client.action.OpenPanelAction.openPanel(actionContext, panel, direction);
        if (opened == 0) throw new IllegalStateException("Could not open in-memory editor panel");
        return opened;
    }

    public List<TargetResult> execute(
            SFMEntitySelector selector,
            Optional<String> focusedEpisodeId,
            OperationKind operation
    ) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(focusedEpisodeId, "focusedEpisodeId");
        Objects.requireNonNull(operation, "operation");
        List<String> ids = historyRuntime.resolveSnapshots(selector, focusedEpisodeId).stream()
                .map(SFMHistoryGraphRuntime.MachineSnapshot::machineId)
                .filter(id -> controller(id).isPresent())
                .distinct()
                .sorted()
                .toList();
        ArrayList<TargetResult> results = new ArrayList<>();
        for (String id : ids) {
            SFMWorkspaceCounterfactualController controller = controller(id).orElseThrow();
            SFMHistoryGraphRuntime.OperationResult result = switch (operation) {
                case FORK_BEFORE_SELECTION -> controller.forkBeforeSelection();
                case CHECKOUT_RECORDED_A -> controller.checkoutRecordedA();
                case REPLAY_FROZEN_A -> controller.replayFrozenAWitness();
                case REEVALUATE_INTENT_ON_B -> controller.reevaluateIntentOnB();
            };
            publishIfChanged(controller, result);
            results.add(new TargetResult(id, result));
        }
        return List.copyOf(results);
    }

    public void publish(SFMWorkspaceCounterfactualController controller) {
        Objects.requireNonNull(controller, "controller");
        if (controller(controller.machineId()).filter(controller::equals).isEmpty()) return;
        historyRuntime.setActiveMachine(controller.machineId());
        historyRuntime.publish(controller.machineId());
    }

    private void publishIfChanged(
            SFMWorkspaceCounterfactualController controller,
            SFMHistoryGraphRuntime.OperationResult result
    ) {
        if (result.status() == SFMHistoryGraphRuntime.OperationStatus.APPLIED) publish(controller);
    }

    public static SFMPath rootPath(String episodeId) {
        return new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                PATH_SCHEME,
                Objects.requireNonNull(episodeId, "episodeId"),
                List.of(),
                Optional.empty(),
                true
        );
    }

    public static SFMPath documentPath(String episodeId, String logicalPath) {
        return new SFMPath(
                SFMPath.Kind.CONTRIBUTED,
                PATH_SCHEME,
                Objects.requireNonNull(episodeId, "episodeId"),
                List.of(Objects.requireNonNull(logicalPath, "logicalPath")),
                Optional.empty(),
                false
        );
    }

    public static String logicalPath(SFMPath path) {
        Objects.requireNonNull(path, "path");
        if (!path.scheme().equals(PATH_SCHEME) || path.segments().size() != 1) {
            throw new IllegalArgumentException("Workspace document addresses require one episode:// segment");
        }
        String logical = path.segments().get(0);
        if (!List.of(
                SFMWorkspaceCounterfactualController.A_PATH,
                SFMWorkspaceCounterfactualController.B_PATH
        ).contains(logical)) {
            throw new IllegalArgumentException("Unknown workspace fixture document " + logical);
        }
        return logical;
    }

    private static List<SFMInMemoryTextExplorerResolver.Node> fixtureNodes(
            SFMWorkspaceCounterfactualController controller,
            SFMPath root
    ) {
        SFMPath a = documentPath(controller.machineId(), SFMWorkspaceCounterfactualController.A_PATH);
        SFMPath b = documentPath(controller.machineId(), SFMWorkspaceCounterfactualController.B_PATH);
        Map<String, SFMExplorerEntry.SortKey> rootSort = Map.of(
                SFMExplorerEntry.SORT_NAME, SFMExplorerEntry.SortKey.available("Counterfactual documents"),
                SFMExplorerEntry.SORT_ICON, SFMExplorerEntry.SortKey.available("minecraft:chest"),
                SFMExplorerEntry.SORT_EXTENSION, SFMExplorerEntry.SortKey.unavailable("root has no extension")
        );
        return List.of(
                SFMInMemoryTextExplorerResolver.Node.directory(
                        new SFMExplorerEntry(root, "Counterfactual documents", true, rootSort, List.of()),
                        List.of(a, b)
                ),
                SFMInMemoryTextExplorerResolver.Node.text(
                        SFMExplorerEntry.simple(a, "A.java", false, Optional.of("minecraft:cocoa_beans")),
                        controller.documentText(SFMWorkspaceCounterfactualController.A_PATH)
                ),
                SFMInMemoryTextExplorerResolver.Node.text(
                        SFMExplorerEntry.simple(b, "B.java", false, Optional.of("minecraft:cocoa_beans")),
                        controller.documentText(SFMWorkspaceCounterfactualController.B_PATH)
                )
        );
    }
}
