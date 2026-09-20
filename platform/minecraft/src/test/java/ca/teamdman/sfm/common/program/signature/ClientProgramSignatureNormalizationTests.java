package ca.teamdman.sfm.common.program.signature;

import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientProgramSignatureNormalizationTests {
    private static ProgramSignatureDescriptor descriptor(String source) {
        return ProgramSignatureDescriptor.fromSource(source, "sfm:client_manager@1",
                List.of(new ResourceLocation("sfm:client_program/execute")));
    }

    private static String parsedName(String source) {
        var result = new ProgramBuilder(source).forExecutionSide(ProgramExecutionSide.CLIENT).build();
        assertTrue(result.isBuildSuccessful(), () -> result.metadata().errors().toString());
        return result.program().name();
    }

    @Test
    void lineEndingEquivalenceRequiresTheRuntimeToExecuteTheNormalizedStoredSource() {
        String lf = "CLIENT BTW\nNAME \"first\nsecond\"\n";
        String crlf = lf.replace("\n", "\r\n");
        String cr = lf.replace('\n', '\r');
        assertEquals(descriptor(lf), descriptor(crlf));
        assertEquals(descriptor(lf), descriptor(cr));
        // SFML preserves literal string contents. A signature's normalized hash alone is NOT
        // permission to execute an unnormalized body. Authoring acknowledgement and client runtime
        // must share normalized stored source before signer trust can become an authority source.
        assertNotEquals(parsedName(lf), parsedName(crlf));
        assertNotEquals(parsedName(lf), parsedName(cr));
        for (String source : List.of(lf, crlf, cr)) {
            String normalized = new String(ProgramSignatureDescriptor.normalizedSourceBytes(source), StandardCharsets.UTF_8);
            assertEquals(lf, normalized);
            assertEquals("first\nsecond", parsedName(normalized));
            assertEquals(descriptor(lf), descriptor(normalized));
        }
    }
}
