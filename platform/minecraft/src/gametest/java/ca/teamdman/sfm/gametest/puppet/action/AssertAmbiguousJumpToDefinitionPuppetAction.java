package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Live ambiguous F12 proof through the constrained command-palette choice surface. */
public final class AssertAmbiguousJumpToDefinitionPuppetAction implements SFMPuppetAction {
    private static final String SYMBOL = "StringDistances";
    private static final String BUILDER_QUALIFIED = "org.simmetrics.builders.StringDistances";
    private static final String METRICS_QUALIFIED = "org.simmetrics.metrics.StringDistances";
    private static final String FIXTURE_TEXT = """
            package ca.teamdman.sfm.client.action;

            import org.simmetrics.builders.StringDistances;
            import org.simmetrics.metrics.StringDistances;

            final class SFMClientActionCommandTree {
                StringDistances value;
            }""";

    private enum State {
        CREATE_FIXTURE,
        POSITION_FIXTURE,
        INVOKE_F12,
        WAIT_FOR_CHOICES,
        CAPTURE_CHOICES,
        SELECT_CHOICE,
        WAIT_FOR_TARGET,
        CAPTURE_TARGET,
        WRITE_AND_CLEANUP
    }

    private record Target(
            SFMSourcePuppetProbe.EditorHandle editor,
            SFMTextDocumentSnapshot document,
            SFMTextDocumentRange range
    ) {
    }

    private final Path sourceFile;
    private final Path authorizedRoot;
    private final Path fixturePath;
    private final Path selectedTargetFile;
    private final SFMPath fixtureAddress;
    private final SFMPath rootAddress;
    private final SFMPath selectedTargetAddress;
    private final String artifactName;
    private final String choiceCaptureName;
    private final Component choiceCaption;
    private final String targetCaptureName;
    private final Component targetCaption;
    private State state = State.CREATE_FIXTURE;
    private int totalTicks;
    private int settleTicks;
    private SFMWorkspacePanelId sourcePanelId;
    private SFMWorkspacePanelId fixturePanelId;
    private SFMWorkspacePanelId targetPanelId;
    private Set<SFMWorkspacePanelId> panelsBefore = Set.of();
    private String sourceHash;
    private String fixtureBaselineHash;
    private String fixtureCurrentHash;
    private String targetHash;
    private SFMTextDocumentRange fixtureRange;
    private SFMTextDocumentRange targetRange;
    private String paletteTitle;
    private List<String> choiceCommands = List.of();
    private String selectedCommand;
    private long queryStartedNanos;
    private long choiceVisibleNanos;
    private long choiceSelectedNanos;
    private long targetVisibleNanos;

    public AssertAmbiguousJumpToDefinitionPuppetAction(
            Path sourceFile,
            Path authorizedRoot,
            Path fixturePath,
            Path selectedTargetFile,
            String artifactName,
            String choiceCaptureName,
            Component choiceCaption,
            String targetCaptureName,
            Component targetCaption
    ) {
        this.sourceFile = normalize(sourceFile, "sourceFile");
        this.authorizedRoot = normalize(authorizedRoot, "authorizedRoot");
        this.fixturePath = normalize(fixturePath, "fixturePath");
        this.selectedTargetFile = normalize(selectedTargetFile, "selectedTargetFile");
        fixtureAddress = SFMPath.fromNative(this.fixturePath);
        rootAddress = SFMPath.fromNative(this.authorizedRoot);
        selectedTargetAddress = SFMPath.fromNative(this.selectedTargetFile);
        this.artifactName = requireNonBlank(artifactName, "artifactName");
        this.choiceCaptureName = requireNonBlank(choiceCaptureName, "choiceCaptureName");
        this.choiceCaption = Objects.requireNonNull(choiceCaption, "choiceCaption").copy();
        this.targetCaptureName = requireNonBlank(targetCaptureName, "targetCaptureName");
        this.targetCaption = Objects.requireNonNull(targetCaption, "targetCaption").copy();
    }

