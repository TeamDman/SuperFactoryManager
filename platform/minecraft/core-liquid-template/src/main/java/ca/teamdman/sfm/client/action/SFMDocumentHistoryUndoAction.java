package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.client.history.SFMHistoryGraphRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

/** Moves an explicitly selected document head to its retained parent revision. */
public final class SFMDocumentHistoryUndoAction implements SFMClientAction<SFMClientActionContext> {
    private final SFMDocumentHistoryRuntime runtime;

    public SFMDocumentHistoryUndoAction() {
        this(SFMDocumentHistoryRuntime.get());
    }

    SFMDocumentHistoryUndoAction(SFMDocumentHistoryRuntime runtime) {
        this.runtime = java.util.Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public Component title() {
        return Component.literal("Undo document history");
    }

    @Override
    public Component description() {
        return Component.literal("Move a selected document head backward without deleting alternate histories");
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
        node.then(documentSelector(runtime).executes(this::invokeSelected));
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
        SFMDocumentHistorySelector selector = SFMDocumentHistorySelector.parseCanonical(
                SFMCanonicalTokenArgument.get(context, "document_selector"));
        SFMDocumentHistoryActionSupport.Target target = SFMDocumentHistoryActionSupport.resolve(
                context.getSource().context(), runtime, selector).orElseThrow(() ->
                new SimpleCommandExceptionType(Component.literal(
                        "No document history matches " + selector.canonical())).create());
        SFMHistoryGraphRuntime.OperationResult result = target.target().undoDocumentHistory();
        context.getSource().sendFeedback(Component.literal(result.message()));
        return result.status() == SFMHistoryGraphRuntime.OperationStatus.APPLIED
                ? PanelActionSupport.closePaletteAfterUnlessTarget(
                        1, target.target(), target.sessionId())
                : 0;
    }

    static RequiredArgumentBuilder<SFMClientActionSource, String> documentSelector(
            SFMDocumentHistoryRuntime runtime
    ) {
        return RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                        "document_selector",
                        SFMCanonicalTokenArgument.token())
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    runtime.sessionIds().stream()
                            .map(SFMDocumentHistorySelector::exact)
                            .map(SFMDocumentHistorySelector::canonical)
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }
}
