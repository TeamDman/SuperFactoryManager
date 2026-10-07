package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewAnalysisIdentityResolver;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewEditorCapture;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSelectorProposalAdapter;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewStore;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Two-process RCS-S1 proof over a staged copy of the complete real release review. */
public final class RealReleaseReviewJourneyPuppetAction implements SFMPuppetAction {
    public enum Operation {
        PREPARE_STAGE,
        PREPARE_EXACT_STAGE,
        CHOOSE_STRUCTURAL_NEEDS_CHANGE,
        ASSERT_STAGED,
        PREPARE_RESUME,
        ASSERT_RESUMED,
        CLEANUP
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAXIMUM_TICKS = SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 16;
    private static final String ACTIVE_QUERY = "remaining intersect 1.19.2 HEAD";
    private static final Path STAGED_PATH = Path.of(
            "sfm-puppet", "real-release-review-journey.sfm-review.json").toAbsolutePath().normalize();
    private static final Path MARKER_PATH = Path.of(
            "sfm-puppet", "real-release-review-journey.state.json").toAbsolutePath().normalize();

    private final Operation operation;
    private int ticks;
    private boolean openedChoice;
    private boolean collapsedSelection;
    private String proposalId;
    private String semanticKey;

    public RealReleaseReviewJourneyPuppetAction(Operation operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    public static Path stagedPath() {
        return STAGED_PATH;
    }

    public static String targetUnitId() {
        Path canonical = canonicalReviewPath();
        return chooseTarget(parse(canonical), canonical).id();
    }

    @Override
    public String description() {
        return "real release-review journey: " + operation.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++ticks > MAXIMUM_TICKS) {
            throw new IllegalStateException("Real release-review journey timed out during " + operation);
        }
        return switch (operation) {
            case PREPARE_STAGE, PREPARE_EXACT_STAGE -> prepareStage(runtime);
            case CHOOSE_STRUCTURAL_NEEDS_CHANGE -> chooseStructuralNeedsChange(runtime);
            case ASSERT_STAGED -> assertStaged(runtime);
            case PREPARE_RESUME -> prepareResume(runtime);
            case ASSERT_RESUMED -> assertResumed(runtime);
            case CLEANUP -> cleanup(runtime);
        };
    }

