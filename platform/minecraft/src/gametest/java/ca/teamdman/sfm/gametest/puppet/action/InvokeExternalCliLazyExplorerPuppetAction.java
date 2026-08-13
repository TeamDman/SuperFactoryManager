package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMSelectionRevision;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerIoCounter;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerProjection;
import ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession;
import ca.teamdman.sfm.client.explorer.lazy.SFMGatedExplorerResolver;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMExternalCliPuppetProcess;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Complete real-CLI X-7 lazy-explorer journey, advanced only by client ticks. */
public final class InvokeExternalCliLazyExplorerPuppetAction implements SFMPuppetAction {
    private static final int COMMAND_TIMEOUT_TICKS = 1200;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private enum Phase {
        PREPARE_FIXTURE,
        LIST_EMPTY,
        CREATE_FIRST,
        WAIT_FIRST_ROOT_IO,
        EXPAND_PRIMARY_ROOT,
        WAIT_PRIMARY_ROOT,
        CAPTURE_SINGLE_ROOT,
        ADD_ITEM_ROOT,
        WAIT_ITEM_ROOT,
        CAPTURE_HETEROGENEOUS,
        EXPAND_DIRECTORY,
        WAIT_DIRECTORY,
        CAPTURE_DIRECTORY,
        MUTATE_FIXTURE,
        REFRESH_DIRECTORY,
        WAIT_GATED_REFRESH,
        CAPTURE_OLD_ROWS,
        RELEASE_REFRESH,
        WAIT_REFRESH_PUBLICATION,
        CAPTURE_NEW_ROWS,
        CREATE_SECOND,
        SET_ALL_SMALL_ICONS,
        WAIT_ALL_SMALL_ICONS,
        CAPTURE_ALL,
        PROVE_EXACT_MISS,
        LIST_FINAL,
        WRITE_ARTIFACTS,
        COMPLETE
    }

    private record Fixture(Path root, Path primary, Path directory, Path secondary) {
    }

    private Phase phase = Phase.PREPARE_FIXTURE;
    private CompletableFuture<Fixture> fixtureFuture;
    private CompletableFuture<Void> mutationFuture;
    private Fixture fixture;
    private SFMPath primaryPath;
    private SFMPath directoryPath;
    private SFMPath secondaryPath;
    private String firstExplorerId;
    private String secondExplorerId;
    private SFMExternalCliPuppetProcess process;
    private int processTicks;
    private final JsonArray commandEvidence = new JsonArray();
    private final JsonArray ioPhaseEvidence = new JsonArray();
    private long ioPhaseCursor;
    private long ioDroppedAtCursor;
    private SFMGatedExplorerResolver.Gate refreshGate;
    private long refreshBeforeRevision;
    private long refreshDuringRevision;
    private long refreshAfterRevision;

    @Override
    public String description() {
        return "prove the selection-backed lazy explorer through the real external sfm.exe";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        switch (phase) {
            case PREPARE_FIXTURE -> prepareFixture();
            case LIST_EMPTY -> listEmpty();
            case CREATE_FIRST -> createFirst();
            case WAIT_FIRST_ROOT_IO -> waitFirstRootIo();
            case EXPAND_PRIMARY_ROOT -> expandPrimaryRoot();
            case WAIT_PRIMARY_ROOT -> waitPrimaryRoot();
            case CAPTURE_SINGLE_ROOT -> capture(runtime, "01-single-root-hoisted",
                    "One materialized filesystem root is hoisted to its immediate children.");
            case ADD_ITEM_ROOT -> addItemRoot();
            case WAIT_ITEM_ROOT -> waitItemRoot();
            case CAPTURE_HETEROGENEOUS -> capture(runtime, "02-heterogeneous-roots",
                    "The same explorer now presents filesystem and Minecraft item-registry roots.");
            case EXPAND_DIRECTORY -> expandDirectory();
            case WAIT_DIRECTORY -> waitDirectory();
            case CAPTURE_DIRECTORY -> capture(runtime, "03-directory-expanded",
                    "Only the selected directory's immediate children were requested.");
            case MUTATE_FIXTURE -> mutateFixture();
            case REFRESH_DIRECTORY -> refreshDirectory();
            case WAIT_GATED_REFRESH -> waitGatedRefresh();
            case CAPTURE_OLD_ROWS -> capture(runtime, "04-refresh-pending-old-rows",
                    "Resolver work completed, but the old relation remains visible before publication.");
            case RELEASE_REFRESH -> releaseRefresh();
            case WAIT_REFRESH_PUBLICATION -> waitRefreshPublication();
            case CAPTURE_NEW_ROWS -> capture(runtime, "05-refresh-published-new-rows",
                    "The completed refresh replaced the requested parent relation atomically.");
            case CREATE_SECOND -> createSecond();
            case SET_ALL_SMALL_ICONS -> setAllSmallIcons();
            case WAIT_ALL_SMALL_ICONS -> waitAllSmallIcons();
            case CAPTURE_ALL -> capture(runtime, "06-all-explorers-small-icons",
                    "One set-valued `all` command changed both explorer panels.");
            case PROVE_EXACT_MISS -> proveExactMiss();
            case LIST_FINAL -> listFinal();
            case WRITE_ARTIFACTS -> writeArtifacts(runtime);
            case COMPLETE -> {
                return true;
            }
        }
        return phase == Phase.COMPLETE;
    }

