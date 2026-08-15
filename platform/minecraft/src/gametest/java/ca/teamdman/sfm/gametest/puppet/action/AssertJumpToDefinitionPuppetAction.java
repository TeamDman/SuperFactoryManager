package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.symbol.SFMSymbolServerSupervisor;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Drives the real F12 binding and records exact addressed-target/focus evidence. */
public final class AssertJumpToDefinitionPuppetAction implements SFMPuppetAction {
    private static final int MAXIMUM_INDEX_OUTPUT_BYTES = 256 * 1024;
    // The action may include a bounded dependency preflight, a 10-second
    // worker handshake, and a 30-second cold branch query before presentation.
    private static final int MAXIMUM_TOTAL_TICKS = SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 6;
    private static final int MAXIMUM_INDEX_PROBE_TICKS = SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS;

    private enum State {
        PROBE_INDEX,
        POSITION_SOURCE,
        INVOKE_F12,
        WAIT_FOR_TARGET,
        CAPTURE,
        COMPLETE
    }

    private record IndexProbe(
            boolean required,
            boolean ready,
            String selector,
            String status,
            String completeness,
            String expectedIdentity,
            int matchedDefinitions,
            int exitCode,
            String reason
    ) {
        private IndexProbe {
            Objects.requireNonNull(selector, "selector");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(completeness, "completeness");
            Objects.requireNonNull(expectedIdentity, "expectedIdentity");
            Objects.requireNonNull(reason, "reason");
        }

        static IndexProbe notRequired() {
            return new IndexProbe(false, true, "", "not-required", "not-applicable", "", 0, 0,
                    "The SFM-owned definition uses the live branch source index");
        }

        JsonObject json() {
            JsonObject result = new JsonObject();
            result.addProperty("required", required);
            result.addProperty("ready", ready);
            if (!selector.isEmpty()) result.addProperty("selector", selector);
            result.addProperty("status", status);
            result.addProperty("completeness", completeness);
            if (!expectedIdentity.isEmpty()) result.addProperty("expected_identity", expectedIdentity);
            result.addProperty("matched_definitions", matchedDefinitions);
            result.addProperty("exit_code", exitCode);
            result.addProperty("reason", reason);
            return result;
        }
    }

    private record Target(
            SFMSourcePuppetProbe.EditorHandle editor,
            SFMTextDocumentSnapshot document,
            SFMTextDocumentRange range
    ) {
    }

    private final Path sourceFile;
    private final SFMPath sourceAddress;
    private final String symbol;
    private final int occurrence;
    private final Optional<SFMPath> expectedTargetAddress;
    private final String artifactName;
    private final Optional<String> dependencySelector;
    private final Optional<String> captureName;
    private final Optional<Component> captureCaption;
    private State state;
    private Process indexProcess;
    private CompletableFuture<byte[]> indexOutput;
    private int indexProbeTicks;
    private int totalTicks;
    private int settleTicks;
    private IndexProbe indexProbe;
    private SFMWorkspacePanelId sourcePanelId;
    private Set<SFMWorkspacePanelId> panelsBefore = Set.of();
    private SFMTextDocumentRange sourceRange;
    private String sourceDocumentHash;
    private long queryStartedNanos;