    private boolean prepareStage(ISFMGamePuppetRuntime runtime) {
        SFMReleaseReviewRuntime.get().discardAndClose();
        Path canonical = canonicalReviewPath();
        byte[] canonicalBytes = readBytes(canonical);
        SFMReleaseReviewV1 review = parse(canonical);
        SFMReleaseReviewV1.ReviewUnit target = operation == Operation.PREPARE_EXACT_STAGE
                ? review.reviewUnits().stream()
                        .filter(unit -> unit.pathAfter().orElse("").equals("platform/minecraft/src/main/java/ca/teamdman/sfm/SFM.java"))
                        .filter(unit -> !unit.afterRanges().isEmpty() && targetAfterRange(unit).endByte() - targetAfterRange(unit).startByte() > 20)
                        .filter(unit -> unit.beforeDocumentRevisionId().isPresent()
                                && unit.beforeRanges().stream().anyMatch(range -> range.endByte() > range.startByte()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("No real SFM.java before/after unit is available"))
                : chooseTarget(review, canonical);
        SFMReleaseReviewKernel.CompletionReport completion = SFMReleaseReviewKernel.completion(review);
        require(completion.status() == SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS,
                "The canonical initialized review must remain in_progress");
        require(review.completionAttestations().isEmpty(),
                "The canonical initialized review must not contain a maintainer attestation");
        try {
            Files.createDirectories(STAGED_PATH.getParent());
            Files.write(STAGED_PATH, canonicalBytes);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not stage the real release review", failure);
        }
        SFMReleaseReviewV1.CorpusDocument targetDocument = corpus(review,
                target.afterDocumentRevisionId().orElseThrow());
        SFMReleaseReviewV1.Utf8Range targetRange = targetAfterRange(target);
        JsonObject marker = new JsonObject();
        marker.addProperty("schema", "sfm.real-release-review-puppet/1");
        marker.addProperty("canonical_path", canonical.toString());
        marker.addProperty("canonical_sha256", SFMReleaseReviewKernel.sha256(canonicalBytes));
        marker.addProperty("staged_path", STAGED_PATH.toString());
        marker.addProperty("candidate_commit", review.repositoryBindings().get(0).candidateCommit());
        marker.addProperty("target_unit_id", target.id());
        marker.addProperty("target_document_revision_id", targetDocument.documentRevisionId());
        marker.addProperty("target_path", targetDocument.path());
        marker.addProperty("target_start_byte", targetRange.startByte());
        marker.addProperty("target_end_byte", targetRange.endByte());
        marker.addProperty("initial_comment_count", review.reviewSession().comments().size());
        marker.addProperty("stage_process_id", ProcessHandle.current().pid());
        marker.addProperty("active_query", ACTIVE_QUERY);
        writeMarker(marker);

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.real-release-review-stage-prepared/1");
        evidence.addProperty("canonical_path", canonical.toString());
        evidence.addProperty("canonical_sha256", marker.get("canonical_sha256").getAsString());
        evidence.addProperty("candidate_commit", marker.get("candidate_commit").getAsString());
        evidence.addProperty("changed_domain", completion.changedDomain());
        evidence.addProperty("target_unit_id", target.id());
        evidence.addProperty("target_path", targetDocument.path());
        evidence.addProperty("target_range", "[" + targetRange.startByte() + ".." + targetRange.endByte() + ")");
        runtime.writeArtifact("real-release-review-stage-prepared", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        return true;
    }

    private boolean chooseStructuralNeedsChange(ISFMGamePuppetRuntime runtime) {
        if (!openedChoice) {
            SFMScreenMultiplexer workspace = workspace();
            if (!(workspace.focusedPanelInstance() instanceof SFMTextEditorPanel editor)) return false;
            if (editor.currentJavaInteractionMap().isEmpty()) return false;
            SFMClientActionContext actionContext = new SFMClientActionContext(
                    workspace, () -> true, workspace.focusedPanelId());
            SFMReleaseReviewEditorCapture.Capture capture;
            try {
                capture = SFMReleaseReviewEditorCapture.capture(
                        actionContext,
                        workspace.contextSnapshot(),
                        SFMReleaseReviewRuntime.get().document().orElseThrow()
                );
            } catch (IllegalArgumentException notReady) {
                return false;
            }
            SFMReleaseReviewV1.SelectorProposal semantic = capture.proposals().proposals().stream()
                    .filter(value -> value.semanticProvider().orElse("").equals(
                            SFMReleaseReviewSelectorProposalAdapter.JAVA_INTERACTION_MAP_PROVIDER))
                    .sorted(Comparator
                            .comparingInt(RealReleaseReviewJourneyPuppetAction::proposalRank)
                            .thenComparing(value -> value.semanticKey().orElse(""))
                            .thenComparing(SFMReleaseReviewV1.SelectorProposal::id))
                    .findFirst().orElse(null);
            if (semantic == null) {
                SFMReleaseReviewV1.PinnedSelectionRange selected =
                        capture.adapted().pinnedSelection().ranges().get(0);
                JsonObject marker = readMarker();
                int targetStart = marker.get("target_start_byte").getAsInt();
                int targetEnd = marker.get("target_end_byte").getAsInt();
                if (!collapsedSelection && selected.startByte() != selected.endByte()) {
                    runtime.pressScreenKey(GLFW.GLFW_KEY_LEFT, 0);
                    collapsedSelection = true;
                    return false;
                }
                int cursor = selected.startByte();
                require(cursor >= targetStart && cursor <= targetEnd,
                        "Natural review cursor escaped the target hunk: " + cursor);
                require(cursor < targetEnd,
                        "No Java structural selector was discoverable inside the selected real diff hunk");
                runtime.pressScreenKey(GLFW.GLFW_KEY_RIGHT, 0);
                collapsedSelection = true;
                return false;
            }
            proposalId = semantic.id();
            semanticKey = semantic.semanticKey().orElseThrow();
            runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_ALT);
            openedChoice = true;
            return false;
        }

        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        String prefix = "sfm action invoke sfm:review/session/comment/create "
                + StringArgumentType.escapeIfRequired(proposalId) + " #needs-change ";
        String command = palette.choiceCommandsForAutomation().stream()
                .filter(value -> value.startsWith(prefix))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "The contextual palette omitted the selected Java structural needs-change action: "
                                + palette.choiceCommandsForAutomation()));
        if (!palette.choiceReadyForPointerAutomation(command)) return false;
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.real-release-review-structural-choice/1");
        evidence.addProperty("proposal_id", proposalId);
        evidence.addProperty("semantic_key", semanticKey);
        evidence.addProperty("provider_id",
                SFMReleaseReviewSelectorProposalAdapter.JAVA_INTERACTION_MAP_PROVIDER);
        evidence.addProperty("selected_command", command);
        runtime.writeArtifact("real-release-review-structural-choice", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        JsonObject marker = readMarker();
        marker.addProperty("proposal_id", proposalId);
        marker.addProperty("semantic_key", semanticKey);
        writeMarker(marker);
        runtime.clickActionChoice(command);
        return true;
    }

