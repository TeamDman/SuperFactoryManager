package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewEditorCapture;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import ca.teamdman.sfm.client.screen.workspace.SFMPanelReopenRecipe;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenPanel;
import net.minecraft.resources.ResourceLocation;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Ordinary palette actions for the explicit-path portable release-review runtime. */
public final class SFMReleaseReviewAction implements SFMClientAction<SFMClientActionContext>, SFMClientActionCompletion {
    @Override public Optional<java.util.List<SFMPaletteCandidate>> argumentCandidates(String command, int argumentStart,
            int cursor, SFMClientActionContext context) { return Optional.empty(); }

    @Override public boolean acceptsContinuation(String command, SFMClientActionContext context) {
        if (kind != Kind.CREATE_WORKING_TREE) return false;
        var reader = new com.mojang.brigadier.StringReader(command);
        String prefix = "sfm action invoke sfm:review/session/create/working_tree ";
        if (!command.startsWith(prefix)) return false;
        reader.setCursor(prefix.length());
        try {
            for (int i = 0; i < 3; i++) {
                if (reader.readString().isBlank()) return false;
                if (i < 2) { if (!reader.canRead() || reader.read() != ' ') return false; }
            }
            return !reader.canRead();
        } catch (CommandSyntaxException invalid) { return false; }
    }
    /**
     * Formats a path for the terminal {@link StringArgumentType#greedyString()}
     * arguments used by the open/save-as actions. A greedy string consumes the
     * remaining command text verbatim, so Brigadier quoting would become part
     * of the Windows path instead of being removed by the parser.
     */
    public static String greedyPathArgument(Path path) {
        return path.toString();
    }

    public enum Kind {
        CREATE("review/session/create", "Create release review",
                "Create and open a complete pinned Git release-review document through the SFM toolchain"),
        CREATE_WORKING_TREE("review/session/create/working_tree", "Create working-tree review",
                "Capture scoped disk bytes, including allowed untracked files, into a new portable review without committing"),
        OPEN("review/session/open", "Open release review", "Open one tracked .sfm-review.json file writable"),
        OPEN_READ_ONLY("review/session/open/read_only", "Open release review read-only",
                "Inspect one tracked review file without taking its writer lease"),
        OPEN_VIEW("review/session/open/view", "Open release review view",
                "Open one tracked review writable and show its generic Changes explorer"),
        OPEN_READ_ONLY_VIEW("review/session/open/read_only/view", "Open release review view read-only",
                "Open one tracked review read-only and show its generic Changes explorer"),
        SAVE("review/session/save", "Save release review", "Atomically save the current review file"),
        CANCEL_OPERATION("review/session/operation/cancel", "Cancel pending review operation",
                "Cancel an exact preparing open/save request; a commit already in progress must finish"),
        SAVE_AS("review/session/save/as", "Save release review as", "Atomically save to another explicit path"),
        QUERY("review/session/query", "Query release review", "Evaluate review-unit set algebra"),
        QUERY_ACTIVATE("review/session/query/activate", "Activate release-review query",
                "Persist a query as the resumable work queue"),
        QUERY_SAVE("review/session/query/save", "Save release-review query",
                "Save a named query in the portable review file and activate it"),
        QUERY_NORMALIZE("review/session/query/normalize", "Normalize release-review query",
                "Show the canonical query expression without changing the work queue"),
        STATUS("review/session/status", "Show release review status", "Show fail-closed completion counts"),
        SELECT("review/session/work/select", "Select release-review unit",
                "Persistently select one stable review-unit id in the active work queue"),
        NEXT("review/session/work/next", "Next release-review unit", "Move to the next stable work unit"),
        PREVIOUS("review/session/work/previous", "Previous release-review unit", "Move to the previous work unit"),
        DEFER("review/session/work/defer", "Defer release-review unit", "Persistently defer the current work unit"),
        RESUME("review/session/work/resume", "Resume deferred release-review unit", "Resume the first deferred unit"),
        SHOW_CURRENT("review/session/work/show", "Show current review work",
                "Reveal the saved work cursor without advancing it or saving the review"),
        COMMENT_CREATE("review/session/comment/create", "Create release-review comment",
                "Create an ordinary comment with a structurally pinned selector proposal"),
        MIGRATION_DECIDE("review/session/migration/decide", "Decide release-review migration",
                "Record a human migration decision and optional candidate retarget"),
        ATTEST("review/session/attest", "Attest completed release review",
                "Record an explicit maintainer attestation only when the pinned review is ready");

