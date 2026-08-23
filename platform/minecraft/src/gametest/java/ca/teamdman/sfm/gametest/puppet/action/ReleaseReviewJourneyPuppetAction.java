package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.context.SFMContextActionProvider;
import ca.teamdman.sfm.client.context.SFMContextActionRegistry;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewEvaluator;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewMigrationPipeline;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSemanticProviderRegistry;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSelectorProposalAdapter;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV1Kernel;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.review.explorer.SFMReviewExplorerPanel;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentRange;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSnapshot;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Natural RCS-5 journey through release-review selection, migration, persistence, and queries. */
public final class ReleaseReviewJourneyPuppetAction implements SFMPuppetAction {
    public enum Operation {
        PREPARE,
        CHOOSE_SEMANTIC_COMMENT,
        ASSERT_COMMENT,
        CHOOSE_RELOCATION,
        ASSERT_MIGRATION,
        ASSERT_REOPENED,
        CLEANUP
    }

    public static final String PROVIDER_ID = "sfm:puppet/release-review-fixture";
    public static final String CAFE_REVISION = "1.19.2:before:src/Cafe.java";
    public static final String AFTER_CAFE_REVISION = "1.19.2:after:src/Cafe.java";
    public static final String OTHER_REVISION = "1.19.2:after:src/Other.java";
    public static final String CAFE_UNIT = "unit:src/Cafe.java:value";
    public static final String OTHER_UNIT = "unit:src/Other.java:file";
    private static final int CAFE_START = 35;
    private static final int CAFE_END = 60;
    private static final int OTHER_START = 35;
    private static final int OTHER_END = 60;
    private static final int MAXIMUM_TICKS = SFMGamePuppetHelper.SCREEN_TIMEOUT_TICKS * 8;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object JOURNEY_LOCK = new Object();
    private static Journey journey;

    private record Journey(
            Path stagedPath,
            SFMReleaseReviewSemanticProviderRegistry.Registration providerRegistration,
            int initialCommentCount,
            String candidateCommit,
            Optional<String> relocatedMigrationId
    ) {}

    private final Operation operation;
    private int ticks;
    private boolean openedChoice;

    public ReleaseReviewJourneyPuppetAction(Operation operation) {
        this.operation = Objects.requireNonNull(operation, "operation");
    }

    /** Stable staged path shared by the declarative puppet and its actions. */
    public static Path stagedPath() {
        return Path.of("sfm-puppet", "release-review-journey.sfm-review.json")
                .toAbsolutePath().normalize();
    }

    @Override
    public String description() {
        return "release-review journey: " + operation.name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        if (++ticks > MAXIMUM_TICKS) {
            throw new IllegalStateException("Release-review journey timed out during " + operation);
        }
        return switch (operation) {
            case PREPARE -> prepare(runtime);
            case CHOOSE_SEMANTIC_COMMENT -> chooseSemanticComment(runtime);
            case ASSERT_COMMENT -> assertComment(runtime);
            case CHOOSE_RELOCATION -> chooseRelocation(runtime);
            case ASSERT_MIGRATION -> assertMigration(runtime);
            case ASSERT_REOPENED -> assertReopened(runtime);
            case CLEANUP -> cleanup(runtime);
        };
    }

