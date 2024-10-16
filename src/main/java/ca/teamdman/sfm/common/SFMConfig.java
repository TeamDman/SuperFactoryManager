package ca.teamdman.sfm.common;


import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class SFMConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final SFMConfig.Common COMMON;
    public static final Client CLIENT;
    static {
        final Pair<Common, ModConfigSpec> commonSpecPair = new ModConfigSpec.Builder().configure(SFMConfig.Common::new);
        COMMON_SPEC = commonSpecPair.getRight();
        COMMON = commonSpecPair.getLeft();
        final Pair<SFMConfig.Client, ModConfigSpec> clientSpecPair = new ModConfigSpec.Builder().configure(SFMConfig.Client::new);
        CLIENT_SPEC = clientSpecPair.getRight();
        CLIENT = clientSpecPair.getLeft();
    }

    public static class Common {
        public final ModConfigSpec.IntValue timerTriggerMinimumIntervalInTicks;
        public final ModConfigSpec.IntValue timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO;
        public final ModConfigSpec.IntValue maxIfStatementsInTriggerBeforeSimulationIsntAllowed;
        public final ModConfigSpec.ConfigValue<List<? extends String>> blacklistedResourceTypesForTransfer;

        Common(ModConfigSpec.Builder builder) {
            timerTriggerMinimumIntervalInTicks = builder
                    .defineInRange("timerTriggerMinimumIntervalInTicks", 20, 1, Integer.MAX_VALUE);
            timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO = builder
                    .defineInRange(
                            "timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIOStatementsPresent",
                            1,
                            1,
                            Integer.MAX_VALUE
                    );
            maxIfStatementsInTriggerBeforeSimulationIsntAllowed = builder
                    .comment(
                            "The number of scenarios to check is 2^n where n is the number of if statements in a trigger")
                    .defineInRange("maxIfStatementsInTriggerBeforeSimulationIsntAllowed", 10, 0, Integer.MAX_VALUE);
            blacklistedResourceTypesForTransfer = builder
                    .comment("What resource types should SFM not be allowed to move")
                    .defineListAllowEmpty("blacklistedResourceTypes", List.of(), s -> s instanceof String);
        }
    }

    /**
     * Get a config value in a way that doesn't fail when running tests
     */
    public static <T> T getOrDefault(ModConfigSpec.ConfigValue<T> configValue) {
        try {
            return configValue.get();
        } catch (Exception e) {
            return configValue.getDefault();
        }
    }

    public static void register(ModLoadingContext context) {
        context.getActiveContainer().registerConfig(ModConfig.Type.COMMON, SFMConfig.COMMON_SPEC);
        context.getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SFMConfig.CLIENT_SPEC);
    }

    public static class Client {
        public final ModConfigSpec.BooleanValue showLineNumbers;

        Client(ModConfigSpec.Builder builder) {
            showLineNumbers = builder
                    .define("showLineNumbers", false);
        }
    }
}
