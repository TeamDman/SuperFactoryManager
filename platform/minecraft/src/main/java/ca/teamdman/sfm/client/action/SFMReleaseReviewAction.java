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
public final class SFMReleaseReviewAction implements SFMClientAction<SFMClientActionContext> {
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
        OPEN("review/session/open", "Open release review", "Open one tracked .sfm-review.json file writable"),
        OPEN_READ_ONLY("review/session/open/read_only", "Open release review read-only",
                "Inspect one tracked review file without taking its writer lease"),
        OPEN_VIEW("review/session/open/view", "Open release review view",
                "Open one tracked review writable and show its generic Changes explorer"),
        OPEN_READ_ONLY_VIEW("review/session/open/read_only/view", "Open release review view read-only",
                "Open one tracked review read-only and show its generic Changes explorer"),
        SAVE("review/session/save", "Save release review", "Atomically save the current review file"),
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
        return Component.literal(kind.title);
    }

    @Override
    public Component description() {
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
            case CREATE -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("path", StringArgumentType.string())
                    .then(RequiredArgumentBuilder
                            .<SFMClientActionSource, String>argument("lane", StringArgumentType.string())
                            .then(RequiredArgumentBuilder
                                    .<SFMClientActionSource, String>argument("before", StringArgumentType.string())
                                    .then(RequiredArgumentBuilder
                                            .<SFMClientActionSource, String>argument(
                                                    "candidate", StringArgumentType.string())
                                            .executes(this::invoke)
                                            .then(RequiredArgumentBuilder
                                                    .<SFMClientActionSource, String>argument(
                                                            "repository_root", StringArgumentType.string())
                                                    .executes(this::invoke))))));
            case OPEN, OPEN_READ_ONLY, OPEN_VIEW, OPEN_READ_ONLY_VIEW, SAVE_AS -> node.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("path", StringArgumentType.greedyString())
                    .executes(this::invoke));
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
                    SFMReleaseReviewRuntime.OpenResult result = runtime.open(path, writable);
                    if (result.document().isEmpty()) {
                        throw new IllegalArgumentException(String.join("; ", result.diagnostics()));
                    }
                    feedback(context, "Opened " + path.toAbsolutePath().normalize()
                            + (writable ? " writable" : " read-only"));
                    if (kind == Kind.OPEN_VIEW || kind == Kind.OPEN_READ_ONLY_VIEW) {
                        ResourceLocation sceneId = new ResourceLocation(
                                "sfm", "explorer/release_review/changes");
                        SFMPanelReopenRecipe recipe = new SFMReleaseReviewExplorerScreenType.Recipe(
                                sceneId,
                                SFMReleaseReviewExplorerScreenType.Projection.CHANGES,
                                Optional.empty()
                        );
                        SFMScreenPanel panel = recipe.reopen();
                        OpenPanelAction.Direction direction = target.originatingHost() instanceof SFMScreenMultiplexer
                                ? OpenPanelAction.Direction.RIGHT
                                : OpenPanelAction.Direction.FOCUSED;
                        if (OpenPanelAction.openPanel(target, panel, direction, recipe) == 0) {
                            throw new IllegalStateException("The release-review Changes explorer could not be opened");
                        }
                    }
                    yield 1;
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
                    SFMReleaseReviewKernel.QueryResult result = runtime.query(expression);
                    int outcome = mutation(context, runtime.activateQuery(Optional.empty(), expression),
                            "Activated " + result.normalizedExpression());
                    yield outcome;
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
                case SELECT -> mutation(
                        context,
                        runtime.selectUnit(StringArgumentType.getString(context, "unit")),
                        "Selected release-review unit");
                case NEXT -> mutation(context, runtime.move(1), "Selected next release-review unit");
                case PREVIOUS -> mutation(context, runtime.move(-1), "Selected previous release-review unit");
                case DEFER -> mutation(context, runtime.deferCurrent(), "Deferred current release-review unit");
                case RESUME -> mutation(context, runtime.resumeDeferred(), "Resumed deferred release-review unit");
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
                    SFMReleaseReviewRuntime.CommentMutationResult created = runtime.createComment(
                            StringArgumentType.getString(context, "text"),
                            capture.adapted().pinnedSelection(),
                            proposal
                    );
                    if (!created.mutation().saved()) {
                        throw new IllegalStateException(created.mutation().failure()
                                .orElse("Unable to save release-review comment"));
                    }
                    feedback(context, "Created " + created.commentId() + " using "
                            + proposal.kind().name().toLowerCase(Locale.ROOT));
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