    private boolean prepare(ISFMGamePuppetRuntime runtime) {
        synchronized (JOURNEY_LOCK) {
            if (journey != null) journey.providerRegistration().close();
            SFMReleaseReviewRuntime.get().close();
            Path fixturePath = findFixture();
            SFMReleaseReviewV1 fixture;
            try {
                fixture = SFMReleaseReviewV1Codec.parse(Files.readString(fixturePath, StandardCharsets.UTF_8));
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read release-review fixture " + fixturePath, failure);
            }
            SFMReleaseReviewV1 staged = withMigrationSources(fixture);
            SFMReleaseReviewKernel.validate(staged);
            Path stagedPath = stagedPath();
            try {
                Files.createDirectories(stagedPath.getParent());
                Files.writeString(stagedPath, SFMReleaseReviewV1Codec.write(staged), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not stage release-review journey " + stagedPath, failure);
            }
            String candidateCommit = staged.repositoryBindings().get(0).candidateCommit();
            String sourceSnapshotId = staged.reviewSession().revisionLanes().get(0).before().id();
            var registration = SFMReleaseReviewSemanticProviderRegistry.global().register(
                    fixtureSemanticProvider(sourceSnapshotId));
            journey = new Journey(
                    stagedPath,
                    registration,
                    staged.reviewSession().comments().size(),
                    candidateCommit,
                    Optional.empty()
            );

            JsonObject evidence = baseEvidence("sfm.release-review-journey-prepared/1", staged);
            evidence.addProperty("fixture", fixturePath.toString());
            evidence.addProperty("staged_path", stagedPath.toString());
            evidence.addProperty("semantic_provider", registration.providerId());
            evidence.add("migration_source_selectors", strings(staged.selectorBindings().stream()
                    .map(value -> value.selectedProposal().id()).toList()));
            evidence.add("raw_approved", strings(query(staged, "#approved intersect 1.19.2 HEAD")));
            evidence.add("effective_approved", strings(query(
                    staged, "effective(#approved) intersect 1.19.2 HEAD")));
            runtime.writeArtifact("release-review-prepared", SFMGamePuppetArtifactFormat.JSON,
                    GSON.toJson(evidence));
        }
        return true;
    }

    private boolean chooseSemanticComment(ISFMGamePuppetRuntime runtime) {
        if (!openedChoice) {
            SFMScreenMultiplexer workspace = workspaceOrNull();
            if (workspace == null || !(workspace.focusedPanelInstance() instanceof SFMTextEditorPanel editor)) {
                return false;
            }
            SFMTextDocumentSnapshot snapshot = editor.documentSnapshot().orElse(null);
            if (snapshot == null || !snapshot.ready()) return false;
            boolean cafeRevision = snapshot.path()
                    .filter(path -> !path.segments().isEmpty())
                    .map(path -> path.segments().get(0).equals(CAFE_REVISION))
                    .orElse(false);
            if (!cafeRevision) return false;
            SFMTextDocumentRange target = snapshot.targetRange().orElse(null);
            if (target == null || target.start().byteOffset() != CAFE_START
                    || target.end().byteOffset() != CAFE_END) {
                throw new IllegalStateException("Review source did not retain the exact Café target range: "
                        + snapshot.targetRange());
            }
            SFMClientActionContext actionContext = new SFMClientActionContext(
                    workspace, () -> true, workspace.focusedPanelId());
            SFMContextActionRegistry.Resolution resolution = SFMContextActionRegistry.minecraftDefaults().resolve(
                    SFMContextActionProvider.Request.capture(actionContext, workspace.contextSnapshot()));
            boolean releaseReviewChoice = resolution.choices().stream().anyMatch(choice ->
                    choice.command().startsWith(
                            "sfm action invoke sfm:review/session/comment/create "));
            if (!releaseReviewChoice) {
                throw new IllegalStateException("Release-review context provider produced no choices; diagnostics="
                        + resolution.diagnostics());
            }
            runtime.pressScreenKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_ALT);
            openedChoice = true;
            return false;
        }

        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        List<String> choices = palette.choiceCommandsForAutomation();
        String prefix = "sfm action invoke sfm:review/session/comment/create ";
        List<String> literal = choices.stream()
                .filter(value -> value.startsWith(prefix + "\"selector-proposal:literal:"))
                .filter(value -> value.contains(" #approved "))
                .toList();
        List<String> semantic = choices.stream()
                .filter(value -> value.startsWith(prefix + "\"selector-proposal:semantic:"))
                .filter(value -> value.contains(" #approved "))
                .toList();
        require(literal.size() == 1, "Contextual palette must retain one literal approval choice: " + choices);
        require(semantic.size() == 1, "Contextual palette must expose one semantic approval choice: " + choices);
        String command = semantic.get(0);
        if (!palette.choiceReadyForPointerAutomation(command)) return false;

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.release-review-semantic-choice/1");
        evidence.add("choices", strings(choices));
        evidence.addProperty("literal_command", literal.get(0));
        evidence.addProperty("selected_command", command);
        runtime.writeArtifact("release-review-semantic-choice", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        runtime.clickActionChoice(command);
        return true;
    }

    private boolean assertComment(ISFMGamePuppetRuntime runtime) {
        Journey journey = requireJourney();
        SFMReleaseReviewV1 document = SFMReleaseReviewRuntime.get().document().orElse(null);
        if (document == null || document.reviewSession().comments().size() <= journey.initialCommentCount()) {
            return false;
        }
        SFMReleaseReviewV1.CommentSelectorBinding binding = document.selectorBindings().stream()
                .filter(value -> value.selectedProposal().semanticProvider().orElse("").equals(PROVIDER_ID))
                .filter(value -> value.selectedProposal().semanticKey().orElse("")
                        .equals("fixture.Café#value():body"))
                .findFirst().orElse(null);
        if (binding == null) return false;
        SFMReviewSessionV2.Comment comment = document.reviewSession().comments().stream()
                .filter(value -> value.id().equals(binding.commentId())).findFirst().orElseThrow();
        require(comment.text().startsWith("#approved "), "Semantic review comment lost #approved");
        require(binding.selectedProposal().kind() == SFMReleaseReviewV1.SelectorKind.BODY,
                "The chosen semantic proposal is not a method body");
        require(binding.selectedProposal().semanticKey().orElse("")
                        .equals("fixture.Café#value():body"),
                "The chosen semantic key changed");
        require(binding.selectedProposal().literalWitness().equals(binding.capturedSelection()),
                "The semantic choice did not retain the exact literal witness");
        require(binding.capturedSelection().ranges().size() == 1,
                "The natural editor selection must remain one exact range");
        SFMReleaseReviewV1.PinnedSelectionRange witness = binding.capturedSelection().ranges().get(0);
        require(witness.direction() == SFMReleaseReviewV1.SelectionDirection.BACKWARD,
                "The natural backward editor selection direction was lost");
        require(witness.documentRevisionId().equals(CAFE_REVISION)
                        && witness.startByte() == CAFE_START && witness.endByte() == CAFE_END,
                "The semantic comment's literal witness changed: " + witness);
        require(binding.selectedProposal().selectionRule() instanceof SFMReviewSessionV1.LiteralUtf8Range rule
                        && rule.documentRevisionId().equals(CAFE_REVISION)
                        && rule.startByte() == CAFE_START && rule.endByte() == CAFE_END,
                "The semantic body proposal did not compile to the exact bounded range");
        require(document.completionAttestations().isEmpty(), "The puppet must never attest for the maintainer");

        SFMReleaseReviewRuntime.MutationResult rebuilt = SFMReleaseReviewRuntime.get().rebuildMigrationReports(
                migrationEvidence(document),
                SFMReleaseReviewEvaluator.Limits.defaults(),
                SFMReleaseReviewMigrationPipeline.Transition.beforeToAfter());
        require(rebuilt.saved(), "Production migration report rebuild did not autosave: " + rebuilt.failure());
        document = SFMReleaseReviewRuntime.get().document().orElseThrow();
        SFMReleaseReviewV1.MigrationReport relocation = document.migrationReports().stream()
                .filter(value -> value.sourceSelectorId().equals(binding.selectedProposal().id()))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Production migration pipeline omitted the natural semantic selector"));
        require(relocation.sourceEvaluation().status() == SFMReleaseReviewV1.EvaluationStatus.EXACT,
                "Natural migration source is not exact: " + relocation.sourceEvaluation());
        require(relocation.candidateEvaluation().status() == SFMReleaseReviewV1.EvaluationStatus.RELOCATED,
                "Natural semantic selector did not produce witnessed relocation: "
                        + relocation.candidateEvaluation());
        Journey updatedJourney = new Journey(
                journey.stagedPath(), journey.providerRegistration(), journey.initialCommentCount(),
                journey.candidateCommit(), Optional.of(relocation.id()));
        synchronized (JOURNEY_LOCK) {
            ReleaseReviewJourneyPuppetAction.journey = updatedJourney;
        }
        journey = updatedJourney;
        requireCanonicalAutosave(document, journey.stagedPath());
        require(query(document, "#approved intersect 1.19.2 HEAD").equals(List.of(CAFE_UNIT)),
                "Raw approval before migration decision must cover only Café");
        require(query(document, "effective(#approved) intersect 1.19.2 HEAD").isEmpty(),
                "Unresolved migration evidence must suspend every Café approval");

        JsonObject evidence = baseEvidence("sfm.release-review-semantic-comment/1", document);
        evidence.addProperty("comment_id", binding.commentId());
        evidence.addProperty("proposal_id", binding.selectedProposal().id());
        evidence.addProperty("provider_id", binding.selectedProposal().semanticProvider().orElseThrow());
        evidence.addProperty("semantic_key", binding.selectedProposal().semanticKey().orElseThrow());
        evidence.addProperty("selection_direction", witness.direction().name().toLowerCase(java.util.Locale.ROOT));
        evidence.addProperty("selection_start_byte", witness.startByte());
        evidence.addProperty("selection_end_byte", witness.endByte());
        runtime.writeArtifact("release-review-comment-created", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        runtime.writeArtifact("release-review-document-after-comment", SFMGamePuppetArtifactFormat.JSON,
                SFMReleaseReviewV1Codec.write(document));
        return true;
    }

    private boolean chooseRelocation(ISFMGamePuppetRuntime runtime) {
        String relocationId = requireJourney().relocatedMigrationId().orElseThrow(() ->
                new IllegalStateException("Natural relocation report was not prepared"));
        if (!openedChoice) {
            SFMScreenMultiplexer workspace = workspaceOrNull();
            if (workspace == null
                    || !(workspace.focusedPanelInstance() instanceof SFMReviewExplorerPanel explorer)) {
                return false;
            }
            String expected = "release/migration/" + relocationId;
            if (!explorer.selectedNodeIdForAutomation().equals(expected)) {
                runtime.pressScreenKey(GLFW.GLFW_KEY_DOWN, 0);
                return false;
            }
            runtime.pressScreenKey(GLFW.GLFW_KEY_F10, GLFW.GLFW_MOD_SHIFT);
            openedChoice = true;
            return false;
        }
        if (!(Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette)) return false;
        List<String> choices = palette.choiceCommandsForAutomation();
        String expectedState = SFMReleaseReviewKernel.semanticStateHash(
                SFMReleaseReviewRuntime.get().document().orElseThrow());
        String prefix = "sfm action invoke sfm:review/session/migration/decide "
                + StringArgumentType.escapeIfRequired(relocationId) + " " + expectedState + " ";
        String command = choices.stream()
                .filter(value -> value.startsWith(prefix + "relocation-confirmed none "))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Migration choices omitted explicit relocation confirmation: " + choices));
        require(choices.stream().anyMatch(value -> value.startsWith(prefix + "retargeted 1 ")),
                "Migration choices omitted explicit retargeting");
        require(choices.stream().anyMatch(value -> value.startsWith(prefix + "selector-edited 1 ")),
                "Migration choices omitted explicit selector editing");
        require(choices.stream().anyMatch(value -> value.startsWith(prefix + "archived none ")),
                "Migration choices omitted archival");
        require(choices.stream().anyMatch(value -> value.startsWith(prefix + "deferred none ")),
                "Migration choices omitted deferral");
        if (!palette.choiceReadyForPointerAutomation(command)) return false;

        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", "sfm.release-review-migration-choice/1");
        evidence.add("choices", strings(choices));
        evidence.addProperty("selected_command", command);
        runtime.writeArtifact("release-review-migration-choice", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        runtime.clickActionChoice(command);
        return true;
    }

    private boolean assertMigration(ISFMGamePuppetRuntime runtime) {
        Journey journey = requireJourney();
        SFMReleaseReviewV1 document = SFMReleaseReviewRuntime.get().document().orElse(null);
        if (document == null) return false;
        String relocationId = journey.relocatedMigrationId().orElseThrow();
        SFMReleaseReviewV1.MigrationReport relocation = migration(document, relocationId);
        if (relocation.decision() != SFMReleaseReviewV1.MigrationDecision.RELOCATION_CONFIRMED) return false;
        require(relocation.decisionCommentId().isPresent(), "Relocation decision has no ordinary comment");
        require(document.reviewSession().comments().stream().anyMatch(comment ->
                        comment.id().equals(relocation.decisionCommentId().orElseThrow())
                                && comment.text().contains("#migration-decision")
                                && comment.text().contains("#relocation-confirmed")),
                "Relocation decision is not represented by an ordinary comment");
        List<SFMReleaseReviewV1.EvaluationStatus> unresolvedStatuses = document.migrationReports().stream()
                .filter(value -> !value.id().equals(relocationId))
                .peek(value -> require(
                        value.decision() == SFMReleaseReviewV1.MigrationDecision.UNRESOLVED,
                        value.id() + " must remain explicitly unresolved"))
                .map(value -> value.candidateEvaluation().status())
                .sorted()
                .toList();
        require(unresolvedStatuses.equals(List.of(
                        SFMReleaseReviewV1.EvaluationStatus.CONTENT_CHANGED,
                        SFMReleaseReviewV1.EvaluationStatus.AMBIGUOUS,
                        SFMReleaseReviewV1.EvaluationStatus.MISSING).stream().sorted().toList()),
                "Production migration matrix lost a terminal state: " + unresolvedStatuses);
        assertPostMigrationQueries(document);
        requireCanonicalAutosave(document, journey.stagedPath());

        SFMReleaseReviewV1.CommentSelectorBinding migrated = document.selectorBindings().stream()
                .filter(value -> value.selectedProposal().id().equals(relocation.sourceSelectorId()))
                .findFirst().orElseThrow();
        require(migrated.selectedProposal().literalWitness().ranges().get(0).documentRevisionId()
                        .equals(CAFE_REVISION),
                "Migration discarded the original Café literal witness");
        require(migrated.selectedProposal().selectionRule() instanceof SFMReviewSessionV1.LiteralUtf8Range rule
                        && rule.documentRevisionId().equals(OTHER_REVISION),
                "Relocation decision did not retarget the effective rule to Other.java");

        JsonObject evidence = baseEvidence("sfm.release-review-migration-resolved/1", document);
        evidence.addProperty("migration_id", relocation.id());
        evidence.addProperty("decision", relocation.decision().name().toLowerCase(java.util.Locale.ROOT));
        evidence.addProperty("decision_comment_id", relocation.decisionCommentId().orElseThrow());
        addQueries(evidence, document);
        addCompletion(evidence, SFMReleaseReviewKernel.completion(document));
        runtime.writeArtifact("release-review-migration-resolved", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        return true;
    }

    private boolean assertReopened(ISFMGamePuppetRuntime runtime) {
        Journey journey = requireJourney();
        SFMReleaseReviewRuntime reviewRuntime = SFMReleaseReviewRuntime.get();
        SFMReleaseReviewV1 document = reviewRuntime.document().orElse(null);
        if (document == null || reviewRuntime.path().isEmpty()) return false;
        require(reviewRuntime.path().orElseThrow().toAbsolutePath().normalize().equals(journey.stagedPath()),
                "The reopened runtime does not own the staged repository-style file");
        require(!reviewRuntime.dirty(), "A canonical reopen must not depend on recovery bytes");
        requireCanonicalAutosave(document, journey.stagedPath());
        require(document.selectorBindings().stream().anyMatch(binding ->
                        binding.selectedProposal().semanticProvider().orElse("").equals(PROVIDER_ID)),
                "The structural semantic comment did not survive reopen");
        require(migration(document, journey.relocatedMigrationId().orElseThrow()).decision()
                        == SFMReleaseReviewV1.MigrationDecision.RELOCATION_CONFIRMED,
                "The explicit relocation decision did not survive reopen");
        assertPostMigrationQueries(document);
        require(document.resumeState().activeQueryExpression().orElse("").equals("remaining-work"),
                "The reopened active query is not the named remaining-work queue");
        require(document.resumeState().currentUnitId().orElse("").equals(CAFE_UNIT),
                "The resumable work cursor is not the sole remaining unit");
        SFMReleaseReviewKernel.CompletionReport completion = SFMReleaseReviewKernel.completion(document);
        require(completion.status() == SFMReleaseReviewKernel.CompletionStatus.IN_PROGRESS,
                "An incomplete review must fail closed as in_progress");
        require(completion.changedDomain() == 2 && completion.approvedRaw() == 2
                        && completion.approvedEffective() == 1 && completion.remaining() == 1,
                "Completion counts do not match the two-unit staged domain: " + completion);
        require(completion.blocking() == 0 && completion.suspended() == 1 && completion.missing() == 1,
                "Pending migration evidence is not represented fail-closed: " + completion);
        require(document.completionAttestations().isEmpty(), "The puppet must not create maintainer attestation");

        JsonObject evidence = baseEvidence("sfm.release-review-reopened/1", document);
        evidence.addProperty("path", journey.stagedPath().toString());
        evidence.addProperty("dirty", reviewRuntime.dirty());
        evidence.addProperty("active_query", document.resumeState().activeQueryExpression().orElse(""));
        evidence.addProperty("current_unit", document.resumeState().currentUnitId().orElse(""));
        addQueries(evidence, document);
        addCompletion(evidence, completion);
        runtime.writeArtifact("release-review-reopened", SFMGamePuppetArtifactFormat.JSON,
                GSON.toJson(evidence));
        runtime.writeArtifact("release-review-reopened-document", SFMGamePuppetArtifactFormat.JSON,
                SFMReleaseReviewV1Codec.write(document));
        return true;
    }

    private boolean cleanup(ISFMGamePuppetRuntime runtime) {
        synchronized (JOURNEY_LOCK) {
            if (journey == null) return true;
            boolean activeBefore = journey.providerRegistration().active();
            journey.providerRegistration().close();
            JsonObject evidence = new JsonObject();
            evidence.addProperty("schema", "sfm.release-review-journey-cleanup/1");
            evidence.addProperty("provider_active_before", activeBefore);
            evidence.addProperty("provider_active_after", journey.providerRegistration().active());
            evidence.addProperty("staged_file_retained", Files.isRegularFile(journey.stagedPath()));
            runtime.writeArtifact("release-review-cleanup", SFMGamePuppetArtifactFormat.JSON,
                    GSON.toJson(evidence));
            journey = null;
        }
        return true;
    }

    private static SFMReleaseReviewV1 withMigrationSources(SFMReleaseReviewV1 document) {
        SFMReviewSessionV1.DocumentRevision before = revision(document, CAFE_REVISION);
        byte[] beforeBytes = before.text().getBytes(StandardCharsets.UTF_8);
        byte[] selected = java.util.Arrays.copyOfRange(beforeBytes, CAFE_START, CAFE_END);
        SFMReviewSessionV1.LiteralUtf8Range literal = new SFMReviewSessionV1.LiteralUtf8Range(
                CAFE_REVISION, CAFE_START, CAFE_END, before.sha256(),
                SFMReviewSessionV1Kernel.sha256(selected));
        SFMReleaseReviewV1.PinnedSelection pinned = new SFMReleaseReviewV1.PinnedSelection(
                "selection:fixture:migration-source",
                "selection://fixture/migration-source",
                0,
                List.of(new SFMReleaseReviewV1.PinnedSelectionRange(
                        SFMReleaseReviewV1.SelectionDirection.FORWARD,
                        CAFE_REVISION,
                        before.sha256(),
                        CAFE_START,
                        CAFE_END))
        );

        ArrayList<SFMReviewSessionV2.Comment> comments = new ArrayList<>(
                document.reviewSession().comments().stream()
                        .filter(value -> !value.id().equals("human:approved-value"))
                        .toList());
        ArrayList<SFMReleaseReviewV1.CommentSelectorBinding> bindings = new ArrayList<>();
        for (String fixtureCase : List.of("content-changed", "ambiguous", "missing")) {
            String commentId = "fixture:migration-source:" + fixtureCase;
            String semanticKey = "fixture:migration:" + fixtureCase;
            String text = fixtureCase.equals("content-changed")
                    ? "#approved #migration-source Preserve approval only after explicit migration review."
                    : "#migration-source Deterministic " + fixtureCase + " migration evidence.";
            comments.add(new SFMReviewSessionV2.Comment(
                    commentId,
                    text,
                    new SFMReviewSessionV1.Provenance(
                            "generated", "sfm:puppet/release-review-migration-source", "1", List.of()),
                    new SFMReviewSessionV2.CommittedReviewTarget(literal)
            ));
            String fingerprint = SFMReleaseReviewKernel.sha256(
                    (commentId + "\n" + semanticKey).getBytes(StandardCharsets.UTF_8));
            SFMReleaseReviewV1.SelectorProposal proposal = new SFMReleaseReviewV1.SelectorProposal(
                    "selector-proposal:fixture:" + fixtureCase,
                    SFMReleaseReviewV1.SelectorKind.BODY,
                    literal,
                    pinned,
                    Optional.of(PROVIDER_ID),
                    Optional.of(semanticKey),
                    List.of(new SFMReleaseReviewV1.Evidence("fixture-case", fixtureCase)),
                    SFMReleaseReviewV1.ProposalConfidence.EXACT,
                    fingerprint,
                    document.reviewSession().revisionLanes().get(0).before().id(),
                    List.of("Prepared source for production migration-pipeline coverage")
            );
            bindings.add(new SFMReleaseReviewV1.CommentSelectorBinding(commentId, pinned, proposal));
        }
        SFMReviewSessionV2 session = new SFMReviewSessionV2(
                document.reviewSession().schema(), document.reviewSession().id(), document.reviewSession().title(),
                document.reviewSession().coordinateSystem(), document.reviewSession().revisionLanes(), comments,
                document.reviewSession().styleRules(), document.reviewSession().completionPolicy());
        return new SFMReleaseReviewV1(
                document.schema(), session, document.repositoryBindings(), document.corpusDocuments(),
                document.reviewUnits(), bindings, List.of(), document.namedQueries(),
                SFMReleaseReviewV1.ResumeState.empty(), document.producerGenerations(), List.of()
        );
    }

    private static SFMReleaseReviewEvaluator.PreparedEvidence migrationEvidence(SFMReleaseReviewV1 document) {
        SFMReviewSessionV1.DocumentRevision before = revision(document, CAFE_REVISION);
        SFMReviewSessionV1.DocumentRevision afterCafe = revision(document, AFTER_CAFE_REVISION);
        SFMReviewSessionV1.DocumentRevision afterOther = revision(document, OTHER_REVISION);
        SFMReleaseReviewV1.AddressedRange source = new SFMReleaseReviewV1.AddressedRange(
                CAFE_REVISION, CAFE_START, CAFE_END);
        SFMReleaseReviewV1.AddressedRange changed = new SFMReleaseReviewV1.AddressedRange(
                AFTER_CAFE_REVISION, CAFE_START, CAFE_END);
        SFMReleaseReviewV1.AddressedRange relocated = new SFMReleaseReviewV1.AddressedRange(
                OTHER_REVISION, OTHER_START, OTHER_END);
        String sourceHash = rangeHash(before, source);
        String changedHash = rangeHash(afterCafe, changed);
        String relocatedHash = rangeHash(afterOther, relocated);
        String generation = "fixture-evaluation-1";

        List<SFMReleaseReviewEvaluator.PreparedScope> scopes = List.of(
                preparedScope(before, SFMReleaseReviewEvaluator.DiffSide.BEFORE, generation),
                preparedScope(afterCafe, SFMReleaseReviewEvaluator.DiffSide.AFTER, generation),
                preparedScope(afterOther, SFMReleaseReviewEvaluator.DiffSide.AFTER, generation)
        );
        ArrayList<SFMReleaseReviewEvaluator.PreparedSemanticCandidate> candidates = new ArrayList<>();
        for (String key : List.of(
                "fixture.Café#value():body",
                "fixture:migration:content-changed",
                "fixture:migration:ambiguous",
                "fixture:migration:missing")) {
            candidates.add(preparedCandidate(
                    key + ":source", key, before, SFMReleaseReviewEvaluator.DiffSide.BEFORE,
                    source, sourceHash, generation));
        }
        candidates.add(preparedCandidate(
                "natural:relocated", "fixture.Café#value():body", afterOther,
                SFMReleaseReviewEvaluator.DiffSide.AFTER, relocated, relocatedHash, generation));
        candidates.add(preparedCandidate(
                "content:changed", "fixture:migration:content-changed", afterCafe,
                SFMReleaseReviewEvaluator.DiffSide.AFTER, changed, changedHash, generation));
        candidates.add(preparedCandidate(
                "ambiguous:cafe", "fixture:migration:ambiguous", afterCafe,
                SFMReleaseReviewEvaluator.DiffSide.AFTER, changed, changedHash, generation));
        candidates.add(preparedCandidate(
                "ambiguous:other", "fixture:migration:ambiguous", afterOther,
                SFMReleaseReviewEvaluator.DiffSide.AFTER, relocated, relocatedHash, generation));
        return new SFMReleaseReviewEvaluator.PreparedEvidence(scopes, candidates, List.of());
    }

    private static SFMReleaseReviewEvaluator.PreparedScope preparedScope(
            SFMReviewSessionV1.DocumentRevision document,
            SFMReleaseReviewEvaluator.DiffSide side,
            String generation
    ) {
        String fingerprint = SFMReleaseReviewKernel.sha256(
                ("scope\n" + document.id() + "\n" + document.sha256() + "\n" + side)
                        .getBytes(StandardCharsets.UTF_8));
        return new SFMReleaseReviewEvaluator.PreparedScope(
                PROVIDER_ID, generation, fingerprint, "1.19.2", document.path(), "java", side,
                true, true, false, List.of());
    }

    private static SFMReleaseReviewEvaluator.PreparedSemanticCandidate preparedCandidate(
            String identity,
            String semanticKey,
            SFMReviewSessionV1.DocumentRevision document,
            SFMReleaseReviewEvaluator.DiffSide side,
            SFMReleaseReviewV1.AddressedRange range,
            String contentHash,
            String generation
    ) {
        String fingerprint = SFMReleaseReviewKernel.sha256(
                ("candidate\n" + identity + "\n" + document.sha256() + "\n" + range)
                        .getBytes(StandardCharsets.UTF_8));
        return new SFMReleaseReviewEvaluator.PreparedSemanticCandidate(
                PROVIDER_ID, generation, fingerprint, "1.19.2", document.path(), "java", side,
                SFMReleaseReviewV1.SelectorKind.BODY, semanticKey, List.of(range), contentHash, List.of());
    }

    private static String rangeHash(
            SFMReviewSessionV1.DocumentRevision document,
            SFMReleaseReviewV1.AddressedRange range
    ) {
        byte[] bytes = document.text().getBytes(StandardCharsets.UTF_8);
        return SFMReviewSessionV1Kernel.sha256(
                java.util.Arrays.copyOfRange(bytes, range.startByte(), range.endByte()));
    }

    private static SFMReviewSessionV1.DocumentRevision revision(
            SFMReleaseReviewV1 document,
            String revisionId
    ) {
        return document.reviewSession().revisionLanes().stream()
                .flatMap(lane -> java.util.stream.Stream.concat(
                        lane.before().documents().stream(), lane.after().documents().stream()))
                .filter(value -> value.id().equals(revisionId))
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Fixture document revision is unavailable: " + revisionId));
    }

    private static SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider fixtureSemanticProvider(
            String sourceSnapshotId
    ) {
        return new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidenceProvider() {
            @Override
            public String id() {
                return PROVIDER_ID;
            }

            @Override
            public List<SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence> propose(
                    SFMReleaseReviewV1.PinnedSelection selection,
                    ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewSelectionAdapter.DocumentResolver documents
            ) {
                boolean exactCafe = selection.ranges().stream().anyMatch(range ->
                        range.documentRevisionId().equals(CAFE_REVISION)
                                && range.startByte() == CAFE_START && range.endByte() == CAFE_END);
                if (!exactCafe) return List.of();
                String fingerprint = SFMReleaseReviewKernel.sha256(
                        (PROVIDER_ID + "\nfixture.Café#value():body\n" + sourceSnapshotId)
                                .getBytes(StandardCharsets.UTF_8));
                return List.of(new SFMReleaseReviewSelectorProposalAdapter.SemanticEvidence(
                        SFMReleaseReviewV1.SelectorKind.BODY,
                        "fixture.Café#value():body",
                        List.of(new SFMReleaseReviewV1.AddressedRange(CAFE_REVISION, CAFE_START, CAFE_END)),
                        List.of(
                                new SFMReleaseReviewV1.Evidence("region-kind", "method-body"),
                                new SFMReleaseReviewV1.Evidence("puppet", "title-screen-release-review-journey")
                        ),
                        SFMReleaseReviewV1.ProposalConfidence.EXACT,
                        fingerprint,
                        sourceSnapshotId,
                        List.of("Exact deterministic puppet semantic witness")
                ));
            }
        };
    }

    private static void assertPostMigrationQueries(SFMReleaseReviewV1 document) {
        require(query(document, "#approved intersect 1.19.2 HEAD")
                        .equals(List.of(CAFE_UNIT, OTHER_UNIT)),
                "Raw approved query must contain both review units");
        require(query(document, "effective(#approved) intersect 1.19.2 HEAD")
                        .equals(List.of(OTHER_UNIT)),
                "Effective approval must contain only the explicitly relocated approval");
        require(query(document, "remaining-work").equals(List.of(CAFE_UNIT)),
                "Remaining-work must contain only the unresolved changed Café surface");
        require(query(document, "blocking").isEmpty(), "No blocking comment was introduced");
        require(query(document, "suspended").equals(List.of(CAFE_UNIT)),
                "The unresolved migration must suspend Café");
    }

    private static void requireCanonicalAutosave(SFMReleaseReviewV1 document, Path path) {
        String expected = SFMReleaseReviewV1Codec.write(document);
        String actual;
        try {
            actual = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read autosaved release-review file " + path, failure);
        }
        require(expected.equals(actual), "The repository-style review file differs from the runtime document");
        require(SFMReleaseReviewV1Codec.parse(actual).equals(document),
                "The autosaved review document did not round-trip canonically");
    }

    private static SFMReleaseReviewV1.MigrationReport migration(SFMReleaseReviewV1 document, String id) {
        return document.migrationReports().stream().filter(value -> value.id().equals(id))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing migration report " + id));
    }

    private static Journey requireJourney() {
        synchronized (JOURNEY_LOCK) {
            if (journey == null) throw new IllegalStateException("Release-review journey was not prepared");
            return journey;
        }
    }

    private static SFMScreenMultiplexer workspaceOrNull() {
        return Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace ? workspace : null;
    }

    private static Path findFixture() {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        for (int depth = 0; depth < 10 && cursor != null; depth++, cursor = cursor.getParent()) {
            Path candidate = cursor.resolve("docs/architecture/fixtures/release-review-v1.json");
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Unable to locate docs/architecture/fixtures/release-review-v1.json");
    }

    private static List<String> query(SFMReleaseReviewV1 document, String expression) {
        return SFMReleaseReviewKernel.query(document, expression).reviewUnitIds();
    }

    private static JsonObject baseEvidence(String schema, SFMReleaseReviewV1 document) {
        JsonObject evidence = new JsonObject();
        evidence.addProperty("schema", schema);
        evidence.addProperty("review_schema", document.schema());
        evidence.addProperty("candidate_commit", document.repositoryBindings().get(0).candidateCommit());
        evidence.addProperty("semantic_state_hash", SFMReleaseReviewKernel.semanticStateHash(document));
        evidence.addProperty("comments", document.reviewSession().comments().size());
        evidence.addProperty("selector_bindings", document.selectorBindings().size());
        evidence.addProperty("migrations", document.migrationReports().size());
        evidence.addProperty("attestations", document.completionAttestations().size());
        return evidence;
    }

    private static void addQueries(JsonObject evidence, SFMReleaseReviewV1 document) {
        JsonObject queries = new JsonObject();
        queries.add("approved", strings(query(document, "#approved intersect 1.19.2 HEAD")));
        queries.add("effective_approved", strings(query(
                document, "effective(#approved) intersect 1.19.2 HEAD")));
        queries.add("remaining", strings(query(document, "remaining-work")));
        queries.add("blocking", strings(query(document, "blocking")));
        queries.add("suspended", strings(query(document, "suspended")));
        evidence.add("queries", queries);
    }

    private static void addCompletion(
            JsonObject evidence,
            SFMReleaseReviewKernel.CompletionReport completion
    ) {
        JsonObject status = new JsonObject();
        status.addProperty("status", completion.status().name().toLowerCase(java.util.Locale.ROOT));
        status.addProperty("changed_domain", completion.changedDomain());
        status.addProperty("approved_raw", completion.approvedRaw());
        status.addProperty("approved_effective", completion.approvedEffective());
        status.addProperty("remaining", completion.remaining());
        status.addProperty("blocking", completion.blocking());
        status.addProperty("suspended", completion.suspended());
        status.addProperty("missing", completion.missing());
        status.addProperty("unsupported", completion.unsupported());
        JsonObject witnesses = new JsonObject();
        witnesses.add("changed_domain", strings(completion.witnesses().changedDomain()));
        witnesses.add("approved_raw", strings(completion.witnesses().approvedRaw()));
        witnesses.add("approved_effective", strings(completion.witnesses().approvedEffective()));
        witnesses.add("remaining", strings(completion.witnesses().remaining()));
        witnesses.add("blocking", strings(completion.witnesses().blocking()));
        witnesses.add("suspended", strings(completion.witnesses().suspended()));
        witnesses.add("missing", strings(completion.witnesses().missing()));
        witnesses.add("deferred", strings(completion.witnesses().deferred()));
        witnesses.add("unsupported", strings(completion.witnesses().unsupported()));
        witnesses.add("stale_producer", strings(completion.witnesses().staleProducer()));
        status.add("witnesses", witnesses);
        status.add("diagnostics", strings(completion.diagnostics()));
        evidence.add("completion", status);
    }

    private static JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
