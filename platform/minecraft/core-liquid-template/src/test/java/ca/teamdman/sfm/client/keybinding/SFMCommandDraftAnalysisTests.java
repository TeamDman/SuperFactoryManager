package ca.teamdman.sfm.client.keybinding;

{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
import ca.teamdman.sfm.common.util.SFMResourceLocation;

{% endcase %}
import ca.teamdman.sfm.client.action.EchoAction;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionDispatcherCompiler;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
{% case minecraft_version %}
{% when "26.1.2" %}
import net.minecraft.resources.Identifier;
{% else %}
import net.minecraft.resources.ResourceLocation;
{% endcase %}
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCommandDraftAnalysisTests {
    private final SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(
{% case minecraft_version %}
{% when "26.1.2" %}
            List.<Map.Entry<Identifier, SFMClientAction<?>>>of(
{% else %}
            List.<Map.Entry<ResourceLocation, SFMClientAction<?>>>of(
{% endcase %}
{% case minecraft_version %}
{% when "1.21", "1.21.1", "26.1.2" %}
                    Map.entry(SFMResourceLocation.fromNamespaceAndPath("sfm", "echo"), new EchoAction())
{% else %}
                    Map.entry(new ResourceLocation("sfm", "echo"), new EchoAction())
{% endcase %}
            )
    );
    private final SFMClientActionSource source = new SFMClientActionSource(
            SFMClientActionContext.create(new Object(), () -> true)
    );

    @Test
    void incompleteEchoExposesTypedMissingParameter() {
        SFMCommandDraftAnalysis analysis = analyze("/sfm action invoke sfm:echo");

        assertEquals(SFMCommandDraftAnalysis.State.INCOMPLETE, analysis.state(), analysis::toString);
        assertEquals("sfm action invoke sfm:echo ", analysis.preparedCommand());
        assertNotNull(analysis.missingParameter());
        assertEquals("message", analysis.missingParameter().name());
        assertEquals("string", analysis.missingParameter().displayType());
    }

    @Test
    void suppliedArgumentIsCompleteAndInvalidSuffixIsDiagnosed() {
        assertEquals(SFMCommandDraftAnalysis.State.COMPLETE,
                analyze("sfm action invoke sfm:echo hello world").state());
        SFMCommandDraftAnalysis invalid = analyze("sfm action invoke sfm:echo-value");
        assertEquals(SFMCommandDraftAnalysis.State.INVALID, invalid.state());
        assertTrue(!invalid.diagnostic().isBlank());
    }

    private SFMCommandDraftAnalysis analyze(String command) {
        return SFMCommandDraftAnalysis.analyze(command, tree, source);
    }
}
