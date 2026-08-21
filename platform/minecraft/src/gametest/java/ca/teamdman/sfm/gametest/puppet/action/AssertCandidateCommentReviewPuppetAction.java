package ca.teamdman.sfm.gametest.puppet.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Codec;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import ca.teamdman.sfm.gametest.puppet.ISFMGamePuppetRuntime;
import ca.teamdman.sfm.gametest.puppet.SFMGamePuppetArtifactFormat;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** End-to-end machine-readable evidence for persistent candidate comments. */
public record AssertCandidateCommentReviewPuppetAction(Stage stage, String artifactName)
        implements SFMPuppetAction {
    public static final String ROUTE_COMMENT = "candidate-human-1";
    public static final String ACTION_COMMENT = "candidate-human-2";
    public static final String STATE_COMMENT = "candidate-human-3";
    public static final String EXACT_GLYPH_COMMENT = "candidate-human-4";
    public static final String DIVERGENT_GLYPH_COMMENT = "candidate-human-5";
    private static final List<String> MAIN_CANDIDATE_IDS = List.of(
            ROUTE_COMMENT,
            ACTION_COMMENT,
            STATE_COMMENT,
            EXACT_GLYPH_COMMENT,
            DIVERGENT_GLYPH_COMMENT
    );
    private static final List<String> UNAVAILABLE_CANDIDATE_IDS = List.of(ROUTE_COMMENT, ACTION_COMMENT);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Journey journey = new Journey();

    public AssertCandidateCommentReviewPuppetAction {
        Objects.requireNonNull(stage, "stage");
        if (artifactName == null || artifactName.isBlank()) {
            throw new IllegalArgumentException("Candidate-comment artifact name must not be blank");
        }
    }

    @Override
    public String description() {
        return "assert candidate-comment review stage " + stage;
    }

    @Override
    public boolean tick(ISFMGamePuppetRuntime runtime) {
        SFMReviewSessionRuntime reviewRuntime = SFMReviewSessionRuntime.get();
        switch (stage) {
            case ROUTE_LABEL -> assertRouteLabel(reviewRuntime);
            case ACTION_LABEL -> assertActionLabel(reviewRuntime);
            case CREATED_ALL -> assertCreatedAll(reviewRuntime);
            case RETAINED_AFTER_REPLAN -> assertRetainedAfterReplan(reviewRuntime);
            case OLD_ROUTE_START_RESTORED -> assertOldRouteStartRestored(reviewRuntime);
            case EXACT_PROMOTED -> assertExactPromotion(reviewRuntime);
            case DIVERGENCE_REJECTED -> assertDivergenceRejected(reviewRuntime);
            case WITNESSED_MIGRATED -> assertWitnessedMigration(reviewRuntime);
            case UNAVAILABLE_ACCEPTED -> assertUnavailableAccepted(reviewRuntime);
            case FINAL_ROUNDTRIP -> assertFinalRoundtrip(reviewRuntime);
        }
        Map<String, Object> evidence = stage == Stage.FINAL_ROUNDTRIP
                ? finalEvidence(reviewRuntime)
                : stageEvidence(reviewRuntime);
        runtime.writeArtifact(artifactName, SFMGamePuppetArtifactFormat.JSON, GSON.toJson(evidence));
        if (stage == Stage.FINAL_ROUNDTRIP) {
            runtime.writeArtifact(
                    artifactName + "-summary",
                    SFMGamePuppetArtifactFormat.UTF8,
                    textSummary(reviewRuntime)
            );
        }
        return true;
    }

    static synchronized void beginJourney(String machineId) {
        journey = new Journey();
        journey.machineIds.put(CandidateCommentSessionPuppetAction.SessionRole.MAIN,
                requireText(machineId, "main machine id"));
    }

    static synchronized void bindUnavailableFixture(String machineId) {
        journey.machineIds.put(CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE,
                requireText(machineId, "unavailable fixture machine id"));
    }

    static synchronized String machineId(CandidateCommentSessionPuppetAction.SessionRole role) {
        String machineId = journey.machineIds.get(role);
        if (machineId == null) throw new IllegalStateException("Candidate-comment role is not bound: " + role);
        return machineId;
    }

    static synchronized void recordReload(
            CandidateCommentSessionPuppetAction.SessionRole role,
            SFMReviewSessionV2 reloaded
    ) {
        String expectedCanonical = journey.canonicalBeforeReload.get(role);
        if (expectedCanonical == null) {
            throw new IllegalStateException("No pre-reload candidate-comment snapshot was captured for " + role);
        }
        String actualCanonical = SFMReviewSessionV2Codec.write(reloaded);
        require(expectedCanonical.equals(actualCanonical), "persisted session changed across reload for " + role);
        journey.reloaded.put(role, true);
    }

    static synchronized void recordNavigation(
            CandidateCommentSessionPuppetAction.SessionRole role,
            String commentId,
            SFMReviewSessionV2.CandidateTrajectoryTarget target
    ) {
        journey.navigations.put(
                role.name() + "/" + commentId,
                new NavigationEvidence(
                        role,
                        commentId,
                        target.trajectoryPlanRevisionId(),
                        target.routeId(),
                        target.routeStepPosition(),
                        target.predictedStateId(),
                        target.predictedStateHash(),
                        target.projectionStatus().name(),
                        true
                )
        );
    }

    private static void assertRouteLabel(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        require(session.comments().size() == 1, "route-label stage must contain exactly one comment");
        SFMReviewSessionV2.CandidateTrajectoryTarget target = candidate(session, ROUTE_COMMENT);
        require(target.targetKind() == SFMReviewSessionV2.CandidateTargetKind.ROUTE,
                "first candidate comment must target the route");
        require(target.routeStepPosition() == 0, "route comment must pin frame zero");
        require(comment(session, ROUTE_COMMENT).text().equals("route-retained-edited"),
                "ordinary edit action did not persist the route label");
        requirePanelLabel(ROUTE_COMMENT, target);
        requireNoEffectiveApproval(session);
    }

    private static void assertActionLabel(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        require(session.comments().size() == 2, "action-label stage must contain exactly two comments");
        SFMReviewSessionV2.CandidateTrajectoryTarget target = candidate(session, ACTION_COMMENT);
        require(target.targetKind() == SFMReviewSessionV2.CandidateTargetKind.ACTION,
                "second candidate comment must target the action");
        require(target.routeStepPosition() == 1, "action comment must pin frame one");
        require(target.actionIntentId().isPresent(), "action target lost its action-intent identity");
        requirePanelLabel(ACTION_COMMENT, target);
        requireNoEffectiveApproval(session);
    }

    private static synchronized void assertCreatedAll(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        require(session.comments().size() == 5, "creation stage must contain five candidate comments");
        assertCandidate(session, ROUTE_COMMENT, SFMReviewSessionV2.CandidateTargetKind.ROUTE, 0);
        assertCandidate(session, ACTION_COMMENT, SFMReviewSessionV2.CandidateTargetKind.ACTION, 1);
        assertCandidate(session, STATE_COMMENT, SFMReviewSessionV2.CandidateTargetKind.STATE, 2);
        assertGlyph(session, EXACT_GLYPH_COMMENT, 3, 9);
        assertGlyph(session, DIVERGENT_GLYPH_COMMENT, 13, 20);
        SFMReviewSessionV2.CandidateTrajectoryTarget route = candidate(session, ROUTE_COMMENT);
        for (String id : MAIN_CANDIDATE_IDS) {
            SFMReviewSessionV2.CandidateTrajectoryTarget target = candidate(session, id);
            require(target.trajectoryPlanRevisionId().equals(route.trajectoryPlanRevisionId()),
                    "candidate comments do not share their immutable plan");
            require(target.routeId().equals(route.routeId()), "candidate comments do not share their route");
            journey.mainBaseline.put(id, fingerprint(target));
        }
        journey.oldPlanRevisionId = route.trajectoryPlanRevisionId();
        journey.oldRouteId = route.routeId();
        requirePanelLabel(STATE_COMMENT, candidate(session, STATE_COMMENT));
        requirePanelLabel(EXACT_GLYPH_COMMENT, candidate(session, EXACT_GLYPH_COMMENT));
        requireNoEffectiveApproval(session);
    }

    private static void assertRetainedAfterReplan(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        requireMainTargetsUnchanged(session);
        String machineId = machineId(CandidateCommentSessionPuppetAction.SessionRole.MAIN);
        SFMHistoryGraphRuntime.MachineSnapshot snapshot = SFMHistoryGraphRuntime.get().snapshotEvent()
                .machine(machineId).orElseThrow();
        String selectedPlan = snapshot.machine().selectedTrajectoryRevisionId().orElseThrow();
        require(!selectedPlan.equals(journey.oldPlanRevisionId), "replan did not select a new plan revision");
        require(snapshot.planBook().plans().stream().anyMatch(plan -> plan.id().equals(journey.oldPlanRevisionId)),
                "replan discarded the commented old plan");
        require(snapshot.planBook().plans().size() >= 2, "replan did not retain both immutable plan revisions");
        SFMCandidateHistoryPanel panel = focusedCandidatePanel();
        require(panel.pinnedPlanRevisionId().equals(Optional.of(journey.oldPlanRevisionId)),
                "open candidate panel silently followed the replan");
        require(panel.pinnedRouteId().equals(Optional.of(journey.oldRouteId)),
                "open candidate panel silently changed its route");
        require(panel.currentPosition() == 2, "old candidate panel moved away from its numbered frame");
        requireNoPromotionLinks(session);
        requireNoEffectiveApproval(session);
    }

    private static void assertOldRouteStartRestored(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        requireMainTargetsUnchanged(session);
        SFMReviewSessionV2.CandidateTrajectoryTarget route = candidate(session, ROUTE_COMMENT);
        SFMHistoryGraphRuntime.CommittedDocument committed = SFMHistoryGraphRuntime.get()
                .committedDocument(machineId(CandidateCommentSessionPuppetAction.SessionRole.MAIN))
                .orElseThrow();
        require(route.predictedStateId().equals(committed.stateId()),
                "natural rewind did not restore the retained route's frame-zero state id");
        require(route.predictedStateHash().equals(Optional.of(committed.stateHash())),
                "natural rewind did not restore the retained route's frame-zero state hash");
        requireNoPromotionLinks(session);
        requireNoEffectiveApproval(session);
    }

    private static void assertExactPromotion(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        requireMainTargetsUnchanged(session);
        SFMReviewSessionV2.CandidateTrajectoryTarget exact = candidate(session, EXACT_GLYPH_COMMENT);
        SFMHistoryGraphRuntime.CommittedDocument committed = SFMHistoryGraphRuntime.get()
                .committedDocument(machineId(CandidateCommentSessionPuppetAction.SessionRole.MAIN))
                .orElseThrow();
        require(exact.predictedStateHash().equals(Optional.of(committed.stateHash())),
                "exact execution did not reach the pinned candidate state hash");
        List<SFMReviewSessionV2.CandidatePromotionLink> links = promotionLinks(session);
        require(links.size() == 1, "exact promotion must create exactly one committed child");
        SFMReviewSessionV2.CandidatePromotionLink link = links.get(0);
        require(link.sourceCandidateCommentId().equals(EXACT_GLYPH_COMMENT),
                "exact promotion linked the wrong candidate comment");
        require(link.decisionId().equals("candidate-comment-exact"), "exact promotion decision id differs");
        require(link.executedStateHash().equals(committed.stateHash()), "promotion lost executed-state evidence");
        require(link.correspondence().equals("exact"), "promotion correspondence is not exact");
        require(promotionLinksFor(session, DIVERGENT_GLYPH_COMMENT).isEmpty(),
                "divergence guard comment was promoted prematurely");
        requireNoEffectiveApproval(session);
    }

    private static void assertDivergenceRejected(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        requireMainTargetsUnchanged(session);
        SFMReviewSessionV2.CandidateTrajectoryTarget divergent = candidate(session, DIVERGENT_GLYPH_COMMENT);
        SFMHistoryGraphRuntime.CommittedDocument committed = SFMHistoryGraphRuntime.get()
                .committedDocument(machineId(CandidateCommentSessionPuppetAction.SessionRole.MAIN))
                .orElseThrow();
        require(!divergent.predictedStateHash().equals(Optional.of(committed.stateHash())),
                "divergent execution unexpectedly matched the old candidate state");
        require(promotionLinksFor(session, DIVERGENT_GLYPH_COMMENT).isEmpty(),
                "failed exact promotion created a committed child");
        require(promotionLinksFor(session, EXACT_GLYPH_COMMENT).size() == 1,
                "divergent execution altered the prior exact promotion");
        require(session.comments().size() == 6,
                "divergent exact promotion must not add or remove a comment");
        requireNoEffectiveApproval(session);
    }

    private static synchronized void assertWitnessedMigration(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = mainSession(runtime);
        requireMainTargetsUnchanged(session);
        require(session.comments().size() == 7,
                "explicit witnessed migration must add exactly one committed child");
        require(promotionLinksFor(session, EXACT_GLYPH_COMMENT).size() == 1,
                "witnessed migration altered the prior exact promotion");
        List<SFMReviewSessionV2.CandidatePromotionLink> witnessed =
                promotionLinksFor(session, DIVERGENT_GLYPH_COMMENT);
        require(witnessed.size() == 1, "explicit witnessed migration did not create one linked child");
        SFMReviewSessionV2.CandidatePromotionLink link = witnessed.get(0);
        require(link.correspondence().equals("witnessed_migration"),
                "divergent child is not labelled as witnessed migration");
        require(link.decisionId().equals("candidate-comment-witnessed"),
                "witnessed migration decision id differs");
        require(link.correspondenceEvidence().equals(List.of(
                        "same-list-item-bananas-after-three-item-divergence")),
                "witnessed migration lost its explicit correspondence evidence");
        SFMHistoryGraphRuntime.CommittedDocument committed = SFMHistoryGraphRuntime.get()
                .committedDocument(machineId(CandidateCommentSessionPuppetAction.SessionRole.MAIN))
                .orElseThrow();
        require(link.executedStateHash().equals(committed.stateHash()),
                "witnessed migration lost the divergent execution hash");
        byte[] bytes = committed.text().getBytes(StandardCharsets.UTF_8);
        require(bytes.length >= 32
                        && new String(Arrays.copyOfRange(bytes, 25, 32), StandardCharsets.UTF_8).equals("bananas"),
                "witnessed migration range no longer identifies bananas in the divergent document");
        require(comment(session, DIVERGENT_GLYPH_COMMENT)
                        .target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget,
                "witnessed migration replaced rather than retained the candidate comment");
        requireNoEffectiveApproval(session);
        journey.canonicalBeforeReload.put(
                CandidateCommentSessionPuppetAction.SessionRole.MAIN,
                SFMReviewSessionV2Codec.write(session)
        );
    }

    private static synchronized void assertUnavailableAccepted(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 session = unavailableSession(runtime);
        require(session.comments().size() == 2,
                "unavailable frame must accept route/action comments and reject the glyph comment");
        SFMReviewSessionV2.CandidateTrajectoryTarget route = assertCandidate(
                session, ROUTE_COMMENT, SFMReviewSessionV2.CandidateTargetKind.ROUTE, 1);
        SFMReviewSessionV2.CandidateTrajectoryTarget action = assertCandidate(
                session, ACTION_COMMENT, SFMReviewSessionV2.CandidateTargetKind.ACTION, 1);
        require(route.projectionStatus()
                        == ca.teamdman.sfm.client.history.SFMHistoryGraphContract.ProjectionStatus.CANCELLED,
                "unavailable route comment did not retain CANCELLED status");
        require(action.projectionStatus()
                        == ca.teamdman.sfm.client.history.SFMHistoryGraphContract.ProjectionStatus.CANCELLED,
                "unavailable action comment did not retain CANCELLED status");
        require(action.actionIntentId().isPresent(), "unavailable action comment lost its action intent");
        require(session.comments().stream().noneMatch(comment ->
                        comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target
                                && target.targetKind() == SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION),
                "unavailable glyph command created a document-region comment");
        for (String id : UNAVAILABLE_CANDIDATE_IDS) {
            journey.unavailableBaseline.put(id, fingerprint(candidate(session, id)));
        }
        requirePanelLabel(ROUTE_COMMENT, route);
        requirePanelLabel(ACTION_COMMENT, action);
        requireNoEffectiveApproval(session);
        journey.canonicalBeforeReload.put(
                CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE,
                SFMReviewSessionV2Codec.write(session)
        );
    }

    private static void assertFinalRoundtrip(SFMReviewSessionRuntime runtime) {
        SFMReviewSessionV2 main = mainSession(runtime);
        SFMReviewSessionV2 unavailable = unavailableSession(runtime);
        require(Boolean.TRUE.equals(journey.reloaded.get(CandidateCommentSessionPuppetAction.SessionRole.MAIN)),
                "main candidate session was not reloaded");
        require(Boolean.TRUE.equals(journey.reloaded.get(
                        CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE)),
                "unavailable candidate session was not reloaded");
        requireMainTargetsUnchanged(main);
        requireFingerprints(unavailable, journey.unavailableBaseline);
        Set<String> expected = new LinkedHashSet<>();
        MAIN_CANDIDATE_IDS.forEach(id -> expected.add(
                CandidateCommentSessionPuppetAction.SessionRole.MAIN.name() + "/" + id));
        UNAVAILABLE_CANDIDATE_IDS.forEach(id -> expected.add(
                CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE.name() + "/" + id));
        require(journey.navigations.keySet().equals(expected),
                "not every reloaded candidate target was navigated: " + journey.navigations.keySet());
        requireNoEffectiveApproval(main);
        requireNoEffectiveApproval(unavailable);
    }

    private Map<String, Object> stageEvidence(SFMReviewSessionRuntime runtime) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.candidate-comment-review-stage/1");
        answer.put("stage", stage.name());
        if (stage == Stage.UNAVAILABLE_ACCEPTED) {
            answer.put("session", sessionEvidence(unavailableSession(runtime)));
        } else {
            answer.put("session", sessionEvidence(mainSession(runtime)));
        }
        return answer;
    }

    private static Map<String, Object> finalEvidence(SFMReviewSessionRuntime runtime) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", "sfm.candidate-comment-review-puppet/1");
        answer.put("main_machine_id", machineId(CandidateCommentSessionPuppetAction.SessionRole.MAIN));
        answer.put("unavailable_machine_id",
                machineId(CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE));
        answer.put("old_plan_revision_id", journey.oldPlanRevisionId);
        answer.put("old_route_id", journey.oldRouteId);
        answer.put("main_session", sessionEvidence(mainSession(runtime)));
        answer.put("unavailable_session", sessionEvidence(unavailableSession(runtime)));
        answer.put("reload", Map.of(
                "main", Boolean.TRUE.equals(journey.reloaded.get(
                        CandidateCommentSessionPuppetAction.SessionRole.MAIN)),
                "unavailable", Boolean.TRUE.equals(journey.reloaded.get(
                        CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE))
        ));
        answer.put("navigation_roundtrip", journey.navigations.values().stream()
                .map(AssertCandidateCommentReviewPuppetAction::navigationEvidence)
                .toList());
        answer.put("candidate_retention", true);
        answer.put("divergent_exact_promotion_created_child", false);
        answer.put("witnessed_migration_created_child", true);
        answer.put("all_approvals_effective", false);
        return answer;
    }

    private static Map<String, Object> sessionEvidence(SFMReviewSessionV2 session) {
        Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluations = new LinkedHashMap<>();
        SFMReviewSessionV2Kernel.evaluateAll(session).forEach(value -> evaluations.put(value.commentId(), value));
        List<Map<String, Object>> comments = session.comments().stream()
                .sorted(Comparator.comparing(SFMReviewSessionV2.Comment::id))
                .map(comment -> commentEvidence(session, comment, evaluations.get(comment.id())))
                .toList();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("schema", session.schema());
        answer.put("session_id", session.id());
        answer.put("comments", comments);
        answer.put("comment_count", comments.size());
        answer.put("approval_effective_count", comments.stream()
                .filter(value -> Boolean.TRUE.equals(value.get("approval_effective"))).count());
        return answer;
    }

    private static Map<String, Object> commentEvidence(
            SFMReviewSessionV2 session,
            SFMReviewSessionV2.Comment comment,
            SFMReviewSessionV2Kernel.Evaluation evaluation
    ) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", comment.id());
        answer.put("text", comment.text());
        answer.put("provenance_kind", comment.provenance().kind());
        answer.put("evaluation_status", evaluation.status().name());
        answer.put("approval_effective",
                SFMReviewSessionV2Kernel.isApprovalEffective(session, comment, evaluation));
        if (comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget candidate) {
            answer.put("target_type", "candidate");
            answer.put("machine_id", candidate.machineId());
            answer.put("machine_revision", candidate.machineRevision());
            answer.put("plan_revision_id", candidate.trajectoryPlanRevisionId());
            answer.put("route_id", candidate.routeId());
            answer.put("frame", candidate.routeStepPosition());
            answer.put("step_id", candidate.trajectoryStepId().orElse(null));
            answer.put("action_intent_id", candidate.actionIntentId().orElse(null));
            answer.put("target_kind", candidate.targetKind().name());
            answer.put("predicted_state_id", candidate.predictedStateId());
            answer.put("predicted_state_hash", candidate.predictedStateHash().orElse(null));
            answer.put("projection_status", candidate.projectionStatus().name());
            answer.put("target_sha256", SFMReviewSessionV2Kernel.candidateTargetSha256(candidate));
            answer.put("projected_selection", candidate.projectedDocumentSelection()
                    .map(AssertCandidateCommentReviewPuppetAction::selectionEvidence).orElse(null));
            answer.put("promotion_link", null);
        } else {
            SFMReviewSessionV2.CommittedReviewTarget committed =
                    (SFMReviewSessionV2.CommittedReviewTarget) comment.target();
            answer.put("target_type", "committed");
            answer.put("selection_rule", committed.selectionRule().getClass().getSimpleName());
            answer.put("promotion_link", committed.candidatePromotion()
                    .map(AssertCandidateCommentReviewPuppetAction::promotionEvidence).orElse(null));
        }
        return answer;
    }

    private static Map<String, Object> selectionEvidence(SFMReviewSessionV2.ProjectedDocumentSelection selection) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("document_id", selection.documentId());
        answer.put("document_state_hash", selection.documentStateHash());
        answer.put("document_text_sha256", selection.documentTextSha256());
        answer.put("start_byte", selection.startByte());
        answer.put("end_byte", selection.endByte());
        answer.put("selected_text_sha256", selection.selectedTextSha256());
        return answer;
    }

    private static Map<String, Object> promotionEvidence(SFMReviewSessionV2.CandidatePromotionLink link) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("source_candidate_comment_id", link.sourceCandidateCommentId());
        answer.put("source_candidate_target_sha256", link.sourceCandidateTargetSha256());
        answer.put("decision_id", link.decisionId());
        answer.put("executed_history_head_id", link.executedHistoryHeadId());
        answer.put("executed_state_id", link.executedStateId());
        answer.put("executed_state_hash", link.executedStateHash());
        answer.put("correspondence", link.correspondence());
        answer.put("correspondence_evidence", link.correspondenceEvidence());
        return answer;
    }

    private static Map<String, Object> navigationEvidence(NavigationEvidence value) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("session_role", value.role().name());
        answer.put("comment_id", value.commentId());
        answer.put("plan_revision_id", value.planRevisionId());
        answer.put("route_id", value.routeId());
        answer.put("frame", value.frame());
        answer.put("predicted_state_id", value.predictedStateId());
        answer.put("predicted_state_hash", value.predictedStateHash().orElse(null));
        answer.put("projection_status", value.projectionStatus());
        answer.put("roundtrip", value.roundtrip());
        return answer;
    }

    private static String textSummary(SFMReviewSessionRuntime runtime) {
        StringBuilder answer = new StringBuilder("schema: sfm.candidate-comment-review-puppet-summary/1\n");
        appendSessionSummary(answer, "main", mainSession(runtime));
        appendSessionSummary(answer, "unavailable", unavailableSession(runtime));
        for (NavigationEvidence navigation : journey.navigations.values()) {
            answer.append("navigate ").append(navigation.role()).append('/').append(navigation.commentId())
                    .append(" -> ").append(navigation.planRevisionId()).append('#')
                    .append(navigation.routeId()).append('@').append(navigation.frame())
                    .append(" status=").append(navigation.projectionStatus())
                    .append(" roundtrip=").append(navigation.roundtrip()).append('\n');
        }
        answer.append("divergent-exact-promotion-created-child: false\n");
        answer.append("witnessed-migration-created-child: true\n");
        answer.append("approval-effective: false\n");
        return answer.toString();
    }

    private static void appendSessionSummary(StringBuilder answer, String label, SFMReviewSessionV2 session) {
        Map<String, SFMReviewSessionV2Kernel.Evaluation> evaluations = new LinkedHashMap<>();
        SFMReviewSessionV2Kernel.evaluateAll(session).forEach(value -> evaluations.put(value.commentId(), value));
        for (SFMReviewSessionV2.Comment comment : session.comments().stream()
                .sorted(Comparator.comparing(SFMReviewSessionV2.Comment::id)).toList()) {
            answer.append(label).append(" comment ").append(comment.id());
            if (comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target) {
                answer.append(" candidate ").append(target.targetKind())
                        .append(' ').append(target.canonicalAddress())
                        .append(" state-hash=").append(target.predictedStateHash().orElse("unavailable"))
                        .append(" status=").append(target.projectionStatus());
            } else {
                SFMReviewSessionV2.CommittedReviewTarget target =
                        (SFMReviewSessionV2.CommittedReviewTarget) comment.target();
                answer.append(" committed promotion=")
                        .append(target.candidatePromotion().map(SFMReviewSessionV2.CandidatePromotionLink::correspondence)
                                .orElse("none"));
            }
            answer.append(" approval=")
                    .append(SFMReviewSessionV2Kernel.isApprovalEffective(
                            session, comment, evaluations.get(comment.id())))
                    .append('\n');
        }
    }

    private static SFMReviewSessionV2.CandidateTrajectoryTarget assertCandidate(
            SFMReviewSessionV2 session,
            String id,
            SFMReviewSessionV2.CandidateTargetKind kind,
            int frame
    ) {
        SFMReviewSessionV2.CandidateTrajectoryTarget target = candidate(session, id);
        require(target.targetKind() == kind, id + " target kind differs");
        require(target.routeStepPosition() == frame, id + " frame differs");
        return target;
    }

    private static void assertGlyph(SFMReviewSessionV2 session, String id, int startByte, int endByte) {
        SFMReviewSessionV2.CandidateTrajectoryTarget target = assertCandidate(
                session, id, SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION, 2);
        SFMReviewSessionV2.ProjectedDocumentSelection selection = target.projectedDocumentSelection().orElseThrow();
        require(selection.startByte() == startByte && selection.endByte() == endByte,
                id + " UTF-8 byte range differs");
        require(selection.documentStateHash().equals(target.predictedStateHash().orElseThrow()),
                id + " document/state witness differs");
    }

    private static void requireMainTargetsUnchanged(SFMReviewSessionV2 session) {
        requireFingerprints(session, journey.mainBaseline);
    }

    private static void requireFingerprints(
            SFMReviewSessionV2 session,
            Map<String, TargetFingerprint> expected
    ) {
        require(!expected.isEmpty(), "candidate target baseline was not captured");
        for (Map.Entry<String, TargetFingerprint> entry : expected.entrySet()) {
            TargetFingerprint actual = fingerprint(candidate(session, entry.getKey()));
            require(actual.equals(entry.getValue()), "candidate target changed for " + entry.getKey());
        }
    }

    private static TargetFingerprint fingerprint(SFMReviewSessionV2.CandidateTrajectoryTarget target) {
        return new TargetFingerprint(
                target.machineRevision(),
                target.trajectoryPlanRevisionId(),
                target.routeId(),
                target.routeStepPosition(),
                target.predictedStateId(),
                target.predictedStateHash(),
                target.projectionStatus().name(),
                target.targetKind().name(),
                SFMReviewSessionV2Kernel.candidateTargetSha256(target)
        );
    }

    private static void requirePanelLabel(
            String commentId,
            SFMReviewSessionV2.CandidateTrajectoryTarget target
    ) {
        SFMCandidateHistoryPanel panel = focusedCandidatePanel();
        require(panel.currentPosition() == target.routeStepPosition(), "candidate label frame differs");
        require(panel.currentComments().stream().anyMatch(comment -> comment.id().equals(commentId)),
                "candidate label is absent from the visible frame");
    }

    private static SFMCandidateHistoryPanel focusedCandidatePanel() {
        if (!(Minecraft.getInstance().screen instanceof SFMScreenMultiplexer workspace)) {
            throw new IllegalStateException("Expected an SFM workspace for candidate-comment evidence");
        }
        if (!(workspace.focusedPanelInstance() instanceof SFMTimelinePanel timeline)
                || !(timeline.child() instanceof SFMCandidateHistoryPanel candidate)) {
            throw new IllegalStateException("Focused panel is not a Candidate History timeline");
        }
        if (candidate.loadStatus() != SFMCandidateHistoryPanel.LoadStatus.READY) {
            throw new IllegalStateException("Focused Candidate History panel is not ready");
        }
        return candidate;
    }

    private static SFMReviewSessionV2 mainSession(SFMReviewSessionRuntime runtime) {
        return runtime.session(machineId(CandidateCommentSessionPuppetAction.SessionRole.MAIN));
    }

    private static SFMReviewSessionV2 unavailableSession(SFMReviewSessionRuntime runtime) {
        return runtime.session(machineId(CandidateCommentSessionPuppetAction.SessionRole.UNAVAILABLE_FIXTURE));
    }

    private static SFMReviewSessionV2.Comment comment(SFMReviewSessionV2 session, String id) {
        return session.comments().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing candidate comment " + id));
    }

    private static SFMReviewSessionV2.CandidateTrajectoryTarget candidate(
            SFMReviewSessionV2 session,
            String id
    ) {
        SFMReviewSessionV2.Comment comment = comment(session, id);
        if (!(comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget target)) {
            throw new IllegalStateException(id + " is not a candidate target");
        }
        return target;
    }

    private static List<SFMReviewSessionV2.CandidatePromotionLink> promotionLinks(SFMReviewSessionV2 session) {
        return session.comments().stream()
                .map(SFMReviewSessionV2.Comment::target)
                .filter(SFMReviewSessionV2.CommittedReviewTarget.class::isInstance)
                .map(SFMReviewSessionV2.CommittedReviewTarget.class::cast)
                .flatMap(target -> target.candidatePromotion().stream())
                .toList();
    }

    private static List<SFMReviewSessionV2.CandidatePromotionLink> promotionLinksFor(
            SFMReviewSessionV2 session,
            String candidateCommentId
    ) {
        return promotionLinks(session).stream()
                .filter(link -> link.sourceCandidateCommentId().equals(candidateCommentId))
                .toList();
    }

    private static void requireNoPromotionLinks(SFMReviewSessionV2 session) {
        require(promotionLinks(session).isEmpty(), "candidate comments gained an implicit promotion link");
    }

    private static void requireNoEffectiveApproval(SFMReviewSessionV2 session) {
        List<SFMReviewSessionV2Kernel.Evaluation> evaluations = SFMReviewSessionV2Kernel.evaluateAll(session);
        Map<String, SFMReviewSessionV2Kernel.Evaluation> byId = new LinkedHashMap<>();
        evaluations.forEach(value -> byId.put(value.commentId(), value));
        for (SFMReviewSessionV2.Comment comment : session.comments()) {
            require(!SFMReviewSessionV2Kernel.isApprovalEffective(session, comment, byId.get(comment.id())),
                    "automation transferred effective approval to " + comment.id());
        }
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public enum Stage {
        ROUTE_LABEL,
        ACTION_LABEL,
        CREATED_ALL,
        RETAINED_AFTER_REPLAN,
        OLD_ROUTE_START_RESTORED,
        EXACT_PROMOTED,
        DIVERGENCE_REJECTED,
        WITNESSED_MIGRATED,
        UNAVAILABLE_ACCEPTED,
        FINAL_ROUNDTRIP
    }

    private record TargetFingerprint(
            long machineRevision,
            String planRevisionId,
            String routeId,
            int frame,
            String predictedStateId,
            Optional<String> predictedStateHash,
            String projectionStatus,
            String targetKind,
            String targetSha256
    ) {
    }

    private record NavigationEvidence(
            CandidateCommentSessionPuppetAction.SessionRole role,
            String commentId,
            String planRevisionId,
            String routeId,
            int frame,
            String predictedStateId,
            Optional<String> predictedStateHash,
            String projectionStatus,
            boolean roundtrip
    ) {
    }

    private static final class Journey {
        private final EnumMap<CandidateCommentSessionPuppetAction.SessionRole, String> machineIds =
                new EnumMap<>(CandidateCommentSessionPuppetAction.SessionRole.class);
        private final EnumMap<CandidateCommentSessionPuppetAction.SessionRole, String> canonicalBeforeReload =
                new EnumMap<>(CandidateCommentSessionPuppetAction.SessionRole.class);
        private final EnumMap<CandidateCommentSessionPuppetAction.SessionRole, Boolean> reloaded =
                new EnumMap<>(CandidateCommentSessionPuppetAction.SessionRole.class);
        private final LinkedHashMap<String, TargetFingerprint> mainBaseline = new LinkedHashMap<>();
        private final LinkedHashMap<String, TargetFingerprint> unavailableBaseline = new LinkedHashMap<>();
        private final LinkedHashMap<String, NavigationEvidence> navigations = new LinkedHashMap<>();
        private String oldPlanRevisionId;
        private String oldRouteId;
    }
}
