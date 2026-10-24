package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.inspection.SFMInspectionRuntime;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerContextActionProvider;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SFMExplorerDiffAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation("sfm", "explorer/diff");
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    @Override public Component title() { return Component.literal("Show diff / open comparison workspace"); }
    @Override public Component description() { return Component.literal("Add a comparison root for two JARs or working trees; open prepared evidence"); }
    @Override public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean() ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(Component.literal("Originating workspace closed"));
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("open").then(
                RequiredArgumentBuilder.<SFMClientActionSource, String>argument("workspace", StringArgumentType.string()).executes(this::invoke)));
        node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("compare").then(
                RequiredArgumentBuilder.<SFMClientActionSource, String>argument("old", StringArgumentType.string()).then(
                RequiredArgumentBuilder.<SFMClientActionSource, String>argument("new", StringArgumentType.string()).executes(this::invoke))));
    }
    @Override public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> command) {
        String prepared;
        try { prepared = StringArgumentType.getString(command, "workspace"); } catch (IllegalArgumentException absent) { prepared = null; }
        final String workspace = prepared;
        final Path old = workspace == null ? Path.of(StringArgumentType.getString(command, "old")) : null;
        final Path newer = workspace == null ? Path.of(StringArgumentType.getString(command, "new")) : null;
        if (!BUSY.compareAndSet(false, true)) { command.getSource().sendFeedback(Component.literal("A comparison is already preparing")); return 0; }
        var continuation = SFMClientActionContinuation.capture(target);
        feedback(command, target, "Preparing Explorer comparison…", false);
        CompletableFuture.supplyAsync(() -> {
            try { return workspace == null ? SFMInspectionRuntime.compare(old, newer) : SFMInspectionRuntime.read(Path.of(workspace)); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, SFMInspectionRuntime.WORKER).whenComplete((trees, error) -> Minecraft.getInstance().execute(() -> {
            try {
                if (!continuation.isCurrent()) { command.getSource().sendFeedback(Component.literal("Origin closed; comparison discarded")); return; }
                if (error != null) throw new IllegalStateException(error.getCause() == null ? error : error.getCause());
                SFMInspectionRuntime.open(trees, continuation.context(), workspace == null);
                feedback(command, continuation.context(), "Comparison roots ready. Expand a strategy, then a file pair and representation.", false);
            } catch (RuntimeException failure) {
                ca.teamdman.sfm.SFM.LOGGER.warn("SFM_INSPECTION_COMPARISON_FAILED", failure);
                feedback(command, continuation.context(), "Comparison unavailable: " + failure.getMessage(), true);
            }
            finally { BUSY.set(false); }
        }));
        return 1;
    }
    private static void feedback(CommandContext<SFMClientActionSource> command, SFMClientActionContext context,
                                 String message, boolean failed) {
        command.getSource().sendFeedback(Component.literal(message));
        ca.teamdman.sfm.SFM.LOGGER.info("SFM_INSPECTION_COMPARISON_STATUS {}", message);
{% if features.workspace_notifications %}
        if (context.originatingHostIsCurrent().getAsBoolean()
                && context.originatingHost() instanceof ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer workspace)
            workspace.showWorkspaceToast("sfm:explorer-inspection", Component.literal(message.lines().findFirst().orElse(message)), failed);
{% endif %}
    }
    public static List<SFMActionChoice> choices(SFMExplorerContextActionProvider.Request request) {
        var context = request.actionContext();
        if (!(context.originatingHost() instanceof ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer workspace)
                || context.originatingPanelId() == null
                || !(workspace.panelInstance(context.originatingPanelId()) instanceof ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel panel)
                || !panel.explorerId().equals(request.explorerId())) return List.of();
        var selection = panel.sessionSnapshot().selectedPaths().stream().sorted().toList();
        if (selection.size() != 2 || selection.stream().anyMatch(path -> path.kind() != SFMPath.Kind.FILE)) return List.of();
        SFMPath a = selection.get(0), b = selection.get(1);
        return List.of(choice(a, b), choice(b, a));
    }
    private static SFMActionChoice choice(SFMPath old, SFMPath newer) {
        return SFMActionChoice.invoke(ID, "compare " + StringArgumentType.escapeIfRequired(old.toNativePath().toString()) + " "
                + StringArgumentType.escapeIfRequired(newer.toNativePath().toString()),
                "Show diff · old: " + SFMInspectionRuntime.inputLabel(old.toNativePath(), newer.toNativePath())
                        + " → new: " + SFMInspectionRuntime.inputLabel(newer.toNativePath(), old.toNativePath()));
    }
}
