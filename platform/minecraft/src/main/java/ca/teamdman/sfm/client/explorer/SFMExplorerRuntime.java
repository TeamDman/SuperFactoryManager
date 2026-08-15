package ca.teamdman.sfm.client.explorer;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.OpenPanelAction;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionExecutor;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionEngine;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionResult;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerPathPolicy;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerRepository;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerIoCounter;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolverRegistry;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMFilesystemExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMGatedExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMItemRegistryExplorerResolver;
import ca.teamdman.sfm.client.explorer.lazy.SFMLazyExplorerLoader;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextRequest;
import ca.teamdman.sfm.client.explorer.lazy.SFMResolverTextResult;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide authority for live explorer sessions.
 *
 * <p>Panels are projections of this state. Command-palette actions and Vox
 * requests both submit the same typed requests to the same transaction engine;
 * resolver work is delegated to daemon workers and never performed by render.</p>
 */
public final class SFMExplorerRuntime implements AutoCloseable {
    public static final int DEFAULT_PAGE_SIZE = 128;

    public record ExplorerEvidence(
            SFMExplorerSession.Snapshot session,
            SFMExplorerProjection.Result projection,
            boolean focused,
            List<SFMExplorerSession.RequestObservation> activeRequests,
            List<SFMExplorerSession.RequestObservation> recentRequests
    ) {
        public ExplorerEvidence {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(projection, "projection");
            activeRequests = List.copyOf(activeRequests);
            recentRequests = List.copyOf(recentRequests);
        }
    }

    public record Evidence(
            long explorerRegistryGeneration,
            long selectionRepositoryGeneration,
            long childRelationRevision,
            long childRelationStatusGeneration,
            List<ExplorerEvidence> explorers,
            SFMExplorerIoCounter.Snapshot filesystemIo
    ) {
        public Evidence {
            explorers = List.copyOf(explorers);
            Objects.requireNonNull(filesystemIo, "filesystemIo");
        }
    }

    private static volatile SFMExplorerRuntime instance;

    private final ExecutorService resolverExecutor;
    private final SFMSelectionRepository selections;
    private final SFMChildRelationRepository relations;
    private final SFMExplorerResolverRegistry resolvers;
    private final SFMFilesystemExplorerResolver filesystem;
    private final SFMGatedExplorerResolver gatedFilesystem;
    private final SFMExplorerIoCounter filesystemIo;
    private final SFMLazyExplorerLoader loader;
    private final SFMExplorerRepository explorers;
    private final SFMExplorerActionEngine actions;
    private final AtomicLong nextExplorerId = new AtomicLong(1);
    private final Map<SFMExplorerId, SFMExplorerPanel> panels = new TreeMap<>(
            java.util.Comparator.comparing(SFMExplorerId::value)
    );
    private boolean closed;

    private SFMExplorerRuntime(Minecraft minecraft) {
        Objects.requireNonNull(minecraft, "minecraft");
        resolverExecutor = Executors.newFixedThreadPool(2, resolverThreadFactory());
        selections = new SFMSelectionRepository();
        relations = new SFMChildRelationRepository();
        resolvers = new SFMExplorerResolverRegistry();
        filesystemIo = new SFMExplorerIoCounter();
        Path gameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        filesystem = new SFMFilesystemExplorerResolver(
                List.of(gameDirectory),
                resolverExecutor,
                filesystemIo,
                DEFAULT_PAGE_SIZE
        );
        gatedFilesystem = new SFMGatedExplorerResolver(filesystem);
        resolvers.register(gatedFilesystem);
        resolvers.register(SFMItemRegistryExplorerResolver.minecraft(
                resolverExecutor,
                DEFAULT_PAGE_SIZE
        ));
        loader = new SFMLazyExplorerLoader(resolvers, relations, minecraft::execute);
        explorers = new SFMExplorerRepository();
        actions = new SFMExplorerActionEngine(
                explorers,
                this::createExplorer,
                this::authorizeRootIfRequired,
                this::locationAuthorityIncompatibility
        );
    }