    /** Required SFM-owned definition leg with an exact expected target file. */
    public AssertJumpToDefinitionPuppetAction(
            Path sourceFile,
            String symbol,
            int occurrence,
            Path expectedTargetFile,
            String artifactName
    ) {
        this(sourceFile, symbol, occurrence, Optional.of(expectedTargetFile), artifactName,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Optional dependency leg, executed only when the pinned index gives one exact preflight result. */
    public AssertJumpToDefinitionPuppetAction(
            Path sourceFile,
            String symbol,
            int occurrence,
            String dependencySelector,
            String artifactName,
            String captureName,
            Component captureCaption
    ) {
        this(sourceFile, symbol, occurrence, Optional.empty(), artifactName,
                Optional.of(requireNonBlank(dependencySelector, "dependencySelector")),
                Optional.of(requireNonBlank(captureName, "captureName")),
                Optional.of(Objects.requireNonNull(captureCaption, "captureCaption").copy()));
    }

    private AssertJumpToDefinitionPuppetAction(
            Path sourceFile,
            String symbol,
            int occurrence,
            Optional<Path> expectedTargetFile,
            String artifactName,
            Optional<String> dependencySelector,
            Optional<String> captureName,
            Optional<Component> captureCaption
    ) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        sourceAddress = SFMPath.fromNative(this.sourceFile);
        this.symbol = requireNonBlank(symbol, "symbol");
        if (occurrence < -1) throw new IllegalArgumentException("Occurrence must be -1 or non-negative");
        this.occurrence = occurrence;
        Optional<Path> normalizedExpectedTarget = Objects.requireNonNull(expectedTargetFile, "expectedTargetFile")
                .map(path -> path.toAbsolutePath().normalize());
        expectedTargetAddress = normalizedExpectedTarget.map(SFMPath::fromNative);
        this.artifactName = requireNonBlank(artifactName, "artifactName");
        this.dependencySelector = Objects.requireNonNull(dependencySelector, "dependencySelector");
        this.captureName = Objects.requireNonNull(captureName, "captureName");
        this.captureCaption = Objects.requireNonNull(captureCaption, "captureCaption");
        if (this.captureName.isPresent() != this.captureCaption.isPresent()) {
            throw new IllegalArgumentException("Capture name and caption must either both be present or both absent");
        }
        state = this.dependencySelector.isPresent() ? State.PROBE_INDEX : State.POSITION_SOURCE;
        indexProbe = this.dependencySelector.isPresent() ? null : IndexProbe.notRequired();
    }

    @Override
    public String description() {
        return "invoke F12 on " + symbol + " and assert addressed definition navigation";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++totalTicks > MAXIMUM_TOTAL_TICKS) {
            if (indexProcess != null && indexProcess.isAlive()) indexProcess.destroyForcibly();
            throw new IllegalStateException("Timed out proving F12 definition navigation for " + symbol
                    + "; " + diagnosticState());
        }
        return switch (state) {
            case PROBE_INDEX -> probeIndex(runtime);
            case POSITION_SOURCE -> positionSource();
            case INVOKE_F12 -> invokeF12(runtime);
            case WAIT_FOR_TARGET -> waitForTarget(runtime);
            case CAPTURE -> capture(runtime);
            case COMPLETE -> true;
        };
    }

