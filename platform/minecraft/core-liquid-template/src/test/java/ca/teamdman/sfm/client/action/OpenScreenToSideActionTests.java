package ca.teamdman.sfm.client.action;

{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import ca.teamdman.sfm.client.screen.workspace.SFMTestScreenType;
import com.mojang.brigadier.ParseResults;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenScreenToSideActionTests {
{% case minecraft_version %}
{% when "26.1.2" %}
    private static final Identifier ACTION_ID = SFMResourceLocation.fromNamespaceAndPath("sfm", "workspace/open_to_side");
{% when "1.21", "1.21.1" %}
    private static final ResourceLocation ACTION_ID = SFMResourceLocation.fromNamespaceAndPath("sfm", "workspace/open_to_side");
{% else %}
    private static final ResourceLocation ACTION_ID = new ResourceLocation("sfm", "workspace/open_to_side");
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
    private static final Identifier SCREEN_ID = SFMResourceLocation.fromNamespaceAndPath("sfm", "test_screen");
{% when "1.21", "1.21.1" %}
    private static final ResourceLocation SCREEN_ID = SFMResourceLocation.fromNamespaceAndPath("sfm", "test_screen");
{% else %}
    private static final ResourceLocation SCREEN_ID = new ResourceLocation("sfm", "test_screen");
{% endcase %}

    @Test
    void registeredScreenTypeContributesItsTypedArgumentsToTheActionTree() {
        OpenScreenToSideAction action = new OpenScreenToSideAction(() -> List.of(
                Map.entry(SCREEN_ID, new SFMTestScreenType())
        ));
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        ParseResults<SFMClientActionSource> incomplete = tree.parse(
                "sfm action invoke sfm:workspace/open_to_side sfm:test_screen ",
                source
        );
        ParseResults<SFMClientActionSource> complete = tree.parse(
                "sfm action invoke sfm:workspace/open_to_side sfm:test_screen test screen 1",
                source
        );

        assertFalse(isExecutable(incomplete));
        assertTrue(isExecutable(complete));
    }

    @Test
    void registeredScreenTypeIsSuggestedByBrigadier() throws Exception {
        OpenScreenToSideAction action = new OpenScreenToSideAction(() -> List.of(
                Map.entry(SCREEN_ID, new SFMTestScreenType())
        ));
        SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(List.of(
                Map.entry(ACTION_ID, action)
        ));
        SFMClientActionSource source = new SFMClientActionSource(SFMClientActionContext.create(null, () -> true));

        var suggestions = tree.getCompletionSuggestions(tree.parse(
                "sfm action invoke sfm:workspace/open_to_side ",
                source
        )).get();

        assertTrue(suggestions.getList().stream().anyMatch(suggestion -> suggestion.getText().equals(SCREEN_ID.toString())));
    }

    private static boolean isExecutable(ParseResults<SFMClientActionSource> parsed) {
        assertNotNull(parsed);
        return !parsed.getReader().canRead()
                && parsed.getExceptions().isEmpty()
                && parsed.getContext().getCommand() != null;
    }
}
