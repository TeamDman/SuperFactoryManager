package ca.teamdman.sfm.client.action;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.screen.SFMActionChoice;
import ca.teamdman.sfm.client.screen.SFMCommandPaletteScreen;
import ca.teamdman.sfm.client.screen.workspace.SFMScreenMultiplexer;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentLanguage;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.ISFMTextEditorRegistration;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import ca.teamdman.sfm.common.config.SFMClientTextEditorConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Action-backed inspection for generated split-diff headers, status, and diagnostics. */
public final class SFMReleaseReviewSurfaceDetailsAction
        implements SFMClientAction<SFMClientActionContext> {
    public static final ResourceLocation ID = new ResourceLocation(SFM.MOD_ID, "review/surface/details");

    public enum Operation { COPY, OPEN }

    public static List<SFMActionChoice> choices(String title, String payload) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(payload, "payload");
        String argument = StringArgumentType.escapeIfRequired(payload);
        return List.of(
                SFMActionChoice.invoke(ID, "copy " + argument, "Copy " + title + " details"),
                SFMActionChoice.invoke(ID, "open " + argument, "Open " + title + " details as text")
        );
    }

    @Override
    public Component title() {
        return Component.literal("Inspect generated review surface");
    }

    @Override
    public Component description() {
        return Component.literal("Copy or open the exact metadata behind a generated diff UI region");
    }

    @Override
    public SFMClientActionRequirement<SFMClientActionContext> requirement() {
        return context -> context.originatingHostIsCurrent().getAsBoolean()
                && context.originatingHost() instanceof SFMScreenMultiplexer
                ? SFMClientActionAvailability.available(context)
                : SFMClientActionAvailability.unavailable(Component.literal(
                        "Open generated review details from an active SFM workspace"));
    }

    @Override
    public void configureCommandNode(LiteralArgumentBuilder<SFMClientActionSource> node) {
        for (Operation operation : Operation.values()) {
            node.then(LiteralArgumentBuilder.<SFMClientActionSource>literal(operation.name().toLowerCase())
                    .then(RequiredArgumentBuilder.<SFMClientActionSource, String>argument(
                                    "details", StringArgumentType.string())
                            .executes(context -> execute(operation, context))));
        }
    }

    @Override
    public int execute(SFMClientActionContext target, CommandContext<SFMClientActionSource> context)
            throws CommandSyntaxException {
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Component.literal("Choose whether to copy or open the generated review details")).create();
    }

    private int execute(Operation operation, CommandContext<SFMClientActionSource> context) {
        String details = StringArgumentType.getString(context, "details");
        if (operation == Operation.COPY) {
            Minecraft.getInstance().keyboardHandler.setClipboard(details);
            context.getSource().sendFeedback(Component.literal("Copied generated review details"));
            return 1;
        }
        SFMClientActionContext target = context.getSource().context();
        if (Minecraft.getInstance().screen instanceof SFMCommandPaletteScreen palette) palette.onClose();
        ResourceLocation editorId = preferredEditorId();
        var recipe = new SFMTextEditorPanelRecipe(
                new ResourceLocation(SFM.MOD_ID, "text_editor"),
                editorId,
                new SFMTextDocumentSource.Literal(details, SFMTextDocumentLanguage.plainText()),
                true,
                "Generated review details"
        );
        return OpenPanelAction.openPanel(target, recipe.reopen(), OpenPanelAction.Direction.FOCUSED, recipe);
    }

    private static ResourceLocation preferredEditorId() {
        ISFMTextEditorRegistration preferred = SFMClientTextEditorConfig.getPreferredTextEditor();
        ResourceLocation configured = SFMTextEditors.registry().getId(preferred);
        return configured == null
                ? SFMTextEditors.V3.getId().orElseThrow().location()
                : configured;
    }
}
