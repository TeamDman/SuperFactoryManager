package ca.teamdman.sfm.client.keybinding;

import ca.teamdman.sfm.client.action.EchoAction;
import ca.teamdman.sfm.client.action.SFMClientAction;
import ca.teamdman.sfm.client.action.SFMClientActionCommandTree;
import ca.teamdman.sfm.client.action.SFMClientActionContext;
import ca.teamdman.sfm.client.action.SFMClientActionDispatcherCompiler;
import ca.teamdman.sfm.client.action.SFMClientActionSource;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SFMCommandDraftAnalysisTests {
    private final SFMClientActionCommandTree tree = SFMClientActionDispatcherCompiler.compileCommandTree(
            List.<Map.Entry<ResourceLocation, SFMClientAction<?>>>of(
                    Map.entry(new ResourceLocation("sfm", "echo"), new EchoAction())
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
