package ca.teamdman.sfm.client.screen.workspace;

import ca.teamdman.sfm.SFM;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import ca.teamdman.sfm.client.screen.text_editor.SFMTextEditorPanel;
import ca.teamdman.sfm.client.text_editor.SFMTextEditorPanelOpenContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only SFML grammar source opened through the shared editor panel. */
public final class SFMGrammarScreenType implements SFMClientScreenType {
    private static final ResourceLocation RESOURCE = new ResourceLocation(SFM.MOD_ID, "grammar/sfml/sfml.g4");

    @Override
    public LiteralArgumentBuilder<SFMClientActionSource> createCommandNode(
            ResourceLocation screenTypeId,
            Opener opener
    ) {
        return LiteralArgumentBuilder.<SFMClientActionSource>literal(screenTypeId.toString())
                .executes(context -> opener.open(context, SFMTextEditorPanel.textEditorV3(
                        new SFMTextEditorPanelOpenContext(
                                "sfm:text_editor_v3", readGrammar(), true, "Grammar · SFML.g4"
                        ))));
    }

    private static String readGrammar() {
        Map<ResourceLocation, Resource> resources = Minecraft.getInstance().getResourceManager()
                .listResources("grammar/sfml", location -> location.equals(RESOURCE));
        Resource resource = resources.get(RESOURCE);
        if (resource == null) return "// Missing runtime grammar resource: " + RESOURCE;
        try (BufferedReader reader = resource.openAsReader()) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException exception) {
            return "// Failed to read runtime grammar resource: " + RESOURCE + "\n// " + exception.getMessage();
        }
    }
}