    private void prepareFixture() {
        if (fixtureFuture == null) {
            Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
            Path root = gameDirectory.resolve("puppet-fixtures")
                    .resolve("external-cli-lazy-explorer-" + ProcessHandle.current().pid())
                    .resolve(Long.toUnsignedString(System.nanoTime()));
            fixtureFuture = CompletableFuture.supplyAsync(() -> createFixture(root));
            return;
        }
        if (!fixtureFuture.isDone()) return;
        fixture = fixtureFuture.join();
        primaryPath = SFMPath.fromNative(fixture.primary());
        directoryPath = SFMPath.fromNative(fixture.directory());
        secondaryPath = SFMPath.fromNative(fixture.secondary());
        SFMExplorerIoCounter.Snapshot io = SFMExplorerRuntime.get().filesystemIo().snapshot();
        ioPhaseCursor = io.latestSequence();
        ioDroppedAtCursor = io.eventsDropped();
        phase = Phase.LIST_EMPTY;
    }

    private void listEmpty() {
        JsonObject result = command("list-empty", 0, "explorer", "list", "all");
        if (result == null) return;
        require(targets(result).size() == 0, "The puppet must start with no live explorer");
        phase = Phase.CREATE_FIRST;
    }

    private void createFirst() {
        JsonObject result = command(
                "create-first",
                0,
                "explorer", "root", "add", "focused", primaryPath.canonical(),
                "--if-no-match", "open-new"
        );
        if (result == null) return;
        JsonArray targets = targets(result);
        require(targets.size() == 1, "Opening the first explorer must return one created target");
        firstExplorerId = targets.get(0).getAsJsonObject().get("explorer_id").getAsString();
        require(explorerCount() == 1, "Exactly one explorer must exist after OPEN_NEW");
        phase = Phase.WAIT_FIRST_ROOT_IO;
    }

    private void waitFirstRootIo() {
        SFMExplorerIoCounter.Snapshot io = SFMExplorerRuntime.get().filesystemIo().snapshot();
        List<SFMExplorerIoCounter.Event> events = io.eventsAfter(ioPhaseCursor);
        if (events.stream().noneMatch(event -> event.operation() == SFMExplorerIoCounter.OperationKind.METADATA_READ
                && event.canonicalPath().equals(Optional.of(primaryPath.canonical())))) return;
        require(events.stream().allMatch(event ->
                        event.operation() == SFMExplorerIoCounter.OperationKind.METADATA_READ
                                && event.canonicalPath().equals(Optional.of(primaryPath.canonical()))),
                "Opening a root must read only that root's metadata and must not enumerate descendants");
        recordIoPhase("open-primary-root", io);
        phase = Phase.EXPAND_PRIMARY_ROOT;
    }

    private void expandPrimaryRoot() {
        JsonObject result = command(
                "expand-primary-root",
                0,
                "explorer", "node", "expand", exact(firstExplorerId), primaryPath.canonical()
        );
        if (result == null) return;
        phase = Phase.WAIT_PRIMARY_ROOT;
    }

