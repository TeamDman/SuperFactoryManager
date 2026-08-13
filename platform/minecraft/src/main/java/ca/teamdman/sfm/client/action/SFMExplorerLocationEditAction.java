package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.explorer.SFMEntitySelector;
import ca.teamdman.sfm.client.explorer.SFMEntitySelectorResolver;
import ca.teamdman.sfm.client.explorer.SFMExplorerId;
import ca.teamdman.sfm.client.explorer.SFMExplorerRuntime;
import ca.teamdman.sfm.client.explorer.SFMPathExpression;
import ca.teamdman.sfm.client.explorer.SFMSelectorDomains;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionRequest;
import ca.teamdman.sfm.client.explorer.action.SFMExplorerActionResult;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveHandler;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSaveResult;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/** Opens the exact canonical location of selected explorers as editable documents. */
public final class SFMExplorerLocationEditAction implements SFMClientAction<SFMClientActionContext> {
    private static final ResourceLocation SCENE_ID = new ResourceLocation(SFM.MOD_ID, "text_editor");

    @Override
    public Component title() {
        return Component.literal("Edit explorer location");
    }

    @Override
    public Component description() {
        return Component.literal("Open an explorer's canonical path expression in a text editor panel");
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
        RequiredArgumentBuilder<SFMClientActionSource, String> selector = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument(
                        "explorer_selector",
                        SFMCanonicalTokenArgument.token()
                )
                .suggests((context, builder) -> {
                    builder.suggest("focused");
                    builder.suggest("all");
                    SFMExplorerRuntime.get().repository().stateSnapshot().explorers().keySet().forEach(id ->
                            builder.suggest(exact(id).canonical())
                    );
                    return builder.buildFuture();
                });
        selector.executes(context -> open(
                context,
                OpenPanelAction.Direction.RIGHT,
                preferredEditorId()
        ));
        for (OpenPanelAction.Direction direction : OpenPanelAction.Direction.values()) {
            String literal = direction.name().toLowerCase(Locale.ROOT);
            LiteralArgumentBuilder<SFMClientActionSource> placement = LiteralArgumentBuilder
                    .<SFMClientActionSource>literal(literal)
                    .executes(context -> open(context, direction, preferredEditorId()));
            placement.then(RequiredArgumentBuilder
                    .<SFMClientActionSource, String>argument(
                            "editor_id",
                            SFMCanonicalTokenArgument.token()
                    )
                    .suggests((context, builder) -> {
                        SFMTextEditors.registry().keys().stream()
                                .map(ResourceLocation::toString)
                                .sorted()
                                .forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(context -> open(
                            context,
                            direction,
                            parseEditorId(SFMCanonicalTokenArgument.get(context, "editor_id"))
                    )));
            selector.then(placement);
        }
        node.then(selector);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        throw new SimpleCommandExceptionType(Component.literal(
                "Provide an explorer selector and optional placement/editor id"
        )).create();
    }

    private int open(
            CommandContext<SFMClientActionSource> context,
            OpenPanelAction.Direction direction,
            ResourceLocation editorId
    ) throws CommandSyntaxException {
        ISFMTextEditorRegistration registration = SFMTextEditors.registry().get(editorId);
        if (registration == null) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Unknown text editor: " + editorId
            )).create();
        }
        SFMEntitySelector selector = SFMEntitySelector.parseCanonical(
                SFMEntitySelector.Domain.EXPLORER,
                SFMCanonicalTokenArgument.get(context, "explorer_selector")
        );
        var runtime = SFMExplorerRuntime.get();
        var resolution = SFMEntitySelectorResolver.resolve(
                selector,
                SFMSelectorDomains.explorers(runtime.repository())
        );
        if (!resolution.complete() || resolution.identities().isEmpty()) {
            String detail = resolution.diagnostics().isEmpty()
                    ? "No explorer matched " + selector.canonical()
                    : resolution.diagnostics().get(0).message();
            throw new SimpleCommandExceptionType(Component.literal(detail)).create();
        }

        int opened = 0;
        for (SFMExplorerId id : resolution.identities()) {
            var explorer = runtime.repository().find(id).orElseThrow();
            var snapshot = explorer.session().snapshot();
            SFMTextEditorPanelRecipe recipe = new SFMTextEditorPanelRecipe(
                    SCENE_ID,
                    editorId,
                    new SFMTextDocumentSource.Literal(locationDocumentText(snapshot.location())),
                    false,
                    "Explorer Location · " + id.value(),
                    () -> locationSaveHandler(runtime, id, snapshot.revision())
            );
            opened += OpenPanelAction.openPanel(
                    context.getSource().context(),
                    recipe.reopen(),
                    direction,
                    recipe
            );
        }
        return opened;
    }

    private static SFMTextDocumentSaveResult save(
            SFMExplorerRuntime runtime,
            SFMExplorerId explorerId,
            java.util.concurrent.atomic.AtomicLong expectedRevision,
            String content
    ) {
        try {
            SFMPathExpression expression = SFMPathExpression.parse(content);
            if (!expression.canonical().equals(content)) {
                return SFMTextDocumentSaveResult.rejected(Component.literal(
                        "Use the canonical location spelling: " + expression.canonical()
                ));
            }
            SFMExplorerActionResult result = runtime.execute(new SFMExplorerActionRequest(
                    exact(explorerId),
                    new SFMExplorerActionRequest.LocationSet(expression, expectedRevision.get()),
                    SFMExplorerActionRequest.IfNoMatch.FAIL
            ));
            if (result.status() != SFMExplorerActionResult.Status.SUCCEEDED) {
                String detail = result.diagnostics().isEmpty()
                        ? result.status().name().toLowerCase(Locale.ROOT)
                        : result.diagnostics().get(0);
                return SFMTextDocumentSaveResult.rejected(Component.literal(detail));
            }
            SFMExplorerActionResult.TargetResult target = result.targets().stream()
                    .filter(candidate -> candidate.explorerId().equals(explorerId))
                    .findFirst()
                    .orElseThrow();
            expectedRevision.set(target.after().sessionRevision());
            return SFMTextDocumentSaveResult.success();
        } catch (RuntimeException failure) {
            String detail = failure.getMessage() == null
                    ? failure.getClass().getSimpleName()
                    : failure.getMessage();
            return SFMTextDocumentSaveResult.rejected(Component.literal(detail));
        }
    }

    private static SFMTextDocumentSaveHandler locationSaveHandler(
            SFMExplorerRuntime runtime,
            SFMExplorerId explorerId,
            long expectedRevision
    ) {
        java.util.concurrent.atomic.AtomicLong revision =
                new java.util.concurrent.atomic.AtomicLong(expectedRevision);
        return content -> save(runtime, explorerId, revision, content);
    }

    static String locationDocumentText(SFMPathExpression expression) {
        return expression.canonical();
    }

    private static SFMEntitySelector exact(SFMExplorerId id) {
        return SFMEntitySelector.exact(SFMEntitySelector.Domain.EXPLORER, id.value());
    }

    private static ResourceLocation parseEditorId(String text) throws CommandSyntaxException {
        ResourceLocation answer = ResourceLocation.tryParse(text);
        if (answer == null) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Invalid text editor id: " + text
            )).create();
        }
        return answer;
    }

    private static ResourceLocation preferredEditorId() {
        ResourceLocation configured = SFMTextEditors.registry().getId(
                SFMClientTextEditorConfig.getPreferredTextEditor()
        );
        return configured == null
                ? SFMTextEditors.V3.getId().orElseThrow().location()
                : configured;
    }
}
