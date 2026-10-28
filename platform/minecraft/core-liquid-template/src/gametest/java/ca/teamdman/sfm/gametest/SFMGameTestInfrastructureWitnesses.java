package ca.teamdman.sfm.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Consumer;

/** Explicitly selected failure witnesses; absent from ordinary GameTest discovery. */
@SFMGameTestGenerator
public final class SFMGameTestInfrastructureWitnesses extends SFMGameTestGeneratorBase {
    @Override
    public void generateTests(Consumer<SFMGameTestDefinition> tests) {
        // The supported CLI --filter sets this property on the game JVM. Keeping
        // negative witnesses out of an unfiltered suite avoids expected failures.
        if (!System.getProperty("sfm.gametestSelection", "").contains("sfm_integrity_witness_")) {
            return;
        }
        for (Kind kind : Kind.values()) {
            tests.accept(new Witness(kind));
        }
    }

    private enum Kind {
        FLOOR_REJECTED, FLOOR_ALLOWED, HELPER_FLOOR_PREWRITE_REJECTED, RUN_BLOCK_REJECTED
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
        , COMMAND_BLOCK_REJECTED
{% endcase %}
    }

    private static final class Witness extends SFMGameTestDefinition {
        private final Kind kind;

        private Witness(Kind kind) {
            this.kind = kind;
        }

        @Override public String template() { return "3x3x3"; }
        @Override public String testName() { return "sfm_integrity_witness_" + kind.name().toLowerCase(java.util.Locale.ROOT); }
        @Override public int maxTicks() { return 10; }
        @Override public boolean mayDamagePlatform() { return kind == Kind.FLOOR_ALLOWED; }

        @Override
        public void run(SFMGameTestHelper helper) {
            if (kind == Kind.RUN_BLOCK_REJECTED) {
{% case minecraft_version %}
{% when "26.1.2" %}
                BlockPos reserved = helper.testInfo.getTestBlockPos();
                helper.getLevel().setBlock(reserved, Blocks.DIRT.defaultBlockState(), 3);
{% else %}
                helper.getLevel().setBlock(helper.absolutePos(BlockPos.ZERO), Blocks.DIRT.defaultBlockState(), 3);
{% endcase %}
{% case minecraft_version %}
{% when "26.1.2" %}
{% else %}
            } else if (kind == Kind.COMMAND_BLOCK_REJECTED) {
                helper.getLevel().setBlock(helper.absolutePos(new BlockPos(1, 0, -1)),
                        Blocks.DIRT.defaultBlockState(), 3);
{% endcase %}
            } else if (kind == Kind.HELPER_FLOOR_PREWRITE_REJECTED) {
                BlockPos floor = new BlockPos(1, 1, 1);
                try {
                    helper.setBlock(floor, Blocks.DIRT.defaultBlockState());
                    helper.assertTrue(false, "helper setBlock accepted a protected floor write");
                } catch (GameTestAssertException e) {
                    helper.assertTrue(e.getMessage() != null && e.getMessage().contains("phase=placement"),
                            "helper setBlock failed for a reason other than the pre-write guard: " + e.getMessage());
                    helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(floor)).is(Blocks.POLISHED_ANDESITE),
                            "helper setBlock changed the floor before rejecting the write");
                }
            } else {
                helper.getLevel().setBlock(helper.absolutePos(new BlockPos(1, 1, 1)),
                        Blocks.DIRT.defaultBlockState(), 3);
            }
            helper.succeed();
        }
    }
}