        private final String path;
        private final String title;
        private final String description;

        Kind(String path, String title, String description) {
            this.path = path;
            this.title = title;
            this.description = description;
        }

        public String path() { return path; }
    }

    private final Kind kind;

    public SFMReleaseReviewAction(Kind kind) {
        this.kind = kind;
    }

    @Override
    public Component title() {
        if (kind == Kind.CREATE_WORKING_TREE) return ca.teamdman.sfm.client.search.SFMExplorerSearchText.CAPTURE_CREATE.getComponent();
        return Component.literal(kind.title);
    }

    @Override
    public Component description() {
        if (kind == Kind.CREATE_WORKING_TREE) return ca.teamdman.sfm.client.search.SFMExplorerSearchText.CAPTURE_DESCRIPTION.getComponent();
        return Component.literal(kind.description);
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> {
            if (!context.originatingHostIsCurrent().getAsBoolean()) {
                return SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
            }
            if (kind == Kind.COMMENT_CREATE
                    && (!(context.originatingHost() instanceof SFMScreenMultiplexer)
                    || context.originatingPanelId() == null
                    || SFMReleaseReviewRuntime.get().document().isEmpty())) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Open a writable release review and focus one of its source documents"));
            }
            if (SFMReviewWorkQueueControls.isQueueKind(kind)) {
                var available = SFMReviewLensSetAction.capture(context);
                if (!available.isAvailable()) return SFMClientActionAvailability.unavailable(available.unavailableReason());
                if (kind != Kind.SHOW_CURRENT && !SFMReleaseReviewRuntime.get().snapshot().writable()) {
                    return SFMClientActionAvailability.unavailable(Component.literal(
                            "Open this review writable to change its saved work cursor"));
                }
            }
            if (kind == Kind.ATTEST) {
                SFMReleaseReviewRuntime runtime = SFMReleaseReviewRuntime.get();
                if (runtime.document().isEmpty()) {
                    return SFMClientActionAvailability.unavailable(Component.literal(
                            "Open a writable release review before attesting"));
                }
                SFMReleaseReviewKernel.CompletionStatus status = runtime.status().status();
                if (status != SFMReleaseReviewKernel.CompletionStatus.READY_FOR_MAINTAINER_ATTESTATION) {
                    return SFMClientActionAvailability.unavailable(Component.literal(
                            "Attestation requires ready_for_maintainer_attestation; current status is "
                                    + status.name().toLowerCase(Locale.ROOT)));
                }
            }
            return SFMClientActionAvailability.available(context);
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        switch (kind) {
            case CREATE, CREATE_WORKING_TREE -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("path", StringArgumentType.string())
                    .then(RequiredArgumentBuilder
                            .<SFMClientActionSource, String>argument("lane", StringArgumentType.string())
                            .then(RequiredArgumentBuilder
                                    .<SFMClientActionSource, String>argument("before", StringArgumentType.string())
                                    .then(RequiredArgumentBuilder
                                            .<SFMClientActionSource, String>argument(
                                                    kind == Kind.CREATE ? "candidate" : "scope", StringArgumentType.string())
                                            .suggests((context, builder) -> {
                                                var examples = kind == Kind.CREATE ? java.util.List.of("HEAD")
                                                        : java.util.List.of("platform/minecraft/src", "platform/cli", "docs", ".");
                                                examples.stream().map(StringArgumentType::escapeIfRequired).forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .executes(this::invoke)
                                            .then(RequiredArgumentBuilder
                                                    .<SFMClientActionSource, String>argument(
                                                            "repository_root", StringArgumentType.string())
                                                    .executes(this::invoke))))));
            case OPEN, OPEN_READ_ONLY, OPEN_VIEW, OPEN_READ_ONLY_VIEW, SAVE_AS -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("path", StringArgumentType.greedyString())
                    .executes(this::invoke));
            case CANCEL_OPERATION -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, Long>argument("operation", LongArgumentType.longArg(1))
                    .suggests((context, builder) -> {
                        SFMReleaseReviewRuntime.get().pendingOperation()
                                .ifPresent(pending -> builder.suggest(Long.toString(pending.id())));
                        return builder.buildFuture();
                    }).executes(this::invoke));
            case QUERY, QUERY_ACTIVATE, QUERY_NORMALIZE -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("expression", StringArgumentType.greedyString())
                    .executes(this::invoke));
            case QUERY_SAVE -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("query", StringArgumentType.string())
                    .then(RequiredArgumentBuilder
                            .<SFMClientActionSource, String>argument("expression", StringArgumentType.greedyString())
                            .executes(this::invoke)));
            case SELECT -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("unit", StringArgumentType.string())
                    .executes(this::invoke));
            case COMMENT_CREATE -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("proposal", StringArgumentType.string())
                    .then(RequiredArgumentBuilder
                            .<SFMClientActionSource, String>argument("text", StringArgumentType.greedyString())
                            .executes(this::invoke)));
            case MIGRATION_DECIDE -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("migration", StringArgumentType.string())
                    .then(RequiredArgumentBuilder
                            .<SFMClientActionSource, String>argument("expected_state", StringArgumentType.word())
                            .then(RequiredArgumentBuilder
                                    .<SFMClientActionSource, String>argument("decision", StringArgumentType.word())
                                    .then(RequiredArgumentBuilder
                                            .<SFMClientActionSource, String>argument("candidate", StringArgumentType.word())
                                            .then(RequiredArgumentBuilder
                                                    .<SFMClientActionSource, String>argument(
                                                            "note", StringArgumentType.greedyString())
                                                    .executes(this::invoke))))));
            case ATTEST -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("maintainer", StringArgumentType.string())
                    .then(RequiredArgumentBuilder
                            .<SFMClientActionSource, String>argument("statement", StringArgumentType.greedyString())
                            .executes(this::invoke)));
            default -> node.executes(this::invoke);
        }
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        SFMReleaseReviewRuntime runtime = SFMReleaseReviewRuntime.get();
        try {
            return switch (kind) {
                case CREATE_WORKING_TREE -> {
                    var continuation = SFMClientActionContinuation.capture(target);
                    yield SFMReleaseReviewCreateRuntime.queue(new SFMReleaseReviewCreateRuntime.Request(
                            Path.of(StringArgumentType.getString(context, "path")),
                            StringArgumentType.getString(context, "lane"),
                            StringArgumentType.getString(context, "before"), null,
                            optionalPath(context, "repository_root"),
                            Optional.of(StringArgumentType.getString(context, "scope"))),
                            context.getSource()::sendFeedback, output -> {
                                if (!continuation.isCurrent()) {
                                    context.getSource().sendFeedback(Component.literal("Created review at " + output
                                            + "; the originating view closed, so it was not opened automatically"));
                                    return;
                                }
                                queueOpen(continuation.context(), output, true, context.getSource()::sendFeedback, current -> {
                                    var scene = new ResourceLocation("sfm", "explorer/release_review/changes");
                                    var recipe = new SFMReleaseReviewExplorerScreenType.Recipe(scene,
                                            SFMReleaseReviewExplorerScreenType.Projection.CHANGES, Optional.empty());
                                    OpenPanelAction.openPanel(current, recipe.reopen(),
                                            current.originatingHost() instanceof SFMScreenMultiplexer
                                                    ? OpenPanelAction.Direction.RIGHT : OpenPanelAction.Direction.FOCUSED, recipe);
                                });
                            });
                }
                case CREATE -> SFMReleaseReviewCreateRuntime.queue(
                        new SFMReleaseReviewCreateRuntime.Request(
                                Path.of(StringArgumentType.getString(context, "path")),
                                StringArgumentType.getString(context, "lane"),
                                StringArgumentType.getString(context, "before"),
                                StringArgumentType.getString(context, "candidate"),
                                optionalPath(context, "repository_root")
                        ),
                        context.getSource()::sendFeedback
                );
                case OPEN, OPEN_READ_ONLY, OPEN_VIEW, OPEN_READ_ONLY_VIEW -> {
                    Path path = Path.of(StringArgumentType.getString(context, "path"));
                    boolean writable = kind == Kind.OPEN || kind == Kind.OPEN_VIEW;
                    yield queueOpen(target, path, writable, context.getSource()::sendFeedback, continuation -> {
                        if (kind != Kind.OPEN_VIEW && kind != Kind.OPEN_READ_ONLY_VIEW) return;
                        ResourceLocation sceneId = new ResourceLocation(
                                "sfm", "explorer/release_review/changes");
                        SFMPanelReopenRecipe recipe = new SFMReleaseReviewExplorerScreenType.Recipe(
                                sceneId,
                                SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                                Optional.empty()
                        );
                        SFMScreenPanel panel = recipe.reopen();
                        OpenPanelAction.Direction direction = continuation.originatingHost() instanceof SFMScreenMultiplexer
                                ? OpenPanelAction.Direction.RIGHT
                                : OpenPanelAction.Direction.FOCUSED;
                        if (OpenPanelAction.openPanel(continuation, panel, direction, recipe) == 0) {
                            throw new IllegalStateException("The release-review Changes explorer could not be opened");
                        }
                    });
                }
                case CANCEL_OPERATION -> {
                    long operation = LongArgumentType.getLong(context, "operation");
                    boolean cancelled = runtime.cancelOperation(operation);
                    var status = new SFMReleaseReviewOperationFeedback(target, operation, context.getSource()::sendFeedback);
                    if (runtime.pendingOperation().filter(value -> value.id() == operation).isPresent()) {
                        status.pending(Component.literal(cancelled ? "Cancellation requested before commit; waiting for the worker"
                                : "Commit is already in progress; waiting for its durable result"));
                    } else {
                        status.complete(Component.literal("That review operation is no longer pending"));
                    }
                    yield cancelled ? 1 : 0;
                }
                case SAVE -> mutation(context, runtime.save(), "Saved release review");
                case SAVE_AS -> {
                    Path path = Path.of(StringArgumentType.getString(context, "path"));
                    runtime.saveAs(path);
                    feedback(context, "Saved release review as " + path.toAbsolutePath().normalize());
                    yield 1;
                }
                case QUERY -> {
                    String expression = StringArgumentType.getString(context, "expression");
                    SFMReleaseReviewKernel.QueryResult result = runtime.query(expression);
                    feedback(context, result.reviewUnitIds().size() + " review unit(s) · "
                            + result.normalizedExpression());
                    yield result.reviewUnitIds().size();
                }
                case QUERY_ACTIVATE -> {
                    String expression = StringArgumentType.getString(context, "expression");
                    String normalized = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewQuery.normalize(expression);
                    var pending = runtime.activateQueryAsync(Optional.empty(), expression);
                    var status = new SFMReleaseReviewOperationFeedback(target,
                            runtime.pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L),
                            context.getSource()::sendFeedback);
                    status.pending(Component.literal("Saving active review query…"));
                    pending.whenComplete((result, failure) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        if (failure != null || !result.saved()) {
                            status.failed("Review query was not saved; previous queue retained.",
                                    failure != null ? message(failure) : result.failure().orElse("Query activation failed"),
                                    java.util.List.of());
                        } else status.complete(Component.literal("Activated " + normalized));
                    }));
                    yield 1;
                }
                case QUERY_SAVE -> mutation(
                        context,
                        runtime.saveNamedQuery(
                                StringArgumentType.getString(context, "query"),
                                StringArgumentType.getString(context, "expression"),
                                true),
                        "Saved and activated release-review query");
                case QUERY_NORMALIZE -> {
                    SFMReleaseReviewKernel.QueryResult result = runtime.query(
                            StringArgumentType.getString(context, "expression"));
                    feedback(context, result.normalizedExpression());
                    yield 1;
                }
                case STATUS -> {
                    SFMReleaseReviewKernel.CompletionReport report = runtime.status();
                    feedback(context, report.status().name().toLowerCase(Locale.ROOT)
                            + " · remaining=" + report.remaining()
                            + " blocking=" + report.blocking()
                            + " suspended=" + report.suspended()
                            + " unsupported=" + report.unsupported());
                    yield report.status() == SFMReleaseReviewKernel.CompletionStatus.COMPLETE ? 1 : 0;
                }
                case SELECT, NEXT, PREVIOUS, DEFER, RESUME, SHOW_CURRENT -> SFMReviewWorkQueueControls.invoke(
                        target, kind, kind == Kind.SELECT
                                ? Optional.of(StringArgumentType.getString(context, "unit")) : Optional.empty(),
                        context.getSource()::sendFeedback) ? 1 : 0;
                case COMMENT_CREATE -> {
                    SFMScreenMultiplexer workspace = (SFMScreenMultiplexer) target.originatingHost();
                    SFMReleaseReviewEditorCapture.Capture capture = SFMReleaseReviewEditorCapture.capture(
                            target,
                            workspace.contextSnapshot(),
                            runtime.document().orElseThrow(() ->
                                    new IllegalStateException("No release-review document is open"))
                    );
                    String proposalId = StringArgumentType.getString(context, "proposal");
                    var proposal = capture.requireProposal(proposalId);
                    var pending = runtime.createCommentAsync(
                            StringArgumentType.getString(context, "text"),
                            capture.adapted().pinnedSelection(),
                            proposal
                    );
                    var status = new SFMReleaseReviewOperationFeedback(target,
                            runtime.pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L),
                            context.getSource()::sendFeedback);
                    status.pending(Component.literal("Saving review comment…"));
                    pending.whenComplete((created, failure) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
                        if (failure != null) status.complete(Component.literal("Review comment save failed: " + message(failure)));
                        else if (!created.mutation().saved()) status.complete(Component.literal(created.mutation().failure()
                                .orElse("Unable to save release-review comment")));
                        else status.complete(Component.literal("Created " + created.commentId() + " using "
                                + proposal.kind().name().toLowerCase(Locale.ROOT)));
                    }));
                    yield 1;
                }
                case MIGRATION_DECIDE -> {
                    String rawDecision = StringArgumentType.getString(context, "decision");
                    SFMReleaseReviewV1.MigrationDecision decision = SFMReleaseReviewV1.MigrationDecision.valueOf(
                            rawDecision.replace('-', '_').toUpperCase(Locale.ROOT));
                    String rawCandidate = StringArgumentType.getString(context, "candidate");
                    java.util.OptionalInt candidate = rawCandidate.equalsIgnoreCase("none")
                            ? java.util.OptionalInt.empty()
                            : java.util.OptionalInt.of(Integer.parseInt(rawCandidate) - 1);
                    SFMReleaseReviewRuntime.MigrationMutationResult result = runtime.decideMigration(
                            StringArgumentType.getString(context, "migration"),
                            StringArgumentType.getString(context, "expected_state"),
                            decision,
                            candidate,
                            StringArgumentType.getString(context, "note")
                    );
                    if (!result.mutation().saved()) {
                        throw new IllegalStateException(result.mutation().failure()
                                .orElse("Unable to save migration decision"));
                    }
                    feedback(context, "Recorded " + decision.name().toLowerCase(Locale.ROOT)
                            + " for " + result.migrationId() + " as " + result.decisionCommentId());
                    yield 1;
                }
                case ATTEST -> mutation(context, runtime.attest(
                        StringArgumentType.getString(context, "maintainer"),
                        StringArgumentType.getString(context, "statement")),
                        "Recorded maintainer attestation");
            };
        } catch (RuntimeException | java.io.IOException failure) {
            throw new SimpleCommandExceptionType(Component.literal(message(failure))).create();
        }
    }

    static int queueOpen(
            SFMClientActionContext target, Path path, boolean writable,
            java.util.function.Consumer<Component> feedback,
            java.util.function.Consumer<SFMClientActionContext> afterOpen
    ) {
        var continuation = SFMClientActionContinuation.capture(target);
        var runtime = SFMReleaseReviewRuntime.get();
        var normalized = path.toAbsolutePath().normalize();
        var previousReview = runtime.snapshot();
        var previousExplorers = captureReopenedExplorers(target, normalized, previousReview.openEpoch());
        var pending = runtime.openAsync(normalized, writable);
        var status = new SFMReleaseReviewOperationFeedback(target,
                runtime.pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L), feedback);
        status.pending(ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastContent.pathMessage(
                "Opening ", ca.teamdman.sfm.client.explorer.SFMPath.fromNative(normalized), "…"));
        pending.whenComplete((opened, failure) -> net.minecraft.client.Minecraft.getInstance().execute(() -> {
            if (failure != null) {
                status.complete(Component.literal("Review open did not complete: " + message(failure)));
                return;
            }
            if (opened.document().isEmpty()) {
                status.complete(Component.literal("Review open failed: " + String.join("; ", opened.diagnostics())));
                return;
            }
            var current = runtime.snapshot();
            if (current.document().orElse(null) != opened.document().orElseThrow()
                    || !current.path().filter(normalized::equals).isPresent()) {
                status.complete(Component.literal("Review open finished, but a newer review is now active"));
                return;
            }
            status.complete(ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastContent.pathMessage(
                    "Opened ", ca.teamdman.sfm.client.explorer.SFMPath.fromNative(normalized),
                    writable ? " writable" : " read-only"));
            if (!continuation.isCurrent()) {
                status.complete(Component.literal("Review loaded; the originating panel closed, so no panel was opened"));
                return;
            }
            try {
                for (var captured : previousExplorers) captured.rebind(current.openEpoch(), status::complete);
                afterOpen.accept(continuation.context());
            } catch (RuntimeException problem) {
                status.complete(Component.literal("Review loaded, but its view could not open: " + message(problem)));
            }
        }));
        return 1;
    }

    private record ReopenedExplorer(
            SFMClientActionContinuation continuation,
            ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel explorer,
            ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.LensDescriptor lens
    ) {
        void rebind(long newEpoch, java.util.function.Consumer<Component> feedback) {
            if (!continuation.isCurrent() || !explorer.sessionSnapshot().roots().equals(java.util.Set.of(lens.root()))) return;
            var root = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.get()
                    .prepareReopenedLens(lens, newEpoch);
            explorer.replaceProjectionRoot(root).whenComplete((ignored, failure) -> {
                if (failure != null) net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    if (continuation.isCurrent()) feedback.accept(Component.literal(
                            "Review reopened, but its Explorer refresh failed: " + message(failure)));
                });
            });
        }
    }

    private static java.util.List<ReopenedExplorer> captureReopenedExplorers(
            SFMClientActionContext target, Path path, long oldEpoch
    ) {
        if (!(target.originatingHost() instanceof SFMScreenMultiplexer workspace)) return java.util.List.of();
        var answer = new java.util.ArrayList<ReopenedExplorer>();
        for (var id : workspace.panelIds()) {
            var panel = workspace.panelInstance(id);
            if (!(panel instanceof ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel explorer)) continue;
            var lens = ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.get()
                    .lensDescriptor(explorer.sessionSnapshot().roots());
            if (lens.isEmpty() || lens.orElseThrow().reviewOpenEpoch() != oldEpoch
                    || !lens.orElseThrow().reviewPath().equals(path)) continue;
            answer.add(new ReopenedExplorer(
                    SFMClientActionContinuation.capture(new SFMClientActionContext(workspace, () -> true, id)),
                    explorer, lens.orElseThrow()));
        }
        return java.util.List.copyOf(answer);
    }

    private static int mutation(
            CommandContext<SFMClientActionSource> context,
            SFMReleaseReviewRuntime.MutationResult result,
            String success
    ) throws CommandSyntaxException {
        if (!result.saved()) {
            throw new SimpleCommandExceptionType(Component.literal(result.failure().orElse("Review mutation failed"))).create();
        }
        feedback(context, success);
        return 1;
    }

    private static void feedback(CommandContext<SFMClientActionSource> context, String text) {
        context.getSource().sendFeedback(Component.literal(text));
    }

    private static String message(Throwable failure) {
        return Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getSimpleName());
    }

    private static Optional<Path> optionalPath(
            CommandContext<SFMClientActionSource> context,
            String name
    ) {
        try {
            return Optional.of(Path.of(StringArgumentType.getString(context, name)));
        } catch (IllegalArgumentException absent) {
            return Optional.empty();
        }
    }
}
