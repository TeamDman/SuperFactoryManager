package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.registry.SFMTextEditors;
import ca.teamdman.sfm.client.text_editor.SFMTextDocumentSource;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelRecipe;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.resources.ResourceLocation;

/** Read-only SFML grammar source opened through the shared editor panel. */
public final class SFMGrammarScreenType implements SFMClientScreenType {
    private static final ResourceLocation RESOURCE = new ResourceLocation(SFM.MOD_ID, "grammar/sfml/sfml.g4");

    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(context, new SFMTextEditorPanelRecipe(
                        screenTypeId,
                        SFMTextEditors.V3.getId().orElseThrow().location(),
                        new SFMTextDocumentSource.ResourceAddress(RESOURCE),
                        true,
                        "Grammar · SFML.g4"
                )));
    }
}