    private boolean assertStaged(ISFMGamePuppetRuntime runtime) {
        JsonObject marker = readMarker();
        String persistedProposalId = marker.get("proposal_id").getAsString();
        String persistedSemanticKey = marker.get("semantic_key").getAsString();
        SFMReleaseReviewV1 review = SFMReleaseReviewRuntime.get().document().orElse(null);
        if (review == null || review.reviewSession().comments().size()
                <= marker.get("initial_comment_count").getAsInt()) return false;
        SFMReleaseReviewV1.CommentSelectorBinding binding = review.selectorBindings().stream()
                .filter(value -> value.selectedProposal().semanticProvider().orElse("").equals(
                        SFMReleaseReviewSelectorProposalAdapter.JAVA_INTERACTION_MAP_PROVIDER))
                .filter(value -> value.selectedProposal().id().equals(persistedProposalId))
                .findFirst().orElse(null);
        if (binding == null) return false;
        SFMReviewSessionV2.Comment comment = review.reviewSession().comments().stream()
                .filter(value -> value.id().equals(binding.commentId())).findFirst().orElseThrow();
        require(comment.text().startsWith("#needs-change "),
                "The real structural review action must remain a non-approval blocker");
        require(review.completionAttestations().isEmpty(), "The real puppet must never attest");
        require(readMarker().get("target_document_revision_id").getAsString().equals(
                        binding.capturedSelection().ranges().get(0).documentRevisionId()),
                "The structural comment escaped its selected real corpus document");
        requireCanonicalUnchanged(marker);

        List<String> raw = query(review, "#approved intersect 1.19.2 HEAD");
        List<String> effective = query(review, "effective(#approved) intersect 1.19.2 HEAD");
        List<String> remaining = query(review, ACTIVE_QUERY);
        List<String> blocking = query(review, "blocking intersect 1.19.2 HEAD");
        require(blocking.contains(marker.get("target_unit_id").getAsString()),
                "The genuine #needs-change comment did not block its real review unit");
        SFMReleaseReviewKernel.CompletionReport completion = SFMReleaseReviewKernel.completion(review);
        require(completion.status() == SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS,
                "The staged review must remain fail-closed in_progress");

        marker.addProperty("comment_id", binding.commentId());
        marker.addProperty("proposal_id", persistedProposalId);
        marker.addProperty("semantic_key", persistedSemanticKey);
        marker.addProperty("staged_sha256", hash(STAGED_PATH));
        marker.addProperty("semantic_state_hash", completion.reviewSemanticStateHash());
        marker.addProperty("resume_current_unit", review.resumeState().currentUnitId().orElseThrow());
        marker.add("approved_raw", strings(raw));
        marker.add("approved_effective", strings(effective));
        marker.add("remaining", strings(remaining));
        marker.add("blocking", strings(blocking));
        addCompletion(marker, completion);
        writeMarker(marker);

        JsonObject evidence = marker.deepCopy();
        evidence.addProperty("schema", "sfm.real-release-review-stage-complete/1");
        runtime.writeArtifact("real-release-review-stage-complete", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        runtime.writeArtifact("real-release-review-staged-document-witness", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(documentWitness(review, marker, completion, "staged")));
        return true;
    }

    private boolean prepareResume(ISFMGamePuppetRuntime runtime) {
        SFMReleaseReviewRuntime.get().discardAndClose();
        JsonObject marker = readMarker();
        require(marker.has("comment_id"), "The stage process did not finish its structural review action");
        require(marker.get("stage_process_id").getAsLong() != ProcessHandle.current().pid(),
                "The resume proof must execute in a different JVM process");
        require(hash(STAGED_PATH).equals(marker.get("staged_sha256").getAsString()),
                "The staged review changed between process invocations");
        requireCanonicalUnchanged(marker);
        SFMReleaseReviewStore.MachineLocalState state = SFMReleaseReviewStore.machineLocalState(STAGED_PATH);
        try {
            Files.deleteIfExists(state.recovery());
            Files.deleteIfExists(state.writerLease());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not clear rebuildable release-review state", failure);
        }
        require(!Files.exists(state.recovery()) && !Files.exists(state.writerLease()),
                "Machine-local release-review state was not completely deleted before reopen");
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.real-release-review-resume-prepared/1");
        evidence.addProperty("stage_process_id", marker.get("stage_process_id").getAsLong());
        evidence.addProperty("resume_process_id", ProcessHandle.current().pid());
        evidence.addProperty("recovery_deleted", true);
        evidence.addProperty("writer_lease_deleted", true);
        evidence.addProperty("canonical_unchanged", true);
        runtime.writeArtifact("real-release-review-resume-prepared", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        return true;
    }

    private boolean assertResumed(ISFMGamePuppetRuntime runtime) {
        JsonObject marker = readMarker();
        SFMReleaseReviewRuntime reviewRuntime = SFMReleaseReviewRuntime.get();
        SFMReleaseReviewV1 review = reviewRuntime.document().orElse(null);
        if (review == null || reviewRuntime.path().isEmpty()) return false;
        require(reviewRuntime.path().orElseThrow().toAbsolutePath().normalize().equals(STAGED_PATH),
                "The resume process did not open the exact staged review path");
        require(!reviewRuntime.dirty(), "The durable staged review must reopen cleanly");
        require(hash(STAGED_PATH).equals(marker.get("staged_sha256").getAsString()),
                "Reopening from canonical bytes changed the staged review file");
        requireCanonicalUnchanged(marker);
        require(review.completionAttestations().isEmpty(), "The resume process must not attest");
        require(review.resumeState().currentUnitId().orElse("").equals(
                        marker.get("resume_current_unit").getAsString()),
                "The exact work cursor did not survive restart");
        require(review.reviewSession().comments().stream().anyMatch(value ->
                        value.id().equals(marker.get("comment_id").getAsString())
                                && value.text().startsWith("#needs-change ")),
                "The real structural comment did not survive restart");
        require(review.selectorBindings().stream().anyMatch(value ->
                        value.commentId().equals(marker.get("comment_id").getAsString())
                                && value.selectedProposal().semanticProvider().orElse("").equals(
                                SFMReleaseReviewSelectorProposalAdapter.JAVA_INTERACTION_MAP_PROVIDER)
                                && value.selectedProposal().semanticKey().orElse("").equals(
                                marker.get("semantic_key").getAsString())),
                "The Java structural selector provenance did not survive restart");
        require(query(review, "#approved intersect 1.19.2 HEAD").equals(strings(marker, "approved_raw")),
                "Raw approval query changed across restart");
        require(query(review, "effective(#approved) intersect 1.19.2 HEAD").equals(
                        strings(marker, "approved_effective")),
                "Effective approval query changed across restart");
        require(query(review, ACTIVE_QUERY).equals(strings(marker, "remaining")),
                "Remaining-work query changed across restart");
        require(query(review, "blocking intersect 1.19.2 HEAD").equals(strings(marker, "blocking")),
                "Blocking query changed across restart");
        SFMReleaseReviewKernel.CompletionReport completion = SFMReleaseReviewKernel.completion(review);
        require(completion.reviewSemanticStateHash().equals(marker.get("semantic_state_hash").getAsString()),
                "Completion semantic state changed across restart");
        require(completion.status() == SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS,
                "The reopened real review must remain in_progress");

        JsonObject evidence = marker.deepCopy();
        evidence.addProperty("schema", "sfm.real-release-review-resumed/1");
        evidence.addProperty("resume_process_id", ProcessHandle.current().pid());
        evidence.addProperty("cache_independent", true);
        evidence.addProperty("canonical_unchanged", true);
        runtime.writeArtifact("real-release-review-resumed", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        runtime.writeArtifact("real-release-review-resumed-document-witness", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(documentWitness(review, marker, completion, "resumed")));
        return true;
    }

    private boolean cleanup(ISFMGamePuppetRuntime runtime) {
        SFMReleaseReviewRuntime.get().discardAndClose();
        SFMReleaseReviewStore.MachineLocalState state = SFMReleaseReviewStore.machineLocalState(STAGED_PATH);
        try {
            Files.deleteIfExists(state.recovery());
            Files.deleteIfExists(state.writerLease());
            Files.deleteIfExists(STAGED_PATH);
            Files.deleteIfExists(MARKER_PATH);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not clean real release-review puppet state", failure);
        }
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.real-release-review-cleanup/1");
        evidence.addProperty("staged_deleted", !Files.exists(STAGED_PATH));
        evidence.addProperty("marker_deleted", !Files.exists(MARKER_PATH));
        runtime.writeArtifact("real-release-review-cleanup", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        return true;
    }

    private static SFMReleaseReviewV1.ReviewUnit chooseTarget(SFMReleaseReviewV1 review, Path reviewPath) {
        List<String> remaining = query(review, ACTIVE_QUERY);
        return review.reviewUnits().stream()
                .filter(value -> remaining.contains(value.id()))
                .filter(value -> value.laneId().equals("1.19.2"))
                .filter(value -> value.pathAfter().map(path -> path.endsWith(".java")).orElse(false))
                .filter(value -> value.surfaceKind() == SFMReleaseReviewV1.SurfaceKind.DIFF_HUNK)
                .filter(value -> value.operation() == SFMReleaseReviewV1.ChangeOperation.MODIFIED
                        || value.operation() == SFMReleaseReviewV1.ChangeOperation.RENAMED)
                .filter(value -> value.afterDocumentRevisionId().isPresent() && !value.afterRanges().isEmpty())
                .filter(value -> value.afterRanges().stream()
                        .anyMatch(range -> range.startByte() < range.endByte()))
                .filter(value -> SFMReleaseReviewAnalysisIdentityResolver.resolve(
                        review, reviewPath, value.afterDocumentRevisionId().orElseThrow()).isPresent())
                .sorted(Comparator
                        .comparingInt((SFMReleaseReviewV1.ReviewUnit value) ->
                                targetAfterRange(value).endByte() - targetAfterRange(value).startByte())
                        .thenComparing(value -> value.pathAfter().orElse(""))
                        .thenComparing(SFMReleaseReviewV1.ReviewUnit::id))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "The complete real review contains no hash-identical modified Java diff hunk"));
    }

    private static SFMReleaseReviewV1.Utf8Range targetAfterRange(SFMReleaseReviewV1.ReviewUnit unit) {
        return unit.afterRanges().stream()
                .filter(range -> range.startByte() < range.endByte())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Real structural review requires a non-empty candidate-side hunk: " + unit.id()));
    }

    private static int proposalRank(SFMReleaseReviewV1.SelectorProposal value) {
        return switch (value.kind()) {
            case BODY -> 0;
            case SIGNATURE -> 1;
            case DECLARATION -> 2;
            case RETURN_TYPE -> 3;
            case SYMBOL -> 4;
            case BOUNDED_MULTI_REGION -> 5;
            case LITERAL -> 6;
        };
    }

    private static SFMScreenMultiplexer workspace() {
        if (Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace) return workspace;
        throw new IllegalStateException("The real release-review journey requires an SFM workspace");
    }

    private static Path canonicalReviewPath() {
        Path root = repositoryRoot();
        Path directory = root.resolve("docs/reviews");
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("No canonical docs/reviews directory exists at " + directory);
        }
        try (var files = Files.list(directory)) {
            List<Path> reviews = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sfm-review.json"))
                    .sorted()
                    .toList();
            if (reviews.size() != 1) {
                throw new IllegalStateException("Expected exactly one canonical release review, found " + reviews);
            }
            return reviews.get(0).toAbsolutePath().normalize();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not enumerate canonical release reviews", failure);
        }
    }

    private static Path repositoryRoot() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            if (Files.exists(cursor.resolve(".git"))
                    && Files.isRegularFile(cursor.resolve("docs/AGENTS.md"))) return cursor;
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate canonical SFM repository root");
    }

