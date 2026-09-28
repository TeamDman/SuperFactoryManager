package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

import java.util.Locale;
import java.util.function.Consumer;

public abstract class SFMGameTestDefinition {
    public abstract String template();
    public String templateModId() {
        return "sfm";
    }

    public abstract void run(SFMGameTestHelper helper);

    public String batchName() {
        return "defaultBatch";
    }

    public String testName() {
        return toSnakeCase(getClass().getSimpleName().replaceAll("GameTest$", ""));
    }

    public int maxTicks() {
        return 100;
    }

    public int setupTicks() {
        return 0;
    }

    public boolean required() {
        return true;
    }

    public TestFunction intoTestFunction() {

        String batchName = this.batchName();
        String testName = this.testName();
        String structureName = this.templateModId() + ":" + this.template();
        Rotation rotation = Rotation.NONE;
        int maxTicks = this.maxTicks();
        int setupTicks = this.setupTicks();
        boolean required = this.required();
        Consumer<GameTestHelper> runner = (GameTestHelper helper) -> {
            try {
                this.run(new SFMGameTestHelper(helper));
            } catch (Exception e) {
                SFM.LOGGER.error("Test failed: {}", testName, e);
                throw e;
            }
        };
        return new TestFunction(
                batchName,
                testName,
                structureName,
                rotation,
                maxTicks,
                setupTicks,
                required,
                runner
        );
    }

    private String toSnakeCase(String input) {
        return
                input.replaceAll("([a-zA-Z])(\\d+)", "$1_$2")
                        .replaceAll("(\\d+)([a-zA-Z])", "$1_$2")
                        .replaceAll("([a-z])([A-Z])", "$1_$2")
                        .toLowerCase(Locale.ROOT);
    }
}
