package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastQueue;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Exact-id semantic operations shared by toast pointer and palette input. */
public final class SFMToastAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Operation {
        COPY,
        STOP_TIMER,
        RESUME_TIMER,
        DISMISS
    }

    private static final SimpleCommandExceptionType STALE = new SimpleCommandExceptionType(
            Component.literal("That workspace toast is stale or no longer available"));

    private final Operation operation;

    public SFMToastAction(Operation operation) {
        this.operation = operation;
    }

    @Override
    public Component title() {
        return Component.literal(switch (operation) {
            case COPY -> "Copy toast text";
            case STOP_TIMER -> "Stop Timer Forever";
            case RESUME_TIMER -> "Resume Timer";
            case DISMISS -> "Dismiss";
        });
    }

    @Override
    public Component description() {
        return Component.literal(switch (operation) {
            case COPY -> "Copy the exact addressed notification to the clipboard";
            case STOP_TIMER -> "Pause this notification without resetting its remaining lifetime";
            case RESUME_TIMER -> "Continue this notification from its previously remaining lifetime";
            case DISMISS -> "Immediately remove only this notification";
        });
    }

    @Override
    public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return context -> context.requireOriginatingHost(
                SFMScreenMultiplexer.class,
                Component.literal("A live SFM workspace is required for toast actions")
        );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument(
                        "toast-id", LongArgumentType.longArg(1))
                .suggests((context, builder) -> {
                    Object host = context.getSource().context().originatingHost();
                    if (host instanceof SFMScreenMultiplexer workspace) {
                        workspace.activeWorkspaceToastIds().forEach(id -> builder.suggest(id.commandArgument()));
                    }
                    return builder.buildFuture();
                })
                .executes(this::invoke));
    }

    @Override
    public int execute(
            SFMScreenMultiplexer workspace,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        SFMWorkspaceToastQueue.ToastId id = new SFMWorkspaceToastQueue.ToastId(
                LongArgumentType.getLong(context, "toast-id"));
        boolean available = switch (operation) {
            case COPY -> workspace.copyWorkspaceToast(id);
            case STOP_TIMER -> isAvailable(workspace.stopWorkspaceToastTimer(id));
            case RESUME_TIMER -> isAvailable(workspace.resumeWorkspaceToastTimer(id));
            case DISMISS -> workspace.dismissWorkspaceToast(id)
                    == SFMWorkspaceToastQueue.MutationResult.APPLIED;
        };
        if (!available) throw STALE.create();
        context.getSource().sendFeedback(Component.literal(switch (operation) {
            case COPY -> "Copied toast " + id.value() + " to the clipboard";
            case STOP_TIMER -> "Stopped timer for toast " + id.value();
            case RESUME_TIMER -> "Resumed timer for toast " + id.value();
            case DISMISS -> "Dismissed toast " + id.value();
        }));
        return 1;
    }

    private static boolean isAvailable(SFMWorkspaceToastQueue.MutationResult result) {
        return result == SFMWorkspaceToastQueue.MutationResult.APPLIED
                || result == SFMWorkspaceToastQueue.MutationResult.UNCHANGED;
    }
}
