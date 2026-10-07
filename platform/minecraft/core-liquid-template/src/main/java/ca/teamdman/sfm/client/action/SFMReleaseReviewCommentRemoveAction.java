package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewGeneratedMarkers;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewKernel;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.common.localization.LocalizationEntry;
import ca.teamdman.sfm.common.localization.SFMLocalizationDatagen;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Identity-bound, confirmed removal of one durable human review comment. */
public final class SFMReleaseReviewCommentRemoveAction
        implements SFMClientAction<SFMReleaseReviewCommentRemoveAction.Target> {
    public enum Phase {
        REQUEST("review/comment/remove"),
        CONFIRM("review/comment/remove/confirm");

        private final ResourceLocation id;

        Phase(String path) {
            id = new ResourceLocation(SFM.MOD_ID, path);
        }

        public ResourceLocation id() {
            return id;
        }
    }

    @SFMLocalizationDatagen
    public static final LocalizationEntry REMOVE = new LocalizationEntry(
            "gui.sfm.release_review.comment.remove", "Remove comment…");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CONFIRM = new LocalizationEntry(
            "gui.sfm.release_review.comment.remove.confirm", "Remove this comment permanently");
    @SFMLocalizationDatagen
    public static final LocalizationEntry CANCEL = new LocalizationEntry(
            "gui.sfm.release_review.comment.remove.cancel", "Cancel — keep this comment");
    @SFMLocalizationDatagen
    public static final LocalizationEntry REMOVING = new LocalizationEntry(
            "gui.sfm.release_review.comment.removing", "Removing review comment…");
    @SFMLocalizationDatagen
    public static final LocalizationEntry REMOVED = new LocalizationEntry(
            "gui.sfm.release_review.comment.removed", "Removed review comment %s");

    record Target(
            SFMClientActionContext context,
            SFMScreenMultiplexer workspace,
            Path reviewPath,
            long openEpoch
    ) {
        Target {
            Objects.requireNonNull(context, "context");
            Objects.requireNonNull(workspace, "workspace");
            reviewPath = Objects.requireNonNull(reviewPath, "reviewPath").toAbsolutePath().normalize();
        }
    }

    private static final ResourceLocation PALETTE_CLOSE = new ResourceLocation(SFM.MOD_ID, "palette/close");
    private final Phase phase;

    public SFMReleaseReviewCommentRemoveAction(Phase phase) {
        this.phase = Objects.requireNonNull(phase, "phase");
    }

    @Override
    public Component title() {
        return phase == Phase.REQUEST ? REMOVE.getComponent() : CONFIRM.getComponent();
    }

    @Override
    public Component description() {
        return Component.literal(phase == Phase.REQUEST
                ? "Preview removal of one human review comment and its owned selector state"
                : "Remove only the comment identified by the confirmed review revision");
    }

    @Override
    public SFMClientActionRequirement<Target> requirement() {
        return context -> {
            var snapshot = SFMReleaseReviewRuntime.get().snapshot();
            if (!context.originatingHostIsCurrent().getAsBoolean()
                    || !(context.originatingHost() instanceof SFMScreenMultiplexer workspace)
                    || snapshot.path().isEmpty() || snapshot.document().isEmpty()) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Open a saved release review in an SFM workspace first"));
            }
            if (!snapshot.writable()) {
                return SFMClientActionAvailability.unavailable(Component.literal(
                        "Reopen this release review writable before removing comments"));
            }
            return SFMClientActionAvailability.available(new Target(
                    context, workspace, snapshot.path().orElseThrow(), snapshot.openEpoch()));
        };
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        var comment = RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                "comment", StringArgumentType.string()).suggests((context, builder) -> {
            SFMReleaseReviewRuntime.get().document().stream()
                    .flatMap(review -> review.reviewSession().comments().stream())
                    .filter(value -> !SFMReleaseReviewGeneratedMarkers.isChangeMarker(value))
                    .map(value -> StringArgumentType.escapeIfRequired(value.id()))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        });
        comment.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(
                        "expected_epoch", LongArgumentType.longArg(0))
                .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                "expected_semantic_hash", StringArgumentType.word())
                        .executes(this::invoke)));
        node.then(comment);
    }

    @Override
    public int execute(Target target, CommandContext<SFMClientActionSource> command)
            throws CommandSyntaxException {
        String commentId = StringArgumentType.getString(command, "comment");
        long expectedEpoch = LongArgumentType.getLong(command, "expected_epoch");
        String expectedHash = StringArgumentType.getString(command, "expected_semantic_hash");
        var snapshot = requireCurrent(target, expectedEpoch, expectedHash, commentId);
        if (phase == Phase.REQUEST) {
            if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
            SFMCommandPaletteScreen.openChoices(
                    target.context(),
                    Component.literal("Remove review comment? · " + summary(commentId)),
                    confirmationChoices(commentId, expectedEpoch, expectedHash));
            command.getSource().sendFeedback(Component.literal(
                    "Comment removal requires confirmation; no data has changed"));
            return 1;
        }

        var continuation = SFMReleaseReviewCommentChoiceAction.captureReviewRefresh(target.context());
        var persistence = SFMReleaseReviewRuntime.get().removeCommentAsync(
                target.reviewPath(), expectedEpoch, commentId, expectedHash);
        long operation = SFMReleaseReviewRuntime.get().pendingOperation()
                .map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L);
        var feedback = new SFMReleaseReviewOperationFeedback(
                target.context(), operation, command.getSource()::sendFeedback);
        feedback.pending(REMOVING.getComponent());
        persistence.whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            if (failure != null) {
                feedback.failed("Comment was not removed", rootMessage(failure), List.of());
                return;
            }
            if (!result.saved()) {
                feedback.failed("Comment was not removed",
                        result.failure().orElse("The review mutation was not saved"), List.of());
                return;
            }
            if (continuation.isCurrent()
                    && SFMReleaseReviewRuntime.get().snapshot().openEpoch() == expectedEpoch) {
                SFMReleaseReviewCommentChoiceAction.refreshReviewExplorers(continuation.context());
            }
            feedback.complete(REMOVED.getComponent(commentId));
        }));
        return PanelActionSupport.closePaletteChainAfter(1, target.workspace());
    }

    public static Optional<SFMActionChoice> requestChoice(String commentId, long openEpoch) {
        var snapshot = SFMReleaseReviewRuntime.get().snapshot();
        if (!snapshot.writable() || snapshot.openEpoch() != openEpoch || snapshot.document().isEmpty()) {
            return Optional.empty();
        }
        var comment = snapshot.document().orElseThrow().reviewSession().comments().stream()
                .filter(value -> value.id().equals(commentId))
                .filter(value -> !SFMReleaseReviewGeneratedMarkers.isChangeMarker(value))
                .findFirst();
        if (comment.isEmpty()) return Optional.empty();
        String hash = SFMReleaseReviewKernel.semanticStateHash(snapshot.document().orElseThrow());
        return Optional.of(SFMActionChoice.invoke(
                Phase.REQUEST.id(), arguments(commentId, openEpoch, hash), REMOVE.getComponent().getString()));
    }

    static List<SFMActionChoice> confirmationChoices(String commentId, long openEpoch, String semanticHash) {
        String arguments = arguments(commentId, openEpoch, semanticHash);
        return List.of(
                SFMActionChoice.invoke(Phase.CONFIRM.id(), arguments, CONFIRM.getComponent().getString()),
                SFMActionChoice.invoke(PALETTE_CLOSE, "", CANCEL.getComponent().getString()));
    }

    private static String arguments(String commentId, long openEpoch, String semanticHash) {
        return StringArgumentType.escapeIfRequired(commentId) + " " + openEpoch + " " + semanticHash;
    }

    private static SFMReleaseReviewRuntime.Snapshot requireCurrent(
            Target target, long expectedEpoch, String expectedHash, String commentId
    ) throws CommandSyntaxException {
        var snapshot = SFMReleaseReviewRuntime.get().snapshot();
        boolean exact = snapshot.writable()
                && snapshot.openEpoch() == target.openEpoch()
                && snapshot.openEpoch() == expectedEpoch
                && snapshot.path().map(value -> value.toAbsolutePath().normalize().equals(target.reviewPath())).orElse(false)
                && snapshot.document().map(value -> SFMReleaseReviewKernel.semanticStateHash(value).equals(expectedHash))
                .orElse(false);
        if (!exact) throw new SimpleCommandExceptionType(Component.literal(
                "That comment-removal confirmation is stale; no comment was removed")).create();
        var comment = snapshot.document().orElseThrow().reviewSession().comments().stream()
                .filter(value -> value.id().equals(commentId)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal(
                        "That comment is no longer present; no comment was removed")).create());
        if (SFMReleaseReviewGeneratedMarkers.isChangeMarker(comment)) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Generated change evidence is not a removable human comment")).create();
        }
        return snapshot;
    }

    private static String summary(String commentId) {
        return SFMReleaseReviewRuntime.get().document().orElseThrow().reviewSession().comments().stream()
                .filter(value -> value.id().equals(commentId)).findFirst()
                .map(value -> ca.teamdman.sfm.client.presentation.SFMTextSummary.codePoints(
                        ca.teamdman.sfm.client.presentation.SFMTextSummary.singleLine(value.text()), 80))
                .orElse(commentId);
    }

    private static String rootMessage(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return String.valueOf(failure.getMessage());
    }
}
