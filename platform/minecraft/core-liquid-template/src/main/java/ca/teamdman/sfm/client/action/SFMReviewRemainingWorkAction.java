package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewExplorerRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewQuery;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewRuntime;
import ca.teamdman.sfm.client.review.release_review.SFMReleaseReviewV1;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.workspace.SFMReleaseReviewExplorerScreenType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Discoverable, persistent work queue over the existing exact-coverage query kernel. */
public final class SFMReviewRemainingWorkAction implements SFMClientAction<SFMReviewLensSetAction.Target> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "review/work/remaining");

    @Override
    public Component title() { return Component.literal("Show remaining review work"); }

    @Override
    public Component description() {
        return Component.literal("Show exact unreviewed before/after surfaces in this Explorer; save the queue when writable");
    }

    @Override
    public SFMClientActionRequirement<SFMReviewLensSetAction.Target> requirement() {
        return SFMReviewLensSetAction::capture;
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument("lane", StringArgumentType.string())
                .suggests((context, builder) -> {
                    builder.suggest("all");
                    SFMReleaseReviewRuntime.get().document().stream()
                            .flatMap(review -> review.repositoryBindings().stream())
                            .map(SFMReleaseReviewV1.RepositoryBinding::laneId)
                            .map(StringArgumentType::escapeIfRequired).forEach(builder::suggest);
                    return builder.buildFuture();
                }).executes(this::invoke));
    }

    public static List<SFMActionChoice> choices(SFMReleaseReviewV1 review) {
        ArrayList<SFMActionChoice> choices = new ArrayList<>();
        choices.add(SFMActionChoice.invoke(ID, "all", "Remaining work · all configured lanes"));
        review.repositoryBindings().stream().map(SFMReleaseReviewV1.RepositoryBinding::laneId)
                .distinct().sorted().forEach(lane -> choices.add(SFMActionChoice.invoke(
                        ID, StringArgumentType.escapeIfRequired(lane), "Remaining work · " + lane)));
        return List.copyOf(choices);
    }

    static String expression(SFMReleaseReviewV1 review, String lane) {
        if (lane.equals("all")) return "remaining intersect HEAD";
        if (review.repositoryBindings().stream().noneMatch(binding -> binding.laneId().equals(lane))) {
            throw new IllegalArgumentException("Unknown configured review lane: " + lane);
        }
        // A lane is data, not executable query text. Refuse names the current
        // grammar cannot represent as one atom instead of interpolating operators.
        if (!(SFMReleaseReviewQuery.parse(lane) instanceof SFMReleaseReviewQuery.Atom atom)
                || !atom.value().equals(lane) || lane.startsWith("#")
                || java.util.Set.of("head", "changed", "changed-domain", "approved-raw", "approved-effective",
                        "remaining", "uncovered", "blocking", "suspended", "missing", "deferred", "unsupported",
                        "stale-producer").contains(lane.toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("This lane cannot be represented by the current review query grammar");
        }
        return SFMReleaseReviewQuery.normalize("remaining intersect " + lane + " HEAD");
    }

    @Override
    public int execute(SFMReviewLensSetAction.Target target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        try {
            if (!target.stillCurrent()) throw new IllegalStateException("The captured review Explorer changed");
            SFMReleaseReviewRuntime runtime = SFMReleaseReviewRuntime.get();
            var before = runtime.snapshot();
            String expression = expression(before.document().orElseThrow(), StringArgumentType.getString(context, "lane"));
            if (!before.writable()) {
                showQueue(target, expression, new SFMReleaseReviewOperationFeedback(target.actionContext(), 0,
                        context.getSource()::sendFeedback), "Temporary read-only queue");
                return 1;
            }
            var pending = runtime.activateQueryAsync(Optional.empty(), expression);
            var status = new SFMReleaseReviewOperationFeedback(target.actionContext(),
                    runtime.pendingOperation().map(SFMReleaseReviewRuntime.OperationSnapshot::id).orElse(0L),
                    context.getSource()::sendFeedback);
            status.pending(Component.literal("Preparing and saving remaining review work…"));
            pending.whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
                if (failure != null || !result.saved()) {
                    status.failed("Remaining-work queue was not saved; previous queue retained.",
                            failure != null ? message(failure) : result.failure().orElse("Query activation failed"),
                            List.of());
                    return;
                }
                if (!target.stillCurrent() || runtime.snapshot().generation() != before.generation() + 1) {
                    status.complete(Component.literal("Review queue saved; originating view changed, so it was not replaced."));
                    return;
                }
                showQueue(target, expression, status, "Saved remaining-work queue");
            }));
            return 1;
        } catch (RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal(message(failure))).create();
        }
    }

    private static void showQueue(SFMReviewLensSetAction.Target target, String expression,
                                  SFMReleaseReviewOperationFeedback status, String outcome) {
        try {
            SFMReleaseReviewExplorerRuntime.get().switchLens(target.explorer(),
                            SFMReleaseReviewExplorerScreenType.Projection.QUERY, Optional.of(expression))
                    .whenComplete((lens, failure) -> Minecraft.getInstance().execute(() -> {
                        if (failure != null) {
                            status.failed("Review queue could not be displayed.", message(failure), List.of());
                        } else {
                            String filter = target.explorer().sessionSnapshot().settings().filterQuery();
                            status.complete(Component.literal(outcome + "; " + SFMReviewLensSetAction.filterDescription(filter)));
                        }
                    }));
        } catch (RuntimeException failure) {
            status.failed("Review queue could not be displayed.", message(failure), List.of());
        }
    }

    private static String message(Throwable failure) {
        while (failure.getCause() != null) failure = failure.getCause();
        return Optional.ofNullable(failure.getMessage()).orElse(failure.getClass().getSimpleName());
    }
}
