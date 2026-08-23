package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewCommentDraftService;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Action-backed constrained choice flow for one immutable review selection. */
public final class SFMReleaseReviewCommentChoiceAction
        implements SFMClientAction<SFMClientActionContext> {
    static final String APPROVED = "#approved Reviewed through the in-game release-review surface.";
    static final String NEEDS_CHANGE = "#needs-change Requires follow-up from the in-game release-review surface.";
    private static final ResourceLocation SCENE_ID = new ResourceLocation(SFM.MOD_ID, "text_editor");

    public enum Kind {
        OPEN("review/comment/choice/open", "Comment on review selection",
                "Choose a recent, approval, blocking, or arbitrary comment for an exact frozen selection"),
        APPLY("review/comment/choice/apply", "Apply review comment",
                "Atomically apply comment text to an exact frozen review selection"),
        OTHER("review/comment/choice/other", "Write another review comment",
                "Open the preferred writable text editor for arbitrary review comment text"),
        CANCEL("review/comment/choice/cancel", "Cancel review comment",
                "Discard an exact pending review-comment choice"),
        REOPEN_WRITABLE("review/comment/choice/reopen_writable", "Reopen review writable",
                "Acquire a writer lease for the same tracked review and retain the frozen selection");

        private final String path;
        private final String title;
        private final String description;

        Kind(String path, String title, String description) {
            this.path = path;
            this.title = title;
            this.description = description;
        }

        public String path() {
            return path;
        }

        ResourceLocation id() {
            return new ResourceLocation(SFM.MOD_ID, path);
        }
    }

    private final Kind kind;

    public SFMReleaseReviewCommentChoiceAction(Kind kind) {
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
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
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent());
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> draft = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("draft", StringArgumentType.word());
        if (kind == Kind.APPLY) {
            draft.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument("text", StringArgumentType.string())
                    .executes(this::invoke));
        } else {
            draft.executes(this::invoke);
        }
        node.then(draft);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        String draftId = StringArgumentType.getString(context, "draft");
        try {
            return switch (kind) {
                case OPEN -> openChoices(target, draftId);
                case APPLY -> apply(
                        target,
                        draftId,
                        StringArgumentType.getString(context, "text"),
                        context
                );
                case OTHER -> openOtherEditor(target, draftId);
                case CANCEL -> {
                    SFMReleaseReviewCommentDraftService.get().cancel(draftId);
                    context.getSource().sendFeedback(Component.literal("Review comment cancelled"));
                    yield 1;
                }
                case REOPEN_WRITABLE -> reopenWritable(target, draftId, context);
            };
        } catch (RuntimeException | IOException failure) {
            throw new SimpleCommandExceptionType(Component.literal(rootMessage(failure))).create();
        }
    }

    static List<SFMActionChoice> choices(
            String draftId,
            boolean writable,
            List<String> recent
    ) {
        String draftArgument = StringArgumentType.escapeIfRequired(draftId);
        ArrayList<SFMActionChoice> answer = new ArrayList<>();
        if (!writable) {
            answer.add(SFMActionChoice.invoke(
                    Kind.REOPEN_WRITABLE.id(),
                    draftArgument,
                    "Reopen this review writable"
            ));
            answer.add(SFMActionChoice.invoke(Kind.CANCEL.id(), draftArgument, "Cancel"));
            return List.copyOf(answer);
        }
        answer.add(applyChoice(draftArgument, APPROVED, "#approved"));
        answer.add(applyChoice(draftArgument, NEEDS_CHANGE, "#needs-change"));
        LinkedHashSet<String> unique = new LinkedHashSet<>(recent);
        unique.remove(APPROVED);
        unique.remove(NEEDS_CHANGE);
        for (String value : unique) {
            String compact = value.replace('\r', ' ').replace('\n', ' ').strip();
            if (compact.length() > 80) compact = compact.substring(0, 79) + "…";
            answer.add(applyChoice(draftArgument, value, "Recent · " + compact));
        }
        answer.add(SFMActionChoice.invoke(Kind.OTHER.id(), draftArgument, "Other…"));
        answer.add(SFMActionChoice.invoke(Kind.CANCEL.id(), draftArgument, "Cancel"));
        return List.copyOf(answer);
    }

    private static SFMActionChoice applyChoice(String draftArgument, String text, String title) {
        return SFMActionChoice.invoke(
                Kind.APPLY.id(),
                draftArgument + " " + StringArgumentType.escapeIfRequired(text),
                title
        );
    }

    private static int openChoices(SFMClientActionContext target, String draftId) {
        SFMReleaseReviewCommentDraftService service = SFMReleaseReviewCommentDraftService.get();
        SFMReleaseReviewCommentDraftService.Draft draft = service.requireCurrent(draftId);
        boolean writable = SFMReleaseReviewRuntime.get().snapshot().writable();
        // Comment is itself a choice from a contextual palette. Replace that
        // transient surface instead of nesting another palette above it, so a
        // terminal Apply/Cancel choice naturally returns to the review panel.
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) {
            palette.onClose();
        }
        SFMCommandPaletteScreen.openChoices(
                target,
                Component.literal("Comment on " + proposalLabel(draft)),
                choices(draft.id(), writable, service.recentTemplates())
        );
        return 1;
    }

    private static int apply(
            SFMClientActionContext target,
            String draftId,
            String text,
            CommandContext<SFMClientActionSource> context
    ) {
        SFMReleaseReviewRuntime.CommentMutationResult result =
                SFMReleaseReviewCommentDraftService.get().apply(draftId, text);
        if (!result.mutation().saved()) {
            throw new IllegalStateException(result.mutation().failure()
                    .orElse("Unable to save release-review comment"));
        }
        refreshReviewExplorers(target);
        context.getSource().sendFeedback(Component.literal(
                "Created " + result.commentId() + " and refreshed review lenses"));
        return 1;
    }

    private static int openOtherEditor(SFMClientActionContext target, String draftId) {
        SFMReleaseReviewCommentDraftService service = SFMReleaseReviewCommentDraftService.get();
        service.requireCurrent(draftId);
        if (!SFMReleaseReviewRuntime.get().snapshot().writable()) {
            throw new IllegalStateException("Reopen this release review writable before adding a comment");
        }
        ResourceLocation editorId = preferredEditorId();
        var recipe = new SFMTextEditorPanelRecipe(
                SCENE_ID,
                editorId,
                new SFMTextDocumentSource.Literal(""),
                false,
                "Review Comment Draft · Ctrl+S to apply",
                () -> content -> saveDraft(target, draftId, content)
        );
        int opened = OpenPanelAction.openPanel(
                target,
                recipe.reopen(),
                OpenPanelAction.Direction.RIGHT,
                recipe
        );
        if (opened == 0) throw new IllegalStateException("The preferred review-comment editor could not be opened");
        return opened;
    }

    private static SFMTextDocumentSaveResult saveDraft(
            SFMClientActionContext target,
            String draftId,
            String content
    ) {
        try {
            SFMReleaseReviewRuntime.CommentMutationResult result =
                    SFMReleaseReviewCommentDraftService.get().apply(draftId, content);
            if (!result.mutation().saved()) {
                return SFMTextDocumentSaveResult.rejected(Component.literal(
                        result.mutation().failure().orElse("Unable to save release-review comment")));
            }
            refreshReviewExplorers(target);
            return SFMTextDocumentSaveResult.success();
        } catch (RuntimeException failure) {
            return SFMTextDocumentSaveResult.rejected(Component.literal(rootMessage(failure)));
        }
    }

    private static int reopenWritable(
            SFMClientActionContext target,
            String draftId,
            CommandContext<SFMClientActionSource> context
    ) throws IOException {
        SFMReleaseReviewCommentDraftService service = SFMReleaseReviewCommentDraftService.get();
        var draft = service.require(draftId);
        var opened = SFMReleaseReviewRuntime.get().open(draft.reviewPath(), true);
        if (opened.document().isEmpty()) {
            throw new IllegalStateException(String.join("; ", opened.diagnostics()));
        }
        service.rebindAfterWritableOpen(draftId);
        context.getSource().sendFeedback(Component.literal("Reopened release review writable"));
        return openChoices(target, draftId);
    }

    private static void refreshReviewExplorers(SFMClientActionContext target) {
        if (!(target.originatingHost() instanceof SFMScreenMultiplexer workspace)) return;
        for (var panel : workspace.panels()) {
            if (panel instanceof SFMExplorerPanel explorer
                    && ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime.get()
                    .lensDescriptor(explorer.sessionSnapshot().roots()).isPresent()) {
                explorer.refreshExpandedProjection();
            }
        }
    }

    private static String proposalLabel(SFMReleaseReviewCommentDraftService.Draft draft) {
        var proposal = draft.proposal();
        String kind = proposal.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return proposal.semanticKey().map(value -> kind + " · " + value).orElse(kind);
    }

    private static ResourceLocation preferredEditorId() {
        ISFMTextEditorRegistration preferred = SFMClientTextEditorConfig.getPreferredTextEditor();
        ResourceLocation configured = SFMTextEditors.registry().getId(preferred);
        return configured == null
                ? SFMTextEditors.V3.getId().orElseThrow().location()
                : configured;
    }

    private static String rootMessage(Throwable failure) {
        Throwable cursor = failure;
        while (cursor.getCause() != null) cursor = cursor.getCause();
        return Optional.ofNullable(cursor.getMessage()).orElse(cursor.getClass().getSimpleName());
    }
}