    private void waitPrimaryRoot() {
        Optional<SFMExplorerRuntime.ExplorerEvidence> explorer = explorer(firstExplorerId);
        if (explorer.isEmpty() || !explorer.get().activeRequests().isEmpty()) return;
        List<String> visible = visiblePaths(explorer.get());
        if (!visible.contains(directoryPath.canonical())
                || visible.contains(primaryPath.canonical())) return;
        SFMExplorerIoCounter.Snapshot io = SFMExplorerRuntime.get().filesystemIo().snapshot();
        requireEnumeratedOnly(io.eventsAfter(ioPhaseCursor), primaryPath,
                "Expanding the primary root must enumerate only that parent");
        recordIoPhase("expand-primary-root", io);
        phase = Phase.CAPTURE_SINGLE_ROOT;
    }

    private void addItemRoot() {
        JsonObject result = command(
                "add-item-root",
                0,
                "explorer", "root", "add", exact(firstExplorerId), "registry://minecraft/item/",
                "--if-no-match", "fail"
        );
        if (result == null) return;
        phase = Phase.WAIT_ITEM_ROOT;
    }

    private void waitItemRoot() {
        Optional<SFMExplorerRuntime.ExplorerEvidence> explorer = explorer(firstExplorerId);
        if (explorer.isEmpty() || explorer.get().session().roots().size() != 2) return;
        List<String> visible = visiblePaths(explorer.get());
        if (!visible.contains(primaryPath.canonical())
                || !visible.contains("registry://minecraft/item/")) return;
        require(explorer.get().session().location().canonical().startsWith("members("),
                "A second heterogeneous root must transition to a selection-backed location");
        phase = Phase.CAPTURE_HETEROGENEOUS;
    }

    private void expandDirectory() {
        JsonObject result = command(
                "expand-directory",
                0,
                "explorer", "node", "expand", exact(firstExplorerId), directoryPath.canonical()
        );
        if (result == null) return;
        phase = Phase.WAIT_DIRECTORY;
    }

    private void waitDirectory() {
        Optional<SFMExplorerRuntime.ExplorerEvidence> explorer = explorer(firstExplorerId);
        if (explorer.isEmpty() || !explorer.get().activeRequests().isEmpty()) return;
        List<String> visible = visiblePaths(explorer.get());
        if (visible.stream().noneMatch(path -> path.endsWith("/before.txt"))) return;
        require(visible.stream().noneMatch(path -> path.endsWith("/must-not-be-observed.txt")),
                "Expanding one directory must not prefetch nested descendants");
        require(SFMExplorerRuntime.get().filesystemIo().snapshot().directoryEnumerations() == 2,
                "Only the primary root and selected directory should have been enumerated");
        SFMExplorerIoCounter.Snapshot io = SFMExplorerRuntime.get().filesystemIo().snapshot();
        requireEnumeratedOnly(io.eventsAfter(ioPhaseCursor), directoryPath,
                "Expanding the selected directory must enumerate only that parent");
        require(io.eventsAfter(ioPhaseCursor).stream().noneMatch(event ->
                        event.canonicalPath().orElse("").endsWith("/must-not-be-observed.txt")),
                "Expanding the selected directory must not observe nested descendants");
        recordIoPhase("expand-selected-directory", io);
        phase = Phase.CAPTURE_DIRECTORY;
    }

    private void mutateFixture() {
        if (mutationFuture == null) {
            mutationFuture = CompletableFuture.runAsync(() -> {
                try {
                    Files.deleteIfExists(fixture.directory().resolve("before.txt"));
                    Files.writeString(
                            fixture.directory().resolve("after.txt"),
                            "after\n",
                            StandardCharsets.UTF_8
                    );
                } catch (IOException failure) {
                    throw new IllegalStateException("Could not mutate lazy-explorer puppet fixture", failure);
                }
            });
            return;
        }
        if (!mutationFuture.isDone()) return;
        mutationFuture.join();
        refreshBeforeRevision = SFMExplorerRuntime.get().relations().snapshot().relation().id();
        refreshGate = SFMExplorerRuntime.get().armFilesystemPublicationGate(directoryPath);
        phase = Phase.REFRESH_DIRECTORY;
    }

    private void refreshDirectory() {
        JsonObject result = command(
                "refresh-directory",
                0,
                "explorer", "node", "refresh", exact(firstExplorerId), directoryPath.canonical()
        );
        if (result == null) return;
        phase = Phase.WAIT_GATED_REFRESH;
    }

