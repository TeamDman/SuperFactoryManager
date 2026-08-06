package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Typed panel scene for the configured or explicitly selected text editor. */
public final class SFMTextEditorScreenType implements SFMClientScreenType {
    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        LiteralArgumentBuilder<SFMClientActionSource> node = LiteralArgumentBuilder
                .<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> open(context, opener, screenTypeId, defaultEditorId()));
        RequiredArgumentBuilder<SFMClientActionSource, String> editorId = RequiredArgumentBuilder
                // Resource locations contain a namespace colon, which is not
                // legal in Brigadier's unquoted word/string reader. The scene
                // consumes the remainder as one typed id and validates it via
                // ResourceLocation below.
                .<SFMClientActionSource, String>argument("editor_id", StringArgumentType.greedyString())
                .suggests((context, suggestions) -> {
                    SFMTextEditors.registry().keys().stream()
                            .map(ResourceLocation::toString)
                            .sorted()
                            .forEach(suggestions::suggest);
                    return suggestions.buildFuture();
                })
                .executes(context -> open(context, opener, screenTypeId,
                        new ResourceLocation(StringArgumentType.getString(context, "editor_id"))));
        return node.then(editorId);
    }

    private int open(
            CommandContext<SFMClientActionSource> context,
            Opener opener,
            ResourceLocation screenTypeId,
            ResourceLocation editorId
    ) throws CommandSyntaxException {
        ISFMTextEditorRegistration registration = SFMTextEditors.registry().get(editorId);
        if (registration == null) {
            throw new SimpleCommandExceptionType(Component.literal("Unknown text editor: " + editorId)).create();
        }
        return opener.open(context, new SFMTextEditorPanelRecipe(
                screenTypeId,
                editorId,
                new SFMTextDocumentSource.Literal(""),
                false,
                "Text Editor v3"
        ));
    }

    private ResourceLocation defaultEditorId() {
        ISFMTextEditorRegistration registration = SFMClientTextEditorConfig.getPreferredTextEditor();
        ResourceLocation configured = SFMTextEditors.registry().getId(registration);
        if (configured != null) return configured;
        return SFMTextEditors.V3.getId().orElseThrow().location();
    }
}
