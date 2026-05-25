package ca.teamdman.sfm.gametest;

import ca.teamdman.sfm.SFM;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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

    public Consumer<GameTestHelper> intoFunctionBody() {
        String testName = this.testName();
        return helper -> {
            try {
                this.run(new SFMGameTestHelper(helper));
            } catch (Exception e) {
                SFM.LOGGER.error("Test failed: {}", testName, e);
                throw e;
            }
        };
    }

    public GameTestInstance intoTestInstance(
            Holder<TestEnvironmentDefinition<?>> environment
    ) {
        return new TestData<>(
                environment,
                Identifier.fromNamespaceAndPath(this.templateModId(), this.template()),
                this.maxTicks(),
                this.setupTicks(),
                this.required(),
                Rotation.NONE
        );
    }

    public ResourceKey<Consumer<GameTestHelper>> testFunctionKey() {
        return ResourceKey.create(
                Registries.TEST_FUNCTION,
                Identifier.fromNamespaceAndPath(this.templateModId(), this.testName())
        );
    }

    public ResourceKey<GameTestInstance> testInstanceKey() {
        return ResourceKey.create(
                Registries.TEST_INSTANCE,
                Identifier.fromNamespaceAndPath(this.templateModId(), this.testName())
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