    @Override
    public String description() {
        return "exercise a live ambiguous StringDistances definition and constrained palette choice";
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++totalTicks > SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 6) {
            throw new IllegalStateException("Timed out proving live ambiguous definition selection");
        }
        return switch (state) {
            case CREATE_FIXTURE -> createFixture();
            case POSITION_FIXTURE -> positionFixture();
            case INVOKE_F12 -> invoke(runtime);
            case WAIT_FOR_CHOICES -> waitForChoices();
            case CAPTURE_CHOICES -> captureChoices(runtime);
            case SELECT_CHOICE -> selectChoice();
            case WAIT_FOR_TARGET -> waitForTarget();
            case CAPTURE_TARGET -> captureTarget(runtime);
            case WRITE_AND_CLEANUP -> writeAndCleanup(runtime);
        };
    }

    private boolean createFixture() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        require(Files.isRegularFile(fixturePath), "Ambiguous fixture requires a real immutable disk baseline");
        require(Files.isRegularFile(selectedTargetFile), "Selected ambiguous target source is unavailable");
        require(SFMSourcePuppetProbe.editor(workspace, selectedTargetFile).isEmpty(),
                "Ambiguous target must not already be open before the live choice");
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, sourceFile)
                .orElse(null);
        if (source == null || source.state().documentSnapshot().isEmpty()) return false;
        SFMTextDocumentSnapshot sourceDocument = source.state().documentSnapshot().orElseThrow();
        if (!sourceDocument.ready()) return false;

        sourcePanelId = source.panelId();
        sourceHash = SFMSourcePuppetProbe.canonicalHash(sourceDocument.sha256().orElseThrow());
        panelsBefore = Set.copyOf(workspace.panelIds());
        String baselineText;
        try {
            baselineText = Files.readString(fixturePath, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read the immutable ambiguity fixture baseline", failure);
        }
        SFMTextDocumentSnapshot fixture = SFMSourcePuppetProbe.addressedReadOnlySnapshot(
                baselineText, fixturePath, authorizedRoot);
        fixtureBaselineHash = SFMSourcePuppetProbe.canonicalHash(fixture.sha256().orElseThrow());
        fixtureCurrentHash = SFMSourcePuppetProbe.canonicalHash(
                SFMTextDocumentSnapshot.literal(FIXTURE_TEXT).sha256().orElseThrow());
        fixtureRange = SFMSourcePuppetProbe.symbolRange(FIXTURE_TEXT, SYMBOL, -1);
        SFMTextEditorPanelRecipe recipe = new SFMTextEditorPanelRecipe(
                new ResourceLocation(SFM.MOD_ID, "text_editor"),
                SFMTextEditors.V3.getId().orElseThrow().location(),
                new SFMTextDocumentSource.Literal(baselineText),
                true,
                "Ambiguous definition fixture"
        );
        require(workspace.openIntoSlot(
                sourcePanelId,
                recipe.createResolvedPanel(fixture),
                SFMWorkspacePanelMetadata.ordinary(),
                null
        ) == SFMWorkspacePanelIntentResult.APPLIED,
                "Could not open the in-memory ambiguity fixture as a temporary tab");
        LinkedHashSet<SFMWorkspacePanelId> added = new LinkedHashSet<>(workspace.panelIds());
        added.removeAll(panelsBefore);
        require(added.size() == 1, "Ambiguity fixture must add exactly one temporary panel");
        fixturePanelId = added.iterator().next();
        require(workspace.focusedPanelId().equals(fixturePanelId), "Ambiguity fixture is not focused");
        state = State.POSITION_FIXTURE;
        return false;
    }

    private boolean positionFixture() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        SFMSourcePuppetProbe.EditorHandle fixture = SFMSourcePuppetProbe.editor(workspace, fixturePanelId)
                .orElse(null);
        if (fixture == null || fixture.state().documentSnapshot().isEmpty()) return false;
        SFMTextDocumentSnapshot document = fixture.state().documentSnapshot().orElseThrow();
        if (!document.ready()) return false;
        require(document.readOnly() && fixture.state().isReadOnly(), "Ambiguity fixture must be read-only");
        require(document.path().equals(Optional.of(fixtureAddress)), "Ambiguity fixture path changed");
        require(document.authorizedRoot().equals(Optional.of(rootAddress)),
                "Ambiguity fixture authorization root changed");
        require(SFMSourcePuppetProbe.canonicalHash(document.sha256().orElseThrow()).equals(fixtureBaselineHash),
                "Ambiguity fixture baseline hash changed");
        require(SFMSourcePuppetProbe.textAtRange(FIXTURE_TEXT, fixtureRange).equals(SYMBOL),
                "Ambiguity fixture range does not name " + SYMBOL);
        require(workspace.focusPanel(fixturePanelId), "Could not focus the ambiguity fixture");
        var projection = SFMSourcePuppetProbe.seedReadOnlyCurrentText(fixture, FIXTURE_TEXT, fixtureRange);
        require(SFMSourcePuppetProbe.canonicalHash(projection.currentSha256()).equals(fixtureCurrentHash),
                "Ambiguity fixture current-document hash changed while seeding");
        settleTicks = 2;
        state = State.INVOKE_F12;
        return false;
    }

    private boolean invoke(ISFMGamePuppetRuntime runtime) {
        if (settleTicks-- > 0) return false;
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        require(workspace.focusedPanelId().equals(fixturePanelId),
                "Ambiguity fixture lost focus before F12");
        queryStartedNanos = System.nanoTime();
        runtime.pressScreenKey(GLFW.GLFW_KEY_F12, 0);
        state = State.WAIT_FOR_CHOICES;
        return false;
    }

    private boolean waitForChoices() {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        paletteTitle = palette.getTitle().getString();
        choiceCommands = List.copyOf(palette.choiceCommandsForAutomation());
        require(paletteTitle.equals("Choose definition (2)"),
                "Ambiguous definition must open the two-choice constrained palette");
        require(choiceCommands.size() == 2, "Ambiguous definition must expose exactly two canonical choices");
        require(choiceCommands.stream().filter(command -> command.contains(BUILDER_QUALIFIED + "@")).count() == 1,
                "Constrained choices are missing the builder StringDistances candidate");
        require(choiceCommands.stream().filter(command -> command.contains(METRICS_QUALIFIED + "@")).count() == 1,
                "Constrained choices are missing the metrics StringDistances candidate");
        require(choiceCommands.get(0).contains(BUILDER_QUALIFIED + "@")
                        && choiceCommands.get(1).contains(METRICS_QUALIFIED + "@"),
                "Ambiguous definition choices are not in stable qualified-name order");
        selectedCommand = choiceCommands.stream()
                .filter(command -> command.contains(METRICS_QUALIFIED + "@"))
                .findFirst()
                .orElseThrow();
        choiceVisibleNanos = System.nanoTime();
        state = State.CAPTURE_CHOICES;
        return false;
    }

    private boolean captureChoices(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen)) {
            throw new IllegalStateException("Constrained ambiguity palette closed before its screenshot");
        }
        if (!runtime.capture(choiceCaptureName, choiceCaption)) return false;
        state = State.SELECT_CHOICE;
        return false;
    }

    private boolean selectChoice() {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) {
            throw new IllegalStateException("Constrained ambiguity palette closed before selection");
        }
        choiceSelectedNanos = System.nanoTime();
        palette.executeChoiceForAutomation(selectedCommand);
        state = State.WAIT_FOR_TARGET;
        return false;
    }

    private boolean waitForTarget() {
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen) return false;
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        List<Target> targets = targets(workspace);
        if (targets.isEmpty()) return false;
        require(targets.size() == 1, "Selected ambiguity choice must open one exact addressed target");
        Target target = targets.get(0);
        require(workspace.focusedPanelId().equals(target.editor().panelId()),
                "Selected ambiguity target panel is not focused");
        require(SFMSourcePuppetProbe.textAtRange(target.document().text(), target.range()).equals(SYMBOL),
                "Selected ambiguity target range does not name " + SYMBOL);
        require(workspace.panelIds().containsAll(panelsBefore),
                "Ambiguous navigation removed an unrelated pre-existing panel");
        SFMSourcePuppetProbe.EditorHandle fixture = SFMSourcePuppetProbe.editor(workspace, fixturePanelId)
                .orElseThrow(() -> new IllegalStateException("Ambiguous navigation replaced its source fixture"));
        require(SFMSourcePuppetProbe.canonicalHash(fixture.state().documentSnapshot()
                        .orElseThrow().sha256().orElseThrow()).equals(fixtureBaselineHash),
                "Ambiguous navigation changed its immutable disk baseline");
        require(SFMSourcePuppetProbe.canonicalHash(SFMSourcePuppetProbe.captureReadOnlyCurrentText(fixture)
                        .currentSha256()).equals(fixtureCurrentHash),
                "Ambiguous navigation changed its in-memory current document");
        require(diskHash().equals(fixtureBaselineHash),
                "Ambiguous navigation changed the fixture's real source file");

        targetPanelId = target.editor().panelId();
        targetRange = target.range();
        targetHash = SFMSourcePuppetProbe.canonicalHash(target.document().sha256().orElseThrow());
        targetVisibleNanos = System.nanoTime();
        state = State.CAPTURE_TARGET;
        return false;
    }

    private boolean captureTarget(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)
                || !workspace.focusedPanelId().equals(targetPanelId)) {
            throw new IllegalStateException("Selected ambiguity target lost focus before its screenshot");
        }
        if (!runtime.capture(targetCaptureName, targetCaption)) return false;
        state = State.WRITE_AND_CLEANUP;
        return false;
    }

    private boolean writeAndCleanup(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) return false;
        long queryToChoiceMicros = Math.max(0L, (choiceVisibleNanos - queryStartedNanos) / 1_000L);
        long choiceToTargetMicros = Math.max(0L, (targetVisibleNanos - choiceSelectedNanos) / 1_000L);
        long queryToTargetMicros = Math.max(0L, (targetVisibleNanos - queryStartedNanos) / 1_000L);

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.ambiguous-definition-live-puppet/1");
        evidence.addProperty("outcome", "success");
        evidence.addProperty("ambiguity_live_exercised", true);
        evidence.addProperty("action", "sfm:symbol/definition/open");
        evidence.addProperty("input_route", "F12");
        evidence.addProperty("symbol", SYMBOL);

        JsonObject fixture = new JsonObject();
        fixture.addProperty("path", fixtureAddress.canonical());
        fixture.addProperty("authorized_root", rootAddress.canonical());
        fixture.addProperty("disk_baseline_sha256", fixtureBaselineHash);
        fixture.addProperty("current_document_sha256", fixtureCurrentHash);
        fixture.addProperty("read_only", true);
        fixture.addProperty("storage", "real-immutable-disk-baseline-plus-unsaved-in-memory-current-document");
        fixture.addProperty("path_existed_before", true);
        fixture.addProperty("disk_baseline_unchanged", diskHash().equals(fixtureBaselineHash));
        fixture.add("selected_range", SFMSourcePuppetProbe.range(fixtureRange));
        fixture.addProperty("panel_id", fixturePanelId.toString());
        fixture.addProperty("preserved_through_navigation", true);
        evidence.add("fixture", fixture);

        JsonObject palette = new JsonObject();
        palette.addProperty("constrained", true);
        palette.addProperty("title", paletteTitle);
        palette.addProperty("choice_count", choiceCommands.size());
        JsonArray candidates = new JsonArray();
        candidates.add(BUILDER_QUALIFIED);
        candidates.add(METRICS_QUALIFIED);
        palette.add("qualified_candidates", candidates);
        palette.addProperty("stable_order_verified", true);
        palette.addProperty("selected_qualified_name", METRICS_QUALIFIED);
        palette.addProperty("selected_canonical_command", selectedCommand);
        evidence.add("choice_palette", palette);

        JsonObject definition = new JsonObject();
        definition.addProperty("path", selectedTargetAddress.canonical());
        definition.addProperty("document_sha256", targetHash);
        definition.add("target_range", SFMSourcePuppetProbe.range(targetRange));
        definition.addProperty("target_range_names_symbol", true);
        definition.addProperty("panel_id", targetPanelId.toString());
        definition.addProperty("target_panel_focused", true);
        definition.addProperty("selected_exact_target", METRICS_QUALIFIED);
        evidence.add("definition", definition);

        JsonObject timing = new JsonObject();
        timing.add("query_to_choice_visible", SFMSourcePuppetProbe.timing(queryToChoiceMicros));
        timing.add("choice_to_target_visible", SFMSourcePuppetProbe.timing(choiceToTargetMicros));
        timing.add("query_to_selected_target_visible", SFMSourcePuppetProbe.timing(queryToTargetMicros));
        evidence.add("timing", timing);

        JsonObject visual = new JsonObject();
        JsonArray requiredCaptures = new JsonArray();
        requiredCaptures.add(choiceCaptureName);
        requiredCaptures.add(targetCaptureName);
        visual.add("mandatory_screenshot_capture_ids", requiredCaptures);
        visual.addProperty("non_empty_png_captures_completed", true);
        visual.addProperty("pixel_assertion_performed", false);
        visual.addProperty("verification", "human-visual-review-required");
        evidence.add("visual_evidence", visual);
        evidence.addProperty("raw_source_retained", false);
        evidence.addProperty("source_files_mutated", false);

        require(workspace.closePanel(targetPanelId) == SFMWorkspacePanelIntentResult.APPLIED,
                "Could not close the selected ambiguity target panel");
        require(workspace.closePanel(fixturePanelId) == SFMWorkspacePanelIntentResult.APPLIED,
                "Could not close the in-memory ambiguity fixture panel");
        require(workspace.focusPanel(sourcePanelId), "Could not restore source-panel focus after ambiguity proof");
        require(Set.copyOf(workspace.panelIds()).equals(panelsBefore),
                "Ambiguity proof did not restore the original panel set");
        SFMSourcePuppetProbe.EditorHandle source = SFMSourcePuppetProbe.editor(workspace, sourceFile)
                .orElseThrow(() -> new IllegalStateException("Ambiguity proof lost the original source editor"));
        require(SFMSourcePuppetProbe.canonicalHash(source.state().documentSnapshot()
                        .orElseThrow().sha256().orElseThrow()).equals(sourceHash),
                "Ambiguity proof changed the original source editor");
        require(diskHash().equals(fixtureBaselineHash),
                "Ambiguity proof changed its immutable fixture baseline on disk");

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
                        .filter(document -> document.path().equals(Optional.of(selectedTargetAddress)))
                        .flatMap(document -> document.targetRange()
                                .filter(range -> SYMBOL.equals(safeTextAtRange(document.text(), range)))
                                .map(range -> new Target(candidate, document, range))))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparingLong(candidate -> candidate.editor().panelId().value()))
                .toList();
    }

    private String diskHash() {
        try {
            String text = Files.readString(fixturePath, StandardCharsets.UTF_8);
            return SFMSourcePuppetProbe.canonicalHash(
                    SFMTextDocumentSnapshot.literal(text).sha256().orElseThrow());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not verify the ambiguity fixture disk baseline", failure);
        }
    }

    private static String safeTextAtRange(String text, SFMTextDocumentRange range) {
        try {
            return SFMSourcePuppetProbe.textAtRange(text, range);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static Path normalize(Path value, String name) {
        return Objects.requireNonNull(value, name).toAbsolutePath().normalize();
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
