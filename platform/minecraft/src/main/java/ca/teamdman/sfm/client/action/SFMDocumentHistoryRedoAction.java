package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.document.SFMDocumentHistoryContract;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** Moves an explicitly selected document head to one retained child revision. */
public final class SFMDocumentHistoryRedoAction implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "document/history/redo");
    private final SFMDocumentHistoryRuntime runtime;

    public SFMDocumentHistoryRedoAction() {
        this(SFMDocumentHistoryRuntime.get());
    }

    SFMDocumentHistoryRedoAction(SFMDocumentHistoryRuntime runtime) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public Component title() {
        return Component.literal("Redo document history");
    }

    @Override
    public Component description() {
        return Component.literal("Move a selected document head to one retained child revision");
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
        RequiredArgumentBuilder<SFMClientActionSource, String> selector =
                SFMDocumentHistoryUndoAction.documentSelector(runtime);
        selector.executes(this::invokeSelected);
        selector.then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "child_revision_id",
                        SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    resolve(context).flatMap(SFMDocumentHistoryActionSupport.Target::session)
                            .stream()
                            .flatMap(session -> session.eligibleRedoChildren().stream())
                            .map(SFMDocumentHistoryContract.DocumentRevision::id)
                            .sorted()
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(this::invokeSelected));
        node.then(selector);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide a document selector such as focused")).create();
    }

    private int invokeSelected(CommandContext<SFMClientActionSource> context) throws CommandSyntaxException {
        SFMDocumentHistoryActionSupport.Target target = resolve(context).orElseThrow(() ->
                new SimpleCommandExceptionType(Component.literal("No matching document history")).create());
        Optional<String> child = optionalChild(context);
        if (child.isEmpty() && target.session().isPresent()) {
            List<String> candidates = target.session().orElseThrow().eligibleRedoChildren().stream()
                    .map(SFMDocumentHistoryContract.DocumentRevision::id)
                    .sorted()
                    .toList();
            if (candidates.size() > 1) {
                String selector = SFMDocumentHistorySelector.exact(target.sessionId()).canonical();
                List<SFMActionChoice> choices = candidates.stream()
                        .map(revision -> SFMActionChoice.invoke(ID, selector + " " + revision))
                        .toList();
                SFMCommandPaletteScreen.openChoices(
                        context.getSource().context(),
                        Component.literal("Choose retained redo branch"),
                        choices);
                context.getSource().sendFeedback(Component.literal(
                        "Choose one of " + candidates.size() + " retained redo branches"));
                return 1;
            }
        }
        SFMHistoryGraphRuntime.OperationResult result = target.target()
                .redoDocumentHistory(child.orElse(""));
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.status() == SFMHistoryGraphRuntime.OperationStatus.APPLIED
                ? PanelActionSupport.closePaletteAfter(1)
                : 0;
    }

    private Optional<SFMDocumentHistoryActionSupport.Target> resolve(
            CommandContext<SFMClientActionSource> context
    ) {
        SFMDocumentHistorySelector selector = SFMDocumentHistorySelector.parseCanonical(
                SFMCanonicalTokenArgument.get(context, "document_selector"));
        return SFMDocumentHistoryActionSupport.resolve(context.getSource().context(), runtime, selector);
    }

    private static Optional<String> optionalChild(CommandContext<SFMClientActionSource> context) {
        try {
            return Optional.of(SFMCanonicalTokenArgument.get(context, "child_revision_id"));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
