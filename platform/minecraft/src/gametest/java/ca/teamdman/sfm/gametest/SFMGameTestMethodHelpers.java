package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.common.blockentity.ManagerBlockEntity;
import ca.teamdman.sfm.common.item.DiskItem;
import ca.teamdman.sfml.ast.SFMLProgram;
import ca.teamdman.sfml.program_builder.SFMLProgramBuilder;
import net.minecraft.gametest.framework.GameTestAssertException;

import java.util.concurrent.atomic.AtomicReference;

public class SFMGameTestMethodHelpers {

    public static void assertTrue(
            boolean condition,
            String message
    ) {

        if (!condition) {
            @SuppressWarnings("UnnecessaryLocalVariable")
            var toThrow = new GameTestAssertException(message);
            // Uncomment below for detailed location information
            // Note that the tests fail every tick using this until they succeed, so you will see logs that make things look like tests are failing if this is uncommented
//            SFM.LOGGER.error("Assertion failed: {}", message, toThrow);
            throw toThrow;
        }
    }

    public static SFMLProgram compile(String code) {

        AtomicReference<SFMLProgram> rtn = new AtomicReference<>();

        new SFMLProgramBuilder(code)
                .build()
                .caseSuccess((program, metadata) -> rtn.set(program))
                .caseFailure((metadata) -> {
                    throw new GameTestAssertException("Failed to compile program: " + metadata.errors()
                            .stream()
                            .map(Object::toString)
                            .reduce("", (a, b) -> a + "\n" + b));
                });
        return rtn.get();
    }

    public static void assertManagerRunning(ManagerBlockEntity manager) {

        SFMGameTestMethodHelpers.assertTrue(manager.getDisk() != null, "No disk in manager");
        SFMGameTestMethodHelpers.assertTrue(
                manager.getState() == ManagerBlockEntity.State.RUNNING,
                "Program did not start running " + DiskItem.getErrors(manager.getDisk())
        );
    }

}
