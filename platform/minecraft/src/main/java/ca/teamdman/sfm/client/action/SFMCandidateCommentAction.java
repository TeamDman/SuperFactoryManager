package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.history.SFMCandidateHistoryContract;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.review.session.SFMCandidateCommentTargetAdapter;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionRuntime;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2;
import ca.teamdman.sfm.client.review.session.SFMReviewSessionV2Kernel;
import ca.teamdman.sfm.client.screen.history.SFMCandidateHistoryPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMCandidateHistoryScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntent;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelIntentResult;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelMetadata;
import ca.teamdman.sfm.client.screen.workspace.timeline.SFMTimelinePanel;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Ordinary palette actions for persistent candidate comments and explicit exact promotion. */
public final class SFMCandidateCommentAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Kind {
        CREATE_ROUTE("review/comment/create/candidate/route", "Comment on candidate route",
                SFMReviewSessionV2.CandidateTargetKind.ROUTE),
        CREATE_STEP("review/comment/create/candidate/step", "Comment on candidate step",
                SFMReviewSessionV2.CandidateTargetKind.STEP),
        CREATE_ACTION("review/comment/create/candidate/action", "Comment on candidate action",
                SFMReviewSessionV2.CandidateTargetKind.ACTION),
        CREATE_STATE("review/comment/create/candidate/state", "Comment on candidate state",
                SFMReviewSessionV2.CandidateTargetKind.STATE),
        CREATE_GLYPH("review/comment/create/candidate/glyph", "Comment on candidate glyph range",
                SFMReviewSessionV2.CandidateTargetKind.DOCUMENT_REGION),
        EDIT("review/comment/edit", "Edit review comment", null),
        ARCHIVE("review/comment/archive", "Archive review comment", null),
        NAVIGATE("review/comment/navigate", "Navigate to review comment", null),
        PROMOTE_EXACT("review/comment/promote/exact", "Link candidate comment to exact execution", null),
        MIGRATE_WITNESSED("review/comment/migrate/witnessed", "Migrate candidate comment with evidence", null);

        private final String path;
        private final String title;
        private final SFMReviewSessionV2.CandidateTargetKind targetKind;

        Kind(String path, String title, SFMReviewSessionV2.CandidateTargetKind targetKind) {
            this.path = path;
            this.title = title;
            this.targetKind = targetKind;
        }

