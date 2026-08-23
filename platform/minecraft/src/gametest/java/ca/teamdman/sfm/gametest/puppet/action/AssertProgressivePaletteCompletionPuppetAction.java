package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.action.SFMCompletionApplication;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Objects;

/** Machine-readable witness for the live progressive command-palette frontiers. */
public final class AssertProgressivePaletteCompletionPuppetAction implements SFMPuppetAction {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PREFIX = "sfm action invoke ";
    private static final String RECENT_OPEN_COMMAND =
            "sfm:panel/open sfm:text_editor sfm:text_editor_v3";

    private final Stage stage;
    private final String artifactName;
    private int stableTicks;

    public AssertProgressivePaletteCompletionPuppetAction(Stage stage, String artifactName) {
        this.stage = Objects.requireNonNull(stage, "stage");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Palette artifact name must not be blank");
        }
        this.artifactName = artifactName;
    }

    @Override
    public String description() {
        return "assert progressive palette stage " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        if (!ready(palette)) {
            stableTicks = 0;
            return false;
        }
        if (++stableTicks <= SFMGamePuppetHelper.RENDER_SETTLE_TICKS) return false;
        assertStage(palette);
        runtime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence(palette)));
        return true;
    }

    private boolean ready(SFMCommandPaletteScreen palette) {
        if (!palette.suggestionsMatchCurrentInputForAutomation()) return false;
        List<SFMCommandPaletteScreen.PaletteCandidateAutomationSnapshot> candidates =
                palette.candidateSnapshotsForAutomation();
        return switch (stage) {
            case BLANK_MRU, OPEN_QUERY, SCENE_FRONTIER, TRAJECTORY_SELECTOR, OVERLAY_SELECTOR,
                    OVERLAY_VISIBILITY, USAGE_HINT -> !candidates.isEmpty();
            case ACTION_BOUNDARY, REDO_BOUNDARY, STRICT_DESCENDANT ->
                    palette.lastCompletionApplication().isPresent();
            case UNDO_QUERY -> true;
        };
    }

    private void assertStage(SFMCommandPaletteScreen palette) {
        List<SFMCommandPaletteScreen.PaletteCandidateAutomationSnapshot> candidates =
                palette.candidateSnapshotsForAutomation();
        switch (stage) {
            case BLANK_MRU -> {
                require(replacement(candidates, 0).equals(RECENT_OPEN_COMMAND),
                        "blank palette did not preserve complete MRU-first behavior");
                require(candidates.get(0).kind().equals("COMPLETE_HISTORY_COMMAND"),
                        "blank palette first row is not a complete historical command");
            }
            case OPEN_QUERY -> {
                require(replacement(candidates, 0).equals("sfm:panel/open"),
                        "open query did not rank the bare panel/open boundary first");
                require(candidates.stream().anyMatch(candidate ->
                                candidate.kind().equals("COMPLETE_HISTORY_COMMAND")
                                        && candidate.replacementText().equals(
                                        RECENT_OPEN_COMMAND)),
                        "open query did not retain the complete historical leaf");
            }
            case ACTION_BOUNDARY -> requireApplication(
                    palette, PREFIX + "open", PREFIX + "sfm:panel/open", "", "ACTION_BOUNDARY");
            case UNDO_QUERY -> require(palette.documentHistorySession().currentState().text().equals(expectedInput()),
                    "palette undo did not restore the typed query");
            case REDO_BOUNDARY -> {
                requireApplication(palette, PREFIX + "open", expectedInput(), "", "ACTION_BOUNDARY");
                require(palette.documentHistorySession().currentState().text().equals(expectedInput()),
                        "palette redo did not restore the accepted boundary");
            }
            case STRICT_DESCENDANT -> requireStrictDescendantApplication(palette);
            case SCENE_FRONTIER -> {
                SFMCommandPaletteScreen.PaletteCandidateAutomationSnapshot historical = candidates.stream()
                        .filter(candidate -> candidate.replacementText().equals("sfm:text_editor"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "scene frontier omitted the compatible text-editor history"));
                require(historical.origin().equals("COMMAND_HISTORY"),
                        "compatible scene history did not retain its history origin");
                require(historical.historyFamily() != null && !historical.historyFamily().isBlank(),
                        "compatible scene history omitted its semantic family");
            }
            case TRAJECTORY_SELECTOR -> {
                requireContains(candidates, "focused");
                requireContains(candidates, "all");
            }
            case OVERLAY_SELECTOR -> {
                requireContains(candidates, "focused");
                requireContains(candidates, "all");
                requireContains(candidates, "id(sfm%3Ahistory)");
            }
            case OVERLAY_VISIBILITY -> {
                requireContains(candidates, "visible");
                requireContains(candidates, "hidden");
            }
            case USAGE_HINT -> require(candidates.stream().anyMatch(candidate ->
                            !candidate.activatable()
                                    && candidate.kind().equals("USAGE_HINT")
                                    && candidate.displayText().contains("message")),
                    "unsuggested required message argument has no non-activatable usage row");
        }
    }

    private JsonObject evidence(SFMCommandPaletteScreen palette) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "sfm.progressive-palette-puppet/1");
        root.addProperty("stage", stage.name());
        root.addProperty("input", palette.inputForAutomation());
        root.addProperty("applied_suggestion_command", palette.appliedSuggestionCommandForAutomation());
        root.addProperty("applied_suggestion_revision", palette.appliedSuggestionRevisionForAutomation());
        JsonArray candidates = new JsonArray();
        for (SFMCommandPaletteScreen.PaletteCandidateAutomationSnapshot candidate
                : palette.candidateSnapshotsForAutomation()) {
            JsonObject value = new JsonObject();
            value.addProperty("order", candidate.order());
            value.addProperty("display_text", candidate.displayText());
            value.addProperty("activatable", candidate.activatable());
            value.addProperty("kind", candidate.kind());
            value.addProperty("origin", candidate.origin());
            value.addProperty("replacement_start", candidate.replacementStart());
            value.addProperty("replacement_end", candidate.replacementEnd());
            value.addProperty("replacement_text", candidate.replacementText());
            value.addProperty("completion_frontier", candidate.completionFrontier());
            value.addProperty("history_recency", candidate.historyRecency());
            if (candidate.historyFamily() == null) value.add("history_family", null);
            else value.addProperty("history_family", candidate.historyFamily());
            value.addProperty("insertion_intent", candidate.insertionIntent());
            candidates.add(value);
        }
        root.add("candidates", candidates);
        palette.lastCompletionApplication().ifPresent(application ->
                root.add("last_completion", completion(application)));
        root.addProperty("history_session", palette.documentHistorySessionId());
        root.addProperty("history_revision", palette.documentHistorySession().currentRevisionId());
        root.addProperty("history_generation", palette.documentHistorySession().generation());
        return root;
    }

    private static JsonObject completion(SFMCompletionApplication application) {
        JsonObject value = new JsonObject();
        value.addProperty("before", application.beforeValue());
        value.addProperty("replacement_start", application.replacementRange().getStart());
        value.addProperty("replacement_end", application.replacementRange().getEnd());
        value.addProperty("replacement_text", application.replacementText());
        value.addProperty("deliberate_separator", application.deliberateSeparator());
        value.addProperty("candidate_kind", application.candidateKind().name());
        value.addProperty("candidate_origin", application.candidateOrigin().name());
        value.addProperty("after", application.afterValue());
        return value;
    }

    private String expectedInput() {
        return switch (stage) {
            case BLANK_MRU -> PREFIX;
            case OPEN_QUERY, UNDO_QUERY -> PREFIX + "open";
            case ACTION_BOUNDARY, REDO_BOUNDARY -> PREFIX + "sfm:panel/open";
            case STRICT_DESCENDANT -> PREFIX + "sfm:panel/open/left";
            case SCENE_FRONTIER -> PREFIX + "sfm:panel/open ";
            case TRAJECTORY_SELECTOR -> PREFIX + "sfm:episode/trajectory/plan ";
            case OVERLAY_SELECTOR -> PREFIX + "sfm:overlay/visibility/set ";
            case OVERLAY_VISIBILITY -> PREFIX
                    + "sfm:overlay/visibility/set id(sfm%3Ahistory) ";
            case USAGE_HINT -> PREFIX + "sfm:echo ";
        };
    }

    private static String replacement(
            List<SFMCommandPaletteScreen.PaletteCandidateAutomationSnapshot> candidates,
            int index
    ) {
        require(candidates.size() > index, "palette candidate index is absent: " + index);
        return candidates.get(index).replacementText();
    }

    private static void requireContains(
            List<SFMCommandPaletteScreen.PaletteCandidateAutomationSnapshot> candidates,
            String replacement
    ) {
        require(candidates.stream().anyMatch(candidate -> candidate.replacementText().equals(replacement)),
                "palette candidates omitted " + replacement);
    }

    private static void requireApplication(
            SFMCommandPaletteScreen palette,
            String before,
            String after,
            String separator,
            String kind
    ) {
        SFMCompletionApplication application = palette.lastCompletionApplication().orElseThrow();
        require(application.beforeValue().equals(before), "completion before value differs");
        require(application.afterValue().equals(after), "completion after value differs");
        require(application.deliberateSeparator().equals(separator), "completion separator differs");
        require(application.candidateKind().name().equals(kind), "completion candidate kind differs");
    }

    private static void requireStrictDescendantApplication(SFMCommandPaletteScreen palette) {
        SFMCompletionApplication application = palette.lastCompletionApplication().orElseThrow();
        String base = PREFIX + "sfm:panel/open";
        require(application.beforeValue().equals(base), "strict completion before value differs");
        require(application.afterValue().startsWith(base + "/"),
                "strict completion did not advance to a panel/open descendant");
        require(application.afterValue().equals(PREFIX + "sfm:panel/open/left"),
                "strict completion did not choose the deterministic panel/open/left descendant");
        require(!application.afterValue().equals(base), "strict completion repeated the current boundary");
        require(application.deliberateSeparator().isEmpty(),
                "strict completion inserted an argument separator");
        require(application.candidateKind().name().equals("ACTION_BOUNDARY"),
                "strict completion candidate kind differs");
        require(palette.inputForAutomation().equals(application.afterValue()),
                "palette input differs from the strict completion result");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public enum Stage {
        BLANK_MRU,
        OPEN_QUERY,
        ACTION_BOUNDARY,
        UNDO_QUERY,
        REDO_BOUNDARY,
        STRICT_DESCENDANT,
        SCENE_FRONTIER,
        TRAJECTORY_SELECTOR,
        OVERLAY_SELECTOR,
        OVERLAY_VISIBILITY,
        USAGE_HINT
    }
}