    private void waitGatedRefresh() {
        if (!refreshGate.captured().isDone()) return;
        Optional<SFMExplorerRuntime.ExplorerEvidence> explorer = explorer(firstExplorerId);
        if (explorer.isEmpty() || explorer.get().activeRequests().size() != 1) return;
        refreshDuringRevision = SFMExplorerRuntime.get().relations().snapshot().relation().id();
        require(refreshDuringRevision == refreshBeforeRevision,
                "A gated refresh must not publish an intermediate relation revision");
        List<String> visible = visiblePaths(explorer.get());
        require(visible.stream().anyMatch(path -> path.endsWith("/before.txt")),
                "Old rows must remain visible while refreshed rows are gated");
        require(visible.stream().noneMatch(path -> path.endsWith("/after.txt")),
                "New rows must not leak before atomic publication");
        SFMExplorerIoCounter.Snapshot io = SFMExplorerRuntime.get().filesystemIo().snapshot();
        requireEnumeratedOnly(io.eventsAfter(ioPhaseCursor), directoryPath,
                "Refreshing the selected directory must enumerate only that parent");
        recordIoPhase("refresh-selected-directory", io);
        phase = Phase.CAPTURE_OLD_ROWS;
    }

    private void releaseRefresh() {
        require(refreshGate.release(), "The refresh publication gate must release exactly once");
        phase = Phase.WAIT_REFRESH_PUBLICATION;
    }

    private void waitRefreshPublication() {
        Optional<SFMExplorerRuntime.ExplorerEvidence> explorer = explorer(firstExplorerId);
        if (explorer.isEmpty() || !explorer.get().activeRequests().isEmpty()) return;
        List<String> visible = visiblePaths(explorer.get());
        if (visible.stream().noneMatch(path -> path.endsWith("/after.txt"))) return;
        require(visible.stream().noneMatch(path -> path.endsWith("/before.txt")),
                "The old row must disappear in the published replacement");
        refreshAfterRevision = SFMExplorerRuntime.get().relations().snapshot().relation().id();
        require(refreshAfterRevision == refreshBeforeRevision + 1,
                "Atomic refresh must advance the child relation exactly once");
        phase = Phase.CAPTURE_NEW_ROWS;
    }

    private void createSecond() {
        JsonObject result = command(
                "create-second",
                0,
                "explorer", "root", "add", "difference(all,all)", secondaryPath.canonical(),
                "--if-no-match", "open-new"
        );
        if (result == null) return;
        JsonArray targets = targets(result);
        require(targets.size() == 1, "Opening the second explorer must return one created target");
        secondExplorerId = targets.get(0).getAsJsonObject().get("explorer_id").getAsString();
        require(!secondExplorerId.equals(firstExplorerId), "Explorer ids must be stable and distinct");
        require(explorerCount() == 2, "Exactly two explorers must exist");
        phase = Phase.SET_ALL_SMALL_ICONS;
    }

    private void setAllSmallIcons() {
        JsonObject result = command(
                "set-all-small-icons",
                0,
                "explorer", "view", "set", "all", "sfm:small_icons"
        );
        if (result == null) return;
        require(targets(result).size() == 2, "The all selector must capture both explorers");
        phase = Phase.WAIT_ALL_SMALL_ICONS;
    }

    private void waitAllSmallIcons() {
        SFMExplorerRuntime.Evidence evidence = SFMExplorerRuntime.get().evidence();
        if (evidence.explorers().size() != 2) return;
        if (evidence.explorers().stream().anyMatch(explorer ->
                explorer.session().settings().view() != SFMExplorerProjection.View.SMALL_ICONS)) return;
        require(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace
                        && workspace.visiblePanelEntries().size() == 2,
                "The two OPEN_NEW explorers must be visible in separate panel slots");
        phase = Phase.CAPTURE_ALL;
    }

    private void proveExactMiss() {
        JsonObject result = command(
                "exact-miss",
                2,
                "explorer", "describe", "id(puppet-missing-explorer)"
        );
        if (result == null) return;
        String normalized = result.get("status").getAsString()
                .toLowerCase(java.util.Locale.ROOT)
                .replace("-", "")
                .replace("_", "");
        require(normalized.equals("nomatch"), "Exact missing explorer must return typed NO_MATCH");
        require(explorerCount() == 2, "An exact miss must not create a replacement explorer");
        phase = Phase.LIST_FINAL;
    }