        public String path() { return path; }
        public String title() { return title; }
        public boolean creates() { return targetKind != null; }
        public SFMReviewSessionV2.CandidateTargetKind targetKind() { return targetKind; }
    }

    private final Kind kind;
    private final SFMHistoryGraphRuntime historyRuntime;
    private final SFMReviewSessionRuntime reviewRuntime;

    public SFMCandidateCommentAction(Kind kind) {
        this(kind, SFMHistoryGraphRuntime.get(), SFMReviewSessionRuntime.get());
    }

    SFMCandidateCommentAction(
            Kind kind,
            SFMHistoryGraphRuntime historyRuntime,
            SFMReviewSessionRuntime reviewRuntime
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.historyRuntime = Objects.requireNonNull(historyRuntime, "historyRuntime");
        this.reviewRuntime = Objects.requireNonNull(reviewRuntime, "reviewRuntime");
    }

    @Override
    public Component title() {
        return Component.literal(kind.title());
    }

    @Override
    public Component description() {
        return Component.literal("Persistent candidate-review action; immutable trajectory addresses never follow replans");
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return context -> context.requireOriginatingHost(
                SFMScreenMultiplexer.class,
                Component.literal("Candidate comments require an SFM workspace")
        );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        if (kind.creates()) {
            LiteralArgumentBuilder<SFMClientActionSource> focused = LiteralArgumentBuilder.literal("focused");
            if (kind == Kind.CREATE_GLYPH) {
                focused.then(RequiredArgumentBuilder
                        .<SFMClientActionSource, Integer>argument("start_byte", IntegerArgumentType.integer(0))
                        .then(RequiredArgumentBuilder
                                .<SFMClientActionSource, Integer>argument("end_byte", IntegerArgumentType.integer(0))
                                .then(RequiredArgumentBuilder
                                        .<SFMClientActionSource, String>argument("text", StringArgumentType.greedyString())
                                        .executes(this::createFocused))));
            } else {
                focused.then(RequiredArgumentBuilder
                        .<SFMClientActionSource, String>argument("text", StringArgumentType.greedyString())
                        .executes(this::createFocused));
            }
            node.then(focused);
            return;
        }

        RequiredArgumentBuilder<SFMClientActionSource, String> machine = machineArgument();
        RequiredArgumentBuilder<SFMClientActionSource, String> comment = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("comment_id", SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    try {
                        String machineId = resolveMachineId(context);
                        reviewRuntime.session(machineId).comments().stream()
                                .map(SFMReviewSessionV2.Comment::id).sorted().forEach(builder::suggest);
                    } catch (RuntimeException ignored) {
                    }
                    return builder.buildFuture();
                });
        switch (kind) {
            case EDIT -> comment.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("text", StringArgumentType.greedyString())
                    .executes(this::edit));
            case ARCHIVE -> comment.executes(this::archive);
            case NAVIGATE -> comment.executes(this::navigate);
            case PROMOTE_EXACT -> comment.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("decision_id", SFMCanonicalTokenArgument.token())
                    .executes(this::promoteExact));
            case MIGRATE_WITNESSED -> comment.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, Integer>argument("start_byte", IntegerArgumentType.integer(0))
                    .then(RequiredArgumentBuilder
                            .<SFMClientActionSource, Integer>argument("end_byte", IntegerArgumentType.integer(0))
                            .then(RequiredArgumentBuilder
                                    .<SFMClientActionSource, String>argument(
                                            "decision_id", SFMCanonicalTokenArgument.token())
                                    .then(RequiredArgumentBuilder
                                            .<SFMClientActionSource, String>argument(
                                                    "correspondence_evidence", StringArgumentType.greedyString())
                                            .executes(this::migrateWitnessed)))));
            default -> throw new AssertionError("Unhandled comment action " + kind);
        }
        machine.then(comment);
        node.then(machine);
    }

    @Override
    public int execute(
            SFMScreenMultiplexer target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw error("Provide the action arguments shown by command completion");
    }

    private int createFocused(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        SFMCandidateHistoryPanel panel = focusedCandidatePanel(context.getSource().context())
                .orElseThrow(() -> error("Focus a Candidate History panel before creating this comment"));
        SFMCandidateHistoryContract.CandidateRouteProjection route = panel.projection()
                .orElseThrow(() -> error("The focused candidate route is not ready"));
        SFMCandidateHistoryContract.CandidateFrame frame = panel.currentFrame()
                .orElseThrow(() -> error("The focused candidate frame is not ready"));
        Optional<SFMCandidateCommentTargetAdapter.Utf8Range> range = kind == Kind.CREATE_GLYPH
                ? Optional.of(new SFMCandidateCommentTargetAdapter.Utf8Range(
                        IntegerArgumentType.getInteger(context, "start_byte"),
                        IntegerArgumentType.getInteger(context, "end_byte")))
                : Optional.empty();
        String id;
        try {
            id = reviewRuntime.createCandidateComment(
                    route,
                    frame,
                    kind.targetKind(),
                    range,
                    StringArgumentType.getString(context, "text")
            );
        } catch (IllegalArgumentException failure) {
            throw error(failure.getMessage());
        }
        context.getSource().sendFeedback(Component.literal(
                "Created " + id + " on candidate " + frame.address().canonical()));
        return 1;
    }

    private int edit(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String machine = resolveMachineId(context);
        reviewRuntime.editComment(machine, commentId(context), StringArgumentType.getString(context, "text"));
        context.getSource().sendFeedback(Component.literal("Edited " + commentId(context)));
        return 1;
    }

    private int archive(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String machine = resolveMachineId(context);
        reviewRuntime.archiveComment(machine, commentId(context));
        context.getSource().sendFeedback(Component.literal("Archived " + commentId(context)));
        return 1;
    }

    private int promoteExact(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String machine = resolveMachineId(context);
        SFMReviewSessionV2Kernel.PromotionResult result = reviewRuntime.promoteExact(
                machine,
                commentId(context),
                SFMCanonicalTokenArgument.get(context, "decision_id")
        );
        context.getSource().sendFeedback(Component.literal(
                result.status().name().toLowerCase(Locale.ROOT) + ": " + result.diagnostic()));
        if (result.status() != SFMReviewSessionV2Kernel.PromotionStatus.PROMOTED_EXACTLY) {
            throw error(result.diagnostic());
        }
        return 1;
    }

    private int migrateWitnessed(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String machine = resolveMachineId(context);
        SFMReviewSessionV2Kernel.MigrationResult result;
        try {
            result = reviewRuntime.migrateWitnessed(
                    machine,
                    commentId(context),
                    IntegerArgumentType.getInteger(context, "start_byte"),
                    IntegerArgumentType.getInteger(context, "end_byte"),
                    SFMCanonicalTokenArgument.get(context, "decision_id"),
                    List.of(StringArgumentType.getString(context, "correspondence_evidence"))
            );
        } catch (IllegalArgumentException failure) {
            throw error(failure.getMessage());
        }
        context.getSource().sendFeedback(Component.literal(
                result.status().name().toLowerCase(Locale.ROOT) + ": " + result.diagnostic()));
        if (result.status() != SFMReviewSessionV2Kernel.MigrationStatus.MIGRATED_WITH_WITNESS) {
            throw error(result.diagnostic());
        }
        return 1;
    }

    private int navigate(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String machine = resolveMachineId(context);
        SFMReviewSessionV2.Comment comment = reviewRuntime.comment(machine, commentId(context))
                .orElseThrow(() -> error("Review comment was not found"));
        if (!(comment.target() instanceof SFMReviewSessionV2.CandidateTrajectoryTarget candidate)) {
            throw error("Committed source navigation is handled by the review explorer");
        }
        SFMScreenMultiplexer workspace = context.getSource().context().requireOriginatingHost(
                SFMScreenMultiplexer.class,
                Component.literal("Candidate navigation requires an SFM workspace")
        ).target();
        ResourceLocation scene = new ResourceLocation("sfm", "episode/candidate-history");
        String exactMachine = SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, machine).canonical();
        SFMCandidateHistoryScreenType.Recipe recipe = new SFMCandidateHistoryScreenType.Recipe(
                scene,
                exactMachine,
                Optional.of(candidate.trajectoryPlanRevisionId()),
                Optional.of(candidate.routeId())
        );
        SFMTimelinePanel panel = recipe.reopen();
        panel.seekWhenAvailable(candidate.routeStepPosition());
        SFMWorkspacePanelIntentResult opened = workspace.submit(
                workspace.focusedPanelId(),
                new SFMWorkspacePanelIntent.OpenAsTab(panel, SFMWorkspacePanelMetadata.ordinary(), recipe)
        );
        if (opened != SFMWorkspacePanelIntentResult.APPLIED) {
            throw error("Candidate navigation unavailable: " + opened);
        }
        context.getSource().sendFeedback(Component.literal(
                "Opened candidate " + candidate.canonicalAddress() + " for " + comment.id()));
        return 1;
    }

    private RequiredArgumentBuilder<SFMClientActionSource, String> machineArgument() {
        return RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("machine_selector", SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    historyRuntime.machineIds().forEach(id -> builder.suggest(
                            SFMEntitySelector.exact(SFMEntitySelector.Domain.EPISODE, id).canonical()));
                    return builder.buildFuture();
                });
    }

    private String resolveMachineId(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        String value = SFMCanonicalTokenArgument.get(context, "machine_selector");
        Optional<String> focused = focusedCandidatePanel(context.getSource().context())
                .flatMap(SFMCandidateHistoryPanel::pinnedMachineId);
        SFMEntitySelector selector;
        try {
            selector = SFMEntitySelector.parseCanonical(SFMEntitySelector.Domain.EPISODE, value);
        } catch (RuntimeException failure) {
            throw error(failure.getMessage());
        }
        List<SFMHistoryGraphRuntime.MachineSnapshot> matches = historyRuntime.resolveSnapshots(selector, focused);
        if (matches.size() != 1) {
            throw error(matches.isEmpty() ? "No trajectory machine matched" : "More than one trajectory machine matched");
        }
        return matches.get(0).machineId();
    }

    private static Optional<SFMCandidateHistoryPanel> focusedCandidatePanel(SFMClientActionContext context) {
        if (!(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null) return Optional.empty();
        return workspace.panel(context.originatingPanelId()).flatMap(panel -> {
            if (panel instanceof SFMCandidateHistoryPanel candidate) return Optional.of(candidate);
            if (panel instanceof SFMTimelinePanel timeline
                    && timeline.child() instanceof SFMCandidateHistoryPanel candidate) return Optional.of(candidate);
            return Optional.empty();
        });
    }

    private static String commentId(CommandContext<SFMClientActionSource> context) {
        return SFMCanonicalTokenArgument.get(context, "comment_id");
    }

    private static SimpleCommandExceptionType errorType(String message) {
        return new SimpleCommandExceptionType(Component.literal(message == null ? "Candidate comment action failed" : message));
    }

    private static CommandSyntaxException error(String message) {
        return errorType(message).create();
    }
}
