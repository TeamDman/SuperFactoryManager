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
        command.getSource().sendFeedback(Component.literal("Preparing Explorer comparison…"));
        CompletableFuture.supplyAsync(() -> {
            try { return workspace == null ? SFMInspectionRuntime.compare(old, newer) : SFMInspectionRuntime.read(Path.of(workspace)); }
            catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, SFMInspectionRuntime.WORKER).whenComplete((trees, error) -> Minecraft.getInstance().execute(() -> {
            try {
                if (!continuation.isCurrent()) { command.getSource().sendFeedback(Component.literal("Origin closed; comparison discarded")); return; }
                if (error != null) throw new IllegalStateException(error.getCause() == null ? error : error.getCause());
                SFMInspectionRuntime.open(trees, continuation.context(), workspace == null);
                command.getSource().sendFeedback(Component.literal("Comparison roots ready. Expand a strategy, then a file pair and representation."));
            } catch (RuntimeException failure) { command.getSource().sendFeedback(Component.literal("Comparison unavailable: " + failure.getMessage())); }
            finally { BUSY.set(false); }
        }));
        return 1;
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
                "Show diff · old: " + old.toNativePath().getFileName() + " → new: " + newer.toNativePath().getFileName());
    }
}
