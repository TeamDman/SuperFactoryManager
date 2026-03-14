package ca.teamdman.sfm.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

public class SFMDelegatedTestFunction extends TestFunction {
    private final SFMGameTestDefinition definition;

    public SFMDelegatedTestFunction(SFMGameTestDefinition definition) {
        // satisfy default constructor, we override all getters anyway
        super(
                definition.batchName(),
                definition.testName(),
                definition.templateModId() + ":" + definition.template(),
                Rotation.NONE,
                definition.maxTicks(),
                definition.setupTicks(),
                definition.required(),
                1,
                1,
                (GameTestHelper helper) -> {
                    throw new UnsupportedOperationException("We expect tests to call the run fn");
                }
        );
        this.definition = definition;
    }

    @Override
    public void run(GameTestHelper pGameTestHelper) {
        definition.run(new SFMGameTestHelper(pGameTestHelper));
    }

    @Override
    public String getTestName() {
        return definition.testName();
    }

    @Override
    public String getStructureName() {
        return definition.templateModId() + ":" + definition.template();
    }

    @Override
    public int getMaxTicks() {
        return definition.maxTicks();
    }

    @Override
    public boolean isRequired() {
        return definition.required();
    }

    @Override
    public String getBatchName() {
        return definition.batchName();
    }

    @Override
    public long getSetupTicks() {
        return definition.setupTicks();
    }

    @Override
    public Rotation getRotation() {
        return Rotation.NONE;
    }

    @Override
    public boolean isFlaky() {
        return getMaxAttempts() > 1;
    }

    @Override
    public int getMaxAttempts() {
        return 1;
    }

    @Override
    public int getRequiredSuccesses() {
        return 1;
    }
}
