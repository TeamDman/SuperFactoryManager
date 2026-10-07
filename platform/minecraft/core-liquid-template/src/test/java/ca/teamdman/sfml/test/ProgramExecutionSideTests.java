package ca.teamdman.sfml.test;

import ca.teamdman.sfml.ast.Program;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramExecutionSideTests {
    @Test
    void explicitHeaderAssertsButDoesNotSelectHost() {
        String source = "-- an author comment\nCLIENT BTW\nNAME \"dashboard\"\nEVERY 20 TICKS DO END";
        Program client = new ProgramBuilder(source)
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().program();
        assertNotNull(client);
        assertEquals(ProgramExecutionSide.CLIENT, client.executionSideDeclaration().side());
        assertTrue(client.toString().startsWith("CLIENT BTW\n"));
        assertTrue(client.astBuilder().getContextForNode(client.executionSideDeclaration()).isPresent());
        assertThrows(IllegalArgumentException.class, () -> client.assertCompatibleWith(ProgramExecutionSide.SERVER));
        assertThrows(IllegalArgumentException.class, () -> client.tick((ManagerBlockEntity) null));

        // The cache must not return a CLIENT-host build for a SERVER host.
        assertFalse(new ProgramBuilder(source)
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
        assertTrue(new ProgramBuilder(source).build().isBuildSuccessful());
    }

    @Test
    void serverHeaderAndUnmarkedProgramsKeepTheirCompatibility() {
        String server = "SERVER BTW\nEVERY 20 TICKS DO END";
        assertTrue(new ProgramBuilder(server)
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
        assertFalse(new ProgramBuilder(server)
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());

        String portable = "EVERY 20 TICKS DO END";
        assertTrue(new ProgramBuilder(portable)
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
        assertTrue(new ProgramBuilder(portable)
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
        assertEquals(null, new ProgramBuilder(portable).build().program().executionSideDeclaration());
    }

    @Test
    void headerMustBeFirstAndKeywordsRemainUsableAsLabels() {
        assertFalse(new ProgramBuilder("NAME \"late\"\nCLIENT BTW\nEVERY 20 TICKS DO END")
                .build().isBuildSuccessful());
        assertTrue(new ProgramBuilder("SERVER BTW\nEVERY 20 TICKS DO INPUT FROM client OUTPUT TO server, btw END")
                .build().isBuildSuccessful());
    }
}
