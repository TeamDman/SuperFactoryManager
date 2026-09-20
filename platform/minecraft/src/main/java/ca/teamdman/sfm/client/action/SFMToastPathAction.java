package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathHierarchy;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.toast.SFMWorkspaceToastQueue;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
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

/** Exact notification/path identity; these operations never infer a path from current focus. */
public final class SFMToastPathAction implements SFMClientAction<SFMScreenMultiplexer> {
    public enum Operation {
        COPY("toast/path/copy", "Copy full path"),
        OPEN_TEXT("toast/path/text/open", "Open path as text"),
        OPEN_EXPLORER("toast/path/explorer/open", "Open path in Explorer");
        private final ResourceLocation id;
        private final String title;
        Operation(String path, String title) {
            this.id = new ResourceLocation("sfm", path);
            this.title = title;
        }
        public ResourceLocation id() { return id; }
    }

    private final Operation operation;
    public SFMToastPathAction(Operation operation) { this.operation = operation; }
    @Override public Component title() { return Component.literal(operation.title); }
    @Override public Component description() {
        return Component.literal("Use this notification's captured path; existing resolver authorization still applies");
    }
    @Override public SFMClientActionRequirement<SFMScreenMultiplexer> requirement() {
        return context -> context.requireOriginatingHost(SFMScreenMultiplexer.class,
                Component.literal("A live workspace notification is required"));
    }
    @Override public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.then(RequiredArgumentBuilder.<SFMClientActionSource, Long>argument("toast-id",
                LongArgumentType.longArg(1)).suggests((context, builder) -> {
            if (context.getSource().context().originatingHost() instanceof SFMScreenMultiplexer workspace) {
                workspace.activeWorkspaceToastIds().stream().filter(id -> !workspace.workspaceToastPaths(id).isEmpty())
                        .forEach(id -> builder.suggest(id.commandArgument()));
            }
            return builder.buildFuture();
        }).then(RequiredArgumentBuilder.<SFMClientActionSource, Integer>argument("path-index",
                IntegerArgumentType.integer(0, 7)).suggests((context, builder) -> {
            if (context.getSource().context().originatingHost() instanceof SFMScreenMultiplexer workspace) {
                var id = new SFMWorkspaceToastQueue.ToastId(LongArgumentType.getLong(context, "toast-id"));
                for (int index = 0; index < workspace.workspaceToastPaths(id).size(); index++) builder.suggest(index);
            }
            return builder.buildFuture();
        }).executes(this::invoke)));
    }
    @Override public int execute(SFMScreenMultiplexer workspace, CommandContext<SFMClientActionSource> command)
            throws CommandSyntaxException {
        var id = new SFMWorkspaceToastQueue.ToastId(LongArgumentType.getLong(command, "toast-id"));
        int index = IntegerArgumentType.getInteger(command, "path-index");
        List<SFMPath> paths = workspace.workspaceToastPaths(id);
        if (index >= paths.size()) throw failure("That notification path is stale or unavailable");
        SFMPath path = paths.get(index);
        if (operation == Operation.COPY) {
            if (!workspace.copyWorkspaceToastPath(id, index)) throw failure(
                    "Clipboard unavailable or the notification path expired");
            return 1;
        }
        // Placement may follow current focus; document identity and authority never do.
        SFMClientActionContext target = new SFMClientActionContext(workspace,
                command.getSource().context().originatingHostIsCurrent(), null);
        if (operation == Operation.OPEN_TEXT) {
            return SFMClientActionExecutor.execute("sfm action invoke sfm:path/open " + path.canonical() + " focus",
                    target, command.getSource()::sendFeedback);
        }
        try {
            SFMExplorerRuntime runtime = SFMExplorerRuntime.get();
            List<SFMPath> roots = runtime.repository().stateSnapshot().explorers().values().stream()
                    .flatMap(explorer -> explorer.session().snapshot().roots().stream()).distinct().toList();
            SFMPath root = path.kind() == SFMPath.Kind.FILE
                    ? runtime.authorizedFilesystemRootFor(path).orElseThrow(() ->
                    new IllegalArgumentException("No live authorized Explorer root contains the notification path"))
                    : SFMPathHierarchy.deepestContainingRoot(roots, path).orElseThrow(() ->
                    new IllegalArgumentException("The notification's resolver root is no longer open"));
            SFMExplorerPanel panel = (SFMExplorerPanel) runtime.openScene(root);
            int opened = OpenPanelAction.openPanel(target, panel, OpenPanelAction.Direction.RIGHT);
            if (opened == 0) {
                runtime.discardExplorer(panel.explorerId());
                throw new IllegalStateException("Could not open a path Explorer");
            }
            panel.revealPath(root, path).whenComplete((result, error) -> {
                if (error != null) Minecraft.getInstance().execute(() -> {
                    if (workspace.panelIds().stream().anyMatch(panelId -> workspace.panelInstance(panelId) == panel)) {
                        workspace.showWorkspaceToast(Component.literal("Notification path reveal failed: "
                                + error.getMessage()), true);
                    }
                });
            });
            return opened;
        } catch (RuntimeException error) {
            throw failure(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }
    public static List<SFMActionChoice> choices(SFMWorkspaceToastQueue.ToastId toast, List<SFMPath> paths) {
        ArrayList<SFMActionChoice> result = new ArrayList<>();
        for (int index = 0; index < paths.size(); index++) {
            for (Operation operation : Operation.values()) {
                result.add(SFMActionChoice.invoke(operation.id, toast.commandArgument() + " " + index,
                        operation.title + (paths.size() == 1 ? "" : " (" + (index + 1) + ")")));
            }
        }
        return List.copyOf(result);
    }
    private static CommandSyntaxException failure(String message) {
        return new SimpleCommandExceptionType(Component.literal(message)).create();
    }
}