    static SFMReleaseReviewV1 parse(Path path) {
        try {
            SFMReleaseReviewV1 review = SFMReleaseReviewV1Codec.parse(
                    Files.readString(path, StandardCharsets.UTF_8));
            SFMReleaseReviewKernel.validate(review);
            return review;
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read release review " + path, failure);
        }
    }

    private static SFMReleaseReviewV1.CorpusDocument corpus(
            SFMReleaseReviewV1 review,
            String revisionId
    ) {
        return review.corpusDocuments().stream()
                .filter(value -> value.documentRevisionId().equals(revisionId))
                .findFirst().orElseThrow();
    }

    private static List<String> query(SFMReleaseReviewV1 review, String expression) {
        return SFMReleaseReviewKernel.query(review, expression).reviewUnitIds();
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read " + path, failure);
        }
    }

    static String hash(Path path) {
        return SFMReleaseReviewKernel.sha256(readBytes(path));
    }

    static JsonObject readMarker() {
        try {
            return JsonParser.parseString(Files.readString(MARKER_PATH, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read real release-review marker", failure);
        }
    }

    static void writeMarker(JsonObject marker) {
        try {
            Files.createDirectories(MARKER_PATH.getParent());
            Files.writeString(MARKER_PATH, GSON.toJson(marker), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not write real release-review marker", failure);
        }
    }

    static void requireCanonicalUnchanged(JsonObject marker) {
        Path canonical = Path.of(marker.get("canonical_path").getAsString());
        require(hash(canonical).equals(marker.get("canonical_sha256").getAsString()),
                "The authoritative initialized review was mutated by the real journey");
    }

    private static JsonArray strings(List<String> values) {
        JsonArray answer = new JsonArray();
        values.forEach(answer::add);
        return answer;
    }

    private static List<String> strings(JsonObject object, String name) {
        List<String> answer = new ArrayList<>();
        object.getAsJsonArray(name).forEach(value -> answer.add(value.getAsString()));
        return List.copyOf(answer);
    }

    private static void addCompletion(JsonObject object, SFMReleaseReviewKernel.CompletionReport completion) {
        object.addProperty("completion_status", completion.status().name().toLowerCase(Locale.ROOT));
        object.addProperty("changed_domain", completion.changedDomain());
        object.addProperty("approved_raw_count", completion.approvedRaw());
        object.addProperty("approved_effective_count", completion.approvedEffective());
        object.addProperty("remaining_count", completion.remaining());
        object.addProperty("blocking_count", completion.blocking());
        object.addProperty("suspended_count", completion.suspended());
        object.addProperty("missing_count", completion.missing());
        object.addProperty("unsupported_count", completion.unsupported());
    }

    /**
     * Keeps puppet evidence bounded while the repository-tracked review remains the complete authority.
     * The real review is intentionally tens of megabytes, so duplicating it into the one-megabyte puppet
     * artifact channel would obscure the stronger proof supplied by its exact byte hash and semantic hash.
     */
    private static JsonObject documentWitness(
            SFMReleaseReviewV1 review,
            JsonObject marker,
            SFMReleaseReviewKernel.CompletionReport completion,
            String phase
    ) {
        JsonObject witness = new JsonObject();
        witness.addProperty("schema", "sfm.real-release-review-document-witness/1");
        witness.addProperty("phase", phase);
        witness.addProperty("review_path", STAGED_PATH.toString());
        witness.addProperty("review_bytes", size(STAGED_PATH));
        witness.addProperty("review_sha256", hash(STAGED_PATH));
        witness.addProperty("review_schema", review.schema());
        witness.addProperty("candidate_commit", review.repositoryBindings().get(0).candidateCommit());
        witness.addProperty("review_units", review.reviewUnits().size());
        witness.addProperty("corpus_documents", review.corpusDocuments().size());
        witness.addProperty("comments", review.reviewSession().comments().size());
        witness.addProperty("selector_bindings", review.selectorBindings().size());
        witness.addProperty("migration_reports", review.migrationReports().size());
        witness.addProperty("completion_attestations", review.completionAttestations().size());
        witness.addProperty("semantic_state_hash", completion.reviewSemanticStateHash());
        review.resumeState().currentUnitId().ifPresent(value -> witness.addProperty("current_unit_id", value));
        for (String name : List.of("comment_id", "proposal_id", "semantic_key", "target_unit_id")) {
            if (marker.has(name)) witness.add(name, marker.get(name));
        }
        addCompletion(witness, completion);
        return witness;
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not inspect " + path, failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
