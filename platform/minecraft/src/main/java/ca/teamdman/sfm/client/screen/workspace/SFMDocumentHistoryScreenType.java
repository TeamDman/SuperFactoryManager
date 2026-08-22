package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMCanonicalTokenArgument;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.history.SFMDocumentHistoryHost;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistoryRuntime;
import ca.teamdman.sfm.client.history.document.runtime.SFMDocumentHistorySelector;
import ca.teamdman.sfm.client.screen.history.document.SFMDocumentHistoryPanel;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Typed ordinary-panel scene for a focused or exact document-history session. */
public final class SFMDocumentHistoryScreenType implements SFMClientScreenType {
    private final SFMDocumentHistoryRuntime runtime;

    public SFMDocumentHistoryScreenType() {
        this(SFMDocumentHistoryRuntime.get());
    }

    SFMDocumentHistoryScreenType(SFMDocumentHistoryRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(screenTypeId.toString());
        RequiredArgumentBuilder<SFMClientActionSource, String> selector = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(
                        "document_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    runtime.sessionIds().stream()
                            .map(SFMDocumentHistorySelector::exact)
                            .map(SFMDocumentHistorySelector::canonical)
                            .forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> open(
                        context,
                        opener,
                        screenTypeId,
                        SFMDocumentHistorySelector.parseCanonical(
                                SFMCanonicalTokenArgument.get(context, "document_selector")
                        )
                ));
        return node.then(selector);
    }

    private int open(
            CommandContext<SFMClientActionSource> context,
            Opener opener,
            ResourceLocation screenTypeId,
            SFMDocumentHistorySelector requested
    ) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        SFMDocumentHistorySelector frozen = freeze(
                runtime,
                context.getSource().context(),
                requested
        );
        return opener.open(context, new Recipe(screenTypeId, frozen.canonical()));
    }

    static SFMDocumentHistorySelector freeze(
            SFMDocumentHistoryRuntime runtime,
            ca.teamdman.sfm.client.action.SFMClientActionContext context,
            SFMDocumentHistorySelector requested
    ) {
        Objects.requireNonNull(context, "context");
        if (requested.kind() == SFMDocumentHistorySelector.Kind.EXACT) return requested;
        Object host = context.originatingHost();
        if (host instanceof SFMDocumentHistoryHost direct) {
            return direct.documentHistoryAvailable()
                    ? SFMDocumentHistorySelector.exact(direct.documentHistorySessionId())
                    : requested;
        }
        if (host instanceof SFMScreenMultiplexer workspace
                && workspace.focusedPanelInstance() instanceof SFMDocumentHistoryHost focused) {
            return focused.documentHistoryAvailable()
                    ? SFMDocumentHistorySelector.exact(focused.documentHistorySessionId())
                    : requested;
        }
        return freeze(runtime, requested);
    }

    static SFMDocumentHistorySelector freeze(
            SFMDocumentHistoryRuntime runtime,
            SFMDocumentHistorySelector requested
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(requested, "requested");
        if (requested.kind() == SFMDocumentHistorySelector.Kind.EXACT) return requested;
        return runtime.focused()
                .map(entry -> SFMDocumentHistorySelector.exact(entry.sessionId()))
                .orElse(requested);
    }

    public record Recipe(ResourceLocation sceneTypeId, String documentSelector)
            implements SFMPanelReopenRecipe {
        public Recipe {
            Objects.requireNonNull(sceneTypeId, "sceneTypeId");
            documentSelector = SFMDocumentHistorySelector.parseCanonical(
                    Objects.requireNonNull(documentSelector, "documentSelector")
            ).canonical();
        }

        @Override
        public SFMDocumentHistoryPanel reopen() {
            return new SFMDocumentHistoryPanel(
                    SFMDocumentHistorySelector.parseCanonical(documentSelector)
            );
        }
    }
}
