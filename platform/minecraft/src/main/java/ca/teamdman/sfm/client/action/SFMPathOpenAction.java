package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPath;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPanel;
import ca.teamdman.sfm.client.screen.explorer.SFMExplorerPreviewPlacement;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.screen.workspace.SFMWorkspacePanelId;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.client.symbol.SFMDefinitionNavigation;
import ca.teamdman.sfm.client.symbol.SFMDefinitionResult;
import ca.teamdman.sfm.client.symbol.SFMSymbolReferenceResultRepository;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Opens one already-authorized concrete file through the shared panel/editor seam. */
public final class SFMPathOpenAction implements SFMClientAction<SFMClientActionContext> {
    @Override
    public Component title() {
        return Component.literal("Open addressed path");
    }

    @Override
    public Component description() {
        return Component.literal("Open resolver-authorized text without granting or writing host files");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(
                        SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent()
                );
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        RequiredArgumentBuilder<SFMClientActionSource, String> path = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("concrete_path", SFMCanonicalTokenArgument.token())
                .executes(context -> invokeOpen(context, SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW));
        path.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("preview")
                .executes(context -> invokeOpen(context, SFMExplorerPreviewPlacement.Mode.PREVIEW)));
        path.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("focus")
                .executes(context -> invokeOpen(context, SFMExplorerPreviewPlacement.Mode.FOCUS_PREVIEW)));
        path.then(LiteralArgumentBuilder.<SFMClientActionSource>literal("adjacent")
                .executes(context -> invokeOpen(context, SFMExplorerPreviewPlacement.Mode.ADJACENT)));
        node.then(path);
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal("Provide one concrete resolver path")).create();
    }

    private int invokeOpen(
            CommandContext<SFMClientActionSource> commandContext,
            SFMExplorerPreviewPlacement.Mode mode
    ) throws CommandSyntaxException {
        try {
            SFMPath path = concretePath(SFMCanonicalTokenArgument.get(commandContext, "concrete_path"));
            if (path.scheme().equals(SFMSymbolReferenceResultRepository.SCHEME)) {
                return openReferenceLeaf(commandContext, path);
            }
            if (path.kind() != SFMPath.Kind.FILE) {
                throw new IllegalArgumentException("The first addressed editor slice supports file:// paths only");
            }
            SFMClientActionContext actionContext = commandContext.getSource().context();
            SFMScreenMultiplexer workspace = actionContext.originatingHost() instanceof SFMScreenMultiplexer value
                    ? value : null;
            SFMWorkspacePanelId sourcePanelId = actionContext.originatingPanelId();
            SFMExplorerPanel sourceExplorer = workspace != null && sourcePanelId != null
                    && workspace.panelInstance(sourcePanelId) instanceof SFMExplorerPanel explorer
                    ? explorer : null;
            SFMExplorerRuntime runtime = SFMExplorerRuntime.get();
            SFMPath authorizedRoot = sourceExplorer == null
                    ? runtime.authorizedFilesystemRootFor(path).orElseThrow(() -> new IllegalArgumentException(
                            "Path is outside every explicit explorer root: " + path.canonical()
                    ))
                    : deepestContainingRoot(sourceExplorer.sessionSnapshot(), path).orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Path is outside the captured explorer location: " + path.canonical()
                            ));

            ResourceLocation editorId = SFMTextEditors.V3.getId().orElseThrow().location();
            String title = path.segments().isEmpty()
                    ? path.canonical()
                    : path.segments().get(path.segments().size() - 1);
            SFMTextEditorPanelRecipe recipe = new SFMTextEditorPanelRecipe(
                    new ResourceLocation(SFM.MOD_ID, "text_editor"),
                    editorId,
                    new SFMTextDocumentSource.PathAddress(path, authorizedRoot),
                    true,
                    title
            );
            var panel = recipe.reopen();
            int opened;
            if (workspace != null && sourcePanelId != null && sourceExplorer != null) {
                SFMExplorerPreviewPlacement.Result result = SFMExplorerPreviewPlacement.place(
                        workspace,
                        sourcePanelId,
                        sourceExplorer.explorerId().value(),
                        mode,
                        panel,
                        recipe
                );
                opened = result.applied() ? 1 : 0;
            } else {
                OpenPanelAction.Direction direction = mode == SFMExplorerPreviewPlacement.Mode.ADJACENT
                        ? OpenPanelAction.Direction.RIGHT
                        : OpenPanelAction.Direction.FOCUSED;
                opened = OpenPanelAction.openPanel(actionContext, panel, direction, recipe);
            }
            if (opened == 0) {
                throw new IllegalStateException("The addressed document could not be placed in the panel workspace");
            }
            commandContext.getSource().sendFeedback(Component.literal(
                    "Opening " + path.canonical() + " as " + mode.name().toLowerCase(java.util.Locale.ROOT)
            ));
            return opened;
        } catch (RuntimeException failure) {
            throw new SimpleCommandExceptionType(Component.literal(
                    failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()
            )).create();
        }
    }

    private int openReferenceLeaf(
            CommandContext<SFMClientActionSource> commandContext,
            SFMPath path
    ) throws CommandSyntaxException {
        SFMExplorerRuntime.ReferenceLeafNavigation navigation = SFMExplorerRuntime.get()
                .referenceLeafNavigation(path)
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal(
                        "The reference result is stale or this row is informational"
                )).create());
        SFMClientActionContext actionContext = commandContext.getSource().context();
        if (actionContext.originatingHost() != navigation.workspace()
                || !actionContext.originatingHostIsCurrent().getAsBoolean()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "The workspace that owns this reference result is no longer available"
            )).create();
        }
        SFMWorkspacePanelId invokingPanelId = actionContext.originatingPanelId();
        boolean invokingExplorerOwnsResult = invokingPanelId != null
                && navigation.workspace().containsPanel(invokingPanelId)
                && navigation.workspace().panelInstance(invokingPanelId) instanceof SFMExplorerPanel explorer
                && explorer.sessionSnapshot().roots().contains(referenceRoot(path));
        SFMWorkspacePanelId navigationPanelId = chooseReferenceNavigationPanel(
                navigation.workspace().containsPanel(navigation.sourcePanelId()),
                navigation.sourcePanelId(),
                invokingExplorerOwnsResult,
                invokingPanelId
        ).orElseThrow(() -> new SimpleCommandExceptionType(Component.literal(
                "Neither the originating editor nor this result explorer can host the reference target"
        )).create());
        var leaf = navigation.leaf();
        SFMDefinitionResult.Definition synthetic = new SFMDefinitionResult.Definition(
                leaf.usage().target(),
                leaf.sourceSpan(),
                leaf.sourceSpan(),
                leaf.usage().confidence()
        );
        SFMDefinitionNavigation.Result result = SFMDefinitionNavigation.open(
                navigation.workspace(),
                navigationPanelId,
                leaf.serverHello(),
                synthetic
        );
        commandContext.getSource().sendFeedback(Component.literal(result.message()));
        if (!result.applied()) {
            throw new SimpleCommandExceptionType(Component.literal(result.message())).create();
        }
        return 1;
    }

    static Optional<SFMWorkspacePanelId> chooseReferenceNavigationPanel(
            boolean sourcePanelAvailable,
            SFMWorkspacePanelId sourcePanelId,
            boolean invokingExplorerOwnsResult,
            SFMWorkspacePanelId invokingPanelId
    ) {
        Objects.requireNonNull(sourcePanelId, "sourcePanelId");
        if (sourcePanelAvailable) return Optional.of(sourcePanelId);
        if (invokingExplorerOwnsResult && invokingPanelId != null) return Optional.of(invokingPanelId);
        return Optional.empty();
    }

    private static SFMPath referenceRoot(SFMPath path) {
        return SFMSymbolReferenceResultRepository.rootPath(
                new SFMSymbolReferenceResultRepository.ResultId(path.authority())
        );
    }

    private static SFMPath concretePath(String canonical) {
        SFMPathExpression expression = SFMPathExpression.parse(canonical);
        if (!(expression instanceof SFMPathExpression.Literal literal)) {
            throw new IllegalArgumentException("Path opening requires one concrete path, not a set expression");
        }
        return literal.path();
    }

    private static Optional<SFMPath> deepestContainingRoot(
            ca.teamdman.sfm.client.explorer.lazy.SFMExplorerSession.Snapshot snapshot,
            SFMPath path
    ) {
        Path nativePath = path.toNativePath().toAbsolutePath().normalize();
        return snapshot.roots().stream()
                .filter(root -> root.kind() == SFMPath.Kind.FILE)
                .filter(root -> nativePath.startsWith(root.toNativePath().toAbsolutePath().normalize()))
                .max(Comparator.comparingInt(root -> root.toNativePath().getNameCount()));
    }

}
