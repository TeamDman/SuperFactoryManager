package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.symbol.SFMNavigationPuppetCompletionGate;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Natural F12 proof for the SS-5 changed-document completion boundary. */
public final class AssertStaleDocumentJumpPuppetAction implements SFMPuppetAction {
    private static final Duration GATE_TIMEOUT = Duration.ofSeconds(55);
    private static final int MAXIMUM_TOTAL_TICKS = SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 9;
    private static final String EXPECTED_TOAST =
            "Jump to definition ignored: the source document bytes changed";

    private enum State {
        POSITION_SOURCE,
        ARM_GATE,
        INVOKE_F12,
        WAIT_ACCEPTED,
        WAIT_COMPLETION_HELD,
        MUTATE_DOCUMENT,
        RELEASE_COMPLETION,
        WAIT_TYPED_REJECTION,
        COMPLETE
    }

    private final Path sourceFile;
    private final SFMPath sourceAddress;
    private final String symbol;
    private final int occurrence;
    private final String artifactName;
    private State state = State.POSITION_SOURCE;
    private int totalTicks;
    private int settleTicks;
    private SFMSourcePuppetProbe.EditorHandle source;
    private SFMWorkspacePanelId sourcePanelId;
    private SFMTextDocumentRange sourceRange;
    private String originalText;
    private String originalHash;
    private String mutatedHash;
    private long mutationNanos;
    private Set<SFMWorkspacePanelId> panelsBefore = Set.of();
    private SFMNavigationPuppetCompletionGate.Lease gate;

