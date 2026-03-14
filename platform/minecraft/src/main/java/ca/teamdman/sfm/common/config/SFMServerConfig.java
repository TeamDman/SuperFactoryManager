package ca.teamdman.sfm.common.config;

import ca.teamdman.sfm.SFM;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = SFM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SFMServerConfig {
    public final ForgeConfigSpec.BooleanValue disableProgramExecution;
    public final ForgeConfigSpec.BooleanValue logResourceLossToConsole;
    public final ForgeConfigSpec.IntValue timerTriggerMinimumIntervalInTicks;
    public final ForgeConfigSpec.IntValue timerTriggerMinimumIntervalInTicksWhenOnlyForgeEnergyIO;
    public final ForgeConfigSpec.IntValue maxIfStatementsInTriggerBeforeSimulationIsntAllowed;
    public final ForgeConfigSpec.IntValue maxDiskProblems;
    public final ForgeConfigSpec.ConfigValue<List<? extends String>> disallowedResourceTypesForTransfer;
    public final ForgeConfigSpec.EnumValue<LevelsToShards> levelsToShards;
    /**
     * This is used by managers to detect when the config has changed.
     * When the manager cached var differs from this, the manager will rebuild its program.
     */
    private int revision = 0;

    SFMServerConfig(ForgeConfigSpec.Builder builder) {
        builder.comment("This config is shown to clients, don't put anything secret in here");
        disableProgramExecution = builder
                .comment("Prevents factory managers from compiling and running code (for emergencies)")
                .define("disableProgramExecution", false);

        logResourceLossToConsole = builder
                .comment("Log resource loss to console")
                .define("logResourceLossToConsole", true);

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

        maxDiskProblems = builder
                .comment("The max number of problems annotated on a disk item before truncation")
                .defineInRange("maxDiskProblems", 10, 0, Integer.MAX_VALUE);

        disallowedResourceTypesForTransfer = builder
                .comment("What resource types should SFM not be allowed to move")
                .defineListAllowEmpty(
                        List.of("disallowedResourceTypesForTransfer"),
                        List::of,
                        String.class::isInstance
                );
        levelsToShards = builder
                .comment(
                        "How to convert Enchanted Books to Experience Shards",
                        "JustOne = always produces 1 shard regardless of enchantments",
                        "EachOne = produces 1 shard per enchantment on the book.",
                        "SumLevels = produces a number of shards equal to the sum of the enchantments' levels",
                        "SumLevelsScaledExponentially = produces a number of shards equal to the sum of 2 to the power of each enchantment's level (1 -> 1 shard, 2 -> 4 shards, 3 -> 8 shards, etc)"
                )
                .defineEnum("levelsToShards", LevelsToShards.JustOne);
    }

    public int getRevision() {
        return revision;
    }

    @SubscribeEvent
    public static void onConfigLoaded(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SFMConfig.SERVER_CONFIG_SPEC) {
            SFMConfig.SERVER_CONFIG.revision++;
            SFM.LOGGER.info("SFM config loaded, now on revision {}", SFMConfig.SERVER_CONFIG.revision);
        }
    }

    @SubscribeEvent
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SFMConfig.SERVER_CONFIG_SPEC) {
            SFMConfig.SERVER_CONFIG.revision++;
            SFM.LOGGER.info("SFM config reloaded, now on revision {}", SFMConfig.SERVER_CONFIG.revision);
        }
    }

    public enum LevelsToShards {
        JustOne,
        EachOne,
        SumLevels,
        SumLevelsScaledExponentially,
    }
}
