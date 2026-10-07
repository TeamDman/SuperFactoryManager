package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Repeats one exact F12 lookup and proves addressed target-panel reuse. */
public final class AssertWarmJumpToDefinitionPuppetAction implements SFMPuppetAction {
    private enum State {
        POSITION_SOURCE,
        INVOKE_F12,
        WAIT_FOR_TARGET
    }

    private record Target(
            SFMSourcePuppetProbe.EditorHandle editor,
            SFMTextDocumentSnapshot document,
            SFMTextDocumentRange range
    ) {
    }

    private final Path sourceFile;
    private final Path expectedTargetFile;
    private final SFMPath sourceAddress;
    private final SFMPath targetAddress;
    private final String symbol;
    private final int occurrence;
    private final String artifactName;
    private final String mandatoryScreenshotCaptureId;
    private State state = State.POSITION_SOURCE;
    private int ticks;
    private int settleTicks;
    private SFMWorkspacePanelId sourcePanelId;
    private SFMWorkspacePanelId targetPanelId;
    private Set<SFMWorkspacePanelId> panelsBefore = Set.of();
    private SFMTextDocumentRange sourceRange;
    private SFMTextDocumentRange targetRange;
    private String sourceHash;
    private String targetHash;
    private long queryStartedNanos;

    public AssertWarmJumpToDefinitionPuppetAction(
            Path sourceFile,
            String symbol,
            int occurrence,
            Path expectedTargetFile,
            String artifactName,
            String mandatoryScreenshotCaptureId
    ) {
        this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").toAbsolutePath().normalize();
        this.expectedTargetFile = Objects.requireNonNull(expectedTargetFile, "expectedTargetFile")
                .toAbsolutePath().normalize();
        sourceAddress = SFMPath.fromNative(this.sourceFile);
        targetAddress = SFMPath.fromNative(this.expectedTargetFile);
        this.symbol = requireNonBlank(symbol, "symbol");
        if (occurrence < -1) throw new IllegalArgumentException("Occurrence must be -1 or non-negative");
        this.occurrence = occurrence;
        this.artifactName = requireNonBlank(artifactName, "artifactName");
        this.mandatoryScreenshotCaptureId = requireNonBlank(
                mandatoryScreenshotCaptureId,
                "mandatoryScreenshotCaptureId"
        );
    }

