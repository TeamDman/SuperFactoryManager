package ca.teamdman.sfml.test;

import ca.teamdman.sfm.client.program.ClientManagerTargetBindings;
import ca.teamdman.sfm.client.program.ClientFrameWorkBudget;
import ca.teamdman.sfm.client.program.ClientFrameSourceBudget;
import ca.teamdman.sfm.common.label.LabelPositionHolder;
import net.minecraft.core.BlockPos;
import ca.teamdman.sfml.ast.BoolFrameModulo;
import ca.teamdman.sfml.ast.FrameTrigger;
import ca.teamdman.sfml.ast.IfStatement;
import ca.teamdman.sfml.ast.ProgramExecutionSide;
import ca.teamdman.sfml.program_builder.ProgramBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientFrameProgramTests {
    private static final String ANIMATION = """
            CLIENT BTW
            EVERY FRAME FOR displays AS display DO
                IF FRAME MOD 2 EQ 0 THEN
                    RENDER IMAGE "minecraft:textures/block/red_concrete.png" TO display
                ELSE
                    RENDER IMAGE "minecraft:textures/block/blue_concrete.png" TO display
                END
            END
            """;

    @Test
    void clientFrameProgramParsesAndChangesFromPureLogic() {
        var result = new ProgramBuilder(ANIMATION)
                .forExecutionSide(ProgramExecutionSide.CLIENT).build();
        assertTrue(result.isBuildSuccessful(), () -> result.metadata().errors().toString());
        FrameTrigger trigger = assertInstanceOf(FrameTrigger.class, result.program().triggers().get(0));
        assertEquals("displays", trigger.labels().get(0).name());
        assertEquals("display", trigger.binding());
        IfStatement branch = assertInstanceOf(IfStatement.class, trigger.block().statements().get(0));
        BoolFrameModulo frame = assertInstanceOf(BoolFrameModulo.class, branch.condition());
        assertTrue(frame.testFrame(0));
        assertFalse(frame.testFrame(1));
        assertTrue(frame.testFrame(2));
        assertTrue(new ProgramBuilder(result.program().toString())
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
    }

    @Test
    void frameSyntaxCannotEscapeIntoServerManager() {
        assertFalse(new ProgramBuilder(ANIMATION)
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
        assertFalse(new ProgramBuilder(ANIMATION.replace("CLIENT BTW\n", ""))
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
        assertFalse(new ProgramBuilder("SERVER BTW\nEVERY 20 TICKS DO "
                + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display END")
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
    }

    @Test
    void frameBodyFailsClosedForServerStatementsAndWorldReads() {
        assertFalse(new ProgramBuilder("CLIENT BTW\nEVERY FRAME FOR displays AS display DO "
                + "INPUT FROM chest END")
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
        assertFalse(new ProgramBuilder("CLIENT BTW\nEVERY FRAME FOR displays AS display DO "
                + "IF REDSTONE THEN RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO display END END")
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
        assertFalse(new ProgramBuilder("CLIENT BTW\nEVERY FRAME FOR displays AS display DO "
                + "RENDER IMAGE \"minecraft:textures/block/red_concrete.png\" TO other END")
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
    }

    @Test
    void frameKeywordsRemainUsableAsLegacyLabels() {
        assertTrue(new ProgramBuilder("SERVER BTW\nEVERY 20 TICKS DO INPUT FROM frame OUTPUT TO render END")
                .forExecutionSide(ProgramExecutionSide.SERVER).build().isBuildSuccessful());
    }

    @Test
    void oversizedFrameWorkFailsBeforeExecution() {
        String statement = "RENDER IMAGE \"a:b\" TO display\n";
        assertFalse(new ProgramBuilder("CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n"
                + statement.repeat(FrameTrigger.MAX_BODY_NODES + 1) + "END")
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
        String nested = "IF TRUE THEN ".repeat(FrameTrigger.MAX_NESTING + 1)
                        + statement + " END".repeat(FrameTrigger.MAX_NESTING + 1);
        assertFalse(new ProgramBuilder("CLIENT BTW\nEVERY FRAME FOR displays AS display DO\n" + nested + " END")
                .forExecutionSide(ProgramExecutionSide.CLIENT).build().isBuildSuccessful());
    }

    @Test
    void missingTargetsFailAndLabelOrderDoesNotChangeConsentScope() {
        var program = new ProgramBuilder(ANIMATION).forExecutionSide(ProgramExecutionSide.CLIENT)
                .build().program();
        assertThrows(IllegalArgumentException.class,
                () -> ClientManagerTargetBindings.canonical(program, LabelPositionHolder.empty()));
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos second = new BlockPos(4, 5, 6);
        assertEquals(
                ClientManagerTargetBindings.canonical(program,
                        LabelPositionHolder.empty().add("displays", first).add("displays", second)),
                ClientManagerTargetBindings.canonical(program,
                        LabelPositionHolder.empty().add("displays", second).add("displays", first))
        );
    }

    @Test
    void displayWorkBudgetIsSharedUntilTheNextActualRenderFrame() {
        ClientFrameWorkBudget budget = new ClientFrameWorkBudget(128);
        for (int display = 0; display < 128; display++) assertTrue(budget.tryAcquire(7));
        for (int display = 0; display < 1000; display++) assertFalse(budget.tryAcquire(7));
        assertTrue(budget.tryAcquire(8));
        budget.clear();
        assertTrue(budget.tryAcquire(8));
    }

    @Test
    void lexicalGuardRejectsDeepSourceBeforeRecursiveParsing() {
        assertTrue(ClientFrameSourceBudget.permits(ANIMATION));
        assertFalse(ClientFrameSourceBudget.permits("IF TRUE THEN ".repeat(1000) + " END".repeat(1000)));
        assertFalse(ClientFrameSourceBudget.permits("IF " + "(".repeat(1000) + "TRUE" + ")".repeat(1000) + " THEN END"));
        assertFalse(ClientFrameSourceBudget.permits("IF " + "NOT ".repeat(1000) + "TRUE THEN END"));
        assertFalse(ClientFrameSourceBudget.permits("IF " + "TRUE AND ".repeat(1000) + "TRUE THEN END"));
        assertTrue(ClientFrameSourceBudget.permits("-- " + "IF ( NOT ".repeat(1000) + "\n" + ANIMATION));
        assertTrue(ClientFrameSourceBudget.permits("NAME \"" + "IF ( NOT ".repeat(1000) + "\"\n" + ANIMATION));
    }
}