    private void listFinal() {
        JsonObject result = command("list-final", 0, "explorer", "list", "all");
        if (result == null) return;
        require(targets(result).size() == 2, "The final CLI listing must contain exactly two explorers");
        phase = Phase.WRITE_ARTIFACTS;
    }

    private void writeArtifacts(ISFMGamePuppetRuntime runtime) {
        SFMExplorerRuntime authority = SFMExplorerRuntime.get();
        SFMExplorerRuntime.Evidence evidence = authority.evidence();
        runtime.writeArtifact("cli-journey", SFMGamePuppetArtifactFormat.JSON, pretty(object(
                "schema", "sfm.puppet.external-cli-lazy-explorer.cli/1",
                "commands", commandEvidence
        )));
        runtime.writeArtifact("request-correlations", SFMGamePuppetArtifactFormat.JSON,
                pretty(requestCorrelations()));
        runtime.writeArtifact("explorer-states", SFMGamePuppetArtifactFormat.JSON, pretty(explorerEvidence(evidence)));
        runtime.writeArtifact("selection-revisions", SFMGamePuppetArtifactFormat.JSON,
                pretty(selectionEvidence(authority)));
        runtime.writeArtifact("child-relation-refresh", SFMGamePuppetArtifactFormat.JSON, pretty(object(
                "schema", "sfm.puppet.external-cli-lazy-explorer.relation/1",
                "before_revision", refreshBeforeRevision,
                "during_revision", refreshDuringRevision,
                "after_revision", refreshAfterRevision,
                "status_generation", authority.relations().snapshot().statusGeneration(),
                "edge_count", authority.relations().snapshot().relation().edges().size()
        )));
        var io = authority.filesystemIo().snapshot();
        runtime.writeArtifact("resolver-io", SFMGamePuppetArtifactFormat.JSON, pretty(object(
                "schema", "sfm.puppet.external-cli-lazy-explorer.resolver-io/2",
                "metadata_reads", io.metadataReads(),
                "directory_enumerations", io.directoryEnumerations(),
                "entries_observed", io.entriesObserved(),
                "containment_rejections", io.containmentRejections(),
                "render_thread_violations", io.renderThreadViolations(),
                "observed_threads", GSON.toJsonTree(io.observedThreads()),
                "event_capacity", io.eventCapacity(),
                "events_dropped", io.eventsDropped(),
                "latest_sequence", io.latestSequence(),
                "events", ioEvents(io.events()),
                "phases", ioPhaseEvidence
        )));
        require(io.renderThreadViolations() == 0, "Explorer resolver I/O must never run on the render thread");
        require(io.eventsDropped() == ioDroppedAtCursor,
                "The resolver I/O evidence buffer must retain every event produced by the puppet journey");
        JsonArray timings = new JsonArray();
        commandEvidence.forEach(element -> {
            JsonObject command = element.getAsJsonObject();
            JsonObject timing = new JsonObject();
            timing.addProperty("label", command.get("label").getAsString());
            timing.addProperty("duration_ms", command.get("duration_ms").getAsLong());
            timings.add(timing);
        });
        runtime.writeArtifact("timings", SFMGamePuppetArtifactFormat.JSON, pretty(object(
                "schema", "sfm.puppet.external-cli-lazy-explorer.timings/1",
                "commands", timings
        )));
        phase = Phase.COMPLETE;
    }

    private void capture(ISFMGamePuppetRuntime runtime, String name, String caption) {
        if (!runtime.capture(name, Component.literal(caption))) return;
        phase = Phase.values()[phase.ordinal() + 1];
    }