    private boolean probeIndex(ISFMGamePuppetRuntime runtime) {
        if (indexProcess == null) {
            try {
                ProcessBuilder builder = new ProcessBuilder(
                        SFMSymbolServerSupervisor.DEFAULT_EXECUTABLE,
                        "symbol", "show-definition", dependencySelector.orElseThrow(),
                        "--branch", "1.19.2",
                        "--output-format", "json"
                );
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process process = builder.start();
                indexProcess = process;
                indexOutput = CompletableFuture.supplyAsync(() -> {
                    try (InputStream stdout = process.getInputStream()) {
                        return stdout.readNBytes(MAXIMUM_INDEX_OUTPUT_BYTES + 1);
                    } catch (IOException failure) {
                        throw new CompletionException(failure);
                    }
                }, command -> {
                    Thread thread = new Thread(command, "sfm-puppet-dependency-index-output");
                    thread.setDaemon(true);
                    thread.start();
                });
                return false;
            } catch (IOException failure) {
                indexProbe = new IndexProbe(true, false, dependencySelector.orElseThrow(),
                        "probe-unavailable", "unknown", "", 0, -1,
                        "Could not start the read-only dependency-index probe");
                writeSkipped(runtime);
                state = State.COMPLETE;
                return true;
            }
        }
        if (indexProcess.isAlive() || !indexOutput.isDone()) {
            if (++indexProbeTicks <= MAXIMUM_INDEX_PROBE_TICKS) return false;
            indexProcess.destroyForcibly();
            indexOutput.cancel(true);
            indexProbe = new IndexProbe(true, false, dependencySelector.orElseThrow(),
                    "probe-timeout", "unknown", "", 0, -1,
                    "The bounded dependency-index probe did not finish");
            writeSkipped(runtime);
            state = State.COMPLETE;
            return true;
        }

        int exitCode = indexProcess.exitValue();
        try {
            byte[] bytes = indexOutput.join();
            if (bytes.length > MAXIMUM_INDEX_OUTPUT_BYTES) {
                indexProbe = new IndexProbe(true, false, dependencySelector.orElseThrow(),
                        "probe-output-oversized", "unknown", "", 0, exitCode,
                        "The dependency-index status exceeded the bounded probe output");
            } else {
                JsonObject output = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8).strip())
                        .getAsJsonObject();
                String outcome = string(output, "outcome", "unknown");
                int definitions = output.has("definitions") && output.get("definitions").isJsonArray()
                        ? output.getAsJsonArray("definitions").size()
                        : 0;
                JsonObject dependencyIndex = output.has("dependency_index")
                        && output.get("dependency_index").isJsonObject()
                        ? output.getAsJsonObject("dependency_index")
                        : new JsonObject();
                String statusName = string(dependencyIndex, "status", "unavailable");
                String completeness = string(dependencyIndex, "completeness", "unknown");
                String expectedIdentity = string(dependencyIndex, "expected_identity", "");
                boolean ready = outcome.equalsIgnoreCase("success")
                        && definitions == 1
                        && !expectedIdentity.isBlank();
                indexProbe = new IndexProbe(
                        true,
                        ready,
                        dependencySelector.orElseThrow(),
                        statusName,
                        completeness,
                        expectedIdentity,
                        definitions,
                        exitCode,
                        ready
                                ? "The pinned dependency index uniquely resolves the requested type"
                                : "The pinned dependency index did not uniquely resolve the requested type"
                );
            }
        } catch (RuntimeException failure) {
            indexProbe = new IndexProbe(true, false, dependencySelector.orElseThrow(),
                    "probe-invalid-output", "unknown", "", 0, exitCode,
                    "The dependency-index status was not valid bounded JSON");
        }
        if (!indexProbe.ready()) {
            writeSkipped(runtime);
            state = State.COMPLETE;
            return true;
        }
        state = State.POSITION_SOURCE;
        return false;
    }

    private boolean positionSource() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, sourceFile).orElse(null);
        if (source == null || source.state().documentSnapshot().isEmpty()
                || !source.state().documentSnapshot().orElseThrow().ready()) return false;
        SFMTextDocumentSnapshot document = source.state().documentSnapshot().orElseThrow();
        sourceRange = SFMSourcePuppetProbe.symbolRange(document.text(), symbol, occurrence);
        require(SFMSourcePuppetProbe.textAtRange(document.text(), sourceRange).equals(symbol),
                "Source range does not identify " + symbol);
        require(workspace.focusPanel(source.panelId()), "Could not focus the source editor panel");
        require(source.state().navigateToRange(sourceRange), "Could not position the source editor range");
        require(workspace.focusedPanelId().equals(source.panelId()), "Source panel focus was not retained");
        sourcePanelId = source.panelId();
        sourceDocumentHash = SFMSourcePuppetProbe.canonicalHash(document.sha256().orElseThrow());
        panelsBefore = Set.copyOf(workspace.panelIds());
        settleTicks = 2;
        state = State.INVOKE_F12;
        return false;
    }

    private boolean invokeF12(ISFMGamePuppetRuntime runtime) {
        if (settleTicks-- > 0) return false;
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        require(workspace.focusedPanelId().equals(sourcePanelId),
                "Source editor lost focus before the F12 event");
        queryStartedNanos = System.nanoTime();
        runtime.pressScreenKey(GLFW.GLFW_KEY_F12, 0);
        state = State.WAIT_FOR_TARGET;
        return false;
    }

    private boolean waitForTarget(ISFMGamePuppetRuntime runtime) {
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen) {
            throw new IllegalStateException("Unexpected ambiguous definition choice for deterministic symbol " + symbol);
        }
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        List<Target> targets = targets(workspace);
        if (targets.isEmpty()) {
            SFMSourcePuppetProbe.workspaceToastText(workspace)
                    .filter(AssertJumpToDefinitionPuppetAction::isTerminalFailureToast)
                    .ifPresent(message -> {
                        throw new IllegalStateException(message);
                    });
            return false;
        }
        require(targets.size() == 1,
                "Expected exactly one addressed definition target for " + symbol + ", found " + targets.size());
        Target target = targets.get(0);

        require(workspace.focusedPanelId().equals(target.editor().panelId()),
                "Definition target panel is not focused");
        require(SFMSourcePuppetProbe.textAtRange(target.document().text(), target.range()).equals(symbol),
                "Definition target range does not name " + symbol);
        require(expectedTargetAddress.isEmpty()
                        || target.document().path().equals(expectedTargetAddress),
                "Definition target path does not match the expected SFM-owned source");
        SFMSourcePuppetProbe.EditorHandle retainedSource = SFMSourcePuppetProbe.editor(workspace, sourceFile)
                .orElseThrow(() -> new IllegalStateException("Definition navigation replaced the source panel"));
        require(retainedSource.panelId().equals(sourcePanelId), "Definition navigation changed source-panel identity");
        require(SFMSourcePuppetProbe.canonicalHash(retainedSource.state().documentSnapshot()
                        .orElseThrow().sha256().orElseThrow()).equals(sourceDocumentHash),
                "Definition navigation changed the source document");
        require(workspace.panelIds().containsAll(panelsBefore),
                "Definition navigation removed an unrelated existing panel");
        long queryToVisibleMicros = Math.max(0L, (System.nanoTime() - queryStartedNanos) / 1_000L);

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.jump-to-definition-live-puppet/1");
        evidence.addProperty("action", "sfm:symbol/definition/open");
        evidence.addProperty("input_route", "F12");
        evidence.addProperty("symbol", symbol);
        evidence.addProperty("outcome", "success");
        evidence.addProperty("navigation_status", panelsBefore.contains(target.editor().panelId())
                ? "focused-existing" : "opened-adjacent");
        evidence.add("dependency_index", indexProbe.json());

        JsonObject source = new JsonObject();
        source.addProperty("path", sourceAddress.canonical());
        source.addProperty("document_sha256", sourceDocumentHash);
        source.add("selected_range", SFMSourcePuppetProbe.range(sourceRange));
        source.addProperty("panel_id", sourcePanelId.toString());
        source.addProperty("preserved", true);
        evidence.add("source", source);

        JsonObject definition = new JsonObject();
        definition.addProperty("observed_target_count", targets.size());
        definition.addProperty("path", target.document().path().orElseThrow().canonical());
        definition.addProperty("authorized_root", target.document().authorizedRoot().orElseThrow().canonical());
        definition.addProperty("document_sha256",
                SFMSourcePuppetProbe.canonicalHash(target.document().sha256().orElseThrow()));
        definition.add("target_range", SFMSourcePuppetProbe.range(target.range()));
        definition.addProperty("target_range_names_symbol", true);
        definition.addProperty("panel_id", target.editor().panelId().toString());
        definition.addProperty("target_panel_focused", true);
        definition.addProperty("query_to_visible_micros", queryToVisibleMicros);
        evidence.add("definition", definition);

        JsonObject timing = new JsonObject();
        if (dependencySelector.isEmpty()) {
            timing.addProperty("classification", "cold-first-definition-worker-startup-to-visible");
            timing.addProperty("cold_precondition",
                    "first definition action in this fresh single-puppet client; symbol runtime is lazy");
            timing.add("cold_worker_startup_to_visible", SFMSourcePuppetProbe.timing(queryToVisibleMicros));
        } else {
            timing.addProperty("classification", "dependency-definition-query-to-visible");
            timing.add("query_to_visible", SFMSourcePuppetProbe.timing(queryToVisibleMicros));
        }
        timing.addProperty("worker_runtime_telemetry", "not-exposed-by-production-runtime");
        evidence.add("timing", timing);

        JsonObject focus = new JsonObject();
        focus.addProperty("focused_panel", workspace.focusedPanelId().toString());
        focus.addProperty("source_panel_preserved", true);
        focus.addProperty("preexisting_panels_preserved", true);
        focus.addProperty("visible_panel_count", workspace.visiblePanelEntries().size());
        focus.add("panels_before", panelIds(panelsBefore));
        focus.add("panels_after", panelIds(new LinkedHashSet<>(workspace.panelIds())));
        evidence.add("focus", focus);

        JsonObject visual = new JsonObject();
        visual.addProperty("mandatory_screenshot_capture_id", captureName.orElse(artifactName));
        visual.addProperty("capture_contract", "puppet fails unless a non-empty PNG is written");
        visual.addProperty("pixel_assertion_performed", false);
        visual.addProperty("verification", "human-visual-review-required");
        evidence.add("visual_evidence", visual);
        evidence.addProperty("raw_source_retained", false);
        evidence.addProperty("source_files_mutated", false);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        state = captureName.isPresent() ? State.CAPTURE : State.COMPLETE;
        return state == State.COMPLETE;
    }

    private static boolean isTerminalFailureToast(String message) {
        return message.startsWith("Jump to definition unavailable:")
                || message.startsWith("Jump to definition ignored")
                || message.startsWith("Jump to definition requires")
                || message.startsWith("No symbol is present")
                || message.startsWith("No definition was found")
                || message.startsWith("The symbol worker")
                || message.startsWith("The captured document")
                || message.startsWith("The focused editor")
                || message.startsWith("Definition lookup")
                || message.startsWith("Definition target")
                || message.startsWith("Definition root")
                || message.startsWith("Definition range")
                || message.startsWith("A newer jump-to-definition request")
                || message.startsWith("That definition choice is stale")
                || message.startsWith("No safe adjacent panel placement");
    }

    private String diagnosticState() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            return "current_screen=" + (Minecraft.getInstance().screen == null
                    ? "none" : Minecraft.getInstance().screen.getClass().getSimpleName());
        }
        String toast = SFMSourcePuppetProbe.workspaceToastText(workspace).orElse("none");
        String editors = SFMSourcePuppetProbe.editors(workspace).stream().map(handle -> {
            Optional<SFMTextDocumentSnapshot> snapshot = handle.state().documentSnapshot();
            if (snapshot.isEmpty()) {
                return handle.panelId() + "{snapshot=none,resolved=" + handle.resolvedPanel().isPresent() + "}";
            }
            SFMTextDocumentSnapshot document = snapshot.orElseThrow();
            String targetText = document.targetRange()
                    .map(range -> safeTextAtRange(document.text(), range))
                    .orElse("none");
            return handle.panelId() + "{state=" + document.state()
                    + ",path=" + document.path().map(SFMPath::canonical).orElse("none")
                    + ",target_range=" + document.targetRange().isPresent()
                    + ",target_text=" + targetText
                    + ",resolved=" + handle.resolvedPanel().isPresent()
                    + ",diagnostics=" + document.diagnostics() + "}";
        }).collect(java.util.stream.Collectors.joining(","));
        return "toast=" + toast + "; focused=" + workspace.focusedPanelId() + "; editors=[" + editors + "]";
    }

    private boolean capture(ISFMGamePuppetRuntime runtime) {
        if (!runtime.capture(captureName.orElseThrow(), captureCaption.orElseThrow())) return false;
        state = State.COMPLETE;
        return true;
    }

    private List<Target> targets(SFMScreenMultiplexer workspace) {
        return SFMSourcePuppetProbe.editors(workspace).stream()
                .filter(candidate -> !candidate.panelId().equals(sourcePanelId))
                .map(candidate -> candidate.state().documentSnapshot()
                        .filter(SFMTextDocumentSnapshot::ready)
                        .flatMap(document -> document.targetRange()
                                .filter(range -> symbol.equals(safeTextAtRange(document.text(), range)))
                                .filter(range -> expectedTargetAddress.isEmpty()
                                        || document.path().equals(expectedTargetAddress))
                                .map(range -> new Target(candidate, document, range))))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingLong(candidate -> candidate.editor().panelId().value()))
                .toList();
    }

    private void writeSkipped(ISFMGamePuppetRuntime runtime) {
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.jump-to-definition-live-puppet/1");
        evidence.addProperty("action", "sfm:symbol/definition/open");
        evidence.addProperty("input_route", "F12");
        evidence.addProperty("symbol", symbol);
        evidence.addProperty("outcome", "skipped");
        evidence.addProperty("skip_reason", "dependency-definition-not-uniquely-indexed");
        evidence.add("dependency_index", indexProbe.json());
        evidence.addProperty("raw_source_retained", false);
        evidence.addProperty("source_files_mutated", false);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
    }

    private static String safeTextAtRange(String text, SFMTextDocumentRange range) {
        try {
            return SFMSourcePuppetProbe.textAtRange(text, range);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String string(JsonObject object, String property, String fallback) {
        return object.has(property) && object.get(property).isJsonPrimitive()
                ? object.get(property).getAsString()
                : fallback;
    }

    private static JsonArray panelIds(Set<SFMWorkspacePanelId> ids) {
        JsonArray result = new JsonArray();
        ids.stream().sorted(Comparator.comparingLong(SFMWorkspacePanelId::value))
                .map(SFMWorkspacePanelId::toString).forEach(result::add);
        return result;
    }

    private static String requireNonBlank(String value, String name) {
        String answer = Objects.requireNonNull(value, name).strip();
        if (answer.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
        return answer;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
