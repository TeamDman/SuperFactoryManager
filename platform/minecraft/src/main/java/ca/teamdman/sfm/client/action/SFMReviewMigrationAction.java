package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.*;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.text_editor.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A bounded transient preview registry. Only explicit acceptance writes review evidence. */
public final class SFMReviewMigrationAction implements SFMClientAction<SFMClientActionContext> {
    public enum Kind {
        PREVIEW("preview", "Preview comment on current observed sources"),
        REPORT("report", "Open migration evidence as text"),
        ACCEPT("accept", "Accept linked comment successor");
        final ResourceLocation id;
        final String label;
        Kind(String suffix, String label) { id = new ResourceLocation("sfm", "review/comment/successor/" + suffix); this.label = label; }
        public ResourceLocation id() { return id; }
    }
    private record Draft(SFMReleaseReviewRuntime.MigrationContext context, SFMReviewMigrationPlan plan, String report) { }
    // Accessed on the client thread only; previews are deliberately not persisted.
    private static final LinkedHashMap<String, Draft> DRAFTS = new LinkedHashMap<>();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sfm-review-migration-preview"); thread.setDaemon(true); return thread;
    });
    private final Kind kind;
    public SFMReviewMigrationAction(Kind kind) { this.kind = kind; }
    @Override public Component title() { return Component.literal(kind.label); }
    @Override public Component description() { return Component.literal("Inspect a unique literal successor; preserve the original comment and require explicit approval transfer"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                && context.originatingHost() instanceof SFMScreenMultiplexer
                && SFMReleaseReviewRuntime.get().document().isPresent()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(Component.literal("Open a saved review in a workspace first"));
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (kind == Kind.PREVIEW) {
            var comment = RequiredArgumentBuilder.<SFMClientActionSource, String>argument("comment", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        SFMReleaseReviewRuntime.get().document().stream().flatMap(review -> review.reviewSession().comments().stream())
                                .filter(value -> !"generated".equals(value.provenance().kind()))
                                .map(value -> StringArgumentType.escapeIfRequired(value.id())).forEach(builder::suggest);
                        return builder.buildFuture();
                    });
            comment.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("destination_lane", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        SFMReleaseReviewRuntime.get().document().stream().flatMap(review -> review.repositoryBindings().stream())
                                .map(value -> StringArgumentType.escapeIfRequired(value.laneId())).distinct().forEach(builder::suggest);
                        return builder.buildFuture();
                    }).executes(this::invoke));
            node.then(comment);
        } else {
            var draft = RequiredArgumentBuilder.<SFMClientActionSource, String>argument("preview", StringArgumentType.word())
                    .suggests((context, builder) -> { DRAFTS.keySet().forEach(builder::suggest); return builder.buildFuture(); });
            if (kind == Kind.ACCEPT) draft.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("decision_note", StringArgumentType.string()).executes(this::invoke));
            else draft.executes(this::invoke);
            node.then(draft);
        }
    }
    public static List<SFMActionChoice> choices(String commentId) {
        try { SFMReleaseReviewRuntime.get().captureMigrationContext(); }
        catch (IllegalStateException unavailable) { return List.of(); }
        var review = SFMReleaseReviewRuntime.get().document();
        if (review.isEmpty() || review.get().selectorBindings().stream().noneMatch(value -> value.commentId().equals(commentId))) return List.of();
        return review.get().repositoryBindings().stream().map(value -> value.laneId()).distinct().map(lane ->
                SFMActionChoice.invoke(Kind.PREVIEW.id, StringArgumentType.escapeIfRequired(commentId) + " " + StringArgumentType.escapeIfRequired(lane),
                        "Preview linked successor · current observed sources · " + lane)).toList();
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> command) {
        try {
            if (kind == Kind.PREVIEW) return preview(target, command);
            String id = StringArgumentType.getString(command, "preview");
            Draft draft = DRAFTS.get(id);
            if (draft == null) throw new IllegalArgumentException("Preview expired; preview this comment again");
            if (!current(draft.context)) throw new IllegalArgumentException("Review changed; preview this comment again");
            if (kind == Kind.REPORT) {
                var recipe = new SFMTextEditorPanelRecipe(new ResourceLocation("sfm", "text_editor"),
                        SFMTextEditors.V3.getId().orElseThrow().location(),
                        new SFMTextDocumentSource.Literal(draft.report, SFMTextDocumentLanguage.plainText()), true, "Comment migration evidence");
                return OpenPanelAction.openPanel(target, recipe.reopen(), OpenPanelAction.Direction.FOCUSED, recipe);
            }
            if (!draft.plan.canAccept()) throw new IllegalArgumentException("No unique complete successor; acceptance is unavailable");
            String note = StringArgumentType.getString(command, "decision_note");
            var continuation = SFMClientActionContinuation.capture(target);
            var persistence = SFMReleaseReviewRuntime.get().acceptMigrationSuccessorAsync(draft.context, draft.plan, note);
            var status = new SFMReleaseReviewOperationFeedback(target,
                    SFMReleaseReviewRuntime.get().pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L),
                    command.getSource()::sendFeedback);
            status.pending(Component.literal("Saving linked successor; original evidence is retained…"));
            persistence.whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
                        if (failure != null) status.failed("Successor not saved; preview retained", rootMessage(failure), List.of());
                        else {
                            if (result.mutation().saved()) {
                                DRAFTS.remove(id);
                                if (continuation.isCurrent() && SFMReleaseReviewRuntime.get().snapshot().openEpoch() == draft.context.lease().openEpoch()) {
                                    try { SFMReleaseReviewCommentChoiceAction.refreshReviewExplorers(continuation.context()); }
                                    catch (RuntimeException refreshFailure) {
                                        ca.teamdman.sfm.SFM.LOGGER.warn("SFM_REVIEW_SUCCESSOR_SAVED_REFRESH_FAILED comment={}", result.commentId(), refreshFailure);
                                    }
                                }
                                status.complete(Component.literal("Saved linked successor " + result.commentId() + "; original retained"));
                            } else status.failed("Successor not saved; refresh and preview again", result.mutation().failure().orElse("Save rejected"), List.of());
                        }
                    }));
            return 1;
        } catch (RuntimeException failure) {
            command.getSource().sendFeedback(Component.literal("Migration unavailable: " + rootMessage(failure))); return 0;
        }
    }
    private static int preview(SFMClientActionContext target, CommandContext<SFMClientActionSource> command) {
        String comment = StringArgumentType.getString(command, "comment");
        String lane = StringArgumentType.getString(command, "destination_lane");
        var captured = SFMReleaseReviewRuntime.get().captureMigrationContext();
        var continuation = SFMClientActionContinuation.capture(target);
        if (!BUSY.compareAndSet(false, true)) throw new IllegalStateException("Another migration preview is running");
        command.getSource().sendFeedback(Component.literal("Comparing exact comment evidence with current observed sources…"));
        CompletableFuture.supplyAsync(() -> {
            var plan = SFMReviewMigrationPlan.preview(captured.observation(), comment, lane, SFMReviewMigrationPreview.Limits.DEFAULT);
            return new Draft(captured, plan, report(captured, plan));
        }, WORKER).whenComplete((draft, failure) -> Minecraft.getInstance().execute(() -> {
            try {
                if (!continuation.isCurrent() || !current(captured)) {
                    command.getSource().sendFeedback(Component.literal("Review context changed; preview discarded")); return;
                }
                if (failure != null) { command.getSource().sendFeedback(Component.literal("Migration preview failed: " + rootMessage(failure))); return; }
                String id = "migration-" + UUID.randomUUID();
                DRAFTS.put(id, draft);
                while (DRAFTS.size() > 8) DRAFTS.remove(DRAFTS.keySet().iterator().next());
                var choices = new ArrayList<SFMActionChoice>();
                choices.add(SFMActionChoice.invoke(Kind.REPORT.id, id, "Inspect original and proposed targets · " + draft.plan.ranges().stream().map(value -> value.status().name()).distinct().toList()));
                if (draft.plan.canAccept() && captured.lease().writable()) choices.add(SFMActionChoice.invoke(Kind.ACCEPT.id,
                        id + " " + StringArgumentType.escapeIfRequired("Explicitly reviewed and accepted the unique literal successor, including any approval text"),
                        "Accept linked successor · copies entire comment INCLUDING any #approved"));
                if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
                SFMCommandPaletteScreen.openChoices(continuation.context(), Component.literal("Migration preview · original retained · " + lane), choices);
            } finally { BUSY.set(false); }
        }));
        return 1;
    }
    private static boolean current(SFMReleaseReviewRuntime.MigrationContext context) {
        var now = SFMReleaseReviewRuntime.get().snapshot();
        return now.openEpoch() == context.lease().openEpoch() && now.generation() == context.lease().generation()
                && now.path().equals(context.lease().path());
    }
    static String report(SFMReleaseReviewRuntime.MigrationContext context, SFMReviewMigrationPlan plan) {
        StringBuilder text = new StringBuilder("Comment successor preview\nReview: ").append(context.lease().path().orElseThrow())
                .append("\nOriginal comment: ").append(plan.originalComment().id()).append("\nComment text (copied verbatim on acceptance):\n")
                .append(plan.originalComment().text()).append("\n\nDestination: current observed AFTER corpus in lane ").append(plan.destinationLane())
                .append("\nThis is an observation, not a live filesystem query or all repository files. Refresh observation first to include newer changes.")
                .append("\nApproval is never transferred automatically. Acceptance creates a linked comment and preserves the original.")
                .append("\nAcceptable: ").append(plan.canAccept()).append("\nObservation: ").append(plan.semanticStateHash()).append('\n');
        if (!context.lease().writable()) text.append("Read-only review: reopen writable and preview again before accepting.\n");
        var oldRanges = plan.originalBinding().capturedSelection().ranges();
        for (int index = 0; index < plan.ranges().size(); index++) {
            var original = oldRanges.get(index);
            var result = plan.ranges().get(index);
            text.append("\nRange ").append(index + 1).append("\nStatus: ")
                    .append(result.status()).append(" · ").append(result.diagnostic()).append('\n');
            text.append("Original bytes: [").append(original.startByte()).append("..").append(original.endByte())
                    .append(") · ").append(original.direction()).append('\n');
            var source = context.observation().sources().get(original.documentRevisionId());
            if (source != null) text.append("Original path: ").append(source.document().path()).append('\n');
            text.append("Original revision:\n").append(original.documentRevisionId())
                    .append("\nOriginal SHA256:\n").append(original.documentSha256()).append('\n');
            for (var candidate : result.candidates()) {
                text.append("\nProposed bytes: [").append(candidate.startByte()).append("..").append(candidate.endByte())
                        .append(")\nProposed path: ").append(candidate.path())
                        .append("\nProposed revision:\n").append(candidate.documentRevisionId())
                        .append("\nProposed SHA256:\n").append(candidate.documentSha256()).append('\n');
            }
        }
        return text.toString();
    }
    private static String rootMessage(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return String.valueOf(failure.getMessage());
    }
}