    private JsonObject command(String label, int expectedExitCode, String... arguments) {
        if (process == null) {
            ArrayList<String> argv = new ArrayList<>();
            String executable = System.getProperty(
                    InvokeExternalCliSizeDisplayPuppetAction.EXECUTABLE_PROPERTY,
                    "sfm.exe"
            ).trim();
            if (executable.isEmpty()) throw new IllegalStateException("SFM CLI executable property is empty");
            argv.add(executable);
            argv.addAll(List.of(arguments));
            argv.add("--instance-pid");
            argv.add(Long.toString(ProcessHandle.current().pid()));
            argv.add("--output-format");
            argv.add("json");
            process = SFMExternalCliPuppetProcess.start(argv);
            processTicks = 0;
            SFM.LOGGER.info("SFM_GAME_PUPPET_EXTERNAL_EXPLORER_CLI_STARTED label={} command={}",
                    label, String.join(" ", argv));
            return null;
        }
        processTicks++;
        if (processTicks > COMMAND_TIMEOUT_TICKS) {
            process.close();
            throw new IllegalStateException("Timed out running external SFM CLI command " + label);
        }
        Optional<SFMExternalCliPuppetProcess.Completed> completed = process.poll();
        if (completed.isEmpty()) return null;
        SFMExternalCliPuppetProcess.Completed result = completed.orElseThrow();
        process = null;
        require(result.exitCode() == expectedExitCode,
                label + " exited " + result.exitCode() + " instead of " + expectedExitCode
                        + "; stderr=" + result.stderr());
        JsonObject parsed;
        try {
            parsed = JsonParser.parseString(result.stdout()).getAsJsonObject();
        } catch (RuntimeException failure) {
            throw new IllegalStateException(label + " did not emit one JSON object: " + result.stdout(), failure);
        }
        JsonObject evidence = new JsonObject();
        evidence.addProperty("label", label);
        evidence.add("argv", GSON.toJsonTree(result.argv()));
        evidence.addProperty("process_id", result.processId());
        evidence.addProperty("exit_code", result.exitCode());
        evidence.addProperty("duration_ms", result.durationMillis());
        evidence.add("stdout", parsed.deepCopy());
        evidence.addProperty("stderr", result.stderr());
        commandEvidence.add(evidence);
        return parsed;
    }

    private static Fixture createFixture(Path root) {
        try {
            Path primary = root.resolve("primary");
            Path directory = primary.resolve("directory");
            Path nested = directory.resolve("nested");
            Path secondary = root.resolve("secondary");
            Files.createDirectories(nested);
            Files.createDirectories(secondary);
            Files.writeString(primary.resolve("stable.txt"), "stable\n", StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("before.txt"), "before\n", StandardCharsets.UTF_8);
            Files.writeString(nested.resolve("must-not-be-observed.txt"), "nested\n", StandardCharsets.UTF_8);
            Files.writeString(secondary.resolve("second.txt"), "second\n", StandardCharsets.UTF_8);
            return new Fixture(root, primary, directory, secondary);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not create lazy-explorer puppet fixture", failure);
        }
    }

    private static JsonArray targets(JsonObject result) {
        JsonArray targets = result.getAsJsonArray("targets");
        if (targets == null) throw new IllegalStateException("Explorer CLI result omitted targets: " + result);
        return targets;
    }

    private JsonObject requestCorrelations() {
        JsonObject root = object(
                "schema", "sfm.puppet.external-cli-lazy-explorer.request-correlations/1"
        );
        JsonArray correlations = new JsonArray();
        for (JsonElement element : commandEvidence) {
            JsonObject command = element.getAsJsonObject();
            JsonObject stdout = command.getAsJsonObject("stdout");
            if (stdout == null || !stdout.has("request_id")) continue;
            String externalRequestId = stdout.get("request_id").getAsString();
            JsonArray responseTargets = stdout.getAsJsonArray("targets");
            if (responseTargets == null || responseTargets.isEmpty()) {
                correlations.add(object(
                        "label", command.get("label").getAsString(),
                        "external_request_id", externalRequestId,
                        "explorer_id", "",
                        "selection_revision", 0,
                        "child_relation_request_id", 0
                ));
                continue;
            }
            for (JsonElement targetElement : responseTargets) {
                JsonObject target = targetElement.getAsJsonObject();
                correlations.add(object(
                        "label", command.get("label").getAsString(),
                        "external_request_id", externalRequestId,
                        "explorer_id", target.get("explorer_id").getAsString(),
                        "selection_revision", target.get("selection_revision_present").getAsBoolean()
                                ? target.get("selection_revision").getAsLong()
                                : 0,
                        "child_relation_request_id", target.get("child_relation_request_present").getAsBoolean()
                                ? target.get("child_relation_request_id").getAsLong()
                                : 0
                ));
            }
        }
        root.add("correlations", correlations);
        require(hasPositiveCorrelation(correlations, "refresh-directory", "child_relation_request_id"),
                "The refresh CLI request must correlate to its child-relation request id");
        require(hasPositiveCorrelation(correlations, "add-item-root", "selection_revision"),
                "The root-add CLI request must correlate to its resulting selection revision");
        return root;
    }