    @Override
    public String description() {
        return "repeat F12 on " + symbol + " and prove warm definition target reuse";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++ticks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 2) {
            throw new IllegalStateException("Timed out proving warm definition navigation for " + symbol);
        }
        return switch (state) {
            case POSITION_SOURCE -> positionSource();
            case INVOKE_F12 -> invoke(runtime);
            case WAIT_FOR_TARGET -> observe(runtime);
        };
    }

    private boolean positionSource() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, sourceFile).orElse(null);
        if (source == null || source.state().documentSnapshot().isEmpty()) return false;
        SFMTextDocumentSnapshot sourceDocument = source.state().documentSnapshot().orElseThrow();
        if (!sourceDocument.ready()) return false;
        List<Target> targets = targets(workspace);
        if (targets.isEmpty()) return false;
        require(targets.size() == 1, "Warm definition proof requires one exact existing target panel");
        Target target = targets.get(0);

        sourceRange = SFMSourcePuppetProbe.symbolRange(sourceDocument.text(), symbol, occurrence);
        require(SFMSourcePuppetProbe.textAtRange(sourceDocument.text(), sourceRange).equals(symbol),
                "Warm definition source range does not name " + symbol);
        require(workspace.focusPanel(source.panelId()), "Could not refocus the definition source panel");
        require(source.state().navigateToRange(sourceRange), "Could not restore the exact source range");
        require(workspace.focusedPanelId().equals(source.panelId()),
                "Source focus was not retained before the warm F12 request");

        sourcePanelId = source.panelId();
        targetPanelId = target.editor().panelId();
        targetRange = target.range();
        sourceHash = SFMSourcePuppetProbe.canonicalHash(sourceDocument.sha256().orElseThrow());
        targetHash = SFMSourcePuppetProbe.canonicalHash(target.document().sha256().orElseThrow());
        panelsBefore = Set.copyOf(workspace.panelIds());
        settleTicks = 2;
        state = State.INVOKE_F12;
        return false;
    }

    private boolean invoke(ISFMGamePuppetRuntime runtime) {
        if (settleTicks-- > 0) return false;
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        require(workspace.focusedPanelId().equals(sourcePanelId),
                "Source editor lost focus before the warm F12 request");
        queryStartedNanos = System.nanoTime();
        runtime.pressScreenKey(GLFW.GLFW_KEY_F12, 0);
        state = State.WAIT_FOR_TARGET;
        return false;
    }

    private boolean observe(ISFMGamePuppetRuntime runtime) {
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen) {
            throw new IllegalStateException("Warm deterministic definition unexpectedly became ambiguous");
        }
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        if (!workspace.focusedPanelId().equals(targetPanelId)) return false;
        SFMSourcePuppetProbe.EditorHandle target = SFMSourcePuppetProbe.editor(workspace, targetPanelId)
                .orElseThrow(() -> new IllegalStateException("Warm definition target panel disappeared"));
        SFMTextDocumentSnapshot targetDocument = target.state().documentSnapshot().orElseThrow();
        require(targetDocument.ready() && targetDocument.path().equals(Optional.of(targetAddress)),
                "Warm definition target path changed");
        require(targetDocument.targetRange().equals(Optional.of(targetRange)),
                "Warm definition target range changed");
        require(SFMSourcePuppetProbe.textAtRange(targetDocument.text(), targetRange).equals(symbol),
                "Warm definition target range no longer names " + symbol);
        require(SFMSourcePuppetProbe.canonicalHash(targetDocument.sha256().orElseThrow()).equals(targetHash),
                "Warm definition target document changed");
        require(Set.copyOf(workspace.panelIds()).equals(panelsBefore),
                "Warm definition navigation added, removed, or replaced a panel");
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, sourceFile)
                .orElseThrow(() -> new IllegalStateException("Warm definition navigation lost the source panel"));
        require(source.panelId().equals(sourcePanelId), "Warm definition navigation changed source-panel identity");
        require(SFMSourcePuppetProbe.canonicalHash(source.state().documentSnapshot()
                        .orElseThrow().sha256().orElseThrow()).equals(sourceHash),
                "Warm definition navigation changed the source document");
        long queryToVisibleMicros = Math.max(0L, (System.nanoTime() - queryStartedNanos) / 1_000L);

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.warm-definition-navigation-live-puppet/1");
        evidence.addProperty("outcome", "success");
        evidence.addProperty("action", "sfm:symbol/definition/open");
        evidence.addProperty("input_route", "F12");
        evidence.addProperty("symbol", symbol);
        evidence.addProperty("source_path", sourceAddress.canonical());
        evidence.addProperty("source_document_sha256", sourceHash);
        evidence.add("source_range", SFMSourcePuppetProbe.range(sourceRange));
        evidence.addProperty("target_path", targetAddress.canonical());
        evidence.addProperty("target_document_sha256", targetHash);
        evidence.add("target_range", SFMSourcePuppetProbe.range(targetRange));
        evidence.addProperty("target_panel_id", targetPanelId.toString());
        evidence.addProperty("target_panel_focused", true);
        evidence.addProperty("existing_target_panel_reused", true);
        evidence.addProperty("panel_set_unchanged", true);
        evidence.addProperty("query_cache_status", "not-exposed-by-production-runtime");

        JsonObject timing = new JsonObject();
        timing.addProperty("classification", "warm-repeated-symbol-query-to-visible");
        timing.add("query_to_visible", SFMSourcePuppetProbe.timing(queryToVisibleMicros));
        evidence.add("timing", timing);

        JsonObject visual = new JsonObject();
        visual.addProperty("mandatory_screenshot_capture_id", mandatoryScreenshotCaptureId);
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
        return true;
    }

    private List<Target> targets(SFMScreenMultiplexer workspace) {
        return SFMSourcePuppetProbe.editors(workspace).stream()
                .map(candidate -> candidate.state().documentSnapshot()
                        .filter(SFMTextDocumentSnapshot::ready)
                        .filter(document -> document.path().equals(Optional.of(targetAddress)))
                        .flatMap(document -> document.targetRange()
                                .filter(range -> symbol.equals(safeTextAtRange(document.text(), range)))
                                .map(range -> new Target(candidate, document, range))))
                .flatMap(Optional::stream)
                .toList();
    }

    private static String safeTextAtRange(String text, SFMTextDocumentRange range) {
        try {
            return SFMSourcePuppetProbe.textAtRange(text, range);
        } catch (RuntimeException ignored) {
            return "";
        }
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