    public static SFMExplorerRuntime get() {
        SFMExplorerRuntime current = instance;
        if (current != null) return current;
        synchronized (SFMExplorerRuntime.class) {
            if (instance == null) instance = new SFMExplorerRuntime(Minecraft.getInstance());
            return instance;
        }
    }

    public SFMExplorerActionResult execute(SFMExplorerActionRequest request) {
        ensureOpen();
        Objects.requireNonNull(request, "request");
        return actions.execute(request);
    }

    /** Purely prepares and captures an action so bounded transports can validate before publication. */
    public SFMExplorerActionEngine.PreparedAction prepare(SFMExplorerActionRequest request) {
        ensureOpen();
        return actions.prepare(Objects.requireNonNull(request, "request"));
    }

    /** Executes one semantic transaction, then hosts any created session normally. */
    public SFMExplorerActionResult executeAndOpen(
            SFMExplorerActionRequest request,
            SFMClientActionContext actionContext
    ) {
        return publishAndOpen(prepare(request), actionContext);
    }

    /** Publishes one prepared transaction, then hosts any explorer it created. */
    public SFMExplorerActionResult publishAndOpen(
            SFMExplorerActionEngine.PreparedAction prepared,
            SFMClientActionContext actionContext
    ) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(actionContext, "actionContext");
        SFMExplorerActionResult result = actions.publish(prepared);
        for (SFMExplorerActionResult.TargetResult target : result.targets()) {
            if (target.outcome() != SFMExplorerActionResult.TargetOutcome.CREATED) continue;
            OpenPanelAction.Direction direction = actionContext.originatingHost() instanceof SFMScreenMultiplexer
                    ? OpenPanelAction.Direction.RIGHT
                    : OpenPanelAction.Direction.FOCUSED;
            int opened = OpenPanelAction.openPanel(
                    actionContext,
                    panel(target.explorerId()),
                    direction
            );
            if (opened == 0) {
                closeExplorer(target.explorerId());
                throw new IllegalStateException("The created explorer could not be opened in the panel workspace");
            }
        }
        return result;
    }

    /** Creates a registered explorer for the explicit panel scene action. */
    public synchronized SFMScreenPanel openScene(SFMPath initialRoot) {
        return openScene(new SFMPathExpression.Literal(Objects.requireNonNull(initialRoot, "initialRoot")));
    }

    /** Creates a registered explorer from one complete, authority-preflighted location capture. */
    public synchronized SFMScreenPanel openScene(SFMPathExpression initialLocation) {
        ensureOpen();
        Objects.requireNonNull(initialLocation, "initialLocation");
        SFMPathExpressionResolution resolution = SFMPathExpressionResolver.resolve(
                initialLocation,
                selections,
                relations
        );
        if (!resolution.complete()) {
            String diagnostics = resolution.diagnostics().stream()
                    .map(diagnostic -> diagnostic.code() + ": " + diagnostic.message())
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalArgumentException(
                    "Explorer location did not resolve completely"
                            + (diagnostics.isEmpty() ? "" : ": " + diagnostics)
            );
        }
        if (resolution.paths().isEmpty()) {
            throw new IllegalArgumentException("Explorer location resolved to no roots");
        }
        // Resolve and validate every member before publishing any session. A
        // failed heterogeneous expression therefore cannot partially grant or
        // display roots.
        resolution.paths().forEach(this::preflightRoot);
        resolution.paths().forEach(this::authorizeRoot);
        SFMExplorerRepository.Explorer explorer = createExplorer(initialLocation, resolution.paths());
        explorers.register(explorer, true);
        resolution.paths().forEach(loader::openRoot);
        return panel(explorer.id());
    }

    public synchronized SFMExplorerPanel panel(SFMExplorerId id) {
        ensureOpen();
        SFMExplorerRepository.Explorer explorer = explorers.find(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("Unknown explorer: " + id.value()));
        return panels.computeIfAbsent(id, ignored -> new SFMExplorerPanel(
                explorer.session(),
                explorer.loader(),
                this::submitPanelAction,
                () -> focusExplorer(id),
                () -> closeExplorer(id)
        ));
    }

    public SFMExplorerRepository repository() {
        return explorers;
    }

    public SFMSelectionRepository selections() {
        return selections;
    }

    public SFMChildRelationRepository relations() {
        return relations;
    }

    public SFMExplorerIoCounter filesystemIo() {
        return filesystemIo;
    }

    /** Dispatches one immutable bounded text request to the path's registered resolver. */
    public CompletableFuture<SFMResolverTextResult> readText(SFMResolverTextRequest request) {
        ensureOpen();
        Objects.requireNonNull(request, "request");
        return resolvers.find(request.path().scheme())
                .map(resolver -> resolver.readText(request))
                .orElseGet(() -> CompletableFuture.completedFuture(SFMResolverTextResult.failure(
                        request,
                        SFMResolverTextResult.Status.UNSUPPORTED_RESOLVER,
                        request.expectedResolverGeneration(),
                        "No explorer resolver is registered for scheme `" + request.path().scheme() + "`"
                )));
    }

    /** Deepest explicit filesystem grant that already contains this path. */
    public Optional<SFMPath> authorizedFilesystemRootFor(SFMPath path) {
        ensureOpen();
        Objects.requireNonNull(path, "path");
        if (path.kind() != SFMPath.Kind.FILE) return Optional.empty();
        Path nativePath = path.toNativePath().toAbsolutePath().normalize();
        return filesystem.explicitRoots().stream()
                .filter(root -> nativePath.startsWith(root.toNativePath().toAbsolutePath().normalize()))
                .max(java.util.Comparator.comparingInt(root -> root.toNativePath().getNameCount()));
    }

    /**
     * Grants one exact read-only source root advertised by the supervised local
     * toolchain worker. Callers must first validate the worker's resolver/root
     * identity; this method never infers a parent or broadens the supplied root.
     */
    public boolean authorizeManagedReadOnlyRoot(SFMPath root) {
        ensureOpen();
        Objects.requireNonNull(root, "root");
        if (root.kind() != SFMPath.Kind.FILE) return false;
        filesystem.authorizeRoot(root.toNativePath());
        return filesystem.explicitRoots().stream().anyMatch(root::equals);
    }

    public Optional<Long> resolverGeneration(String scheme) {
        ensureOpen();
        Objects.requireNonNull(scheme, "scheme");
        return resolvers.find(scheme).map(ca.teamdman.sfm.client.explorer.lazy.SFMExplorerResolver::generation);
    }

    public SFMGatedExplorerResolver.Gate armFilesystemPublicationGate(SFMPath parent) {
        return gatedFilesystem.armNext(parent);
    }

    public Evidence evidence() {
        SFMExplorerRepository.StateSnapshot registry = explorers.stateSnapshot();
        ArrayList<ExplorerEvidence> explorerEvidence = new ArrayList<>();
        for (Map.Entry<SFMExplorerId, SFMExplorerRepository.Explorer> entry : registry.explorers().entrySet()) {
            SFMExplorerSession.Snapshot session = entry.getValue().session().snapshot();
            explorerEvidence.add(new ExplorerEvidence(
                    session,
                    SFMExplorerProjection.project(session, relations.snapshot(), loader.entrySnapshot()),
                    registry.focused().equals(Optional.of(entry.getKey())),
                    entry.getValue().session().activeRequestEvidence(),
                    entry.getValue().session().recentRequestEvidence()
            ));
        }
        SFMChildRelationRepository.Snapshot relation = relations.snapshot();
        return new Evidence(
                registry.generation(),
                selections.stateSnapshot().generation(),
                relation.relation().id(),
                relation.statusGeneration(),
                explorerEvidence,
                filesystemIo.snapshot()
        );
    }

    /**
     * Disposes every live explorer while keeping the process-wide resolver
     * authority available for a subsequent workspace or viewport journey.
     */
    public synchronized void closeAllExplorers() {
        ensureOpen();
        closeAllExplorersLocked();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closeAllExplorersLocked();
        closed = true;
        resolverExecutor.shutdownNow();
    }

    private void closeAllExplorersLocked() {
        for (SFMExplorerRepository.Explorer explorer : explorers.explorersInStableOrder()) {
            SFMExplorerPanel panel = panels.remove(explorer.id());
            if (panel != null) panel.closed();
            explorer.session().close();
            explorers.unregister(explorer.id());
        }
        panels.clear();
    }

    private SFMExplorerRepository.Explorer createExplorer(SFMPath initialRoot) {
        return createExplorer(new SFMPathExpression.Literal(initialRoot), Set.of(initialRoot));
    }

    private SFMExplorerRepository.Explorer createExplorer(
            SFMPathExpression initialLocation,
            Set<SFMPath> initialRoots
    ) {
        SFMExplorerId id = new SFMExplorerId("explorer-" + nextExplorerId.getAndIncrement());
        SFMExplorerSession session = new SFMExplorerSession(id, initialLocation, initialRoots, selections);
        return new SFMExplorerRepository.Explorer(
                session,
                loader,
                this::pathIncompatibility,
                DEFAULT_PAGE_SIZE
        );
    }

    private Optional<String> pathIncompatibility(SFMPath path) {
        if (resolvers.find(path.scheme()).isEmpty()) {
            return Optional.of("No explorer resolver is registered for scheme `" + path.scheme() + "`");
        }
        if (path.scheme().equals("registry") && !isItemRegistryPath(path)) {
            return Optional.of("The current registry resolver accepts only `registry://minecraft/item/` entries");
        }
        return Optional.empty();
    }

    private static boolean isItemRegistryPath(SFMPath path) {
        if (path.equals(SFMItemRegistryExplorerResolver.ROOT)) return true;
        return path.kind() == SFMPath.Kind.REGISTRY
                && path.authority().equals("minecraft")
                && path.segments().size() >= 3
                && path.segments().get(0).equals("item");
    }

    private void authorizeRootIfRequired(SFMExplorerActionRequest.Operation operation) {
        if (operation instanceof SFMExplorerActionRequest.RootAdd add) authorizeRoot(add.path());
    }

    private void authorizeRoot(SFMPath path) {
        Optional<String> incompatibility = pathIncompatibility(path);
        if (incompatibility.isPresent()) throw new IllegalArgumentException(incompatibility.orElseThrow());
        if (path.scheme().equals("file")) filesystem.authorizeRoot(path.toNativePath());
    }

    private void preflightRoot(SFMPath path) {
        Optional<String> incompatibility = pathIncompatibility(path);
        if (incompatibility.isPresent()) throw new IllegalArgumentException(incompatibility.orElseThrow());
    }

    private Optional<String> locationAuthorityIncompatibility(SFMPath path) {
        return path.scheme().equals("file")
                ? filesystem.locationIncompatibility(path)
                : Optional.empty();
    }

    private void submitPanelAction(String canonicalCommand) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen origin = minecraft.screen;
        try {
            SFMClientActionExecutor.execute(
                    canonicalCommand,
                    SFMClientActionContext.create(origin, () -> minecraft.screen == origin),
                    component -> SFM.LOGGER.info("SFM_EXPLORER_ACTION_FEEDBACK {}", component.getString())
            );
        } catch (CommandSyntaxException | RuntimeException failure) {
            SFM.LOGGER.error("SFM_EXPLORER_ACTION_FAILED command={}", canonicalCommand, failure);
        }
    }

    private synchronized void focusExplorer(SFMExplorerId id) {
        if (closed || explorers.find(id).isEmpty()) return;
        if (!explorers.stateSnapshot().focused().equals(Optional.of(id))) explorers.focus(id);
    }

    private synchronized void closeExplorer(SFMExplorerId id) {
        panels.remove(id);
        explorers.unregister(id).ifPresent(explorer -> explorer.session().close());
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("Explorer runtime is closed");
    }

    private static ThreadFactory resolverThreadFactory() {
        AtomicLong ordinal = new AtomicLong(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "sfm-explorer-resolver-" + ordinal.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