    public AssertStaleDocumentJumpPuppetAction(
            Path sourceFile,
            String symbol,
            int occurrence,
            String artifactName
    ) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        sourceAddress = SFMPath.fromNative(this.sourceFile);
        this.symbol = requireNonBlank(symbol, "symbol");
        if (occurrence < -1) throw new IllegalArgumentException("Occurrence must be -1 or non-negative");
        this.occurrence = occurrence;
        this.artifactName = requireNonBlank(artifactName, "artifactName");
    }

    @Override
    public String description() {
        return "hold F12 completion, mutate the editor, and require DOCUMENT_CONTENT_CHANGED";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        try {
            if (++totalTicks > MAXIMUM_TOTAL_TICKS) {
                throw new IllegalStateException("Timed out proving the stale-document navigation boundary; "
                        + diagnosticState());
            }
            return switch (state) {
                case POSITION_SOURCE -> positionSource();
                case ARM_GATE -> armGate();
                case INVOKE_F12 -> invokeF12(runtime);
                case WAIT_ACCEPTED -> waitAccepted();
                case WAIT_COMPLETION_HELD -> waitCompletionHeld();
                case MUTATE_DOCUMENT -> mutateDocument();
                case RELEASE_COMPLETION -> releaseCompletion();
                case WAIT_TYPED_REJECTION -> waitTypedRejection(runtime);
                case COMPLETE -> true;
            };
        } catch (RuntimeException | Error failure) {
            closeGate();
            throw failure;
        }
    }

    private boolean positionSource() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        SFMSourcePuppetProbe.EditorHandle candidate =
                SFMSourcePuppetProbe.editor(workspace, sourceFile).orElse(null);
        if (candidate == null || candidate.resolvedPanel().isEmpty()) return false;
        SFMTextDocumentSnapshot document = candidate.state().documentSnapshot().orElse(null);
        if (document == null || !document.ready()) return false;
        if (!document.readOnly()) {
            throw new IllegalStateException("The stale-document puppet requires the addressed read-only editor");
        }
        sourceRange = SFMSourcePuppetProbe.symbolRange(document.text(), symbol, occurrence);
        if (!SFMSourcePuppetProbe.textAtRange(document.text(), sourceRange).equals(symbol)) {
            throw new IllegalStateException("The selected source range does not identify " + symbol);
        }
        if (!workspace.focusPanel(candidate.panelId()) || !candidate.state().navigateToRange(sourceRange)) {
            throw new IllegalStateException("Could not focus and position the source editor");
        }
        var projection = SFMSourcePuppetProbe.captureReadOnlyCurrentText(candidate);
        if (!projection.currentText().equals(document.text()) || projection.dirty()) {
            throw new IllegalStateException("The stale-document proof must begin from clean addressed bytes");
        }
        source = candidate;
        sourcePanelId = candidate.panelId();
        originalText = projection.currentText();
        originalHash = SFMSourcePuppetProbe.canonicalHash(projection.currentSha256());
        panelsBefore = Set.copyOf(workspace.panelIds());
        settleTicks = 2;
        state = State.ARM_GATE;
        return false;
    }

    private boolean armGate() {
        gate = SFMNavigationPuppetCompletionGate.armNextJump(GATE_TIMEOUT);
        state = State.INVOKE_F12;
        return false;
    }

    private boolean invokeF12(ISFMGamePuppetRuntime runtime) {
        if (settleTicks-- > 0) return false;
        SFMScreenMultiplexer workspace = workspace();
        if (!workspace.focusedPanelId().equals(sourcePanelId)) {
            throw new IllegalStateException("Source editor lost focus before natural F12 input");
        }
        runtime.pressScreenKey(GLFW.GLFW_KEY_F12, 0);
        state = State.WAIT_ACCEPTED;
        return false;
    }

    private boolean waitAccepted() {
        SFMNavigationPuppetCompletionGate.Observation observation = observation();
        rejectTimeout(observation);
        if (!observation.accepted()) return false;
        if (observation.sourcePanelId() != sourcePanelId.value()) {
            throw new IllegalStateException("The gate accepted a request from another source panel");
        }
        if (!SFMSourcePuppetProbe.canonicalHash(observation.documentSha256()).equals(originalHash)) {
            throw new IllegalStateException("The accepted witness does not contain the clean source bytes");
        }
        if (!observation.documentAddress().orElseThrow().equals(sourceAddress.canonical())) {
            throw new IllegalStateException("The accepted witness does not contain the source address");
        }
        state = State.WAIT_COMPLETION_HELD;
        return false;
    }

    private boolean waitCompletionHeld() {
        SFMNavigationPuppetCompletionGate.Observation observation = observation();
        rejectTimeout(observation);
        if (!observation.completionHeld()) return false;
        if (observation.released()) {
            throw new IllegalStateException("The lookup completion escaped before the editor mutation");
        }
        state = State.MUTATE_DOCUMENT;
        return false;
    }

    private boolean mutateDocument() {
        // EditorV3's glyph projection intentionally has no representation for
        // trailing layout-only newlines. End on a visible glyph so the puppet
        // mutates and then re-captures the exact current document bytes.
        String mutatedText = originalText + "\n// SS-5 game-puppet stale-document overlay";
        var projection = SFMSourcePuppetProbe.seedReadOnlyCurrentText(source, mutatedText, sourceRange);
        mutatedHash = SFMSourcePuppetProbe.canonicalHash(projection.currentSha256());
        if (mutatedHash.equals(originalHash) || !projection.dirty() || !projection.readOnly()) {
            throw new IllegalStateException("The test-only editor overlay did not change the captured bytes");
        }
        mutationNanos = System.nanoTime();
        SFMNavigationPuppetCompletionGate.Observation observation = observation();
        if (mutationNanos < observation.acceptedNanos()) {
            throw new IllegalStateException("The editor mutation occurred before request acceptance");
        }
        state = State.RELEASE_COMPLETION;
        return false;
    }

    private boolean releaseCompletion() {
        gate.release();
        state = State.WAIT_TYPED_REJECTION;
        return false;
    }

    private boolean waitTypedRejection(ISFMGamePuppetRuntime runtime) {
        SFMNavigationPuppetCompletionGate.Observation observation = observation();
        rejectTimeout(observation);
        if (observation.rejectionCode().isEmpty()) return false;
        if (!observation.rejectionCode().orElseThrow().equals("DOCUMENT_CONTENT_CHANGED")) {
            throw new IllegalStateException("Expected DOCUMENT_CONTENT_CHANGED, received "
                    + observation.rejectionCode().orElseThrow());
        }
        if (!observation.rejectionDescription().orElseThrow().equals("the source document bytes changed")) {
            throw new IllegalStateException("The typed stale-document reason changed unexpectedly");
        }
        SFMScreenMultiplexer workspace = workspace();
        String toast = SFMSourcePuppetProbe.workspaceToastText(workspace).orElse("");
        if (!toast.equals(EXPECTED_TOAST)) {
            throw new IllegalStateException("The natural rejection toast was not exact: " + toast);
        }
        if (!Set.copyOf(workspace.panelIds()).equals(panelsBefore)) {
            throw new IllegalStateException("A stale completion changed the workspace panel set");
        }
        if (!workspace.focusedPanelId().equals(sourcePanelId)) {
            throw new IllegalStateException("A stale completion navigated away from the source panel");
        }
        String retainedOverlayHash = SFMSourcePuppetProbe.canonicalHash(
                SFMSourcePuppetProbe.captureReadOnlyCurrentText(source).currentSha256()
        );
        if (!retainedOverlayHash.equals(mutatedHash)) {
            throw new IllegalStateException("Stale rejection did not retain the mutated editor overlay");
        }
        try {
            if (!Files.readString(sourceFile, StandardCharsets.UTF_8).equals(originalText)) {
                throw new IllegalStateException("The test-only overlay unexpectedly changed the source file");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify the source file after stale rejection", failure);
        }

        JsonObject evidence = evidence(observation, toast, retainedOverlayHash);
        runtime.writeArtifact(
                artifactName,
                SFMGamePuppetArtifactFormat.JSON,
                new GsonBuilder().setPrettyPrinting().create().toJson(evidence)
        );
        SFM.LOGGER.info(
                "SFM_STALE_DOCUMENT_NAVIGATION_PUPPET_PROVED gate={} request={} reason={} source={} original_hash={} mutated_hash={}",
                observation.gateId(),
                observation.requestGeneration(),
                observation.rejectionCode().orElseThrow(),
                sourceAddress.canonical(),
                originalHash,
                mutatedHash
        );
        closeGate();
        state = State.COMPLETE;
        return true;
    }

    private JsonObject evidence(
            SFMNavigationPuppetCompletionGate.Observation observation,
            String toast,
            String retainedOverlayHash
    ) {
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.navigation-stale-document-puppet/1");
        evidence.addProperty("input_route", "F12");
        evidence.addProperty("natural_canvas_input", true);
        evidence.addProperty("source", sourceAddress.canonical());
        evidence.addProperty("source_panel_id", sourcePanelId.value());
        evidence.addProperty("selected_symbol", symbol);
        evidence.add("selected_range", SFMSourcePuppetProbe.range(sourceRange));
        evidence.addProperty("original_document_sha256", originalHash);
        evidence.addProperty("mutated_document_sha256", mutatedHash);
        evidence.addProperty("retained_overlay_sha256", retainedOverlayHash);
        evidence.addProperty("editor_overlay_mutated", true);
        evidence.addProperty("source_file_mutated", false);

        JsonObject gateEvidence = new JsonObject();
        gateEvidence.addProperty("gate_id", observation.gateId());
        gateEvidence.addProperty("request_generation", observation.requestGeneration());
        gateEvidence.addProperty("origin_id", observation.originId());
        gateEvidence.addProperty("editor_id", observation.editorId());
        gateEvidence.addProperty("document_address", observation.documentAddress().orElseThrow());
        gateEvidence.addProperty("authorized_root", observation.authorizedRoot().orElseThrow());
        gateEvidence.addProperty("provider_generation", observation.providerGeneration());
        gateEvidence.addProperty("accepted", observation.accepted());
        gateEvidence.addProperty("completion_held", observation.completionHeld());
        gateEvidence.addProperty("released", observation.released());
        gateEvidence.addProperty("timed_out", observation.timedOut());
        gateEvidence.addProperty("accepted_to_held_micros", micros(
                observation.acceptedNanos(), observation.completionHeldNanos()));
        gateEvidence.addProperty("held_to_mutation_micros", micros(
                observation.completionHeldNanos(), mutationNanos));
        gateEvidence.addProperty("mutation_to_release_micros", micros(
                mutationNanos, observation.releasedNanos()));
        gateEvidence.addProperty("release_to_rejection_micros", micros(
                observation.releasedNanos(), observation.rejectionNanos()));
        evidence.add("gate", gateEvidence);

        JsonObject rejection = new JsonObject();
        rejection.addProperty("code", observation.rejectionCode().orElseThrow());
        rejection.addProperty("description", observation.rejectionDescription().orElseThrow());
        rejection.addProperty("toast", toast);
        rejection.addProperty("navigation_applied", false);
        rejection.addProperty("panel_set_unchanged", true);
        rejection.addProperty("source_panel_remained_focused", true);
        evidence.add("rejection", rejection);
        return evidence;
    }

    private SFMNavigationPuppetCompletionGate.Observation observation() {
        if (gate == null) throw new IllegalStateException("The completion gate is not armed");
        return gate.observation();
    }

    private SFMScreenMultiplexer workspace() {
        if (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace) return workspace;
        throw new IllegalStateException("The stale-document proof lost its SFM workspace");
    }

    private void rejectTimeout(SFMNavigationPuppetCompletionGate.Observation observation) {
        if (!observation.timedOut()) return;
        closeGate();
        throw new IllegalStateException("The bounded navigation completion gate timed out; accepted="
                + observation.accepted() + " completion_held=" + observation.completionHeld());
    }

    private String diagnosticState() {
        if (gate == null) return "state=" + state + "; gate=unarmed";
        SFMNavigationPuppetCompletionGate.Observation observation = gate.observation();
        return "state=" + state
                + "; accepted=" + observation.accepted()
                + "; completion_held=" + observation.completionHeld()
                + "; released=" + observation.released()
                + "; timed_out=" + observation.timedOut()
                + "; rejection=" + observation.rejectionCode().orElse("none");
    }

    private void closeGate() {
        if (gate == null) return;
        gate.close();
        gate = null;
    }

    private static long micros(long startNanos, long endNanos) {
        return Math.max(0L, endNanos - startNanos) / 1_000L;
    }

    private static String requireNonBlank(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
