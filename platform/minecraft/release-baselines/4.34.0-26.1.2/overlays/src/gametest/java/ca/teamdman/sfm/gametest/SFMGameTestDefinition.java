package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;

import java.util.Locale;
import java.util.function.Consumer;

public abstract class SFMGameTestDefinition {
    public abstract String template();
    public String templateModId() {
        return SFM.MOD_ID;
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

    public void intoTestFunction(GameTestHelper helper) {
        String testName = this.testName();
            try {
                this.run(new SFMGameTestHelper(helper));
            } catch (Exception e) {
                SFM.LOGGER.error("Test failed: {}", testName, e);
                throw e;
            }
    }

    public FunctionGameTestInstance intoTestInstance(
            ResourceKey<Consumer<GameTestHelper>> testFunctionKey,
            Holder<TestEnvironmentDefinition<?>> environment
    ) {
        return new FunctionGameTestInstance(
                testFunctionKey,
                new TestData<>(
                        environment,
                        Identifier.fromNamespaceAndPath(this.templateModId(), this.template()),
                        this.maxTicks(),
                        this.setupTicks(),
                        this.required(),
                        Rotation.NONE
                )
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
