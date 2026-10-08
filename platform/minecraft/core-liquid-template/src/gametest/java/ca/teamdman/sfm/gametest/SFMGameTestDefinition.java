package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
{% when "26.1.2" %}
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
{% endcase %}
import net.minecraft.gametest.framework.GameTestHelper;
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
import net.minecraft.gametest.framework.TestFunction;
{% when "26.1.2" %}
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
{% endcase %}
import net.minecraft.world.level.block.Rotation;

import java.util.Locale;
import java.util.function.Consumer;

public abstract class SFMGameTestDefinition {
    public abstract String template();
    public String templateModId() {
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        return "sfm";
{% when "26.1.2" %}
        return SFM.MOD_ID;
{% endcase %}
    }

    public abstract void run(SFMGameTestHelper helper);

    public String batchName() {
        return "defaultBatch";
    }

    public String testName() {
{% if features.gametest_static_names %}
        return testNameFor(getClass());
    }

    public static String testNameFor(Class<?> clazz) {

        return toSnakeCase(clazz.getSimpleName().replaceAll("GameTest$", ""));
{% else %}
        return toSnakeCase(getClass().getSimpleName().replaceAll("GameTest$", ""));
{% endif %}
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

{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
    public TestFunction intoTestFunction() {

        String batchName = this.batchName();
{% when "26.1.2" %}
    public void intoTestFunction(GameTestHelper helper) {

{% endcase %}
        String testName = this.testName();
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
        String structureName = this.templateModId() + ":" + this.template();
        Rotation rotation = Rotation.NONE;
        int maxTicks = this.maxTicks();
        int setupTicks = this.setupTicks();
        boolean required = this.required();
        Consumer<GameTestHelper> runner = (GameTestHelper helper) -> {
{% when "26.1.2" %}
{% endcase %}
            try {
                this.run(new SFMGameTestHelper(helper));
            } catch (Exception e) {
                SFM.LOGGER.error("Test failed: {}", testName, e);
                throw e;
            }
{% case minecraft_version %}
{% when "1.19.2", "1.19.4", "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.21", "1.21.1" %}
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
{% when "26.1.2" %}
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
{% endcase %}
        );
    }

{% if features.gametest_static_names %}
    private static String toSnakeCase(String input) {
{% else %}
    private String toSnakeCase(String input) {
{% endif %}
        return
                input.replaceAll("([a-zA-Z])(\\d+)", "$1_$2")
                        .replaceAll("(\\d+)([a-zA-Z])", "$1_$2")
                        .replaceAll("([a-z])([A-Z])", "$1_$2")
                        .toLowerCase(Locale.ROOT);
    }
}