    private static boolean hasPositiveCorrelation(JsonArray correlations, String label, String field) {
        for (JsonElement element : correlations) {
            JsonObject correlation = element.getAsJsonObject();
            if (correlation.get("label").getAsString().equals(label)
                    && correlation.get(field).getAsLong() > 0) return true;
        }
        return false;
    }

    private static String exact(String explorerId) {
        return SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, explorerId).canonical();
    }

    private static int explorerCount() {
        return SFMExplorerRuntime.get().evidence().explorers().size();
    }

    private static Optional<SFMExplorerRuntime.ExplorerEvidence> explorer(String id) {
        return SFMExplorerRuntime.get().evidence().explorers().stream()
                .filter(explorer -> explorer.session().id().value().equals(id))
                .findFirst();
    }

    private static List<String> visiblePaths(SFMExplorerRuntime.ExplorerEvidence explorer) {
        return explorer.projection().rows().stream().map(row -> row.path().canonical()).toList();
    }

    private static JsonObject explorerEvidence(SFMExplorerRuntime.Evidence evidence) {
        JsonObject root = object(
                "schema", "sfm.puppet.external-cli-lazy-explorer.explorers/1",
                "explorer_registry_generation", evidence.explorerRegistryGeneration(),
                "selection_repository_generation", evidence.selectionRepositoryGeneration(),
                "child_relation_revision", evidence.childRelationRevision(),
                "child_relation_status_generation", evidence.childRelationStatusGeneration()
        );
        JsonArray explorers = new JsonArray();
        for (SFMExplorerRuntime.ExplorerEvidence explorer : evidence.explorers()) {
            JsonObject item = object(
                    "explorer_id", explorer.session().id().value(),
                    "revision", explorer.session().revision(),
                    "focused", explorer.focused(),
                    "location", explorer.session().location().canonical(),
                    "roots", GSON.toJsonTree(explorer.session().roots().stream().map(SFMPath::canonical).toList()),
                    "expanded", GSON.toJsonTree(explorer.session().expanded().stream().map(SFMPath::canonical).toList()),
                    "view", viewId(explorer.session().settings().view()),
                    "sort", "sfm:" + explorer.session().settings().sort().name().toLowerCase(java.util.Locale.ROOT),
                    "group", "sfm:" + explorer.session().settings().group().name().toLowerCase(java.util.Locale.ROOT),
                    "hoist", explorer.session().settings().hoist().name().toLowerCase(java.util.Locale.ROOT)
                            .replace('_', '-'),
                    "visible_paths", GSON.toJsonTree(visiblePaths(explorer)),
                    "active_requests", requestEvidence(explorer.activeRequests()),
                    "recent_requests", requestEvidence(explorer.recentRequests())
            );
            explorers.add(item);
        }
        root.add("explorers", explorers);
        return root;
    }

    private static JsonArray requestEvidence(List<SFMExplorerSession.RequestObservation> requests) {
        JsonArray answer = new JsonArray();
        for (SFMExplorerSession.RequestObservation request : requests) {
            answer.add(object(
                    "request_id", request.evidence().relationRequestId(),
                    "mode", request.evidence().mode().name().toLowerCase(java.util.Locale.ROOT),
                    "parent", request.evidence().parent().canonical(),
                    "resolver_scheme", request.evidence().resolverScheme(),
                    "resolver_generation", request.evidence().resolverGeneration(),
                    "continuation", request.evidence().continuation().orElse(""),
                    "page_size", request.evidence().pageSize(),
                    "started_at_epoch_millis", request.startedAtEpochMillis(),
                    "completed_at_epoch_millis", request.completedAtEpochMillis().orElse(0L),
                    "disposition", request.disposition().map(value -> value.name().toLowerCase(java.util.Locale.ROOT))
                            .orElse("pending"),
                    "diagnostic", request.diagnostic().orElse("")
            ));
        }
        return answer;
    }

    private static JsonObject selectionEvidence(SFMExplorerRuntime runtime) {
        var snapshot = runtime.selections().stateSnapshot();
        JsonObject root = object(
                "schema", "sfm.puppet.external-cli-lazy-explorer.selections/1",
                "repository_generation", snapshot.generation()
        );
        JsonArray selections = new JsonArray();
        snapshot.selections().values().forEach(selection -> selections.add(object(
                "selection_id", selection.id().value(),
                "name", selection.name().orElse(""),
                "head_revision", selection.headRevisionId()
        )));
        JsonArray revisions = new JsonArray();
        for (SFMSelectionRevision revision : snapshot.revisions().values()) {
            revisions.add(object(
                    "revision", revision.id(),
                    "selection_id", revision.selectionId().value(),
                    "parents", GSON.toJsonTree(revision.parentRevisionIds()),
                    "members", GSON.toJsonTree(revision.members().stream().map(SFMPath::canonical).toList()),
                    "operation", revision.operation().kind().name().toLowerCase(java.util.Locale.ROOT),
                    "actor", revision.actor(),
                    "request_id", revision.requestId()
            ));
        }
        root.add("selections", selections);
        root.add("revisions", revisions);
        return root;
    }

    private void recordIoPhase(String label, SFMExplorerIoCounter.Snapshot snapshot) {
        require(snapshot.eventsDropped() == ioDroppedAtCursor,
                "Resolver I/O events were dropped before phase " + label);
        List<SFMExplorerIoCounter.Event> events = snapshot.eventsAfter(ioPhaseCursor);
        if (snapshot.latestSequence() > ioPhaseCursor) {
            require(!events.isEmpty() && events.get(0).sequence() == ioPhaseCursor + 1,
                    "Resolver I/O evidence has a sequence gap before phase " + label);
        }
        ioPhaseEvidence.add(object(
                "label", label,
                "after_sequence", ioPhaseCursor,
                "through_sequence", snapshot.latestSequence(),
                "metadata_reads", snapshot.metadataReads(),
                "directory_enumerations", snapshot.directoryEnumerations(),
                "entries_observed", snapshot.entriesObserved(),
                "containment_rejections", snapshot.containmentRejections(),
                "render_thread_violations", snapshot.renderThreadViolations(),
                "events", ioEvents(events)
        ));
        ioPhaseCursor = snapshot.latestSequence();
        ioDroppedAtCursor = snapshot.eventsDropped();
    }

    private static void requireEnumeratedOnly(
            List<SFMExplorerIoCounter.Event> events,
            SFMPath expectedParent,
            String message
    ) {
        List<String> enumerated = events.stream()
                .filter(event -> event.operation() == SFMExplorerIoCounter.OperationKind.DIRECTORY_ENUMERATION)
                .flatMap(event -> event.canonicalPath().stream())
                .toList();
        require(enumerated.equals(List.of(expectedParent.canonical())), message + ": " + enumerated);
        Path expectedNative = expectedParent.toNativePath().toAbsolutePath().normalize();
        require(events.stream()
                        .filter(event -> event.operation() == SFMExplorerIoCounter.OperationKind.ENTRY_OBSERVED)
                        .flatMap(event -> event.canonicalPath().stream())
                        .map(SFMPath::parse)
                        .map(SFMPath::toNativePath)
                        .map(path -> path.toAbsolutePath().normalize().getParent())
                        .allMatch(expectedNative::equals),
                message + ": an observed entry was not an immediate child");
    }

    private static JsonArray ioEvents(List<SFMExplorerIoCounter.Event> events) {
        JsonArray answer = new JsonArray();
        for (SFMExplorerIoCounter.Event event : events) {
            JsonObject item = object(
                    "sequence", event.sequence(),
                    "operation", event.operation().id(),
                    "worker_thread", event.workerThread()
            );
            item.add("canonical_path", event.canonicalPath()
                    .<JsonElement>map(value -> GSON.toJsonTree(value))
                    .orElse(JsonNull.INSTANCE));
            answer.add(item);
        }
        return answer;
    }

    private static JsonObject object(Object... values) {
        if (values.length % 2 != 0) throw new IllegalArgumentException("JSON object values must be key/value pairs");
        JsonObject answer = new JsonObject();
        for (int index = 0; index < values.length; index += 2) {
            String key = Objects.toString(values[index]);
            Object value = values[index + 1];
            if (value instanceof JsonElement element) answer.add(key, element);
            else if (value instanceof Boolean bool) answer.addProperty(key, bool);
            else if (value instanceof Number number) answer.addProperty(key, number);
            else answer.addProperty(key, Objects.toString(value, ""));
        }
        return answer;
    }

    private static String pretty(JsonObject value) {
        return GSON.toJson(value);
    }

    private static String viewId(SFMExplorerProjection.View view) {
        return view == SFMExplorerProjection.View.LIST ? "sfm:list" : "sfm:small_icons";
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
