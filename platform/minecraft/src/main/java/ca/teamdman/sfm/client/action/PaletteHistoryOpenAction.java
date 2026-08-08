package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.command.SFMCommandHistoryService;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Opens a point-in-time, read-only command-history document in a panel. */
public final class PaletteHistoryOpenAction implements SFMClientAction<SFMClientActionContext> {
    public enum Direction {
        CENTER,
        LEFT,
        RIGHT,
        ABOVE,
        BELOW
    }

    private static final ResourceLocation SCENE_ID = new ResourceLocation(SFM.MOD_ID, "text_editor");
    private final Direction direction;

    public PaletteHistoryOpenAction(Direction direction) {
        this.direction = direction;
    }

    @Override
    public Component title() {
        return Component.literal(direction == Direction.CENTER
                ? "Open command history"
                : "Open command history " + direction.name().toLowerCase());
    }

    @Override
    public Component description() {
        return Component.literal("Open a read-only snapshot of successful palette commands");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> SFMCommandHistoryService.isPersistenceEnabled()
                && context.originatingHostIsCurrent().getAsBoolean()
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(Component.literal(
                        SFMCommandHistoryService.isPersistenceEnabled()
                                ? SFMClientActionContext.ORIGINATING_HOST_CHANGED.getComponent().getString()
                                : "Command history is disabled"));
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        node.executes(context -> open(context, defaultEditorId()));
        RequiredArgumentBuilder<SFMClientActionSource, String> editorId = RequiredArgumentBuilder
                .<SFMClientActionSource, String>argument("editor_id", StringArgumentType.greedyString())
                .suggests((context, suggestions) -> {
                    SFMTextEditors.registry().keys().stream()
                            .filter(id -> {
                                ISFMTextEditorRegistration registration = SFMTextEditors.registry().get(id);
                                return registration != null && registration.supportsReadOnlyPanel();
                            })
                            .map(ResourceLocation::toString)
                            .sorted()
                            .forEach(suggestions::suggest);
                    return suggestions.buildFuture();
                })
                .executes(context -> open(context, parseEditorId(context)));
        node.then(editorId);
    }

    @Override
    public int execute(
            SFMClientActionContext target,
            CommandContext<SFMClientActionSource> context
    ) throws CommandSyntaxException {
        return open(context, defaultEditorId());
    }

    private int open(CommandContext<SFMClientActionSource> context, ResourceLocation editorId)
            throws CommandSyntaxException {
        if (!SFMCommandHistoryService.isPersistenceEnabled()) {
            throw new SimpleCommandExceptionType(Component.literal("Command history is disabled")).create();
        }
        ISFMTextEditorRegistration registration = SFMTextEditors.registry().get(editorId);
        if (registration == null) {
            throw new SimpleCommandExceptionType(Component.literal("Unknown text editor: " + editorId)).create();
        }
        if (!registration.supportsReadOnlyPanel()) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Text editor does not support read-only history: " + editorId)).create();
        }
        var recipe = new SFMTextEditorPanelRecipe(
                SCENE_ID,
                editorId,
                new SFMTextDocumentSource.Literal(SFMCommandHistoryService.documentText()),
                true,
                "Command History"
        );
        return OpenPanelAction.openPanel(
                context.getSource().context(),
                recipe.reopen(),
                switch (direction) {
                    case CENTER -> OpenPanelAction.Direction.FOCUSED;
                    case LEFT -> OpenPanelAction.Direction.LEFT;
                    case RIGHT -> OpenPanelAction.Direction.RIGHT;
                    case ABOVE -> OpenPanelAction.Direction.ABOVE;
                    case BELOW -> OpenPanelAction.Direction.BELOW;
                },
                recipe);
    }

    private static ResourceLocation parseEditorId(CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        String raw = StringArgumentType.getString(context, "editor_id").strip();
        ResourceLocation editorId = ResourceLocation.tryParse(raw);
        if (editorId == null) {
            throw new SimpleCommandExceptionType(Component.literal("Invalid text editor id: " + raw)).create();
        }
        return editorId;
    }

    private static ResourceLocation defaultEditorId() {
        var preferred = SFMClientTextEditorConfig.getPreferredTextEditor();
        ResourceLocation configured = SFMTextEditors.registry().getId(preferred);
        ISFMTextEditorRegistration registration = configured == null
                ? null : SFMTextEditors.registry().get(configured);
        return registration != null && registration.supportsReadOnlyPanel()
                ? configured
                : SFMTextEditors.V3.getId().orElseThrow().location();
    }
}
